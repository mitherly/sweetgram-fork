package org.telegram.ui;

import android.view.View;

import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.sweetgram.SweetgramGhost;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Ghost-режим»: быть в чате так, будто тебя там нет.
 *
 * Два переключателя не связаны между собой: можно прятать только печатание
 * или только отметку прочтения. Отложенные отметки, накопившиеся к моменту,
 * пока режим выключили, доигрываются сразу — иначе выключение выглядело бы
 * как «прочитано задним числом», а это и есть то, чего человек не хотел.
 */
public class SweetgramGhostActivity extends UniversalFragment {

    private static final int ID_TYPING = 1;
    private static final int ID_READ = 2;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramGhost);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_TYPING, LocaleController.getString(R.string.SweetgramGhostTyping))
                .setChecked(SweetgramConfig.ghostTyping()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramGhostTypingAbout)));
        items.add(UItem.asCheck(ID_READ, LocaleController.getString(R.string.SweetgramGhostRead))
                .setChecked(SweetgramConfig.ghostRead()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramGhostReadAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_TYPING) {
            SweetgramConfig.setGhostTyping(!SweetgramConfig.ghostTyping());
        } else if (item.id == ID_READ) {
            final boolean on = !SweetgramConfig.ghostRead();
            SweetgramConfig.setGhostRead(on);
            if (!on) {
                SweetgramGhost.flushAll();
            }
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
