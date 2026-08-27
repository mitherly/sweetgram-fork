package org.telegram.sweetgram;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Копирование сообщения вместе с оформлением.
 *
 * Обычное копирование в телеграме кладёт в буфер голый текст: жирный, курсив и
 * ссылки теряются по дороге. Здесь в буфер кладётся два представления сразу —
 * обычный текст и то же самое разметкой HTML. Кто вставит в блокнот, получит
 * текст; кто вставит туда, где разметку понимают, получит оформление.
 *
 * Своё оформление форка остаётся в тексте невидимыми метками: вставишь такое
 * обратно в Sweetgram — оно снова заиграет.
 */
public class SweetgramCopy {

    /** Открывающая или закрывающая метка HTML для этого вида разметки. */
    private static String tag(TLRPC.MessageEntity entity, boolean open) {
        final String name;
        if (entity instanceof TLRPC.TL_messageEntityBold) {
            name = "b";
        } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
            name = "i";
        } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
            name = "u";
        } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
            name = "s";
        } else if (entity instanceof TLRPC.TL_messageEntityCode
                || entity instanceof TLRPC.TL_messageEntityPre) {
            name = "code";
        } else if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
            name = "blockquote";
        } else if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
            // Пары для спойлера в HTML нет. Ближе всего текст цветом фона: он
            // так же не читается, пока не выделишь.
            return open ? "<span style=\"color:transparent;background:#555\">" : "</span>";
        } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
            return open ? "<a href=\"" + escape(((TLRPC.TL_messageEntityTextUrl) entity).url) + "\">" : "</a>";
        } else {
            return null;
        }
        return open ? "<" + name + ">" : "</" + name + ">";
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Собирает HTML по разметке сообщения.
     *
     * Наивный способ — выписать метку на каждый кусок — ломается там, где
     * куски <b>пересекаются, но не вложены</b>: жирный на «АБВГ» и курсив на
     * «ВГДЕ» дают &lt;b&gt;АБ&lt;i&gt;ВГ&lt;/b&gt;ДЕ&lt;/i&gt;, а такую разметку
     * никто не разберёт. Поймано моделью (tools/copy_model.py) до того, как это
     * увидел кто-то живой.
     *
     * Поэтому режем текст по всем границам сразу и держим стопку открытых
     * меток: на каждом отрезке закрываем лишние в обратном порядке и открываем
     * недостающие. При таком порядке разметка закрывается всегда правильно, а
     * пересечение само собой распадается на два куска.
     */
    public static String html(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        if (text == null) {
            return null;
        }
        final List<TLRPC.MessageEntity> usable = new ArrayList<>();
        final TreeSet<Integer> bounds = new TreeSet<>();
        bounds.add(0);
        bounds.add(text.length());
        if (entities != null) {
            for (TLRPC.MessageEntity entity : entities) {
                if (tag(entity, true) != null && entity.offset >= 0 && entity.length > 0
                        && entity.offset + entity.length <= text.length()) {
                    usable.add(entity);
                    bounds.add(entity.offset);
                    bounds.add(entity.offset + entity.length);
                }
            }
        }
        // Порядок вложения: кто начинается раньше — тот снаружи; при общем
        // начале снаружи длинный.
        Collections.sort(usable, (a, b) -> a.offset != b.offset
                ? Integer.compare(a.offset, b.offset)
                : Integer.compare(b.length, a.length));

        final StringBuilder out = new StringBuilder();
        final List<TLRPC.MessageEntity> stack = new ArrayList<>();
        final List<Integer> points = new ArrayList<>(bounds);
        for (int k = 0; k + 1 < points.size(); k++) {
            final int from = points.get(k), to = points.get(k + 1);
            final List<TLRPC.MessageEntity> want = new ArrayList<>();
            for (TLRPC.MessageEntity entity : usable) {
                if (entity.offset <= from && from < entity.offset + entity.length) {
                    want.add(entity);
                }
            }
            int same = 0;
            while (same < stack.size() && same < want.size() && stack.get(same) == want.get(same)) {
                same++;
            }
            while (stack.size() > same) {
                out.append(tag(stack.remove(stack.size() - 1), false));
            }
            for (int i = same; i < want.size(); i++) {
                out.append(tag(want.get(i), true));
                stack.add(want.get(i));
            }
            for (int i = from; i < to; i++) {
                final char c = text.charAt(i);
                if (c == '&') {
                    out.append("&amp;");
                } else if (c == '<') {
                    out.append("&lt;");
                } else if (c == '>') {
                    out.append("&gt;");
                } else if (c == '\n') {
                    out.append("<br>");
                } else {
                    out.append(c);
                }
            }
        }
        while (!stack.isEmpty()) {
            out.append(tag(stack.remove(stack.size() - 1), false));
        }
        return out.toString();
    }


    /**
     * Разметка телеграма значками, как её пишут руками.
     *
     * Это и есть то, что попадает в буфер обычным текстом. HTML понимают
     * далеко не везде, а вот **жирный** телеграм разбирает обратно сам — я
     * взял ровно те значки, которые он ищет при отправке (BOLD_PATTERN и
     * соседние в MediaDataController), чтобы вставленное вернулось тем же
     * оформлением, а не осталось звёздочками.
     *
     * Подчёркиванию значка в телеграме нет, поэтому оно теряется — честнее
     * потерять, чем написать значок, который потом не разберётся.
     */
    private static String token(TLRPC.MessageEntity entity) {
        final String kind;
        final String token;
        if (entity instanceof TLRPC.TL_messageEntityBold) {
            kind = "bold"; token = "**";
        } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
            kind = "italic"; token = "__";
        } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
            kind = "strike"; token = "~~";
        } else if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
            kind = "spoiler"; token = "||";
        } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
            kind = "underline"; token = "++";
        } else if (entity instanceof TLRPC.TL_messageEntityCode) {
            kind = "code"; token = "`";
        } else if (entity instanceof TLRPC.TL_messageEntityPre) {
            kind = "code"; token = "```";
        } else if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
            final boolean collapsed = ((TLRPC.TL_messageEntityBlockquote) entity).collapsed;
            kind = collapsed ? "quote_collapsed" : "quote";
            token = collapsed ? ">>>" : ">>";
        } else {
            return null;
        }
        // Выключенный вид не пишем: значок, который у человека не разбирается,
        // в буфере только мешает.
        return org.telegram.sweetgram.SweetgramConfig.markdownEnabled(kind) ? token : null;
    }

    /**
     * Закрывающий знак. У всех он совпадает с открывающим, у цитаты — зеркальный:
     * «больше» открывает, «меньше» закрывает.
     */
    private static String closeToken(TLRPC.MessageEntity entity) {
        final String open = token(entity);
        if (open == null) {
            return null;
        }
        if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
            return open.replace('>', '<');
        }
        return open;
    }

    /** То же разрезание по границам, что и в HTML, только значками телеграма. */
    public static String markdown(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        if (text == null) {
            return null;
        }
        final List<TLRPC.MessageEntity> usable = new ArrayList<>();
        final TreeSet<Integer> bounds = new TreeSet<>();
        bounds.add(0);
        bounds.add(text.length());
        if (entities != null) {
            for (TLRPC.MessageEntity entity : entities) {
                if (token(entity) != null && entity.offset >= 0 && entity.length > 0
                        && entity.offset + entity.length <= text.length()) {
                    usable.add(entity);
                    bounds.add(entity.offset);
                    bounds.add(entity.offset + entity.length);
                }
            }
        }
        if (usable.isEmpty()) {
            return text.toString();
        }
        Collections.sort(usable, (a, b) -> a.offset != b.offset
                ? Integer.compare(a.offset, b.offset)
                : Integer.compare(b.length, a.length));

        final StringBuilder out = new StringBuilder();
        final List<TLRPC.MessageEntity> stack = new ArrayList<>();
        final List<Integer> points = new ArrayList<>(bounds);
        for (int k = 0; k + 1 < points.size(); k++) {
            final int from = points.get(k), to = points.get(k + 1);
            final List<TLRPC.MessageEntity> want = new ArrayList<>();
            for (TLRPC.MessageEntity entity : usable) {
                if (entity.offset <= from && from < entity.offset + entity.length) {
                    want.add(entity);
                }
            }
            int same = 0;
            while (same < stack.size() && same < want.size() && stack.get(same) == want.get(same)) {
                same++;
            }
            while (stack.size() > same) {
                final String close = closeToken(stack.remove(stack.size() - 1));
                if (close != null) {
                    out.append(close);
                }
            }
            for (int i = same; i < want.size(); i++) {
                final String open = token(want.get(i));
                if (open != null) {
                    out.append(open);
                }
                stack.add(want.get(i));
            }
            out.append(text, from, to);
        }
        while (!stack.isEmpty()) {
            final String close = closeToken(stack.remove(stack.size() - 1));
            if (close != null) {
                out.append(close);
            }
        }
        return out.toString();
    }

    /** Кладёт сообщение в буфер вместе с оформлением. */
    public static void copy(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        final CharSequence plain = message.messageOwner.message;
        if (TextUtils.isEmpty(plain)) {
            return;
        }
        // В буфер кладём два представления. Обычным текстом идёт разметка
        // значками: её телеграм разбирает обратно сам, и вставленное снова
        // становится жирным, а не остаётся звёздочками. Владелец на это и
        // жаловался — HTML понимают далеко не везде.
        final CharSequence asText = markdown(plain, message.messageOwner.entities);
        final String asHtml = html(plain, message.messageOwner.entities);
        if (TextUtils.isEmpty(asHtml)) {
            AndroidUtilities.addToClipboard(asText);
        } else {
            // Через двухдоводный addToClipboard: он не чистит текст, а
            // одинарный нарочно вычищает наши метки — здесь они и есть смысл.
            AndroidUtilities.addToClipboard(asText, asHtml);
        }
    }
}
