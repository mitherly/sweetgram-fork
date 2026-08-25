package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.margelet.MargeletUpdate;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Обновления»: как часто спрашивать про новую версию и кнопка спросить
 * прямо сейчас.
 *
 * Раньше проверка была ровно одна — при запуске приложения. Для того, кто
 * держит телеграм открытым сутками, это значило «никогда».
 */
public class MargeletUpdatesActivity extends UniversalFragment {

    /** Кнопка и заголовок не должны попасть в номера значений интервала. */
    private static final int ID_CHECK_NOW = 1000;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletUpdates);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    /** Название интервала: «3 минуты», «6 часов», «Никогда». */
    private static String name(int minutes) {
        if (minutes <= 0) {
            return LocaleController.getString(R.string.MargeletUpdatesOff);
        }
        if (minutes < 60) {
            return LocaleController.formatPluralString("Minutes", minutes);
        }
        return LocaleController.formatPluralString("Hours", minutes / 60);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.MargeletUpdatesHeader)));
        final int chosen = MargeletConfig.updateIntervalMinutes();
        for (int i = 0; i < MargeletConfig.UPDATE_INTERVALS.length; i++) {
            final int minutes = MargeletConfig.UPDATE_INTERVALS[i];
            items.add(UItem.asRadio(i, name(minutes)).setChecked(minutes == chosen));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletUpdatesAbout)));
        items.add(UItem.asButton(ID_CHECK_NOW, LocaleController.getString(R.string.MargeletUpdatesNow)));
        // Показываем и свой номер, и версию телеграма, на которой собрано:
        // без второго числа непонятно, из какого исходника выросла сборка.
        items.add(UItem.asShadow(LocaleController.formatString(R.string.MargeletUpdatesCurrent,
                MargeletConfig.APP_VERSION)
                + "\n"
                + LocaleController.formatString(R.string.MargeletUpdatesBased,
                        BuildVars.BUILD_VERSION_STRING)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CHECK_NOW) {
            checkNow();
            return;
        }
        if (item.id >= 0 && item.id < MargeletConfig.UPDATE_INTERVALS.length) {
            MargeletConfig.setUpdateIntervalMinutes(MargeletConfig.UPDATE_INTERVALS[item.id]);
            // Расписание переставляется сразу: иначе новое значение начало бы
            // действовать только после перезапуска приложения.
            MargeletUpdate.schedule();
            listView.adapter.update(true);
        }
    }

    /**
     * Проверка по кнопке работает и при выключенныхавтоматических проверках: человек
     * попросил — значит спрашиваем.
     */
    private void checkNow() {
        // Проверяем разом и версию, и значки: для человека это одно действие.
        MargeletUpdate.checkAll(() -> {
            if (getContext() == null) {
                return;
            }
            final MargeletUpdate.Info info = MargeletUpdate.available();
            if (info != null) {
                NotificationCenter.getGlobalInstance()
                        .postNotificationName(NotificationCenter.appUpdateAvailable);
                offer(info);
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip,
                        LocaleController.getString(R.string.MargeletUpdatesLatest)).show();
            }
        });
    }

    /**
     * Окно с найденной версией и кнопкой, которая её ставит.
     *
     * Раньше здесь была подсказка внизу экрана: «вышла версия новее» — и всё.
     * Человек нажал «проверить», узнал, что обновление есть, и остался с этим
     * знанием наедине: ни скачать, ни поставить отсюда было нельзя. Сообщать
     * о работе, которую сам же и не даёшь сделать, — худший вид вежливости.
     */
    private void offer(MargeletUpdate.Info info) {
        final StringBuilder text = new StringBuilder();
        final String about = info.about();
        if (about != null && about.length() > 0) {
            text.append(about);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.formatString(R.string.MargeletUpdatesFound, info.version))
                .setMessage(text.length() > 0 ? text.toString()
                        : LocaleController.getString(R.string.MargeletUpdatesAbout))
                .setNegativeButton(LocaleController.getString(R.string.MargeletLater), null);
        if (MargeletUpdate.downloaded() != null) {
            // Уже скачано — второй раз качать незачем.
            builder.setPositiveButton(LocaleController.getString(R.string.MargeletUpdatesInstall),
                    (d, w) -> MargeletUpdate.install(getParentActivity()));
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.MargeletUpdatesDownload),
                    (d, w) -> startDownload());
        }
        builder.show();
    }

    /**
     * Качает и показывает, сколько уже. Без этого длинная закачка выглядит
     * так же, как ничего не происходящее нажатие.
     */
    private void startDownload() {
        final AlertDialog progress = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_LOADING);
        progress.setCanCancel(true);
        progress.setOnCancelListener(d -> MargeletUpdate.cancel());
        progress.show();
        MargeletUpdate.download(() -> {
            if (MargeletUpdate.downloading()) {
                return;
            }
            progress.dismiss();
            if (getContext() == null) {
                return;
            }
            if (MargeletUpdate.downloaded() != null) {
                MargeletUpdate.install(getParentActivity());
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                        LocaleController.getString(R.string.MargeletUpdatesFailed)).show();
            }
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
