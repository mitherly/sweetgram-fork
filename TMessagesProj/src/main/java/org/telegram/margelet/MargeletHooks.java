package org.telegram.margelet;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Двери, через которые плагин узнаёт о происходящем.
 *
 * Раньше плагину оставалось одно: просыпаться по будильнику и смотреть, не
 * изменилось ли что-нибудь. Так написан первый наш плагин, и так писать
 * плохо: телефон греется впустую, а плагин всё равно узнаёт о событии позже,
 * чем оно случилось.
 *
 * Дверей нарочно немного, и каждая названа. Это не то же самое, что дать
 * плагину подменять любой метод приложения: подмена любого метода — это
 * переписывание чужого кода на ходу, для этого нужна отдельная библиотека,
 * которая правит машинный код, и любое обновление телеграма ломает всё, что
 * на ней написано. Названная дверь переживает обновление, потому что за неё
 * отвечаем мы, а не случайное совпадение имён.
 *
 * Флаги здесь не для красоты: пока никто не подписан, питон не дёргается
 * вовсе, и отправка сообщения стоит ровно столько же, сколько без плагинов.
 */
public class MargeletHooks {

    /**
     * Ответ питона, означающий «не отправляй».
     *
     * Записан экранированием нарочно. Раньше здесь стоял сам знак — невидимый
     * нулевой байт прямо в исходнике, — а в питоне на его месте оказался
     * обычный пробел. Строки не совпадали никогда, отмена не срабатывала ни
     * разу, и вместо неё в переписку уходила сама эта метка. Увидел это не я,
     * а человек, у которого перед каждым курсом биткоина появлялось
     * «margelet-cancel».
     *
     * Невидимый знак в исходнике нельзя ни прочитать глазами, ни сверить.
     * Поэтому — только экранированием, и в обоих файлах одинаково.
     */
    public static final String CANCEL = "\u0000margelet-cancel";

    /** Кнопка плагина в меню чата. */
    public static final class Button {
        public final String pluginId;
        public final String key;
        public final String title;

        Button(String pluginId, String key, String title) {
            this.pluginId = pluginId;
            this.key = key;
            this.title = title;
        }
    }

    private static volatile boolean wantsSend;
    private static volatile boolean wantsMessage;
    private static final List<Button> buttons = new ArrayList<>();
    private static boolean watching;

    // --- подписка со стороны питона ---

    public static void wantSend() {
        wantsSend = true;
    }

    public static void wantMessage() {
        wantsMessage = true;
        watch();
    }

    public static boolean hasSend() {
        return wantsSend;
    }

