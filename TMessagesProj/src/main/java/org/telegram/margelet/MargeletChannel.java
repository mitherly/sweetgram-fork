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
     * Юзернейм нашего канала. Номер канал получает сам, через резолв имени:
     * так не нужно вшивать цифры, которые легко перепутать.
     */
    public static final String CHANNEL_USERNAME = "SweetGramOfficial";

    /** Номер канала после резолва юзернейма, до первого ответа — ноль. */
    private static volatile long channelId;
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
        final long id = channelId;
        TLRPC.Dialog existing = null;
        if (id != 0) {
            final long dialogId = -id;
            for (TLRPC.Dialog dialog : array) {
                if (dialog != null && dialog.id == dialogId) {
                    existing = dialog;
                    break;
                }
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
        if (channelId == 0 || controller.getChat(channelId) == null) {
            // Спрашиваем не один раз навсегда, а не чаще раза в десять секунд.
            final long now = android.os.SystemClock.elapsedRealtime();
            if (now - askedAt > 10_000L) {
                askedAt = now;
                controller.getUserNameResolver().resolve(CHANNEL_USERNAME, id -> {
                    // Запоминаем номер: без него строку не с чем сравнивать,
                    // если человек уже подписан и переписка есть в списке.
                    if (id != 0) {
                        channelId = id;
                    }
                    // Список уже нарисован без канала, сам себя он не
                    // пересоберёт — просим перерисовать.
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                            org.telegram.messenger.NotificationCenter.getInstance(account)
                                    .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload));
                });
            }
            return false;
        }
        if (own == null || own.id != -channelId) {
            own = new TLRPC.TL_dialog();
            own.id = -channelId;
            own.folder_id = 0;
        }
        return true;
    }
}
