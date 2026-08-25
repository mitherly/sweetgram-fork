package org.telegram.margelet;

import org.telegram.messenger.UserConfig;

/**
 * Режим стримера: номер телефона и, если попросят, юзернейм закрываются
 * точками всюду, где приложение их показывает.
 *
 * Настоящего спойлера, как в сообщениях, тут нет и быть не может: он умеет
 * жить только в тех вьюшках телеграма, которые его рисуют, а номер в шапке
 * профиля показывает простой текст. Поэтому замазка.
 *
 * Показа по нажатию нет намеренно. Он тут был и был убран по требованию
 * владельца, и требование правильное: в эфире случайное касание экрана — это
 * ровно тот случай, ради которого режим и включают. Открывается только
 * выключателем в настройках.
 */
public class MargeletPrivacy {

    public static boolean streamer() {
        return MargeletConfig.streamerMode();
    }

    private static boolean own(long userId) {
        return userId != 0 && userId == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    /** Номер телефона, уже отформатированный. userId — чей он. */
    public static String phone(String formatted, long userId) {
        if (formatted == null || !streamer()) {
            return formatted;
        }
        if (!own(userId) && !MargeletConfig.streamerHidesOthers()) {
            return formatted;
        }
        return mask(formatted);
    }

    /** Юзернейм без собачки. */
    public static String username(String name, long userId) {
        if (name == null || !streamer()) {
            return name;
        }
        if (!own(userId) || !MargeletConfig.streamerHidesUsername()) {
            return name;
        }
        return mask(name);
    }

    /**
     * Цифры и буквы заменяются точками, а скобки, пробелы и плюс остаются:
     * так видно, что это номер, и не видно какой. Полностью однородная строка
     * читалась бы как ошибка отрисовки.
     */
    private static String mask(String value) {
        final StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            out.append(Character.isLetterOrDigit(c) ? '•' : c);
        }
        return out.toString();
    }
}
