package org.telegram.sweetgram;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/**
 * Общая кладовая баннеров: публичная группа вместо своего сервера.
 *
 * Своего сервера у форка нет, и баннер, который показывается всем, надо где-то
 * хранить так, чтобы никто не мог положить чужое имя. Группа решает это сама:
 * сообщение уже подписано телеграмом — пришло от аккаунта, значит от него.
 * Подделать нельзя, проверять нечего.
 *
 * Порт из Margy (Margelet): группа и метка те же, поэтому баннеры, поставленные
 * там, видны здесь и наоборот.
 */
public class SweetgramBannerGroup {

    /** Где всё лежит. Публичная группа, читать может кто угодно. */
    public static final String USERNAME = "sweetgrambanners";

    /** Метка баннера. Одна на всех: чей баннер — видно по автору сообщения. */
    public static final String TAG_BANNER = "#sweetgram_banner";

    private static long groupId;
    private static boolean resolving;

    /** Адрес группы между запусками: имя в адрес переводит сервер, и делать
     *  это при каждом открытии профиля — лишняя поездка. */
    private static final String KEY_GROUP = "sweetgram_group_id";
    /** Кто ждёт адрес группы, пока он выясняется. */
    private static final List<Peer> waiting = new ArrayList<>();

    public interface Peer {
        void onPeer(long dialogId);
    }

