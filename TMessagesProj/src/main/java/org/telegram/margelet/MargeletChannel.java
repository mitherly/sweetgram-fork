package org.telegram.margelet;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.DialogObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Канал форка первой строкой в списке чатов.
 *
 * Подписки он не требует: строка появляется, даже если человек в канале не
 * состоит, — нажатие открывает его обычным предпросмотром с кнопкой
 * «подписаться». Выключается в настройках форка, по умолчанию включён.
 *
 * Настоящий список чатов при этом не трогается: строка добавляется в копию,
 * которую видит только список. Влезать в хранилище телеграма ради украшения
 * нельзя — там лежит переписка, а не наше место под баннер.
 */
public class MargeletChannel {

    /**
     * Номер канала в том виде, в каком его понимает список чатов.
     *
     * Здесь я ошибся ровно один раз и заметно: написал «минус триллион минус
     * номер», как принято в ботовом интерфейсе телеграма. В самом приложении
     * не так — номер переписки канала это просто минус номер канала
     * (DialogObject.getPeerDialogId). Из-за этого моя строка не совпадала с
     * настоящей перепиской, и у подписчиков канал оказывался в списке дважды.
     */
    public static final long CHANNEL_ID = 4426743212L;
    public static final long DIALOG_ID = -CHANNEL_ID;

    private static TLRPC.TL_dialog own;
    private static long askedAt;

    /**
     * Список чатов с каналом форка первой строкой.
     *
     * Возвращает тот же список, если добавлять нечего: лишняя копия на каждой
     * перерисовке списка чатов — не то место, где стоит сорить.
     */
    public static ArrayList<TLRPC.Dialog> onTop(int account, ArrayList<TLRPC.Dialog> array,
                                                int dialogsType, int folderId) {
        if (array == null || dialogsType != 0 || folderId != 0 || !MargeletConfig.channelOnTop()) {
            return array;
        }
        TLRPC.Dialog existing = null;
        for (TLRPC.Dialog dialog : array) {
            if (dialog != null && dialog.id == DIALOG_ID) {
                existing = dialog;
                break;
            }
        }
        if (existing == null && !load(account)) {
            return array;   // канал ещё не загружен — рисовать нечего
        }
        final TLRPC.Dialog channel = existing != null ? existing : own;
        // Архив — это папка (TL_dialogFolder) в самом верху списка, и телеграм
        // прячет её при прокрутке только пока она первая. Поэтому канал ставим
        // сразу ПОД ведущими папками, а не выше них, иначе архив не сворачивается.
        final ArrayList<TLRPC.Dialog> out = new ArrayList<>(array.size() + 1);
        boolean placed = false;
        for (TLRPC.Dialog dialog : array) {
            if (dialog == existing) {
                continue;   // старую позицию канала убираем
            }
            if (!placed && (dialog == null || !DialogObject.isFolderDialogId(dialog.id))) {
                out.add(channel);
                placed = true;
            }
            out.add(dialog);
        }
        if (!placed) {
            out.add(channel);   // список пуст или содержит только архив
        }
        return out;
    }

    /**
     * Готовит строку канала. Пока канал не подгружен, строке неоткуда взять
     * ни имени, ни снимка, поэтому её просто нет — пустая серая полоска в
     * начале списка выглядела бы поломкой.
     */
    private static boolean load(int account) {
        final MessagesController controller = MessagesController.getInstance(account);
        if (controller.getChat(CHANNEL_ID) == null) {
            // Спрашиваем не один раз навсегда, а не чаще раза в десять секунд.
            //
            // Первая версия спрашивала однажды — и ломалась ровно там, ради
            // чего всё затевалось: пока человек подписан, канал и так в
            // памяти, а стоит выйти — он оттуда пропадает, и единственная
            // попытка к тому моменту давно потрачена. Строка не появлялась
            // именно у тех, кому она нужна.
            final long now = android.os.SystemClock.elapsedRealtime();
            if (now - askedAt > 10_000L) {
                askedAt = now;
                controller.getUserNameResolver().resolve("margeletter", id -> {
                    // Ответ сам по себе не нужен: важно, что канал теперь в
                    // памяти. Но список уже нарисован без него, и сам себя он
                    // не пересоберёт — просим перерисовать.
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                            org.telegram.messenger.NotificationCenter.getInstance(account)
                                    .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload));
                });
            }
            return false;
        }
        if (own == null) {
            own = new TLRPC.TL_dialog();
            own.id = DIALOG_ID;
            own.folder_id = 0;
        }
        return true;
    }
}
