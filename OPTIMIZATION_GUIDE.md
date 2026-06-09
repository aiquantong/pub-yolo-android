# Android YOLO 项目优化指南

基于对项目代码的深度分析，从**性能、架构、代码质量、内存管理**四个维度给出优化建议。

---

## 一、性能优化（高优先级）

### 1.1 模型加载优化

**问题**：`YoloDetector` 在 `init` 中同步加载模型，阻塞主线程

```kotlin
// 当前代码 - 阻塞主线程
init {
    yolo = TfliteDetector(context)
    yolo.loadModel(config, useGPU)  // 耗时操作！
}
```

**优化方案**：异步加载 + 预加载机制

```kotlin
class YoloDetector(
    private val context: Context,
    private val config: DetectorConfig
) : ObjectDetector {

    private var yolo: TfliteDetector? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        // 异步加载模型，不阻塞主线程
        scope.launch(Dispatchers.IO) {
            val detector = TfliteDetector(context)
            detector.setIouThreshold(config.iouThreshold)
            detector.setConfidenceThreshold(config.confidenceThreshold)
            detector.loadModel(config.model, config.useGPU)
            
            yolo = detector
            _isReady.value = true
        }
    }

    override fun detect(image: TensorImage, rotation: Int): DetectionResult {
        // 如果模型未加载完成，返回空结果
        val detector = yolo ?: return DetectionResult.empty()
        
        return withContext(Dispatchers.Default) {
            // 执行检测...
        }
    }
}
```

### 1.2 图像预处理优化

**问题**：`TfliteDetector.preprocess()` 每次创建新 Bitmap

```java
// 当前代码 - 每次创建新 Bitmap
public Bitmap preprocess(Bitmap bitmap) {
    Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
    return resizedBitmap;
}
```

**优化方案**：使用 Bitmap 池复用

```java
public class TfliteDetector extends Detector {
    // 使用 Bitmap 池避免频繁创建
    private final BitmapPool bitmapPool = new BitmapPool(2);
    private Bitmap pendingBitmapFrame;
    
    public Bitmap preprocess(Bitmap bitmap) {
        // 从池中获取或创建
        Bitmap target = bitmapPool.acquire(INPUT_SIZE, INPUT_SIZE);
        
        // 使用 Canvas 缩放，避免 createScaledBitmap 的内存分配
        Canvas canvas = new Canvas(target);
        Matrix matrix = new Matrix();
        matrix.setScale(
            (float) INPUT_SIZE / bitmap.getWidth(),
            (float) INPUT_SIZE / bitmap.getHeight()
        );
        canvas.drawBitmap(bitmap, matrix, null);
        
        return target;
    }
    
    public void releaseBitmap(Bitmap bitmap) {
        bitmapPool.release(bitmap);
    }
}

// Bitmap 池实现
class BitmapPool {
    private final Queue<Bitmap> pool = new LinkedList<>();
    private final int maxSize;
    
    public synchronized Bitmap acquire(int width, int height) {
        for (Bitmap bitmap : pool) {
            if (bitmap.getWidth() == width && bitmap.getHeight() == height) {
                pool.remove(bitmap);
                bitmap.eraseColor(Color.TRANSPARENT);
                return bitmap;
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }
    
    public synchronized void release(Bitmap bitmap) {
        if (pool.size() < maxSize) {
            pool.offer(bitmap);
        } else {
            bitmap.recycle();
        }
    }
}
```

### 1.3 推理线程优化

**问题**：检测在主线程执行，导致 UI 卡顿

```kotlin
// 当前代码 - CameraFragment 中直接调用
detectObjects(image)
```

**优化方案**：使用协程 + 背压处理

```kotlin
class CameraFragment : Fragment() {
    
    private val detectionScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob()
    )
    
    // 使用 Channel 处理背压，只处理最新帧
    private val frameChannel = Channel<ImageProxy>(
        capacity = Channel.CONFLATED  // 只保留最新帧
    )
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 启动检测协程
        detectionScope.launch {
            for (frame in frameChannel) {
                val result = detector.detect(frame)
                withContext(Dispatchers.Main) {
                    updateOverlay(result)
                }
                frame.close()  // 及时释放 ImageProxy
            }
        }
    }
    
    private fun onNewFrame(image: ImageProxy) {
        // 非阻塞发送，旧帧会被丢弃
        frameChannel.trySend(image)
    }
}
```

