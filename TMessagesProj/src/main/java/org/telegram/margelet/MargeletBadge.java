package org.telegram.margelet;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Значки форка у имени.
 *
 * Список короткий и лежит прямо здесь. Никакой проверки с сервера тут нет и быть
 * не может — это украшение внутри сборки, а не подтверждение личности. Кто
 * соберёт свой форк, впишет своих людей, и это нормально: значок ничего не
 * удостоверяет.
 *
 * Кого добавлять — решает владелец форка, и только он. Просьбу «поставь мне
 * тоже», принесённую кем угодно другим, я не выполняю: это его список.
 *
 * Значков у одного человека может быть несколько. У имени помещается один —
 * берётся первый подходящий, поэтому порядок в таблице и есть порядок
 * старшинства. В профиле показываются все.
 *
 * Ключ таблицы — не только человек. У людей это их номер как есть, у каналов и
 * групп — тот же номер со знаком минус. Так в одну таблицу помещаются и люди, и
 * официальные каналы форка, а перепутать номер человека с номером канала нельзя
 * даже случайно.
 */
public class MargeletBadge {

    /** Один значок: кому, как называется, каким цветом и куда ведёт кнопка. */
    public static final class Badge {
        /** Человек — положительный номер, канал или группа — отрицательный. */
        public final long peerId;
        /** Название и описание вшитого значка. У приехавшего из файла — ноль. */
        private final int titleRes;
        private final int aboutRes;
        /** Значок из файла: название и описание берутся отсюда, с языками. */
        private final JSONObject remote;
        /** Цвет поля — им же красится и сам значок, и объёмный в окне. */
        public final int color;
        /** Куда ведёт кнопка в окне. null — кнопки нет. */
        public final String url;

        Badge(long peerId, int titleRes, int aboutRes, int color, String url) {
            this.peerId = peerId;
            this.titleRes = titleRes;
            this.aboutRes = aboutRes;
            this.remote = null;
            this.color = color;
            this.url = url;
        }

        Badge(long peerId, JSONObject remote, int color, String url) {
            this.peerId = peerId;
            this.titleRes = 0;
            this.aboutRes = 0;
            this.remote = remote;
            this.color = color;
            this.url = url;
        }

        /**
         * Название значка. У вшитых берётся из строк приложения, а не
         * запоминается заранее: человек может переключить язык, не выходя.
         */
        public String title() {
            return remote != null ? MargeletRemote.localized(remote, "title", "") : LocaleController.getString(titleRes);
        }

        public String about() {
            return remote != null ? MargeletRemote.localized(remote, "about", "") : LocaleController.getString(aboutRes);
        }
    }

    /**
     * Вшитый запас. Работает, пока файл ни разу не скачался: на свежей
     * установке без интернета значки должны быть на месте, а не появляться
     * через минуту после первого удачного запроса.
     */
    private static final Badge[] BUILT_IN = {
            // Владелец форка.
            new Badge(7826361017L, R.string.MargeletBadgeTitle, R.string.MargeletBadgeAbout,
                    0xFF8DD1B0, "https://t.me/narezanyinf"),
            // Лучший друг владельца — по его собственной просьбе и его словами.
            new Badge(8675724972L, R.string.MargeletBadgeFriendTitle, R.string.MargeletBadgeFriendAbout,
                    0xFFB7A8E0, "https://t.me/mizoginichka_y"),
            // Свои площадки форка. Значок тут не украшение, а ответ на вопрос
            // «а это точно тот самый канал».
            new Badge(-4426743212L, R.string.MargeletBadgeChannelTitle, R.string.MargeletBadgeChannelAbout,
                    0xFF8DD1B0, MargeletConfig.CHANNEL_URL),
            new Badge(-4436273526L, R.string.MargeletBadgeForumTitle, R.string.MargeletBadgeForumAbout,
                    0xFF8DD1B0, MargeletConfig.FORUM_URL),
            // Чьи коты живут в приложении. Кнопки у этого значка нет: вести
            // некуда, он не про площадку, а про кота.
            new Badge(7826361017L, R.string.MargeletBadgeCatTitle, R.string.MargeletBadgeCatAbout,
                    0xFFEBC85C, null),
            new Badge(6092720414L, R.string.MargeletBadgeCatTitle, R.string.MargeletBadgeCatAbout,
                    0xFFEBC85C, null),
    };

