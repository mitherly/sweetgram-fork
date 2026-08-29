package org.telegram.sweetgram;

import android.graphics.Bitmap;
import android.net.Uri;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Баннер профиля — картинка за аватаркой.
 *
 * Лежит не у нас, а в общей группе: человек отправляет туда фотографию с
 * меткой, и она становится его баннером. Чей баннер — видно по тому, кто
 * отправил, а подпись под сообщением ставит телеграм. Подделать нельзя, и
 * проверять нам нечего.
 *
 * Сменить баннер — это отправить новый и удалить старый. Удалить — просто
 * удалить своё сообщение. Обе вещи человек может сделать и руками, прямо в
 * группе, без нашего приложения: мы не владеем его баннером, мы его только
 * показываем.
 *
 * Порт из Margy (Margelet); хранилище то же самое — класс
 * {@link SweetgramBannerGroup}.
 */
public class SweetgramBanner {

    /** Найденное держим в памяти: профиль перерисовывается часто, баннер редко. */
    private static final HashMap<Long, Bitmap> pictures = new HashMap<>();
    private static final HashMap<Long, Integer> ownMessage = new HashMap<>();
    private static final Set<Long> looking = new HashSet<>();
    private static final Set<Long> missing = new HashSet<>();

    private static long me() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Баннер этого человека, если он уже у нас есть.
     *
     * Зовётся из отрисовки, поэтому ничего не ждёт и ничего не качает: нет —
     * рисуем как раньше, а картинку тем временем принесут и позовут
     * {@code whenReady}.
     */
    public static Bitmap of(long userId, Runnable whenReady) {
        if (userId <= 0 || !SweetgramConfig.bannersEnabled()) {
            return null;
        }
        synchronized (pictures) {
            final Bitmap ready = pictures.get(userId);
            if (ready != null) {
                return ready;
            }
            if (missing.contains(userId) || looking.contains(userId)) {
                return null;
            }
            looking.add(userId);
        }
        // Спрашиваем сразу про одного человека: метка у баннеров одна на всех,
        // и без этого пришлось бы тащить полсотни чужих ради одного своего.
        // Ответ приходит дважды — от поиска и от истории, — а баннер нужен
        // один. Берём первый пришедший с картинкой и второй уже не слушаем.
        final boolean[] handled = new boolean[1];
        SweetgramBannerGroup.find(SweetgramBannerGroup.TAG_BANNER, userId, 20, (messages, problem) -> {
            if (handled[0]) {
                return;
            }
            if (problem != null) {
                FileLog.e("sweetgram: баннер не искался: " + problem);
                synchronized (pictures) {
                    looking.remove(userId);
                }
                return;     // не «нет баннера», а «не спросили» — спросим ещё
            }
            MessageObject mine = null;
            for (MessageObject message : messages) {
                if (message.getDocument() == null) {
                    mine = message;
                    break;      // новое сверху, первое совпадение и есть свежее
                }
            }
            if (mine == null) {
                synchronized (pictures) {
                    looking.remove(userId);
                    missing.add(userId);
                }
                return;
            }
            handled[0] = true;
            if (userId == me()) {
                ownMessage.put(userId, mine.getId());
            }
            load(userId, mine, whenReady);
        });
        return null;
    }

    /**
     * Забирает саму картинку. Файл может быть уже скачан — телеграм хранит
     * скачанное у себя, и второй раз в сеть за ним не пойдёт.
     */
    private static void load(long userId, MessageObject message, Runnable whenReady) {
        try {
            final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(
                    message.photoThumbs, 1280, false, null, true);
            if (size == null) {
                synchronized (pictures) {
                    looking.remove(userId);
                    missing.add(userId);
                }
                return;
            }
            final File file = FileLoader.getInstance(UserConfig.selectedAccount)
                    .getPathToAttach(size, true);
            if (file != null && file.exists()) {
                decode(userId, file, whenReady);
                return;
            }
            // Дожидаемся именно окончания загрузки, а не «двух с половиной
            // секунд»: не успел скачаться — баннер бросали до следующего
            // открытия профиля, и выглядело это так, будто баннеров нет вовсе.
            waitFor(userId, size, message, whenReady);
        } catch (Throwable t) {
            FileLog.e(t);
            synchronized (pictures) {
                looking.remove(userId);
                missing.add(userId);
            }
        }
    }

    /**
     * Подписывается на окончание загрузки этого файла и уходит, как только оно
     * случилось. Отписываемся сразу же: подписка, которую забыли снять,
     * переживёт и профиль, и сам баннер.
     */
    private static void waitFor(long userId, TLRPC.PhotoSize size, MessageObject message,
                                Runnable whenReady) {
        final String name = FileLoader.getAttachFileName(size);
        final NotificationCenter center = NotificationCenter.getInstance(UserConfig.selectedAccount);
        final NotificationCenter.NotificationCenterDelegate[] holder =
                new NotificationCenter.NotificationCenterDelegate[1];
        holder[0] = (id, account, args) -> {
            if (args.length == 0 || !name.equals(args[0])) {
                return;
            }
            center.removeObserver(holder[0], NotificationCenter.fileLoaded);
            center.removeObserver(holder[0], NotificationCenter.fileLoadFailed);
            if (id == NotificationCenter.fileLoadFailed) {
                synchronized (pictures) {
                    looking.remove(userId);
                }
                return;     // не «нет баннера», а «не скачался» — попробуем ещё
            }
            final File ready = FileLoader.getInstance(UserConfig.selectedAccount)
                    .getPathToAttach(size, true);
            if (ready != null && ready.exists()) {
                decode(userId, ready, whenReady);
            } else {
                synchronized (pictures) {
                    looking.remove(userId);
                }
            }
        };
        center.addObserver(holder[0], NotificationCenter.fileLoaded);
        center.addObserver(holder[0], NotificationCenter.fileLoadFailed);
        FileLoader.getInstance(UserConfig.selectedAccount).loadFile(
                ImageLocation.getForObject(size, message.photoThumbsObject), message,
                null, FileLoader.PRIORITY_NORMAL, FileLoader.PRELOAD_CACHE_TYPE);
    }

