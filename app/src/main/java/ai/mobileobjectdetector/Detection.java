package ai.mobileobjectdetector;

import android.graphics.RectF;

/**
 * Detection result.
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public class Detection {
    public final RectF box;
    public final String label;
    public final float confidence;
    public final int classIndex;

    public Detection(RectF box, String label, float confidence, int classIndex) {
        this.box = box;
        this.label = label;
        this.confidence = confidence;
        this.classIndex = classIndex;
    }
}
