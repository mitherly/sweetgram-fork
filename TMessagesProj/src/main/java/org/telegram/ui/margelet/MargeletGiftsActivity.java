package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.margelet.MargeletGifts;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Удалённые подарки». */
public class MargeletGiftsActivity extends UniversalFragment {

    private static final int ID_ON = 1;
    private static final int ID_AUTHOR = 2;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletGifts);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_ON, LocaleController.getString(R.string.MargeletGiftsOn))
                .setChecked(MargeletConfig.giftsEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletGiftsAbout)));
        // Отметка автора приёма. Стоит отдельной строкой и нажимается: спрятать
        // её в мелкий текст было бы дешёвым способом сделать вид, что придумали
        // сами.
        items.add(UItem.asButton(ID_AUTHOR, LocaleController.getString(R.string.MargeletGiftsAuthor)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ON) {
            MargeletConfig.setGiftsEnabled(!MargeletConfig.giftsEnabled());
            if (MargeletConfig.giftsEnabled()) {
                MargeletGifts.load(getCurrentAccount());
            }
            listView.adapter.update(true);
        } else if (item.id == ID_AUTHOR) {
            Browser.openUrl(getContext(), "https://t.me/" + MargeletGifts.AUTHOR);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
