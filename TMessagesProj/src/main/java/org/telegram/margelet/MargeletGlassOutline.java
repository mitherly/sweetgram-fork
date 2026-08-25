package org.telegram.margelet;

/**
 * Стили обводки стекла (Glass Outline Style).
 */
public class MargeletGlassOutline {
    public static final int GLARE = 0;   // Блик (по умолчанию)
    public static final int SOLID = 1;   // Сплошная линия
    public static final int HIDDEN = 2;  // Скрыта (без обводки)

    public static int getStyle() {
        return MargeletConfig.glassOutlineStyle();
    }
}
