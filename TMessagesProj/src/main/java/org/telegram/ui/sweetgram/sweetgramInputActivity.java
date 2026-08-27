package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Поле ввода»: сколько строк, какой размер текста, где оно стоит. */
public class SweetgramInputActivity extends UniversalFragment {

    /** Значения ползунка строк. Ноль — «сколько влезет на экран». */
    private static final int[] LINES = {2, 3, 4, 5, 6, 8, 10, 15, 0};
    private static final int ID_TOP = 1;

    private static final int[] SIZES = {14, 15, 16, 17, 18, 19, 20, 22, 24};

    private static int indexOf(int[] arr, int value, int fallback) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return fallback;
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramInput);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.SweetgramLines)));
        String[] lines = new String[LINES.length];
        for (int i = 0; i < LINES.length; i++) {
            lines[i] = LINES[i] == 0 ? LocaleController.getString(R.string.SweetgramLinesMax) : String.valueOf(LINES[i]);
        }
        items.add(UItem.asSlideView(lines, indexOf(LINES, SweetgramConfig.inputMaxLinesRaw(), 4),
                i -> SweetgramConfig.setInputMaxLines(LINES[i])));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramLinesAbout)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.SweetgramTextSize)));
        String[] sizes = new String[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            sizes[i] = String.valueOf(SIZES[i]);
        }
        items.add(UItem.asSlideView(sizes, indexOf(SIZES, Math.round(SweetgramConfig.inputTextSize()), 4),
                i -> SweetgramConfig.setInputTextSize(SIZES[i])));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramNextChat)));

        items.add(UItem.asCheck(ID_TOP, LocaleController.getString(R.string.SweetgramInputOnTop)).setChecked(SweetgramConfig.inputOnTop()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramInputOnTopAbout)));
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        // Скруглённые карточки — так выглядят нынешние экраны настроек.
        // Без этой строки список рисуется сплошной лентой, как в прошлой
        // версии приложения: владелец это заметил сразу.
        listView.setSections();
        return view;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_TOP) {
            SweetgramConfig.setInputOnTop(!SweetgramConfig.inputOnTop());
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
