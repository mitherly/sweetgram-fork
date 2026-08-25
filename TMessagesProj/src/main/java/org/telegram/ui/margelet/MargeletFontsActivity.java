package org.telegram.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;

import org.telegram.margelet.MargeletFonts;
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
public class MargeletFontsActivity extends UniversalFragment {

    private static final int ID_DEFAULT = 900;
    private static final int ID_ADD = 901;
    private static final int PICK_FONT = 4802;

    /** Список на экране: по нему же находим шрифт по номеру строки. */
    private List<MargeletFonts.Font> fonts = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletFonts);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        fonts = MargeletFonts.list();
        final String chosen = MargeletFonts.chosen();
        items.add(UItem.asHeader(LocaleController.getString(R.string.MargeletFontsHeader)));
        items.add(UItem.asRadio(ID_DEFAULT, LocaleController.getString(R.string.MargeletFontsDefault))
                .setChecked(chosen == null || chosen.isEmpty()));
        for (int i = 0; i < fonts.size(); i++) {
            final MargeletFonts.Font font = fonts.get(i);
            items.add(UItem.asRadio(i, font.name).setChecked(font.id.equals(chosen)));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletFontsAbout)));
        items.add(UItem.asButton(ID_ADD, LocaleController.getString(R.string.MargeletFontsAdd)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletFontsAddAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            pick();
            return;
        }
        if (item.id == ID_DEFAULT) {
            confirm(MargeletFonts.DEFAULT);
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
        final MargeletFonts.Font font = fonts.get(item.id);
        if (!font.own) {
            return false;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(font.name)
                .setMessage(LocaleController.getString(R.string.MargeletFontsRemoveAbout))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    MargeletFonts.remove(font);
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
        final String installed = MargeletFonts.install(uri, name(uri));
        if (installed == null) {
            BulletinFactory.of(this).createErrorBulletin(
                    LocaleController.getString(R.string.MargeletFontsBad)).show();
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
                .setTitle(LocaleController.getString(R.string.MargeletFonts))
                .setMessage(LocaleController.getString(R.string.MargeletFontsRestart))
                .setPositiveButton(LocaleController.getString(R.string.MargeletFontsRestartNow), (d, w) -> {
                    MargeletFonts.choose(id);
                    MargeletFonts.restart(getParentActivity());
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }
}