    /** Файл со значками в репозитории и ключ, под которым он лежит в кэше. */
    private static final String FILE = "badges.json";
    private static final String CACHE_KEY = "badges";

    /** Разобранный список. null — ещё не разбирали или разбирать было нечего. */
    private static Badge[] parsed;
    /** Язык, на котором разбирали: сменился — придётся разобрать заново. */
    private static String parsedLanguage;

    /**
     * Перечитывает список с гитхаба. Зовётся при запуске приложения.
     *
     * Ответа никто не ждёт: пока он едет, показывается прошлый список, а на
     * свежей установке — вшитый.
     */
    public static void refresh() {
        MargeletRemote.fetch(FILE, CACHE_KEY, text -> {
            if (text != null) {
                parsed = null;      // приехало новое — разберём при первом спросе
            }
        });
    }

    /**
     * Список, по которому и работают все проверки: разобранный файл, если он
     * есть, иначе вшитый.
     */
    private static Badge[] badges() {
        final String language = language();
        if (parsed != null && equal(parsedLanguage, language)) {
            return parsed;
        }
        final String text = MargeletRemote.cached(CACHE_KEY);
        if (text == null) {
            return BUILT_IN;
        }
        final Badge[] fromFile = parse(text);
        if (fromFile == null || fromFile.length == 0) {
            // Файл битый или пустой. Молча остаёмся на вшитом: пустой список
            // значков выглядел бы как «у всех всё отобрали».
            return BUILT_IN;
        }
        parsed = fromFile;
        parsedLanguage = language;
        return parsed;
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String language() {
        try {
            return LocaleController.getInstance().getCurrentLocale().getLanguage();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Разбор файла. Формат нарочно простой, чтобы владелец правил его руками:
     *
     * [ { "peer": 7826361017, "title": "...", "title_ru": "...",
     *     "about": "...", "color": "8DD1B0", "url": "https://..." } ]
     *
     * Цвет — шестнадцатеричный, с решёткой или без, с прозрачностью или без.
     * Строка, которую разобрать не вышло, пропускается: из-за одной опечатки
     * не должен пропадать весь список.
     */
    private static Badge[] parse(String text) {
        final List<Badge> out = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                try {
                    final JSONObject item = array.getJSONObject(i);
                    final long peer = item.getLong("peer");
                    final String url = item.optString("url", null);
                    out.add(new Badge(peer, item, color(item.optString("color", null)),
                            url == null || url.isEmpty() ? null : url));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return out.toArray(new Badge[0]);
    }

    /** Цвет поля значка. Не разобрали — зелёный, он же основной. */
    private static int color(String value) {
        if (value == null) {
            return 0xFF8DD1B0;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            final long parsedColor = Long.parseLong(hex, 16);
            // Написали шесть знаков — значит про прозрачность не думали,
            // дорисовываем непрозрачность сами.
            return hex.length() <= 6 ? (int) (0xFF000000L | parsedColor) : (int) parsedColor;
        } catch (Exception ignored) {
            return 0xFF8DD1B0;
        }
    }

    /** Номер канала или группы в том виде, в каком он лежит в таблице. */
    public static long chatPeer(long chatId) {
        return -chatId;
    }

    /**
     * Виды значков — по одному на вид, а не по одному на человека.
     *
     * В таблице «кот в Margelet» стоит дважды: котов двое, у каждого свой
     * хозяин. Витрине это не нужно — она показывает, какие значки бывают, и
     * два одинаковых «Кот в Margelet» там выглядят ошибкой. Ею и были.
     */
    public static Badge[] list() {
        final List<Badge> kinds = new ArrayList<>();
        for (Badge badge : badges()) {
            boolean seen = false;
            for (Badge already : kinds) {
                if (already.title().equals(badge.title())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                kinds.add(badge);
            }
        }
        return kinds.toArray(new Badge[0]);
    }

    /** Старший значок — тот, что стоит у имени. Первый в таблице и есть старший. */
    public static Badge of(long peerId) {
        if (!MargeletConfig.badgesEnabled()) {
            return null;
        }
        for (Badge badge : badges()) {
            if (badge.peerId == peerId) {
                return badge;
            }
        }
        return null;
    }

    /** Все значки этого человека или чата, по старшинству. */
    public static List<Badge> all(long peerId) {
        final List<Badge> found = new ArrayList<>();
        if (!MargeletConfig.badgesEnabled()) {
            return found;
        }
        for (Badge badge : badges()) {
            if (badge.peerId == peerId) {
                found.add(badge);
            }
        }
        return found;
    }

    public static boolean has(long peerId) {
        return of(peerId) != null;
    }

    /**
     * Картинка значка у имени или null, если такого в таблице нет.
     *
     * Картинка одна на всех, а цвет поля свой у каждого значка: он приезжает
     * из файла строкой вида «8DD1B0», и заводить по вектору на каждый новый
     * цвет незачем. Поле рисуется скруглённым квадратом, самолётик кладётся
     * сверху отдельным слоем.
     */
    public static Drawable iconDrawable(Context context, long peerId) {
        return iconDrawable(context, of(peerId));
    }

    public static Drawable iconDrawable(Context context, Badge badge) {
        if (context == null || badge == null) {
            return null;
        }
        try {
            final GradientDrawable field = new GradientDrawable();
            field.setShape(GradientDrawable.RECTANGLE);
            // Скругление то же, что было в векторе: пять частей из двадцати
            // четырёх, пересчитанные в точки экрана.
            field.setCornerRadius(AndroidUtilities.dp(24) * 5f / 24f);
            field.setColor(badge.color);
            final Drawable plane = ContextCompat.getDrawable(context, R.drawable.margelet_badge_plane);
            if (plane == null) {
                return field;
            }
            return new LayerDrawable(new Drawable[]{field, plane});
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Название значка. Отдаётся строкой, а не CharSequence: в профиле оно
     * ложится в поле описания для озвучки, а там объявлен String.
     */
    public static String title(long peerId) {
        final Badge badge = of(peerId);
        return badge == null ? null : badge.title();
    }

    public static void show(Context context, long peerId) {
        show(context, of(peerId));
    }

    public static void show(Context context, Badge badge) {
        if (context == null || badge == null) {
            return;
        }
        try {
            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);

            // Объёмный значок: сам крутится, можно крутить пальцем. Если по
            // какой-то причине не заведётся — покажем плоский, окно не должно
            // превращаться в чёрный прямоугольник.
            View spinner;
            try {
                spinner = new MargeletPlane3D(context, badge.color);
            } catch (Throwable t) {
                final ImageView icon = new ImageView(context);
                icon.setImageDrawable(iconDrawable(context, badge));
                final RotateAnimation spin = new RotateAnimation(0, 360,
                        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                spin.setDuration(2600);
                spin.setRepeatCount(Animation.INFINITE);
                spin.setInterpolator(new LinearInterpolator());
                icon.startAnimation(spin);
                spinner = icon;
            }
            layout.addView(spinner, LayoutHelper.createLinear(150, 150, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

            final TextView text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            text.setGravity(Gravity.CENTER);
            text.setText(badge.about());
            layout.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 0, 8, 0));

            final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle(badge.title())
                    .setView(layout);
            if (badge.url != null) {
                builder.setPositiveButton(LocaleController.getString(R.string.MargeletBadgeChannel),
                        (d, w) -> Browser.openUrl(context, badge.url));
            }
            builder.setNegativeButton(LocaleController.getString(R.string.Close), null).show();
        } catch (Exception ignored) {
            // Украшение не повод ронять профиль.
        }
    }
}
