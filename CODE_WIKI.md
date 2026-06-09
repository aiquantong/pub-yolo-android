# YOLOv11 Android 物体检测项目 - Code Wiki

## 1. 项目概述

本项目是一个基于 **TensorFlow Lite** 的 Android 实时物体检测应用，集成了 **Ultralytics YOLOv11** 模型。项目源自两个主要代码库：
- [TensorFlow Lite Object Detection Android Demo](https://github.com/tensorflow/examples/tree/master/lite/examples/object_detection/android)
- [Ultralytics Flutter demo app](https://github.com/ultralytics/yolo-flutter-app)

### 主要特性

| 特性 | 描述 |
|------|------|
| 多模型支持 | MobileNetV1、EfficientDet Lite 0/1/2、YOLOv11 |
| 硬件加速 | CPU、GPU、NNAPI 三种推理后端 |
| 实时检测 | 基于 CameraX 的实时摄像头画面检测 |
| 可调参数 | 置信度阈值、最大检测数量、线程数等 |

---

## 2. 项目架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用层 (UI)                             │
│  MainActivity  →  CameraFragment  →  PermissionsFragment       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    检测协调层                                   │
│              ObjectDetectorHelper                              │
│      (配置管理、检测器创建、结果回调)                            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                 ▼
┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
│   YoloDetector    │ │ TaskVisionDetector│ │   OverlayView     │
│   (YOLOv11)       │ │ (MobileNet/EffDet)│ │   (结果绘制)       │
└─────────┬─────────┘ └───────────────────┘ └───────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Ultralytics YOLO 核心层                      │
│  TfliteDetector  →  Predictor  →  Detector  →  PostProcessUtils│
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        模型层                                   │
│              YoloModel  →  LocalYoloModel                      │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责划分

| 模块 | 包路径 | 职责 |
|------|--------|------|
| **UI层** | `org.tensorflow.lite.examples.objectdetection` | 界面展示、相机控制、用户交互 |
| **检测协调** | `org.tensorflow.lite.examples.objectdetection` | 检测器配置、生命周期管理 |
| **检测器接口** | `org.tensorflow.lite.examples.objectdetection.detectors` | 检测算法抽象接口 |
| **YOLO核心** | `com.ultralytics.yolo` | YOLOv11模型加载、推理、后处理 |
| **工具类** | `com.ultralytics.yolo` | 图像预处理、格式转换 |

---

## 3. 核心类与函数说明

### 3.1 UI层

#### MainActivity
- **路径**: [app/src/main/java/org/tensorflow/lite/examples/objectdetection/MainActivity.kt](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/org/tensorflow/lite/examples/objectdetection/MainActivity.kt)
- **职责**: 应用入口，采用单Activity架构，承载Fragment容器
- **关键方法**:
  - `onCreate()`: 初始化DataBinding，设置布局
  - `onBackPressed()`: 处理Android Q的内存泄漏问题

#### CameraFragment
- **路径**: [app/src/main/java/org/tensorflow/lite/examples/objectdetection/fragments/CameraFragment.kt](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/org/tensorflow/lite/examples/objectdetection/fragments/CameraFragment.kt)
- **职责**: 相机预览、图像分析、检测结果展示
- **关键方法**:
  - `setUpCamera()`: 初始化CameraX，绑定相机用例
  - `bindCameraUseCases()`: 配置预览、分析用例
  - `detectObjects()`: 处理相机帧，调用检测器
  - `onResults()`: 接收检测结果，更新UI

### 3.2 检测协调层

#### ObjectDetectorHelper
- **路径**: [app/src/main/java/org/tensorflow/lite/examples/objectdetection/ObjectDetectorHelper.kt](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/org/tensorflow/lite/examples/objectdetection/ObjectDetectorHelper.kt)
- **职责**: 检测器工厂、配置管理、检测执行
- **核心配置参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `threshold` | Float | 0.5f | 置信度阈值 |
| `numThreads` | Int | 2 | 推理线程数 |
| `maxResults` | Int | 3 | 最大检测数量 |
| `currentDelegate` | Int | 0 | 推理后端 (0=CPU,1=GPU,2=NNAPI) |
| `currentModel` | Int | 0 | 模型选择 (0-4对应不同模型) |

- **关键方法**:
  - `setupObjectDetector()`: 根据配置创建对应检测器
  - `detect()`: 执行检测流程（预处理→推理→回调）
  - `clearObjectDetector()`: 释放检测器资源

### 3.3 检测器接口层

#### ObjectDetector (接口)
- **路径**: [app/src/main/java/org/tensorflow/lite/examples/objectdetection/detectors/ObjectDetector.kt](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/org/tensorflow/lite/examples/objectdetection/detectors/ObjectDetector.kt)
- **职责**: 定义检测接口契约
- **核心方法**:
  - `detect(image: TensorImage, imageRotation: Int): DetectionResult`

#### 数据类定义

| 类名 | 字段 | 说明 |
|------|------|------|
| `Category` | `label: String`, `confidence: Float` | 检测类别信息 |
| `ObjectDetection` | `boundingBox: RectF`, `category: Category` | 单个检测结果 |
| `DetectionResult` | `image: Bitmap`, `detections: List<ObjectDetection>`, `info: Any?` | 检测结果集合 |

#### YoloDetector
- **路径**: [app/src/main/java/org/tensorflow/lite/examples/objectdetection/detectors/YoloDetector.kt](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/org/tensorflow/lite/examples/objectdetection/detectors/YoloDetector.kt)
- **职责**: YOLOv11模型的检测器实现
- **关键特性**:
  - 加载本地TFLite模型 (`best_float16.tflite`)
  - 解析YAML元数据文件获取类别标签
  - 支持置信度和IoU阈值配置

#### TaskVisionDetector
- **路径**: [app/src/main/java/org/tensorflow/lite/examples/objectdetection/detectors/TaskVisionDetector.kt](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/org/tensorflow/lite/examples/objectdetection/detectors/TaskVisionDetector.kt)
- **职责**: TensorFlow Lite Task Library实现的检测器
- **支持模型**:
  - `MODEL_MOBILENETV1`: mobilenetv1.tflite
  - `MODEL_EFFICIENTDETV0`: efficientdet-lite0.tflite
  - `MODEL_EFFICIENTDETV1`: efficientdet-lite1.tflite
  - `MODEL_EFFICIENTDETV2`: efficientdet-lite2.tflite

### 3.4 Ultralytics YOLO核心层

#### TfliteDetector
- **路径**: [app/src/main/java/com/ultralytics/yolo/predict/detect/TfliteDetector.java](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/com/ultralytics/yolo/predict/detect/TfliteDetector.java)
- **职责**: YOLOv11 TFLite模型推理核心
- **关键方法**:
  - `loadModel()`: 加载模型文件和标签
  - `initDelegate()`: 初始化推理后端（GPU/CPU）
  - `preprocess()`: 图像预处理（缩放至INPUT_SIZE）
  - `predict()`: 执行推理并返回检测结果
  - `setInputOptim()`: 优化的输入数据准备

#### Predictor (抽象类)
- **路径**: [app/src/main/java/com/ultralytics/yolo/predict/Predictor.java](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/com/ultralytics/yolo/predict/Predictor.java)
- **职责**: 预测器基类，定义核心接口
- **核心字段**:
  - `INPUT_SIZE`: 模型输入尺寸（默认320，从YAML读取）
  - `labels`: 类别标签列表
- **关键方法**:
  - `loadLabels()`: 从YAML文件加载类别标签

#### Detector (抽象类)
- **路径**: [app/src/main/java/com/ultralytics/yolo/predict/detect/Detector.java](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/com/ultralytics/yolo/predict/detect/Detector.java)
- **职责**: 检测器基类，扩展Predictor

#### PostProcessUtils
- **路径**: [app/src/main/java/com/ultralytics/yolo/predict/detect/PostProcessUtils.java](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/com/ultralytics/yolo/predict/detect/PostProcessUtils.java)
- **职责**: YOLO输出后处理工具类
- **核心算法**:
  - `qsortDescentInplace()`: 快速排序（按置信度降序）
  - `nmsSortedBboxes()`: 非极大值抑制(NMS)
  - `postprocess()`: 完整后处理流程

### 3.5 模型层

#### YoloModel (抽象类)
- **路径**: [app/src/main/java/com/ultralytics/yolo/models/YoloModel.java](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/com/ultralytics/yolo/models/YoloModel.java)
- **字段**: `task`, `format`, `labels`

#### LocalYoloModel
- **路径**: [app/src/main/java/com/ultralytics/yolo/models/LocalYoloModel.java](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/com/ultralytics/yolo/models/LocalYoloModel.java)
- **字段**: `modelPath`, `metadataPath`

### 3.6 工具类

#### ImageProcessing
- **路径**: [app/src/main/java/com/ultralytics/yolo/ImageProcessing.java](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/com/ultralytics/yolo/ImageProcessing.java)
- **职责**: JNI图像预处理（ARGB转YOLO格式）
- **关键方法**: `argb2yolo()` - 调用Native库进行像素转换

#### ImageUtils
- **路径**: [app/src/main/java/com/ultralytics/yolo/ImageUtils.java](file:///Users/aiquantong/Documents/androidOpenSource/pub-yolo-android/app/src/main/java/com/ultralytics/yolo/ImageUtils.java)
- **职责**: 图像格式转换工具
- **关键方法**:
  - `toBitmap()`: ImageProxy转Bitmap
  - `getTransformationMatrix()`: 图像变换矩阵计算

---

## 4. 依赖关系

### 4.1 外部依赖

| 依赖 | GroupId | ArtifactId | Version | 用途 |
|------|---------|------------|---------|------|
| TensorFlow Lite | org.tensorflow | tensorflow-lite | 2.16.1 | 核心推理引擎 |
| TFLite GPU | org.tensorflow | tensorflow-lite-gpu | 2.16.1 | GPU加速 |
| TFLite Task Vision | org.tensorflow | tensorflow-lite-task-vision | 0.4.4 | 高级API封装 |
| CameraX Core | androidx.camera | camera-core | 1.4.1 | 相机框架 |
| CameraX Camera2 | androidx.camera | camera-camera2 | 1.4.1 | Camera2扩展 |
| CameraX Lifecycle | androidx.camera | camera-lifecycle | 1.4.1 | 生命周期管理 |
| CameraX View | androidx.camera | camera-view | 1.4.1 | 预览视图 |
| Navigation | androidx.navigation | navigation-fragment-ktx | 2.9.3 | Fragment导航 |
| SnakeYAML | org.yaml | snakeyaml | 1.29 | YAML解析 |

### 4.2 模块依赖关系

```
MainActivity
    └── CameraFragment
            ├── ObjectDetectorHelper
            │       ├── YoloDetector
            │       │       └── TfliteDetector
            │       │               ├── Predictor
            │       │               ├── Detector
            │       │               ├── PostProcessUtils
            │       │               └── ImageProcessing (JNI)
            │       └── TaskVisionDetector
            └── OverlayView
```

---

## 5. 项目运行方式

### 5.1 环境要求

| 项目 | 要求 |
|------|------|
| Android Studio | Bumblebee 及以上 |
| 最小SDK | 26 (Android 8.0) |
| 目标SDK | 33 |
| 编译SDK | 35 |

### 5.2 构建步骤

1. **打开项目**: 在Android Studio中打开 `pub-yolo-android` 目录
2. **Gradle同步**: 等待Gradle同步完成（模型文件会自动下载）
3. **连接设备**: 连接Android设备并启用开发者模式
4. **运行**: 点击Run按钮部署应用

### 5.3 模型文件

应用支持以下预训练模型：

| 模型名称 | 文件 | 来源 |
|----------|------|------|
| MobileNetV1 | `mobilenetv1.tflite` | TensorFlow Hub |
| EfficientDet Lite 0 | `efficientdet-lite0.tflite` | TensorFlow Hub |
| EfficientDet Lite 1 | `efficientdet-lite1.tflite` | TensorFlow Hub |
| EfficientDet Lite 2 | `efficientdet-lite2.tflite` | TensorFlow Hub |
| YOLOv11n | `best_float16.tflite` | Ultralytics |
| YOLOv11n (备用) | `yolo11n_float32.tflite` | Ultralytics |

模型文件位于 `app/src/main/assets/` 目录，由 `download_models.gradle` 脚本自动下载。

### 5.4 配置说明

#### 推理后端选择

| 值 | 后端 | 说明 |
|----|------|------|
| 0 | CPU | 默认，兼容性最好 |
| 1 | GPU | 性能最佳，需硬件支持 |
| 2 | NNAPI | 利用设备AI加速器 |

#### 模型选择

| 值 | 模型 | 特点 |
|----|------|------|
| 0 | MobileNetV1 | 轻量级，速度快 |
| 1 | EfficientDet Lite 0 | 平衡速度与精度 |
| 2 | EfficientDet Lite 1 | 精度更高 |
| 3 | EfficientDet Lite 2 | 精度最高 |
| 4 | YOLOv11 | 最新YOLO模型 |

---

## 6. 核心工作流程

### 6.1 检测流程

```
相机帧采集 → 图像预处理 → TFLite推理 → 后处理(NMS) → 结果绘制
     │              │              │              │           │
     ▼              ▼              ▼              ▼           ▼
 CameraX      preprocess()    runForMultiple   postprocess()  OverlayView
 ImageAnalysis   缩放至320x320  InputsOutputs    NMS过滤      绘制 bounding box
```

### 6.2 数据流转

1. **CameraFragment** 获取相机帧 (`ImageProxy`)
2. 转换为 `Bitmap`，传递给 `ObjectDetectorHelper`
3. `ObjectDetectorHelper` 根据配置选择检测器
   - YOLO模型 → `YoloDetector` → `TfliteDetector`
   - 其他模型 → `TaskVisionDetector`
4. 检测器返回 `DetectionResult`
5. `CameraFragment` 更新UI，`OverlayView` 绘制检测框

---

## 7. 关键技术点

### 7.1 YOLO输出格式

YOLOv11的输出张量形状为 `[1, 8400, 84]`（COCO数据集）：
- 8400: 检测框数量
- 84: 4个坐标 + 80个类别概率

### 7.2 NMS算法流程

1. 遍历所有检测框，筛选置信度高于阈值的候选框
2. 按置信度降序排序
3. 对每个候选框，计算与已选框的IoU
4. IoU高于阈值则剔除，保留最优框

### 7.3 GPU加速配置

```java
CompatibilityList compatibilityList = new CompatibilityList();
if (useGpu && compatibilityList.isDelegateSupportedOnThisDevice()) {
    GpuDelegateFactory.Options delegateOptions = 
        compatibilityList.getBestOptionsForThisDevice();
    GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions
        .setQuantizedModelsAllowed(true));
    interpreterOptions.addDelegate(gpuDelegate);
}
```

---

## 8. 扩展指南

### 8.1 添加自定义YOLO模型

1. 将 `.tflite` 模型文件放入 `app/src/main/assets/`
2. 创建对应的YAML元数据文件（包含 `names` 和 `imgsz` 字段）
3. 修改 `YoloDetector.kt` 中的模型路径配置

### 8.2 性能优化建议

| 优化项 | 建议 |
|--------|------|
| 线程数 | 根据设备配置调整（推荐2-4） |
| 输入尺寸 | 较小尺寸提升速度，降低精度 |
| 置信度阈值 | 较高阈值减少后处理负担 |
| GPU加速 | 支持设备优先使用GPU |

---

## 9. 许可证

项目遵循双重许可证：
- **TensorFlow Lite Demo**: [Apache License 2.0](LICENSE-Apache2.0.txt)
- **Ultralytics YOLO**: [GNU General Public License](LICENSE)

使用本项目代码需同时遵守上述两个许可证。