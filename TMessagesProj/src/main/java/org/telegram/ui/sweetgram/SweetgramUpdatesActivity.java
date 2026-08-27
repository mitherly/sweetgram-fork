package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.sweetgram.SweetgramUpdate;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Ветка «Обновления»: показывает список релизов с GitHub и сообщает,
 * когда доступна версия новее установленной.
 *
 * Релизы тянутся прямо из GitHub Releases REST API — публично, без токена.
 * Сам apk отсюда не качаем: по нажатию открываем страницу релиза, а дальше
 * человек решает сам.
 */
public class SweetgramUpdatesActivity extends UniversalFragment {

    /** Кнопка проверки не должна попасть в номера значений интервала. */
    private static final int ID_CHECK_NOW = 1000;

    /** Откуда берём релизы. Публично, токен не нужен. */
    private static final String RELEASES_URL =
            "https://api.github.com/repos/mitherly/sweetgram-fork/releases";

    /** GitHub требует заголовок User-Agent, иначе отдаёт 403. */
    private static final String USER_AGENT = "SweetgramClient";

    /** Что мы знаем про один релиз. */
    private static final class Release {
        final String tag;
        final String name;
        final String body;
        final String publishedAt;
        final String htmlUrl;

        Release(String tag, String name, String body, String publishedAt, String htmlUrl) {
            this.tag = tag;
            this.name = name;
            this.body = body;
            this.publishedAt = publishedAt;
            this.htmlUrl = htmlUrl;
        }
    }

