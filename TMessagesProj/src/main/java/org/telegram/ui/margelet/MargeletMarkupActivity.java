package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.margelet.MargeletMarkup;
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
public class MargeletMarkupActivity extends UniversalFragment {

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
            case 1: return LocaleController.getString(R.string.MargeletAvatarCornersSquare);
            case 2: return LocaleController.getString(R.string.MargeletAvatarCornersSquircle);
            case 3: return LocaleController.getString(R.string.MargeletAvatarCornersMedium);
            default: return LocaleController.getString(R.string.MargeletAvatarCornersDefault);
        }
    }

    private String getGlassOutlineName(int mode) {
        switch (mode) {
            case 1: return LocaleController.getString(R.string.MargeletGlassOutlineSolid);
            case 2: return LocaleController.getString(R.string.MargeletGlassOutlineHidden);
            default: return LocaleController.getString(R.string.MargeletGlassOutlineGlare);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletMarkup);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_SIZE, LocaleController.getString(R.string.MargeletMarkupSize))
                .setChecked(MargeletConfig.markupEnabled(MargeletMarkup.KIND_SIZE)));
        items.add(UItem.asCheck(ID_DIM, LocaleController.getString(R.string.MargeletMarkupDim))
                .setChecked(MargeletConfig.markupEnabled(MargeletMarkup.KIND_DIM)));
        items.add(UItem.asCheck(ID_RAINBOW, LocaleController.getString(R.string.MargeletMarkupRainbow))
                .setChecked(MargeletConfig.markupEnabled(MargeletMarkup.KIND_RAINBOW)));
        items.add(UItem.asCheck(ID_OUTLINE, LocaleController.getString(R.string.MargeletMarkupOutline))
                .setChecked(MargeletConfig.markupEnabled(MargeletMarkup.KIND_OUTLINE)));
        items.add(UItem.asCheck(ID_BUTTON, LocaleController.getString(R.string.MargeletMarkupButton))
                .setChecked(MargeletConfig.markupEnabled(MargeletMarkup.KIND_BUTTON)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletMarkupAbout)));
        items.add(UItem.asCheck(ID_EMOJI, LocaleController.getString(R.string.MargeletFreeEmoji))
                .setChecked(MargeletConfig.freeEmoji()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletFreeEmojiAbout)));
        items.add(UItem.asButton(ID_MARKDOWN, LocaleController.getString(R.string.MargeletMarkdown),
                LocaleController.getString(R.string.MargeletMarkdownInfo)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(ID_COPY, LocaleController.getString(R.string.MargeletCopyFormatted))
                .setChecked(MargeletConfig.copyFormatting()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletCopyFormattedAbout)));
        items.add(UItem.asButton(ID_AVATAR_CORNERS, LocaleController.getString(R.string.MargeletAvatarCorners),
                getAvatarCornerName(MargeletConfig.avatarRadius())));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_GLASS_OUTLINE, LocaleController.getString(R.string.MargeletGlassOutlineStyle),
                getGlassOutlineName(MargeletConfig.glassOutlineStyle())));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(ID_HIDE_BOTTOM_TABS, LocaleController.getString(R.string.MargeletHideBottomTabs))
                .setChecked(MargeletConfig.hideBottomTabs()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletHideBottomTabsAbout)));
        items.add(UItem.asCheck(ID_CLASSIC_DRAWER, LocaleController.getString(R.string.MargeletClassicDrawer))
                .setChecked(MargeletConfig.classicDrawer()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletClassicDrawerAbout)));
        items.add(UItem.asCheck(ID_HIDE_ALL_CHATS, LocaleController.getString(R.string.MargeletHideAllChats))
                .setChecked(MargeletConfig.hideAllChats()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletHideAllChatsAbout)));
        items.add(UItem.asCheck(ID_M3_SWITCHES, LocaleController.getString(R.string.MargeletM3SwitchStyle))
                .setChecked(MargeletConfig.m3SwitchStyle()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletM3SwitchStyleAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_M3_SWITCHES) {
            MargeletConfig.setM3SwitchStyle(!MargeletConfig.m3SwitchStyle());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            return;
        }
        if (item.id == ID_HIDE_ALL_CHATS) {
            MargeletConfig.setHideAllChats(!MargeletConfig.hideAllChats());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            return;
        }
        if (item.id == ID_AVATAR_CORNERS) {
            final CharSequence[] options = new CharSequence[]{
                    LocaleController.getString(R.string.MargeletAvatarCornersDefault),
                    LocaleController.getString(R.string.MargeletAvatarCornersSquare),
                    LocaleController.getString(R.string.MargeletAvatarCornersSquircle),
                    LocaleController.getString(R.string.MargeletAvatarCornersMedium)
            };
            new AlertDialog.Builder(getContext())
                    .setTitle(LocaleController.getString(R.string.MargeletAvatarCorners))
                    .setItems(options, (d, which) -> {
                        MargeletConfig.setAvatarRadius(which);
                        listView.adapter.update(true);
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
                        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_ALL);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return;
        } else if (item.id == ID_GLASS_OUTLINE) {
            final CharSequence[] options = new CharSequence[]{
                    LocaleController.getString(R.string.MargeletGlassOutlineGlare),
                    LocaleController.getString(R.string.MargeletGlassOutlineSolid),
                    LocaleController.getString(R.string.MargeletGlassOutlineHidden)
            };
            new AlertDialog.Builder(getContext())
                    .setTitle(LocaleController.getString(R.string.MargeletGlassOutlineStyle))
                    .setItems(options, (d, which) -> {
                        MargeletConfig.setGlassOutlineStyle(which);
                        listView.adapter.update(true);
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return;
        } else if (item.id == ID_HIDE_BOTTOM_TABS) {
            MargeletConfig.setHideBottomTabs(!MargeletConfig.hideBottomTabs());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_ALL);
            return;
        } else if (item.id == ID_CLASSIC_DRAWER) {
            MargeletConfig.setClassicDrawer(!MargeletConfig.classicDrawer());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            return;
        }
        if (item.id == ID_COPY) {
            MargeletConfig.setCopyFormatting(!MargeletConfig.copyFormatting());
        } else if (item.id == ID_MARKDOWN) {
            presentFragment(new MargeletMarkdownActivity());
            return;
        } else if (item.id == ID_EMOJI) {
            MargeletConfig.setFreeEmoji(!MargeletConfig.freeEmoji());
        } else if (item.id == ID_BUTTON) {
            MargeletConfig.setMarkupEnabled(MargeletMarkup.KIND_BUTTON,
                    !MargeletConfig.markupEnabled(MargeletMarkup.KIND_BUTTON));
        } else {
            final int kind = item.id == ID_SIZE ? MargeletMarkup.KIND_SIZE
                    : item.id == ID_DIM ? MargeletMarkup.KIND_DIM
                    : item.id == ID_OUTLINE ? MargeletMarkup.KIND_OUTLINE
                    : MargeletMarkup.KIND_RAINBOW;
            MargeletConfig.setMarkupEnabled(kind, !MargeletConfig.markupEnabled(kind));
        }
        listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
