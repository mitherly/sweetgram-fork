package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.telegram.sweetgram.SweetgramBanner;
import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Профили»: что форк дописывает на страницу человека или чата. */
public class SweetgramProfilesActivity extends UniversalFragment {

    private static final int ID_SHOW_IDS = 1;
    private static final int ID_BANNER = 2;
    private static final int ID_BANNER_OFF = 3;
    private static final int ID_BANNERS_SHOW = 4;

    private static final int PICK_BANNER = 4833;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramProfiles);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_SHOW_IDS, LocaleController.getString(R.string.SweetgramShowIds))
                .setChecked(SweetgramConfig.showIds()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramShowIdsAbout)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.SweetgramBannerHeader)));
        items.add(UItem.asButton(ID_BANNER, LocaleController.getString(R.string.SweetgramBannerPick)));
        items.add(UItem.asButton(ID_BANNER_OFF, LocaleController.getString(R.string.SweetgramBannerRemove)));
        items.add(UItem.asCheck(ID_BANNERS_SHOW, LocaleController.getString(R.string.SweetgramBannerShow))
                .setChecked(SweetgramConfig.bannersEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramBannerAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SHOW_IDS) {
            SweetgramConfig.setShowIds(!SweetgramConfig.showIds());
            listView.adapter.update(true);
        } else if (item.id == ID_BANNER) {
            pick();
        } else if (item.id == ID_BANNER_OFF) {
            SweetgramBanner.clear(what -> {
                listView.adapter.update(true);
                if (what == SweetgramBanner.REMOVED) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.SweetgramBannerRemoved)).show();
                } else if (what == SweetgramBanner.NOTHING) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.SweetgramBannerNone)).show();
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                            LocaleController.getString(R.string.SweetgramGroupUnreachable)).show();
                }
            });
        } else if (item.id == ID_BANNERS_SHOW) {
            SweetgramConfig.setBannersEnabled(!SweetgramConfig.bannersEnabled());
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void pick() {
        // Предупреждаем до выбора, а не после отправки: баннер уходит в общую
        // группу, и оттуда его видно всем, даже тем, у кого форка нет. Человек
        // должен знать это раньше, чем выберет фотографию.
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.SweetgramBannerHeader))
                .setMessage(LocaleController.getString(R.string.SweetgramBannerWarn))
                .setPositiveButton(LocaleController.getString(R.string.SweetgramBannerPick), (d, w) -> {
                    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    try {
                        startActivityForResult(intent, PICK_BANNER);
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_BANNER || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        SweetgramBanner.set(uri, () -> {
            if (getContext() == null) {
                return;
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.SweetgramBannerSaved)).show();
        });
    }
}