    public interface Messages {
        /**
         * Найденное, новое сверху.
         *
         * {@code problem} — причина неудачи или null, если всё прошло. Пустой
         * список и неудача выглядят одинаково, если про причину не спросить,
         * а это ровно та разница, из-за которой «баннера нет» нельзя отличить
         * от «не спросили».
         */
        void onMessages(List<MessageObject> messages, String problem);
    }

    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                SweetgramConfig.PREFS, android.content.Context.MODE_PRIVATE);
    }

    private static AccountInstance account() {
        return AccountInstance.getInstance(UserConfig.selectedAccount);
    }

    /**
     * Находит группу по имени. Ответ приходит в главный поток.
     *
     * Найденное запоминаем: имя в адрес переводится запросом к серверу, и
     * делать его на каждый открытый профиль — то же самое, что опрашивать
     * впустую.
     */
    public static void resolve(Peer done) {
        if (groupId == 0) {
            groupId = prefs().getLong(KEY_GROUP, 0);
        }
        if (groupId != 0) {
            done.onPeer(groupId);
            return;
        }
        synchronized (waiting) {
            waiting.add(done);
            if (resolving) {
                // Уже выясняем. Тихий return здесь оставил бы второго
                // спросившего без ответа вовсе: ждут все, и ответ получают все.
                return;
            }
            resolving = true;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                account().getMessagesController().getUserNameResolver().resolve(USERNAME,
                        id -> answer(id == null ? 0 : id));
            } catch (Throwable t) {
                FileLog.e(t);
                answer(0);
            }
        });
    }

    /** Раздать выясненный адрес всем, кто его ждал. */
    private static void answer(long id) {
        final List<Peer> ready;
        synchronized (waiting) {
            resolving = false;
            if (id != 0) {
                groupId = id;
                prefs().edit().putLong(KEY_GROUP, id).apply();
            }
            ready = new ArrayList<>(waiting);
            waiting.clear();
        }
        for (Peer peer : ready) {
            try {
                peer.onPeer(id);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    /**
     * Ищет в группе сообщения с этой меткой, при желании — только от одного
     * человека.
     *
     * Ищет сервер, а не телефон: своими силами пришлось бы выкачать всю
     * группу. Но на сервер одного полагаться нельзя — его поиск разбивает
     * слова по подчёркиваниям, и найденное мы ещё и перепроверяем сами.
     *
     * {@code from} — чьи сообщения нужны, ноль значит чьи угодно. Для баннера
     * это важно: метка у баннеров одна на всех, и без этого пришлось бы тащить
     * чужие и отбирать свой.
     */
    public static void find(String tag, long from, int limit, Messages done) {
        resolve(dialogId -> {
            if (dialogId == 0) {
                done.onMessages(new ArrayList<>(), "группа не нашлась");
                return;
            }
            final MessagesController controller = account().getMessagesController();
            final TLRPC.InputPeer peer = controller.getInputPeer(dialogId);
            if (peer == null) {
                done.onMessages(new ArrayList<>(), "группа не открывается");
                return;
            }
            // Два запроса разом, а не один за другим.
            //
            // Поиск на сервере знает всю группу, но свежее сообщение попадает в
            // него не сразу. Чтение истории видит свежее мгновенно, но недалеко
            // вглубь. Порознь каждый способ даёт либо «нового не видно», либо
            // «старого не видно»; вместе они закрывают друг друга.
            final Wait wait = new Wait(2, done);
            search(peer, tag, from, limit, wait);
            history(peer, tag, from, limit, wait);
        });
    }

    /**
     * Складывает ответы двух запросов в один.
     *
     * Отдаём найденное, как только пришёл первый непустой ответ, — и потом ещё
     * раз, когда придёт второй, если он что-то добавил. Ждать оба ради полноты
     * значило бы ждать медленный там, где быстрый уже всё принёс.
     */
    private static class Wait {
        private final Messages done;
        private final List<MessageObject> all = new ArrayList<>();
        private final java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        private int left;
        private String problem;

        Wait(int count, Messages done) {
            this.left = count;
            this.done = done;
        }

        void add(List<MessageObject> found, String why) {
            left--;
            if (why != null) {
                problem = why;
            }
            boolean fresh = false;
            for (MessageObject message : found) {
                if (seen.add(message.getId())) {
                    all.add(message);
                    fresh = true;
                }
            }
            if (fresh) {
                // Новое сверху: номер сообщения растёт со временем.
                java.util.Collections.sort(all, (a, b) -> b.getId() - a.getId());
            }
            if (fresh || left == 0) {
                done.onMessages(new ArrayList<>(all), all.isEmpty() ? problem : null);
            }
        }
    }

    /** Поиск на сервере: знает всё, но свежее видит с задержкой. */
    private static void search(TLRPC.InputPeer peer, String tag, long from, int limit, Wait wait) {
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = peer;
        if (from != 0) {
            req.from_id = account().getMessagesController().getInputPeer(from);
        }
        req.q = tag;
        req.limit = limit;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        account().getConnectionsManager().sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (error != null) {
                        FileLog.e("sweetgram: поиск в группе не вышел: " + error.text);
                        wait.add(new ArrayList<>(), error.text);
                        return;
                    }
                    wait.add(collect(response, tag, from), null);
                }));
    }

    /** Свежая история: видит только что написанное, но недалеко вглубь. */
    private static void history(TLRPC.InputPeer peer, String tag, long from, int limit, Wait wait) {
        final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer;
        req.limit = Math.max(limit, 100);
        account().getConnectionsManager().sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (error != null) {
                        FileLog.e("sweetgram: история группы не пришла: " + error.text);
                        wait.add(new ArrayList<>(), error.text);
                        return;
                    }
                    wait.add(collect(response, tag, from), null);
                }));
    }

    /**
     * Отбирает из ответа то, что нам действительно подходит.
     *
     * Проверка метки здесь, а не только на сервере, потому что серверный поиск
     * приблизительный: на {@code #margy_banner} он охотно вернёт и сообщение,
     * где эти слова встретились случайно.
     */
    private static List<MessageObject> collect(TLObject response, String tag, long from) {
        final List<MessageObject> out = new ArrayList<>();
        if (!(response instanceof TLRPC.messages_Messages)) {
            return out;
        }
        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
        account().getMessagesController().putUsers(res.users, false);
        account().getMessagesController().putChats(res.chats, false);
        for (TLRPC.Message message : res.messages) {
            if (message == null || !hasTag(message, tag)) {
                continue;
            }
            final MessageObject object =
                    new MessageObject(UserConfig.selectedAccount, message, true, true);
            if (from != 0 && authorOf(object) != from) {
                continue;
            }
            out.add(object);
        }
        return out;
    }

    /**
     * Стоит ли в сообщении ровно эта метка.
     *
     * Ровно — значит следом не идёт ни буквы, ни цифры, ни подчёркивания:
     * иначе одна метка совпала бы с началом другой, похожей на неё.
     */
    private static boolean hasTag(TLRPC.Message message, String tag) {
        final String text = message.message;
        if (text == null) {
            return false;
        }
        int at = text.indexOf(tag);
        while (at >= 0) {
            final int end = at + tag.length();
            if (end >= text.length()) {
                return true;
            }
            final char next = text.charAt(end);
            if (!Character.isLetterOrDigit(next) && next != '_') {
                return true;
            }
            at = text.indexOf(tag, at + 1);
        }
        return false;
    }

    /** Убрать своё сообщение из группы. Чужие удалять нечем — и не надо. */
    public static void remove(int messageId) {
        resolve(dialogId -> {
            if (dialogId == 0 || messageId == 0) {
                return;
            }
            try {
                final ArrayList<Integer> ids = new ArrayList<>();
                ids.add(messageId);
                // Ноль в конце — обычный режим переписки, тот же, каким
                // телеграм удаляет сообщения из своего же экрана чата.
                account().getMessagesController().deleteMessages(ids, null, null, dialogId,
                        0, true, 0);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    /** Тот ли это человек, чьё сообщение мы смотрим. */
    public static long authorOf(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return 0;
        }
        try {
            return MessageObject.getFromChatId(message.messageOwner);
        } catch (Throwable t) {
            return 0;
        }
    }
}
