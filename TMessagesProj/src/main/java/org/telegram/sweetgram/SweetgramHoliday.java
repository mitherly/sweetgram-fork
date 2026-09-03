package org.telegram.sweetgram;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.util.Calendar;

/**
 * Праздничные темы: сами включаются в свой день и сами уходят после.
 *
 * Каждому празднику — своя тема (готовые .attheme из assets, см.
 * Theme.createDefaultThemes) и своё окно в пару дней вокруг даты. В окно
 * тема ставится сама; после окна возвращается та, что была у человека до
 * праздника. Если человек в праздник сам поставил другую тему — его выбор
 * дороже нашего: праздничная тема больше не навязывается.
 *
 * Проверка стоит на запуске приложения: смена дня под открытым приложением
 * подождёт до следующего. Один запуск в день — это ровно та частота, с
 * которой праздник меняется.
 */
public class SweetgramHoliday {

    /** Один праздник: тема и окно дат, в которое она стоит. */
    private static final class Holiday {
        final String themeName;
        final int month;          // Calendar-месяц начала окна
        final int startDay;
        final int endMonth;       // Calendar-месяц конца окна
        final int endDay;

        Holiday(String themeName, int month, int startDay, int endMonth, int endDay) {
            this.themeName = themeName;
            this.month = month;
            this.startDay = startDay;
            this.endMonth = endMonth;
            this.endDay = endDay;
        }

        /** Попадает ли день в окно. Окно короткое и не переходит через месяц, кроме нового года. */
        boolean today(Calendar now) {
            final int m = now.get(Calendar.MONTH);
            final int d = now.get(Calendar.DAY_OF_MONTH);
            if (m == month && d >= startDay) {
                return true;
            }
            return m == endMonth && d <= endDay;
        }
    }

    private static final Holiday[] HOLIDAYS = {
            // 1 сентября: учебный год.
            new Holiday("Sweetgram 1 сентября", Calendar.AUGUST, 31, Calendar.SEPTEMBER, 1),
            // Хеллоуин.
            new Holiday("Sweetgram Хеллоуин", Calendar.OCTOBER, 30, Calendar.OCTOBER, 31),
            // Новый год: окно через границу года, 31 декабря — 1 января.
            new Holiday("Sweetgram Новый год", Calendar.DECEMBER, 31, Calendar.JANUARY, 1),
            // 23 февраля.
            new Holiday("Sweetgram 23 февраля", Calendar.FEBRUARY, 22, Calendar.FEBRUARY, 23),
            // 8 марта.
            new Holiday("Sweetgram 8 марта", Calendar.MARCH, 7, Calendar.MARCH, 8),
    };

    private static final String PREF_ACTIVE = "holiday_active";
    private static final String PREF_PREV_THEME = "holiday_prev_theme";
    private static final String PREF_PREV_ACCENT = "holiday_prev_accent";

    /** Праздник, чья тема стоит сейчас, или пусто. */
    public static String active() {
        return prefs().getString(PREF_ACTIVE, "");
    }

    private static android.content.SharedPreferences prefs() {
        return org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences(
                SweetgramConfig.PREFS, android.content.Context.MODE_PRIVATE);
    }

    /** Зовётся из LaunchActivity при каждом запуске. */
    public static void onLaunch() {
        try {
            check();
        } catch (Throwable t) {
            // Праздник — не то, ради чего стоит ронять запуск.
            FileLog.e(t);
        }
    }

    private static void check() {
        final Holiday today = current();
        final android.content.SharedPreferences.Editor edit = prefs().edit();
        final String active = active();

        if (!SweetgramConfig.holidayThemes()) {
            // Выключили в праздник — вернём как было и забудем.
            if (!active.isEmpty()) {
                restore();
                edit.putString(PREF_ACTIVE, "").apply();
            }
            return;
        }

        if (today != null) {
            if (!today.themeName.equals(active)) {
                // Новый праздник: запоминаем, что было, и ставим праздничное.
                saveCurrentAsPrevious(edit);
                edit.putString(PREF_ACTIVE, today.themeName);
                edit.apply();
                apply(today.themeName);
                return;
            }
            // Праздник уже стоит. Если человек сам ушёл с неё на другую тему —
            // его выбор, снимаем дежурство и больше не лезем до следующего
            // праздника.
            final Theme.ThemeInfo current = Theme.getCurrentTheme();
            final Theme.ThemeInfo holiday = Theme.getTheme(today.themeName);
            if (current == null || holiday == null || !current.getKey().equals(holiday.getKey())) {
                edit.putString(PREF_ACTIVE, "").apply();
            }
            return;
        }

        // Праздника сегодня нет.
        if (!active.isEmpty()) {
            if (isHolidayTheme(Theme.getCurrentTheme())) {
                restore();
            }
            // Праздничная тема уже не стоит — человек сменил её сам.
            edit.putString(PREF_ACTIVE, "").apply();
        }
    }

    /** Праздник, чьё окно открыто сегодня, или null. */
    private static Holiday current() {
        final Calendar now = Calendar.getInstance();
        for (Holiday holiday : HOLIDAYS) {
            if (holiday.today(now)) {
                return holiday;
            }
        }
        return null;
    }

    private static boolean isHolidayTheme(Theme.ThemeInfo theme) {
        if (theme == null) {
            return false;
        }
        for (Holiday holiday : HOLIDAYS) {
            final Theme.ThemeInfo info = Theme.getTheme(holiday.themeName);
            if (info != null && info.getKey().equals(theme.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static void saveCurrentAsPrevious(android.content.SharedPreferences.Editor edit) {
        try {
            final Theme.ThemeInfo current = Theme.getCurrentTheme();
            if (current == null || isHolidayTheme(current)) {
                return;
            }
            edit.putString(PREF_PREV_THEME, current.getKey());
            edit.putInt(PREF_PREV_ACCENT, current.currentAccentId);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void apply(String themeName) {
        final Theme.ThemeInfo holiday = Theme.getTheme(themeName);
        if (holiday == null) {
            return;
        }
        Theme.applyTheme(holiday);
    }

    private static void restore() {
        try {
            final String key = prefs().getString(PREF_PREV_THEME, null);
            if (key == null || key.isEmpty()) {
                return;
            }
            final Theme.ThemeInfo previous = Theme.getTheme(key);
            if (previous == null) {
                return;
            }
            previous.setCurrentAccentId(prefs().getInt(PREF_PREV_ACCENT, previous.currentAccentId));
            Theme.saveThemeAccents(previous, true, false, true, false);
            Theme.applyTheme(previous);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
