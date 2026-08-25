package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.margelet.MargeletHooks;
import org.telegram.margelet.MargeletPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * Строка плагина: значок, имя, версия с автором, переключатель.
 *
 * Значок — файл icon.png из архива плагина. Своего у плагина может и не быть,
 * поэтому пустое место не оставляем: рисуем плашку с первой буквой имени.
 * Цвет плашки берётся из номера плагина — не случайный, значит при каждом
 * открытии списка один и тот же, и плагин узнаётся по цвету.
 */
public class MargeletPluginCell extends FrameLayout implements Theme.Colorable {

    /** Цвета плашек. Те же, что у кнопок разметки: набор уже подобран. */
    private static final int[] COLORS = {
            0xFF4F85F6, 0xFF55CA47, 0xFFF09F1B, 0xFFF45255, 0xFF32C0CE,
            0xFFC46EF4, 0xFF8699AA, 0xFFE26314
    };

    private final ImageView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ImageView gearView;
    private final Switch switchView;

    public MargeletPluginCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(iconView, LayoutHelper.createFrame(38, 38,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,
                LocaleController.isRTL ? 0 : 18, 0, LocaleController.isRTL ? 18 : 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                LocaleController.isRTL ? 74 : 70, 10, LocaleController.isRTL ? 70 : 74, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                LocaleController.isRTL ? 74 : 70, 33, LocaleController.isRTL ? 70 : 74, 0));

        // Шестерёнка стоит там, куда и надо нажать, чтобы открыть настройки:
        // левее переключателя. Без неё человеку неоткуда узнать, что у
        // плагина вообще есть что настраивать.
        gearView = new ImageView(context);
        gearView.setScaleType(ImageView.ScaleType.CENTER);
        gearView.setImageResource(R.drawable.msg_settings_old);
        addView(gearView, LayoutHelper.createFrame(24, 24,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                LocaleController.isRTL ? 68 : 20, 0, LocaleController.isRTL ? 20 : 68, 0));

        switchView = new Switch(context, resourcesProvider);
        switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        addView(switchView, LayoutHelper.createFrame(37, 20,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                22, 0, 22, 0));

        updateColors();
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        gearView.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon),
                android.graphics.PorterDuff.Mode.SRC_IN));
    }

    public void set(MargeletPlugins.Plugin plugin, boolean checked, boolean animated) {
        titleView.setText(plugin.name);
        subtitleView.setText(plugin.version + " · " + plugin.author);
        final Bitmap icon = plugin.icon();
        iconView.setImageDrawable(icon != null
                ? new Rounded(icon)
                : new Letter(plugin.name, COLORS[Math.abs(plugin.id.hashCode()) % COLORS.length]));
        gearView.setVisibility(MargeletHooks.hasSettings(plugin.id) ? VISIBLE : GONE);
        switchView.setChecked(checked, animated);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
    }

    /** Картинка плагина, вписанная в скруглённый квадрат. */
    private static class Rounded extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap bitmap;
        private final RectF rect = new RectF();

        Rounded(Bitmap bitmap) {
            this.bitmap = bitmap;
            paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rect.set(getBounds());
            canvas.save();
            // Картинку присылает автор плагина, и она какая угодно: тянем её
            // в квадрат строки, а не полагаемся на то, что размер совпал.
            canvas.translate(rect.left, rect.top);
            canvas.scale(rect.width() / bitmap.getWidth(), rect.height() / bitmap.getHeight());
            canvas.drawRoundRect(0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    bitmap.getWidth() * 0.24f, bitmap.getHeight() * 0.24f, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Плашка с первой буквой — когда своей картинки у плагина нет. */
    private static class Letter extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String letter;
        private final RectF rect = new RectF();

        Letter(String name, int color) {
            paint.setColor(color);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextAlign(Paint.Align.CENTER);
            final String trimmed = name == null ? "" : name.trim();
            letter = trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rect.set(getBounds());
            final float r = rect.width() * 0.24f;
            canvas.drawRoundRect(rect, r, r, paint);
            textPaint.setTextSize(rect.height() * 0.5f);
            canvas.drawText(letter, rect.centerX(),
                    rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            textPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static class Factory extends UItem.UItemFactory<MargeletPluginCell> {
        static { setup(new Factory()); }

        @Override
        public MargeletPluginCell createView(Context context, RecyclerListView listView, int currentAccount,
                                             int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new MargeletPluginCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            ((MargeletPluginCell) view).set((MargeletPlugins.Plugin) item.object, item.checked, false);
        }

        public static UItem of(int id, MargeletPlugins.Plugin plugin, boolean checked) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = plugin;
            item.checked = checked;
            return item;
        }
    }
}
