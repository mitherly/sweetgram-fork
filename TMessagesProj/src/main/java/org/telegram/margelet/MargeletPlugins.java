package org.telegram.margelet;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.SpannableStringBuilder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Плагины Margelet: установка, список, включение.
 *
 * Плагин — это архив .marp: манифест, код на питоне, иконка и всё, что автор
 * захотел положить рядом. Код лежит исходником и читается кем угодно; это не
 * техническая мера, а условие форума, и владелец форка выбрал именно её.
 *
 * <b>Про безопасность честно.</b> Плагин исполняется как часть приложения и
 * технически может всё, что может само приложение, — включая доступ к данным
 * входа. Список разрешений в манифесте это <b>заявление автора</b>, а не
 * ограничение, и приложение не может его проверить. Так решено владельцем
 * форка: он выбрал открытость кода и проверку людьми вместо песочницы.
 * Единственное, чего здесь делать нельзя, — говорить пользователю, будто
 * разрешения его защищают. Поэтому на окне установки написано ровно то, что
 * есть.
 */
public class MargeletPlugins {

    /** Что плагин заявляет о себе. Именно заявляет — проверить это нечем. */
    public static final String[] PERMISSIONS = {
            "read_chats", "send_messages", "edit_messages",
            "delete_messages", "change_profile", "ui"
    };

    public static final class Plugin {
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        public final List<String> permissions;
        /** Какая версия форка нужна плагину. Пусто — любая. */
        public final String minVersion;
        public final File folder;

        Plugin(String id, String name, String version, String author, String description,
               List<String> permissions, String minVersion, File folder) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.permissions = permissions;
            this.minVersion = minVersion;
            this.folder = folder;
        }

        public File entry() {
            return new File(folder, "main.py");
        }

        private Bitmap icon;
        private boolean iconRead;

        /**
         * Значок плагина, если автор его положил. Читается один раз: список
         * перерисовывается на каждое переключение, и разбирать png заново
         * каждый раз незачем.
         */
        public Bitmap icon() {
            if (!iconRead) {
                iconRead = true;
                final File file = new File(folder, "icon.png");
                if (file.exists()) {
                    icon = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            }
            return icon;
        }

        public boolean enabled() {
            return MargeletConfig.pluginEnabled(id);
        }
    }

