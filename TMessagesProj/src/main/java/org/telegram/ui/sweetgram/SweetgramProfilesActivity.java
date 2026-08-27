package org.telegram.ui;

import android.view.View;

import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Профили»: что форк дописывает на страницу человека или чата. */
public class SweetgramProfilesActivity extends UniversalFragment {

    private static final int ID_SHOW_IDS = 1;

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
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SHOW_IDS) {
            SweetgramConfig.setShowIds(!SweetgramConfig.showIds());
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
