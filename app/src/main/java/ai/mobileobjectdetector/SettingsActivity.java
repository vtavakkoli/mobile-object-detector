package ai.mobileobjectdetector;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Advanced detector settings.
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public class SettingsActivity extends AppCompatActivity {
    private TextView confidenceValueText;
    private TextView iouValueText;
    private SeekBar confidenceSeekBar;
    private SeekBar iouSeekBar;
    private LinearLayout classesContainer;
    private final List<CheckBox> classCheckBoxes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        confidenceValueText = findViewById(R.id.confidenceValueText);
        iouValueText = findViewById(R.id.iouValueText);
        confidenceSeekBar = findViewById(R.id.confidenceSeekBar);
        iouSeekBar = findViewById(R.id.iouSeekBar);
        classesContainer = findViewById(R.id.classesContainer);
        Button selectAllButton = findViewById(R.id.selectAllButton);
        Button deselectAllButton = findViewById(R.id.deselectAllButton);
        Button saveButton = findViewById(R.id.saveSettingsButton);

        setupThresholds();
        buildClassList();

        selectAllButton.setOnClickListener(v -> {
            for (CheckBox checkBox : classCheckBoxes) {
                checkBox.setChecked(true);
            }
        });

        deselectAllButton.setOnClickListener(v -> {
            for (CheckBox checkBox : classCheckBoxes) {
                checkBox.setChecked(false);
            }
        });

        saveButton.setOnClickListener(v -> saveSettingsAndClose());
    }

    private void setupThresholds() {
        float conf = SettingsStore.getConfidenceThreshold(this);
        float iou = SettingsStore.getIouThreshold(this);
        confidenceSeekBar.setProgress(Math.round(conf * 100));
        iouSeekBar.setProgress(Math.round(iou * 100));
        updateConfidenceText(conf);
        updateIouText(iou);

        confidenceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateConfidenceText(Math.max(5, progress) / 100f);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        iouSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateIouText(Math.max(5, progress) / 100f);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void buildClassList() {
        Set<String> selected = SettingsStore.getSelectedClasses(this);
        classesContainer.removeAllViews();
        classCheckBoxes.clear();
        for (String name : ClassNames.COCO_CLASSES) {
            CheckBox cb = new CheckBox(this);
            cb.setText(name);
            cb.setTextSize(16f);
            cb.setTextColor(0xFF000000); // Black color
            cb.setChecked(selected.contains(name));
            cb.setPadding(0, 8, 0, 8);
            classesContainer.addView(cb);
            classCheckBoxes.add(cb);
        }
    }

    private void saveSettingsAndClose() {
        float conf = Math.max(5, confidenceSeekBar.getProgress()) / 100f;
        float iou = Math.max(5, iouSeekBar.getProgress()) / 100f;
        Set<String> selected = new HashSet<>();
        for (CheckBox cb : classCheckBoxes) {
            if (cb.isChecked()) selected.add(cb.getText().toString());
        }

        if (selected.isEmpty()) {
            Toast.makeText(this, "Please select at least one class.", Toast.LENGTH_LONG).show();
            return;
        }

        SettingsStore.setConfidenceThreshold(this, conf);
        SettingsStore.setIouThreshold(this, iou);
        SettingsStore.setSelectedClasses(this, selected);
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateConfidenceText(float value) {
        confidenceValueText.setText(String.format(Locale.US, "Confidence threshold: %.2f", value));
    }

    private void updateIouText(float value) {
        iouValueText.setText(String.format(Locale.US, "NMS IoU threshold: %.2f", value));
    }
}
