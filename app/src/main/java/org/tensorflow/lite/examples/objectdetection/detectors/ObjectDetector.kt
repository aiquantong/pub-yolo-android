package org.tensorflow.lite.examples.objectdetection.detectors

import android.graphics.Bitmap
import android.graphics.RectF
import com.ultralytics.yolo.predict.detect.DetectedObject
import org.tensorflow.lite.support.image.TensorImage

class Category (
    val label: String,
    val confidence: Float
)

class ObjectDetection(
    val boundingBox: RectF,
    val category: Category,
    var angle: Float = 0.0f,
)

class DetectionResult(
    val image: Bitmap,
    val detections: List<ObjectDetection>,
    var info: Any?=null,
    var rawDetectedObjects: List<DetectedObject>?=null
)

interface ObjectDetector {
    fun detect(image: TensorImage, imageRotation: Int): DetectionResult
}
