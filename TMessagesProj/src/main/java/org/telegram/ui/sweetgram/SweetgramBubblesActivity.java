package org.telegram.ui.sweetgram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.sweetgram.SweetgramBubbles;
import org.telegram.sweetgram.SweetgramConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * «Свой пузырь»: цвет и форма своих исходящих сообщений.
 *
 * Экран честно называет вещь своими именами: настройка локальная, её видит
 * только сам человек. Палитра — плашки, как у прочих экранов форка; второй
 * цвет перехода не выбирают руками, он выводится из первого: меньше свободы,
 * зато ни один переход не выходит грязным.
 */
public class SweetgramBubblesActivity extends UniversalFragment {

    private static final int ID_ON = 1;
    private static final int ID_GRADIENT = 2;

    private Preview preview;
    private Swatches swatches;
    private Radius radius;

    /** Палитра: два ряда — нежные и насыщенные. */
    private static final int[] COLORS = {
            0xFFF8BBD0, 0xFFF48FB1, 0xFFEC6A9C, 0xFFE91E63,
            0xFFFFD3E0, 0xFFFDA4AF, 0xFFFB7185, 0xFFE11D48,
            0xFFC8E6C9, 0xFFA5D6A7, 0xFF81C784, 0xFF43A047,
            0xFFB3E5FC, 0xFF81D4FA, 0xFF4FC3F7, 0xFF0288D1,
            0xFFFFE082, 0xFFFFC107, 0xFFB39DDB, 0xFF7E57C2,
    };

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.SweetgramBubbles);
    }

    @Override
    public View createView(Context context) {
        preview = null;
        swatches = null;
        radius = null;
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getContext() == null) {
            return;
        }
        if (preview == null) {
            preview = new Preview(getContext());
        }
        if (swatches == null) {
            swatches = new Swatches(getContext(), () -> {
                if (preview != null) {
                    preview.invalidate();
                }
            });
        }
        if (radius == null) {
            radius = new Radius(getContext());
        }
        preview.set(SweetgramConfig.ownBubbleColor1(),
                effectiveSecondColor(SweetgramConfig.ownBubbleColor1(), SweetgramConfig.ownBubbleGradient()));
        swatches.setSelected(SweetgramConfig.ownBubbleColor1());

        items.add(UItem.asCustom(preview, previewHeight()));
        items.add(UItem.asCheck(ID_ON, LocaleController.getString(R.string.SweetgramBubblesOn))
                .setChecked(SweetgramConfig.ownBubbleOn()));
        items.add(UItem.asCheck(ID_GRADIENT, LocaleController.getString(R.string.SweetgramBubblesGradient))
                .setChecked(SweetgramConfig.ownBubbleGradient()));
        items.add(UItem.asCustom(swatches, swatchHeight()));
        items.add(UItem.asCustom(radius, radiusHeight()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SweetgramBubblesAbout)));
        items.add(UItem.asShadow("Based on Margy (@margeletter , github.com/narezany/Margelet)"));
    }

    private int previewHeight() {
        return 120;
    }

    private int swatchHeight() {
        return AndroidUtilities.dp(4 * 52 + 24);
    }

    private int radiusHeight() {
        return 76;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ON) {
            SweetgramConfig.setOwnBubbleOn(!SweetgramConfig.ownBubbleOn());
            SweetgramBubbles.reset();
            listView.adapter.update(true);
        } else if (item.id == ID_GRADIENT) {
            SweetgramConfig.setOwnBubbleGradient(!SweetgramConfig.ownBubbleGradient());
            SweetgramBubbles.reset();
            updateViews();
        }
    }

    private void updateViews() {
        if (preview != null) {
            preview.set(SweetgramConfig.ownBubbleColor1(),
                    effectiveSecondColor(SweetgramConfig.ownBubbleColor1(), SweetgramConfig.ownBubbleGradient()));
        }
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    /** Второй цвет перехода: тот же тон, глубже и чуть холоднее. */
    private static int companionColor(int color) {
        final float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 340.0f) % 360.0f;
        hsv[1] = Math.min(1f, hsv[1] * 1.08f);
        hsv[2] = Math.max(0f, hsv[2] * 0.72f);
        return android.graphics.Color.HSVToColor(0xFF, hsv);
    }

    static int effectiveSecondColor(int first, boolean gradient) {
        return gradient ? companionColor(first) : 0;
    }

    private static float perceivedBrightness(int color) {
        final float r = android.graphics.Color.red(color) / 255f;
        final float g = android.graphics.Color.green(color) / 255f;
        final float b = android.graphics.Color.blue(color) / 255f;
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    /** Два пузыря: как будут выглядеть свои сообщения. */
    private static class Preview extends View {

        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private int color1 = 0xFFE59CB8;
        private int color2 = 0;

        Preview(Context context) {
            super(context);
            textPaint.setTextSize(AndroidUtilities.dp(16));
            timePaint.setTextSize(AndroidUtilities.dp(12));
        }

        void set(int first, int second) {
            color1 = first;
            color2 = second;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int width = getMeasuredWidth();
            if (SweetgramConfig.ownBubbleOn()) {
                if (color2 != 0 && color2 != color1) {
                    bubblePaint.setShader(new LinearGradient(
                            width * 0.35f, 0, width * 0.35f, AndroidUtilities.dp(64),
                            color1 | 0xFF000000, color2 | 0xFF000000, Shader.TileMode.CLAMP));
                } else {
                    bubblePaint.setShader(null);
                    bubblePaint.setColor(color1 | 0xFF000000);
                }
            } else {
                bubblePaint.setShader(null);
                bubblePaint.setColor(Theme.getColor(Theme.key_chat_outBubble));
            }
            final int ink = SweetgramBubbles.on()
                    ? (perceivedBrightness(color1 | 0xFF000000) > 0.705f
                            ? 0xff000000 : 0xffffffff)
                    : Theme.getColor(Theme.key_chat_messageTextOut);

            final float r = AndroidUtilities.dp(SharedConfig.bubbleRadius);
            final float right = width - AndroidUtilities.dp(16);
            final float left = width - AndroidUtilities.dp(220);
            rect.set(left, AndroidUtilities.dp(16), right, AndroidUtilities.dp(64));
            canvas.drawRoundRect(rect, r, r, bubblePaint);
            // Хвостик — нижний правый угол меньше прочих, как у телеграма.
            rect.set(right - r, AndroidUtilities.dp(64) - r, right, AndroidUtilities.dp(64));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), r, bubblePaint);

            textPaint.setColor(ink);
            canvas.drawText(LocaleController.getString(R.string.SweetgramBubblesSample),
                    left + AndroidUtilities.dp(12), AndroidUtilities.dp(40), textPaint);
            timePaint.setColor(ink);
            timePaint.setAlpha(180);
            canvas.drawText("14:01 ✓✓", right - AndroidUtilities.dp(58), AndroidUtilities.dp(58), timePaint);
        }
    }

    /** Сетка плашек: тап — выбрать цвет. */
    private static class Swatches extends View {

        private final Runnable changed;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private int selected;

        Swatches(Context context, Runnable changed) {
            super(context);
            this.changed = changed;
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(AndroidUtilities.dp(3));
            setSelected(SweetgramConfig.ownBubbleColor1());
        }

        void setSelected(int color) {
            selected = color;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                handleTap(event.getX(), event.getY());
                return true;
            }
            return super.onTouchEvent(event);
        }

        private void handleTap(float x, float y) {
            final int cell = AndroidUtilities.dp(52);
            final int cols = 4;
            final int col = (int) (x / cell);
            final int row = (int) ((y - AndroidUtilities.dp(10)) / cell);
            if (col < 0 || col >= cols || row < 0 || row >= COLORS.length / cols) {
                return;
            }
            final int color = COLORS[row * cols + col];
            SweetgramConfig.setOwnBubbleColor1(color);
            SweetgramConfig.setOwnBubbleOn(true);
            SweetgramBubbles.reset();
            selected = color;
            invalidate();
            if (changed != null) {
                changed.run();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int cell = AndroidUtilities.dp(52);
            final int size = AndroidUtilities.dp(38);
            final int offset = (cell - size) / 2;
            for (int i = 0; i < COLORS.length; i++) {
                final int col = i % 4;
                final int row = i / 4;
                final float x = col * cell + offset;
                final float y = AndroidUtilities.dp(10) + row * cell + offset;
                rect.set(x, y, x + size, y + size);
                paint.setColor(COLORS[i]);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), paint);
                if ((COLORS[i] | 0xFF000000) == (selected | 0xFF000000)) {
                    ringPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), ringPaint);
                }
            }
        }
    }

    /** Ползунок скругления: та же настройка телеграма, рядом — для удобства. */
    private static class Radius extends LinearLayout {

        private final TextView label;

        Radius(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), 0);

            label = new TextView(context);
            label.setTextSize(16);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final SeekBar seekBar = new SeekBar(context);
            seekBar.setMax(17);
            seekBar.setProgress(SharedConfig.bubbleRadius);
            seekBar.getProgressDrawable().setColorFilter(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), android.graphics.PorterDuff.Mode.SRC_IN);
            try {
                seekBar.getThumb().setColorFilter(
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), android.graphics.PorterDuff.Mode.SRC_IN);
            } catch (Throwable ignored) {
            }
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                    if (!fromUser) {
                        return;
                    }
                    SharedConfig.bubbleRadius = value;
                    ApplicationLoaderPreferencesPut("bubbleRadius", value);
                    label.setText(LocaleController.getString(R.string.SweetgramBubblesRadius) + ": " + value);
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                }
            });
            addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
            label.setText(LocaleController.getString(R.string.SweetgramBubblesRadius) + ": " + SharedConfig.bubbleRadius);
        }
    }

    /** SharedConfig держит и поле, и prefs: писать надо в оба, как делает сам телеграм. */
    private static void ApplicationLoaderPreferencesPut(String key, int value) {
        try {
            org.telegram.messenger.ApplicationLoader.applicationContext
                    .getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                    .edit().putInt(key, value).apply();
        } catch (Throwable ignored) {
        }
    }
}
