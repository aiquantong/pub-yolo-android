# Android YOLO 项目编码规范与重构指南

## 基础交互规则
1. 请用中文回答我
2. 如果回答的是代码，请给每个关键节点、比较难懂的代码增加中文注释
3. 当生成的代码行数超过20行时，请考虑聚合代码以及考虑其颗粒度是否适合

## 项目架构规范

### 包结构规范
```
org.tensorflow.lite.examples.objectdetection/    # 主应用层
├── MainActivity.kt                               # 单Activity入口
├── ObjectDetectorHelper.kt                       # 检测器协调器
├── OverlayView.kt                                # 检测结果绘制
├── fragments/                                    # Fragment层
│   ├── CameraFragment.kt                         # 相机预览与检测
│   └── PermissionsFragment.kt                    # 权限管理
└── detectors/                                    # 检测器实现层
    ├── ObjectDetector.kt                         # 检测接口定义
    ├── YoloDetector.kt                           # YOLO检测器实现
    └── TaskVisionDetector.kt                     # TFLite Task Vision检测器

com.ultralytics.yolo/                             # Ultralytics YOLO库
├── predict/                                      # 预测引擎
│   ├── Predictor.java                            # 预测器抽象基类
│   └── detect/                                   # 检测实现
│       ├── TfliteDetector.java                   # TFLite推理引擎
│       ├── DetectedObject.java                   # 检测结果对象
│       └── PostProcessUtils.java                 # 后处理工具
├── meter/                                        # 仪表读数处理
│   ├── MeterReading.java                         # 读数数据模型
│   ├── MeterRange.java                           # 仪表量程定义
│   └── MeterReadingProcessor.java               # 读数计算处理器
├── models/                                       # 模型定义
│   ├── YoloModel.java                            # YOLO模型基类
│   └── LocalYoloModel.java                       # 本地模型配置
└── ImageProcessing.java                          # 图像处理工具
```

### 职责分离原则
- **Activity/Fragment**: 只负责UI生命周期管理和事件分发
- **Helper**: 负责业务逻辑协调（如ObjectDetectorHelper管理检测器创建和配置）
- **Detector**: 负责具体检测算法实现（YoloDetector、TaskVisionDetector）
- **Predictor**: 负责底层模型加载和推理（TfliteDetector）
- **Processor**: 负责后处理（MeterReadingProcessor处理仪表读数）

## 代码质量与重构规范

### 通用编码规范
1. 避免不必要的对象复制或克隆
2. 避免多层嵌套，提前返回
3. 使用适当的并发控制机制
4. 优先使用Kotlin空安全特性（?.、?:、let等）
5. 避免使用 !! 强制解包，除非确定非空

### Kotlin 特有规范
```kotlin
// 推荐：使用空安全调用和 Elvis 运算符
val label = detectedObject?.label ?: "unknown"

// 推荐：使用 let 处理可空类型
detectedObject?.let {
    processDetection(it)
}

// 避免：强制解包
detectedObject!!.label  // 危险操作

// 推荐：使用 apply 配置对象
val paint = Paint().apply {
    color = Color.RED
    strokeWidth = 2f
    style = Paint.Style.STROKE
}
```

### Java 特有规范
```java
// 推荐：使用 try-with-resources 确保资源释放
try (InputStream is = assetManager.open(path)) {
    // 处理输入流
} catch (IOException e) {
    Log.e(TAG, "加载失败: " + path, e);
}

// 避免：手动关闭资源且未处理异常
InputStream is = null;
try {
    is = assetManager.open(path);
} finally {
    if (is != null) {
        try {
            is.close();  // 可能抛出异常
        } catch (IOException e) {
            // 忽略
        }
    }
}
```

## 代码坏味道识别与处理

### 1. 神秘命名
- **问题**：变量、函数、类或模块的名称不能清晰表达其用途
- **处理**：重命名为具有描述性的名称，使代码自解释

```kotlin
// 坏味道
val ip = ImageProcessing()  // ip 含义不明
val yolo = TfliteDetector(context)  // yolo 是框架名，不是职责描述

// 重构后
val imageProcessor = ImageProcessing()
val tfliteDetector = TfliteDetector(context)
```

### 2. 重复代码
- **问题**：相同或相似的代码出现在多个地方
- **处理**：提取为函数、类或模块；应用模板方法模式

