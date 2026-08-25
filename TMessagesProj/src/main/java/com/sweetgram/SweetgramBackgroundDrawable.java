package com.radolyn.ayugram.sweetgram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SweetgramBackgroundDrawable extends Drawable {
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path cloudPath = new Path();

    public SweetgramBackgroundDrawable() {
        cloudPaint.setColor(Color.argb(60, 255, 255, 255));
        starPaint.setColor(Color.argb(100, 255, 255, 255));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        LinearGradient gradient = new LinearGradient(
                0, 0, 0, bounds.bottom,
                new int[]{Color.parseColor("#FFC0CB"), Color.parseColor("#FFB6C1"), Color.parseColor("#FFF0F5")},
                null, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(bounds, paint);

        // Draw simple clouds
        drawCloud(canvas, bounds.width() * 0.2f, bounds.height() * 0.8f, bounds.width() * 0.15f);
        drawCloud(canvas, bounds.width() * 0.8f, bounds.height() * 0.9f, bounds.width() * 0.2f);
        drawCloud(canvas, bounds.width() * 0.5f, bounds.height() * 0.95f, bounds.width() * 0.3f);

        // Draw simple stars
        drawStar(canvas, bounds.width() * 0.1f, bounds.height() * 0.1f, 10f);
        drawStar(canvas, bounds.width() * 0.85f, bounds.height() * 0.15f, 15f);
        drawStar(canvas, bounds.width() * 0.5f, bounds.height() * 0.05f, 8f);
        drawStar(canvas, bounds.width() * 0.2f, bounds.height() * 0.4f, 12f);
    }

    private void drawCloud(Canvas canvas, float cx, float cy, float radius) {
        cloudPath.reset();
        cloudPath.addCircle(cx, cy, radius, Path.Direction.CW);
        cloudPath.addCircle(cx - radius * 0.6f, cy + radius * 0.2f, radius * 0.7f, Path.Direction.CW);
        cloudPath.addCircle(cx + radius * 0.6f, cy + radius * 0.2f, radius * 0.8f, Path.Direction.CW);
        canvas.drawPath(cloudPath, cloudPaint);
    }

    private void drawStar(Canvas canvas, float cx, float cy, float radius) {
        Path starPath = new Path();
        int points = 4;
        double angle = Math.PI / points;
        for (int i = 0; i < points * 2; i++) {
            double r = (i % 2 == 0) ? radius : radius / 3;
            float x = (float) (cx + Math.cos(i * angle - Math.PI / 2) * r);
            float y = (float) (cy + Math.sin(i * angle - Math.PI / 2) * r);
            if (i == 0) {
                starPath.moveTo(x, y);
            } else {
                starPath.lineTo(x, y);
            }
        }
        starPath.close();
        canvas.drawPath(starPath, starPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        cloudPaint.setAlpha(alpha);
        starPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        cloudPaint.setColorFilter(colorFilter);
        starPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
