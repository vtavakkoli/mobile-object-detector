package ai.mobileobjectdetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws detection boxes over the CameraX preview.
 * Written by Dr. Vahid Tavakkoli, 2026.
 */
public class OverlayView extends View {
    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint textBackgroundPaint = new Paint();
    private List<Detection> detections = new ArrayList<>();
    private int imageWidth = 1;
    private int imageHeight = 1;

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
        boxPaint.setColor(0xFF00E676);
        boxPaint.setAntiAlias(true);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(36f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);

        textBackgroundPaint.setColor(0xCC000000);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void setDetections(List<Detection> detections, int imageWidth, int imageHeight) {
        this.detections = detections == null ? new ArrayList<>() : detections;
        this.imageWidth = Math.max(1, imageWidth);
        this.imageHeight = Math.max(1, imageHeight);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (imageWidth <= 0 || imageHeight <= 0) return;

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // Calculate scale and offsets to match PreviewView's fillCenter scaleType
        float scale = Math.max(viewWidth / imageWidth, viewHeight / imageHeight);
        float offsetX = (viewWidth - imageWidth * scale) / 2f;
        float offsetY = (viewHeight - imageHeight * scale) / 2f;

        for (Detection d : detections) {
            RectF box = new RectF(
                    d.box.left * scale + offsetX,
                    d.box.top * scale + offsetY,
                    d.box.right * scale + offsetX,
                    d.box.bottom * scale + offsetY
            );

            canvas.drawRect(box, boxPaint);
            String label = String.format(Locale.US, "%s %.0f%%", d.label, d.confidence * 100f);
            float textWidth = textPaint.measureText(label);
            float textHeight = 44f;
            float textLeft = Math.max(0, box.left);
            float textTop = Math.max(textHeight, box.top);

            canvas.drawRect(textLeft, textTop - textHeight, textLeft + textWidth + 16f, textTop + 8f, textBackgroundPaint);
            canvas.drawText(label, textLeft + 8f, textTop - 8f, textPaint);
        }
    }
}
