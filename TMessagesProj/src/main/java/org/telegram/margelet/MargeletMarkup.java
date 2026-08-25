package org.telegram.margelet;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Своё оформление текста поверх телеграмовского.
 *
 * Телеграм умеет жирный, курсив, зачёркнутый и ещё несколько видов — и список
 * этот закрыт: он лежит на сервере. Дописать в него «размер» нельзя. Поэтому
 * оформление едет прямо в тексте сообщения, невидимыми знаками, а разбирает их
 * уже сам форк.
 *
 * <b>Формат.</b> Кусок текста обёрнут парой меток:
 * <pre>
 *     ОТКРЫТЬ вид значение ... текст ... ЗАКРЫТЬ
 * </pre>
 * Все четыре знака — селекторы начертания (U+FE00…U+FE0F). Это служебный
 * диапазон юникода: он ничего не рисует и в обычном клиенте не виден вовсе.
 * Поэтому у человека без форка сообщение выглядит не «текстом с сором», а
 * просто текстом без оформления.
 *
 * <b>Почему не видимые символы.</b> Владелец предполагал видимые. Невидимые
 * лучше ровно тем, что не портят чтение посторонним: реклама форка и так стоит
 * заголовком, а засорять чужой экран сверх этого незачем. Смена на видимые —
 * это другие значения четырёх констант ниже.
 *
 * <b>Чего этот формат не умеет.</b> Он ничего не подтверждает и не защищает:
 * любой может поставить те же знаки руками. Это оформление, а не подпись.
 */
public class MargeletMarkup {

    /**
     * Знаки меток. Все пять — невидимые служебные знаки из одного места:
     * U+2060 «склейка слов» и четыре невидимых математических знака за ним.
     *
     * Сначала я взял под метки селекторы начертания U+FE00…U+FE0F. Это была
     * ошибка, и нашёл её владелец: чужие сообщения приходили без оформления,
     * хотя своё оставалось. Причина в разборе эмодзи — он этот диапазон
     * <b>съедает</b> целиком, считая его частью значка (Emoji.java, ветки
     * «c >= 0xFE00 && c <= 0xFE0F»). Своё сообщение выживало потому, что у
     * него разметка оставалась в поле ввода и заново не разбиралась.
     */
    public static final char OPEN = '\u2060';
    public static final char CLOSE = '\u2061';
    private static final char TRIT = '\u2062';
    private static final int TRITS = 3;

    /** Вид — два троичных разряда, значение — три. Итого шесть знаков на метку. */
    private static final int KIND_TRITS = 2;
    private static final int VALUE_TRITS = 3;
    private static final int MARK_LEN = 1 + KIND_TRITS + VALUE_TRITS;
    private static final int DIGITS = 14;

    public static final int KIND_SIZE = 0;
    public static final int KIND_DIM = 1;
    public static final int KIND_RAINBOW = 2;
    /** Кнопка: подпись видна текстом, ссылка едет в нагрузке метки. */
    public static final int KIND_BUTTON = 3;
    /** Премиум-значок: в нагрузке номер документа. */
    public static final int KIND_EMOJI = 4;
    /** Обводка: буквы становятся полыми, наружу идёт контур цветом текста. */
    public static final int KIND_OUTLINE = 5;

    /**
     * Длина нагрузки в разрядах и разрядов на байт.
     *
     * Нагрузка нужна не всем видам: у размера и радуги хватает значения, а
     * кнопке нужна ссылка, значку — его номер. Поэтому у метки переменная
     * длина: сначала пять разрядов длины, потом байты по шесть разрядов
     * (три в шестой степени — 729, любой байт помещается).
     */
    private static final int LEN_TRITS = 5;
    private static final int BYTE_TRITS = 6;

    private static boolean hasPayload(int kind) {
        return kind == KIND_BUTTON || kind == KIND_EMOJI;
    }

    /**
     * Заголовок, который форк дописывает в начало оформленного сообщения.
     * В самом форке он спрятан, у остальных виден — так и задумано владельцем.
     */
    public static final String HEADER = "<! Message looks better with @margeletter! >";

    /** Размер: от 0,6 до 2,0 обычного. Границы жёсткие с обеих сторон. */
    private static final float SIZE_MIN = 0.6f;
    private static final float SIZE_MAX = 2.0f;

