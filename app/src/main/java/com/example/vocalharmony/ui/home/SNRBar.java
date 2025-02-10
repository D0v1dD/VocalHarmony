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
    private static final float SNR_MIN_DB = -20f;  // dB value at which the bar is 0%
    private static final float SNR_MAX_DB = 40f;   // dB value at which the bar is 100%

    private float currentSNR = 0f;  // Current SNR (in dB)
    private float maxSNR = 0f;      // Tracks the highest SNR reached (in dB)
    private String snrCategory = "";

    // Paint objects
    private final Paint snrPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maxSNRPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Gradient for the bar
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
     * Common initialization for the bar:
     * - Reads custom colors if provided,
     * - Sets default text/line colors,
     * - Prepares the paints.
     */
    private void init(Context context, AttributeSet attrs) {
        if (isInEditMode()) {
            // In layout preview, just pick some arbitrary colors
            startColor = 0xFF00FF00; // Bright green
            endColor   = 0xFF0000FF; // Bright blue
            maxSNRPaint.setColor(0xFFFF0000); // Red line for max SNR
            textPaint.setColor(0xFF000000);   // Black text
        } else {
            // Real runtime environment
            maxSNRPaint.setColor(ContextCompat.getColor(context, R.color.max_snr_color));
            textPaint.setColor(ContextCompat.getColor(context, R.color.snrbar_text_color));

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
                startColor = ContextCompat.getColor(context, R.color.snrbar_start_color);
                endColor   = ContextCompat.getColor(context, R.color.snrbar_end_color);
            }
        }

        // Configure the paint for the max SNR line
        maxSNRPaint.setStrokeWidth(5f);
        maxSNRPaint.setStyle(Paint.Style.STROKE);

        // Configure the text paint (size, alignment)
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        setWillNotDraw(false);
    }

    /**
     * Sets the current SNR (in dB) and triggers a redraw.
     * Also updates the maximum SNR and rating text.
     *
     * @param snrValue SNR in decibels (dB)
     */
    public void setSNRValue(final double snrValue) {
        post(() -> {
            currentSNR = (float) snrValue;

            // Update max SNR if this is higher
            if (currentSNR > maxSNR) {
                maxSNR = currentSNR;
            }

            // Compute the text label (rating) for the new SNR
            snrCategory = getSNRRating(currentSNR);

            // Force a redraw
            invalidate();
        });
    }

    /**
     * Rebuilds the gradient when the view’s size changes.
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

    /**
     * Draws the bar for the current SNR, a line for the max SNR,
     * and a rating string in the center.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // In preview mode, or if gradient is null, create a fallback
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

        // Convert current SNR to a 0..1 normalized range
        float normalizedSNR = normalizeSNR(currentSNR);
        float barHeight = getHeight() * normalizedSNR;

        // Draw the filled bar from the bottom up
        RectF barRect = new RectF(
                0,
                getHeight() - barHeight,
                getWidth(),
                getHeight()
        );
        canvas.drawRect(barRect, snrPaint);

        // Draw a horizontal line for the max SNR
        float normalizedMax = normalizeSNR(maxSNR);
        float maxBarHeight = getHeight() * normalizedMax;
        canvas.drawLine(
                0,
                getHeight() - maxBarHeight,
                getWidth(),
                getHeight() - maxBarHeight,
                maxSNRPaint
        );

        // Draw the rating text in the center of the view
        float textX = getWidth() / 2f;
        float textY = getHeight() / 2f;
        canvas.drawText(snrCategory, textX, textY, textPaint);
    }

    /**
     * Map a decibel value to [0..1] between SNR_MIN_DB and SNR_MAX_DB.
     */
    private float normalizeSNR(float snrDb) {
        float range = SNR_MAX_DB - SNR_MIN_DB;
        float normalized = (snrDb - SNR_MIN_DB) / range;
        return clamp(normalized, 0f, 1f);
    }

    /**
     * Provide a simple text rating based on the SNR in dB.
     * Adjust thresholds as needed for your environment.
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
            // 40+ is "Excellent"
            return "Excellent";
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
