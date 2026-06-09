package ai.mobileobjectdetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Camera image conversion utilities.
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public final class ImageUtils {
    private ImageUtils() {
    }

    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        byte[] nv21 = yuv420ToNv21(imageProxy);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                imageProxy.getWidth(), imageProxy.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 85, out);
        byte[] jpegBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        if (rotation == 0) return bitmap;

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        return rotated;
    }

    private static byte[] yuv420ToNv21(ImageProxy image) {
        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        ImageProxy.PlaneProxy uPlane = image.getPlanes()[1];
        ImageProxy.PlaneProxy vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;
        byte[] nv21 = new byte[ySize + uvSize * 2];

        copyPlane(yBuffer, yPlane.getRowStride(), yPlane.getPixelStride(), width, height, nv21, 0, 1);

        int offset = ySize;
        int chromaWidth = width / 2;
        int chromaHeight = height / 2;
        byte[] vBytes = new byte[uvSize];
        byte[] uBytes = new byte[uvSize];
        copyPlane(vBuffer, vPlane.getRowStride(), vPlane.getPixelStride(), chromaWidth, chromaHeight, vBytes, 0, 1);
        copyPlane(uBuffer, uPlane.getRowStride(), uPlane.getPixelStride(), chromaWidth, chromaHeight, uBytes, 0, 1);

        for (int i = 0; i < uvSize; i++) {
            nv21[offset++] = vBytes[i];
            nv21[offset++] = uBytes[i];
        }
        return nv21;
    }

    private static void copyPlane(ByteBuffer buffer, int rowStride, int pixelStride,
                                  int width, int height, byte[] output, int offset, int outputPixelStride) {
        byte[] row = new byte[rowStride];
        int outputOffset = offset;
        for (int rowIndex = 0; rowIndex < height; rowIndex++) {
            int length = Math.min(rowStride, buffer.remaining());
            buffer.get(row, 0, length);
            for (int col = 0; col < width; col++) {
                output[outputOffset] = row[col * pixelStride];
                outputOffset += outputPixelStride;
            }
        }
        buffer.rewind();
    }
}
