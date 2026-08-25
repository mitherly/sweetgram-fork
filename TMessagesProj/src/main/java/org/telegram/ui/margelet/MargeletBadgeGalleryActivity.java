package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.margelet.MargeletBadge;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Витрина значков: какие бывают и как значок выглядит у имени.
 *
 * Примерки здесь больше нет. Раньше выбранный значок вставал на своё имя по
 * всему приложению, и это была плохая мысль: значок ничего не удостоверяет,
 * но человек, увидевший его у себя в профиле, легко решит обратное — и
 * покажет знакомым. Теперь выбор живёт только на этом экране: вверху видно,
 * как значок смотрится у имени, и всё. Ходить с ним нельзя.
 */
public class MargeletBadgeGalleryActivity extends UniversalFragment {

    private Preview preview;
    private int chosen;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletBadgeGallery);
    }

    @Override
    public View createView(Context context) {
        preview = null;
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final MargeletBadge.Badge[] badges = MargeletBadge.list();
        if (chosen < 0 || chosen >= badges.length) {
            chosen = 0;
        }
        if (preview == null && getContext() != null) {
            preview = new Preview(getContext());
        }
        if (preview != null) {
            preview.set(badges[chosen]);
            items.add(UItem.asCustomShadow(preview, 92));
        }
        for (int i = 0; i < badges.length; i++) {
            items.add(UItem.asRadio(i, badges[i].title())
                    .setChecked(chosen == i));
        }
        items.add(UItem.asShadow(badges[chosen].about()));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= 0 && item.id < MargeletBadge.list().length) {
            chosen = item.id;
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    /**
     * Кусок профиля: круг вместо снимка, своё имя и значок за ним. Ровно то
     * место, где значок и появляется на настоящей странице.
     */
    private static class Preview extends FrameLayout {

        private final TextView nameView;
        private final ImageView badgeView;

        Preview(Context context) {
            super(context);

            final View avatar = new View(context) {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(Canvas canvas) {
                    paint.setColor(Theme.getColor(Theme.key_avatar_backgroundGreen));
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
                            Math.min(getWidth(), getHeight()) / 2f, paint);
                }
            };
            addView(avatar, LayoutHelper.createFrame(54, 54, Gravity.LEFT | Gravity.CENTER_VERTICAL, 20, 0, 0, 0));

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            nameView.setSingleLine(true);
            nameView.setText(name());
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.LEFT | Gravity.CENTER_VERTICAL, 86, 0, 0, 0));

            badgeView = new ImageView(context);
            addView(badgeView, LayoutHelper.createFrame(20, 20,
                    Gravity.LEFT | Gravity.CENTER_VERTICAL, 86, 0, 0, 0));

            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }

        private static String name() {
            try {
                final org.telegram.tgnet.TLRPC.User self = UserConfig
                        .getInstance(UserConfig.selectedAccount).getCurrentUser();
                if (self != null && self.first_name != null) {
                    return self.first_name;
                }
            } catch (Throwable ignored) {
            }
            return LocaleController.getString(R.string.MargeletBadgeGallery);
        }

        void set(MargeletBadge.Badge badge) {
            badgeView.setImageDrawable(MargeletBadge.iconDrawable(getContext(), badge));
            requestLayout();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            // Значок стоит сразу за именем, как и в профиле. Ширину имени
            // знаем только после измерения, поэтому двигаем здесь.
            badgeView.setTranslationX(nameView.getWidth() + AndroidUtilities.dp(6));
        }
    }
}
