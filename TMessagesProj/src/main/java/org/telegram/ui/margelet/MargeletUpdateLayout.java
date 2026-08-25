package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import org.telegram.margelet.MargeletUpdate;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;

/**
 * Полоска обновления внизу списка чатов — та же, что у телеграма для своих
 * бета-сборок, только про наш форк.
 *
 * Штатную взять не вышло: она умеет качать лишь документ из переписки, через
 * файловую очередь телеграма. Наш apk лежит на гитхабе, поэтому здесь свои
 * нажатия и свой ход скачивания, а вид и повадки — как у штатной, чтобы
 * человеку не пришлось узнавать новое.
 *
 * Помечено @Keep не для красоты. Библиотека прогоняется через R8 отдельно и
 * раньше приложения, а ссылается на этот класс только сборка standalone —
 * с точки зрения библиотеки он никому не нужен, и R8 его выкидывал. Ровно
 * поэтому @Keep висит и на самом IUpdateLayout.
 */
@Keep
public class MargeletUpdateLayout extends IUpdateLayout {

    private FrameLayout updateLayout;
    private RadialProgress2 updateLayoutIcon;
    private AnimatedTextView updateTextView;

    private final Activity activity;
    private final ViewGroup sideMenuContainer;
    private int account;

    @Keep
    public MargeletUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        super(activity, sideMenuContainer);
        this.activity = activity;
        this.sideMenuContainer = sideMenuContainer;
    }

    @Override
    public void createUpdateUI(int currentAccount) {
        if (sideMenuContainer == null || updateLayout != null) {
            return;
        }
        account = currentAccount;
        updateLayout = new FrameLayout(activity);
        updateLayout.setVisibility(View.INVISIBLE);
        updateLayout.setTranslationY(dp(44));
        updateLayout.setBackground(Theme.getSelectorDrawable(0x40ffffff, false));
        sideMenuContainer.addView(updateLayout,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        updateLayout.setOnClickListener(v -> onClick());

        updateTextView = new AnimatedTextView(activity, true, true, true) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.translate(dp(15), 0);
                super.onDraw(canvas);
                canvas.translate((getMeasuredWidth() - width()) / 2f - dp(30), dp(11));
                updateLayoutIcon.draw(canvas);
                canvas.restore();
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return super.verifyDrawable(who);
            }
        };
        updateTextView.setTextSize(dp(15));
        updateTextView.setTypeface(AndroidUtilities.bold());
        updateTextView.setTextColor(0xffffffff);
        updateTextView.setGravity(Gravity.CENTER);
        updateLayout.addView(updateTextView, LayoutHelper.createFrameMatchParent());

        updateLayoutIcon = new RadialProgress2(updateTextView);
        updateLayoutIcon.setColors(0xffffffff, 0xffffffff,
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButton));
        updateLayoutIcon.setProgressRect(0, 0, dp(22), dp(22));
        updateLayoutIcon.setCircleRadius(dp(11));
        updateLayoutIcon.setAsMini();
    }

    /**
     * Одно нажатие делает то, что видно на полоске: качает, отменяет или
     * ставит. Отдельных кнопок нет — их некуда девать в сорока четырёх точках.
     */
    private void onClick() {
        if (MargeletUpdate.downloaded() != null) {
            MargeletUpdate.install(activity);
        } else if (MargeletUpdate.downloading()) {
            MargeletUpdate.cancel();
        } else {
            MargeletUpdate.download(() -> updateAppUpdateViews(account, true));
        }
        updateAppUpdateViews(account, true);
    }

    @Override
    public void updateFileProgress(Object[] args) {
        // Ход скачивания приходит не из файловой очереди телеграма, а из
        // нашей качалки — обновляемся по её же вызову.
    }

    @Override
    public void updateAppUpdateViews(int currentAccount, boolean animated) {
        if (sideMenuContainer == null) {
            return;
        }
        account = currentAccount;
        if (MargeletUpdate.available() != null) {
            createUpdateUI(currentAccount);
            if (MargeletUpdate.downloaded() != null) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_UPDATE, true, animated);
                updateTextView.setText(LocaleController.getString(R.string.AppUpdateNow), animated);
            } else if (MargeletUpdate.downloading()) {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_CANCEL, true, animated);
                updateLayoutIcon.setProgress(MargeletUpdate.progress(), true);
                updateTextView.setText(LocaleController.formatString(R.string.AppUpdateDownloading,
                        (int) (MargeletUpdate.progress() * 100)), animated);
            } else {
                updateLayoutIcon.setIcon(MediaActionDrawable.ICON_DOWNLOAD, true, animated);
                updateTextView.setText(LocaleController.getString(R.string.AppUpdate), animated);
            }
            if (updateLayout.getTag() != null) {
                return;
            }
            updateLayout.setVisibility(View.VISIBLE);
            updateLayout.setTag(1);
            if (animated) {
                updateLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT)
                        .setListener(null).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(0);
            }
        } else {
            if (updateLayout == null || updateLayout.getTag() == null) {
                return;
            }
            updateLayout.setTag(null);
            if (animated) {
                updateLayout.animate().translationY(dp(44)).setInterpolator(CubicBezierInterpolator.EASE_OUT)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (updateLayout.getTag() == null) {
                                    updateLayout.setVisibility(View.INVISIBLE);
                                }
                            }
                        }).setDuration(180).start();
            } else {
                updateLayout.setTranslationY(dp(44));
                updateLayout.setVisibility(View.INVISIBLE);
            }
        }
    }
}
