package org.telegram.sweetgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * Название форка, нарисованное текстом вместо картинки.
 *
 * На главном экране телеграм показывает имя двумя способами: развёрнутый
 * заголовок — настоящей надписью, а свёрнутый — картинкой со словом
 * «Telegram». Владелец это и заметил: имя менялось только когда развёрнуты
 * истории. Картинку рисовать смысла нет, поэтому здесь просто текст,
 * притворяющийся картинкой.
 *
 * Красится он белым, потому что поверх стоит цветовой фильтр умножением:
 * белое умножается на цвет темы и становится этим цветом.
 */
public class SweetgramWordmark extends Drawable {

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int height;

    public SweetgramWordmark() {
        this(20);
    }

    /** Высота букв. На главном экране одна, на экране до входа крупнее. */
    public SweetgramWordmark(int heightDp) {
        height = AndroidUtilities.dp(heightDp);
        paint.setTypeface(AndroidUtilities.bold());
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(height);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        paint.setTextSize(height);
        float w = paint.measureText(SweetgramConfig.APP_NAME);
        if (w > b.width() && w > 0) {
            paint.setTextSize(height * b.width() / w);
            w = paint.measureText(SweetgramConfig.APP_NAME);
        }
        final Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(SweetgramConfig.APP_NAME, b.left,
                b.centerY() - (fm.ascent + fm.descent) / 2f, paint);
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) Math.ceil(paint.measureText(SweetgramConfig.APP_NAME));
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
