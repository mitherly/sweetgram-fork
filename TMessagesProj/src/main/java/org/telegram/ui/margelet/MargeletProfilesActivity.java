package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Профили»: что форк дописывает на страницу человека или чата. */
public class MargeletProfilesActivity extends UniversalFragment {

    private static final int ID_SHOW_IDS = 1;
    private static final int ID_BADGES = 2;
    private static final int ID_GALLERY = 3;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletProfiles);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_SHOW_IDS, LocaleController.getString(R.string.MargeletShowIds))
                .setChecked(MargeletConfig.showIds()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletShowIdsAbout)));
        items.add(UItem.asCheck(ID_BADGES, LocaleController.getString(R.string.MargeletBadges))
                .setChecked(MargeletConfig.badgesEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletBadgesAbout)));
        items.add(UItem.asButton(ID_GALLERY, LocaleController.getString(R.string.MargeletBadgeGallery),
                LocaleController.getString(R.string.MargeletBadgeGalleryInfo)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SHOW_IDS) {
            MargeletConfig.setShowIds(!MargeletConfig.showIds());
            listView.adapter.update(true);
        } else if (item.id == ID_GALLERY) {
            presentFragment(new MargeletBadgeGalleryActivity());
        } else if (item.id == ID_BADGES) {
            MargeletConfig.setBadgesEnabled(!MargeletConfig.badgesEnabled());
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
