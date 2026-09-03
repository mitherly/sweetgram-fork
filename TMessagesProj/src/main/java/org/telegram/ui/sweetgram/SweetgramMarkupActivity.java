package org.telegram.ui;

import android.view.View;

import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.sweetgram.SweetgramMarkup;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Оформление»: что из своего оформления показывать у себя.
 *
 * Выключатели тут не про отправку, а про показ. Отправить оформленное можно
 * всегда — а вот принимать чужую радугу человек может и не хотеть.
 */
public class SweetgramMarkupActivity extends UniversalFragment {

    private static final int ID_SIZE = 1;
    private static final int ID_DIM = 2;
    private static final int ID_RAINBOW = 3;
    private static final int ID_OUTLINE = 90;
    private static final int ID_WATERMARKS = 4;
    private static final int ID_COPY = 5;
    private static final int ID_BUTTON = 6;
    private static final int ID_EMOJI = 7;
    private static final int ID_MARKDOWN = 8;
    private static final int ID_WATERMARK_SEND = 9;
    private static final int ID_AVATAR_CORNERS = 10;
    private static final int ID_GLASS_OUTLINE = 11;
    private static final int ID_HIDE_BOTTOM_TABS = 12;
    private static final int ID_CLASSIC_DRAWER = 13;
    private static final int ID_HIDE_ALL_CHATS = 14;
    private static final int ID_M3_SWITCHES = 15;

    private String getAvatarCornerName(int mode) {
        switch (mode) {
            case 1: return LocaleController.getString(R.string.SweetgramAvatarCornersSquare);
            case 2: return LocaleController.getString(R.string.SweetgramAvatarCornersSquircle);
            case 3: return LocaleController.getString(R.string.SweetgramAvatarCornersMedium);
            default: return LocaleController.getString(R.string.SweetgramAvatarCornersDefault);
        }
    }

