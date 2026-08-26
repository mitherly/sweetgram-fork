package com.sweetgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Значок верификации Sweetgram: розовый кружок с белым контуром
 * самолётика и текст подписи рядом.
 */
public class SweetgramVerifiedDrawable extends Drawable {

    private final Paint circlePaint;
    private final Paint planePaint;
    private final Paint textPaint;
    private final Path planePath = new Path();
    private final String text;
    private float textWidth;

    public SweetgramVerifiedDrawable(String text) {
        this.text = text;

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(0xFFE59CB8);

        planePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        planePaint.setStyle(Paint.Style.STROKE);
        planePaint.setStrokeWidth(AndroidUtilities.dp(1.4f));
        planePaint.setStrokeJoin(Paint.Join.ROUND);
        planePaint.setStrokeCap(Paint.Cap.ROUND);
        planePaint.setColor(0xFFFFFFFF);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Theme.getColor(Theme.key_profile_verifiedBackground));
        textPaint.setTextSize(AndroidUtilities.dp(14));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        if (this.text != null && !this.text.isEmpty()) {
            textWidth = textPaint.measureText(this.text);
        }
    }

    /** Контур бумажного самолётика вписан в круг диаметром size. */
    private void updatePlanePath(float cx, float cy, float size) {
        // Точки силуэта самолётика относительно радиуса круга.
        float s = size / 2f;
        float noseX = cx + s * 0.78f, noseY = cy - s * 0.72f;
        float tailX = cx - s * 0.80f, tailY = cy + s * 0.06f;
        float foldX = cx - s * 0.26f, foldY = cy + s * 0.30f;
        float tipX = cx - s * 0.08f, tipY = cy + s * 0.78f;

        planePath.reset();
        planePath.moveTo(noseX, noseY);
        planePath.lineTo(tailX, tailY);
        planePath.lineTo(foldX, foldY);
        planePath.close();
        planePath.moveTo(foldX, foldY);
        planePath.lineTo(tipX, tipY);
        planePath.lineTo(noseX, noseY);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float radius = AndroidUtilities.dp(8);
        float cx = bounds.left + radius;
        float cy = bounds.centerY();

        canvas.drawCircle(cx, cy, radius, circlePaint);

        updatePlanePath(cx, cy, radius * 2f);
        canvas.drawPath(planePath, planePaint);

        if (textWidth > 0) {
            canvas.drawText(text, cx + radius + AndroidUtilities.dp(4), cy + AndroidUtilities.dp(5), textPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        circlePaint.setAlpha(alpha);
        planePaint.setAlpha(alpha);
        textPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        circlePaint.setColorFilter(colorFilter);
        planePaint.setColorFilter(colorFilter);
        textPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        int w = AndroidUtilities.dp(16);
        if (textWidth > 0) {
            w += AndroidUtilities.dp(4) + (int) textWidth;
        }
        return w;
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(16);
    }
}
