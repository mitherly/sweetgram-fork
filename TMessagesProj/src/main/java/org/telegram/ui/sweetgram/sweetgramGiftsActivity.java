package org.telegram.ui;

import android.view.View;

import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.sweetgram.SweetgramGifts;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Удалённые подарки». */
public class SweetgramGiftsActivity extends UniversalFragment {

    private static final int ID_ON = 1;
    private static final int ID_AUTHOR = 2;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramGifts);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_ON, LocaleController.getString(R.string.SweetgramGiftsOn))
                .setChecked(SweetgramConfig.giftsEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramGiftsAbout)));
        // Отметка автора приёма. Стоит отдельной строкой и нажимается: спрятать
        // её в мелкий текст было бы дешёвым способом сделать вид, что придумали
        // сами.
        items.add(UItem.asButton(ID_AUTHOR, LocaleController.getString(R.string.SweetgramGiftsAuthor)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ON) {
            SweetgramConfig.setGiftsEnabled(!SweetgramConfig.giftsEnabled());
            if (SweetgramConfig.giftsEnabled()) {
                SweetgramGifts.load(getCurrentAccount());
            }
            listView.adapter.update(true);
        } else if (item.id == ID_AUTHOR) {
            Browser.openUrl(getContext(), "https://t.me/" + SweetgramGifts.AUTHOR);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
