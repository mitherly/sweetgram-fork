package org.telegram.sweetgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
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
public class SweetgramChannel {

    /** Юзернейм канала форка, который держим первой строкой списка чатов. */
    public static final String CHANNEL_USERNAME = "SweetGramOfficial";

    /**
     * Номер переписки канала. Узнаём его сами через резолв юзернейма, а не
     * хардкодим: так не ошибёшься с форматом id (для канала/чата это минус
     * номер, DialogObject.getPeerDialogId) и не привяжешься к чужому каналу.
     */
    private static TLRPC.TL_dialog own;
    private static long resolvedId;
    private static long askedAt;

    /**
     * Список чатов с каналом форка первой строкой.
     *
     * Возвращает тот же список, если добавлять нечего: лишняя копия на каждой
     * перерисовке списка чатов — не то место, где стоит сорить.
     */
    public static ArrayList<TLRPC.Dialog> onTop(int account, ArrayList<TLRPC.Dialog> array,
                                                int dialogsType, int folderId) {
        if (array == null || dialogsType != 0 || folderId != 0 || !SweetgramConfig.channelOnTop()) {
            return array;
        }
        final long dialogId = own != null ? own.id : 0;
        TLRPC.Dialog existing = null;
        if (dialogId != 0) {
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
        if (resolvedId != 0) {
            final MessagesController controller = MessagesController.getInstance(account);
            final boolean inMemory = resolvedId > 0
                    ? controller.getUser(resolvedId) != null
                    : controller.getChat(-resolvedId) != null;
            if (inMemory) {
                if (own == null) {
                    own = new TLRPC.TL_dialog();
                    own.folder_id = 0;
                    own.pinned = true;
                    own.pinnedNum = Integer.MAX_VALUE;
                    own.notify_settings = new TLRPC.TL_peerNotifySettings();
                }
                own.id = resolvedId;
                if (resolvedId > 0) {
                    own.peer = new TLRPC.TL_peerUser();
                    own.peer.user_id = resolvedId;
                } else {
                    own.peer = new TLRPC.TL_peerChannel();
                    own.peer.channel_id = -resolvedId;
                }
                return true;
            }
            // Память очистилась (человек отписался) — забываем id и переспросим.
            resolvedId = 0;
            own = null;
        }
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
            MessagesController.getInstance(account).getUserNameResolver().resolve(CHANNEL_USERNAME, id -> {
                // Ответ сам по себе не нужен: важно, что канал теперь в
                // памяти. Но список уже нарисован без него, и сам себя он
                // не пересоберёт — просим перерисовать.
                if (id != null && id != Long.MAX_VALUE) {
                    resolvedId = id;
                }
                AndroidUtilities.runOnUIThread(() ->
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload));
            });
        }
        return false;
    }
}
