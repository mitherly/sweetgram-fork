package com.sweetgram;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Панель форка: две вкладки — управление верификацией и аналитика.
 * Открывается только после пароля из настроек форка.
 */
public class SweetgramAdminActivity extends BaseFragment {

    private EditText userIdEditText;
    private EditText verificationTextEditText;

    private TextView manageTab;
    private TextView analyticsTab;
    private View manageView;
    private View analyticsView;

    private TextView launchesValue;
    private TextView usersValue;
    private TextView installsValue;
    private TextView verifiedValue;

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

        FrameLayout root = new FrameLayout(context);
        fragmentView = root;

        ScrollView manageScroll = new ScrollView(context);
        manageView = buildManageView(context);
        manageScroll.addView(manageView, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        root.addView(manageScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 56, 0, 0));

        ScrollView analyticsScroll = new ScrollView(context);
        analyticsView = buildAnalyticsView(context);
        analyticsScroll.addView(analyticsView, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        analyticsScroll.setVisibility(View.GONE);
        root.addView(analyticsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 56, 0, 0));

        LinearLayout tabBar = new LinearLayout(context);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        tabBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        manageTab = makeTab(context, LocaleController.getString(R.string.sg_analytics_manage_tab_title), true);
        manageTab.setOnClickListener(v -> selectTab(true));
        analyticsTab = makeTab(context, LocaleController.getString(R.string.sg_analytics_tab_title), false);
        analyticsTab.setOnClickListener(v -> {
            selectTab(false);
            loadAnalytics();
        });

        tabBar.addView(manageTab, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        tabBar.addView(analyticsTab, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        root.addView(tabBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.LEFT));

        return fragmentView;
    }

    private TextView makeTab(Context context, String text, boolean selected) {
        TextView tab = new TextView(context);
        tab.setText(text);
        tab.setGravity(Gravity.CENTER);
        tab.setTextColor(Theme.getColor(selected ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));
        tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tab.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tab.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        return tab;
    }

    private void selectTab(boolean manage) {
        manageView.setVisibility(manage ? View.VISIBLE : View.GONE);
        analyticsView.setVisibility(manage ? View.GONE : View.VISIBLE);
        manageTab.setTextColor(Theme.getColor(manage ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));
        analyticsTab.setTextColor(Theme.getColor(manage ? Theme.key_windowBackgroundWhiteGrayText : Theme.key_windowBackgroundWhiteBlackText));
    }

    private View buildManageView(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

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

        return linearLayout;
    }

    private View buildAnalyticsView(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        launchesValue = addMetric(context, linearLayout, R.string.sg_analytics_launches);
        usersValue = addMetric(context, linearLayout, R.string.sg_analytics_users);
        installsValue = addMetric(context, linearLayout, R.string.sg_analytics_installs);
        verifiedValue = addMetric(context, linearLayout, R.string.sg_analytics_verified);

        return linearLayout;
    }

    private TextView addMetric(Context context, LinearLayout parent, int labelRes) {
        TextView label = new TextView(context);
        label.setText(LocaleController.getString(labelRes));
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        parent.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView value = new TextView(context);
        value.setText(LocaleController.getString(R.string.sg_analytics_loading));
        value.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        value.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        parent.addView(value, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        return value;
    }

    private void loadAnalytics() {
        try {
            DatabaseReference root = FirebaseDatabase.getInstance().getReference();

            root.child("stats").child("launches").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long v = snapshot.getValue(Long.class);
                    setText(launchesValue, v == null ? "0" : String.valueOf(v));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    setText(launchesValue, "-");
                }
            });

            root.child("stats").child("users").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    setText(usersValue, String.valueOf(snapshot.getChildrenCount()));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    setText(usersValue, "-");
                }
            });

            root.child("stats").child("installs").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long v = snapshot.getValue(Long.class);
                    setText(installsValue, v == null ? "0" : String.valueOf(v));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    setText(installsValue, "-");
                }
            });

            root.child("verified_users").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    setText(verifiedValue, String.valueOf(snapshot.getChildrenCount()));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    setText(verifiedValue, "-");
                }
            });
        } catch (Throwable e) {
            android.util.Log.e("SweetgramAdmin", "loadAnalytics failed", e);
        }
    }

    private void setText(TextView view, String text) {
        if (view == null) return;
        AndroidUtilities.runOnUIThread(() -> {
            if (getParentActivity() == null) return;
            view.setText(text);
        });
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
            SweetgramAuth.getInstance().grantVerification(userId, text.isEmpty() ? "Verified" : text,
                    (ok, error) -> org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
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
            SweetgramAuth.getInstance().revokeVerification(userId,
                    (ok, error) -> org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                        if (getParentActivity() == null) return;
                        if (ok) {
                            Toast.makeText(getParentActivity(), "Revoked from " + userId, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getParentActivity(), "FAILED: " + error, Toast.LENGTH_LONG).show();
                        }
                    }));
            userIdEditText.setText("");
        } catch (NumberFormatException e) {
            Toast.makeText(getParentActivity(), "Invalid User ID", Toast.LENGTH_SHORT).show();
        }
    }
}
