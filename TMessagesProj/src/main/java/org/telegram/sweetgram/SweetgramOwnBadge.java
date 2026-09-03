package org.telegram.sweetgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Свой значок: человек выбирает сам, видят все.
 *
 * Значки нарисованы здесь же: у каждого — свой цвет поля и свой белый глиф
 * (путь в координатах 24×24, начерчен руками в этом файле). Каталог
 * фиксированный — значок это украшение, а не достижение, но ставить можно
 * любой, когда хочешь.
 *
 * Лежит значок не у нас, а в общей группе баннеров ({@link SweetgramBannerGroup}):
 * человек отправляет туда сообщение с меткой, и по автору сообщения телеграм
 * сам гарантирует, чей это значок. То же доверие, на котором работают баннеры
 * и стена, только значок — текст, и стоит он одного сообщения.
 *
 * Сменить значок — отправить новый; убрать — удалить своё сообщение. И то и
 * другое человек может сделать руками прямо в группе.
 */
public class SweetgramOwnBadge {

    /** Метка в группе: число следом — какой это значок из каталога. */
    public static final String TAG = "#sweetgram_badge";

    private static final Pattern BADGE_IN_TEXT =
            Pattern.compile(Pattern.quote(TAG) + "\\s*\\n?\\s*(\\d+)");

    /**
     * Один значок каталога.
     *
     * Глиф — путь в сетке 24×24, белый; поле красится своим цветом. Название
     * и описание берутся строками приложения, а не запоминаются заранее:
     * человек может переключить язык, не выходя. Номера значков — их id,
     * менять после выпуска нельзя: номера уже уехали в группу в чужих
     * сообщениях.
     */
    public static final class Kind {
        public final int id;
        public final int color;
        public final int titleRes;
        public final int aboutRes;
        public final String pathData;

        Kind(int id, int color, int titleRes, int aboutRes, String pathData) {
            this.id = id;
            this.color = color;
            this.titleRes = titleRes;
            this.aboutRes = aboutRes;
            this.pathData = pathData;
        }

        public String title() {
            return org.telegram.messenger.LocaleController.getString(titleRes);
        }

        public String about() {
            return org.telegram.messenger.LocaleController.getString(aboutRes);
        }
    }

