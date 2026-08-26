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
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Панель выдачи верификации. Открывается только после пароля из настроек
 * форка: галочка — ручная операция, интерфейс для неё нужен простой,
 * без лишних экранов.
 */
public class SweetgramAdminActivity extends BaseFragment {

    private EditText userIdEditText;
    private EditText verificationTextEditText;

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

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        TextView title = new TextView(context);
        title.setText("Verification management");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        userIdEditText = new EditText(context);
        userIdEditText.setHint("User ID (e.g., 123456789)");
        userIdEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        userIdEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        userIdEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        linearLayout.addView(userIdEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        verificationTextEditText = new EditText(context);
        verificationTextEditText.setHint("Verification text (optional)");
        verificationTextEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        verificationTextEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        linearLayout.addView(verificationTextEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView grantButton = new TextView(context);
        grantButton.setText("GRANT VERIFICATION");
        grantButton.setGravity(Gravity.CENTER);
        grantButton.setTextColor(Color.WHITE);
        grantButton.setBackgroundColor(Color.parseColor("#FF69B4"));
        grantButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        grantButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        grantButton.setOnClickListener(v -> grant());
        linearLayout.addView(grantButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView revokeButton = new TextView(context);
        revokeButton.setText("REVOKE VERIFICATION");
        revokeButton.setGravity(Gravity.CENTER);
        revokeButton.setTextColor(Color.WHITE);
        revokeButton.setBackgroundColor(Color.parseColor("#FF4C4C"));
        revokeButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        revokeButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        revokeButton.setOnClickListener(v -> revoke());
        linearLayout.addView(revokeButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView hint = new TextView(context);
        hint.setText("The badge appears for the user after restart or when the profile is reopened.");
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }

    private void grant() {
        String userIdStr = userIdEditText.getText().toString();
        if (userIdStr.isEmpty()) {
            Toast.makeText(getParentActivity(), "Please enter a User ID", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            long userId = Long.parseLong(userIdStr);
            String text = verificationTextEditText.getText().toString();
            SweetgramAuth.getInstance().grantVerification(userId, text.isEmpty() ? "Verified" : text);
            Toast.makeText(getParentActivity(), "Granted to " + userId, Toast.LENGTH_SHORT).show();
            userIdEditText.setText("");
            verificationTextEditText.setText("");
        } catch (NumberFormatException e) {
            Toast.makeText(getParentActivity(), "Invalid User ID", Toast.LENGTH_SHORT).show();
        }
    }

    private void revoke() {
        String userIdStr = userIdEditText.getText().toString();
        if (userIdStr.isEmpty()) {
            Toast.makeText(getParentActivity(), "Please enter a User ID", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            long userId = Long.parseLong(userIdStr);
            SweetgramAuth.getInstance().revokeVerification(userId);
            Toast.makeText(getParentActivity(), "Revoked from " + userId, Toast.LENGTH_SHORT).show();
            userIdEditText.setText("");
        } catch (NumberFormatException e) {
            Toast.makeText(getParentActivity(), "Invalid User ID", Toast.LENGTH_SHORT).show();
        }
    }
}
