package org.telegram.sweetgram.drawer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.sweetgram.SweetgramAvatars;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;

public class DrawerHeaderView extends FrameLayout {
    private final AvatarDrawable avatarDrawable;
    private final BackupImageView avatarView;
    private final SimpleTextView nameView;
    private final SimpleTextView subtitleView;
    private final RLottieDrawable sunDrawable;
    private final RLottieImageView themeToggleView;
    private final FrameLayout themeToggleBg;
    private Runnable onChevronClick;
    private final ImageView chevronView;

    public DrawerHeaderView(Context context) {
        super(context);
        avatarDrawable = new AvatarDrawable();
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(SweetgramAvatars.getAvatarCorners(72.0f, false));
        addView(avatarView, LayoutHelper.createFrame(72, 72.0f, 51, 16.0f, 16.0f, 0.0f, 0.0f));
        avatarView.setOnClickListener(v -> {
            TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            if (user != null && getContext() instanceof org.telegram.ui.LaunchActivity) {
                ((org.telegram.ui.LaunchActivity) getContext()).presentFragment(ProfileActivity.of(user.id));
            }
        });

        themeToggleBg = new FrameLayout(context);
        themeToggleBg.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18.0f), getThemeToggleBackgroundColor()));
        addView(themeToggleBg, LayoutHelper.createFrame(36, 36.0f, 53, 0.0f, 16.0f, 16.0f, 0.0f));

        int i = R.raw.sun;
        sunDrawable = new RLottieDrawable(i, String.valueOf(i), AndroidUtilities.dp(24.0f), AndroidUtilities.dp(24.0f), true, null);
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        themeToggleView = new RLottieImageView(context);
        themeToggleView.setAnimation(sunDrawable);
        themeToggleView.setScaleType(ImageView.ScaleType.CENTER);
        themeToggleBg.addView(themeToggleView, LayoutHelper.createFrame(-1, -1.0f));

        boolean isDark = Theme.getActiveTheme().isDark();
        sunDrawable.setCustomEndFrame(isDark ? sunDrawable.getFramesCount() - 1 : 0);
        sunDrawable.setCurrentFrame(isDark ? sunDrawable.getFramesCount() - 1 : 0, false);
        themeToggleBg.setOnClickListener(v -> toggleTheme());

        FrameLayout textAndChevronContainer = new FrameLayout(context);
        textAndChevronContainer.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 8, 8));
        addView(textAndChevronContainer, LayoutHelper.createFrame(-1, 56.0f, 51, 0.0f, 92.0f, 0.0f, 0.0f));

        nameView = new SimpleTextView(context);
        nameView.setTextSize(16);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textAndChevronContainer.addView(nameView, LayoutHelper.createFrame(-1, -2.0f, 51, 16.0f, 6.0f, 48.0f, 0.0f));

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        textAndChevronContainer.addView(subtitleView, LayoutHelper.createFrame(-1, -2.0f, 51, 16.0f, 28.0f, 48.0f, 0.0f));

        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.arrow_more);
        chevronView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        textAndChevronContainer.addView(chevronView, LayoutHelper.createFrame(24, 24.0f, 21, 0.0f, 0.0f, 16.0f, 0.0f));

        textAndChevronContainer.setOnClickListener(v -> {
            if (onChevronClick != null) {
                onChevronClick.run();
            }
        });
    }

    public void setOnChevronClick(Runnable runnable) {
        this.onChevronClick = runnable;
    }

    public void setChevronExpanded(boolean expanded) {
        chevronView.animate().rotation(expanded ? 180 : 0).setDuration(200).start();
    }

    public void updateUserInfo() {
        int currentAccount = UserConfig.selectedAccount;
        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        avatarView.setRoundRadius(SweetgramAvatars.getAvatarCorners(72.0f, false));
        if (user != null) {
            avatarDrawable.setInfo(user);
            avatarView.setForUserOrChat(user, avatarDrawable);
            nameView.setText(org.telegram.messenger.ContactsController.formatName(user.first_name, user.last_name));
            if (user.phone != null && !user.phone.isEmpty()) {
                subtitleView.setText(org.telegram.sweetgram.SweetgramPrivacy.phone(PhoneFormat.getInstance().format("+" + user.phone), user.id));
            } else if (user.username != null) {
                subtitleView.setText("@" + org.telegram.sweetgram.SweetgramPrivacy.username(user.username, user.id));
            } else {
                subtitleView.setText("");
            }
        }
        updateColors();
    }

    public void updateColors() {
        themeToggleBg.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18.0f), getThemeToggleBackgroundColor()));
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        chevronView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
    }

    private int getThemeToggleBackgroundColor() {
        return Theme.getColor(Theme.key_dialogBackgroundGray);
    }

    private void toggleTheme() {
        if (DialogsActivity.switchingTheme) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", 0);
        String dayTheme = preferences.getString("lastDayTheme", "Blue");
        String darkTheme = preferences.getString("lastDarkTheme", "Dark Blue");
        Theme.ThemeInfo active = Theme.getActiveTheme();
        boolean toDark = !active.isDark();
        Theme.ThemeInfo themeInfo = toDark ? Theme.getTheme(darkTheme) : Theme.getTheme(dayTheme);
        if (themeInfo == null) {
            themeInfo = toDark ? Theme.getTheme("Night") : Theme.getTheme("Blue");
        }
        sunDrawable.setCustomEndFrame(toDark ? sunDrawable.getFramesCount() - 1 : 0);
        themeToggleView.playAnimation();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, null, -1, toDark, themeToggleView);
    }
}
