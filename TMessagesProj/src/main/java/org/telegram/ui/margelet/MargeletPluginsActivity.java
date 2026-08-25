package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.margelet.MargeletHooks;
import org.telegram.margelet.MargeletPluginHost;
import org.telegram.margelet.MargeletPlugins;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Ветка «Плагины»: список, установка, консоль и ссылки.
 *
 * Экран честный до неудобства. Плагин выполняется внутри приложения и может
 * всё, что может оно, — значит, так и написано, а не «разрешения защищают
 * вас». Разрешения из манифеста показываются как заявление автора: это то,
 * что он о себе сказал, проверить их приложению нечем.
 */
public class MargeletPluginsActivity extends UniversalFragment {

    private static final int ID_MASTER = 1;
    private static final int ID_INSTALL = 2;
    private static final int ID_CONSOLE = 3;
    private static final int ID_DOCS = 4;
    private static final int ID_FORUM = 5;
    private static final int ID_RESTART = 6;
    /** Строки самих плагинов идут отсюда и дальше, по одному номеру на плагин. */
    private static final int ID_PLUGIN = 100;

    private static final int PICK_FILE = 4831;

    private List<MargeletPlugins.Plugin> plugins = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletPlugins);
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        plugins = MargeletPlugins.installed();

        items.add(UItem.asCheck(ID_MASTER, LocaleController.getString(R.string.MargeletPlugins))
                .setChecked(MargeletConfig.pluginsEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletPluginsAbout)));

        if (!plugins.isEmpty()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.MargeletPluginsInstalled)));
            for (int i = 0; i < plugins.size(); i++) {
                final MargeletPlugins.Plugin plugin = plugins.get(i);
                items.add(MargeletPluginCell.Factory.of(ID_PLUGIN + i, plugin, plugin.enabled()));
            }
            items.add(UItem.asShadow(LocaleController.getString(
                    MargeletConfig.pluginsEnabled()
                            ? R.string.MargeletPluginsHint
                            : R.string.MargeletPluginsOffHint)));
        }

        // Перезапуск прямо здесь: включённый плагин поднимается только на
        // старте, а выключенный доживает до него. Раньше человеку приходилось
        // закрывать телеграм самому и догадываться, что это вообще нужно.
        items.add(UItem.asButton(ID_RESTART, LocaleController.getString(R.string.MargeletPluginsRestart)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletPluginsRestartAbout)));
        items.add(UItem.asButton(ID_INSTALL, LocaleController.getString(R.string.MargeletPluginInstall)));
        items.add(UItem.asButton(ID_CONSOLE, LocaleController.getString(R.string.MargeletPluginConsole)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_DOCS, LocaleController.getString(R.string.MargeletPluginDocs)));
        items.add(UItem.asButton(ID_FORUM, LocaleController.getString(R.string.MargeletPluginLibrary)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MASTER) {
            toggleMaster();
        } else if (item.id == ID_RESTART) {
            MargeletPlugins.restart(getParentActivity());
        } else if (item.id == ID_INSTALL) {
            pickFile();
        } else if (item.id == ID_CONSOLE) {
            presentFragment(new MargeletPluginConsoleActivity());
        } else if (item.id == ID_DOCS) {
            Browser.openUrl(getContext(), MargeletConfig.pluginsDocsUrl());
        } else if (item.id == ID_FORUM) {
            Browser.openUrl(getContext(), MargeletConfig.FORUM_URL);
        } else if (item.id >= ID_PLUGIN) {
            open(plugin(item.id), view, x);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_PLUGIN) {
            about(plugin(item.id));
            return true;
        }
        return false;
    }

    private MargeletPlugins.Plugin plugin(int id) {
        final int index = id - ID_PLUGIN;
        return index >= 0 && index < plugins.size() ? plugins.get(index) : null;
    }

    /**
     * Главный выключатель. Включается через предупреждение: это единственное
     * место, где человек решает, пускать ли внутрь приложения чужой код.
     */
    private void toggleMaster() {
        if (MargeletConfig.pluginsEnabled()) {
            MargeletConfig.setPluginsEnabled(false);
            listView.adapter.update(true);
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.MargeletPlugins))
                .setMessage(LocaleController.getString(R.string.MargeletPluginsWarn))
                .setPositiveButton(LocaleController.getString(R.string.MargeletSeizureEnable), (d, w) -> {
                    MargeletConfig.setPluginsEnabled(true);
                    MargeletPluginHost.start();
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    /**
     * Нажатие по строке плагина. У строки два смысла, и разводим их по месту
     * нажатия: справа переключатель — значит включить или выключить, слева
     * всё остальное — значит открыть настройки, если плагин их заявил.
     *
     * Нажатие приходит вместе с координатой, так что гадать не приходится.
     * У плагина без настроек строка целиком остаётся выключателем: пустой
     * экран вместо настроек был бы хуже, чем его отсутствие.
     */
    private void open(MargeletPlugins.Plugin plugin, View view, float x) {
        if (plugin == null) {
            return;
        }
        final int edge = org.telegram.messenger.AndroidUtilities.dp(60);
        final boolean onSwitch = LocaleController.isRTL
                ? x < edge
                : x > view.getWidth() - edge;
        if (!onSwitch && MargeletHooks.hasSettings(plugin.id)) {
            presentFragment(new MargeletPluginSettingsActivity(plugin));
            return;
        }
        toggle(plugin);
    }

    private void toggle(MargeletPlugins.Plugin plugin) {
        if (plugin == null) {
            return;
        }
        final boolean on = !plugin.enabled();
        MargeletConfig.setPluginEnabled(plugin.id, on);
        listView.adapter.update(true);
        if (on && MargeletConfig.pluginsEnabled()) {
            // Включение перезапуска не требует: плагин поднимается сразу.
            MargeletPluginHost.launch(plugin);
        } else if (!on) {
            // А вот выключение — требует. Остановить уже работающий питон
            // нечем, и делать вид, что галочка его убила, нельзя. Раньше здесь
            // была подсказка внизу экрана: она честно об этом говорила, но
            // человеку оставалось закрывать телеграм самому. Спрашиваем прямо.
            new AlertDialog.Builder(getContext())
                    .setTitle(plugin.name)
                    .setMessage(LocaleController.getString(R.string.MargeletPluginStopHint))
                    .setPositiveButton(LocaleController.getString(R.string.MargeletPluginsRestart),
                            (d, w) -> MargeletPlugins.restart(getParentActivity()))
                    .setNegativeButton(LocaleController.getString(R.string.MargeletLater), null)
                    .show();
        }
    }

    /** Карточка плагина: кто написал, что заявил, и кнопка «удалить». */
    private void about(MargeletPlugins.Plugin plugin) {
        if (plugin == null) {
            return;
        }
        final SpannableStringBuilder text = new SpannableStringBuilder();
        if (plugin.description.length() > 0) {
            text.append(plugin.description).append("\n\n");
        }
        text.append(LocaleController.getString(R.string.MargeletPluginDeclares));
        if (plugin.permissions.isEmpty()) {
            text.append("\n— ").append(LocaleController.getString(R.string.MargeletPluginPermNone));
        } else {
            for (String permission : plugin.permissions) {
                text.append("\n— ").append(MargeletPlugins.permissionName(permission));
            }
        }
        new AlertDialog.Builder(getContext())
                .setTitle(plugin.name + " " + plugin.version)
                .setMessage(text)
                .setPositiveButton(LocaleController.getString(R.string.Close), null)
                .setNegativeButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    MargeletPlugins.remove(plugin);
                    listView.adapter.update(true);
                })
                .show();
    }

    private void pickFile() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        // Своего типа у .marp в системе нет, поэтому просим любой файл.
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, PICK_FILE);
        } catch (Exception ignored) {
            // Не на каждом телефоне есть чем открыть выбор файла.
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FILE || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        boolean known = false;
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                known = MargeletPlugins.askInstall(getContext(), in, () -> {
                    listView.adapter.update(true);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.MargeletPluginInstalled)).show();
                });
            }
        } catch (Exception ignored) {
        }
        if (!known) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.MargeletPluginBadFile)).show();
        }
    }
}
