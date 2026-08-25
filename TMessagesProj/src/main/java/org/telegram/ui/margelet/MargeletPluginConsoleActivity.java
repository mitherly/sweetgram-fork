package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.margelet.MargeletPluginHost;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Консоль плагинов: всё, что они напечатали, и их ошибки.
 *
 * Нужна не для вида. Питон падает молча, и без этого экрана автор плагина
 * узнаёт о своей ошибке только по тому, что «ничего не работает». Ошибки
 * выделены красным, остальное — обычным цветом.
 */
public class MargeletPluginConsoleActivity extends BaseFragment {

    private static final int ID_CLEAR = 1;

    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final Runnable listener = this::refresh;

    private TextView text;
    private ScrollView scroll;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.MargeletPluginConsole));
        actionBar.createMenu().addItem(ID_CLEAR, R.drawable.msg_delete);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_CLEAR) {
                    MargeletPluginHost.clear();
                }
            }
        });

        scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        text = new TextView(context);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        text.setTextIsSelectable(true);
        text.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        scroll.addView(text, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        fragmentView = scroll;
        refresh();
        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        MargeletPluginHost.addListener(listener);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        MargeletPluginHost.removeListener(listener);
        super.onFragmentDestroy();
    }

    private void refresh() {
        if (text == null) {
            return;
        }
        final List<MargeletPluginHost.Line> lines = MargeletPluginHost.console();
        if (lines.isEmpty()) {
            text.setText(LocaleController.getString(R.string.MargeletPluginConsoleEmpty));
            return;
        }
        final SpannableStringBuilder out = new SpannableStringBuilder();
        for (MargeletPluginHost.Line line : lines) {
            final int from = out.length();
            out.append(time.format(line.time)).append("  ")
                    .append(line.plugin).append(": ").append(line.text).append("\n");
            if (line.error) {
                out.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_text_RedBold)),
                        from, out.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        text.setText(out);
        // Смотрят всегда последнюю строку: она и есть новость.
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
}
