package org.telegram.sweetgram;

import android.media.MediaPlayer;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SweetgramSound {

    private static volatile String cachedPath;
    private static final Object lock = new Object();

    public static void playWordmark() {
        try {
            FileLog.d("SweetgramSound.playWordmark called");
            final String path = ensureCached();
            if (TextUtils.isEmpty(path)) {
                return;
            }
            final MediaPlayer mp = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
                } catch (Throwable ignore) {
                }
            }
            mp.setVolume(1.0f, 1.0f);
            mp.setDataSource(path);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.setOnErrorListener((m, what, extra) -> {
                FileLog.e("SweetgramSound error " + what + "/" + extra);
                m.release();
                return true;
            });
            mp.prepareAsync();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String ensureCached() {
        if (cachedPath != null) {
            return cachedPath;
        }
        synchronized (lock) {
            if (cachedPath != null) {
                return cachedPath;
            }
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
                cachedPath = out.getAbsolutePath();
            } catch (Throwable e) {
                FileLog.e(e);
                cachedPath = null;
            }
        }
        return cachedPath;
    }
}