### 1.4 GPU 加速优化

**问题**：GPU 兼容性检查被注释掉，且未处理不支持的情况

```kotlin
// 当前代码
DELEGATE_GPU -> {
    // 注释掉的兼容性检查
    // if (CompatibilityList().isDelegateSupportedOnThisDevice) {
    baseOptionsBuilder.useGpu()
}
```

**优化方案**：智能降级 + 性能监控

```kotlin
class DelegateSelector(private val context: Context) {
    
    fun selectBestDelegate(): Delegate {
        return when {
            isGpuSupported() -> Delegate.GPU
            isNnapiSupported() -> Delegate.NNAPI
            else -> Delegate.CPU
        }
    }
    
    private fun isGpuSupported(): Boolean {
        return try {
            val compatList = CompatibilityList()
            compatList.isDelegateSupportedOnThisDevice
        } catch (e: Exception) {
            false
        }
    }
    
    // 运行时性能监控，自动切换
    suspend fun autoSelectDelegate(
        detector: ObjectDetector
    ): Delegate {
        val delegates = listOf(Delegate.GPU, Delegate.NNAPI, Delegate.CPU)
        var bestDelegate = Delegate.CPU
        var bestFps = 0f
        
        for (delegate in delegates) {
            val fps = benchmarkDelegate(detector, delegate)
            if (fps > bestFps) {
                bestFps = fps
                bestDelegate = delegate
            }
        }
        
        return bestDelegate
    }
}
```

---

## 二、架构优化（高优先级）

### 2.1 检测器工厂模式

**问题**：`ObjectDetectorHelper` 中使用 if-else 创建检测器

```kotlin
// 当前代码
if (currentModel == MODEL_YOLO) {
    objectDetector = YoloDetector(...)
} else {
    objectDetector = TaskVisionDetector(...)
}
```

**优化方案**：工厂模式 + 注册机制

```kotlin
// 检测器工厂接口
interface DetectorFactory {
    fun create(context: Context, config: DetectorConfig): ObjectDetector
    fun supports(modelType: Int): Boolean
}

// YOLO 检测器工厂
class YoloDetectorFactory : DetectorFactory {
    override fun supports(modelType: Int) = modelType == MODEL_YOLO
    override fun create(context: Context, config: DetectorConfig) = 
        YoloDetector(context, config)
}

// TaskVision 检测器工厂
class TaskVisionDetectorFactory : DetectorFactory {
    override fun supports(modelType: Int) = modelType in MODEL_MOBILENET..MODEL_EFFICIENTDET2
    override fun create(context: Context, config: DetectorConfig) = 
        TaskVisionDetector(context, config)
}

// 检测器管理器
class DetectorManager(factories: List<DetectorFactory> = defaultFactories) {
    
    companion object {
        val defaultFactories = listOf(
            YoloDetectorFactory(),
            TaskVisionDetectorFactory()
        )
    }
    
    fun createDetector(
        modelType: Int,
        context: Context,
        config: DetectorConfig
    ): Result<ObjectDetector> {
        val factory = factories.find { it.supports(modelType) }
            ?: return Result.failure(IllegalArgumentException("不支持的模型类型: $modelType"))
        
        return try {
            Result.success(factory.create(context, config))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 2.2 配置对象化

**问题**：`YoloDetector` 参数过多，且使用魔术数字

```kotlin
// 当前代码
class YoloDetector(
    var confidenceThreshold: Float = 0.5f,
    var iouThreshold: Float = 0.3f,
    var numThreads: Int = 2,
    var maxResults: Int = 3,
    var currentDelegate: Int = 0,  // 魔术数字！
    var currentModel: Int = 0,
    val context: Context
)
```

**优化方案**：使用数据类 + 枚举

```kotlin
// 委托类型枚举
enum class Delegate(val code: Int) {
    CPU(0),
    GPU(1),
    NNAPI(2)
}

// 模型类型枚举
enum class ModelType(val code: Int) {
    MOBILENET(0),
    EFFICIENTDET0(1),
    EFFICIENTDET1(2),
    EFFICIENTDET2(3),
    YOLO(4)
}