    /** Каталог. Порядок — порядок показа в галерее. */
    private static final Kind[] KINDS = {
            new Kind(1, 0xFFE85D9E, R.string.sg_badge_1_title, R.string.sg_badge_1_about,
                    "M12,20.5 C8.5,17.5 3.5,13.5 3.5,9 C3.5,6.2 5.7,4 8.4,4 " +
                    "C10,4 11.3,4.9 12,6 C12.7,4.9 14,4 15.6,4 C18.3,4 20.5,6.2 20.5,9 " +
                    "C20.5,13.5 15.5,17.5 12,20.5 Z"),
            new Kind(2, 0xFFF2B01E, R.string.sg_badge_2_title, R.string.sg_badge_2_about,
                    "M12,3.2 L14.7,8.9 L20.9,9.6 L16.3,13.8 L17.5,20 L12,16.9 " +
                    "L6.5,20 L7.7,13.8 L3.1,9.6 L9.3,8.9 Z"),
            new Kind(3, 0xFFB08C3A, R.string.sg_badge_3_title, R.string.sg_badge_3_about,
                    "M3.5,7 L8.2,10.6 L12,4.6 L15.8,10.6 L20.5,7 L18.9,17.2 L5.1,17.2 Z " +
                    "M5.1,18.6 L18.9,18.6 L18.9,20.2 L5.1,20.2 Z"),
            new Kind(4, 0xFFE85D3A, R.string.sg_badge_4_title, R.string.sg_badge_4_about,
                    "M12,2.5 C12,2.5 6.5,8.2 6.5,13 A5.5,5.5 0 0 0 17.5,13 " +
                    "C17.5,11.2 16.6,9.4 15.2,7.9 C15.3,10 14.2,11.3 13.1,11.6 " +
                    "C13.3,9.5 12.9,5.5 12,2.5 Z"),
            new Kind(5, 0xFF8FA65A, R.string.sg_badge_5_title, R.string.sg_badge_5_about,
                    "M4.5,5 L8.2,7.6 L15.8,7.6 L19.5,5 L19.5,12.5 A7.5,7.5 0 0 1 4.5,12.5 Z " +
                    "M9.1,10.9 A1.15,1.15 0 1 0 9.1,13.2 A1.15,1.15 0 1 0 9.1,10.9 Z " +
                    "M14.9,10.9 A1.15,1.15 0 1 0 14.9,13.2 A1.15,1.15 0 1 0 14.9,10.9 Z"),
            new Kind(6, 0xFFC74B6E, R.string.sg_badge_6_title, R.string.sg_badge_6_about,
                    "M12,3 A5.2,5.2 0 1 0 12,13.4 A5.2,5.2 0 1 0 12,3 Z " +
                    "M12,5.6 A2.6,2.6 0 1 1 12,10.8 A2.6,2.6 0 1 1 12,5.6 Z " +
                    "M11.2,13.4 L12.8,13.4 L12.8,20.6 L11.2,20.6 Z " +
                    "M12.8,15 C15.2,15 16.9,16.4 17.2,18.2 C15.2,18 13.5,16.8 12.8,15 Z"),
            new Kind(7, 0xFF6E7A8A, R.string.sg_badge_7_title, R.string.sg_badge_7_about,
                    "M12,3.2 A7,7 0 0 0 5,10.2 L5,14.6 L7.5,14.6 L7.5,17.3 L9.6,19.8 " +
                    "L14.4,19.8 L16.5,17.3 L16.5,14.6 L19,14.6 L19,10.2 A7,7 0 0 0 12,3.2 Z " +
                    "M8.9,10 A1.5,1.5 0 1 0 8.9,13 A1.5,1.5 0 1 0 8.9,10 Z " +
                    "M15.1,10 A1.5,1.5 0 1 0 15.1,13 A1.5,1.5 0 1 0 15.1,10 Z " +
                    "M12,13.8 L13.2,15.9 L10.8,15.9 Z"),
            new Kind(8, 0xFF4FB6D8, R.string.sg_badge_8_title, R.string.sg_badge_8_about,
                    "M7,4 L17,4 L20.8,9.2 L12,20.6 L3.2,9.2 Z " +
                    "M4.6,9.2 L19.4,9.2 L19.4,10.1 L4.6,10.1 Z"),
            new Kind(9, 0xFFF2C94C, R.string.sg_badge_9_title, R.string.sg_badge_9_about,
                    "M13.2,2.8 L5.5,13.2 L10.4,13.2 L9.4,21.2 L18.5,9.8 L13.3,9.8 Z"),
            new Kind(10, 0xFF7A6ED8, R.string.sg_badge_10_title, R.string.sg_badge_10_about,
                    "M19.5,14.8 A8,8 0 1 1 9.2,4.5 A6.5,6.5 0 0 0 19.5,14.8 Z"),
            new Kind(11, 0xFF5AA9E6, R.string.sg_badge_11_title, R.string.sg_badge_11_about,
                    "M11.2,7 C9,3.5 4.5,3.8 4,7.5 C3.6,10.5 6.5,12.2 9.5,12 " +
                    "C6.8,12.8 4.8,14.8 5.6,17.2 C6.4,19.6 9.8,19 11.2,15.5 Z " +
                    "M12.8,7 C15,3.5 19.5,3.8 20,7.5 C20.4,10.5 17.5,12.2 14.5,12 " +
                    "C17.2,12.8 19.2,14.8 18.4,17.2 C17.6,19.6 14.2,19 12.8,15.5 Z " +
                    "M11.4,6 L12.6,6 L12.6,19 L11.4,19 Z"),
            new Kind(12, 0xFFE86A5A, R.string.sg_badge_12_title, R.string.sg_badge_12_about,
                    "M4.5,10.5 L19.5,10.5 L19.5,13 L4.5,13 Z " +
                    "M5.8,14 L10.9,14 L10.9,20.5 L5.8,20.5 Z " +
                    "M13.1,14 L18.2,14 L18.2,20.5 L13.1,20.5 Z " +
                    "M10.9,13 L13.1,13 L13.1,20.5 L10.9,20.5 Z " +
                    "M12,9.6 C9.4,9.6 8.4,7.6 9.4,6.3 C10.4,5.1 12.2,5.9 12,8.2 Z " +
                    "M12,9.6 C14.6,9.6 15.6,7.6 14.6,6.3 C13.6,5.1 11.8,5.9 12,8.2 Z"),
    };

