/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import com.ultralytics.yolo.meter.MeterReading
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import java.util.LinkedList
import kotlin.math.max
import org.tensorflow.lite.task.vision.detector.Detection

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<ObjectDetection> = LinkedList<ObjectDetection>()
    private var meterReadings: List<MeterReading> = LinkedList<MeterReading>()
    private var boxPaint = Paint()
    private var obbPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var meterPaint = Paint()
    private var meterTextPaint = Paint()

    private var scaleFactor: Float = 1f

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        obbPaint.reset()
        meterPaint.reset()
        meterTextPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 40f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 40f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 4F
        boxPaint.style = Paint.Style.STROKE

        obbPaint.color = Color.GREEN
        obbPaint.strokeWidth = 8F
        obbPaint.style = Paint.Style.STROKE
        obbPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK)

        // 仪表读数画笔
        meterPaint.color = Color.parseColor("#FF5722")
        meterPaint.strokeWidth = 12f
        meterPaint.style = Paint.Style.STROKE
        meterPaint.setShadowLayer(6f, 0f, 0f, Color.parseColor("#80000000"))

        meterTextPaint.color = Color.WHITE
        meterTextPaint.style = Paint.Style.FILL
        meterTextPaint.textSize = 50f
        meterTextPaint.isFakeBoldText = true
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // 先绘制检测框
        for (result in results) {
            val boundingBox = result.boundingBox
            val angle = result.angle

            val top = boundingBox.top * scaleFactor
            val bottom = boundingBox.bottom * scaleFactor
            val left = boundingBox.left * scaleFactor
            val right = boundingBox.right * scaleFactor

            val width = right - left
            val height = bottom - top
            val centerX = left + width / 2
            val centerY = top + height / 2

            // Draw OBB (Oriented Bounding Box) if angle is not zero
            if (angle > 0.1f) {
                // Save canvas state
                canvas.withTranslation(centerX, centerY) {
                    // Translate to center of bounding box
                    // Rotate by the angle (convert radians to degrees if needed)
                    val rotationDegrees = Math.toDegrees(angle.toDouble()).toFloat()
                    rotate(rotationDegrees)

                    // Draw rotated rectangle centered at origin
                    val halfWidth = width / 2
                    val halfHeight = height / 2
                    val rotatedRect = RectF(-halfWidth, -halfHeight, halfWidth, halfHeight)
                    drawRect(rotatedRect, obbPaint)
                    // Restore canvas state
                }
            } else {
                // Draw regular bounding box for non-rotated objects
                val drawableRect = RectF(left, top, right, bottom)
                canvas.drawRect(drawableRect, boxPaint)
            }

            // Create text to display alongside detected objects
            val drawableText =
                result.category.label + " " +
                        String.format("%.2f", result.category.confidence) +
                        if (angle != 0f) " (${String.format("%.1f", Math.toDegrees(angle.toDouble()))}°)" else ""

            // Draw rect behind display text
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top - textHeight - Companion.BOUNDING_RECT_TEXT_PADDING,
                left + textWidth + Companion.BOUNDING_RECT_TEXT_PADDING,
                top,
                textBackgroundPaint
            )

            // Draw text for detected object
            canvas.drawText(drawableText, left, top - Companion.BOUNDING_RECT_TEXT_PADDING, textPaint)
        }

        // 绘制仪表读数
        for (reading in meterReadings) {
            val meterBox = reading.meterBoundingBox
            val top = meterBox.top * scaleFactor
            val bottom = meterBox.bottom * scaleFactor
            val left = meterBox.left * scaleFactor
            val right = meterBox.right * scaleFactor

            // 绘制仪表高亮框
            val meterRect = RectF(left - 10, top - 10, right + 10, bottom + 10)
            canvas.drawRect(meterRect, meterPaint)

            // 绘制读数文本
            val displayText = reading.readingString
            meterTextPaint.getTextBounds(displayText, 0, displayText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()

            // 绘制文本背景
            val bgRect = RectF(
                left - 5,
                bottom + 5,
                left + textWidth + 20,
                bottom + textHeight + 30
            )
            canvas.drawRoundRect(bgRect, 10f, 10f, textBackgroundPaint)

            // 绘制读数
            canvas.drawText(displayText, left, bottom + textHeight + 15, meterTextPaint)

            // 绘制类型标签
            val typeText = reading.meterType
            meterTextPaint.textSize = 30f
            meterTextPaint.getTextBounds(typeText, 0, typeText.length, bounds)
            canvas.drawText(typeText, left, top - 10, meterTextPaint)
            meterTextPaint.textSize = 50f
        }
    }

    fun setResults(
        detectionResults: List<ObjectDetection>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        // PreviewView is in FILL_START mode. So we need to scale up the bounding box to match with
        // the size that the captured images will be displayed.
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
    }

    fun setMeterReadings(readings: List<MeterReading>) {
        meterReadings = readings
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
