package org.telegram.ui;

import android.view.View;

import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Разметка значками: какие сочетания превращать в оформление при отправке.
 *
 * Выключенный вид остаётся в тексте как есть — звёздочки уедут звёздочками.
 * Подчёркивание и цитаты придуманы форком: в телеграме значков для них нет,
 * поэтому у собеседника без форка они и останутся плюсами и «больше».
 */
public class SweetgramMarkdownActivity extends UniversalFragment {

    private static final String[] KINDS = {
            "bold", "italic", "underline", "strike", "spoiler", "code", "quote", "quote_collapsed"
    };
    private static final int[] TITLES = {
            R.string.Bold, R.string.Italic, R.string.Underline, R.string.Strike,
            R.string.Spoiler, R.string.Mono, R.string.SweetgramMarkdownQuote,
            R.string.SweetgramMarkdownQuoteCollapsed
    };
    private static final String[] SAMPLES = {
            "**…**", "__…__", "++…++", "~~…~~", "||…||", "`…`", ">…", ">>…"
    };

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramMarkdown);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        for (int i = 0; i < KINDS.length; i++) {
            items.add(UItem.asCheck(i, LocaleController.getString(TITLES[i]) + "   " + SAMPLES[i])
                    .setChecked(SweetgramConfig.markdownEnabled(KINDS[i])));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramMarkdownAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= 0 && item.id < KINDS.length) {
            SweetgramConfig.setMarkdownEnabled(KINDS[item.id],
                    !SweetgramConfig.markdownEnabled(KINDS[item.id]));
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
