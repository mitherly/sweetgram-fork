package org.telegram.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.sweetgram.SweetgramPlane3D;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Корень своего раздела: ветки, а не свалка переключателей. Пока веток одна,
 * «Поле ввода», плюс две ссылки — канал и форум.
 *
 * Строки рисуются тем же классом, что и на главном экране настроек
 * (SettingsActivity.SettingCell): цветная плашка со значком, название,
 * подпись под ним. Первая версия была собрана на старых ячейках, и владелец
 * сразу заметил, что раздел выглядит из прошлой версии приложения.
 */
public class SweetgramSettingsActivity extends UniversalFragment {

    private static final int ID_INPUT = 1;
    private static final int ID_SOUND = 2;
    private static final int ID_CHANNEL = 3;
    private static final int ID_FORUM = 4;
    private static final int ID_CONVENIENCES = 5;
    private static final int ID_UPDATES = 17;
    private static final int ID_STREAMER = 6;
    private static final int ID_GIFTS = 7;
    private static final int ID_SOURCE = 8;
    private static final int ID_PROFILES = 9;
    private static final int ID_MARKUP = 12;
    private static final int ID_HELP = 13;
    private static final int ID_STICKERS = 14;
    private static final int ID_PLUGINS = 15;
    private static final int ID_FEEDBACK = 18;
    private static final int ID_ADMIN = 19;

    /** Объёмный значок в шапке. Пользы ноль, и в этом вся мысль. */
    private FrameLayout header;

