package org.telegram.sweetgram;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import com.sweetgram.SweetgramAuth;
import com.sweetgram.SweetgramVerifiedDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Значки верификации Sweetgram. Обёртка над {@link SweetgramAuth}: показывает
 * розовый лого-значок ({@link SweetgramVerifiedDrawable}) для верифицированных
 * пользователей и каналов, а по тапу — подпись верификации.
 */
public class SweetgramBadge {

    public static class Badge {
        public final long peerId;
        public final Drawable icon;
        public final String title;

        public Badge(long peerId, Drawable icon, String title) {
            this.peerId = peerId;
            this.icon = icon;
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** id чата (отрицательный) → peer-id, с которым работает SweetgramAuth. */
    public static long chatPeer(long chatId) {
        return -chatId;
    }

    private static long absPeer(long peerId) {
        return peerId > 0 ? peerId : -peerId;
    }

    public static boolean has(long peerId) {
        return SweetgramAuth.getInstance().isUserVerified(absPeer(peerId));
    }

    public static List<Badge> all(long peerId) {
        List<Badge> result = new ArrayList<>();
        if (has(peerId)) {
            Drawable icon = new SweetgramVerifiedDrawable(null);
            String text = SweetgramAuth.getInstance().getVerificationText(absPeer(peerId));
            result.add(new Badge(peerId, icon, text));
        }
        return result;
    }

    public static Drawable iconDrawable(Context context, long peerId) {
        return new SweetgramVerifiedDrawable(null);
    }

    public static Drawable iconDrawable(Context context, Badge badge) {
        return badge != null ? badge.icon : null;
    }

    public static String title(long peerId) {
        return SweetgramAuth.getInstance().getVerificationText(absPeer(peerId));
    }

    public static void show(Context context, long peerId) {
        if (context == null) {
            return;
        }
        Toast.makeText(context, SweetgramAuth.getInstance().getVerificationText(absPeer(peerId)), Toast.LENGTH_LONG).show();
    }

    public static void show(Context context, Badge badge) {
        if (context == null || badge == null) {
            return;
        }
        show(context, badge.peerId);
    }
}