    private static File root() {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "margelet_plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Все установленные плагины, по имени. */
    /** Начало имени папки, в которую распаковывают перед установкой. */
    private static final String STAGING = "tmp_";

    public static List<Plugin> installed() {
        final List<Plugin> found = new ArrayList<>();
        final File[] folders = root().listFiles();
        if (folders == null) {
            return found;
        }
        for (File folder : folders) {
            // Папка распаковки — не установленный плагин. Лежит она здесь же,
            // и без этой проверки только что распакованный плагин находил сам
            // себя: окно установки говорило «у тебя уже такой стоит, установка
            // его заменит» вообще всем и всегда, даже на первом в жизни
            // плагине. Заодно недораспакованное больше не мелькнёт в списке.
            if (folder.getName().startsWith(STAGING)) {
                continue;
            }
            final Plugin plugin = read(folder);
            if (plugin != null) {
                found.add(plugin);
            }
        }
        Collections.sort(found, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return found;
    }

    private static Plugin read(File folder) {
        final File manifest = new File(folder, "manifest.json");
        if (!folder.isDirectory() || !manifest.exists()) {
            return null;
        }
        try {
            final JSONObject json = new JSONObject(readAll(new java.io.FileInputStream(manifest)));
            final List<String> permissions = new ArrayList<>();
            final JSONArray array = json.optJSONArray("permissions");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    permissions.add(array.optString(i));
                }
            }
            return new Plugin(
                    json.optString("id", folder.getName()),
                    localized(json, "name", folder.getName()),
                    json.optString("version", "?"),
                    json.optString("author", "?"),
                    localized(json, "description", ""),
                    permissions,
                    json.optString("min_version", ""),
                    folder);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        stream.close();
        return out.toString("UTF-8");
    }

    /**
     * Распаковывает .marp во временную папку и возвращает, что там лежит.
     * Ставить сразу нельзя: человек должен сначала увидеть, кто автор и что
     * плагин о себе заявляет, — а это написано внутри архива.
     *
     * Пути из архива чистятся: запись вида «../../что-то» в обычном
     * распаковщике вылезает за папку плагина и пишет куда попало. Такие
     * записи пропускаются.
     */
    public static Plugin stage(Context context, InputStream source) {
        File folder = null;
        try {
            folder = new File(root(), STAGING + System.currentTimeMillis());
            folder.mkdirs();
            final ZipInputStream zip = new ZipInputStream(source);
            ZipEntry entry;
            final byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                final String name = entry.getName();
                if (entry.isDirectory() || name.contains("..") || name.startsWith("/")) {
                    continue;
                }
                final File out = new File(folder, name);
                final File parent = out.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                final FileOutputStream stream = new FileOutputStream(out);
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    stream.write(buffer, 0, read);
                }
                stream.close();
            }
            zip.close();

            final Plugin plugin = read(folder);
            if (plugin == null) {
                delete(folder);
                return null;
            }
            return plugin;
        } catch (Exception e) {
            FileLog.e(e);
            if (folder != null) {
                delete(folder);
            }
            return null;
        }
    }

    /**
     * Переносит распакованное на место. Плагин с тем же номером заменяется —
     * так обновление не плодит копии.
     */
    public static Plugin commit(Plugin staged) {
        if (staged == null) {
            return null;
        }
        final File target = new File(root(), staged.id);
        // Свои настройки плагина переживают обновление, но не удаление:
        // удалил — значит, отказался.
        delete(target);
        staged.folder.renameTo(target);
        return read(target);
    }

    /** Передумали на окне установки — временная папка не должна остаться. */
    public static void discard(Plugin staged) {
        if (staged != null) {
            delete(staged.folder);
        }
    }

    public static Plugin install(Context context, InputStream source) {
        return commit(stage(context, source));
    }

    public static void remove(Plugin plugin) {
        if (plugin != null) {
            delete(plugin.folder);
            MargeletConfig.setPluginEnabled(plugin.id, false);
        }
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }

    /**
     * Кладёт пример плагина при первом запуске. Выключенным: пример нужен,
     * чтобы его открыли и прочитали, а не чтобы он что-то делал сам.
     */
    public static void preinstallExample() {
        if (!MargeletConfig.claimExamplePlugin()) {
            return;
        }
        try {
            final Context context = ApplicationLoader.applicationContext;
            final Plugin plugin = install(context, context.getAssets().open("margelet_example.marp"));
            if (plugin != null) {
                MargeletConfig.setPluginEnabled(plugin.id, false);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Есть ли что запускать. Питон не поднимаем зря: это одиннадцать мегабайт. */
    public static boolean anyEnabled() {
        if (!MargeletConfig.pluginsEnabled()) {
            return false;
        }
        for (Plugin plugin : installed()) {
            if (plugin.enabled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Как назвать разрешение по-человечески. Незнакомое имя показываем как
     * есть: автор мог написать что угодно, и подменять это на «прочее» —
     * значит прятать.
     */
    public static String permissionName(String key) {
        if ("read_chats".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermRead);
        } else if ("send_messages".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermSend);
        } else if ("edit_messages".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermEdit);
        } else if ("delete_messages".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermDelete);
        } else if ("change_profile".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermProfile);
        } else if ("ui".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermUi);
        }
        return key;
    }

    /**
     * Имя или описание на языке приложения: рядом с «name» автор может
     * положить «name_en», «name_zh» и любое другое. Нет перевода — берём то,
     * что есть; выдумывать за автора нечего.
     */
    private static String localized(JSONObject json, String key, String fallback) {
        String language = null;
        try {
            language = LocaleController.getInstance().getCurrentLocale().getLanguage();
        } catch (Exception ignored) {
        }
        if (language != null) {
            final String value = json.optString(key + "_" + language, null);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return json.optString(key, fallback);
    }

    /**
     * Окно установки: кто автор, что заявлено, и честная строка о том, что
     * заявленное никем не проверяется.
     *
     * Живёт здесь, а не на экране плагинов, потому что ставить умеют оба
     * входа — экран настроек и нажатие на файл .marp прямо в переписке.
     * Один текст на два места лучше, чем два текста, которые разойдутся.
     *
     * @param whenInstalled что сделать после установки; может быть null.
     * @return false, если это не плагин — тогда звать было не за чем.
     */
    /**
     * Хватает ли этой сборки форка для такого плагина.
     *
     * Автор пишет в манифесте `min_version`. Ставить плагин, которому нужны
     * двери, которых в этой сборке ещё нет, — значит отдать человеку молча
     * ничего не делающий плагин; он будет думать, что сломано у него.
     */
    public static boolean supported(Plugin plugin) {
        if (plugin == null || plugin.minVersion == null || plugin.minVersion.isEmpty()) {
            return true;
        }
        return !MargeletUpdate.newer(plugin.minVersion, MargeletConfig.APP_VERSION);
    }

    /**
     * Перезапуск приложения: включённый плагин поднимается только на старте,
     * а выключенный до перезапуска доживает — остановить работающий питон
     * нечем. Раньше человеку приходилось убивать телеграм самому.
     */
    public static void restart(Context context) {
        MargeletFonts.restart(context instanceof android.app.Activity
                ? (android.app.Activity) context : null);
    }

    public static boolean askInstall(Context context, InputStream source, Runnable whenInstalled) {
        // Сначала кладём архив на диск, и уже из него разбираем. Поток читается
        // один раз, а архив нужен ещё и потом: человек вправе прочитать код
        // до установки, а не после.
        final File archive = keepArchive(context, source);
        if (archive == null) {
            return false;
        }
        Plugin first = null;
        try {
            first = stage(context, new java.io.FileInputStream(archive));
        } catch (Exception ignored) {
        }
        final Plugin[] staged = { first };
        if (staged[0] == null) {
            return false;
        }
        final Plugin plugin = staged[0];
        // Тот же плагин уже стоит? Тогда это обновление, и говорить надо так.
        // Раньше окно молчало, ставило рядом второй раз, и человек получал
        // два одинаковых плагина: в списке один, а делают они всё вдвойне.
        Plugin existing = null;
        for (Plugin already : installed()) {
            if (already.id.equals(plugin.id)) {
                existing = already;
                break;
            }
        }

        if (!supported(plugin)) {
            // Не ставим вовсе: плагин рассчитан на сборку новее этой.
            discard(staged[0]);
            staged[0] = null;
            new AlertDialog.Builder(context)
                    .setTitle(plugin.name)
                    .setMessage(LocaleController.formatString(R.string.MargeletPluginTooOld,
                            plugin.minVersion, MargeletConfig.APP_VERSION))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
            return true;
        }

        final boolean[] startNow = { false };
        final boolean[] keep = { false };
        final Runnable[] show = new Runnable[1];
        final SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(LocaleController.formatString(R.string.MargeletPluginBy, plugin.author)).append("\n\n");
        if (existing != null) {
            text.append(LocaleController.formatString(R.string.MargeletPluginAlready, existing.version))
                    .append("\n\n");
        }
        text.append(LocaleController.getString(R.string.MargeletPluginDeclares));
        if (plugin.permissions.isEmpty()) {
            text.append("\n— ").append(LocaleController.getString(R.string.MargeletPluginPermNone));
        } else {
            for (String permission : plugin.permissions) {
                text.append("\n— ").append(permissionName(permission));
            }
        }
        text.append("\n\n").append(LocaleController.getString(R.string.MargeletPluginInstallWarn));
        if (existing != null) {
            text.append("\n\n").append(LocaleController.getString(R.string.MargeletPluginAlreadyRestart));
        }

        // Копия: existing присваивается в цикле выше, а лямбда берёт только
        // то, что после присвоения уже не меняется.
        final Plugin already = existing;
        show[0] = () -> buildInstallDialog(context, staged, plugin, already, archive,
                text, startNow, keep, show, whenInstalled);
        show[0].run();
        return true;
    }

    /**
     * Само окно установки. Вынесено отдельно ровно потому, что показать его
     * надо уметь дважды: человек уходит читать архив и возвращается.
     */
    private static void buildInstallDialog(Context context, Plugin[] staged, Plugin plugin,
                                           Plugin existing, File archive,
                                           SpannableStringBuilder text, boolean[] startNow,
                                           boolean[] keep, Runnable[] show, Runnable whenInstalled) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(plugin.name + " " + plugin.version)
                .setMessage(text)
                .setPositiveButton(LocaleController.getString(existing != null
                        ? R.string.MargeletPluginUpdateOk : R.string.MargeletPluginInstallOk), (d, w) -> {
                    final Plugin ready = commit(staged[0]);
                    staged[0] = null;
                    // Включение перезапуска не требует: поднять плагин можно
                    // прямо сейчас. Перезапуск нужен обратному — выключить уже
                    // работающий питон нечем, — и просят о нём там, где
                    // выключают, а не здесь.
                    if (startNow[0] && ready != null) {
                        MargeletConfig.setPluginEnabled(ready.id, true);
                        if (MargeletConfig.pluginsEnabled()) {
                            MargeletPluginHost.launch(ready);
                        }
                    }
                    if (whenInstalled != null) {
                        whenInstalled.run();
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                // Прочитать код до установки. Правило форума — «плагин едет
                // исходником, и открыть его должен мочь любой» — до сих пор
                // держалось на честном слове: посмотреть содержимое можно было
                // только после установки, то есть уже пустив чужой код внутрь.
                .setNeutralButton(LocaleController.getString(R.string.MargeletPluginViewSource), (d, w) -> {
                    keep[0] = true;
                    openArchive(context, archive);
                    // Окно закрылось нажатием, а вернувшись из архиватора,
                    // человек ожидает застать его на месте — иначе придётся
                    // заново искать файл. Показываем его заново.
                    AndroidUtilities.runOnUIThread(() -> {
                        keep[0] = false;
                        show[0].run();
                    }, 300);
                })
                // Отказ бывает и кнопкой «назад» — распакованное не должно
                // остаться лежать в папке. Но уход в архиватор отказом не
                // считается.
                .setOnDismissListener(d -> {
                    if (keep[0]) {
                        return;
                    }
                    discard(staged[0]);
                    staged[0] = null;
                    archive.delete();
                });
        // Галочка «включить сразу». Раньше это была третья кнопка, и окно из-за
        // неё читалось как выбор между двумя установками, хотя установка одна,
        // а решается тут только одно: запускать ли сейчас.
        //
        // Снята по умолчанию нарочно. Прямо над ней написано, что плагин может
        // всё, что может приложение; поставить галочку — одно движение, а
        // «поставилось и сразу побежало» на плагине, который человек видит
        // впервые, отменить уже нечем.
        final CheckBoxCell enableCell = new CheckBoxCell(context, 1, null);
        enableCell.allowMultiline();
        enableCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        enableCell.setText(LocaleController.getString(R.string.MargeletPluginEnableAfter), "", false, false);
        enableCell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0,
                LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
        enableCell.setOnClickListener(v -> {
            startNow[0] = !startNow[0];
            ((CheckBoxCell) v).setChecked(startNow[0], true);
        });
        builder.setView(enableCell);

        // Значок автора, если он его положил: по названию не всегда понятно,
        // что именно тебе предлагают поставить.
        final Bitmap icon = plugin.icon();
        if (icon != null) {
            try {
                final ImageView view = new ImageView(context);
                view.setImageBitmap(icon);
                final int size = AndroidUtilities.dp(64);
                final FrameLayout wrap = new FrameLayout(context);
                wrap.addView(view, LayoutHelper.createFrame(64, 64, Gravity.CENTER, 0, 12, 0, 4));
                builder.setTopView(wrap);
            } catch (Throwable ignored) {
                // Окно без значка — всё ещё окно.
            }
        }
        builder.show();
    }

    /** Кладёт присланный архив на диск: из потока читать можно только раз. */
    private static File keepArchive(Context context, InputStream source) {
        try {
            final File folder = new File(context.getFilesDir(), "cache");
            folder.mkdirs();
            // Имя одно на всех: смотрят архив по одному, и копить их незачем.
            final File file = new File(folder, "margelet_plugin.marp");
            final FileOutputStream out = new FileOutputStream(file);
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.close();
            return file.length() > 0 ? file : null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * Открывает архив плагина чем-нибудь, что умеет архивы.
     *
     * Своей смотрелки не пишем: файловые менеджеры показывают zip лучше, чем
     * это сделали бы мы, и человеку привычнее. Если открыть нечем, предлагаем
     * передать файл куда угодно — тогда он хотя бы дойдёт до архиватора.
     */
    private static void openArchive(Context context, File file) {
        try {
            final android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, ApplicationLoader.getApplicationId() + ".provider", file);
            final android.content.Intent view = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW);
            view.setDataAndType(uri, "application/zip");
            view.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (view.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(view);
                return;
            }
            // Смотреть zip нечем — отдаём файл наружу.
            final android.content.Intent share = new android.content.Intent(
                    android.content.Intent.ACTION_SEND);
            share.setType("application/zip");
            share.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            share.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(android.content.Intent.createChooser(share,
                    LocaleController.getString(R.string.MargeletPluginViewSource)));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Нажали на файл .marp в переписке. Открывать его нечем — это наш
     * формат, — поэтому предлагаем поставить.
     */
    public static boolean offerInstall(Context context, File file) {
        try {
            return askInstall(context, new java.io.FileInputStream(file), null);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
}
