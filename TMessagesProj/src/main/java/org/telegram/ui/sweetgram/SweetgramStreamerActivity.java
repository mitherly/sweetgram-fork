package org.telegram.ui;

import android.view.View;

import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Режим стримера»: что прятать на экране во время эфира. */
public class SweetgramStreamerActivity extends UniversalFragment {

    private static final int ID_ON = 1;
    private static final int ID_OTHERS = 2;
    private static final int ID_USERNAME = 3;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramStreamer);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_ON, LocaleController.getString(R.string.SweetgramStreamerOn))
                .setChecked(SweetgramConfig.streamerMode()));
        // Остальные два появляются только при включённом режиме: сами по себе
        // они ничего не делают, и включённый выключатель без последствий
        // выглядит как поломка.
        if (SweetgramConfig.streamerMode()) {
            items.add(UItem.asCheck(ID_OTHERS, LocaleController.getString(R.string.SweetgramStreamerOthers))
                    .setChecked(SweetgramConfig.streamerHidesOthers()));
            items.add(UItem.asCheck(ID_USERNAME, LocaleController.getString(R.string.SweetgramStreamerUsername))
                    .setChecked(SweetgramConfig.streamerHidesUsername()));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramStreamerAbout)));
        items.add(UItem.asShadow("Based on Margy (@margeletter , github.com/narezany/Margelet)"));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ON) {
            SweetgramConfig.setStreamerMode(!SweetgramConfig.streamerMode());
        } else if (item.id == ID_OTHERS) {
            SweetgramConfig.setStreamerHidesOthers(!SweetgramConfig.streamerHidesOthers());
        } else if (item.id == ID_USERNAME) {
            SweetgramConfig.setStreamerHidesUsername(!SweetgramConfig.streamerHidesUsername());
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