    public static float sizeOf(int value) {
        final int v = Math.max(0, Math.min(DIGITS - 1, value));
        return SIZE_MIN + (SIZE_MAX - SIZE_MIN) * v / (DIGITS - 1);
    }

    /** Ближайшее значение шкалы к нужному множителю. */
    public static int sizeValue(float scale) {
        final float clamped = Math.max(SIZE_MIN, Math.min(SIZE_MAX, scale));
        return Math.round((clamped - SIZE_MIN) * (DIGITS - 1) / (SIZE_MAX - SIZE_MIN));
    }

    private static boolean isTrit(char c) {
        return c >= TRIT && c < TRIT + TRITS;
    }

    /** Число троичными разрядами, младший первым. */
    private static void number(StringBuilder out, int value, int count) {
        int left = Math.max(0, value);
        for (int i = 0; i < count; i++) {
            out.append((char) (TRIT + left % TRITS));
            left /= TRITS;
        }
    }

    private static int number(CharSequence text, int at, int count) {
        int value = 0, mul = 1;
        for (int i = 0; i < count; i++) {
            value += (text.charAt(at + i) - TRIT) * mul;
            mul *= TRITS;
        }
        return value;
    }

    /** Открывающая метка как строка. */
    public static String open(int kind, int value) {
        return open(kind, value, null);
    }

    public static String open(int kind, int value, byte[] payload) {
        final StringBuilder out = new StringBuilder();
        out.append(OPEN);
        number(out, kind, KIND_TRITS);
        number(out, value, VALUE_TRITS);
        if (hasPayload(kind)) {
            final byte[] bytes = payload == null ? new byte[0] : payload;
            final int length = Math.min(bytes.length, pow(TRITS, LEN_TRITS) - 1);
            number(out, length, LEN_TRITS);
            for (int i = 0; i < length; i++) {
                number(out, bytes[i] & 0xFF, BYTE_TRITS);
            }
        }
        return out.toString();
    }

    private static int pow(int base, int power) {
        int result = 1;
        for (int i = 0; i < power; i++) {
            result *= base;
        }
        return result;
    }