    private volatile ArrayList<Release> releases;
    private volatile boolean loading;
    private volatile String error;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.sg_updates_title);
    }

    /** Название интервала: «3 минуты», «6 часов», «Никогда». */
    private static String name(int minutes) {
        if (minutes <= 0) {
            return LocaleController.getString(R.string.SweetgramUpdatesOff);
        }
        if (minutes < 60) {
            return LocaleController.formatPluralString("Minutes", minutes);
        }
        return LocaleController.formatPluralString("Hours", minutes / 60);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        // Первый заход сразу тянет список релизов.
        loadReleases();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.sg_updates_title)));

        // Как часто спрашивать гитхаб про новую версию (для плашки в списке
        // чатов — она живёт отдельно от этого экрана). «Проверить» справа
        // работает и при выключенных автопроверках.
        final int chosen = SweetgramConfig.updateIntervalMinutes();
        for (int i = 0; i < SweetgramConfig.UPDATE_INTERVALS.length; i++) {
            final int minutes = SweetgramConfig.UPDATE_INTERVALS[i];
            items.add(UItem.asRadio(i, name(minutes)).setChecked(minutes == chosen));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramUpdatesAbout)));

        // Свой номер и версия телеграма, на которой собрано: без второго
        // числа непонятно, из какого исходника выросла сборка.
        items.add(UItem.asShadow(LocaleController.formatString(R.string.sg_updates_current,
                SweetgramConfig.APP_VERSION)
                + "\n"
                + LocaleController.formatString(R.string.SweetgramUpdatesBased,
                        BuildVars.BUILD_VERSION_STRING)));
        items.add(UItem.asButton(ID_CHECK_NOW, LocaleController.getString(R.string.sg_updates_check)));

        final ArrayList<Release> list = releases;
        if (loading) {
            items.add(UItem.asFullyCustom(textCell(LocaleController.getString(R.string.sg_updates_loading), false)));
        } else if (error != null) {
            items.add(UItem.asFullyCustom(textCell(LocaleController.getString(R.string.sg_updates_error), true)));
        } else if (list != null && !list.isEmpty()) {
            items.add(UItem.asFullyCustom(buildReleasesView(list)));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CHECK_NOW) {
            loadReleases();
            return;
        }
        if (item.id >= 0 && item.id < SweetgramConfig.UPDATE_INTERVALS.length) {
            SweetgramConfig.setUpdateIntervalMinutes(SweetgramConfig.UPDATE_INTERVALS[item.id]);
            // Расписание переставляется сразу, чтобы новое значение начало
            // действовать не после перезапуска, а сейчас же.
            SweetgramUpdate.schedule();
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    /**
     * Тянет релизы в фоне и перерисовывает список. Ошибка сети не падает
     * молча — показываем понятную строку.
     */
    private void loadReleases() {
        loading = true;
        error = null;
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        new Thread(() -> {
            try {
                final ArrayList<Release> result = fetch();
                AndroidUtilities.runOnUIThread(() -> {
                    releases = result;
                    loading = false;
                    error = null;
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                });
            } catch (Throwable t) {
                AndroidUtilities.runOnUIThread(() -> {
                    loading = false;
                    error = t.getMessage();
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                });
            }
        }, "sg-updates").start();
    }

    /**
     * Ходит в GitHub и разбирает JSON. Первый годный (не черновик и не
     * pre-release) релиз считаем «последним» для сравнения с нашей версией.
     */
    private static ArrayList<Release> fetch() throws Exception {
        final HttpURLConnection connection =
                (HttpURLConnection) new URL(RELEASES_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        final int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            connection.disconnect();
        }
        return parse(sb.toString());
    }

    private static ArrayList<Release> parse(String json) throws JSONException {
        final JSONArray array = new JSONArray(json);
        final ArrayList<Release> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            final JSONObject o = array.getJSONObject(i);
            if (o.optBoolean("draft", false) || o.optBoolean("prerelease", false)) {
                continue;
            }
            out.add(new Release(
                    o.optString("tag_name", ""),
                    o.optString("name", ""),
                    o.optString("body", ""),
                    o.optString("published_at", ""),
                    o.optString("html_url", "")));
        }
        return out;
    }

    /** Свежая версия новее нашей — значит, есть что качать. */
    private static boolean isUpdateAvailable(ArrayList<Release> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        return !stripTag(list.get(0).tag).equalsIgnoreCase(stripTag(SweetgramConfig.APP_VERSION));
    }

    /** Убираем ведущее «v», чтобы «v0.5» сравнивалось с «0.5». */
    private static String stripTag(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    /**
     * Собирает блок: сверху — баннер «доступно обновление» (если версия
     * отстаёт), ниже — карточки релизов.
     */
    private View buildReleasesView(ArrayList<Release> list) {
        final Context context = getContext();
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        if (isUpdateAvailable(list)) {
            root.addView(bannerView(context, list.get(0)));
        }
        for (int i = 0; i < list.size(); i++) {
            root.addView(releaseCell(context, list.get(i)));
        }
        return root;
    }

    /**
     * Яркая плашка сверху: «Доступно обновление» и кнопка «Скачать»,
     * открывающая страницу релиза. Никакой автозагрузки apk.
     */
    private View bannerView(Context context, Release latest) {
        final LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        banner.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.sg_updates_available));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        title.setTextSize(16);
        title.setTypeface(AndroidUtilities.bold());
        banner.addView(title);

        final TextView sub = new TextView(context);
        sub.setText(latest.name != null && !latest.name.isEmpty() ? latest.name : latest.tag);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setTextSize(14);
        banner.addView(sub);

        final TextView action = new TextView(context);
        action.setText(LocaleController.getString(R.string.sg_updates_download));
        action.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        action.setTextSize(15);
        action.setTypeface(AndroidUtilities.bold());
        action.setGravity(Gravity.END);
        action.setOnClickListener(v -> openUrl(latest.htmlUrl));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(8);
        banner.addView(action, lp);

        final LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        wrap.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        wrap.addView(banner, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return wrap;
    }

    /** Одна карточка релиза: заголовок, дата, описание, кнопка «Открыть». */
    private View releaseCell(Context context, Release r) {
        final LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        final TextView title = new TextView(context);
        final String heading = (r.name != null && !r.name.isEmpty()) ? r.name : r.tag;
        title.setText(heading);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(16);
        title.setTypeface(AndroidUtilities.bold());
        cell.addView(title);

        if (!TextUtils.isEmpty(r.publishedAt)) {
            final TextView date = new TextView(context);
            date.setText(formatDate(r.publishedAt));
            date.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            date.setTextSize(13);
            final LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.topMargin = AndroidUtilities.dp(2);
            cell.addView(date, dl);
        }

        if (!TextUtils.isEmpty(r.body)) {
            final TextView body = new TextView(context);
            body.setText(r.body.trim());
            body.setMaxLines(6);
            body.setEllipsize(TextUtils.TruncateAt.END);
            body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            body.setTextSize(14);
            final LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.topMargin = AndroidUtilities.dp(6);
            cell.addView(body, bl);
        }

        final TextView open = new TextView(context);
        open.setText(LocaleController.getString(R.string.sg_updates_open));
        open.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        open.setTextSize(15);
        open.setGravity(Gravity.END);
        final LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ol.topMargin = AndroidUtilities.dp(8);
        cell.addView(open, ol);

        cell.setOnClickListener(v -> openUrl(r.htmlUrl));

        final LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        final LinearLayout.LayoutParams wlp = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        wlp.topMargin = AndroidUtilities.dp(8);
        wlp.leftMargin = AndroidUtilities.dp(8);
        wlp.rightMargin = AndroidUtilities.dp(8);
        wrap.addView(cell, wlp);
        return wrap;
    }

    /** Простая строка-сообщение (загрузка / ошибка). */
    private View textCell(String text, boolean error) {
        final Context context = getContext();
        final TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        tv.setTextColor(Theme.getColor(error
                ? Theme.key_text_RedRegular
                : Theme.key_windowBackgroundWhiteGrayText));
        final LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        wrap.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return wrap;
    }

    /** «Опубликовано» из ISO-времени делаем короче: без буквы T и Z. */
    private static String formatDate(String iso) {
        String s = iso.replace('T', ' ');
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Открывает страницу релиза в браузере — сами apk не качаем. */
    private void openUrl(String url) {
        if (url == null || url.isEmpty() || getParentActivity() == null) {
            return;
        }
        Browser.openUrl(getParentActivity(), url);
    }
}
