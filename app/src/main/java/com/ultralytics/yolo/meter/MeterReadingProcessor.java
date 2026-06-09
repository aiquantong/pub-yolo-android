package com.ultralytics.yolo.meter;

import android.graphics.RectF;
import android.util.Log;

import com.ultralytics.yolo.predict.detect.DetectedObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表读数处理器
 * 处理检测到的仪表元素并计算读数
 */
public class MeterReadingProcessor {
    private static final String TAG = "MeterReadingProcessor";

    // 仪表范围定义
    private static final Map<String, MeterRangeConfig> METER_RANGES = new HashMap<>();

    static {
        // 压力仪表
        METER_RANGES.put("meter_p_0-1N6", new MeterRangeConfig(0.0f, 1.6f, "bar"));
        METER_RANGES.put("meter_p_0-1", new MeterRangeConfig(0.0f, 1.0f, "bar"));
        METER_RANGES.put("meter_p_0-2N5", new MeterRangeConfig(0.0f, 2.5f, "bar"));

        // 温度仪表
        METER_RANGES.put("meter_t_-20-80", new MeterRangeConfig(-20f, 80f, "°C"));
        METER_RANGES.put("meter_t_0-100", new MeterRangeConfig(0f, 100f, "°C"));
        METER_RANGES.put("meter_t_0-120", new MeterRangeConfig(0f, 120f, "°C"));
        METER_RANGES.put("meter_t_0-50", new MeterRangeConfig(0f, 
    }

    /**
     * 处理检测结果，计算仪表读数
     */
    public static List<MeterReading> processDetections(List<DetectedObject> detections) {
        List<MeterReading> readings = new ArrayList<>();

        if (detections == null || detections.isEmpty()) {
            return readings;
        }

        // 分组检测结果
        Map<String, DetectedObject> meters = new HashMap<>();
        List<DetectedObject> needles = new ArrayList<>();
        List<DetectedObject> scales = new ArrayList<>();
        List<DetectedObject> numbers = new ArrayList<>();
        List<DetectedObject> numNodes = new ArrayList<>();

        for (DetectedObject obj : detections) {
            String label = obj.label;
            if (label == null) continue;

            if (label.startsWith("meter_")) {
                meters.put(label, obj);
            } else if (label.startsWith("needle_")) {
                needles.add(obj);
            } else if (label.startsWith("scale_")) {
                scales.add(obj);
            } else if (label.startsWith("num_")) {
                if (label.equals("num_node")) {
                    numNodes.add(obj);
                } else {
                    numbers.add(obj);
                }
            }
        }

        Log.d(TAG, "检测到 - 仪表: " + meters.size() + 
                   ", 指针: " + needles.size() + 
                   ", 刻度: " + scales.size() + 
                   ", 数字: " + numbers.size());

        // 处理每个仪表
        for (Map.Entry<String, DetectedObject> entry : meters.entrySet()) {
            String meterType = entry.getKey();
            DetectedObject meter = entry.getValue();

            MeterReading reading = processMeter(meterType, meter, needles, scales, numbers, numNodes);
            if (reading != null) {
                readings.add(reading);
            }
        }

        // 处理数字仪表（如果有数字节点）
        if (!numbers.isEmpty() || !numNodes.isEmpty()) {
            MeterReading digitalReading = processDigitalMeter(numbers, numNodes);
            if (digitalReading != null) {
                readings.add(digitalReading);
            }
        }

        return readings;
    }

    /**
     * 处理单个指针式仪表
     */
    private static MeterReading processMeter(
            String meterType,
            DetectedObject meter,
            List<DetectedObject> needles,
            List<DetectedObject> scales,
            List<DetectedObject> numbers,
            List<DetectedObject> numNodes) {

        RectF meterBox = meter.boundingBox;

        // 查找与仪表关联的指针
        DetectedObject bestNeedle = findBestNeedle(meterBox, needles);
        if (bestNeedle == null) {
            Log.d(TAG, "未找到与 " + meterType + " 关联的指针");
            return null;
        }

        // 计算读数
        float reading = calculateReading(meterType, meterBox, bestNeedle, scales);
        float confidence = Math.min(meter.confidence, bestNeedle.confidence);

        return new MeterReading(meterType, reading, confidence, meterBox);
    }

    /**
     * 计算指针式仪表读数
     */
    private static float calculateReading(
            String meterType,
            RectF meterBox,
            DetectedObject needle,
            List<DetectedObject> scales) {

        // 获取仪表范围
        MeterRangeConfig range = METER_RANGES.get(meterType);
        if (range == null) {
            // 默认范围
            range = new MeterRangeConfig(0, 100, 0, 180, "unit");
        }

        RectF needleBox = needle.boundingBox;

        // 计算仪表中心点
        float centerX = (meterBox.left + meterBox.right) / 2;
        float centerY = (meterBox.top + meterBox.bottom) / 2;

        // 计算指针末端点（使用指针框的末端）
        float needleEndX = (needleBox.left + needleBox.right) / 2;
        float needleEndY = needleBox.top; // 假设指针指向上方

        // 计算指针角度
        float angle = calculateAngle(centerX, centerY, needleEndX, needleEndY);

        // 基于角度计算读数（简化版本）
        // 假设仪表是圆形的，0度是最小值，180度是最大值
        float normalizedAngle = angle / 180.0f;
        normalizedAngle = Math.max(0, Math.min(1, normalizedAngle));

        float reading = range.min + normalizedAngle * (range.max - range.min);

        // 使用刻度信息优化读数
        if (!scales.isEmpty()) {
            reading = refineReadingWithScales(reading, meterBox, needleBox, scales, range);
        }

        return reading;
    }

    /**
     * 使用刻度信息优化读数
     */
    private static float refineReadingWithScales(
            float initialReading,
            RectF meterBox,
            RectF needleBox,
            List<DetectedObject> scales,
            MeterRange range) {

        // 简化实现：基于刻度位置优化
        float meterWidth = meterBox.right - meterBox.left;
        float meterHeight = meterBox.bottom - meterBox.top;

        float needleCenterX = (needleBox.left + needleBox.right) / 2;
        float needleCenterY = (needleBox.top + needleBox.bottom) / 2;

        // 找到离指针最近的刻度
        DetectedObject nearestScale = null;
        float minDistance = Float.MAX_VALUE;

        for (DetectedObject scale : scales) {
            RectF scaleBox = scale.boundingBox;
            float scaleCenterX = (scaleBox.left + scaleBox.right) / 2;
            float scaleCenterY = (scaleBox.top + scaleBox.bottom) / 2;

            float distance = (float) Math.sqrt(
                    Math.pow(needleCenterX - scaleCenterX, 2) +
                    Math.pow(needleCenterY - scaleCenterY, 2));

            if (distance < minDistance) {
                minDistance = distance;
                nearestScale = scale;
            }
        }

        // 如果有附近的刻度，基于刻度位置调整
        if (nearestScale != null) {
            // 这里实现更精细的刻度插值逻辑
            Log.d(TAG, "最近刻度: " + nearestScale.label);
        }

        return initialReading;
    }

    /**
     * 计算两点之间的角度
     */
    private static float calculateAngle(float cx, float cy, float px, float py) {
        float dx = px - cx;
        float dy = py - cy;

        // 计算弧度并转换为角度
        double angleRadians = Math.atan2(dx, -dy); // 注意：y轴是反的
        double angleDegrees = Math.toDegrees(angleRadians);

        // 归一化到 0-180 度范围
        if (angleDegrees < 0) {
            angleDegrees += 360;
        }
        if (angleDegrees > 180) {
            angleDegrees = 360 - angleDegrees;
        }

        return (float) angleDegrees;
    }

    /**
     * 查找与仪表关联的最佳指针
     */
    private static DetectedObject findBestNeedle(RectF meterBox, List<DetectedObject> needles) {
        if (needles.isEmpty()) {
            return null;
        }

        DetectedObject bestNeedle = null;
        float maxOverlap = 0;

        float meterCenterX = (meterBox.left + meterBox.right) / 2;
        float meterCenterY = (meterBox.top + meterBox.bottom) / 2;

        for (DetectedObject needle : needles) {
            RectF needleBox = needle.boundingBox;
            float needleCenterX = (needleBox.left + needleBox.right) / 2;
            float needleCenterY = (needleBox.top + needleBox.bottom) / 2;

            // 计算中心点距离
            float distance = (float) Math.sqrt(
                    Math.pow(meterCenterX - needleCenterX, 2) +
                    Math.pow(meterCenterY - needleCenterY, 2));

            // 距离越近越好
            float overlapScore = 1.0f / (1.0f + distance * 10);

            if (overlapScore > maxOverlap) {
                maxOverlap = overlapScore;
                bestNeedle = needle;
            }
        }

        return bestNeedle;
    }

    /**
     * 处理数字仪表
     */
    private static MeterReading processDigitalMeter(
            List<DetectedObject> numbers,
            List<DetectedObject> numNodes) {

        if (numbers.isEmpty() && numNodes.isEmpty()) {
            return null;
        }

        // 按X坐标排序数字
        Collections.sort(numbers, new Comparator<DetectedObject>() {
            @Override
            public int compare(DetectedObject o1, DetectedObject o2) {
                float x1 = (o1.boundingBox.left + o1.boundingBox.right) / 2;
                float x2 = (o2.boundingBox.left + o2.boundingBox.right) / 2;
                return Float.compare(x1, x2);
            }
        });

        // 拼接数字
        StringBuilder sb = new StringBuilder();
        float totalConfidence = 0;

        for (DetectedObject num : numbers) {
            String label = num.label;
            if (label.startsWith("num_") && label.length() > 4) {
                char digit = label.charAt(4);
                if (Character.isDigit(digit)) {
                    sb.append(digit);
                    totalConfidence += num.confidence;
                }
            }
        }

        if (sb.length() > 0) {
            try {
                float value = Float.parseFloat(sb.toString());
                float avgConfidence = totalConfidence / numbers.size();

                // 计算整体边界框
                RectF overallBox = calculateOverallBounds(numbers);

                return new MeterReading("meter_p_digital", value, avgConfidence, overallBox);
            } catch (NumberFormatException e) {
                Log.e(TAG, "解析数字失败: " + sb.toString());
            }
        }

        return null;
    }

    /**
     * 计算多个检测框的整体边界
     */
    private static RectF calculateOverallBounds(List<DetectedObject> objects) {
        if (objects.isEmpty()) {
            return new RectF(0, 0, 0, 0);
        }

        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = Float.MIN_VALUE;
        float bottom = Float.MIN_VALUE;

        for (DetectedObject obj : objects) {
            RectF box = obj.boundingBox;
            left = Math.min(left, box.left);
            top = Math.min(top, box.top);
            right = Math.max(right, box.right);
            bottom = Math.max(bottom, box.bottom);
        }

        return new RectF(left, top, right, bottom);
    }

}
