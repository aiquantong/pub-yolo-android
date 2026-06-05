package com.ultralytics.yolo.predict.detect;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;

public class PostProcessUtils {

    // Sort objects in descending order based on confidence
    private static void qsortDescentInplace(ArrayList<DetectedObject> objects, int left, int right) {
        int i = left, j = right;
        float pivot = objects.get((left + right) / 2).confidence;

        while (i <= j) {
            while (objects.get(i).confidence > pivot) i++;
            while (objects.get(j).confidence < pivot) j--;
            if (i <= j) {
                // Swap
                Collections.swap(objects, i, j);
                i++;
                j--;
            }
        }

        if (left < j) qsortDescentInplace(objects, left, j);
        if (i < right) qsortDescentInplace(objects, i, right);
    }

    private static void qsortDescentInplace(ArrayList<DetectedObject> objects) {
        if (!objects.isEmpty()) {
            qsortDescentInplace(objects, 0, objects.size() - 1);
        }
    }

    // Calculate intersection area of two RectF objects
    private static float intersectionArea(RectF a, RectF b) {
        if (!RectF.intersects(a, b)) return 0;

        float left = Math.max(a.left, b.left);
        float right = Math.min(a.right, b.right);
        float top = Math.max(a.top, b.top);
        float bottom = Math.min(a.bottom, b.bottom);

        return Math.max(0, right - left) * Math.max(0, bottom - top);
    }

    // Non-Maximum Suppression (NMS)
    private static void nmsSortedBboxes(ArrayList<DetectedObject> objects, ArrayList<Integer> picked, float nmsThreshold) {
        picked.clear();

        int n = objects.size();
        ArrayList<Float> areas = new ArrayList<>(n);

        for (DetectedObject obj : objects) {
            areas.add(obj.boundingBox.width() * obj.boundingBox.height());
        }

        for (int i = 0; i < n; i++) {
            DetectedObject a = objects.get(i);
            boolean keep = true;

            for (int j : picked) {
                DetectedObject b = objects.get(j);

                // Intersection over Union (IoU)
                float interArea = intersectionArea(a.boundingBox, b.boundingBox);
                float unionArea = areas.get(i) + areas.get(j) - interArea;
                if (interArea / unionArea > nmsThreshold) {
                    keep = false;
                    break;
                }
            }

            if (keep) picked.add(i);
        }
    }

    // Postprocess function
    public static ArrayList<DetectedObject> postprocess(
            float[][] recognitions,
            int w, int h,
            float confidenceThreshold,
            float iouThreshold,
            int numItemsThreshold,
            int numClasses,
            ArrayList<String> labels
    ) {

        ArrayList<DetectedObject> proposals = new ArrayList<>();
        ArrayList<DetectedObject> objects = new ArrayList<>();

        // Process recognitions
        for (int i = 0; i < w; i++) {
            float maxScore = -Float.MAX_VALUE;
            int classIndex = -1;

            for (int c = 0; c < numClasses; c++) {
                if (recognitions[c + 4][i] > maxScore) {
                    maxScore = recognitions[c + 4][i];
                    classIndex = c;
                }
            }

            if (maxScore > confidenceThreshold) {
                float dx = recognitions[0][i];
                float dy = recognitions[1][i];
                float dw = recognitions[2][i];
                float dh = recognitions[3][i];

                RectF rect = new RectF(
                        dx - dw / 2,
                        dy - dh / 2,
                        dx + dw / 2,
                        dy + dh / 2
                );
                String label = (labels != null && classIndex < labels.size()) ? labels.get(classIndex) : "Unknown";
                DetectedObject obj = new DetectedObject(maxScore, rect, classIndex, label);

                proposals.add(obj);
            }
        }

        // Sort proposals by confidence
        qsortDescentInplace(proposals);

        // Apply NMS
        ArrayList<Integer> picked = new ArrayList<>();
        nmsSortedBboxes(proposals, picked, iouThreshold);

        int count = Math.min(picked.size(), numItemsThreshold);
        for (int i = 0; i < count; i++) {
            objects.add(proposals.get(picked.get(i)));
        }

        // Convert to result format
        //float[][] result = new ArrayList<>();
        ArrayList<DetectedObject> result = new ArrayList<>();
        for (DetectedObject obj : objects) {

            float left = Math.max(0, obj.boundingBox.left);
            float top = Math.max(0, obj.boundingBox.top);
            float right = Math.min(1, obj.boundingBox.right);
            float bottom = Math.min(1, obj.boundingBox.bottom);

            RectF boundingBox = new RectF(
                left,
                top,
                right,
                bottom
            );

            DetectedObject det = new DetectedObject(
                obj.confidence,
                boundingBox,
                obj.index, obj.label
            );
            result.add(det);
            //result.add(new float[]{x, y, width, height, obj.confidence, obj.index});
        }

        return result;
    }