```kotlin
// 坏味道：YoloDetector 和 TaskVisionDetector 中都有相似的画框逻辑
// 处理：提取到 OverlayView 或独立的绘制工具类
```

### 3. 过长函数
- **问题**：函数过长，难以理解和维护
- **处理**：提取函数，将大函数分解为多个小函数

```java
// 坏味道：MeterReadingProcessor.processMeter() 超过100行
// 处理：提取为多个小方法
private MeterReading processMeter(...) {
    DetectedObject needle = findBestNeedle(meterBox, needles);
    if (needle == null) return null;
    
    float angle = calculateNeedleAngle(needle, meterBox);
    float value = convertAngleToValue(angle, meterRange);
    
    return new MeterReading(meterType, value, meterRange.unit);
}
```

### 4. 过大的类
- **问题**：类做了太多事情，违反单一职责原则
- **处理**：提取类，将相关功能拆分到新类

```kotlin
// 坏味道：OverlayView 同时处理检测框绘制、OBB旋转框绘制、仪表读数绘制
// 处理：拆分为多个绘制器
class DetectionBoxRenderer(private val canvas: Canvas)
class OBBRenderer(private val canvas: Canvas)
class MeterReadingRenderer(private val canvas: Canvas)
```

### 5. 过长参数列表
- **问题**：函数参数过多，难以理解和使用
- **处理**：引入参数对象、使用建造者模式

```kotlin
// 坏味道
class YoloDetector(
    var confidenceThreshold: Float = 0.5f,
    var iouThreshold: Float = 0.3f,
    var numThreads: Int = 2,
    var maxResults: Int = 3,
    var currentDelegate: Int = 0,
    var currentModel: Int = 0,
    val context: Context
)

// 重构：使用配置对象
data class DetectorConfig(
    val confidenceThreshold: Float = 0.5f,
    val iouThreshold: Float = 0.3f,
    val numThreads: Int = 2,
    val maxResults: Int = 3,
    val delegate: Delegate = Delegate.CPU
)

class YoloDetector(config: DetectorConfig, context: Context)
```

### 6. 发散式变化
- **问题**：一个类经常因为不同的原因在不同的方向上发生变化
- **处理**：拆分类，使每个类只因一种变化而变化

```kotlin
// 坏味道：YoloDetector 同时处理模型加载、图像预处理、后处理
// 处理：拆分为 ModelLoader、ImagePreprocessor、PostProcessor
```

### 7. 霰弹式修改
- **问题**：遇到某种变化需要修改多个类
- **处理**：将相关功能移到同一个类中

### 8. 依恋情结
- **问题**：一个函数用了过多另一个类的功能
- **处理**：将函数移到它更依恋的类中

### 9. 数据泥团
- **问题**：相同的几项数据经常一起出现
- **处理**：提取为数据类

```kotlin
// 坏味道：经常一起传递的宽、高、角度
fun drawBox(left: Float, top: Float, right: Float, bottom: Float, angle: Float)

// 重构：使用 RectF 和角度封装
fun drawBox(bounds: RectF, rotation: Float)
```

### 10. 基本类型偏执
- **问题**：过度使用基本类型，而不是自定义类型
- **处理**：引入值对象

```kotlin
// 坏味道
val confidence: Float  // 0.0 ~ 1.0
val angle: Float       // 弧度

// 重构：使用值对象
@JvmInline
value class Confidence(val value: Float) {
    init { require(value in 0.0f..1.0f) }
}

@JvmInline
value class Radians(val value: Float)
```

### 11. 重复的 switch/if-else
- **问题**：相同的条件判断出现在多个地方
- **处理**：使用多态或策略模式

```kotlin
// 坏味道：ObjectDetectorHelper 中的模型选择 if-else
// 处理：使用工厂模式
interface DetectorFactory {
    fun create(context: Context): ObjectDetector
    fun supports(modelType: Int): Boolean
}

class DetectorManager(factories: List<DetectorFactory>) {
    fun createDetector(modelType: Int, context: Context): ObjectDetector {
        return factories.first { it.supports(modelType) }.create(context)
    }
}
```

### 12. 循环语句
- **问题**：循环体内逻辑复杂
- **处理**：提取循环体为函数，或使用函数式操作

