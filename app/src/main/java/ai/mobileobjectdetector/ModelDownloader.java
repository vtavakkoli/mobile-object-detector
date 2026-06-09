package ai.mobileobjectdetector;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads the YOLOv8n TFLite model from Hugging Face into private app storage.
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public final class ModelDownloader {
    public static final String MODEL_FILE_NAME = "yolov8n_float16.tflite";

    // SpotLab/YOLOv8Detection on Hugging Face.
    // If the repository changes, update this URL only.
    public static final String MODEL_URL =
            "https://huggingface.co/SpotLab/YOLOv8Detection/resolve/main/tflite_model.tflite";

    private ModelDownloader() {
    }

    public interface ProgressListener {
        void onProgress(int percent, String message);
        void onComplete(File modelFile);
        void onError(Exception error);
    }

    public static File getModelFile(Context context) {
        return new File(context.getFilesDir(), MODEL_FILE_NAME);
    }

    public static boolean isModelAvailable(Context context) {
        File file = getModelFile(context);
        return file.exists() && file.length() > 1024 * 1024;
    }

    public static void download(Context context, ProgressListener listener) {
        File outputFile = getModelFile(context);
        File tempFile = new File(context.getFilesDir(), MODEL_FILE_NAME + ".tmp");

        try {
            listener.onProgress(0, "Connecting to Hugging Face...");
            URL url = new URL(MODEL_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("Download failed. HTTP code: " + responseCode);
            }

            int length = connection.getContentLength();
            long downloaded = 0;
            byte[] buffer = new byte[8192];

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile, false)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (length > 0) {
                        int progress = (int) Math.min(100, (downloaded * 100) / length);
                        listener.onProgress(progress, "Downloading model... " + progress + "%");
                    } else {
                        listener.onProgress(0, "Downloading model... " + (downloaded / 1024 / 1024) + " MB");
                    }
                }
            }

            if (outputFile.exists() && !outputFile.delete()) {
                throw new IllegalStateException("Could not replace old model file.");
            }
            if (!tempFile.renameTo(outputFile)) {
                throw new IllegalStateException("Could not save downloaded model.");
            }
            listener.onProgress(100, "Model saved.");
            listener.onComplete(outputFile);
        } catch (Exception e) {
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
            listener.onError(e);
        }
    }
}