    /**
     * Parse End2End model output (models with end2end=true)
     * These models have already performed NMS internally and output final detections directly.
     * <p>
     * Expected output format: [1, num_detections, params]
     * Common formats:
     * - Detection: [x1, y1, x2, y2, confidence, class_id]
     * - OBB: [x, y, w, h, angle, confidence, class_id]
     */
    public static ArrayList<DetectedObject> parseEnd2EndOutput(float[][] outputData, int[] outputTensorShape, float confidenceThreshold, float iouThreshold, int numItemsThreshold, ArrayList<String> labels) {
        ArrayList<DetectedObject> detections = new ArrayList<>();

        int numParams = outputTensorShape.length > 2 ? outputTensorShape[2] : 1;
        int numDetections = outputTensorShape.length > 2 ? outputTensorShape[1] : outputTensorShape[0];

        // Debug: Print first few raw outputs
        if (numDetections > 0) {
            System.out.println("=== parseEnd2EndOutput Debug ===");
            System.out.println("Output shape: numDetections=" + numDetections + ", numParams=" + numParams);
            System.out.println("First detection raw values:");
            for (int j = 0; j < Math.min(5, numParams); j++) {
                System.out.printf("  idx " + j + ": " + outputData[0][j]);
            }
            System.out.println("");
        }

        for (int i = 0; i < numDetections; i++) {
            try {
                float confidence = 0;
                int classId = 0;
                float x1 = 0, y1 = 0, x2 = 0, y2 = 0;
                float angle = 0.0f;

                if (numParams >= 7) {

//                    0 = 0.5015248
//                    1 = 0.469653
//                    2 = 0.06314033
//                    3 = 0.019181196
//                    4 = 0.67212796
//                    5 = 1.0
//                    6 = 0.12661847

                    // OBB format: [x, y, w, h, angle, confidence, class]
                    float cx = outputData[i][0];
                    float cy = outputData[i][1];
                    float w = outputData[i][2];
                    float h = outputData[i][3];
                    // angle at index 4 is ignored for now
                    confidence = outputData[i][4];
                    classId = (int) outputData[i][5];
                    angle = outputData[i][6];

                    // Convert center + size to bounding box
                    x1 = cx - w / 2;
                    y1 = cy - h / 2;
                    x2 = cx + w / 2;
                    y2 = cy + h / 2;
                }
                // Try different format interpretations
                else if (numParams == 6) {
                    // Format: [x1, y1, x2, y2, confidence, class]
                    x1 = outputData[i][0];
                    y1 = outputData[i][1];
                    x2 = outputData[i][2];
                    y2 = outputData[i][3];
                    confidence = outputData[i][4];
                    classId = (int) outputData[i][5];
                }

                // Validate detection
                if (confidence >= confidenceThreshold && classId >= 0 && classId < labels.size()) {

                    // Ensure coordinates are normalized [0-1]
                    float normX1 = Math.max(0, Math.min(1, x1));
                    float normY1 = Math.max(0, Math.min(1, y1));
                    float normX2 = Math.max(0, Math.min(1, x2));
                    float normY2 = Math.max(0, Math.min(1, y2));

                    RectF bbox = new RectF(normX1, normY1, normX2, normY2);
                    String label = labels.get(classId);

                    detections.add(new DetectedObject(confidence, bbox, classId, label, angle));
                }
            } catch (Exception e) {
                // Skip this detection if parsing fails
                System.out.println("Error parsing detection " + i + ": " + e.getMessage());
            }
        }

        // Sort by confidence descending
        qsortDescentInplace(detections);

        // Optional: apply NMS again if needed
//        if (detections.size() > 1 && iouThreshold > 0) {
//            ArrayList<Integer> picked = new ArrayList<>();
//            nmsSortedBboxes(detections, picked, iouThreshold);
//
//            ArrayList<DetectedObject> filtered = new ArrayList<>();
//            for (int idx : picked) {
//                filtered.add(detections.get(idx));
//            }
//            detections = filtered;
//        }

        // Limit result count
        if (numItemsThreshold > 0 && detections.size() > numItemsThreshold) {
            ArrayList<DetectedObject> limited = new ArrayList<>();
            for (int i = 0; i < numItemsThreshold; i++) {
                limited.add(detections.get(i));
            }
            detections = limited;
        }

        return detections;
    }
}
