package org.telegram.sweetgram;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Хранение баннера профиля БЕЗ Firebase Storage (он платный/Blaze).
 * Вместо этого картинка сжимается до JPEG и пишется как base64 прямо в
 * Realtime Database (profiles/<id>/bannerB64) — это бесплатно на Spark-плане
 * и доступно другим клиентам форка при анонимном входе.
 */
public class SweetgramBanner {

    public static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final String PREF_NAME = "sweetgram";
    private static final String KEY_BANNER = "banner_b64";

    public interface UrlCallback {
        void onResult(String data);
    }

    public interface UploadCallback {
        void onResult(boolean ok, String data, String error);
    }

    public static long selfId() {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    public static String idStr(long userId) {
        return String.valueOf(Math.abs(userId));
    }

    public static void saveLocalBanner(String b64) {
        try {
            ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
                    .edit().putString(KEY_BANNER, b64).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String getLocalBanner() {
        try {
            return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
                    .getString(KEY_BANNER, null);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Сжимает файл до JPEG (макс. 1024px по длинной стороне, quality 80) и кодирует в base64. */
    private static String fileToBase64(File file) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            int max = 1024;
            int scale = 1;
            while ((opts.outWidth / scale) > max || (opts.outHeight / scale) > max) {
                scale *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = scale;
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bmp == null) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            bmp.recycle();
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Загружает баннер: сжимает, пишет base64 в RTDB profiles/<id>/bannerB64. */
    public static void uploadBanner(File file, UploadCallback cb) {
        SweetgramFirebaseAuth.ensureSignedIn(() -> uploadBannerInternal(file, cb));
    }

    private static void uploadBannerInternal(File file, UploadCallback cb) {
        try {
            final String b64 = fileToBase64(file);
            if (b64 == null) {
                if (cb != null) cb.onResult(false, null, "encode failed");
                return;
            }
            final String id = idStr(selfId());
            FirebaseDatabase.getInstance().getReference().child("profiles").child(id).child("bannerB64")
                    .setValue(b64, (DatabaseError error, DatabaseReference ref) -> {
                        if (error != null) {
                            FileLog.e("SweetgramBanner write failed: " + error.getMessage());
                            if (cb != null) cb.onResult(false, null, error.getMessage());
                        } else {
                            saveLocalBanner(b64);
                            if (cb != null) cb.onResult(true, b64, null);
                        }
                    });
        } catch (Throwable e) {
            FileLog.e(e);
            if (cb != null) cb.onResult(false, null, e.getMessage());
        }
    }

    /** Читает base64 баннера из RTDB (для себя или чужого пользователя). */
    public static void fetchBanner(long userId, UrlCallback cb) {
        SweetgramFirebaseAuth.ensureSignedIn(() -> fetchBannerInternal(userId, cb));
    }

    private static void fetchBannerInternal(long userId, UrlCallback cb) {
        final String id = idStr(userId);
        try {
            FirebaseDatabase.getInstance().getReference().child("profiles").child(id).child("bannerB64")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            String b64 = snapshot.getValue(String.class);
                            if (cb != null) cb.onResult(b64);
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            FileLog.e("SweetgramBanner fetch cancelled: " + error.getMessage());
                            if (cb != null) cb.onResult(null);
                        }
                    });
        } catch (Throwable e) {
            FileLog.e(e);
            if (cb != null) cb.onResult(null);
        }
    }

    /** Декодирует base64 в фоне и ставит в ImageView. */
    public static void showBanner(String b64, ImageView view) {
        if (b64 == null || view == null) {
            return;
        }
        new Thread(() -> {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    final Bitmap result = bmp;
                    AndroidUtilities.runOnUIThread(() -> {
                        view.setVisibility(View.VISIBLE);
                        view.setImageBitmap(result);
                    });
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }).start();
    }
}
