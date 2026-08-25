package com.exteragram.messenger.utils;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;

public class SweetDecor {

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path heartPath = new Path();

    /**
     * Draws subtle glowing heart bubble & 4-pointed sparkle stars on the right side of header bars.
     */
    public static void drawHeaderHearts(Canvas canvas, float width, float height, float alpha) {
        if (alpha <= 0.01f || width <= 0 || height <= 0) {
            return;
        }

        int saveCount = canvas.save();

        float centerX = width - AndroidUtilities.dp(85);
        float centerY = height * 0.45f;
        float size = AndroidUtilities.dp(16);

        // 1. Soft radial glow behind heart
        glowPaint.setShader(new RadialGradient(centerX, centerY, size * 1.8f,
                Color.argb((int) (60 * alpha), 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, centerY, size * 1.8f, glowPaint);

        // 2. Translucent heart shape
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (90 * alpha), 255, 255, 255));

        heartPath.reset();
        float topY = centerY - size * 0.4f;
        float bottomY = centerY + size * 0.6f;
        float leftX = centerX - size * 0.6f;
        float rightX = centerX + size * 0.6f;

        heartPath.moveTo(centerX, topY + size * 0.3f);
        heartPath.cubicTo(centerX - size * 0.4f, topY - size * 0.3f, leftX, topY + size * 0.2f, centerX, bottomY);
        heartPath.cubicTo(rightX, topY + size * 0.2f, centerX + size * 0.4f, topY - size * 0.3f, centerX, topY + size * 0.3f);
        heartPath.close();

        canvas.drawPath(heartPath, paint);

        // 3. Heart specular highlight (glass bubble look)
        paint.setColor(Color.argb((int) (140 * alpha), 255, 255, 255));
        canvas.drawCircle(centerX - size * 0.22f, centerY - size * 0.15f, size * 0.12f, paint);

        // 4. Sparkle stars
        drawSparkleStar(canvas, centerX + size * 1.1f, centerY - size * 0.4f, AndroidUtilities.dp(5), (int) (180 * alpha));
        drawSparkleStar(canvas, centerX - size * 1.0f, centerY + size * 0.2f, AndroidUtilities.dp(3.5f), (int) (150 * alpha));
        drawSparkleStar(canvas, centerX + size * 0.6f, centerY + size * 0.7f, AndroidUtilities.dp(2.5f), (int) (130 * alpha));

        canvas.restoreToCount(saveCount);
    }

    /**
     * Draws a delicate 4-pointed diamond sparkle star.
     */
    public static void drawSparkleStar(Canvas canvas, float cx, float cy, float radius, int alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.min(255, Math.max(0, alpha)), 255, 255, 255));

        Path path = new Path();
        path.moveTo(cx, cy - radius);
        path.quadTo(cx, cy, cx + radius * 0.3f, cy);
        path.quadTo(cx, cy, cx, cy + radius);
        path.quadTo(cx, cy, cx - radius * 0.3f, cy);
        path.quadTo(cx, cy, cx, cy - radius);
        path.close();

        // horizontal beam
        Path hPath = new Path();
        hPath.moveTo(cx - radius, cy);
        hPath.quadTo(cx, cy, cx, cy - radius * 0.3f);
        hPath.quadTo(cx, cy, cx + radius, cy);
        hPath.quadTo(cx, cy, cx, cy + radius * 0.3f);
        hPath.quadTo(cx, cy, cx - radius, cy);
        hPath.close();

        canvas.drawPath(path, paint);
        canvas.drawPath(hPath, paint);
        canvas.drawCircle(cx, cy, radius * 0.2f, paint);
    }
}