```kotlin
// 坏味道
for (obj in detections) {
    val label = obj.label
    if (label == null) continue
    if (label.startsWith("meter_")) {
        meters.put(label, obj)
    } else if (label.startsWith("needle_")) {
        needles.add(obj)
    } else if (label.startsWith("scale_")) {
        scales.add(obj)
    }
    // ... 更多逻辑
}

// 重构：使用 groupBy
grouped = detections.groupBy { obj ->
    when {
        obj.label?.startsWith("meter_") == true -> "meter"
        obj.label?.startsWith("needle_") == true -> "needle"
        obj.label?.startsWith("scale_") == true -> "scale"
        else -> "other"
    }
}
```

## Android 特定规范

### 生命周期管理
```kotlin
// 推荐：使用 viewLifecycleOwner 避免内存泄漏
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    viewModel.uiState
        .flowWithLifecycle(viewLifecycleOwner.lifecycle)
        .onEach { updateUI(it) }
        .launchIn(viewLifecycleOwner.lifecycleScope)
}
```

### 资源管理
```kotlin
// 推荐：使用 use 自动关闭资源
context.contentResolver.openInputStream(uri)?.use { stream ->
    // 处理流
}

// 推荐：协程作用域自动取消
lifecycleScope.launch {
    // 异步操作，自动处理生命周期
}
```

### 性能优化
```kotlin
// 推荐：避免在 onDraw 中创建对象
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    // 复用 Paint 对象，不在 onDraw 中创建
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    override fun onDraw(canvas: Canvas) {
        // 直接使用预创建的 Paint
        canvas.drawRect(rect, boxPaint)
    }
}
```

## 检测器实现规范

### 模型配置
```kotlin
// 推荐：使用密封类定义模型类型
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

### 检测结果处理
```kotlin
// 推荐：使用数据类封装检测结果
data class DetectionResult(
    val processedImage: Bitmap,
    val detections: List<ObjectDetection>,
    val inferenceTime: Long,
    val info: String = ""
) {
    val detectionCount: Int get() = detections.size
    val hasDetections: Boolean get() = detections.isNotEmpty()
}
```

## JNI 与 Native 代码规范

### JNI 函数命名
```cpp
// 规范：Java_包名_类名_方法名
extern "C" JNIEXPORT void JNICALL
Java_com_ultralytics_yolo_ImageProcessing_argb2yolo(
    JNIEnv* env,
    jobject thiz,
    jintArray argb,
    jint width,
    jint height,
    jbyteArray yolo
)
```

### 资源释放
```cpp
// 推荐：使用 RAII 管理 JNI 引用
class JniIntArray {
    JNIEnv* env;
    jintArray array;
    jint* ptr;
public:
    JniIntArray(JNIEnv* e, jintArray arr) : env(e), array(arr) {
        ptr = env->GetIntArrayElements(array, nullptr);
    }
    ~JniIntArray() {
        env->ReleaseIntArrayElements(array, ptr, 0);
    }
    jint* get() { return ptr; }
};
```

## 测试规范

### 单元测试
```kotlin
@Test
fun `detect with empty image returns empty result`() {
    val detector = YoloDetector(context)
    val emptyBitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
    
    val result = detector.detect(TensorImage.fromBitmap(emptyBitmap), 0)
    
    assertThat(result.detections).isEmpty()
}
```

### 集成测试
```kotlin
@Test
fun `end-to-end detection with test image`() {
    // 加载测试图片
    val testBitmap = loadTestAsset("test_meter.jpg")
    
    // 执行检测
    val result = detector.detect(TensorImage.fromBitmap(testBitmap), 0)
    
    // 验证结果
    assertThat(result.detections).isNotEmpty()
    assertThat(result.detections.first().category.confidence).isGreaterThan(0.5f)
}
```

## 文档规范

### KDoc 注释
```kotlin
/**
 * 处理检测结果，计算仪表读数
 *
 * @param detections 检测到的对象列表
 * @return 仪表读数列表，如果没有检测到仪表则返回空列表
 * @throws IllegalArgumentException 如果 detections 包含 null 元素
 */
fun processDetections(detections: List<DetectedObject>): List<MeterReading> {
    // 实现
}
```

### 复杂逻辑注释
```kotlin
// 计算指针角度：使用 atan2 获取指针相对于仪表中心的角度
// 注意：YOLO 输出的角度是弧度，需要转换为度
val angleDegrees = Math.toDegrees(atan2(dy, dx)).toFloat()

// 将角度归一化到 0-360 范围，便于后续计算读数
val normalizedAngle = (angleDegrees + 360) % 360
```