    public static Kind[] kinds() {
        return KINDS;
    }

    public static Kind byId(int id) {
        for (Kind kind : KINDS) {
            if (kind.id == id) {
                return kind;
            }
        }
        return null;
    }

    /**
     * Значок у этого человека, если он уже у нас есть: номер из каталога или
     * ноль, если значка нет. Минус один — пока не знаем: спросить можно
     * {@link #request(long)}, он ответит уведомлением, когда придёт ответ.
     */
    public static int of(long peerId) {
        final Integer cached = cache.get(peerId);
        if (cached != null) {
            return cached;
        }
        request(peerId);
        return -1;
    }

    /** Иконка значка у имени: цветное поле с белым глифом, размер как у галочки. */
    public static Drawable icon(Kind kind) {
        return kind == null ? null : new BadgeDrawable(kind);
    }

    /**
     * Рисованный значок: скруглённое цветное поле и белый глиф поверх.
     *
     * Размер берёт из границ, как ведут себя все drawable: у имени его
     * поставят в 16 точек, в диалоге можно дать и побольше — глиф
     * промасштабируется сам.
     */
    public static final class BadgeDrawable extends Drawable {

        private static final android.util.SparseArray<android.graphics.Path> pathCache =
                new android.util.SparseArray<>();

        private final Kind kind;
        private final Paint fieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF field = new RectF();
        private final float radius = AndroidUtilities.dp(24) * 5f / 24f;

        public BadgeDrawable(Kind kind) {
            this.kind = kind;
            fieldPaint.setColor(kind.color);
            glyphPaint.setColor(0xFFFFFFFF);
        }

        private android.graphics.Path glyphPath() {
            android.graphics.Path cached = pathCache.get(kind.id);
            if (cached == null) {
                cached = PathParser.createPathFromPathData(kind.pathData);
                pathCache.put(kind.id, cached);
            }
            return cached;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final android.graphics.Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            field.set(bounds.left + 1, bounds.top + 1, bounds.right - 1, bounds.bottom - 1);
            final float corner = radius * bounds.width() / AndroidUtilities.dp(16);
            canvas.drawRoundRect(field, corner, corner, fieldPaint);
            // Глиф вписан в поле с запасом в четверть: сетка 24 — значок 18.
            final android.graphics.Matrix matrix = new android.graphics.Matrix();
            final float scale = bounds.width() * 0.75f / 24f;
            final float offset = bounds.width() * 0.125f;
            matrix.setScale(scale, scale);
            matrix.postTranslate(bounds.left + offset, bounds.top + offset);
            final android.graphics.Path glyph = new android.graphics.Path(glyphPath());
            glyph.transform(matrix);
            canvas.drawPath(glyph, glyphPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            fieldPaint.setAlpha(alpha);
            glyphPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            // Значок всегда рисуется своими цветами и не должен перекрашиваться
            // внешним тинтом (например, цветом ника в чате).
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(16);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(16);
        }
    }

    // Ниже — хранилище: кто что выбрал, по одному сообщению на человека.

    /** peer → номер значка. Ноль — «знаем, что значка нет». */
    private static final HashMap<Long, Integer> cache = new HashMap<>();
    /** Кто уже спрашивается, чтобы не спрашивать дважды. */
    private static final Set<Long> asking = new HashSet<>();

