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

import androidx.core.content.ContextCompat;

import com.example.vocalharmony.R;

public class SNRBar extends View {

    private static final String TAG = "SNRBar";
    private static final float SNR_MIN_DB = -20f;
    private static final float SNR_MAX_DB = 40f;

    private float currentSNR = SNR_MIN_DB;
    private float maxSNR = SNR_MIN_DB;
    private Paint snrPaint;
    private Paint maxSNRPaint;
    private Paint textPaint;
    private LinearGradient gradient;
    private ValueAnimator animator;
    private RectF barRect;

    public SNRBar(Context context) {
        super(context);
        init(context);
    }

    public SNRBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SNRBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        snrPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maxSNRPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barRect = new RectF();

        int startColor = ContextCompat.getColor(context, R.color.snrbar_start_color);
        int endColor = ContextCompat.getColor(context, R.color.snrbar_end_color);

        gradient = new LinearGradient(0, 0, 0, 0, startColor, endColor, Shader.TileMode.CLAMP);
        snrPaint.setShader(gradient);

        maxSNRPaint.setColor(ContextCompat.getColor(context, R.color.max_snr_color));
        maxSNRPaint.setStrokeWidth(5f);

        textPaint.setColor(ContextCompat.getColor(context, R.color.snrbar_text_color));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        int startColor = ContextCompat.getColor(getContext(), R.color.snrbar_start_color);
        int endColor = ContextCompat.getColor(getContext(), R.color.snrbar_end_color);
        gradient = new LinearGradient(0, h, 0, 0, startColor, endColor, Shader.TileMode.CLAMP);
        snrPaint.setShader(gradient);

        textPaint.setTextSize(h * 0.1f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float normalizedSNR = normalizeSNR(currentSNR);
        float barHeight = getHeight() * normalizedSNR;

        barRect.set(0, getHeight() - barHeight, getWidth(), getHeight());
        canvas.drawRect(barRect, snrPaint);

        float maxSNRHeight = getHeight() * normalizeSNR(maxSNR);
        canvas.drawLine(0, getHeight() - maxSNRHeight, getWidth(), getHeight() - maxSNRHeight, maxSNRPaint);

        String snrText = getSNRRating(currentSNR);
        if (currentSNR <= SNR_MIN_DB) {
            snrText = "No Signal";
        }

        canvas.drawText(snrText, getWidth() / 2f, getHeight() * 0.85f, textPaint);
    }

    public void setSNRValue(final double snrValue) {
        float newSNR = (float) Math.max(SNR_MIN_DB, Math.min(snrValue, SNR_MAX_DB));

        Log.d(TAG, "setSNRValue: Received SNR = " + snrValue + ", Clamped SNR = " + newSNR);

        if (Math.abs(newSNR - currentSNR) > 0.1) {
            if (animator != null) {
                animator.cancel();
            }

            animator = ValueAnimator.ofFloat(currentSNR, newSNR);
            animator.setDuration(300);
            animator.addUpdateListener(animation -> {
                currentSNR = (float) animation.getAnimatedValue();
                maxSNR = Math.max(maxSNR, currentSNR);
                Log.d(TAG, "setSNRValue: Current SNR = " + currentSNR + ", Max SNR = " + maxSNR);
                invalidate();
            });
            animator.start();
        }
    }

    private float normalizeSNR(float snrDb) {
        return (snrDb - SNR_MIN_DB) / (SNR_MAX_DB - SNR_MIN_DB);
    }

    private String getSNRRating(float snrValue) {
        if (snrValue <= SNR_MIN_DB) return "No Signal";
        if (snrValue < 0) return "Very Poor";
        if (snrValue < 5) return "Poor";
        if (snrValue < 10) return "Fair";
        if (snrValue < 15) return "Good";
        if (snrValue < 20) return "Very Good";
        return "Excellent";
    }
}
