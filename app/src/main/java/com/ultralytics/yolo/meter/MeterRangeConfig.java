/*
 * @Author: aiquantong aiquantong@163.com
 * @Date: 2026-06-08 17:14:53
 * @LastEditors: aiquantong aiquantong@163.com
 * @LastEditTime: 2026-06-09 22:29:05
 * @FilePath: /pub-yolo-android/app/src/main/java/com/ultralytics/yolo/meter/MeterRangeConfig.java
 * @Description: 这是默认设置,请设置`customMade`, 打开koroFileHeader查看配置 进行设置: https://github.com/OBKoro1/koro1FileHeader/wiki/%E9%85%8D%E7%BD%AE
 */
package com.ultralytics.yolo.meter;

/**
 * 仪表量程定义
 */
public class MeterRangeConfig {
    public final float min;
    public final float max;

    public final float min_angle;
    public final float max_angle;
    public final String unit;

    public MeterRangeConfig(float min, float max, float min_angle, float max_angle, String unit) {
        this.min = min;
        this.max = max;
        this.min_angle = min_angle;
        this.max_angle = max_angle;
        this.unit = unit;
    }
}