    @Override
    protected CharSequence getTitle() {
        return "Sweetgram";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (header == null && getContext() != null) {
            header = new FrameLayout(getContext());
            // Значок сидит в своём квадрате, а не во всю ширину строки: тогда
            // список тянется пальцем везде, кроме самого значка, и вертикальная
            // прокрутка не спорит с вращением.
            // Логотип в своей секции, а не на всю ширину строки: тогда
            // список выглядит аккуратно, крупной карточкой.
            final android.widget.ImageView logoView = new android.widget.ImageView(getContext());
            logoView.setImageResource(R.drawable.logo);
            header.addView(logoView,
                    LayoutHelper.createFrame(150, 150, Gravity.CENTER));
        }
        if (header != null) {
            items.add(UItem.asCustomShadow(header, 168));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_INPUT,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                R.drawable.settings_chat, LocaleController.getString(R.string.SweetgramInput), LocaleController.getString(R.string.SweetgramInputInfo)));
        // Раздел «Звук» появляется, только когда мяуканье уже услышали.
        if (SweetgramConfig.meowHeard()) {
            items.add(SettingsActivity.SettingCell.Factory.of(ID_SOUND,
                    IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom,
                    R.drawable.settings_sounds, LocaleController.getString(R.string.SweetgramSound), LocaleController.getString(R.string.SweetgramSoundInfo)));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_STREAMER,
                IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom,
                R.drawable.settings_privacy, LocaleController.getString(R.string.SweetgramStreamer),
                LocaleController.getString(R.string.SweetgramStreamerInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CONVENIENCES,
                IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                R.drawable.settings_folders, LocaleController.getString(R.string.SweetgramConveniences),
                LocaleController.getString(R.string.SweetgramConveniencesInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_MARKUP,
                IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom,
                R.drawable.settings_language, LocaleController.getString(R.string.SweetgramMarkup),
                LocaleController.getString(R.string.SweetgramMarkupInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_PROFILES,
                IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom,
                R.drawable.settings_account, LocaleController.getString(R.string.SweetgramProfiles),
                LocaleController.getString(R.string.SweetgramProfilesInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_GIFTS,
                IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom,
                R.drawable.settings_gift, LocaleController.getString(R.string.SweetgramGifts),
                LocaleController.getString(R.string.SweetgramGiftsInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_UPDATES,
                IconBackgroundColors.CYAN.top, IconBackgroundColors.CYAN.bottom,
                R.drawable.settings_devices, LocaleController.getString(R.string.SweetgramUpdates),
                LocaleController.getString(R.string.SweetgramUpdatesInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_PLUGINS,
                IconBackgroundColors.BLUE_ALT.top, IconBackgroundColors.BLUE_ALT.bottom,
                R.drawable.settings_devices, LocaleController.getString(R.string.SweetgramPlugins),
                LocaleController.getString(R.string.SweetgramPluginsInfo)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_STICKERS, LocaleController.getString(R.string.SweetgramStickers),
                LocaleController.getString(R.string.SweetgramStickersAdd)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramStickersAbout)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CHANNEL,
                IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom,
                R.drawable.settings_channel, LocaleController.getString(R.string.SweetgramChannel), "t.me/SweetGramOfficial"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_FORUM,
                IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom,
                R.drawable.settings_group, LocaleController.getString(R.string.SweetgramForum), "github.com/mitherly/sweetgram-fork"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_FEEDBACK,
                IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                R.drawable.msg_message, LocaleController.getString(R.string.SweetgramFeedback),
                LocaleController.getString(R.string.SweetgramFeedbackInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_SOURCE,
                IconBackgroundColors.GRAY.top, IconBackgroundColors.GRAY.bottom,
                R.drawable.settings_features, LocaleController.getString(R.string.SweetgramSource),
                LocaleController.getString(R.string.SweetgramSourceInfo)));
        items.add(UItem.asButton(ID_ADMIN, "Admin panel"));
        items.add(UItem.asShadow(null));
    }

    @Override
    public View createView(android.content.Context context) {
        header = null;      // прошлый экран уносит с собой своё окно
        final View view = super.createView(context);
        // Знак вопроса в шапке: короткий рассказ о том, что это вообще такое.
        // Свой обработчик ставится после родительского и потому заменяет его —
        // значит, «назад» надо обслужить самому.
        actionBar.createMenu().addItem(ID_HELP, R.drawable.outline_question_mark);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_HELP) {
                    new AlertDialog.Builder(getContext())
                            .setTitle(LocaleController.getString(R.string.SweetgramAboutTitle))
                            .setMessage(LocaleController.getString(R.string.SweetgramAboutText))
                            .setPositiveButton(LocaleController.getString(R.string.Close), null)
                            .show();
                }
            }
        });
        // Скруглённые карточки — так выглядят нынешние экраны настроек.
        // Без этой строки список рисуется сплошной лентой, как в прошлой
        // версии приложения: владелец это заметил сразу.
        listView.setSections();
        return view;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_STICKERS) {
            Browser.openUrl(getContext(), SweetgramConfig.STICKERS_URL);
        } else if (item.id == ID_PLUGINS) {
            presentFragment(new SweetgramPluginsActivity());
        } else if (item.id == ID_MARKUP) {
            presentFragment(new SweetgramMarkupActivity());
        } else if (item.id == ID_INPUT) {
            presentFragment(new SweetgramInputActivity());
        } else if (item.id == ID_SOUND) {
            presentFragment(new SweetgramSoundActivity());
        } else if (item.id == ID_CHANNEL) {
            Browser.openUrl(getContext(), SweetgramConfig.CHANNEL_URL);
        } else if (item.id == ID_PROFILES) {
            presentFragment(new SweetgramProfilesActivity());
        } else if (item.id == ID_GIFTS) {
            presentFragment(new SweetgramGiftsActivity());
        } else if (item.id == ID_STREAMER) {
            presentFragment(new SweetgramStreamerActivity());
        } else if (item.id == ID_UPDATES) {
            presentFragment(new SweetgramUpdatesActivity());
        } else if (item.id == ID_CONVENIENCES) {
            presentFragment(new SweetgramConveniencesActivity());
        } else if (item.id == ID_SOURCE) {
            Browser.openUrl(getContext(), SweetgramConfig.SOURCE_URL);
        } else if (item.id == ID_FEEDBACK) {
            Browser.openUrl(getContext(), SweetgramConfig.FEEDBACK_URL);
        } else if (item.id == ID_ADMIN) {
            final android.widget.EditText input = new android.widget.EditText(getContext());
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                    .setTitle("Admin access")
                    .setMessage("Enter password:")
                    .setView(input)
                    .setPositiveButton("Submit", (dialog, which) -> {
                        if ("507143561213885610345637".equals(input.getText().toString())) {
                            presentFragment(new com.sweetgram.SweetgramAdminActivity());
                        } else {
                            android.widget.Toast.makeText(getContext(), "Incorrect password", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else if (item.id == ID_FORUM) {
            Browser.openUrl(getContext(), SweetgramConfig.FORUM_URL);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

}
