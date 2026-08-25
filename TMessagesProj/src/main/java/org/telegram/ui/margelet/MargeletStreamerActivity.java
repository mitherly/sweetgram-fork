package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Режим стримера»: что прятать на экране во время эфира. */
public class MargeletStreamerActivity extends UniversalFragment {

    private static final int ID_ON = 1;
    private static final int ID_OTHERS = 2;
    private static final int ID_USERNAME = 3;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletStreamer);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_ON, LocaleController.getString(R.string.MargeletStreamerOn))
                .setChecked(MargeletConfig.streamerMode()));
        // Остальные два появляются только при включённом режиме: сами по себе
        // они ничего не делают, и включённый выключатель без последствий
        // выглядит как поломка.
        if (MargeletConfig.streamerMode()) {
            items.add(UItem.asCheck(ID_OTHERS, LocaleController.getString(R.string.MargeletStreamerOthers))
                    .setChecked(MargeletConfig.streamerHidesOthers()));
            items.add(UItem.asCheck(ID_USERNAME, LocaleController.getString(R.string.MargeletStreamerUsername))
                    .setChecked(MargeletConfig.streamerHidesUsername()));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletStreamerAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ON) {
            MargeletConfig.setStreamerMode(!MargeletConfig.streamerMode());
        } else if (item.id == ID_OTHERS) {
            MargeletConfig.setStreamerHidesOthers(!MargeletConfig.streamerHidesOthers());
        } else if (item.id == ID_USERNAME) {
            MargeletConfig.setStreamerHidesUsername(!MargeletConfig.streamerHidesUsername());
        } else {
            return;
        }
        listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
