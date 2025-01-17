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
 * Custom View that displays a vertical bar representing Signal-to-Noise Ratio (SNR).
 * The bar’s height is proportional to the normalized SNR value, and a line indicates
 * the maximum SNR reached. A text label shows a qualitative rating of the current SNR.
 */
public class SNRBar extends View {

    // Constants for mapping the SNR range to [0..1].
    private static final float SNR_OFFSET = 20f;     // Shift to ensure negative SNR still shows on bar
    private static final float SNR_DIVISOR = 100f;   // Divisor to normalize the SNR into 0..1

    private float currentSNR = 0f;  // Current SNR value
    private float maxSNR = 0f;      // Tracks the highest SNR reached
    private String snrCategory = "";

    // Paint objects
    private Paint snrPaint;     // Paint for the gradient bar
    private Paint maxSNRPaint;  // Paint for the max SNR line
    private Paint textPaint;    // Paint for label text

    // Gradient fields
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
     * Common initialization method for all constructors.
     * Sets up paints, default or XML-specified colors, and text properties.
     */
    private void init(Context context, AttributeSet attrs) {
        // Set up the Paint objects with anti-aliasing for smoother edges
        snrPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maxSNRPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Decide colors for preview (layout editor) vs. runtime
        if (isInEditMode()) {
            // Hardcoded preview colors
            startColor = 0xFF00FF00; // Bright green
            endColor   = 0xFF0000FF; // Bright blue
            maxSNRPaint.setColor(0xFFFF0000); // Red line for max SNR
            textPaint.setColor(0xFF000000);   // Black text
        } else {
            // Normal app runtime colors from resources
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

        // Configure the line used to mark maximum SNR
        maxSNRPaint.setStrokeWidth(5f);
        maxSNRPaint.setStyle(Paint.Style.STROKE);

        // Configure the label text
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Let the system know we handle drawing ourselves
        setWillNotDraw(false);
    }

    /**
     * Updates the current SNR value on the bar and triggers a redraw.
     * Automatically updates the max SNR and SNR rating label.
     *
     * @param snrValue the new SNR value to display
     */
    public void setSNRValue(final double snrValue) {
        post(() -> {
            currentSNR = (float) snrValue;

            // If current SNR exceeds stored max, update it
            if (currentSNR > maxSNR) {
                maxSNR = currentSNR;
            }

            // Recompute the rating
            snrCategory = getSNRRating(currentSNR);

            // Request a redraw
            invalidate();
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Recreate the gradient with the new size
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

        // If in preview, or gradient is null, create a fallback gradient
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

        // Normalize the current SNR into [0..1] range
        float normalizedSNR = (currentSNR + SNR_OFFSET) / SNR_DIVISOR;
        normalizedSNR = clamp(normalizedSNR, 0f, 1f);

        // Calculate the bar height
        float barHeight = getHeight() * normalizedSNR;
        RectF barRect = new RectF(
                0,
                getHeight() - barHeight,
                getWidth(),
                getHeight()
        );

        // Draw the main bar
        canvas.drawRect(barRect, snrPaint);

        // Draw the line for max SNR
        float normalizedMaxSNR = (maxSNR + SNR_OFFSET) / SNR_DIVISOR;
        normalizedMaxSNR = clamp(normalizedMaxSNR, 0f, 1f);

        float maxSNRHeight = getHeight() * normalizedMaxSNR;
        canvas.drawLine(
                0,
                getHeight() - maxSNRHeight,
                getWidth(),
                getHeight() - maxSNRHeight,
                maxSNRPaint
        );

        // Draw SNR category text in the center of the view
        float textX = getWidth() / 2f;
        float textY = getHeight() / 2f;
        canvas.drawText(snrCategory, textX, textY, textPaint);

        // Uncomment if you need to debug values
        // Log.d("SNRBar", "onDraw: currentSNR=" + currentSNR
        //      + ", maxSNR=" + maxSNR
        //      + ", snrCategory=" + snrCategory);
    }

    /**
     * Provides a textual category for a given SNR value.
     * Adjust thresholds as needed for your application.
     *
     * @param snrValue the SNR in dB
     * @return a descriptive string for the SNR quality
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

    /**
     * Clamps a value between two bounds.
     */
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