    /** Нагрузка как строка в UTF-8, или пусто. */
    public static String payloadOf(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        try {
            return new String(payload, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Приводит написанное человеком к настоящей ссылке.
     *
     * Если это не «http://» и не «https://», считаем, что назвали человека или
     * канал: «@ник» и просто «ник» превращаются в t.me. Так короче и так его
     * и просили.
     */
    public static String link(String written) {
        if (written == null) {
            return "";
        }
        final String text = written.trim();
        if (text.isEmpty()) {
            return "";
        }
        final String lower = text.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return text;
        }
        if (lower.startsWith("t.me/") || lower.startsWith("telegram.me/")) {
            return "https://" + text;
        }
        return "https://t.me/" + (text.startsWith("@") ? text.substring(1) : text);
    }

    public static byte[] bytesOf(String text) {
        if (text == null) {
            return new byte[0];
        }
        try {
            return text.getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static String close() {
        return String.valueOf(CLOSE);
    }

    /** Найденный кусок оформления. */
    public static final class Run {
        public final int kind;
        public final int value;
        public final int start;
        public final int end;
        /** Ссылка у кнопки, номер документа у значка. Иначе пусто. */
        public final byte[] payload;

        Run(int kind, int value, int start, int end, byte[] payload) {
            this.kind = kind;
            this.value = value;
            this.start = start;
            this.end = end;
            this.payload = payload;
        }

        public String text() {
            return payloadOf(payload);
        }
    }

    /**
     * Разбирает метки в тексте.
     *
     * Отсчёты возвращаются <b>по исходному тексту, вместе с метками</b> — их
     * никто не вырезает. Причина простая: отсчёты жирного и курсива приходят с
     * сервера и посчитаны по тому же тексту. Вырежешь четыре знака в начале — и
     * весь остальной разбор уедет.
     */
    public static List<Run> parse(CharSequence text) {
        final List<Run> runs = new ArrayList<>();
        if (text == null || text.length() < 4) {
            return runs;
        }
        // Метки могут вкладываться друг в друга, поэтому открытые куски
        // держим стопкой: закрывающий знак закрывает последний открытый.
        final ArrayList<Object[]> open = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == OPEN && i + MARK_LEN <= text.length() && allTrits(text, i + 1)) {
                final int kind = number(text, i + 1, KIND_TRITS);
                final int value = number(text, i + 1 + KIND_TRITS, VALUE_TRITS);
                int after = i + MARK_LEN;
                byte[] payload = new byte[0];
                if (hasPayload(kind)) {
                    if (after + LEN_TRITS > text.length() || !allTrits(text, after, LEN_TRITS)) {
                        continue;   // метка обрезана — считаем её мусором
                    }
                    final int length = number(text, after, LEN_TRITS);
                    after += LEN_TRITS;
                    if (after + length * BYTE_TRITS > text.length()
                            || !allTrits(text, after, length * BYTE_TRITS)) {
                        continue;
                    }
                    payload = new byte[length];
                    for (int b = 0; b < length; b++) {
                        payload[b] = (byte) number(text, after + b * BYTE_TRITS, BYTE_TRITS);
                    }
                    after += length * BYTE_TRITS;
                }
                open.add(new Object[]{kind, value, after, payload});
                i = after - 1;
            } else if (c == CLOSE && !open.isEmpty()) {
                final Object[] top = open.remove(open.size() - 1);
                final int start = (Integer) top[2];
                if (i > start) {
                    runs.add(new Run((Integer) top[0], (Integer) top[1], start, i, (byte[]) top[3]));
                }
            }
        }
        return runs;
    }

    private static boolean allTrits(CharSequence text, int at) {
        return allTrits(text, at, MARK_LEN - 1);
    }

    private static boolean allTrits(CharSequence text, int at, int count) {
        if (at + count > text.length()) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (!isTrit(text.charAt(at + i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean has(CharSequence text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == OPEN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Превращает разметку в тексте поля ввода в метки перед отправкой.
     *
     * Идём с конца: вставка сдвигает всё, что правее, а то, что левее, стоит на
     * месте. Так отсчёты не нужно пересчитывать ни разу.
     */
    public static CharSequence encode(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return text;
        }
        final Spanned spanned = (Spanned) text;
        final MargeletSpans.Base[] all = spanned.getSpans(0, spanned.length(), MargeletSpans.Base.class);
        final ArrayList<MargeletSpans.Base> fresh = new ArrayList<>();
        for (MargeletSpans.Base span : all) {
            if (!span.decoded) {
                fresh.add(span);
            }
        }
        final MargeletSpans.Base[] spans = fresh.toArray(new MargeletSpans.Base[0]);
        final boolean hasEmoji = spanned.getSpans(0, spanned.length(),
                org.telegram.ui.Components.AnimatedEmojiSpan.class).length > 0;
        if (spans.length == 0 && !hasEmoji) {
            return text;
        }
        final SpannableStringBuilder out = new SpannableStringBuilder(text);
        // Сначала все границы, потом вставка — иначе порядок вставок зависит
        // от того, в каком порядке система вернула разметку.
        // Метка: позиция, открывающая?, начало куска, конец куска, вид, значение.
        final ArrayList<int[]> marks = new ArrayList<>();
        final ArrayList<byte[]> payloads = new ArrayList<>();
        for (MargeletSpans.Base span : spans) {
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            if (start < 0 || end <= start) {
                continue;
            }
            marks.add(new int[]{start, 1, start, end, span.kind(), span.value(), payloads.size()});
            marks.add(new int[]{end, 0, start, end, span.kind(), span.value(), payloads.size()});
            payloads.add(span.payload());
            // Оформление теперь живёт в метках. Спан снимаем, иначе второй
            // проход по тому же тексту напишет метки ещё раз: подписей у
            // одного отправления бывает несколько.
            out.removeSpan(span);
        }

        // Премиум-значки: у них своя разметка от телеграма, не наша. Меняем её
        // на нашу метку с номером документа и убираем — иначе при отправке
        // соберётся телеграмовская пометка, а сервер её у не-премиума не
        // пропустит и не отправится вообще ничего.
        final org.telegram.ui.Components.AnimatedEmojiSpan[] emoji =
                out.getSpans(0, out.length(), org.telegram.ui.Components.AnimatedEmojiSpan.class);
        for (org.telegram.ui.Components.AnimatedEmojiSpan span : emoji) {
            final int start = out.getSpanStart(span);
            final int end = out.getSpanEnd(span);
            out.removeSpan(span);
            if (start < 0 || end <= start) {
                continue;
            }
            marks.add(new int[]{start, 1, start, end, KIND_EMOJI, 0, payloads.size()});
            marks.add(new int[]{end, 0, start, end, KIND_EMOJI, 0, payloads.size()});
            payloads.add(bytesOf(Long.toString(span.getDocumentId())));
        }
        // Порядок вставки тут не «как удобнее», а единственно верный, и обе
        // тонкости я сначала сделал наоборот. Обе поймала отдельная модель
        // формата на питоне (tools/markup_model.py) до сборки.
        //
        // Идём с конца текста, чтобы не пересчитывать отсчёты. Вставка в одну и
        // ту же точку переворачивает порядок, отсюда всё остальное:
        //   — открывающие раньше закрывающих, иначе два куска встык склеятся;
        //   — среди открывающих в одной точке первым идёт короткий кусок: он
        //     ляжет внутрь длинного, а не наоборот;
        //   — среди закрывающих в одной точке первым идёт внешний, тогда
        //     внутренний закроется раньше него.
        Collections.sort(marks, (a, b) -> {
            if (a[0] != b[0]) {
                return Integer.compare(b[0], a[0]);
            }
            if (a[1] != b[1]) {
                return Integer.compare(b[1], a[1]);
            }
            return a[1] == 1 ? Integer.compare(a[3], b[3]) : Integer.compare(a[2], b[2]);
        });
        for (int[] mark : marks) {
            out.insert(mark[0], mark[1] == 1
                    ? open(mark[4], mark[5], payloads.get(mark[6]))
                    : close());
        }
        // Заголовок стоит в конце, а не в начале: из начала он лезет в
        // уведомления и в список чатов, где от сообщения видна одна строка.
        // Его можно выключить — но выключается он с просьбой, а не молча:
        // просьба честная, а решение всё равно за человеком.
        if (!MargeletConfig.watermarkOnSend()) {
            return out;
        }
        return out.append("\n").append(HEADER);
    }

    /**
     * Дописывает кнопки в список разметки сообщения обычной ссылкой телеграма.
     *
     * Три раза подряд я пытался сделать нажимаемую кнопку своей разметкой — и
     * три раза не угадал, почему она не нажимается. Плашка при этом рисовалась,
     * то есть до сообщения всё доходило; мёртвой была именно ссылочная часть.
     *
     * Поэтому теперь ссылку не делаю я. Я лишь дописываю в список разметки
     * обычную «ссылку с текстом», а дальше телеграм строит её сам — тем же
     * кодом, которым строит любую ссылку в любом сообщении. Если она не
     * нажмётся, значит не нажимаются вообще все ссылки.
     *
     * Вызывается до разбора разметки, поэтому дописанное успевает попасть в
     * обработку. Повторный вызов на том же сообщении ничего не удваивает.
     */
    public static void injectEntities(CharSequence text, java.util.ArrayList<org.telegram.tgnet.TLRPC.MessageEntity> entities) {
        if (text == null || entities == null || !has(text)) {
            return;
        }
        for (Run run : parse(text)) {
            if (run.kind != KIND_BUTTON || !MargeletConfig.markupEnabled(KIND_BUTTON)) {
                continue;
            }
            final String url = run.text();
            if (url.isEmpty()) {
                continue;
            }
            // Отсчёты считаем до проверки на повтор: проверять надо ровно то,
            // что положим, иначе на каждом заходе будет добавляться ещё одна
            // такая же ссылка.
            final int offset = Math.max(0, run.start - 1);
            final int length = Math.min(text.length() - offset,
                    run.end - run.start + (run.start - offset) + 1);
            boolean already = false;
            for (org.telegram.tgnet.TLRPC.MessageEntity entity : entities) {
                if (entity instanceof org.telegram.tgnet.TLRPC.TL_messageEntityTextUrl
                        && entity.offset == offset && entity.length == length) {
                    already = true;
                    break;
                }
            }
            if (already) {
                continue;
            }
            final org.telegram.tgnet.TLRPC.TL_messageEntityTextUrl link =
                    new org.telegram.tgnet.TLRPC.TL_messageEntityTextUrl();
            // Ссылка шире подписи на один знак с каждой стороны — на
            // невидимые метки, стоящие вплотную. Телеграм переводит точку
            // касания в отсчёт знака, и на краю плашки этот отсчёт попадает
            // на границу куска, а не внутрь; растянутый кусок ловит и край.
            link.offset = offset;
            link.length = length;
            link.url = url;
            entities.add(link);
        }
    }

    /** Вешает оформление по меткам. Текст не меняется, меняется только вид. */
    public static void apply(Spannable text) {
        if (text == null) {
            return;
        }
        for (Run run : parse(text)) {
            if (!MargeletConfig.markupEnabled(run.kind)) {
                continue;   // этот вид оформления человек выключил у себя
            }
            if (run.kind == KIND_BUTTON) {
                final String url = run.text();
                text.setSpan(new MargeletSpans.Button(run.value, url),
                        run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            if (run.kind == KIND_OUTLINE) {
                // Обводку вешаем по одному слову, а не на весь кусок целиком.
                //
                // Рисуется она отдельным куском разметки, а такой кусок для
                // переноса неделим: андроид переносит строку между кусками, но
                // не внутри. Поэтому обводка на длинной фразе оставалась одной
                // неразрывной строкой и вылезала за пузырь — в отличие от
                // размера и остальных, которые переносятся сами.
                //
                // Разбив по словам, мы возвращаем переносу его обычные места:
                // пробелы. Выглядит так же, ведёт себя как обычный текст.
                int i = run.start;
                while (i < run.end) {
                    while (i < run.end && Character.isWhitespace(text.charAt(i))) {
                        i++;
                    }
                    final int word = i;
                    while (i < run.end && !Character.isWhitespace(text.charAt(i))) {
                        i++;
                    }
                    if (i > word) {
                        text.setSpan(new MargeletSpans.Outline(run.value), word, i,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                continue;
            }
            if (run.kind == KIND_EMOJI) {
                try {
                    final long id = Long.parseLong(run.text());
                    text.setSpan(new org.telegram.ui.Components.AnimatedEmojiSpan(id, null),
                            run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception ignored) {
                    // Номер не разобрался — оставляем запасной значок как есть.
                }
                continue;
            }
            final Object span = MargeletSpans.create(run.kind, run.value);
            if (span != null) {
                if (span instanceof MargeletSpans.Base) {
                    // Метки этого куска уже в тексте: при отправке их не
                    // ставить заново.
                    ((MargeletSpans.Base) span).decoded = true;
                }
                text.setSpan(span, run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if (!MargeletConfig.showWatermarks()) {
            hideHeader(text);
        }
    }

    /**
     * Прячет заголовок с рекламой форка внутри самого форка.
     *
     * Именно прячет, а не вырезает: вырезание сдвинуло бы отсчёты жирного,
     * курсива и ссылок, которые пришли с сервера и посчитаны по тексту
     * вместе с заголовком.
     */
    private static void hideHeader(Spannable text) {
        final int at = indexOf(text, HEADER);
        if (at < 0) {
            return;
        }
        // Перевод строки перед заголовком прячем вместе с ним, иначе от
        // спрятанной строки останется пустая.
        int start = at;
        if (start > 0 && text.charAt(start - 1) == '\n') {
            start--;
        }
        int end = at + HEADER.length();
        if (end < text.length() && text.charAt(end) == '\n') {
            end++;
        }
        text.setSpan(new MargeletSpans.Hidden(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * Убирает метки и строку со ссылкой на форк.
     *
     * Нужно при обычном копировании: человек копирует текст, а не наши
     * служебные знаки. Оформление уносит отдельный пункт «копировать с
     * оформлением» — там знаки как раз нужны.
     */
    public static CharSequence strip(CharSequence text) {
        if (text == null || text.length() == 0) {
            return text;
        }
        final SpannableStringBuilder out = new SpannableStringBuilder(text);
        int at = indexOf(out, HEADER);
        if (at >= 0) {
            int start = at;
            if (start > 0 && out.charAt(start - 1) == '\n') {
                start--;
            }
            int end = at + HEADER.length();
            if (end < out.length() && out.charAt(end) == '\n') {
                end++;
            }
            out.delete(start, end);
        }
        // Идём с конца: удаление сдвигает всё, что правее.
        for (int i = out.length() - 1; i >= 0; i--) {
            final char c = out.charAt(i);
            if (c == OPEN || c == CLOSE || isTrit(c)) {
                out.delete(i, i + 1);
            }
        }
        return out;
    }

    private static int indexOf(CharSequence text, String what) {
        final int limit = text.length() - what.length();
        outer:
        for (int i = 0; i <= limit; i++) {
            for (int j = 0; j < what.length(); j++) {
                if (text.charAt(i + j) != what.charAt(j)) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
