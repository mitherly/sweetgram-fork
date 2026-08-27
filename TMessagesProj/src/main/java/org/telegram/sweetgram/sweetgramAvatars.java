package org.telegram.sweetgram;

import org.telegram.messenger.AndroidUtilities;

/**
 * Расчет радиуса скругления аватарок по настройкам Sweetgram.
 */
public class SweetgramAvatars {

    public static int getAvatarCorners(float size) {
        return getAvatarCorners(size, false);
    }

    public static int getAvatarCorners(float size, boolean isForum) {
        int mode = SweetgramConfig.avatarRadius();
        if (mode == 0) { // Обычные круглые
            if (isForum) {
                return AndroidUtilities.dp(size * 0.33f);
            }
            return AndroidUtilities.dp(size / 2.0f);
        } else if (mode == 1) { // Квадратные
            return 0;
        } else if (mode == 2) { // Слегка скругленные (Squircle)
            return AndroidUtilities.dp(size * 0.22f);
        } else if (mode == 3) { // Средне скругленные
            return AndroidUtilities.dp(size * 0.35f);
        }
        return AndroidUtilities.dp(size / 2.0f);
    }
}
