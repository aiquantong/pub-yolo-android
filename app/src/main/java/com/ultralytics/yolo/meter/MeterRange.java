package com.ultralytics.yolo.meter;

/**
 * 仪表量程定义
 */
public class MeterRange {
    public final float min;
    public final float max;

    public final float min_angle;
    public final float max_angle;
    public final String unit;

    public MeterRange(float min, float max, float min_angle, float max_angle, String unit) {
        this.min = min;
        this.max = max;
        this.min_angle = min_angle;
        this.max_angle = max_angle;
        this.unit = unit;
    }
}
