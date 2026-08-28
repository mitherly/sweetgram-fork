package org.telegram.sweetgram;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Короткий звук «buru-nyaa» по тапу по слову Sweetgram.
 * Используем SoundPool — он создан для многократных UI-звуков и не имеет
 * состояний-ловушек MediaPlayer (из-за которых звук играл лишь один раз).
 * Первый тап, пока пул ещё грузится, проигрывается через MediaPlayer.
 */
public class SweetgramSound {

    private static final Object lock = new Object();
    private static SoundPool pool;
    private static int soundId = -1;
    private static boolean loading = false;

    public static void playWordmark() {
        FileLog.d("SweetgramSound.playWordmark called");
        boolean ready;
        synchronized (lock) {
            ready = (pool != null && soundId > 0);
        }
        if (ready) {
            try {
                pool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
                return;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        playViaMediaPlayer();
        ensureLoaded();
    }

    /** Заранее подгружает звук в пул (вызывается при старте приложения). */
    public static void warmup() {
        ensureLoaded();
    }

    private static void ensureLoaded() {
        synchronized (lock) {
            if (loading || (pool != null && soundId > 0)) {
                return;
            }
            loading = true;
        }
        try {
            SoundPool p;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SoundPool.Builder b = new SoundPool.Builder();
                b.setMaxStreams(3);
                b.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                p = b.build();
            } else {
                p = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
            }
            File f = getCachedFile();
            if (f == null) {
                loading = false;
                return;
            }
            int id = p.load(f.getAbsolutePath(), 1);
            synchronized (lock) { pool = p; }
            p.setOnLoadCompleteListener((sp, sampleId, status) -> {
                synchronized (lock) {
                    if (status == 0) {
                        soundId = sampleId;
                    }
                    loading = false;
                }
            });
        } catch (Throwable e) {
            FileLog.e(e);
            loading = false;
        }
    }

    private static void playViaMediaPlayer() {
        try {
            File f = getCachedFile();
            if (f == null) {
                return;
            }
            MediaPlayer mp = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    mp.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
                } catch (Throwable ignore) {
                }
            }
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.setOnErrorListener((m, what, extra) -> {
                FileLog.e("SweetgramSound mp error " + what + "/" + extra);
                m.release();
                return true;
            });
            mp.prepareAsync();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static File getCachedFile() {
        try {
            android.content.Context ctx = ApplicationLoader.applicationContext;
            File out = new File(ctx.getFilesDir(), "sweetgram_buru_nyaa.mp3");
            if (!out.exists() || out.length() == 0) {
                try (InputStream in = ctx.getAssets().open("buru-nyaa.mp3");
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                }
            }
            return out;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}
