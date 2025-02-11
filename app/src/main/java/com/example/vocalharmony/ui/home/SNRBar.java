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

/**
 * Custom View that displays a vertical bar representing Signal-to-Noise Ratio (SNR) in dB.
 * The bar’s height is proportional to a user-defined range [SNR_MIN_DB..SNR_MAX_DB],
 * and a line indicates the maximum SNR reached. A text label shows a qualitative rating.
 */
public class SNRBar extends View {

    // Define a dB range to map to [0..1] on the bar
    private static final float SNR_MIN_DB = -20f;  // dB at which bar is 0%
    private static final float SNR_MAX_DB = 40f;   // dB at which bar is 100%

    private float currentSNR = 0f;  // Current SNR (in dB)
    private float maxSNR = 0f;      // Tracks the highest SNR
    private String snrCategory = "";

    // Paint objects
    private final Paint snrPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maxSNRPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Gradient
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
     * Common initialization:
     * - Reads custom colors (if provided),
     * - Sets default text/line colors,
     * - Prepares the paints.
     */
    private void init(Context context, AttributeSet attrs) {
        if (isInEditMode()) {
            // Layout preview defaults
            startColor = 0xFF00FF00; // bright green
            endColor   = 0xFF0000FF; // bright blue
            maxSNRPaint.setColor(0xFFFF0000); // red line
            textPaint.setColor(0xFF000000);   // black text
        } else {
            // Normal runtime environment
            maxSNRPaint.setColor(ContextCompat.getColor(context, R.color.max_snr_color));
            textPaint.setColor(ContextCompat.getColor(context, R.color.snrbar_text_color));

            // If custom attributes are provided, read them
            if (attrs != null) {
                TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SNRBar);
                try {
                    startColor = a.getColor(
                            R.styleable.SNRBar_startColor,
                            ContextCompat.getColor(context, R.color.snrbar_start_color)
                    );
                    endColor = a.getColor(
                            R.styleable.SNRBar_endColor,
                            ContextCompat.getColor(context, R.color.snrbar_end_color)
                    );
                } finally {
                    a.recycle();
                }
            } else {
                // Fallback if no XML attributes are specified
                startColor = ContextCompat.getColor(context, R.color.snrbar_start_color);
                endColor   = ContextCompat.getColor(context, R.color.snrbar_end_color);
            }
        }

        // Configure line used to mark max SNR
        maxSNRPaint.setStrokeWidth(5f);
        maxSNRPaint.setStyle(Paint.Style.STROKE);

        // Configure text paint
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        setWillNotDraw(false);
    }

    /**
     * Update current SNR (in dB) and request a redraw.
     */
    public void setSNRValue(final double snrValue) {
        post(() -> {
            currentSNR = (float) snrValue;

            // Update max SNR if higher
            if (currentSNR > maxSNR) {
                maxSNR = currentSNR;
            }

            // Recompute the rating
            snrCategory = getSNRRating(currentSNR);

            // Force a redraw
            invalidate();
        });
    }

    /**
     * Rebuild gradient if size changed
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        gradient = new LinearGradient(
                0, 0,
                0, h,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
        );
        snrPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (gradient == null) {
            gradient = new LinearGradient(
                    0, 0,
                    0, getHeight(),
                    startColor,
                    endColor,
                    Shader.TileMode.CLAMP
            );
            snrPaint.setShader(gradient);
        }

        // Map currentSNR => 0..1
        float normalizedSNR = normalizeSNR(currentSNR);
        float barHeight = getHeight() * normalizedSNR;

        // Draw main bar from bottom
        RectF barRect = new RectF(
                0,
                getHeight() - barHeight,
                getWidth(),
                getHeight()
        );
        canvas.drawRect(barRect, snrPaint);

        // Draw line for max SNR
        float normalizedMaxSNR = normalizeSNR(maxSNR);
        float maxSNRHeight = getHeight() * normalizedMaxSNR;
        canvas.drawLine(
                0,
                getHeight() - maxSNRHeight,
                getWidth(),
                getHeight() - maxSNRHeight,
                maxSNRPaint
        );

        // Draw textual rating in the center
        float textX = getWidth() / 2f;
        float textY = getHeight() / 2f;
        canvas.drawText(snrCategory, textX, textY, textPaint);
    }

    /**
     * Convert from [-20..40] dB => [0..1]
     */
    private float normalizeSNR(float snrDb) {
        float range = SNR_MAX_DB - SNR_MIN_DB; // 40 - (-20) = 60
        float normalized = (snrDb - SNR_MIN_DB) / range;
        return clamp(normalized, 0f, 1f);
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(val, max));
    }

    /**
     * Provide a textual rating for a given SNR in dB.
     * Adjust thresholds for your environment or scaling.
     */
    private String getSNRRating(float snrValue) {
        // Example thresholds:
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
            return "Excellent"; // 40+
        }
    }
}
