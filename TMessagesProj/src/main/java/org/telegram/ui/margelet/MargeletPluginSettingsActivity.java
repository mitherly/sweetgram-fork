package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.margelet.MargeletHooks;
import org.telegram.margelet.MargeletPluginHost;
import org.telegram.margelet.MargeletPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран настроек одного плагина.
 *
 * Экран этот рисуем мы, а из чего он состоит — говорит плагин. Обратное
 * решение, дать плагину рисовать свои экраны самому, выглядит свободнее, но
 * кончается тем, что каждый плагин выглядит по-своему и ни один не похож на
 * приложение, в котором стоит. Здесь же переключатель у плагина такой же, как
 * везде, и работает так же: тем же пальцем, в той же теме, тем же цветом.
 *
 * Заявку плагин оставляет в своей же памяти, поэтому экран открывается и у
 * выключенного плагина: человеку может понадобиться поправить настройку до
 * того, как включать.
 */
public class MargeletPluginSettingsActivity extends UniversalFragment {

    private final MargeletPlugins.Plugin plugin;
    private final List<JSONObject> rows = new ArrayList<>();

    /** Номера строк идут отсюда: по одному на заявленную плагином строку. */
    private static final int ID_ROW = 100;

    public MargeletPluginSettingsActivity(MargeletPlugins.Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected CharSequence getTitle() {
        return plugin.name;
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    /** Разбирает заявку плагина. Кривой JSON — пустой экран, а не падение. */
    private void read() {
        rows.clear();
        final String json = MargeletHooks.declared(plugin.id);
        if (json == null) {
            return;
        }
        try {
            final JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                final JSONObject row = array.optJSONObject(i);
                if (row != null) {
                    rows.add(row);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        read();
        if (rows.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletPluginNoSettings)));
            return;
        }
        boolean opened = false;
        for (int i = 0; i < rows.size(); i++) {
            final JSONObject row = rows.get(i);
            final String kind = row.optString("kind");
            final String title = row.optString("title");
            final String key = row.optString("key");
            switch (kind) {
                case "header":
                    items.add(UItem.asHeader(title));
                    opened = true;
                    break;
                case "note":
                    items.add(UItem.asShadow(title));
                    opened = false;
                    break;
                case "switch":
                    items.add(UItem.asCheck(ID_ROW + i, title)
                            .setChecked("1".equals(value(row))));
                    opened = true;
                    break;
                case "text":
                case "choice":
                    items.add(UItem.asButton(ID_ROW + i, title, value(row)));
                    opened = true;
                    break;
                case "action": {
                    final UItem item = UItem.asButton(ID_ROW + i, title);
                    if (row.optBoolean("danger")) {
                        item.red();
                    }
                    items.add(item);
                    opened = true;
                    break;
                }
                default:
                    break;
            }
            final String about = row.optString("about", "");
            if (about.length() > 0) {
                items.add(UItem.asShadow(about));
                opened = false;
            }
        }
        if (opened) {
            items.add(UItem.asShadow(null));
        }
    }

    private String value(JSONObject row) {
        final String key = row.optString("key");
        if (key.length() == 0) {
            return "";
        }
        final String stored = MargeletPluginHost.get(plugin.id, key, null);
        return stored != null ? stored : row.optString("default", "");
    }

    private void store(JSONObject row, String value) {
        final String key = row.optString("key");
        MargeletPluginHost.set(plugin.id, key, value);
        MargeletHooks.settingsChanged(plugin.id, key, value);
        listView.adapter.update(true);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final int index = item.id - ID_ROW;
        if (index < 0 || index >= rows.size()) {
            return;
        }
        final JSONObject row = rows.get(index);
        switch (row.optString("kind")) {
            case "switch":
                store(row, "1".equals(value(row)) ? "0" : "1");
                break;
            case "text":
                askText(row);
                break;
            case "choice":
                askChoice(row);
                break;
            case "action":
                // Действие ничего не хранит: плагину важно само нажатие.
                MargeletHooks.settingsChanged(plugin.id, row.optString("key"), null);
                break;
            default:
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void askText(JSONObject row) {
        final Context context = getContext();
        final EditText input = new EditText(context);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setBackground(Theme.createEditTextDrawable(context, true));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(value(row));
        input.setSelection(input.getText().length());

        final FrameLayout container = new FrameLayout(context);
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 24, 0, 24, 0));
        container.setPadding(0, dp(8), 0, dp(8));

        new AlertDialog.Builder(context)
                .setTitle(row.optString("title"))
                .setView(container)
                .setPositiveButton(LocaleController.getString(R.string.Save),
                        (d, w) -> store(row, input.getText().toString()))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void askChoice(JSONObject row) {
        final JSONArray options = row.optJSONArray("options");
        if (options == null || options.length() == 0) {
            return;
        }
        final CharSequence[] titles = new CharSequence[options.length()];
        for (int i = 0; i < options.length(); i++) {
            titles[i] = options.optString(i);
        }
        new AlertDialog.Builder(getContext())
                .setTitle(row.optString("title"))
                .setItems(titles, (d, which) -> store(row, String.valueOf(titles[which])))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }
}
