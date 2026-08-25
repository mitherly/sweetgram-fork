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

public class SweetgramVerifiedDrawable extends Drawable {

    private Paint paint;
    private Paint textPaint;
    private Path starPath;
    private String text;
    private float textWidth;

    public SweetgramVerifiedDrawable(String text) {
        this.text = text;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Theme.getColor(Theme.key_profile_verifiedBackground));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Theme.getColor(Theme.key_profile_verifiedBackground));
        textPaint.setTextSize(AndroidUtilities.dp(14));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        
        if (this.text != null && !this.text.isEmpty()) {
            textWidth = textPaint.measureText(this.text);
        } else {
            textWidth = 0;
        }

        starPath = new Path();
    }

    private void updateStarPath(float cx, float cy, float radius) {
        starPath.reset();
        int points = 5;
        double angle = Math.PI / points;
        for (int i = 0; i < points * 2; i++) {
            double r = (i % 2 == 0) ? radius : radius / 2.5;
            float x = (float) (cx + Math.cos(i * angle - Math.PI / 2) * r);
            float y = (float) (cy + Math.sin(i * angle - Math.PI / 2) * r);
            if (i == 0) {
                starPath.moveTo(x, y);
            } else {
                starPath.lineTo(x, y);
            }
        }
        starPath.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float starRadius = AndroidUtilities.dp(8);
        float cx = bounds.left + starRadius;
        float cy = bounds.centerY();

        updateStarPath(cx, cy, starRadius);
        canvas.drawPath(starPath, paint);

        if (textWidth > 0) {
            canvas.drawText(text, cx + starRadius + AndroidUtilities.dp(4), cy + AndroidUtilities.dp(5), textPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        textPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
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
            w += AndroidUtilities.dp(4) + textWidth;
        }
        return w;
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(16);
    }
}
