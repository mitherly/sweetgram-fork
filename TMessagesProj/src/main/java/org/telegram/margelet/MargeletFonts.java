package org.telegram.margelet;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Свой шрифт на всё приложение.
 *
 * Телеграм берёт шрифты в одном месте — {@code AndroidUtilities.getTypeface} —
 * и складывает их в свой кэш. Поэтому подменять надо там же и один раз: если
 * ловить каждое место, где рисуется текст, часть экранов останется со старым
 * шрифтом, и выглядеть это будет как поломка, а не как настройка.
 *
 * Начертания не теряются. Телеграм просит у себя отдельные файлы под жирный и
 * курсив; здесь из выбранного шрифта делаются те же начертания — если своих в
 * нём нет, система дорисует их сама.
 */
public class MargeletFonts {

    /** Пусто — ничего не подменяем, всё как в телеграме. */
    public static final String DEFAULT = "";

    /** Готовые семейства из самой системы. Ничего скачивать не нужно. */
    public static final String[] SYSTEM = {"sans-serif", "serif", "monospace", "sans-serif-condensed"};

    private static final String PREFS = "margelet_fonts";
    private static final String KEY = "font";
    private static final String DIRECTORY = "margelet_fonts";

    /** Свой кэш: getTypeface зовут на каждой строчке текста. */
    private static final Map<String, Typeface> cache = new HashMap<>();
    private static String cachedFor;

    /** Один шрифт в списке: как называется и чем является. */
    public static final class Font {
        public final String id;
        public final String name;
        /** Свой файл можно удалить, встроенное — нет. */
        public final boolean own;

        Font(String id, String name, boolean own) {
            this.id = id;
            this.name = name;
            this.own = own;
        }
    }

    private static File directory() {
        final File directory = new File(ApplicationLoader.getFilesDirFixed(), DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    /**
     * Выбранный шрифт. Осторожно с ранними вызовами: за шрифтом приходят и до
     * того, как приложение полностью поднялось, а настроек тогда ещё нет.
     * Молчаливый ответ «как в телеграме» здесь лучше падения на старте.
     */
    public static String chosen() {
        try {
            return ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT);
        } catch (Throwable t) {
            return DEFAULT;
        }
    }

    public static void choose(String id) {
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, id == null ? DEFAULT : id).apply();
        synchronized (cache) {
            cache.clear();
            cachedFor = null;
        }
    }

    /** Всё, из чего можно выбрать: системные семейства и свои файлы. */
    public static List<Font> list() {
        final List<Font> out = new ArrayList<>();
        for (String family : SYSTEM) {
            out.add(new Font(family, family, false));
        }
        final File[] files = directory().listFiles();
        if (files != null) {
            for (File file : files) {
                final String name = file.getName();
                final int dot = name.lastIndexOf('.');
                out.add(new Font(file.getAbsolutePath(), dot > 0 ? name.substring(0, dot) : name, true));
            }
        }
        return out;
    }

