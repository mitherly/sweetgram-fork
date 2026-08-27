package org.telegram.sweetgram;

/**
 * Подмена имени в текстах интерфейса.
 *
 * Слово «Telegram» встречается не только как название приложения: «Telegram
 * Premium», «Telegram Stars», «Telegram Business» — это платные услуги
 * самого телеграма, и переименовывать их во что-то своё было бы враньём:
 * человек платит телеграму, а не нам. Поэтому такие связки оставляем как
 * есть, а одиночное имя заменяем.
 *
 * Ссылки не трогаем: в них имя пишется строчными (telegram.org), а мы ищем
 * слово с большой буквы.
 */
public class SweetgramStrings {

    private static final String NAME = "Telegram";

    /** Что после имени означает «это их услуга, не переименовывать». */
    private static final String[] KEEP_AFTER = {
            " Premium", " Stars", " Business", " Wallet", " Passport", " Ads", " API", " FAQ"
    };

    public static String rename(String value) {
        if (value == null || value.indexOf(NAME) < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        int from = 0;
        while (true) {
            int at = value.indexOf(NAME, from);
            if (at < 0) {
                out.append(value, from, value.length());
                break;
            }
            int end = at + NAME.length();
            // Часть более длинного слова (Telegrammer и подобное) не трогаем.
            boolean wordEnd = end >= value.length() || !Character.isLetterOrDigit(value.charAt(end));
            boolean wordStart = at == 0 || !Character.isLetterOrDigit(value.charAt(at - 1));
            boolean keep = !wordStart || !wordEnd;
            if (!keep) {
                for (String tail : KEEP_AFTER) {
                    if (value.startsWith(tail, end)) {
                        keep = true;
                        break;
                    }
                }
            }
            out.append(value, from, at).append(keep ? NAME : SweetgramConfig.APP_NAME);
            from = end;
        }
        return out.toString();
    }
}
