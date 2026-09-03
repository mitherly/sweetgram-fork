package org.telegram.ui.sweetgram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.sweetgram.SweetgramOwnBadge;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Витрина значков: какие бывают и какой у тебя стоит.
 *
 * Рядом с образцом — как значок смотрится у имени: ровно то место, где он и
 * появляется на настоящей странице. Выбор здесь настоящий: значок уезжает в
 * общую группу и встаёт у имени для всех, у кого стоит Sweetgram.
 */
public class SweetgramBadgeGalleryActivity extends UniversalFragment {

    private Preview preview;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramBadgeGallery);
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
        if (getContext() == null) {
            return;
        }
        if (preview == null) {
            preview = new Preview(getContext());
        }
        final int mine = SweetgramOwnBadge.of(UserConfig.getInstance(currentAccount).getClientUserId());
        preview.set(mine > 0 ? SweetgramOwnBadge.byId(mine) : null);
        items.add(UItem.asCustomShadow(preview, 84));
        for (SweetgramOwnBadge.Kind kind : SweetgramOwnBadge.kinds()) {
            items.add(UItem.asCustom(new BadgeCell(getContext(), kind, kind.id == mine, this::pick), 64));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramBadgeGalleryPick)));
        items.add(UItem.asButton(ID_REMOVE, LocaleController.getString(R.string.SweetgramBadgeGalleryRemove)));
        items.add(UItem.asShadow("Based on Margy (@margeletter , github.com/narezany/Margelet)"));
    }

    private static final int ID_REMOVE = 100;

    /** Тап по значку в списке: спрашивать нечего, ставим сразу. */
    private void pick(SweetgramOwnBadge.Kind kind) {
        SweetgramOwnBadge.set(kind, what -> {
            if (what == SweetgramOwnBadge.FAILED) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                        LocaleController.getString(R.string.SweetgramGroupUnreachable)).show();
                return;
            }
            BulletinFactory.of(this).createSimpleBulletin(
                    new SweetgramOwnBadge.BadgeDrawable(kind),
                    LocaleController.getString(R.string.SweetgramBadgeSent)).show();
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_REMOVE) {
            new AlertDialog.Builder(getContext())
                    .setTitle(LocaleController.getString(R.string.SweetgramBadgeGalleryRemove))
                    .setMessage(LocaleController.getString(R.string.SweetgramBadgeRemoveWarn))
                    .setPositiveButton(LocaleController.getString(R.string.Continue), (d, w) ->
                            SweetgramOwnBadge.clear(what -> {
                                if (what == SweetgramOwnBadge.REMOVED) {
                                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                                            LocaleController.getString(R.string.SweetgramBadgeRemoved)).show();
                                } else if (what == SweetgramOwnBadge.NOTHING) {
                                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                                            LocaleController.getString(R.string.SweetgramBadgeNone)).show();
                                } else {
                                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                                            LocaleController.getString(R.string.SweetgramGroupUnreachable)).show();
                                }
                                if (listView != null) {
                                    listView.adapter.update(true);
                                }
                            }))
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
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
    private static class Preview extends View {

        private final Paint avatarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private SweetgramOwnBadge.BadgeDrawable badge;

        Preview(Context context) {
            super(context);
            textPaint.setTypeface(AndroidUtilities.bold());
        }

        void set(SweetgramOwnBadge.Kind kind) {
            badge = kind == null ? null : new SweetgramOwnBadge.BadgeDrawable(kind);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final org.telegram.tgnet.TLRPC.User me =
                    UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            final String name = me != null && !org.telegram.messenger.UserObject.getFirstName(me).isEmpty()
                    ? org.telegram.messenger.UserObject.getFirstName(me)
                    : "Sweetgram";
            final int avatarSize = AndroidUtilities.dp(44);
            final int left = (getMeasuredWidth() - AndroidUtilities.dp(280)) / 2;
            final int cy = AndroidUtilities.dp(42);

            final int color = AvatarDrawable.getColorForId(
                    UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
            avatarPaint.setColor(color);
            final RectF circle = new RectF(left, cy - avatarSize / 2f,
                    left + avatarSize, cy + avatarSize / 2f);
            canvas.drawOval(circle, avatarPaint);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(avatarSize * 0.42f);
            final String initial = name.substring(0, 1).toUpperCase();
            canvas.drawText(initial, left + avatarSize / 2f
                            - textPaint.measureText(initial) / 2f,
                    cy + textPaint.getTextSize() * 0.35f, textPaint);

            final float nameX = left + avatarSize + AndroidUtilities.dp(12);
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textPaint.setTextSize(AndroidUtilities.dp(17));
            canvas.drawText(name, nameX, cy + AndroidUtilities.dp(6), textPaint);
            if (badge != null) {
                badge.setBounds((int) (nameX + textPaint.measureText(name)) + AndroidUtilities.dp(6),
                        cy - AndroidUtilities.dp(8),
                        (int) (nameX + textPaint.measureText(name)) + AndroidUtilities.dp(6) + AndroidUtilities.dp(16),
                        cy + AndroidUtilities.dp(8));
                badge.draw(canvas);
            }
        }
    }

    /** Строка каталога: значок, название, описание и отметка «стоит у вас». */
    private static final class BadgeCell extends FrameLayout {

        private final ImageView iconView;
        private final TextView titleView;
        private final TextView aboutView;
        private final View mark;
        private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BadgeCell(Context context, SweetgramOwnBadge.Kind kind, boolean checked, java.util.function.Consumer<SweetgramOwnBadge.Kind> onClick) {
            super(context);

            iconView = new ImageView(context);
            iconView.setImageDrawable(new SweetgramOwnBadge.BadgeDrawable(kind));
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(iconView, LayoutHelper.createFrame(30, 30, Gravity.CENTER_VERTICAL, 18, 0, 0, 0));

            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            titleView = new TextView(context);
            titleView.setTextSize(16);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setText(kind.title());
            texts.addView(titleView);
            aboutView = new TextView(context);
            aboutView.setTextSize(13);
            aboutView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            aboutView.setText(kind.about());
            texts.addView(aboutView);
            addView(texts, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.LEFT, 64, 0, 64, 0));

            mark = new View(context);
            addView(mark, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 20, 0));

            setChecked(checked);
            setOnClickListener(v -> onClick.accept(kind));
            setBackground(Theme.createSelectorWithBackgroundDrawable(
                    Theme.getColor(Theme.key_windowBackgroundWhite),
                    Theme.getColor(Theme.key_listSelector)));
        }

        void setChecked(boolean checked) {
            mark.setVisibility(checked ? VISIBLE : INVISIBLE);
            markPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            mark.invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (mark.getVisibility() == VISIBLE) {
                final int cx = (int) (mark.getLeft() + mark.getMeasuredWidth() / 2f);
                final int cy = (int) (mark.getTop() + mark.getMeasuredHeight() / 2f);
                markPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, AndroidUtilities.dp(10), markPaint);
                markPaint.setColor(0xFFFFFFFF);
                markPaint.setStyle(Paint.Style.STROKE);
                markPaint.setStrokeWidth(AndroidUtilities.dp(2));
                canvas.drawLine(cx - AndroidUtilities.dp(4.5f), cy,
                        cx - AndroidUtilities.dp(1.5f), cy + AndroidUtilities.dp(3.5f), markPaint);
                canvas.drawLine(cx - AndroidUtilities.dp(1.5f), cy + AndroidUtilities.dp(3.5f),
                        cx + AndroidUtilities.dp(4.5f), cy - AndroidUtilities.dp(3), markPaint);
                markPaint.setStyle(Paint.Style.FILL);
                markPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            }
        }
    }
}
