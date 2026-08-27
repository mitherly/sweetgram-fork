package org.telegram.sweetgram;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Свой писатель тегов ID3v2.3 — название, исполнитель, обложка.
 *
 * Почему теги пишутся в файл, а не подставляются при отправке: телеграм,
 * отправляя mp3, сам читает из него теги и складывает их в атрибуты документа,
 * а обложку — в превью. Значит достаточно поправить файл, и всё остальное
 * произойдёт само, тем же кодом, что и обычно. Собирать документ руками
 * означало бы дублировать чужую работу и разойтись с ней на следующей версии.
 *
 * Библиотеки для этого не беру: формат простой, а лишняя зависимость в чужом
 * дереве — это то, что придётся тащить через все будущие обновления.
 */
public class SweetgramTags {

    /** Заголовок ID3v2 занимает десять байт, дальше идут кадры. */
    private static final int HEADER = 10;

    /**
     * Размер в заголовке пишется «безопасными» семибитными байтами: старший бит
     * каждого байта всегда ноль, чтобы кусок тега не притворился началом
     * звукового кадра.
     */
    private static void writeSynchsafe(OutputStream out, int value) throws Exception {
        out.write((value >> 21) & 0x7F);
        out.write((value >> 14) & 0x7F);
        out.write((value >> 7) & 0x7F);
        out.write(value & 0x7F);
    }

    private static void writeInt(OutputStream out, int value) throws Exception {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void frame(ByteArrayOutputStream out, String id, byte[] body) throws Exception {
        out.write(id.getBytes(StandardCharsets.ISO_8859_1));
        writeInt(out, body.length);
        out.write(0);
        out.write(0);
        out.write(body);
    }

    /** Текстовый кадр: признак кодировки, затем UTF-16 с меткой порядка байт. */
    private static byte[] text(String value) throws Exception {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(1);
        body.write(0xFF);
        body.write(0xFE);
        body.write(value.getBytes(StandardCharsets.UTF_16LE));
        return body.toByteArray();
    }

    /** Кадр с картинкой: кодировка, mime, тип (3 — передняя обложка), подпись. */
    private static byte[] picture(byte[] jpeg) throws Exception {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);
        body.write("image/jpeg".getBytes(StandardCharsets.ISO_8859_1));
        body.write(0);
        body.write(3);
        body.write(0);
        body.write(jpeg);
        return body.toByteArray();
    }

    /** Сколько байт в начале файла занимает старый тег, если он там есть. */
    private static int oldTagLength(InputStream in) throws Exception {
        final byte[] head = new byte[HEADER];
        if (in.read(head) != HEADER) {
            return 0;
        }
        if (head[0] != 'I' || head[1] != 'D' || head[2] != '3') {
            return -1;   // тега нет, эти десять байт — уже музыка
        }
        int size = 0;
        for (int i = 6; i < 10; i++) {
            size = (size << 7) | (head[i] & 0x7F);
        }
        return size;
    }

    /**
     * Переписывает src в dst с новыми тегами. Пустые значения пропускаются:
     * пустой кадр и отсутствие кадра — разные вещи, и второе честнее.
     */
    public static boolean write(File src, File dst, String title, String artist, byte[] cover) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {

            final ByteArrayOutputStream frames = new ByteArrayOutputStream();
            if (title != null && !title.isEmpty()) {
                frame(frames, "TIT2", text(title));
            }
            if (artist != null && !artist.isEmpty()) {
                frame(frames, "TPE1", text(artist));
            }
            if (cover != null && cover.length > 0) {
                frame(frames, "APIC", picture(cover));
            }
            final byte[] body = frames.toByteArray();

            out.write("ID3".getBytes(StandardCharsets.ISO_8859_1));
            out.write(3);
            out.write(0);
            out.write(0);
            writeSynchsafe(out, body.length);
            out.write(body);

            final int skip = oldTagLength(in);
            if (skip < 0) {
                // Старого тега не было — те десять байт, что мы уже прочитали,
                // надо вернуть на место, иначе музыка начнётся с середины.
                try (InputStream again = new FileInputStream(src)) {
                    copy(again, out);
                }
            } else {
                long left = skip;
                final byte[] buf = new byte[8192];
                while (left > 0) {
                    final int read = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (read <= 0) {
                        break;
                    }
                    left -= read;
                }
                copy(in, out);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        final byte[] buf = new byte[16384];
        int read;
        while ((read = in.read(buf)) > 0) {
            out.write(buf, 0, read);
        }
    }
}
