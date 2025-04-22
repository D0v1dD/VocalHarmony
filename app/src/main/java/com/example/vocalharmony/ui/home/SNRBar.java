package com.example.vocalharmony.ui.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull; // Import NonNull
import androidx.annotation.Nullable; // Import Nullable for attrs/defStyleAttr
import androidx.core.content.ContextCompat;
import com.example.vocalharmony.R;
import java.util.Locale; // <<< ADDED IMPORT

public class SNRBar extends View {

    private static final String TAG = "SNRBar";
    private static final float SNR_MIN_DB = 0f;
    private static final float SNR_MAX_DB = 30f;

    private float currentAnimatedSNR = SNR_MIN_DB;
    private float currentMaxSNRToDraw = SNR_MIN_DB;

    // Keep fields - Ignore "can be local variable" warning for these
    private Paint snrPaint;
    private Paint maxSNRPaint;
    private LinearGradient gradient;
    private ValueAnimator animator;
    private RectF barRect;

    // Constructors with annotations
    public SNRBar(@NonNull Context context) { // Added NonNull
        super(context);
        init(context);
    }

    public SNRBar(@NonNull Context context, @Nullable AttributeSet attrs) { // Added NonNull/Nullable
        super(context, attrs);
        init(context);
    }

    public SNRBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) { // Added NonNull/Nullable
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) { // Added NonNull
        snrPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maxSNRPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barRect = new RectF();

        // Gradient setup done in onSizeChanged

        maxSNRPaint.setColor(ContextCompat.getColor(context, R.color.max_snr_color));
        maxSNRPaint.setStrokeWidth(5f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        int startColor = ContextCompat.getColor(getContext(), R.color.snrbar_start_color);
        int endColor = ContextCompat.getColor(getContext(), R.color.snrbar_end_color);
        gradient = new LinearGradient(0, h, 0, 0, startColor, endColor, Shader.TileMode.CLAMP);
        snrPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) { // Added NonNull
        super.onDraw(canvas);

        int viewWidth = getWidth();
        int viewHeight = getHeight();

        float normalizedCurrentSNR = normalizeSNR(currentAnimatedSNR);
        float barHeight = viewHeight * normalizedCurrentSNR;
        barRect.set(0, viewHeight - barHeight, viewWidth, viewHeight);
        canvas.drawRect(barRect, snrPaint);

        float normalizedMaxSNR = normalizeSNR(currentMaxSNRToDraw);
        float maxLineY = viewHeight * (1 - normalizedMaxSNR);
        canvas.drawLine(0, maxLineY, viewWidth, maxLineY, maxSNRPaint);
    }

    public void setSNRValue(final double currentSnrValue, final double maxSnrValue) {
        float newTargetSNR = (float) Math.max(SNR_MIN_DB, Math.min(currentSnrValue, SNR_MAX_DB));
        currentMaxSNRToDraw = (float) Math.max(SNR_MIN_DB, Math.min(maxSnrValue, SNR_MAX_DB));

        // Use Locale.US for consistency in logging format if needed, otherwise Locale.getDefault() is often fine
        Log.d(TAG, String.format(Locale.US,"setSNRValue: Received Current=%.1f, Max=%.1f -> Clamped Target=%.1f, MaxToDraw=%.1f",
                currentSnrValue, maxSnrValue, newTargetSNR, currentMaxSNRToDraw));

        if (Math.abs(newTargetSNR - currentAnimatedSNR) > 0.1f) {
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(currentAnimatedSNR, newTargetSNR);
            animator.setDuration(200);
            animator.addUpdateListener(animation -> {
                currentAnimatedSNR = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            invalidate();
        }
    }

    private float normalizeSNR(float snrDb) {
        float range = SNR_MAX_DB - SNR_MIN_DB;
        // REMOVED redundant check: if (range <= 0f) return 0f;
        float clampedSnr = Math.max(SNR_MIN_DB, Math.min(snrDb, SNR_MAX_DB));
        // Added safety check for range still (though unlikely with constants)
        if (range == 0f) return 0f;
        return (clampedSnr - SNR_MIN_DB) / range;
    }

    public void reset() {
        Log.d(TAG, "Resetting SNRBar state.");
        currentAnimatedSNR = SNR_MIN_DB;
        currentMaxSNRToDraw = SNR_MIN_DB;
        if (animator != null) {
            animator.cancel();
        }
        invalidate();
    }
}