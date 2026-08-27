package org.telegram.sweetgram;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;

/**
 * «Приступ»: весь текст переливается радугой.
 *
 * Как это сделано. Цвет текста в телеграме почти везде берётся из одного места —
 * Theme.getColor(ключ). Туда и вставлена развилка: если режим включён и ключ
 * отвечает за текст, вместо цвета темы отдаётся текущий цвет радуги. Ничего в
 * теме при этом не меняется — выключил, и всё вернулось само, чинить нечего.
 *
 * Вторая половина — перерисовка. Экран сам себя не обновляет, поэтому пока режим
 * включён, по кадрам идёт обход дерева и всем видимым View говорится
 * перерисоваться.
 *
 * Про скорость. Просили «быстро», но быстро мигающая картинка с сильным
 * перепадом яркости — это ровно то, от чего у людей со светочувствительной
 * эпилепсией бывает приступ; опаснее всего частота от трёх до тридцати вспышек в
 * секунду. Поэтому здесь не мигание, а непрерывный перелив по кругу цветов:
 * оттенок едет плавно, а яркость держится постоянной. Выглядит так же весело,
 * а вспышек нет. Предупреждение перед включением всё равно стоит — за чужие
 * глаза я решать не могу.
 */
public class SweetgramSeizure {

    /** Полный круг оттенков. Быстрее — уже начинается мельтешение. */
    private static final long CYCLE_MS = 1800L;

    private static boolean[] textKeys;

    /**
     * Развилка стоит на самом горячем пути в приложении: цвет спрашивают тысячи
     * раз за кадр. Поэтому флаг живёт в памяти, а не вычитывается из настроек
     * каждый раз.
     */
    private static Boolean cached;

    public static boolean enabled() {
        if (cached == null) {
            try {
                cached = SweetgramConfig.seizure();
            } catch (Throwable t) {
                return false;   // спросили раньше, чем поднялось приложение
            }
        }
        return cached;
    }

    /**
     * Просьба подержать кадры ещё немного: её шлёт радужная разметка, когда её
     * рисуют. «Приступ» при этом может быть выключен — обход нужен обоим, и
     * заводить второй такой же незачем.
     */
    private static long pokedAt;

    public static void poke() {
        pokedAt = SystemClock.elapsedRealtime();
        start();
    }

    private static boolean wanted() {
        return enabled() || SystemClock.elapsedRealtime() - pokedAt < 1000L;
    }

    public static void set(boolean on) {
        SweetgramConfig.setSeizure(on);
        cached = on;
        if (on) {
            start();    // включили — обход надо завести заново
        }
    }

    /**
     * Слова, по которым ключ темы опознаётся как текстовый. Ключей больше
     * восьмисот, перечислять их руками — гарантированно что-нибудь забыть.
     */
    private static final String[] WORDS = {
            "text", "title", "subtitle", "name", "link", "hint", "message",
            "date", "status", "time", "placeholder", "header", "caption", "label"
    };

    /**
     * Что стоит в названии ПОСЛЕ слова про текст и означает, что красится не
     * текст, а подложка под ним, значок или полоска.
     */
    private static final String[] NOT_AFTER = {
            "background", "selection", "highlight", "shadow", "icon",
            "gradient", "cursor", "arrow", "progress", "line", "panel"
    };

    /** Немногие исключения, которые правилом не ловятся. */
    private static final String[] NOT_AT_ALL = {
            "emptylist", "photoplaceholder", "locationplaceholder",
            "sharedmedia_linkplaceholder", "panelvoice", "panelsend", "panelcancel"
    };

    /**
     * Отвечает ли ключ темы за текст.
     *
     * Первая версия правила смотрела на названия с учётом регистра и знала
     * шесть слов. Из-за этого мимо прошли chats_name, chats_message, chats_date
     * — то есть весь список чатов целиком, самый видный экран приложения.
     * Владелец посмотрел и сказал: «меняется очень мало, большинство остаётся
     * белым». Так и было.
     *
     * Отдельная ловушка — слово «background» в названии: у половины текстовых
     * ключей оно стоит в начале (windowBackgroundWhiteBlackText — это цвет
     * букв, а не фона). Поэтому подложка отсекается по хвосту после слова про
     * текст, а не по всему названию.
     */
    private static boolean isTextKey(int key) {
        if (textKeys == null) {
            final boolean[] table = new boolean[Theme.colorsCount];
            for (int i = 0; i < Theme.colorsCount; i++) {
                final String name = ThemeColors.getStringName(i);
                if (name == null) {
                    continue;
                }
                final String low = name.toLowerCase();
                int at = -1;
                for (String word : WORDS) {
                    at = Math.max(at, low.lastIndexOf(word));
                }
                if (at < 0 || has(low.substring(at), NOT_AFTER) || has(low, NOT_AT_ALL)) {
                    continue;
                }
                table[i] = true;
            }
            textKeys = table;
        }
        return key >= 0 && key < textKeys.length && textKeys[key];
    }

    private static boolean has(String where, String[] what) {
        for (String s : what) {
            if (where.contains(s)) {
                return true;
            }
        }
        return false;
    }

    /** Цвет для ключа или ноль, если этот ключ трогать не надо. */
    public static int colorFor(int key) {
        if (!enabled() || !isTextKey(key)) {
            return 0;
        }
        return color();
    }

    private static final float[] hsv = {0f, 0.85f, 0.95f};
    private static long colorAt = -1;
    private static int colorNow;

    /**
     * Текущий цвет круга. Насыщенность и яркость постоянные — вспышек нет.
     * Считается раз в миллисекунду и переиспользуется: за кадр цвет спрашивают
     * тысячи раз, и каждый раз считать заново — только мусор копить.
     */
    public static int color() {
        final long now = SystemClock.elapsedRealtime();
        if (now != colorAt) {
            colorAt = now;
            hsv[0] = (now % CYCLE_MS) * 360f / CYCLE_MS;
            colorNow = Color.HSVToColor(hsv);
        }
        return colorNow;
    }

    /**
     * Перерисовка всего видимого дерева, пока режим включён. Корень окна
     * запоминается один раз при запуске; обход идёт только во включённом
     * состоянии и сам прекращается, когда режим выключают, — иначе кадры
     * рисовались бы вхолостую и просто ели батарею.
     */
    public static void attach(View root) {
        window = root == null ? null : new WeakReference<>(root);
        if (wanted()) {
            start();
        }
    }

    private static WeakReference<View> window;
    private static boolean running;

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            running = false;
            final View root = window == null ? null : window.get();
            if (root == null) {
                return;
            }
            final Context context = root.getContext();
            if (context instanceof Activity && ((Activity) context).isDestroyed()) {
                return;
            }
            if (!wanted()) {
                return;
            }
            invalidate(root);
            start();
        }
    };

    private static void start() {
        final View root = window == null ? null : window.get();
        if (root == null || running) {
            return;
        }
        running = true;
        root.postOnAnimation(TICK);
    }

    private static void invalidate(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        view.invalidate();
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidate(group.getChildAt(i));
            }
        }
    }
}
