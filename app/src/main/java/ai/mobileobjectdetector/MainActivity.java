package ai.mobileobjectdetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main screen for Mobile Object Detector.
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA = 1001;

    private TextView modelStatusText;
    private TextView thresholdValueText;
    private TextView classSummaryText;
    private ProgressBar downloadProgress;
    private Button downloadModelButton;
    private Button openCameraButton;
    private SeekBar confidenceSeekBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        modelStatusText = findViewById(R.id.modelStatusText);
        thresholdValueText = findViewById(R.id.thresholdValueText);
        classSummaryText = findViewById(R.id.classSummaryText);
        downloadProgress = findViewById(R.id.downloadProgress);
        downloadModelButton = findViewById(R.id.downloadModelButton);
        openCameraButton = findViewById(R.id.openCameraButton);
        confidenceSeekBar = findViewById(R.id.confidenceSeekBar);
        Button settingsButton = findViewById(R.id.settingsButton);

        setupQuickThreshold();
        refreshUi();

        downloadModelButton.setOnClickListener(v -> startModelDownload());
        openCameraButton.setOnClickListener(v -> openCameraWithPermission());
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void setupQuickThreshold() {
        float threshold = SettingsStore.getConfidenceThreshold(this);
        confidenceSeekBar.setProgress(Math.round(threshold * 100));
        updateThresholdText(threshold);
        confidenceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = Math.max(5, progress) / 100f;
                updateThresholdText(value);
                if (fromUser) {
                    SettingsStore.setConfidenceThreshold(MainActivity.this, value);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void updateThresholdText(float threshold) {
        thresholdValueText.setText(String.format(Locale.US, "Confidence threshold: %.2f", threshold));
    }

    private void refreshUi() {
        File modelFile = ModelDownloader.getModelFile(this);
        if (ModelDownloader.isModelAvailable(this)) {
            long sizeMb = modelFile.length() / 1024 / 1024;
            modelStatusText.setText("Model status: ready (" + sizeMb + " MB)");
            openCameraButton.setEnabled(true);
            downloadProgress.setProgress(100);
        } else {
            modelStatusText.setText("Model status: not downloaded");
            openCameraButton.setEnabled(false);
            downloadProgress.setProgress(0);
        }
        float threshold = SettingsStore.getConfidenceThreshold(this);
        confidenceSeekBar.setProgress(Math.round(threshold * 100));
        updateThresholdText(threshold);
        classSummaryText.setText(SettingsStore.selectedClassesSummary(this));
    }

    private void startModelDownload() {
        downloadModelButton.setEnabled(false);
        openCameraButton.setEnabled(false);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setProgress(0);
        modelStatusText.setText("Model status: starting download...");

        executor.execute(() -> ModelDownloader.download(this, new ModelDownloader.ProgressListener() {
            @Override
            public void onProgress(int percent, String message) {
                mainHandler.post(() -> {
                    downloadProgress.setProgress(percent);
                    modelStatusText.setText("Model status: " + message);
                });
            }

            @Override
            public void onComplete(File modelFile) {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Model downloaded successfully", Toast.LENGTH_LONG).show();
                    downloadModelButton.setEnabled(true);
                    refreshUi();
                });
            }

            @Override
            public void onError(Exception error) {
                mainHandler.post(() -> {
                    modelStatusText.setText("Model status: download failed");
                    Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    downloadModelButton.setEnabled(true);
                    refreshUi();
                });
            }
        }));
    }

    private void openCameraWithPermission() {
        if (!ModelDownloader.isModelAvailable(this)) {
            Toast.makeText(this, "Please load the YOLO model first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, CameraDetectionActivity.class));
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(this, CameraDetectionActivity.class));
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