// 检测器配置
@Parcelize
data class DetectorConfig(
    val confidenceThreshold: Float = 0.5f,
    val iouThreshold: Float = 0.3f,
    val numThreads: Int = 2,
    val maxResults: Int = 3,
    val delegate: Delegate = Delegate.CPU,
    val modelType: ModelType = ModelType.YOLO,
    val modelConfig: YoloModelConfig = YoloModelConfig.OBB_Best_Float32
) : Parcelable

// YOLO 模型配置密封类
sealed class YoloModelConfig(
    val task: String,
    val format: String,
    val modelPath: String,
    val metadataPath: String
) {
    object OBB_Best_Float32 : YoloModelConfig(
        "obb", "tflite",
        "yolo26_obb_best_float32.tflite",
        "yolo26_obb_best_metadata.yaml"
    )
    
    object Standard_COCO : YoloModelConfig(
        "detect", "tflite",
        "yolo11n_float32.tflite",
        "metadata.yaml"
    )
}
```

### 2.3 绘制逻辑拆分

**问题**：`OverlayView` 同时处理检测框、OBB、仪表读数绘制

```kotlin
// 当前代码 - OverlayView 超过200行
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    // 6个 Paint 对象
    // 绘制检测框、OBB、仪表读数...
}
```

**优化方案**：策略模式拆分绘制器

```kotlin
// 绘制器接口
interface DetectionRenderer {
    fun render(canvas: Canvas, detection: ObjectDetection, scaleFactor: Float)
}

// 普通检测框绘制器
class BoxRenderer(private val paint: Paint) : DetectionRenderer {
    override fun render(canvas: Canvas, detection: ObjectDetection, scaleFactor: Float) {
        val box = detection.boundingBox
        val rect = RectF(
            box.left * scaleFactor,
            box.top * scaleFactor,
            box.right * scaleFactor,
            box.bottom * scaleFactor
        )
        canvas.drawRect(rect, paint)
    }
}

// OBB 旋转框绘制器
class OBBRenderer(private val paint: Paint) : DetectionRenderer {
    override fun render(canvas: Canvas, detection: ObjectDetection, scaleFactor: Float) {
        val box = detection.boundingBox
        val angle = detection.angle
        
        val centerX = (box.left + box.right) / 2 * scaleFactor
        val centerY = (box.top + box.bottom) / 2 * scaleFactor
        val width = (box.right - box.left) * scaleFactor
        val height = (box.bottom - box.top) * scaleFactor
        
        canvas.withTranslation(centerX, centerY) {
            rotate(Math.toDegrees(angle.toDouble()).toFloat())
            drawRect(
                RectF(-width/2, -height/2, width/2, height/2),
                paint
            )
        }
    }
}

// 仪表读数绘制器
class MeterReadingRenderer(
    private val boxPaint: Paint,
    private val textPaint: Paint,
    private val bgPaint: Paint
) : DetectionRenderer {
    override fun render(canvas: Canvas, detection: ObjectDetection, scaleFactor: Float) {
        // 绘制仪表读数...
    }
}

// 简化的 OverlayView
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    
    private val renderers = mutableMapOf<String, DetectionRenderer>()
    
    init {
        renderers["box"] = BoxRenderer(boxPaint)
        renderers["obb"] = OBBRenderer(obbPaint)
        renderers["meter"] = MeterReadingRenderer(meterPaint, textPaint, bgPaint)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        for (result in results) {
            val renderer = when {
                result.angle != 0f -> renderers["obb"]
                result.category.label.startsWith("meter_") -> renderers["meter"]
                else -> renderers["box"]
            }
            renderer?.render(canvas, result, scaleFactor)
        }
    }
}
```

---

## 三、代码质量优化（中优先级）

### 3.1 资源泄漏修复

**问题**：`Predictor.loadLabels()` 未使用 try-with-resources

```java
// 当前代码
protected void loadLabels(AssetManager assetManager, String metadataPath) throws IOException {
    InputStream inputStream;
    Yaml yaml = new Yaml();
    inputStream = assetManager.open(metadataPath);
    // ... 处理 ...
    inputStream.close();  // 可能不执行！
}
```

**优化方案**：

```java
protected void loadLabels(AssetManager assetManager, String metadataPath) throws IOException {
    try (InputStream inputStream = assetManager.open(metadataPath)) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(inputStream);
        // ... 处理 ...
    }  // 自动关闭
}
```

### 3.2 线程安全修复

**问题**：`Predictor.INPUT_SIZE` 是静态变量，多实例冲突

```java
// 当前代码
public static int INPUT_SIZE = 320;  // 静态变量！
```

**优化方案**：

```java
public abstract class Predictor {
    // 改为实例变量
    protected int inputSize = 320;
    
