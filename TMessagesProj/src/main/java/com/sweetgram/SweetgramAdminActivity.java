package com.sweetgram;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Панель выдачи верификации.
 *
 * Вход двойной: сначала Firebase Auth по почте и паролю, потом сверка со
 * списком админов в базе. Пароля в исходнике нет — его в принципе нельзя
 * хранить в клиенте, потому что клиент у всех один и тот же. Того, кто
 * вошёл, но не в списке, панель выкидывает с отказом.
 */
public class SweetgramAdminActivity extends BaseFragment {

    private EditText emailEditText;
    private EditText passwordEditText;
    private EditText userIdEditText;
    private EditText verificationTextEditText;
    private LinearLayout contentLayout;

    @Override
    public android.view.View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Sweetgram Admin");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        fragmentView = scrollView;

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        scrollView.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        if (SweetgramAuth.getInstance().isAdminSignedIn()) {
            // Сессия Firebase живёт долго, но телеграм-ID могли уже убрать
            // из списка — проверяем каждый раз, а не только при вводе пароля.
            SweetgramAuth.getInstance().ensureTelegramAdmin(new SweetgramAuth.WriteCallback() {
                @Override
                public void onResult(boolean ok, String error) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (getParentActivity() == null) return;
                        if (ok) {
                            buildAdminUI();
                        } else {
                            SweetgramAuth.getInstance().adminSignOut();
                            buildLoginUI();
                            toast(error == null ? "Access revoked" : error);
                        }
                    });
                }
            });
        } else {
            buildLoginUI();
        }
        return fragmentView;
    }

    // --- вход ---

    private void buildLoginUI() {
        final Context context = ApplicationLoader.applicationContext;

        TextView title = new TextView(context);
        title.setText("Admin sign-in");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        contentLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        emailEditText = makeField(context, "Admin email");
        passwordEditText = makeField(context, "Password");
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        TextView signInButton = new TextView(context);
        signInButton.setText("SIGN IN");
        signInButton.setGravity(Gravity.CENTER);
        signInButton.setTextColor(Color.WHITE);
        signInButton.setBackgroundColor(Color.parseColor("#FF69B4"));
        signInButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        signInButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        signInButton.setOnClickListener(v -> signIn());
        contentLayout.addView(signInButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView hint = new TextView(context);
        final long tgId = SweetgramAuth.myTelegramId();
        hint.setText("Access is decided by the admins list in the database, not by this app.\n"
                + (tgId != 0 ? "Your Telegram ID: " + tgId + " — it must be in tg_admins."
                             : "No Telegram account on this device."));
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        contentLayout.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private EditText makeField(Context context, String hint) {
        final EditText field = new EditText(context);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        contentLayout.addView(field, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        return field;
    }

    private void signIn() {
        final String email = emailEditText.getText().toString().trim();
        final String password = passwordEditText.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter email and password");
            return;
        }
        SweetgramAuth.getInstance().adminSignIn(email, password, (ok, error) -> {
            if (getParentActivity() == null) return;
            if (ok) {
                toast("Signed in");
                contentLayout.removeAllViews();
                buildAdminUI();
            } else {
                toast("FAILED: " + (error == null ? "rejected" : error));
            }
        });
    }

    // --- выдача ---

    private void buildAdminUI() {
        final Context context = ApplicationLoader.applicationContext;

        TextView title = new TextView(context);
        title.setText("Verification management");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        contentLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        userIdEditText = new EditText(context);
        userIdEditText.setHint("User ID (e.g., 123456789)");
        userIdEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        userIdEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        userIdEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        contentLayout.addView(userIdEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        verificationTextEditText = new EditText(context);
        verificationTextEditText.setHint("Verification text (optional)");
        verificationTextEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        verificationTextEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        contentLayout.addView(verificationTextEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView grantButton = new TextView(context);
        grantButton.setText("GRANT VERIFICATION");
        grantButton.setGravity(Gravity.CENTER);
        grantButton.setTextColor(Color.WHITE);
        grantButton.setBackgroundColor(Color.parseColor("#FF69B4"));
        grantButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        grantButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        grantButton.setOnClickListener(v -> grant());
        contentLayout.addView(grantButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView revokeButton = new TextView(context);
        revokeButton.setText("REVOKE VERIFICATION");
        revokeButton.setGravity(Gravity.CENTER);
        revokeButton.setTextColor(Color.WHITE);
        revokeButton.setBackgroundColor(Color.parseColor("#FF4C4C"));
        revokeButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        revokeButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        revokeButton.setOnClickListener(v -> revoke());
        contentLayout.addView(revokeButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView signOutButton = new TextView(context);
        signOutButton.setText("SIGN OUT");
        signOutButton.setGravity(Gravity.CENTER);
        signOutButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        signOutButton.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        signOutButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        signOutButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        signOutButton.setOnClickListener(v -> {
            SweetgramAuth.getInstance().adminSignOut();
            contentLayout.removeAllViews();
            buildLoginUI();
        });
        contentLayout.addView(signOutButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView hint = new TextView(context);
        hint.setText("The badge appears for the user after restart or when the profile is reopened.");
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        contentLayout.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView attribution = new TextView(context);
        attribution.setText("Based on Margy (@margeletter , github.com/narezany/Margelet)");
        attribution.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        attribution.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        attribution.setPadding(0, AndroidUtilities.dp(16), 0, 0);
        contentLayout.addView(attribution, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void grant() {
        String userIdStr = userIdEditText.getText().toString();
        if (userIdStr.isEmpty()) {
            toast("Please enter a User ID");
            return;
        }
        try {
            long userId = Long.parseLong(userIdStr);
            String text = verificationTextEditText.getText().toString();
            SweetgramAuth.getInstance().grantVerification(userId, text.isEmpty() ? "Verified" : text,
                    (ok, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (getParentActivity() == null) return;
                        if (ok) {
                            Toast.makeText(getParentActivity(), "Granted to " + userId + ", reopen profile", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getParentActivity(), "FAILED: " + error, Toast.LENGTH_LONG).show();
                        }
                    }));
            userIdEditText.setText("");
            verificationTextEditText.setText("");
        } catch (NumberFormatException e) {
            toast("Invalid User ID");
        }
    }

    private void revoke() {
        String userIdStr = userIdEditText.getText().toString();
        if (userIdStr.isEmpty()) {
            toast("Please enter a User ID");
            return;
        }
        try {
            long userId = Long.parseLong(userIdStr);
            SweetgramAuth.getInstance().revokeVerification(userId,
                    (ok, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (getParentActivity() == null) return;
                        if (ok) {
                            Toast.makeText(getParentActivity(), "Revoked from " + userId, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getParentActivity(), "FAILED: " + error, Toast.LENGTH_LONG).show();
                        }
                    }));
            userIdEditText.setText("");
        } catch (NumberFormatException e) {
            toast("Invalid User ID");
        }
    }

    private void toast(String text) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), text, Toast.LENGTH_SHORT).show();
        }
    }
}
