package org.telegram.margelet;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.io.File;

/**
 * Мяуканье по нажатию на название на главном экране, с прогибом надписи.
 *
 * Сначала было по удержанию, но владелец справедливо сказал, что по нажатию
 * живее. Событие не съедается: нажатие по названию по-прежнему прокручивает
 * список к началу, и ломать привычный жест ради шутки не стоит.
 *
 * Нажатием считаем короткое касание без ухода пальца в сторону — иначе мяу
 * срабатывало бы на каждой прокрутке, начавшейся с названия.
 */
public class MargeletMeow {

    public static void attach(View view) {
        if (view == null) {
            return;
        }
        view.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private long startAt;
            private boolean tracking;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        startAt = System.currentTimeMillis();
                        tracking = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - startX) > slop
                                || Math.abs(event.getY() - startY) > slop) {
                            tracking = false;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (tracking && System.currentTimeMillis() - startAt
                                <= ViewConfiguration.getLongPressTimeout()) {
                            squish(v);
                            play(v.getContext());
                        }
                        tracking = false;
                        break;
                    default:
                        tracking = false;
                        break;
                }
                return false;
            }
        });
    }

    /** Прогиб: надпись приседает и отпружинивает обратно. */
    public static void squish(View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setPivotY(view.getHeight());
        view.animate()
                .scaleY(0.72f).scaleX(1.09f)
                .setDuration(90)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT)
                .withEndAction(() -> view.animate()
                        .scaleY(1f).scaleX(1f)
                        .setDuration(340)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK)
                        .start())
                .start();
    }

    /** Проиграть звук — тот, что выбран: свой файл или лежащий в сборке. */
    public static void play(Context context) {
        if (!MargeletConfig.meowEnabled()) {
            return;
        }
        try {
            MediaPlayer player;
            final String own = MargeletConfig.meowPath();
            if (own != null && new File(own).exists()) {
                player = new MediaPlayer();
                player.setDataSource(own);
                player.prepare();
            } else {
                player = MediaPlayer.create(context, R.raw.margelet_meow);
            }
            if (player == null) {
                return;
            }
            player.setOnCompletionListener(MediaPlayer::release);
            player.start();
            // Настройки звука появляются только после того, как его услышали
            // хотя бы раз: до этого их не за что настраивать.
            MargeletConfig.setMeowHeard();
        } catch (Exception ignored) {
            // Звук — украшение. Не дала система — экран должен жить дальше.
        }
    }
}
