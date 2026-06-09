package ai.mobileobjectdetector;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Stores detection settings.
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public final class SettingsStore {
    private static final String PREFS_NAME = "mobile_object_detector_settings";
    private static final String KEY_CONFIDENCE = "confidence_threshold";
    private static final String KEY_IOU = "iou_threshold";
    private static final String KEY_SELECTED_CLASSES = "selected_classes";

    public static final float RECOMMENDED_CONFIDENCE = 0.55f;
    public static final float RECOMMENDED_IOU = 0.45f;

    private SettingsStore() {
    }

    public static float getConfidenceThreshold(Context context) {
        return getPrefs(context).getFloat(KEY_CONFIDENCE, RECOMMENDED_CONFIDENCE);
    }

    public static void setConfidenceThreshold(Context context, float value) {
        getPrefs(context).edit().putFloat(KEY_CONFIDENCE, clamp(value, 0.05f, 0.95f)).apply();
    }

    public static float getIouThreshold(Context context) {
        return getPrefs(context).getFloat(KEY_IOU, RECOMMENDED_IOU);
    }

    public static void setIouThreshold(Context context, float value) {
        getPrefs(context).edit().putFloat(KEY_IOU, clamp(value, 0.05f, 0.95f)).apply();
    }

    public static Set<String> getSelectedClasses(Context context) {
        Set<String> all = new HashSet<>();
        for (String name : ClassNames.COCO_CLASSES) {
            all.add(name);
        }
        return getPrefs(context).getStringSet(KEY_SELECTED_CLASSES, all);
    }

    public static void setSelectedClasses(Context context, Set<String> selectedClasses) {
        getPrefs(context).edit().putStringSet(KEY_SELECTED_CLASSES, new HashSet<>(selectedClasses)).apply();
    }

    public static String selectedClassesSummary(Context context) {
        Set<String> selected = getSelectedClasses(context);
        if (selected.size() == ClassNames.COCO_CLASSES.length) {
            return "Classes: all COCO classes";
        }
        if (selected.isEmpty()) {
            return "Classes: none selected";
        }
        StringBuilder sb = new StringBuilder("Classes: ");
        int count = 0;
        for (String c : selected) {
            if (count > 0) sb.append(", ");
            sb.append(c);
            count++;
            if (count == 5 && selected.size() > 5) {
                sb.append(" + ").append(selected.size() - 5).append(" more");
                break;
            }
        }
        return sb.toString();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
