package org.telegram.margelet.drawer;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerMenuItemView extends FrameLayout {
    private final ImageView iconView;
    private final TextView textView;

    public DrawerMenuItemView(Context context) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(-1, AndroidUtilities.dp(48.0f)));
        setBackground(createSelectorDrawable());

        ImageView imageView = new ImageView(context);
        this.iconView = imageView;
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(createIconColorFilter());
        addView(imageView, LayoutHelper.createFrame(24, 24.0f, 19, 20.0f, 0.0f, 0.0f, 0.0f));

        TextView textView = new TextView(context);
        this.textView = textView;
        textView.setTextSize(1, 15.0f);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setGravity(19);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(-1, -1.0f, 19, 68.0f, 0.0f, 16.0f, 0.0f));
    }

    public void setMenuItem(int iconRes, CharSequence text) {
        this.iconView.setImageResource(iconRes);
        this.textView.setText(text);
    }

    public void updateColors() {
        setBackground(createSelectorDrawable());
        this.iconView.setColorFilter(createIconColorFilter());
        this.textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        invalidate();
    }

    private static Drawable createSelectorDrawable() {
        return Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 12, 12);
    }

    private static PorterDuffColorFilter createIconColorFilter() {
        return new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN);
    }
}