    /**
     * Кладёт выбранный человеком файл к себе.
     *
     * Копия нужна, потому что доступ к чужому файлу живёт ровно до конца
     * выбора: сам файл может лежать в загрузках, на карточке или вовсе в
     * облаке, и через день его там не будет.
     */
    public static String install(Uri uri, String name) {
        if (uri == null) {
            return null;
        }
        final File target = new File(directory(), safe(name));
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                return null;
            }
            final byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        // Файл, который система не считает шрифтом, до настроек лучше не
        // пускать: иначе приложение перезапустится и останется без текста.
        try {
            Typeface.createFromFile(target);
        } catch (Throwable t) {
            target.delete();
            return null;
        }
        return target.getAbsolutePath();
    }

    /** Имя файла без чужих папок и сюрпризов. */
    private static String safe(String name) {
        if (name == null || name.isEmpty()) {
            return "font.ttf";
        }
        String cleaned = name.replace('/', '_').replace('\\', '_').replace("..", "_");
        if (!cleaned.toLowerCase().endsWith(".ttf") && !cleaned.toLowerCase().endsWith(".otf")) {
            cleaned = cleaned + ".ttf";
        }
        return cleaned;
    }

    public static void remove(Font font) {
        if (font == null || !font.own) {
            return;
        }
        if (font.id.equals(chosen())) {
            choose(DEFAULT);
        }
        new File(font.id).delete();
    }

    /**
     * Чем рисовать вместо телеграмовского файла. null — рисовать как было.
     *
     * Начертание берётся из имени файла, о котором просил телеграм: он держит
     * жирный и курсив отдельными файлами, и по их именам понятно, что именно
     * сейчас нужно.
     */
    public static Typeface replace(String assetPath) {
        try {
            return replaceOrThrow(assetPath);
        } catch (Throwable t) {
            // Шрифт — украшение. Что бы здесь ни случилось,текст должен
            // остаться на экране, пусть и телеграмовским шрифтом.
            return null;
        }
    }

    private static Typeface replaceOrThrow(String assetPath) {
        final String id = chosen();
        if (id == null || id.isEmpty() || assetPath == null) {
            return null;
        }
        synchronized (cache) {
            if (!id.equals(cachedFor)) {
                cache.clear();
                cachedFor = id;
            }
            final Typeface ready = cache.get(assetPath);
            if (ready != null) {
                return ready;
            }
        }
        Typeface base;
        try {
            base = id.startsWith("/") ? Typeface.createFromFile(new File(id)) : Typeface.create(id, Typeface.NORMAL);
        } catch (Throwable t) {
            return null;
        }
        if (base == null) {
            return null;
        }
        final boolean bold = assetPath.contains("medium") || assetPath.contains("bold");
        final boolean italic = assetPath.contains("italic");
        final int style = bold && italic ? Typeface.BOLD_ITALIC
                : bold ? Typeface.BOLD
                : italic ? Typeface.ITALIC
                : Typeface.NORMAL;
        Typeface result;
        try {
            result = Typeface.create(base, style);
        } catch (Throwable t) {
            result = base;
        }
        synchronized (cache) {
            cache.put(assetPath, result);
        }
        return result;
    }

    /**
     * Подменяет системные шрифты по умолчанию.
     *
     * Одной ловушки в {@code getTypeface} мало, и это выяснилось на живом
     * телефоне: через неё телеграм берёт только свои файлы, а обычные поля,
     * кнопки и системные окна рисуются шрифтом по умолчанию и мимо неё не
     * проходят. Поэтому здесь подменяются и сами значения по умолчанию.
     *
     * Делается это отражением по внутренностям андроида, и потому — очень
     * осторожно: каждый шаг отдельно и молча. Не вышло ни одного — приложение
     * просто останется со стандартным шрифтом там, где не достали, и ничего
     * не сломается.
     */
    public static void applyGlobally() {
        final Typeface regular = replace("fonts/rregular.ttf");
        if (regular == null) {
            return;
        }
        final Typeface bold = replace("fonts/rmedium.ttf");
        final Typeface italic = replace("fonts/ritalic.ttf");
        final Typeface boldItalic = replace("fonts/rmediumitalic.ttf");

        set("DEFAULT", regular);
        set("DEFAULT_BOLD", bold != null ? bold : regular);
        set("SANS_SERIF", regular);
        set("SERIF", regular);
        set("MONOSPACE", regular);

        // Массив, из которого система берёт шрифт по начертанию.
        try {
            final java.lang.reflect.Field field = Typeface.class.getDeclaredField("sDefaults");
            field.setAccessible(true);
            field.set(null, new Typeface[]{
                    regular,
                    bold != null ? bold : regular,
                    italic != null ? italic : regular,
                    boldItalic != null ? boldItalic : regular
            });
        } catch (Throwable ignored) {
        }

        // Имена семейств — «sans-serif», «monospace» и прочие. Через них шрифт
        // приходит из вёрстки: android:fontFamily в разметке экрана и любой
        // Typeface.create по имени. Пока эта карта не подменена, такие места
        // остаются с системным шрифтом, и выглядит это как «шрифт применился
        // не везде».
        //
        // Поле у этой карты называется по-разному в разных версиях андроида, и
        // на старых её нет вовсе. Поэтому не угадываем одно имя, а пробуем все
        // known и берём то, что и правда оказалось картой имён на шрифты.
        // Промах здесь ничего не стоит: не нашли — остались как были.
        for (String name : new String[]{"sSystemFontMap", "sSystemFallbackMap", "sTypefaceCache"}) {
            poison(name, regular, bold != null ? bold : regular);
        }
    }

    /** Подменяет карту «имя семейства → шрифт», если такая под этим именем есть. */
    private static void poison(String fieldName, Typeface regular, Typeface bold) {
        try {
            final java.lang.reflect.Field field = Typeface.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            final Object value = field.get(null);
            if (!(value instanceof Map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> map = (Map<String, Object>) value;
            // Полужирные имена ведут на полужирный: подменить их обычным
            // начертанием значит развалить заголовки, которые в вёрстке
            // просят именно medium.
            final String[] plain = {"sans-serif", "sans-serif-light", "sans-serif-thin",
                    "sans-serif-condensed", "serif", "monospace", "normal", "default", "roboto"};
            final String[] heavy = {"sans-serif-medium", "sans-serif-black", "roboto-medium"};
            for (String family : plain) {
                replaceEntry(map, family, regular);
            }
            for (String family : heavy) {
                replaceEntry(map, family, bold);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Кладёт шрифт в карту только туда, где уже лежал шрифт.
     *
     * Проверка не лишняя: под теми же именами в разных версиях андроида лежат
     * не Typeface, а внутренние объекты семейств. Положить туда Typeface —
     * значит уронить приложение при первой же отрисовке, причём не у себя, а
     * на чужой версии андроида, которой у меня нет.
     */
    private static void replaceEntry(Map<String, Object> map, String key, Typeface value) {
        try {
            final Object was = map.get(key);
            if (was instanceof Typeface) {
                map.put(key, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void set(String name, Typeface typeface) {
        try {
            final java.lang.reflect.Field field = Typeface.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, typeface);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Перезапуск приложения.
     *
     * Шрифты уже разошлись по чужим кэшам и по нарисованным экранам, и
     * заменить их на лету нечем — поэтому процесс поднимается заново. Человека
     * об этом спрашивают: приложение, которое само себя закрывает без
     * предупреждения, выглядит упавшим.
     */
    public static void restart(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            final Intent intent = activity.getPackageManager()
                    .getLaunchIntentForPackage(activity.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(intent);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        // Даём окну уйти, иначе система покажет чёрный кадр на месте бывшего.
        org.telegram.messenger.AndroidUtilities.runOnUIThread(
                () -> Runtime.getRuntime().exit(0), 300);
    }
}
