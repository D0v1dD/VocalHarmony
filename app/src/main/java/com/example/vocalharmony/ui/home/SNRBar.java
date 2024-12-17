package com.example.vocalharmony.ui.home;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.vocalharmony.R;

public class SNRBar extends View {
    // Constants to define how SNR maps to the bar's height
    private static final float SNR_OFFSET = 20f; // Offset to shift SNR range
    private static final float SNR_DIVISOR = 100f; // Divisor to normalize the SNR into [0,1] range

    private float currentSNR = 0;
    private float maxSNR = 0;
    private Paint snrPaint;
    private Paint maxSNRPaint;
    private Paint textPaint;
    private String snrCategory = "";
    private LinearGradient gradient;
    private int startColor;
    private int endColor;

    public SNRBar(Context context) {
        super(context);
        init(context, null);
    }

    public SNRBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SNRBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    /**
     * Initializes the paints and colors for the SNR bar.
     */
    private void init(Context context, AttributeSet attrs) {
        snrPaint = new Paint();
        maxSNRPaint = new Paint();
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (isInEditMode()) {
            // Hardcoded colors for preview in layout editor
            startColor = 0xFF00FF00; // Green
            endColor = 0xFF0000FF;   // Blue
            maxSNRPaint.setColor(0xFFFF0000); // Red
            textPaint.setColor(0xFF000000);   // Black
        } else {
            // Initialize colors from resources
            maxSNRPaint.setColor(ContextCompat.getColor(context, R.color.max_snr_color));
            textPaint.setColor(ContextCompat.getColor(context, R.color.snrbar_text_color));

            if (attrs != null) {
                TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SNRBar);
                try {
                    startColor = a.getColor(R.styleable.SNRBar_startColor,
                            ContextCompat.getColor(context, R.color.snrbar_start_color));
                    endColor = a.getColor(R.styleable.SNRBar_endColor,
                            ContextCompat.getColor(context, R.color.snrbar_end_color));
                } finally {
                    a.recycle();
                }
            } else {
                // Default colors if no attributes provided
                startColor = ContextCompat.getColor(context, R.color.snrbar_start_color);
                endColor = ContextCompat.getColor(context, R.color.snrbar_end_color);
            }
        }

        maxSNRPaint.setStrokeWidth(5f);
        maxSNRPaint.setStyle(Paint.Style.STROKE);

        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        setWillNotDraw(false);
    }

    /**
     * Sets the SNR value and triggers a redraw.
     * @param snrValue The new SNR value calculated by the back-end.
     */
    public void setSNRValue(final double snrValue) {
        // Ensure updates happen on the main thread
        post(() -> {
            currentSNR = (float) snrValue;

            // Update max SNR if current is greater
            if (currentSNR > maxSNR) {
                maxSNR = currentSNR;
            }

            snrCategory = getSNRRating(currentSNR);
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Initialize gradient once we have dimensions
        if (gradient == null) {
            gradient = new LinearGradient(0, 0, 0, getHeight(),
                    startColor,
                    endColor,
                    Shader.TileMode.CLAMP);
            snrPaint.setShader(gradient);
        }

        // Normalize SNR to [0,1] range. The logic "(SNR + 20) / 100" was chosen based on expected SNR range.
        // Adjust SNR_OFFSET and SNR_DIVISOR to match your environment or tests.
        float normalizedSNR = (currentSNR + SNR_OFFSET) / SNR_DIVISOR;
        normalizedSNR = Math.max(0, Math.min(normalizedSNR, 1));  // Clamp between 0 and 1

        float barHeight = getHeight() * normalizedSNR;
        RectF rect = new RectF(0, getHeight() - barHeight, getWidth(), getHeight());
        canvas.drawRect(rect, snrPaint);

        // Draw the maximum SNR indicator line
        float normalizedMaxSNR = (maxSNR + SNR_OFFSET) / SNR_DIVISOR;
        normalizedMaxSNR = Math.max(0, Math.min(normalizedMaxSNR, 1));
        float maxSNRHeight = getHeight() * normalizedMaxSNR;
        canvas.drawLine(0, getHeight() - maxSNRHeight, getWidth(), getHeight() - maxSNRHeight, maxSNRPaint);

        // Draw the SNR category text at the center of the view
        canvas.drawText(snrCategory, getWidth() / 2f, getHeight() / 2f, textPaint);

        // Optional: If debugging is needed
        // Log.d("SNRBar", "currentSNR: " + currentSNR + ", maxSNR: " + maxSNR + ", category: " + snrCategory);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Recreate gradient with updated height
        gradient = new LinearGradient(0, 0, 0, h,
                startColor,
                endColor,
                Shader.TileMode.CLAMP);
        snrPaint.setShader(gradient);
    }

    /**
     * Provides qualitative feedback based on SNR values.
     * @param snrValue The current SNR.
     * @return A string representing the quality category.
     */
    private String getSNRRating(float snrValue) {
        if (snrValue < 0) {
            return "Very Poor";
        } else if (snrValue < 10) {
            return "Poor";
        } else if (snrValue < 20) {
            return "Fair";
        } else if (snrValue < 30) {
            return "Good";
        } else if (snrValue < 40) {
            return "Very Good";
        } else {
            return "Excellent";
        }
    }
}