    /**
     * Кнопка плагина в меню чата. Плагин зовёт это при запуске; при
     * перезапуске приложения список собирается заново, поэтому одинаковую
     * запись заменяем, а не копим.
     */
    public static synchronized void addButton(String pluginId, String key, String title) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            if (buttons.get(i).pluginId.equals(pluginId) && buttons.get(i).key.equals(key)) {
                buttons.remove(i);
            }
        }
        buttons.add(new Button(pluginId, key, title));
    }

    public static synchronized List<Button> buttons() {
        return new ArrayList<>(buttons);
    }

    public static synchronized Button button(int index) {
        return index >= 0 && index < buttons.size() ? buttons.get(index) : null;
    }

    /** Нажали кнопку плагина. Экран чата уходит плагину как есть. */
    public static void buttonClicked(int index, Object fragment) {
        final Button button = button(index);
        if (button == null) {
            return;
        }
        MargeletPluginHost.post(() -> {
            try {
                MargeletPluginHost.python("buttonClicked",
                        new Class<?>[]{String.class, String.class, Object.class},
                        button.pluginId, button.key, fragment);
            } catch (Throwable t) {
                FileLog.e(t);
                MargeletPluginHost.log(button.title, String.valueOf(t), true);
            }
        });
    }

    // --- отправка ---

    /**
     * Человек отправляет текст. Плагин может его поменять или отменить
     * отправку совсем.
     *
     * Здесь единственное место во всём движке плагинов, где питон зовётся
     * прямо на том потоке, который его позвал, и ждать приходится по-честному.
     * Иначе никак: сообщение уже уходит, и ответ «поменяй текст» нужен сейчас,
     * а не через секунду. Поэтому долгая работа в этом обработчике задержит
     * отправку — и мы говорим об этом вслух, в консоль, когда замечаем.
     *
     * @return текст, который надо отправить, или null — не отправлять.
     */
    public static String sending(String text, long dialogId) {
        if (!wantsSend || text == null) {
            return text;
        }
        final long started = System.currentTimeMillis();
        try {
            final Object answer = MargeletPluginHost.pythonValue("sending",
                    new Class<?>[]{String.class, long.class}, text, dialogId);
            final long spent = System.currentTimeMillis() - started;
            if (spent > 100) {
                MargeletPluginHost.log("margelet",
                        "обработчик отправки думал " + spent + " мс — столько же ждал человек", true);
            }
            if (answer == null) {
                return text;
            }
            final String result = String.valueOf(answer);
            // Сравнение с меткой — основной путь. Проверка на нулевой байт —
            // страховка от того, что уже один раз случилось: метки разошлись
            // между двумя файлами, и мусор ушёл в переписку. Набрать такое с
            // клавиатуры нельзя, значит это в любом случае не текст человека.
            if (CANCEL.equals(result) || result.indexOf('\u0000') >= 0) {
                return null;
            }
            return result;
        } catch (Throwable t) {
            FileLog.e(t);
            MargeletPluginHost.log("margelet", String.valueOf(t), true);
            return text;
        }
    }

    // --- приход сообщений ---

    /**
     * Ставит наблюдателя за новыми сообщениями — по одному на каждую учётную
     * запись, потому что уведомления в телеграме заведены отдельно на каждую.
     *
     * Второй раз не ставим: два наблюдателя означали бы, что каждое сообщение
     * придёт плагину дважды.
     */
    private static void watch() {
        AndroidUtilities.runOnUIThread(() -> {
            if (watching) {
                return;
            }
            watching = true;
            final NotificationCenter.NotificationCenterDelegate delegate = (id, account, args) -> {
                if (id == NotificationCenter.didReceiveNewMessages && wantsMessage && args != null && args.length >= 2) {
                    deliver(args);
                } else if (id == NotificationCenter.messagesDeleted && args != null && args.length >= 1) {
                    deliverDeleted(args);
                }
            };
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                NotificationCenter.getInstance(account)
                        .addObserver(delegate, NotificationCenter.didReceiveNewMessages);
                NotificationCenter.getInstance(account)
                        .addObserver(delegate, NotificationCenter.messagesDeleted);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void deliver(Object[] args) {
        final long dialogId;
        final ArrayList<MessageObject> messages;
        try {
            dialogId = (Long) args[0];
            messages = (ArrayList<MessageObject>) args[1];
        } catch (Throwable ignored) {
            return;
        }
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (MessageObject message : messages) {
            if (message == null) {
                continue;
            }
            final String text = message.messageText == null ? "" : message.messageText.toString();
            final int messageId = message.getId();
            final boolean out = message.isOut();
            MargeletPluginHost.post(() -> {
                try {
                    MargeletPluginHost.python("received",
                            new Class<?>[]{String.class, long.class, int.class, boolean.class},
                            text, dialogId, messageId, out);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static void deliverDeleted(Object[] args) {
        final ArrayList<Integer> deletedIds;
        final long channelId;
        try {
            deletedIds = (ArrayList<Integer>) args[0];
            channelId = args.length > 1 && args[1] instanceof Long ? (Long) args[1] : 0;
        } catch (Throwable ignored) {
            return;
        }
        if (deletedIds == null || deletedIds.isEmpty()) {
            return;
        }
        for (Integer msgId : deletedIds) {
            if (msgId == null) continue;
            final int id = msgId;
            MargeletPluginHost.post(() -> {
                try {
                    MargeletPluginHost.python("deleted",
                            new Class<?>[]{int.class, long.class},
                            id, channelId);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
        }
    }

    // --- работа в стороне и своя отправка ---

    /**
     * Отдельный поток для долгой работы плагина.
     *
     * Появился не из красоты, а по чужому коду. Первые два плагина не от нас —
     * погода и курс валют — оба ходят в сеть прямо внутри обработчика
     * отправки. А обработчик этот исполняется на том же потоке, что рисует
     * экран: пока идёт запрос, телефон не рисует ничего. У погоды это до пяти
     * секунд, у курса — до восемнадцати, три запроса подряд по шесть.
     *
     * Обычно андроид ловит такое сам: полез в сеть с главного потока — сразу
     * падение с понятным объяснением. Здесь эта защита не срабатывает. Она
     * живёт в джавовых сокетах, а питон ходит в сеть своими, мимо джавы, и
     * охранник этого просто не видит. Ни падения, ни предупреждения — телефон
     * молча замирает. Оба автора были уверены, что у них всё хорошо.
     *
     * Значит, виновата не их невнимательность, а то, что замены не было.
     * Теперь есть.
     */
    public static void background(Runnable work) {
        final Thread thread = new Thread(work, "margelet-plugin-work");
        // Приложение не должно ждать закрытия из-за плагина, ушедшего в сеть.
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Отправить сообщение от имени человека. Так плагин отвечает на команду,
     * когда ответ наконец пришёл, — вместо того чтобы держать отправку.
     */
    public static void send(long dialogId, String text) {
        if (text == null || text.length() == 0 || dialogId == 0) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                final int account = org.telegram.messenger.UserConfig.selectedAccount;
                org.telegram.messenger.SendMessagesHelper.getInstance(account).sendMessage(
                        org.telegram.messenger.SendMessagesHelper.SendMessageParams.of(text, dialogId));
            } catch (Throwable t) {
                FileLog.e(t);
                MargeletPluginHost.log("margelet", "не отправилось: " + t, true);
            }
        });
    }

    // --- сеть и экран ---

    /**
     * Запрос в сеть, который не может подвесить приложение.
     *
     * Оба первых плагина не от нас писали запрос руками и оба вешали экран:
     * питон ходит в сеть мимо джавы, поэтому обычная андроидовская защита
     * молчит, и подвисание выглядит как «просто тормозит». Дать замену мало —
     * надо, чтобы правильный путь был короче неправильного. Отсюда этот метод:
     * писать его через background и urllib длиннее, чем позвать отсюда.
     *
     * Ответ отдаётся в главный поток. Не получилось — отдаётся null, и это
     * не ошибка плагина: сети может не быть.
     */
    public static void fetch(String url, FetchCallback callback) {
        final Thread worker = new Thread(() -> {
            String result = null;
            java.net.HttpURLConnection connection = null;
            try {
                connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", MargeletConfig.APP_NAME);
                if (connection.getResponseCode() == 200) {
                    try (java.io.InputStream in = connection.getInputStream()) {
                        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        final byte[] buffer = new byte[8192];
                        int read;
                        // Полтора мегабайта — потолок. Плагину, которому нужно
                        // больше, нужен не этот метод, а своя работа в фоне.
                        while ((read = in.read(buffer)) > 0 && out.size() <= 1536 * 1024) {
                            out.write(buffer, 0, read);
                        }
                        result = out.toString("UTF-8");
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final String delivered = result;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    callback.onResult(delivered);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
        }, "margelet-plugin-fetch");
        worker.setDaemon(true);
        worker.start();
    }

    /** Ответ на запрос. Зовётся в главном потоке; text — null, если не вышло. */
    public interface FetchCallback {
        void onResult(String text);
    }

    /**
     * Текущий экран приложения.
     *
     * Без него плагин, которому нужно что-нибудь показать, лезет во внутренние
     * поля приложения по имени — как пришлось делать мне же в плагине с
     * играми. Пусть лучше будет названный способ.
     */
    public static android.app.Activity activity() {
        try {
            return org.telegram.ui.LaunchActivity.instance;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Показать окно с тем, что плагин собрал сам.
     *
     * Рамку — заголовок, кнопку «Закрыть», тему — берёт на себя приложение,
     * поэтому окно плагина выглядит как окно приложения, а не как чужая
     * вставка.
     */
    public static void window(String title, android.view.View view) {
        AndroidUtilities.runOnUIThread(() -> {
            final android.app.Activity activity = activity();
            if (activity == null || view == null) {
                return;
            }
            try {
                new org.telegram.ui.ActionBar.AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setView(view)
                        .setNegativeButton(org.telegram.messenger.LocaleController
                                .getString(org.telegram.messenger.R.string.Close), null)
                        .show();
            } catch (Throwable t) {
                FileLog.e(t);
                MargeletPluginHost.log("margelet", "окно не открылось: " + t, true);
            }
        });
    }

    /** Цвет для андроида: там он знаковый, а из питона приходит без знака. */
    public static int color(long argb) {
        return (int) argb;
    }

    // --- настройки плагина ---

    /**
     * Плагин заявляет, из чего состоит его экран настроек. Заявка хранится
     * рядом с его памятью, а не в оперативной: экран настроек надо уметь
     * открыть и у выключенного плагина, который сейчас не выполняется.
     */
    public static void declare(String pluginId, String json) {
        MargeletPluginHost.set(pluginId, "__settings", json);
    }

    public static String declared(String pluginId) {
        return MargeletPluginHost.get(pluginId, "__settings", null);
    }

    public static boolean hasSettings(String pluginId) {
        final String json = declared(pluginId);
        return json != null && json.length() > 2;
    }

    /** Человек поменял настройку. Плагин узнаёт об этом сразу, без перезапуска. */
    public static void settingsChanged(String pluginId, String key, String value) {
        MargeletPluginHost.post(() -> {
            try {
                MargeletPluginHost.python("settingsChanged",
                        new Class<?>[]{String.class, String.class, String.class},
                        pluginId, key, value);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }
}
