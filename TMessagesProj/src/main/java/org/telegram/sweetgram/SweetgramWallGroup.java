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
import java.util.Locale;

/**
 * Кладовая стены: публичная группа вместо своего сервера.
 *
 * Стена — это то, что о человеке написали другие. Писать о себе самому смысла
 * нет, значит хранить записи надо там, где авторство гарантирует телеграм, —
 * та же схема, на которой работают баннеры ({@link SweetgramBannerGroup}).
 * Отдельная группа, а не общая с баннерами: записи о людях и картинки за
 * аватарками живут по-разному, и смешивать их не стоит.
 *
 * Метка записи — {@code #sweetgram_wall_<номер>} того, О КОМ пишут. У каналов
 * и групп номер в переписке отрицательный, а минус в хэштег не входит, поэтому
 * им отдельная буква: {@code #sweetgram_wall_c<номер>}.
 *
 * Порт из Margy (Margelet), где стена жила в общей группе форка.
 */
public class SweetgramWallGroup {

    /** Где всё лежит. Публичная группа, читать может кто угодно, писать — тоже. */
    public static final String USERNAME = "sweetgramwall";

    /** Первые буквы всех наших меток в этой группе. */
    private static final String TAG_PREFIX = "#sweetgram_";

    /**
     * Любая наша метка. Нужна в двух ролях: вырезать её из показываемого
     * текста и не принять похожее за своё.
     */
    private static final java.util.regex.Pattern TAGS =
            java.util.regex.Pattern.compile("#sweetgram_(wall_c?\\d+)\\b");

    /** Метка конкретной стены: число — того, О КОМ пишут, а не того, кто пишет. */
    public static String tagWall(long peerId) {
        return peerId >= 0 ? "#sweetgram_wall_" + peerId : "#sweetgram_wall_c" + (-peerId);
    }

    /** Достать из текста номер стены, про которую он написан. Ноль — ни про чью. */
    private static final java.util.regex.Pattern WALL =
            java.util.regex.Pattern.compile("#sweetgram_wall_(c?)(\\d+)\\b");

    private static long groupId;
    private static boolean resolving;

    /**
     * Чья стена сейчас открыта. Ноль — ничья.
     *
     * Пока стена открыта, отправка в группу дописывает её метку сама: человек
     * пишет в обычное поле обычной переписки и про метки не знает.
     */
    private static long wallPeer;

    /** Адрес группы между запусками: имя в адрес переводит сервер, и делать
     *  это на каждый открытый профиль — лишняя поездка. */
    private static final String KEY_GROUP = "sweetgram_wall_group_id";
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
         * а это ровно та разница, из-за которой «записей нет» нельзя отличить
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
     * делать его на каждую открытую стену — то же самое, что опрашивать
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
     * Ищет в группе записи с этой меткой, при желании — только от одного
     * человека.
     *
     * Ищет сервер, а не телефон: своими силами пришлось бы выкачать всю
     * группу. Но на сервер одного полагаться нельзя — его поиск разбивает
     * слова по подчёркиваниям, и найденное мы ещё и перепроверяем сами.
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
                        FileLog.e("sweetgram: поиск по стене не вышел: " + error.text);
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
                        FileLog.e("sweetgram: история стены не пришла: " + error.text);
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
     * приблизительный: он охотно вернёт и сообщение, где слова метки встретились
     * случайно.
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

