package org.telegram.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;

import org.telegram.sweetgram.SweetgramFonts;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Ветка «Шрифт»: библиотека шрифтов и свои файлы.
 *
 * Смена шрифта требует перезапуска, и это не лень: шрифты уже разошлись по
 * кэшам и по нарисованным экранам. Поэтому здесь сначала спрашивают, а потом
 * приложение поднимается заново — с уже выбранным шрифтом.
 */
public class SweetgramFontsActivity extends UniversalFragment {

    private static final int ID_DEFAULT = 900;
    private static final int ID_ADD = 901;
    private static final int PICK_FONT = 4802;

    /** Список на экране: по нему же находим шрифт по номеру строки. */
    private List<SweetgramFonts.Font> fonts = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramFonts);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        fonts = SweetgramFonts.list();
        final String chosen = SweetgramFonts.chosen();
        items.add(UItem.asHeader(LocaleController.getString(R.string.SweetgramFontsHeader)));
        items.add(UItem.asRadio(ID_DEFAULT, LocaleController.getString(R.string.SweetgramFontsDefault))
                .setChecked(chosen == null || chosen.isEmpty()));
        for (int i = 0; i < fonts.size(); i++) {
            final SweetgramFonts.Font font = fonts.get(i);
            items.add(UItem.asRadio(i, font.name).setChecked(font.id.equals(chosen)));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramFontsAbout)));
        items.add(UItem.asButton(ID_ADD, LocaleController.getString(R.string.SweetgramFontsAdd)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramFontsAddAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            pick();
            return;
        }
        if (item.id == ID_DEFAULT) {
            confirm(SweetgramFonts.DEFAULT);
            return;
        }
        if (item.id >= 0 && item.id < fonts.size()) {
            confirm(fonts.get(item.id).id);
        }
    }

    /** Долгое нажатие по своему шрифту — убрать его из библиотеки. */
    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 0 || item.id >= fonts.size()) {
            return false;
        }
        final SweetgramFonts.Font font = fonts.get(item.id);
        if (!font.own) {
            return false;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(font.name)
                .setMessage(LocaleController.getString(R.string.SweetgramFontsRemoveAbout))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    SweetgramFonts.remove(font);
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
        return true;
    }

    private void pick() {
        // Шрифты приходят с самыми разными типами, а бывает, что и без типа.
        // Поэтому спрашиваем любой файл, а годится он или нет — проверяем сами
        // после копирования.
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, PICK_FONT);
        } catch (Exception ignored) {
            // Не на каждом телефоне есть чем открыть выбор файла.
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FONT || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final String installed = SweetgramFonts.install(uri, name(uri));
        if (installed == null) {
            BulletinFactory.of(this).createErrorBulletin(
                    LocaleController.getString(R.string.SweetgramFontsBad)).show();
            return;
        }
        listView.adapter.update(true);
        confirm(installed);
    }

    /** Как файл называется у человека — чтобы в списке было узнаваемо. */
    private String name(Uri uri) {
        try (Cursor cursor = ApplicationLoader.applicationContext.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    return cursor.getString(column);
                }
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    /** Выбор вступает в силу только после перезапуска, и об этом спрашивают. */
    private void confirm(String id) {
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.SweetgramFonts))
                .setMessage(LocaleController.getString(R.string.SweetgramFontsRestart))
                .setPositiveButton(LocaleController.getString(R.string.SweetgramFontsRestartNow), (d, w) -> {
                    SweetgramFonts.choose(id);
                    SweetgramFonts.restart(getParentActivity());
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }
}
