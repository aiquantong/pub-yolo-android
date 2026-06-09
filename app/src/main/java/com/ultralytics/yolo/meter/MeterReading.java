package com.ultralytics.yolo.meter;

import android.graphics.RectF;

/**
 * 仪表读数结果
 */
public class MeterReading {
    public String meterType;          // 仪表类型
    public float reading;             // 读数
    public float confidence;          // 置信度
    public RectF meterBoundingBox;    // 仪表边界框
    public String readingString;      // 读数字符串表示
    public long timestamp;            // 时间戳

    public MeterReading(String meterType, float reading, float confidence, RectF meterBoundingBox) {
        this.meterType = meterType;
        this.reading = reading;
        this.confidence = confidence;
        this.meterBoundingBox = meterBoundingBox;
        this.readingString = String.format("%.2f", reading);
        this.timestamp = System.currentTimeMillis();
    }

    public String getDisplayText() {
        return meterType + ": " + readingString;
    }
}
