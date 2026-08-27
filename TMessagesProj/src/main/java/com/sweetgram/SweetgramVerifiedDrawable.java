package com.sweetgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/**
 * Значок верификации Sweetgram — розовый кружок с белым самолётиком внутри
 * (res/drawable/sweetgram_verified), с опциональной подписью (текстом верификации).
 */
public class SweetgramVerifiedDrawable extends Drawable {

    private Drawable logo;
    private Paint textPaint;
    private String text;
    private float textWidth;
    private float badgeSize = AndroidUtilities.dp(16);

    public SweetgramVerifiedDrawable(String text) {
        this.text = text;

        try {
            logo = ApplicationLoader.applicationContext.getDrawable(org.telegram.messenger.R.drawable.sweetgram_verified).mutate();
        } catch (Throwable e) {
            logo = null;
        }

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF4FA9);
        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        if (this.text != null && !this.text.isEmpty()) {
            textWidth = textPaint.measureText(this.text);
        } else {
            textWidth = 0;
        }
    }

    /**
     * Значок для диалога/пира: рисует его, только если пир верифицирован в
     * Sweetgram. Для пользователя и канала/чата передаём одинаковый положительный
     * id (Math.abs(dialogId)), как ожидает {@link SweetgramAuth#isUserVerified(long)}.
     */
    public static Drawable getForPeer(long dialogId) {
        long id = dialogId > 0 ? dialogId : -dialogId;
        SweetgramAuth auth = SweetgramAuth.getInstance();
        if (auth.isUserVerified(id)) {
            return new SweetgramVerifiedDrawable(auth.getVerificationText(id));
        }
        return null;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float cx = bounds.left + badgeSize / 2f;
        float cy = bounds.centerY();

        if (logo != null) {
            logo.setBounds(
                    (int) (cx - badgeSize / 2f),
                    (int) (cy - badgeSize / 2f),
                    (int) (cx + badgeSize / 2f),
                    (int) (cy + badgeSize / 2f)
            );
            logo.draw(canvas);
        }

        if (textWidth > 0) {
            canvas.drawText(text, cx + badgeSize / 2f + AndroidUtilities.dp(4), cy + AndroidUtilities.dp(5), textPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (logo != null) logo.setAlpha(alpha);
        textPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        if (logo != null) logo.setColorFilter(colorFilter);
        textPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        int w = (int) badgeSize;
        if (textWidth > 0) {
            w += AndroidUtilities.dp(4) + (int) textWidth;
        }
        return w;
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) badgeSize;
    }
}