    /** Убрать своё сообщение из группы. Чужие удалять нечем — и не надо. */
    public static void remove(int messageId) {
        resolve(dialogId -> {
            if (dialogId == 0 || messageId == 0) {
                return;
            }
            try {
                final ArrayList<Integer> ids = new ArrayList<>();
                ids.add(messageId);
                account().getMessagesController().deleteMessages(ids, null, null, dialogId,
                        0, true, 0);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    /**
     * Оставить из списка только сообщения этой стены.
     *
     * Метка на месте: мы её не вырезаем, только прячем при показе. Поэтому
     * отбирать можно по самому тексту, как и задумано.
     */
    public static ArrayList<MessageObject> onlyWall(
            ArrayList<MessageObject> messages, String tag) {
        if (tag == null || tag.length() == 0 || messages == null || messages.isEmpty()) {
            return messages;
        }
        final ArrayList<MessageObject> out = new ArrayList<>();
        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null) {
                continue;
            }
            if (message.messageOwner.message != null && hasTag(message.messageOwner, tag)) {
                out.add(message);
            }
        }
        return out;
    }

    /**
     * Чью стену это сообщение о person'е: номер из метки.
     *
     * Ради этого метки и видны поиску: в группе люди пишут о человеке, а о
     * ком именно — понятно только из метки. Нажал на запись в группе — и
     * попал на стену этого человека.
     */
    public static long wallOf(MessageObject message) {
        if (message == null || message.messageOwner == null
                || message.messageOwner.message == null) {
            return 0;
        }
        final java.util.regex.Matcher at = WALL.matcher(message.messageOwner.message);
        if (!at.find()) {
            return 0;
        }
        try {
            final long id = Long.parseLong(at.group(2));
            // Буква перед числом значит канал или группу: у них номер
            // отрицательный, а минус в хэштег не входит.
            return "c".equals(at.group(1)) ? -id : id;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Где в тексте стоят наши метки, чтобы их вырезать при показе.
     *
     * Метки нужны поиску: по ним стена и собирается. Читателю они — мусор в
     * первой строке каждой записи, и вырезаем мы их при показе, а не при
     * отправке: сообщение в группе должно оставаться само по себе.
     */
    public static List<int[]> cutsIn(TLRPC.Message message) {
        if (message == null || message.message == null
                || message.message.indexOf(TAG_PREFIX) < 0) {
            return null;
        }
        final java.util.regex.Matcher at = TAGS.matcher(message.message);
        List<int[]> cuts = null;
        while (at.find()) {
            int to = at.end();
            // Съедаем пробелы и перевод строки следом: иначе запись
            // начиналась бы с пустой строки там, где метка стояла отдельно.
            while (to < message.message.length()
                    && (message.message.charAt(to) == '\n' || message.message.charAt(to) == ' ')) {
                to++;
            }
            if (cuts == null) {
                cuts = new ArrayList<>();
            }
            cuts.add(new int[]{at.start(), to});
        }
        return cuts;
    }

    /** Вырезать найденные метки из показываемого текста. */
    public static CharSequence applyCuts(CharSequence text, List<int[]> cuts) {
        if (text == null || cuts == null || cuts.isEmpty()) {
            return text;
        }
        final android.text.SpannableStringBuilder out =
                new android.text.SpannableStringBuilder(text);
        for (int i = cuts.size() - 1; i >= 0; i--) {
            final int from = Math.min(cuts.get(i)[0], out.length());
            final int to = Math.min(cuts.get(i)[1], out.length());
            if (from < to) {
                out.delete(from, to);
            }
        }
        return out;
    }

    /**
     * Сдвинуть разметку сообщения вслед за вырезанными метками.
     *
     * Разметка (жирный, курсив и прочее) живёт номерами символов; вырезали
     * кусок — сдвинули всё, что было правее. Сущность, попавшая на метку
     * целиком, умирает: разметка на служебном слове читателю не нужна.
     *
     * Копия, а не правка на месте: разметка принадлежит сообщению, а сообщение
     * общее — сдвинув её на месте, мы испортили бы его для остальных экранов,
     * где метка должна остаться.
     */
    public static ArrayList<TLRPC.MessageEntity> shiftEntities(
            ArrayList<TLRPC.MessageEntity> entities, List<int[]> cuts) {
        if (entities == null || entities.isEmpty() || cuts == null || cuts.isEmpty()) {
            return entities;
        }
        final ArrayList<TLRPC.MessageEntity> out = new ArrayList<>();
        for (TLRPC.MessageEntity entity : entities) {
            int from = entity.offset;
            int to = entity.offset + entity.length;
            boolean gone = false;
            for (int i = cuts.size() - 1; i >= 0; i--) {
                final int cutFrom = cuts.get(i)[0];
                final int cutTo = cuts.get(i)[1];
                if (from >= cutFrom && to <= cutTo) {
                    gone = true;
                    break;
                }
                final int size = cutTo - cutFrom;
                if (from >= cutTo) {
                    from -= size;
                    to -= size;
                } else if (to > cutFrom) {
                    to -= Math.min(size, to - cutFrom);
                    if (from > cutFrom) {
                        from = cutFrom;
                    }
                }
            }
            if (gone || to <= from) {
                continue;
            }
            out.add(copyWith(entity, from, to - from));
        }
        return out;
    }

    /**
     * Копия разметки со сдвинутыми номерами.
     *
     * Именно копия: разметка принадлежит сообщению, а сообщение общее —
     * сдвинув её на месте, мы испортили бы его для всех остальных экранов,
     * где метка должна остаться.
     */
    private static TLRPC.MessageEntity copyWith(TLRPC.MessageEntity entity, int offset, int length) {
        try {
            final TLRPC.MessageEntity copy = entity.getClass().newInstance();
            copy.flags = entity.flags;
            copy.offset = offset;
            copy.length = length;
            copy.url = entity.url;
            copy.language = entity.language;
            copy.collapsed = entity.collapsed;
            return copy;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    /** Открыта ли сейчас чья-нибудь стена: отправка знает про метки. */
    public static boolean writing() {
        return wallPeer != 0;
    }

    /** Чью стену сейчас пишем. Ноль — обычная переписка. */
    public static void writingTo(long peerId) {
        wallPeer = peerId;
    }

    /**
     * Дописать метку стены к тексту, уходящему в группу.
     *
     * Возвращает null, если отправлять нельзя, — в тексте запрещённая ссылка.
     * Отдельная проверка на отправке нужна потому, что показ чужого мы и так
     * фильтруем, но пускать своё в группу и молча прятать его на стене было бы
     * враньём обоим: и написавшему, и читающему.
     */
    public static String tagged(String text, long dialogId) {
        if (wallPeer == 0 || text == null || groupId == 0 || dialogId != groupId) {
            return text;
        }
        final String tag = tagWall(wallPeer);
        if (text.contains(tag)) {
            return text;    // уже с меткой: повторяться незачем
        }
        if (firstBadLink(text) != null) {
            return null;
        }
        return tag + "\n" + text;
    }

    /** Сказать вслух, почему сообщение не ушло. Молча проглотить нельзя. */
    public static void refuse() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.widget.Toast.makeText(
                        ApplicationLoader.applicationContext,
                        "Ссылки на сторонние сайты на стене не размещаются",
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    /** Разрешённые места для ссылок на стене. Сравнение по концу имени. */
    private static final String[] ALLOWED_LINKS = {
            "t.me", "telegram.me", "telegram.org", "telegram.dog",
            "youtube.com", "youtu.be",
            "twitter.com", "x.com",
            "reddit.com", "redd.it",
            "vk.com", "vk.ru", "vk.me",
            "whatsapp.com", "wa.me",
    };

    /**
     * Первая запрещённая ссылка в тексте или null.
     *
     * Стена задумана как место, где о человеке говорят другие, и первое, что
     * туда понесут, — ссылки на развод-сайты. Правило грубое нарочно: пускаем
     * только известные крупные площадки, всё остальное — мимо.
     *
     * Проверяются и адреса ссылок, не только видимый текст: написать
     * «youtube.com», а вести на что угодно, может кто угодно.
     */
    public static String firstBadLink(String text) {
        if (text == null) {
            return null;
        }
        final String lower = text.toLowerCase(Locale.US);
        final java.util.regex.Matcher at =
                java.util.regex.Pattern.compile(
                        "https?://[^\\s<>\"']+|[\\w.-]+\\.[a-z]{2,}[^\\s<>\"']*",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(lower);
        while (at.find()) {
            String url = at.group();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                url = url.substring(url.indexOf("//") + 2);
            }
            // Отрезаем путь: разрешено место, а не каждая его страница.
            final int slash = url.indexOf('/');
            final String host = slash >= 0 ? url.substring(0, slash) : url;
            // Отрезаем порт и логин, если затесались.
            final int atSign = host.indexOf('@');
            final String bare = atSign >= 0 ? host.substring(atSign + 1) : host;
            final int colon = bare.indexOf(':');
            final String name = colon >= 0 ? bare.substring(0, colon) : bare;
            boolean ok = false;
            for (String allowed : ALLOWED_LINKS) {
                // Совпадение либо целиком, либо по точке слева:
                // «youtube.com.scam.ru» — это не ютуб, это scam.ru.
                if (name.equals(allowed) || name.endsWith("." + allowed)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return at.group();
            }
        }
        return null;
    }
}
