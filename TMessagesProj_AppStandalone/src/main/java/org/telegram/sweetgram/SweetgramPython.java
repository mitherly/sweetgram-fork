package org.telegram.sweetgram;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/**
 * Единственное место, где форк трогает питон напрямую.
 *
 * Лежит в модуле приложения, а не в общей библиотеке, потому что движок
 * питона подключается только к этой сборке: остальным вариантам приложения
 * он не нужен, и тащить одиннадцать мегабайт во все — глупость. Библиотека
 * зовёт этот класс по имени, через отражение: так она собирается и там, где
 * питона нет вовсе.
 */
public class sweetgramPython {

    public static void start(Context context) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
    }

    /** Открылся чат. Плагины, которые на это подписаны, узнают об этом. */
    public static void chatOpened(Object fragment) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        host.callAttr("chat_opened", fragment);
    }

    /**
     * Человек отправляет текст. Ответ нужен сразу же, поэтому это
     * единственный вызов питона, который не откладывается, а ждёт.
     */
    public static String sending(String text, long dialogId) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        final PyObject answer = host.callAttr("sending", text, dialogId);
        return answer == null ? text : answer.toString();
    }

    /** Пришло сообщение. */
    public static void received(String text, long dialogId, int messageId, boolean out) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        host.callAttr("received", text, dialogId, messageId, out);
    }

    /**
     * Человек отправляет медиа. Ответ нужен до отправки, поэтому — как и
     * sending() — ждём по-честному. Пустая строка значит «подписи не было».
     */
    public static String sendingMedia(String kind, String caption, long dialogId) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        final PyObject answer = host.callAttr("sending_media", kind, caption, dialogId);
        return answer == null ? null : answer.toString();
    }

    /** У сообщения изменились реакции. Сводка вида «👍=3,🔥=1». */
    public static void reactions(long dialogId, int messageId, String summary) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        host.callAttr("reactions", dialogId, messageId, summary);
    }

    /** Сообщение отредактировано. */
    public static void edited(long dialogId, int messageId) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        host.callAttr("edited", dialogId, messageId);
    }

    /** Участник вошёл (joined) или вышел из чата. */
    public static void member(long dialogId, long userId, boolean joined) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        host.callAttr("member", dialogId, userId, joined);
    }

    /** Нажали кнопку плагина в меню чата. */
    public static void buttonClicked(String pluginId, String key, Object fragment) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        host.callAttr("button_clicked", pluginId, key, fragment);
    }

    /** Человек поменял настройку плагина на его экране настроек. */
    public static void settingsChanged(String pluginId, String key, String value) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        host.callAttr("settings_changed", pluginId, key, value);
    }

    public static void run(String id, String name, String folder) {
        final PyObject host = Python.getInstance().getModule("sweetgram_host");
        host.callAttr("run_plugin", id, name, folder);
    }
}
