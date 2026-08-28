package org.telegram.sweetgram;

import android.util.LongSparseArray;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

/**
 * Ghost-режим: не отправлять «печатает» и откладывать отметку прочтения.
 *
 * Обе половины режима — это молчание: вместо перехвата того, что уже ушло,
 * мы не даём этому уйти. Печатание не отправляется вовсе, а прочитанное
 * остаётся непрочитанным ровно до тех пор, пока человек не выйдет из чата.
 *
 * Отложенная отметка живёт здесь, а не в MessagesController: базовый код
 * знает только одну точку (markDialogAsRead) — мы запоминаем последний
 * вызов по каждому чату и проигрываем его один раз, когда чат закрыт.
 * Проигрывание помечено флагом, иначе повторный вызов запомнил бы сам
 * себя, и отметка не поставилась бы никогда.
 */
public class SweetgramGhost {

    /** Последний отложенный вызов по каждому чату. */
    private static final LongSparseArray<Read> pending = new LongSparseArray<>();

    /** Пока идёт проигрывание, подавления нет: иначе оно подавит само себя. */
    private static volatile boolean replaying;

    private static final class Read {
        final int maxPositiveId;
        final int maxNegativeId;
        final int maxDate;
        final int countDiff;
        final int scheduledCount;
        final boolean popup;
        final long threadId;

        Read(int maxPositiveId, int maxNegativeId, int maxDate, boolean popup,
             long threadId, int countDiff, int scheduledCount) {
            this.maxPositiveId = maxPositiveId;
            this.maxNegativeId = maxNegativeId;
            this.maxDate = maxDate;
            this.popup = popup;
            this.threadId = threadId;
            this.countDiff = countDiff;
            this.scheduledCount = scheduledCount;
        }
    }

    /** Пустить ли статус «печатает»/«записывает». Пока режим включён — нет. */
    public static boolean hideTyping() {
        return SweetgramConfig.ghostTyping();
    }

    /**
     * Точка входа markDialogAsRead. true — отметку сейчас не делать: вызов
     * запомнен и будет проигран при выходе из чата. Если человек уже читал
     * дальше (id больше), запоминаем новый: проигрывать надо самое свежее.
     */
    public static synchronized boolean suppressRead(long dialogId, int maxPositiveId, int maxNegativeId,
                                                    int maxDate, boolean popup, long threadId,
                                                    int countDiff, int scheduledCount) {
        if (replaying || !SweetgramConfig.ghostRead()) {
            return false;
        }
        final Read prev = pending.get(dialogId);
        if (prev == null
                || maxPositiveId > prev.maxPositiveId
                || maxNegativeId > prev.maxNegativeId
                || maxDate > prev.maxDate) {
            pending.put(dialogId, new Read(maxPositiveId, maxNegativeId, maxDate, popup,
                    threadId, countDiff, scheduledCount));
        }
        return true;
    }

    /** Чат закрыт — прочитанное в нём становится прочитанным. */
    public static synchronized void onChatClosed(long dialogId) {
        if (dialogId == 0 || !SweetgramConfig.ghostRead()) {
            return;
        }
        flushDialog(dialogId);
    }

    /** Режим выключили — то, что копилось, надо доиграть. */
    public static synchronized void flushAll() {
        for (int i = pending.size() - 1; i >= 0; i--) {
            flushDialog(pending.keyAt(i));
        }
    }

    /** Проиграть один отложенный вызов. Явное «отметить прочитанным» тоже идёт сюда. */
    public static synchronized void flushDialog(long dialogId) {
        final Read read = pending.get(dialogId);
        if (read == null) {
            return;
        }
        pending.remove(dialogId);
        replaying = true;
        try {
            MessagesController.getInstance(UserConfig.selectedAccount).markDialogAsRead(
                    dialogId, read.maxPositiveId, read.maxNegativeId, read.maxDate,
                    read.popup, read.threadId, read.countDiff, true, read.scheduledCount);
        } finally {
            replaying = false;
        }
    }
}
