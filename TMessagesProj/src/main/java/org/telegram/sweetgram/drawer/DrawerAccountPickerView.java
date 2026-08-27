package org.telegram.sweetgram.drawer;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.sweetgram.SweetgramAvatars;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LoginActivity;

public class DrawerAccountPickerView extends ScrollView {
    private final LinearLayout container;
    private boolean isExpanded = false;
    private Runnable onAccountSelected;

    public DrawerAccountPickerView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setVisibility(GONE);
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));
        addView(container, new FrameLayout.LayoutParams(-1, -2));
    }

    public void setOnAccountSelected(Runnable runnable) {
        this.onAccountSelected = runnable;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void toggleExpand() {
        isExpanded = !isExpanded;
        setVisibility(isExpanded ? VISIBLE : GONE);
        if (isExpanded) {
            loadAccounts();
        }
    }

    public void loadAccounts() {
        container.removeAllViews();
        int currentAccount = UserConfig.selectedAccount;

        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (UserConfig.getInstance(i).isClientActivated()) {
                final int accountNum = i;
                TLRPC.User user = UserConfig.getInstance(i).getCurrentUser();
                if (user == null) continue;

                FrameLayout row = new FrameLayout(getContext());
                row.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 12, 12));
                row.setLayoutParams(new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(56)));

                BackupImageView avatar = new BackupImageView(getContext());
                avatar.setRoundRadius(SweetgramAvatars.getAvatarCorners(38, false));
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(user);
                avatar.setForUserOrChat(user, avatarDrawable);
                row.addView(avatar, LayoutHelper.createFrame(38, 38.0f, 19, 16.0f, 0.0f, 0.0f, 0.0f));

                TextView name = new TextView(getContext());
                name.setText(ContactsController.formatName(user.first_name, user.last_name));
                name.setTextSize(14);
                name.setTypeface(AndroidUtilities.bold());
                name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                row.addView(name, LayoutHelper.createFrame(-1, -2.0f, 51, 68.0f, 10.0f, 16.0f, 0.0f));

                TextView phone = new TextView(getContext());
                String formattedPhone = user.phone != null ? PhoneFormat.getInstance().format("+" + user.phone) : "";
                phone.setText(org.telegram.sweetgram.SweetgramPrivacy.phone(formattedPhone, user.id));
                phone.setTextSize(12);
                phone.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                row.addView(phone, LayoutHelper.createFrame(-1, -2.0f, 51, 68.0f, 30.0f, 16.0f, 0.0f));

                if (accountNum == currentAccount) {
                    ImageView check = new ImageView(getContext());
                    check.setImageResource(R.drawable.mini_checklist_done);
                    check.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon), android.graphics.PorterDuff.Mode.SRC_IN));
                    row.addView(check, LayoutHelper.createFrame(20, 20.0f, 21, 0.0f, 0.0f, 16.0f, 0.0f));
                }

                row.setOnClickListener(v -> {
                    if (accountNum != UserConfig.selectedAccount) {
                        LaunchActivity launchActivity = LaunchActivity.instance;
                        if (launchActivity != null) {
                            launchActivity.switchToAccount(accountNum, true);
                        }
                    }
                    if (onAccountSelected != null) {
                        onAccountSelected.run();
                    }
                });

                container.addView(row);
            }
        }

        // Кнопка добавления аккаунта
        if (UserConfig.getActivatedAccountsCount() < UserConfig.MAX_ACCOUNT_COUNT) {
            FrameLayout addRow = new FrameLayout(getContext());
            addRow.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 12, 12));
            addRow.setLayoutParams(new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48)));

            ImageView addIcon = new ImageView(getContext());
            addIcon.setImageResource(R.drawable.mini_checklist_add);
            addIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon), android.graphics.PorterDuff.Mode.SRC_IN));
            addRow.addView(addIcon, LayoutHelper.createFrame(24, 24.0f, 19, 23.0f, 0.0f, 0.0f, 0.0f));

            TextView addText = new TextView(getContext());
            addText.setText(LocaleController.getString(R.string.AddAccount));
            addText.setTextSize(15);
            addText.setTypeface(AndroidUtilities.bold());
            addText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon));
            addRow.addView(addText, LayoutHelper.createFrame(-1, -2.0f, 19, 68.0f, 0.0f, 16.0f, 0.0f));

            addRow.setOnClickListener(v -> {
                LaunchActivity launchActivity = LaunchActivity.instance;
                if (launchActivity != null) {
                    launchActivity.presentFragment(new LoginActivity());
                }
                if (onAccountSelected != null) {
                    onAccountSelected.run();
                }
            });
            container.addView(addRow);
        }
    }
}
