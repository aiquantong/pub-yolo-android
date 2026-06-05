package com.ultralytics.yolo.predict.detect;

import android.graphics.RectF;

import androidx.annotation.Keep;

public class DetectedObject {
    public final Float confidence;
    public final RectF boundingBox;
    public final int index;
    public final String label;
    public final float angle;

    public DetectedObject(
            final Float confidence,
            final RectF boundingBox,
            final int index,
            final String label
    ) {
        this.confidence = confidence;
        this.boundingBox = boundingBox;
        this.index = index;
        this.label = label;
        this.angle = 0f;
    }

    public DetectedObject(
            final Float confidence,
            final RectF boundingBox,
            final int index,
            final String label,
            final float angle
    ) {
        this.confidence = confidence;
        this.boundingBox = boundingBox;
        this.index = index;
        this.label = label;
        this.angle = angle;
    }

    @Keep
    public Float getConfidence() {
        return confidence;
    }

    @Keep
    public RectF getBoundingBox() {
        return new RectF(boundingBox);
    }

    @Keep
    public int getIndex() {
        return index;
    }

    @Keep
    public String getLabel() {
        return label;
    }
}

