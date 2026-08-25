package org.telegram.margelet;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

/**
 * Тема по умолчанию — тёмная с зелёным акцентом.
 *
 * Своей темы не рисую: такая уже есть в самом телеграме. «Night» — чёрная
 * основа, а зелёный берётся её же готовым акцентом (0xff4ab841). Из
 * четырнадцати акцентов ночной темы зелёных два; второй, кроме зелёного,
 * красит сообщения в фиолетовый, поэтому взят этот.
 *
 * Ставится ровно один раз, при первом запуске. Если человек потом выберет
 * другую тему, форк в этот выбор больше не лезет.
 */
public class MargeletTheme {

    private static final int GREEN_ACCENT_ID = 2;

    public static void applyOnFirstLaunch() {
        try {
            if (!MargeletConfig.claimFirstLaunch()) {
                return;
            }
            Theme.ThemeInfo night = Theme.getTheme("Night");
            if (night == null) {
                return;
            }
            night.setCurrentAccentId(GREEN_ACCENT_ID);
            Theme.saveThemeAccents(night, true, false, true, false);
            Theme.applyTheme(night);
        } catch (Exception e) {
            // Тема — не то, ради чего стоит ронять запуск.
            FileLog.e(e);
        }
    }
}
