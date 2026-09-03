package org.telegram.sweetgram;

import android.graphics.Color;

import org.telegram.ui.ActionBar.Theme;

/**
 * Свой пузырь: исходящие сообщения красятся выбранным цветом.
 *
 * Видно это только тому, кто включил: цвет подменяется при показе, в сами
 * сообщения ничего не дописывается и никуда не отправляется. Собеседник
 * видит обычные пузыри своей темы.
 *
 * Перехват идёт по ключу цвета — тем же способом, каким это делают «приступ»
 * и Margy у себя. Тема при этом не трогается: выключил — и всё вернулось
 * само, чинить нечего.
 *
 * С цветом пузыря обязана меняться и вся его начинка: галочки, часы, время,
 * ответы, ссылки, полосы голосовых. Если пузырь стал тёмным, а текст остался
 * тёмным — читать нечего. Поэтому цвет текста здесь свой: чёрный или белый
 * по яркости выбранного, те же правила, которыми телеграм красит свои
 * градиентные пузыри.
 */
public class SweetgramBubbles {

    /** Кэш флага: цвет спрашивают тысячи раз за кадр, prefs на таком пути нельзя. */
    private static Boolean cachedOn;
    private static boolean cachedColors;
    private static int plain, selected, top, bottom, ink, inkSub, seekbar;

    public static boolean on() {
        if (cachedOn == null) {
            try {
                cachedOn = SweetgramConfig.ownBubbleOn();
            } catch (Throwable t) {
                cachedOn = false;
            }
        }
        return cachedOn;
    }

    /** Настройки поменяли — кэш надо бы сбросить. */
    public static void reset() {
        cachedOn = null;
        cachedColors = false;
    }

    private static void prepare() {
        if (cachedColors) {
            return;
        }
        cachedColors = true;
        final int c1 = SweetgramConfig.ownBubbleColor1();
        final int c2 = SweetgramConfig.ownBubbleColor2();
        if (c2 == 0 || c2 == c1) {
            // Один цвет: без перехода.
            top = bottom = plain = c1 | 0xFF000000;
        } else {
            top = c1 | 0xFF000000;
            bottom = c2 | 0xFF000000;
            plain = mix(top, bottom, 0.5f);
        }
        // Выделенное — заметно, но не «другой цвет»: сдвигаем к белому или к
        // чёрному, смотря что читается на этом фоне.
        final double r = Color.red(plain) / 255.0;
        final double g = Color.green(plain) / 255.0;
        final double b = Color.blue(plain) / 255.0;
        final double perceivedBrightness = 0.299 * r + 0.587 * g + 0.114 * b;
        final boolean blackText = perceivedBrightness > 0.705;
        selected = blackText ? mix(plain, Color.BLACK, 0.16f) : mix(plain, Color.WHITE, 0.18f);
        // Текст: чёрный на светлом, белый на тёмном — по воспринимаемой
        // яркости, те же правила, которыми телеграм красит свои градиентные
        // пузыри.
        ink = blackText ? 0xff000000 : 0xffffffff;
        inkSub = blackText ? 0xff555555 : 0xffeeeeee;
        seekbar = blackText ? 0x4d000000 : 0x4dffffff;
    }

    private static int mix(int a, int b, float t) {
        final int r = Math.round(Color.red(a) * (1 - t) + Color.red(b) * t);
        final int g = Math.round(Color.green(a) * (1 - t) + Color.green(b) * t);
        final int bl = Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return Color.argb(255, r, g, bl);
    }

    /** Ключи, которыми красится сам исходящий пузырь. */
    private static boolean bubbleKey(int key) {
        return key == Theme.key_chat_outBubble
                || key == Theme.key_chat_outBubbleSelected
                || key == Theme.key_chat_outBubbleGradient1;
    }

