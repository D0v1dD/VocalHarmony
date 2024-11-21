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

    private void init(Context context, AttributeSet attrs) {
        snrPaint = new Paint();
        maxSNRPaint = new Paint();
        textPaint = new Paint();

        if (isInEditMode()) {
            // Provide hardcoded colors for the editor
            startColor = 0xFF00FF00; // Green
            endColor = 0xFF0000FF;   // Blue
            maxSNRPaint.setColor(0xFFFF0000); // Red
            textPaint.setColor(0xFF000000);   // Black
        } else {
            // Initialize colors from resources
            maxSNRPaint.setColor(ContextCompat.getColor(context, R.color.max_snr_color));
            textPaint.setColor(ContextCompat.getColor(context, R.color.snrbar_text_color));

            // Handle custom attributes
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
                // Set default colors if no attributes are provided
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

    public void setSNRValue(final double snrValue) {
        // Ensure updates happen on the main thread
        post(() -> {
            currentSNR = (float) snrValue;

            // Update max SNR if the current value is greater
            if (currentSNR > maxSNR) {
                maxSNR = currentSNR;
            }

            // Update SNR category
            snrCategory = getSNRRating(currentSNR);

            // Trigger redraw
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Initialize gradient if not already done
        if (gradient == null) {
            gradient = new LinearGradient(0, 0, 0, getHeight(),
                    startColor,
                    endColor,
                    Shader.TileMode.CLAMP);
            snrPaint.setShader(gradient);
        }

        // Normalize SNR value to a 0-1 range for the bar height
        float normalizedSNR = (currentSNR + 20) / 100f;  // Adjust based on expected SNR range
        normalizedSNR = Math.max(0, Math.min(normalizedSNR, 1));  // Clamp between 0 and 1

        float barHeight = getHeight() * normalizedSNR;
        RectF rect = new RectF(0, getHeight() - barHeight, getWidth(), getHeight());
        canvas.drawRect(rect, snrPaint);

        // Draw the maximum SNR indicator
        float normalizedMaxSNR = (maxSNR + 20) / 100f;
        normalizedMaxSNR = Math.max(0, Math.min(normalizedMaxSNR, 1));
        float maxSNRHeight = getHeight() * normalizedMaxSNR;
        canvas.drawLine(0, getHeight() - maxSNRHeight, getWidth(), getHeight() - maxSNRHeight, maxSNRPaint);

        // Draw the SNR category text
        canvas.drawText(snrCategory, getWidth() / 2f, getHeight() / 2f, textPaint);
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

    // Provide feedback on the SNR quality
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