    protected void loadLabels(...) {
        // ...
        if (imgszArray != null && imgszArray.size() == 2) {
            inputSize = Math.max(imgszArray.get(0), imgszArray.get(1));
        }
    }
}
```

### 3.3 空安全优化

**问题**：多处使用 `!!` 和未检查的空值

```kotlin
// 当前代码
boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
```

**优化方案**：

```kotlin
class OverlayView(
    context: Context,  // 非空
    attrs: AttributeSet?
) : View(context, attrs) {
    
    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.bounding_box_color)
    }
}
```

---

## 四、内存管理优化（中优先级）

### 4.1 ByteBuffer 复用

**问题**：`TfliteDetector` 每次推理创建新的 ByteBuffer

```java
// 当前代码
private ByteBuffer imgData;

public void setInputOptim(Bitmap bitmap) {
    if (imgData == null) {
        imgData = ByteBuffer.allocateDirect(...);  // 延迟初始化
    }
    // ...
}
```

**优化方案**：预分配 + 复用

```java
public class TfliteDetector extends Detector {
    
    private final ByteBuffer inputBuffer;
    private final int[] pixelBuffer;
    
    public TfliteDetector(Context context) {
        super(context);
        
        // 预分配缓冲区
        int inputSize = INPUT_SIZE * INPUT_SIZE;
        inputBuffer = ByteBuffer.allocateDirect(
            inputSize * NUM_BYTES_PER_CHANNEL
        ).order(ByteOrder.nativeOrder());
        
        pixelBuffer = new int[inputSize];
    }
    
    public void setInput(Bitmap bitmap) {
        inputBuffer.clear();  // 重置位置，不释放内存
        
        bitmap.getPixels(pixelBuffer, 0, bitmap.getWidth(), 0, 0, 
                        bitmap.getWidth(), bitmap.getHeight());
        
        // 填充 inputBuffer...
    }
}
```

### 4.2 图像资源及时释放

**问题**：`ImageProxy` 未及时关闭

```kotlin
// 当前代码
override fun analyze(image: ImageProxy) {
    detectObjects(image)  // 可能持有引用
}
```

**优化方案**：

```kotlin
override fun analyze(image: ImageProxy) {
    try {
        val bitmap = image.toBitmap()
        detectObjects(bitmap, image.imageInfo.rotationDegrees)
    } finally {
        image.close()  // 确保释放
    }
}
```

---

## 五、优先级总结

| 优先级 | 优化项 | 预期收益 |
|--------|--------|----------|
| **P0** | 异步模型加载 | 消除启动卡顿 |
| **P0** | 推理线程优化 | 提升 FPS，避免 ANR |
| **P1** | Bitmap 池复用 | 减少 GC，提升流畅度 |
| **P1** | 检测器工厂模式 | 提升可扩展性 |
| **P2** | 绘制逻辑拆分 | 提升可维护性 |
| **P2** | 资源泄漏修复 | 提升稳定性 |
| **P3** | ByteBuffer 复用 | 减少内存分配 |// 当前：每次创建新 Bitmap
Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

// 优化：使用 Bitmap 池
Bitmap target = bitmapPool.acquire(INPUT_SIZE, INPUT_SIZE);
Canvas canvas = new Canvas(target);
canvas.drawBitmap(bitmap, matrix, null);

---

## 六、快速开始

建议按以下顺序实施优化：

1. **第一周**：实施 P0 优化（异步加载 + 推理线程）
2. **第二周**：实施 P1 优化（Bitmap 池 + 工厂模式）
3. **第三周**：实施 P2 优化（绘制拆分 + 资源修复）

如需我帮你实现某个具体优化，告诉我！
