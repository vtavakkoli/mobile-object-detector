package ai.mobileobjectdetector;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live camera activity with YOLOv8 LiteRT/TFLite detection.
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public class CameraDetectionActivity extends AppCompatActivity {
    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView cameraStatusText;
    private TextView fpsText;

    private ExecutorService cameraExecutor;
    private YoloV8Detector detector;
    private final AtomicBoolean isDetecting = new AtomicBoolean(false);
    private long lastFrameTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_detection);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        cameraStatusText = findViewById(R.id.cameraStatusText);
        fpsText = findViewById(R.id.fpsText);
        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            File modelFile = ModelDownloader.getModelFile(this);
            detector = new YoloV8Detector(this, modelFile);
            cameraStatusText.setText("YOLO model loaded. Starting camera...");
            startCamera();
        } catch (Exception e) {
            cameraStatusText.setText("Could not load model: " + e.getMessage());
            Toast.makeText(this, "Model load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreviewAndAnalysis(cameraProvider);
            } catch (Exception e) {
                cameraStatusText.setText("Camera start failed: " + e.getMessage());
                Toast.makeText(this, "Camera start failed", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreviewAndAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        cameraStatusText.setText("Live YOLO detection running");
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        synchronized (this) {
            if (detector == null || !isDetecting.compareAndSet(false, true)) {
                imageProxy.close();
                return;
            }
        }

        try {
            Bitmap bitmap = ImageUtils.imageProxyToBitmap(imageProxy);
            float confidence = SettingsStore.getConfidenceThreshold(this);
            float iou = SettingsStore.getIouThreshold(this);
            Set<String> selectedClasses = SettingsStore.getSelectedClasses(this);

            List<Detection> detections;
            synchronized (this) {
                if (detector == null) {
                    imageProxy.close();
                    isDetecting.set(false);
                    return;
                }
                detections = detector.detect(bitmap, confidence, iou, selectedClasses);
            }
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            long now = System.currentTimeMillis();
            final double fps = lastFrameTime == 0L ? 0.0 : 1000.0 / Math.max(1L, now - lastFrameTime);
            lastFrameTime = now;

            runOnUiThread(() -> {
                overlayView.setDetections(detections, width, height);
                fpsText.setText(String.format(Locale.US, "FPS: %.1f | Detections: %d", fps, detections.size()));
            });

            bitmap.recycle();
        } catch (Exception e) {
            runOnUiThread(() -> cameraStatusText.setText("Detection error: " + e.getMessage()));
        } finally {
            imageProxy.close();
            isDetecting.set(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            try {
                if (!cameraExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    cameraExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cameraExecutor.shutdownNow();
            }
        }

        synchronized (this) {
            if (detector != null) {
                detector.close();
                detector = null;
            }
        }
        super.onDestroy();
    }
}
