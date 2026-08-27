package org.telegram.sweetgram;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Запуск плагинов и консоль.
 *
 * Питон поднимается один раз на всё приложение и живёт в отдельном потоке:
 * плагин, ушедший в вечный цикл, не должен вешать переписку. Всё, что плагин
 * печатает, попадает в консоль — она нужна не для красоты, а чтобы автор
 * плагина видел свою ошибку, а пользователь видел, что плагин вообще делает.
 */
public class SweetgramPluginHost {

    /** Строка консоли: время, плагин, текст. */
    public static final class Line {
        public final long time = System.currentTimeMillis();
        public final String plugin;
        public final String text;
        public final boolean error;

        Line(String plugin, String text, boolean error) {
            this.plugin = plugin;
            this.text = text;
            this.error = error;
        }
    }

    private static final int MAX_LINES = 500;
    private static final List<Line> console = new ArrayList<>();
    private static final List<Runnable> listeners = new ArrayList<>();
    private static Thread worker;
    private static Handler handler;
    private static boolean started;

    public static synchronized List<Line> console() {
        return new ArrayList<>(console);
    }

    public static synchronized void clear() {
        console.clear();
        notifyListeners();
    }

    public static synchronized void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static synchronized void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        final List<Runnable> copy;
        synchronized (SweetgramPluginHost.class) {
            copy = new ArrayList<>(listeners);
        }
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            for (Runnable listener : copy) {
                listener.run();
            }
        });
    }

    public static synchronized void log(String plugin, String text, boolean error) {
        console.add(new Line(plugin, text, error));
        while (console.size() > MAX_LINES) {
            console.remove(0);
        }
        notifyListeners();
    }

    /**
     * Поднимает питон и запускает включённые плагины. Второй раз ничего не
     * делает. Пока плагинов нет или они выключены, питон не трогаем: это
     * одиннадцать лишних мегабайт памяти ни за чем.
     */
    public static synchronized void start() {
        if (started || !SweetgramPlugins.anyEnabled()) {
            return;
        }
        started = true;
        try {
            worker = new Thread(() -> {
                Looper.prepare();
                handler = new Handler(Looper.myLooper());
                try {
                    python("start", new Class<?>[]{android.content.Context.class},
                            ApplicationLoader.applicationContext);
                } catch (Throwable t) {
                    FileLog.e(t);
                    log("sweetgram", "питон не поднялся: " + t, true);
                    // Не держим флаг «поднято»: при следующем включении
                    // плагина попытка повторится, а не молча пропадёт.
                    started = false;
                    return;
                }
                log("sweetgram", "питон готов", false);
                for (SweetgramPlugins.Plugin plugin : SweetgramPlugins.installed()) {
                    if (plugin.enabled() && SweetgramConfig.pluginsEnabled()) {
                        run(plugin);
                    }
                }
                Looper.loop();
            }, "sweetgram-plugins");
            worker.setDaemon(true);
            worker.start();
        } catch (Throwable t) {
            // Поднять поток не вышло — плагины просто не заработают, но
            // приложение не должно из-за этого падать.
            FileLog.e(t);
            started = false;
        }
    }

    /**
     * Просит запустить плагин. Выполняется в потоке плагинов, не в переписке.
     *
     * Обратной кнопки нет: остановить уже работающий питоновский код, не
     * рискуя данными, нечем. Выключение плагина значит «больше не
     * запускать»; запущенный доживёт до перезапуска приложения. Так и
     * написано на экране, врать тут нечем.
     */
    public static void launch(SweetgramPlugins.Plugin plugin) {
        try {
            start();
        } catch (Throwable t) {
            FileLog.e(t);
            return;
        }
        final Handler h = handler;
        if (h == null) {
            // Питон ещё поднимается — плагин заберут при старте.
            return;
        }
        try {
            h.post(() -> run(plugin));
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void run(SweetgramPlugins.Plugin plugin) {
        if (plugin == null || !plugin.entry().exists()) {
            log(plugin == null ? "?" : plugin.name, "нет main.py", true);
            return;
        }
        try {
            python("run", new Class<?>[]{String.class, String.class, String.class},
                    plugin.id, plugin.name, plugin.folder.getAbsolutePath());
        } catch (Throwable t) {
            FileLog.e(t);
            log(plugin.name, String.valueOf(t), true);
        }
    }

    /**
     * Зовёт SweetgramPython из модуля приложения.
     *
     * Через отражение, потому что сам питон подключён только к одной сборке:
     * прямая ссылка сломала бы сборку всех остальных, где движка нет. Если
     * класса нет, ошибка попадёт в консоль как есть — молча не пропадёт.
     */
    /**
     * Сообщает плагинам, что человек открыл чат.
     *
     * Раньше такого события не было, и плагину, которому нужен открытый чат,
     * приходилось спрашивать самому по четыре раза в секунду. Опрос впустую
     * — плохая плата за то, что приложение и так знает.
     *
     * Зовётся из переписки, поэтому здесь тихо: питон может быть ещё не
     * поднят, плагинов может не быть вовсе, и ронять из-за этого чат нельзя.
     */
    public static void chatOpened(Object fragment) {
        if (!started) {
            return;
        }
        final Handler h = handler;
        if (h == null) {
            return;
        }
        h.post(() -> {
            try {
                python("chatOpened", new Class<?>[]{Object.class}, fragment);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    /**
     * Отдать работу потоку плагинов. Если питон ещё не поднят, работы просто
     * не будет: событие без слушателя — не повод будить одиннадцать мегабайт.
     */
    static void post(Runnable work) {
        final Handler h = handler;
        if (h != null) {
            h.post(work);
        }
    }

    /** Короткое сообщение на экране. Зовётся из плагина. */
    public static void toast(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.widget.Toast.makeText(ApplicationLoader.applicationContext,
                        text, android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Своя память плагина: переживает перезапуск приложения и не пропадает
     * при обновлении самого плагина, потому что лежит не в его папке.
     */
    public static String get(String pluginId, String key, String fallback) {
        try {
            return ApplicationLoader.applicationContext
                    .getSharedPreferences("sweetgram_plugin_" + pluginId, Context.MODE_PRIVATE)
                    .getString(key, fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static void set(String pluginId, String key, String value) {
        try {
            ApplicationLoader.applicationContext
                    .getSharedPreferences("sweetgram_plugin_" + pluginId, Context.MODE_PRIVATE)
                    .edit().putString(key, value).apply();
        } catch (Throwable ignored) {
        }
    }

    static void python(String method, Class<?>[] types, Object... args) throws Throwable {
        pythonValue(method, types, args);
    }

    /** То же самое, но с ответом: нужен там, где питона спрашивают, а не зовут. */
    static Object pythonValue(String method, Class<?>[] types, Object... args) throws Throwable {
        try {
            return Class.forName("org.telegram.sweetgram.SweetgramPython")
                    .getMethod(method, types)
                    .invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Иначе в консоли будет «InvocationTargetException» вместо того,
            // что на самом деле упало внутри.
            throw e.getCause() == null ? e : e.getCause();
        }
    }
}
