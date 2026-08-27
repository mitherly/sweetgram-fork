package org.telegram.ui;

import android.view.View;

import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.sweetgram.SweetgramSeizure;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * «Удобности» — мелкие переключатели, которым не нужен свой экран каждому:
 * канал форка сверху, теги музыки и «приступ». Раньше они были разбросаны по
 * корню настроек (а теги музыки занимали целую вкладку ради одного тумблера);
 * собраны сюда, чтобы корень не был свалкой.
 */
public class SweetgramConveniencesActivity extends UniversalFragment {

    private static final int ID_CHANNEL_TOP = 1;
    private static final int ID_TRACKS = 2;
    private static final int ID_SEIZURE = 3;
    private static final int ID_FONTS = 4;
    private static final int ID_RELATIVE_ONLINE_TIME = 5;
    private static final int ID_FILTER_ZALGO = 6;
    private static final int ID_HIDE_SEND_AS = 7;
    private static final int ID_HIDE_BOT_BUTTON = 8;
    private static final int ID_ANTI_DELETE = 9;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramConveniences);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        // Скруглённые карточки — как на прочих экранах настроек.
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_ANTI_DELETE, LocaleController.getString(R.string.SweetgramAntiDelete))
                .setChecked(SweetgramConfig.antiDelete()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramAntiDeleteAbout)));
        items.add(UItem.asCheck(ID_CHANNEL_TOP, LocaleController.getString(R.string.SweetgramChannelOnTop))
                .setChecked(SweetgramConfig.channelOnTop()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramChannelOnTopAbout)));
        items.add(UItem.asCheck(ID_RELATIVE_ONLINE_TIME, LocaleController.getString(R.string.SweetgramRelativeOnlineTime))
                .setChecked(SweetgramConfig.relativeOnlineTime()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramRelativeOnlineTimeAbout)));
        items.add(UItem.asCheck(ID_FILTER_ZALGO, LocaleController.getString(R.string.SweetgramFilterZalgo))
                .setChecked(SweetgramConfig.filterZalgo()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramFilterZalgoAbout)));
        items.add(UItem.asCheck(ID_HIDE_SEND_AS, LocaleController.getString(R.string.SweetgramHideSendAsPeer))
                .setChecked(SweetgramConfig.hideSendAsPeer()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramHideSendAsPeerAbout)));
        items.add(UItem.asCheck(ID_HIDE_BOT_BUTTON, LocaleController.getString(R.string.SweetgramHideBotButton))
                .setChecked(SweetgramConfig.hideBotButton()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramHideBotButtonAbout)));
        items.add(UItem.asCheck(ID_TRACKS, LocaleController.getString(R.string.SweetgramTracksEnabled))
                .setChecked(SweetgramConfig.tagsEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramTracksEnabledAbout)));
        items.add(UItem.asCheck(ID_SEIZURE, LocaleController.getString(R.string.SweetgramSeizure))
                .setChecked(SweetgramSeizure.enabled()));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_FONTS, LocaleController.getString(R.string.SweetgramFonts),
                LocaleController.getString(R.string.SweetgramFontsInfo)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ANTI_DELETE) {
            SweetgramConfig.setAntiDelete(!SweetgramConfig.antiDelete());
            listView.adapter.update(true);
        } else if (item.id == ID_CHANNEL_TOP) {
            SweetgramConfig.setChannelOnTop(!SweetgramConfig.channelOnTop());
            listView.adapter.update(true);
        } else if (item.id == ID_RELATIVE_ONLINE_TIME) {
            SweetgramConfig.setRelativeOnlineTime(!SweetgramConfig.relativeOnlineTime());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_STATUS);
        } else if (item.id == ID_FILTER_ZALGO) {
            SweetgramConfig.setFilterZalgo(!SweetgramConfig.filterZalgo());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
        } else if (item.id == ID_HIDE_SEND_AS) {
            SweetgramConfig.setHideSendAsPeer(!SweetgramConfig.hideSendAsPeer());
            listView.adapter.update(true);
        } else if (item.id == ID_HIDE_BOT_BUTTON) {
            SweetgramConfig.setHideBotButton(!SweetgramConfig.hideBotButton());
            listView.adapter.update(true);
        } else if (item.id == ID_TRACKS) {
            SweetgramConfig.setTagsEnabled(!SweetgramConfig.tagsEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_SEIZURE) {
            toggleSeizure();
        } else if (item.id == ID_FONTS) {
            presentFragment(new SweetgramFontsActivity());
        }
    }

    /**
     * Выключается молча, включается только через предупреждение: подвижная
     * картинка бывает опасна не в переносном смысле, и решать это за человека
     * нельзя.
     */
    private void toggleSeizure() {
        if (SweetgramSeizure.enabled()) {
            SweetgramSeizure.set(false);
            listView.adapter.update(true);
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.SweetgramSeizureWarning))
                .setMessage(LocaleController.getString(R.string.SweetgramSeizureWarningText))
                .setPositiveButton(LocaleController.getString(R.string.SweetgramSeizureEnable), (d, w) -> {
                    SweetgramSeizure.set(true);
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