    /**
     * Спросить у группы значок этого человека.
     *
     * Ответ приходит уведомлением mainUserInfoChanged — на него уже
     * перерисовываются профили и списки, отдельного слушателя не нужно.
     */
    public static void request(long peerId) {
        if (peerId == 0 || cache.containsKey(peerId) || !asking.add(peerId)) {
            return;
        }
        SweetgramBannerGroup.find(TAG, peerId, 5, (messages, problem) -> {
            asking.remove(peerId);
            if (problem != null) {
                // Не спросили, а не «нет значка»: в кэш не пишем, чтобы
                // следующий профиль снова попробовал.
                return;
            }
            int found = 0;
            // Сообщения приходят новыми сверху, значит первое — актуальный выбор.
            for (org.telegram.messenger.MessageObject message : messages) {
                final int id = parse(message);
                if (id > 0) {
                    found = id;
                    break;
                }
            }
            cache.put(peerId, found);
            notifyChanged();
        });
    }

    /** Номер значка из текста сообщения или ноль. */
    private static int parse(org.telegram.messenger.MessageObject message) {
        if (message == null || message.messageOwner == null
                || message.messageOwner.message == null) {
            return 0;
        }
        try {
            final Matcher at = BADGE_IN_TEXT.matcher(message.messageOwner.message);
            if (at.find()) {
                return Integer.parseInt(at.group(1));
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** Чем кончилась публикация или уборка значка. */
    public interface Result {
        void onResult(int what);
    }

    /** Группы нет — отправить было некуда. */
    public static final int FAILED = 0;
    /** Сообщение с меткой ушло / своё сообщение удалено. */
    public static final int SENT = 1;
    public static final int REMOVED = 1;
    /** Убирать было нечего. */
    public static final int NOTHING = 2;

    /** Выбрать значок себе: публикует сообщение в группу и красит локально сразу. */
    public static void set(Kind kind, Result done) {
        if (kind == null) {
            return;
        }
        final long me = me();
        if (me <= 0) {
            if (done != null) {
                done.onResult(FAILED);
            }
            return;
        }
        SweetgramBannerGroup.resolve(dialogId -> {
            if (dialogId == 0) {
                if (done != null) {
                    done.onResult(FAILED);
                }
                return;
            }
            try {
                final SendMessagesHelper.SendMessageParams params =
                        new SendMessagesHelper.SendMessageParams();
                params.message = TAG + " " + kind.id;
                params.peer = dialogId;
                AccountInstance.getInstance(UserConfig.selectedAccount)
                        .getSendMessagesHelper().sendMessage(params);
            } catch (Throwable t) {
                org.telegram.messenger.FileLog.e(t);
                if (done != null) {
                    done.onResult(FAILED);
                }
                return;
            }
            // Своё показываем немедленно: ждать, пока сервер вернёт поиском,
            // человеку незачем — он только что выбрал.
            cache.put(me, kind.id);
            notifyChanged();
            if (done != null) {
                done.onResult(SENT);
            }
        });
    }

    /** Убрать значок: удаляет своё последнее сообщение с меткой. */
    public static void clear(Result done) {
        final long me = me();
        if (me <= 0) {
            if (done != null) {
                done.onResult(FAILED);
            }
            return;
        }
        SweetgramBannerGroup.find(TAG, me, 5, (messages, problem) -> {
            int what = NOTHING;
            for (org.telegram.messenger.MessageObject message : messages) {
                SweetgramBannerGroup.remove(message.getId());
                what = REMOVED;     // актуальный выбор — одно сообщение, его и удаляем
                break;
            }
            cache.put(me, 0);
            notifyChanged();
            if (done != null) {
                done.onResult(what);
            }
        });
    }

    private static long me() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.mainUserInfoChanged);
                    NotificationCenter.getInstance(a).postNotificationName(
                            NotificationCenter.updateInterfaces,
                            org.telegram.messenger.MessagesController.UPDATE_MASK_AVATAR
                                    | org.telegram.messenger.MessagesController.UPDATE_MASK_NAME);
                }
            }
        });
    }
}
