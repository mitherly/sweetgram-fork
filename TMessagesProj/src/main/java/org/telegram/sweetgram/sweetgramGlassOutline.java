package org.telegram.sweetgram;

/**
 * Стили обводки стекла (Glass Outline Style).
 */
public class SweetgramGlassOutline {
    public static final int GLARE = 0;   // Блик (по умолчанию)
    public static final int SOLID = 1;   // Сплошная линия
    public static final int HIDDEN = 2;  // Скрыта (без обводки)

    public static int getStyle() {
        return SweetgramConfig.glassOutlineStyle();
    }
}