    /** Ключи, красящиеся цветом текста (или его приглушённым вариантом). */
    private static boolean inkSubKey(int key) {
        return key == Theme.key_chat_outAudioDurationText
                || key == Theme.key_chat_outAudioDurationSelectedText
                || key == Theme.key_chat_outContactPhoneText
                || key == Theme.key_chat_outContactPhoneSelectedText
                || key == Theme.key_chat_outVenueInfoText
                || key == Theme.key_chat_outVenueInfoSelectedText
                || key == Theme.key_chat_outFileInfoText
                || key == Theme.key_chat_outFileInfoSelectedText;
    }

    /** Ключи полос и прогрессов: полупрозрачная версия цвета текста. */
    private static boolean seekbarKey(int key) {
        return key == Theme.key_chat_outAudioProgress
                || key == Theme.key_chat_outAudioSelectedProgress
                || key == Theme.key_chat_outAudioSeekbar
                || key == Theme.key_chat_outAudioSeekbarSelected
                || key == Theme.key_chat_outAudioCacheSeekbar
                || key == Theme.key_chat_outVoiceSeekbar
                || key == Theme.key_chat_outVoiceSeekbarSelected;
    }

    private static boolean inkKey(int key) {
        if (inkSubKey(key) || seekbarKey(key)) {
            return false;
        }
        return key == Theme.key_chat_messageTextOut
                || key == Theme.key_chat_messageLinkOut
                || key == Theme.key_chat_outForwardedNameText
                || key == Theme.key_chat_outViaBotNameText
                || key == Theme.key_chat_outReplyLine
                || key == Theme.key_chat_outReplyLine2
                || key == Theme.key_chat_outReplyNameText
                || key == Theme.key_chat_outReplyMessageText
                || key == Theme.key_chat_outReplyMediaMessageText
                || key == Theme.key_chat_outReplyMediaMessageSelectedText
                || key == Theme.key_chat_outPreviewLine
                || key == Theme.key_chat_outSiteNameText
                || key == Theme.key_chat_outInstant
                || key == Theme.key_chat_outInstantSelected
                || key == Theme.key_chat_outPreviewInstantText
                || key == Theme.key_chat_outViews
                || key == Theme.key_chat_outViewsSelected
                || key == Theme.key_chat_outAudioTitleText
                || key == Theme.key_chat_outFileNameText
                || key == Theme.key_chat_outContactNameText
                || key == Theme.key_chat_outAudioPerformerText
                || key == Theme.key_chat_outAudioPerformerSelectedText
                || key == Theme.key_chat_outSentCheck
                || key == Theme.key_chat_outSentCheckSelected
                || key == Theme.key_chat_outSentCheckRead
                || key == Theme.key_chat_outSentCheckReadSelected
                || key == Theme.key_chat_outSentClock
                || key == Theme.key_chat_outSentClockSelected
                || key == Theme.key_chat_outMenu
                || key == Theme.key_chat_outMenuSelected
                || key == Theme.key_chat_outTimeText
                || key == Theme.key_chat_outTimeSelectedText
                || key == Theme.key_chat_outLoader
                || key == Theme.key_chat_outLoaderSelected
                || key == Theme.key_chat_outAudioSeekbarFill
                || key == Theme.key_chat_outVoiceSeekbarFill;
    }

    /**
     * Цвет для ключа или ноль, если этот ключ трогать не надо.
     *
     * Зовётся из Theme.getColor, то есть на самом горячем пути приложения:
     * поэтому сначала один флаг, и только потом всё остальное.
     */
    public static int colorFor(int key) {
        if (!on()) {
            return 0;
        }
        prepare();
        if (key == Theme.key_chat_outBubbleSelected) {
            return selected;
        }
        if (key == Theme.key_chat_outBubbleGradient1) {
            return top;
        }
        if (key == Theme.key_chat_outBubble) {
            return bottom;
        }
        if (inkSubKey(key)) {
            return inkSub;
        }
        if (seekbarKey(key)) {
            return seekbar;
        }
        if (inkKey(key)) {
            return ink;
        }
        return 0;
    }
}
