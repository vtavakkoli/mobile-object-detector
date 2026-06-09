package ai.mobileobjectdetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal YOLOv8 TFLite/LiteRT detector.
 *
 * This implementation supports common YOLOv8 TFLite output shapes:
 * [1, 84, 8400] and [1, 8400, 84].
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public class YoloV8Detector implements AutoCloseable {
    private static final int NUM_THREADS = 4;
    private static final int MAX_DETECTIONS = 12;
    private static final float MIN_BOX_AREA_RATIO = 0.0015f;
    private static final float MAX_BOX_AREA_RATIO = 0.92f;

    private final Interpreter interpreter;
    private final int inputWidth;
    private final int inputHeight;
    private final int inputChannels;
    private final DataType inputType;
    private final int[] outputShape;
    private final DataType outputType;

    public YoloV8Detector(Context context, File modelFile) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(NUM_THREADS);
        interpreter = new Interpreter(loadModel(modelFile), options);

        Tensor inputTensor = interpreter.getInputTensor(0);
        int[] inputShape = inputTensor.shape();
        inputType = inputTensor.dataType();

        // Expected NHWC: [1, height, width, channels]
        if (inputShape.length != 4) {
            throw new IllegalStateException("Unsupported input shape. Expected [1,H,W,3].");
        }
        inputHeight = inputShape[1];
        inputWidth = inputShape[2];
        inputChannels = inputShape[3];
        if (inputChannels != 3) {
            throw new IllegalStateException("Unsupported input channels: " + inputChannels);
        }

        Tensor outputTensor = interpreter.getOutputTensor(0);
        outputShape = outputTensor.shape();
        outputType = outputTensor.dataType();
        if (outputType != DataType.FLOAT32) {
            throw new IllegalStateException("This sample expects FLOAT32 output. Actual: " + outputType);
        }
    }

    public List<Detection> detect(Bitmap bitmap, float confidenceThreshold, float iouThreshold, Set<String> selectedClasses) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true);
        ByteBuffer input = createInputBuffer(resized);
        ByteBuffer output = createOutputBuffer();

        interpreter.run(input, output);
        output.rewind();

        List<Detection> raw = parseOutput(output, bitmap.getWidth(), bitmap.getHeight(), confidenceThreshold, selectedClasses);
        return nonMaxSuppression(raw, iouThreshold, MAX_DETECTIONS);
    }

    private ByteBuffer createInputBuffer(Bitmap bitmap) {
        int bytesPerChannel = inputType == DataType.FLOAT32 ? 4 : 1;
        ByteBuffer buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * inputChannels * bytesPerChannel);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputWidth * inputHeight];
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            if (inputType == DataType.FLOAT32) {
                buffer.putFloat(r / 255.0f);
                buffer.putFloat(g / 255.0f);
                buffer.putFloat(b / 255.0f);
            } else if (inputType == DataType.UINT8) {
                buffer.put((byte) r);
                buffer.put((byte) g);
                buffer.put((byte) b);
            } else {
                throw new IllegalStateException("Unsupported input type: " + inputType);
            }
        }
        buffer.rewind();
        return buffer;
    }

    private ByteBuffer createOutputBuffer() {
        int elements = 1;
        for (int dim : outputShape) {
            elements *= dim;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(elements * 4);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    private List<Detection> parseOutput(ByteBuffer output, int originalWidth, int originalHeight,
                                        float confidenceThreshold, Set<String> selectedClasses) {
        float[] values = new float[output.capacity() / 4];
        output.asFloatBuffer().get(values);

        boolean channelFirst;
        int channels;
        int boxes;

        if (outputShape.length == 3 && outputShape[1] <= 200 && outputShape[2] > outputShape[1]) {
            // [1, 84, 8400]
            channelFirst = true;
            channels = outputShape[1];
            boxes = outputShape[2];
        } else if (outputShape.length == 3) {
            // [1, 8400, 84]
            channelFirst = false;
            boxes = outputShape[1];
            channels = outputShape[2];
        } else {
            throw new IllegalStateException("Unsupported YOLO output shape.");
        }

        int classCount = Math.min(ClassNames.COCO_CLASSES.length, channels - 4);
        Set<String> allowed = selectedClasses == null ? new HashSet<>() : selectedClasses;
        List<Detection> detections = new ArrayList<>();

        for (int i = 0; i < boxes; i++) {
            float x = getValue(values, channelFirst, channels, boxes, i, 0);
            float y = getValue(values, channelFirst, channels, boxes, i, 1);
            float w = getValue(values, channelFirst, channels, boxes, i, 2);
            float h = getValue(values, channelFirst, channels, boxes, i, 3);

            int bestClass = -1;
            float bestScore = 0f;
            for (int c = 0; c < classCount; c++) {
                float score = getValue(values, channelFirst, channels, boxes, i, 4 + c);
                if (score > bestScore) {
                    bestScore = score;
                    bestClass = c;
                }
            }

            if (bestClass < 0 || bestScore < confidenceThreshold) continue;
            String label = ClassNames.COCO_CLASSES[bestClass];
            if (!allowed.isEmpty() && !allowed.contains(label)) continue;

            // YOLOv8 normally returns xywh in model input scale, often 0..640.
            // Some exports return normalized 0..1, so this handles both cases.
            if (Math.max(Math.max(x, y), Math.max(w, h)) > 2f) {
                x /= inputWidth;
                w /= inputWidth;
                y /= inputHeight;
                h /= inputHeight;
            }

            float left = (x - w / 2f) * originalWidth;
            float top = (y - h / 2f) * originalHeight;
            float right = (x + w / 2f) * originalWidth;
            float bottom = (y + h / 2f) * originalHeight;

            RectF rect = new RectF(
                    clamp(left, 0, originalWidth),
                    clamp(top, 0, originalHeight),
                    clamp(right, 0, originalWidth),
                    clamp(bottom, 0, originalHeight)
            );
            if (rect.width() < 2 || rect.height() < 2) continue;

            float areaRatio = (rect.width() * rect.height()) / Math.max(1f, originalWidth * originalHeight);
            if (areaRatio < MIN_BOX_AREA_RATIO || areaRatio > MAX_BOX_AREA_RATIO) continue;

            detections.add(new Detection(rect, label, bestScore, bestClass));
        }
        return detections;
    }

    private float getValue(float[] values, boolean channelFirst, int channels, int boxes, int boxIndex, int channelIndex) {
        if (channelFirst) {
            return values[channelIndex * boxes + boxIndex];
        } else {
            return values[boxIndex * channels + channelIndex];
        }
    }

    private List<Detection> nonMaxSuppression(List<Detection> detections, float iouThreshold, int maxDetections) {
        Collections.sort(detections, new Comparator<Detection>() {
            @Override
            public int compare(Detection a, Detection b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });

        List<Detection> selected = new ArrayList<>();
        boolean[] removed = new boolean[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            if (removed[i]) continue;
            Detection candidate = detections.get(i);
            selected.add(candidate);
            if (selected.size() >= maxDetections) break;

            for (int j = i + 1; j < detections.size(); j++) {
                if (removed[j]) continue;
                Detection other = detections.get(j);
                if (candidate.classIndex == other.classIndex && iou(candidate.box, other.box) > iouThreshold) {
                    removed[j] = true;
                }
            }
        }
        return selected;
    }

    private float iou(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float intersection = Math.max(0, right - left) * Math.max(0, bottom - top);
        float union = a.width() * a.height() + b.width() * b.height() - intersection;
        return union <= 0 ? 0 : intersection / union;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private MappedByteBuffer loadModel(File modelFile) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(modelFile);
             FileChannel fileChannel = inputStream.getChannel()) {
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
        }
    }

    @Override
    public void close() {
        interpreter.close();
    }
}