    private static void decode(long userId, File file, Runnable whenReady) {
        Bitmap bitmap = null;
        try {
            final android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            // Баннер рисуется полосой в пару сотен точек высотой. Держать в
            // памяти полноразмерную фотографию ради этого незачем.
            options.inSampleSize = 2;
            bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        synchronized (pictures) {
            looking.remove(userId);
            if (bitmap != null) {
                pictures.put(userId, bitmap);
            } else {
                missing.add(userId);
            }
        }
        if (bitmap != null && whenReady != null) {
            AndroidUtilities.runOnUIThread(whenReady);
        }
    }

    /** Показать только что выбранную картинку, не дожидаясь сервера. */
    private static void preview(long userId, Uri image) {
        try (java.io.InputStream in = org.telegram.messenger.ApplicationLoader
                .applicationContext.getContentResolver().openInputStream(image)) {
            if (in == null) {
                return;
            }
            final android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            options.inSampleSize = 2;
            final Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in, null, options);
            if (bitmap == null) {
                return;
            }
            synchronized (pictures) {
                pictures.put(userId, bitmap);
                missing.remove(userId);
                looking.remove(userId);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Забыть скачанное: свой баннер поменяли — старый показывать нельзя. */
    public static void forget(long userId) {
        synchronized (pictures) {
            pictures.remove(userId);
            missing.remove(userId);
            looking.remove(userId);
        }
    }

    /**
     * Поставить себе баннер: отправить картинку в группу с меткой, а прошлую
     * убрать.
     *
     * Порядок именно такой — сначала отправляем, потом удаляем старое. Наоборот
     * было бы хуже: если отправка не пройдёт, человек останется вообще без
     * баннера, хотя ничего не просил удалять.
     */
    public static void set(Uri image, Runnable done) {
        final long id = me();
        if (id <= 0 || image == null) {
            if (done != null) {
                done.run();
            }
            return;
        }
        SweetgramBannerGroup.resolve(dialogId -> {
            if (dialogId == 0) {
                if (done != null) {
                    done.run();
                }
                return;
            }
            final Integer old = ownMessage.get(id);
            try {
                SendMessagesHelper.prepareSendingPhoto(AccountInstance.getInstance(UserConfig.selectedAccount),
                        null, image, dialogId, null, null, null,
                        SweetgramBannerGroup.TAG_BANNER, null, null, null, 0,
                        null, true, 0, 0, null);
            } catch (Throwable t) {
                FileLog.e(t);
            }
            forget(id);
            // Свою картинку показываем немедленно, как это делает и сам
            // телеграм с отправленным фото: ждать, пока сервер её разберёт и
            // вернёт поиском, человеку незачем — он её только что выбрал.
            preview(id, image);
            if (old != null) {
                // Старое убираем с задержкой: пусть новое сперва уйдёт.
                AndroidUtilities.runOnUIThread(() -> SweetgramBannerGroup.remove(old), 4000);
                ownMessage.remove(id);
            }
            if (done != null) {
                done.run();
            }
        });
    }

    /** Чем кончилось удаление. */
    public interface Removed {
        /**
         * @param what {@code REMOVED} — убрали, {@code NOTHING} — нечего было
         *             убирать, {@code FAILED} — не смогли спросить группу.
         */
        void onRemoved(int what);
    }

    public static final int REMOVED = 1;
    public static final int NOTHING = 2;
    public static final int FAILED = 3;

    /**
     * Убрать свой баннер — то есть удалить своё сообщение из группы.
     *
     * Ответ честный: «убрали», «нечего было убирать» или «не достучались».
     * Всегда одинаковая «баннер удалён» не сообщает ничего.
     */
    public static void clear(Removed done) {
        final long id = me();
        if (id <= 0) {
            answer(done, FAILED);
            return;
        }
        final Integer known = ownMessage.get(id);
        if (known != null) {
            SweetgramBannerGroup.remove(known);
            ownMessage.remove(id);
            forget(id);
            answer(done, REMOVED);
            return;
        }
        // Номера сообщения не знаем — найдём и удалим. Ответ даём после
        // поиска, а не до: до него мы попросту не знаем, что сказать.
        SweetgramBannerGroup.find(SweetgramBannerGroup.TAG_BANNER, id, 20, (messages, problem) -> {
            if (problem != null) {
                answer(done, FAILED);
                return;
            }
            int removed = 0;
            for (MessageObject message : messages) {
                SweetgramBannerGroup.remove(message.getId());
                removed++;
            }
            forget(id);
            answer(done, removed > 0 ? REMOVED : NOTHING);
        });
    }

    private static void answer(Removed done, int what) {
        if (done != null) {
            AndroidUtilities.runOnUIThread(() -> done.onRemoved(what));
        }
    }
}
