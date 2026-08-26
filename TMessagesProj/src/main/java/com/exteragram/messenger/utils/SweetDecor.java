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
     * Розовые сердечки со свечением, рассыпанные по шапке профиля: одно
     * заметное справа и несколько мелких вокруг, чтобы композиция смотрелась,
     * а не одинокий значок в углу.
     */
    public static void drawHeaderHearts(Canvas canvas, float width, float height, float alpha) {
        if (alpha <= 0.01f || width <= 0 || height <= 0) {
            return;
        }

        int saveCount = canvas.save();

        // Главное сердце — правее центра, как раньше.
        drawHeart(canvas, width - AndroidUtilities.dp(85), height * 0.45f, AndroidUtilities.dp(16), alpha);

        // Рассыпанные помельче: разный размер и прозрачность, чтобы
        // смотрелось живо, а не решёткой.
        drawHeart(canvas, width - AndroidUtilities.dp(150), height * 0.28f, AndroidUtilities.dp(10), alpha * 0.85f);
        drawHeart(canvas, width - AndroidUtilities.dp(205), height * 0.62f, AndroidUtilities.dp(8), alpha * 0.70f);
        drawHeart(canvas, width - AndroidUtilities.dp(118), height * 0.74f, AndroidUtilities.dp(7), alpha * 0.65f);
        drawHeart(canvas, width * 0.38f, height * 0.32f, AndroidUtilities.dp(9), alpha * 0.55f);
        drawHeart(canvas, width * 0.16f, height * 0.58f, AndroidUtilities.dp(6), alpha * 0.45f);

        canvas.restoreToCount(saveCount);
    }

    /**
     * Одно сердце с розовым свечением и бликом.
     */
    private static void drawHeart(Canvas canvas, float centerX, float centerY, float size, float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        alpha = Math.min(1f, alpha);

        // 1. Мягкое розовое свечение под сердцем.
        glowPaint.setShader(new RadialGradient(centerX, centerY, size * 1.8f,
                Color.argb((int) (70 * alpha), 0xF8, 0xC8, 0xDC),
                Color.argb(0, 0xF8, 0xC8, 0xDC),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, centerY, size * 1.8f, glowPaint);
        glowPaint.setShader(null);

        heartPath.reset();
        float topY = centerY - size * 0.4f;
        float bottomY = centerY + size * 0.6f;
        float leftX = centerX - size * 0.6f;
        float rightX = centerX + size * 0.6f;

        heartPath.moveTo(centerX, topY + size * 0.3f);
        heartPath.cubicTo(centerX - size * 0.4f, topY - size * 0.3f, leftX, topY + size * 0.2f, centerX, bottomY);
        heartPath.cubicTo(rightX, topY + size * 0.2f, centerX + size * 0.4f, topY - size * 0.3f, centerX, topY + size * 0.3f);
        heartPath.close();

        // 2. Само сердце — нежно-розовое.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (110 * alpha), 0xE5, 0x9C, 0xB8));
        canvas.drawPath(heartPath, paint);

        // 3. Светлый блик — стеклянный вид.
        paint.setColor(Color.argb((int) (160 * alpha), 0xFC, 0xEF, 0xF5));
        canvas.drawCircle(centerX - size * 0.22f, centerY - size * 0.15f, size * 0.12f, paint);

        // 4. Искры-звёздочки рядом.
        drawSparkleStar(canvas, centerX + size * 1.1f, centerY - size * 0.4f, AndroidUtilities.dp(5), (int) (180 * alpha));
        drawSparkleStar(canvas, centerX - size * 1.0f, centerY + size * 0.2f, AndroidUtilities.dp(3.5f), (int) (150 * alpha));
        drawSparkleStar(canvas, centerX + size * 0.6f, centerY + size * 0.7f, AndroidUtilities.dp(2.5f), (int) (130 * alpha));
    }

    /**
     * Draws a delicate 4-pointed diamond sparkle star.
     */
    public static void drawSparkleStar(Canvas canvas, float cx, float cy, float radius, int alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.min(255, Math.max(0, alpha)), 0xFC, 0xEF, 0xF5));

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
