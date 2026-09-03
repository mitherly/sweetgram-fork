package org.telegram.sweetgram;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import com.sweetgram.SweetgramAuth;
import com.sweetgram.SweetgramVerifiedDrawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Значки у имени. Два вида: верификация форка (розовый лого-значок,
 * {@link SweetgramVerifiedDrawable}, выдаёт владелец через Firebase) и свой
 * значок — человек выбирает сам из каталога, {@link SweetgramOwnBadge}.
 * Верификация главнее и стоит первой, свой значок идёт следом.
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
        return SweetgramAuth.getInstance().isUserVerified(absPeer(peerId)) || ownKind(peerId) != null;
    }

    public static List<Badge> all(long peerId) {
        List<Badge> result = new ArrayList<>();
        if (SweetgramAuth.getInstance().isUserVerified(absPeer(peerId))) {
            Drawable icon = new SweetgramVerifiedDrawable(null);
            String text = SweetgramAuth.getInstance().getVerificationText(absPeer(peerId));
            result.add(new Badge(peerId, icon, text));
        }
        // Свой значок идёт после верификации: она главнее — её ставит форк, а
        // не сам человек. Порядок здесь и есть порядок старшинства.
        final SweetgramOwnBadge.Kind own = ownKind(peerId);
        if (own != null) {
            result.add(new Badge(peerId, SweetgramOwnBadge.icon(own), own.title() + ". " + own.about()));
        }
        return result;
    }

    /** Свой значок этого человека или null. Попутно просит его у группы. */
    private static SweetgramOwnBadge.Kind ownKind(long peerId) {
        final int id = SweetgramOwnBadge.of(peerId);
        return id > 0 ? SweetgramOwnBadge.byId(id) : null;
    }

    public static Drawable iconDrawable(Context context, long peerId) {
        final SweetgramOwnBadge.Kind own = ownKind(peerId);
        if (own != null) {
            return SweetgramOwnBadge.icon(own);
        }
        return new SweetgramVerifiedDrawable(null);
    }

    public static Drawable iconDrawable(Context context, Badge badge) {
        return badge != null ? badge.icon : null;
    }

    public static String title(long peerId) {
        final SweetgramOwnBadge.Kind own = ownKind(peerId);
        if (own != null && !SweetgramAuth.getInstance().isUserVerified(absPeer(peerId))) {
            return own.title();
        }
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
        // Свой значок: показать и рисунок, не только слова.
        final SweetgramOwnBadge.Kind own = ownKind(badge.peerId);
        if (own != null && !(badge.icon instanceof com.sweetgram.SweetgramVerifiedDrawable)) {
            final android.widget.ImageView picture = new android.widget.ImageView(context);
            picture.setImageDrawable(new SweetgramOwnBadge.BadgeDrawable(own));
            final android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            layout.addView(picture, org.telegram.ui.Components.LayoutHelper.createLinear(72, 72, 0, 12, 0, 0));
            final android.widget.TextView text = new android.widget.TextView(context);
            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setGravity(android.view.Gravity.CENTER);
            text.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
            text.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
            text.setText(own.title() + ". " + own.about());
            layout.addView(text, org.telegram.ui.Components.LayoutHelper.createLinear(-1, -2));
            new org.telegram.ui.ActionBar.AlertDialog.Builder(context)
                    .setTitle(own.title())
                    .setView(layout)
                    .setPositiveButton(LocaleController.getString(R.string.Close), null)
                    .show();
            return;
        }
        show(context, badge.peerId);
    }
}