    private String getGlassOutlineName(int mode) {
        switch (mode) {
            case 1: return LocaleController.getString(R.string.SweetgramGlassOutlineSolid);
            case 2: return LocaleController.getString(R.string.SweetgramGlassOutlineHidden);
            default: return LocaleController.getString(R.string.SweetgramGlassOutlineGlare);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramMarkup);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_SIZE, LocaleController.getString(R.string.SweetgramMarkupSize))
                .setChecked(SweetgramConfig.markupEnabled(SweetgramMarkup.KIND_SIZE)));
        items.add(UItem.asCheck(ID_DIM, LocaleController.getString(R.string.SweetgramMarkupDim))
                .setChecked(SweetgramConfig.markupEnabled(SweetgramMarkup.KIND_DIM)));
        items.add(UItem.asCheck(ID_RAINBOW, LocaleController.getString(R.string.SweetgramMarkupRainbow))
                .setChecked(SweetgramConfig.markupEnabled(SweetgramMarkup.KIND_RAINBOW)));
        items.add(UItem.asCheck(ID_OUTLINE, LocaleController.getString(R.string.SweetgramMarkupOutline))
                .setChecked(SweetgramConfig.markupEnabled(SweetgramMarkup.KIND_OUTLINE)));
        items.add(UItem.asCheck(ID_BUTTON, LocaleController.getString(R.string.SweetgramMarkupButton))
                .setChecked(SweetgramConfig.markupEnabled(SweetgramMarkup.KIND_BUTTON)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramMarkupAbout)));
        items.add(UItem.asCheck(ID_EMOJI, LocaleController.getString(R.string.SweetgramFreeEmoji))
                .setChecked(SweetgramConfig.freeEmoji()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramFreeEmojiAbout)));
        items.add(UItem.asButton(ID_MARKDOWN, LocaleController.getString(R.string.SweetgramMarkdown),
                LocaleController.getString(R.string.SweetgramMarkdownInfo)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(ID_COPY, LocaleController.getString(R.string.SweetgramCopyFormatted))
                .setChecked(SweetgramConfig.copyFormatting()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramCopyFormattedAbout)));
        items.add(UItem.asButton(ID_AVATAR_CORNERS, LocaleController.getString(R.string.SweetgramAvatarCorners),
                getAvatarCornerName(SweetgramConfig.avatarRadius())));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_GLASS_OUTLINE, LocaleController.getString(R.string.SweetgramGlassOutlineStyle),
                getGlassOutlineName(SweetgramConfig.glassOutlineStyle())));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(ID_HIDE_BOTTOM_TABS, LocaleController.getString(R.string.SweetgramHideBottomTabs))
                .setChecked(SweetgramConfig.hideBottomTabs()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramHideBottomTabsAbout)));
        items.add(UItem.asCheck(ID_CLASSIC_DRAWER, LocaleController.getString(R.string.SweetgramClassicDrawer))
                .setChecked(SweetgramConfig.classicDrawer()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramClassicDrawerAbout)));
        items.add(UItem.asCheck(ID_HIDE_ALL_CHATS, LocaleController.getString(R.string.SweetgramHideAllChats))
                .setChecked(SweetgramConfig.hideAllChats()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramHideAllChatsAbout)));
        items.add(UItem.asCheck(ID_M3_SWITCHES, LocaleController.getString(R.string.SweetgramM3SwitchStyle))
                .setChecked(SweetgramConfig.m3SwitchStyle()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramM3SwitchStyleAbout)));
        items.add(UItem.asShadow("Based on Margy (@margeletter , github.com/narezany/Margelet)"));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_M3_SWITCHES) {
            SweetgramConfig.setM3SwitchStyle(!SweetgramConfig.m3SwitchStyle());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            return;
        }
        if (item.id == ID_HIDE_ALL_CHATS) {
            SweetgramConfig.setHideAllChats(!SweetgramConfig.hideAllChats());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            return;
        }
        if (item.id == ID_AVATAR_CORNERS) {
            final CharSequence[] options = new CharSequence[]{
                    LocaleController.getString(R.string.SweetgramAvatarCornersDefault),
                    LocaleController.getString(R.string.SweetgramAvatarCornersSquare),
                    LocaleController.getString(R.string.SweetgramAvatarCornersSquircle),
                    LocaleController.getString(R.string.SweetgramAvatarCornersMedium)
            };
            new AlertDialog.Builder(getContext())
                    .setTitle(LocaleController.getString(R.string.SweetgramAvatarCorners))
                    .setItems(options, (d, which) -> {
                        SweetgramConfig.setAvatarRadius(which);
                        listView.adapter.update(true);
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
                        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_ALL);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return;
        } else if (item.id == ID_GLASS_OUTLINE) {
            final CharSequence[] options = new CharSequence[]{
                    LocaleController.getString(R.string.SweetgramGlassOutlineGlare),
                    LocaleController.getString(R.string.SweetgramGlassOutlineSolid),
                    LocaleController.getString(R.string.SweetgramGlassOutlineHidden)
            };
            new AlertDialog.Builder(getContext())
                    .setTitle(LocaleController.getString(R.string.SweetgramGlassOutlineStyle))
                    .setItems(options, (d, which) -> {
                        SweetgramConfig.setGlassOutlineStyle(which);
                        listView.adapter.update(true);
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return;
        } else if (item.id == ID_HIDE_BOTTOM_TABS) {
            SweetgramConfig.setHideBottomTabs(!SweetgramConfig.hideBottomTabs());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_ALL);
            return;
        } else if (item.id == ID_CLASSIC_DRAWER) {
            SweetgramConfig.setClassicDrawer(!SweetgramConfig.classicDrawer());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            return;
        }
        if (item.id == ID_COPY) {
            SweetgramConfig.setCopyFormatting(!SweetgramConfig.copyFormatting());
        } else if (item.id == ID_MARKDOWN) {
            presentFragment(new SweetgramMarkdownActivity());
            return;
        } else if (item.id == ID_EMOJI) {
            SweetgramConfig.setFreeEmoji(!SweetgramConfig.freeEmoji());
        } else if (item.id == ID_BUTTON) {
            SweetgramConfig.setMarkupEnabled(SweetgramMarkup.KIND_BUTTON,
                    !SweetgramConfig.markupEnabled(SweetgramMarkup.KIND_BUTTON));
        } else {
            final int kind = item.id == ID_SIZE ? SweetgramMarkup.KIND_SIZE
                    : item.id == ID_DIM ? SweetgramMarkup.KIND_DIM
                    : item.id == ID_OUTLINE ? SweetgramMarkup.KIND_OUTLINE
                    : SweetgramMarkup.KIND_RAINBOW;
            SweetgramConfig.setMarkupEnabled(kind, !SweetgramConfig.markupEnabled(kind));
        }
        listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
