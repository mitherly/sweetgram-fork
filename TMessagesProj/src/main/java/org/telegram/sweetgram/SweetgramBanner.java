package org.telegram.sweetgram;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

/**
 * Загрузка/чтение баннера профиля в облако (Firebase Storage) и его URL
 * в Realtime Database. Общий код для ProfileActivity, чтобы не дублировать Firebase-логику.
 */
public class SweetgramBanner {

    public static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final String PREF_NAME = "sweetgram";
    private static final String KEY_BANNER_URL = "banner_url";

    public interface UrlCallback {
        void onResult(String url);
    }

    public interface UploadCallback {
        void onResult(boolean ok, String url, String error);
    }

    public static long selfId() {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    public static String idStr(long userId) {
        return String.valueOf(Math.abs(userId));
    }

    public static void saveLocalBannerUrl(String url) {
        try {
            ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
                    .edit().putString(KEY_BANNER_URL, url).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String getLocalBannerUrl() {
        try {
            return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
                    .getString(KEY_BANNER_URL, null);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Загружает файл в Storage/profiles/<id>/banner и пишет URL в RTDB/profiles/<id>/bannerUrl. */
    public static void uploadBanner(File file, UploadCallback cb) {
        SweetgramFirebaseAuth.ensureSignedIn(() -> uploadBannerInternal(file, cb));
    }

    private static void uploadBannerInternal(File file, UploadCallback cb) {
        try {
            final String id = idStr(selfId());
            StorageReference ref = FirebaseStorage.getInstance().getReference()
                    .child("profiles").child(id).child("banner");
            UploadTask task = ref.putFile(Uri.fromFile(file));
            task.continueWithTask(upload -> {
                if (!upload.isSuccessful()) {
                    if (upload.getException() != null) {
                        throw upload.getException();
                    }
                    throw new RuntimeException("banner upload failed");
                }
                return ref.getDownloadUrl();
            }).addOnCompleteListener(t -> {
                if (t.isSuccessful()) {
                    String url = t.getResult().toString();
                    try {
                        FirebaseDatabase.getInstance().getReference().child("profiles").child(id).child("bannerUrl").setValue(url);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    saveLocalBannerUrl(url);
                    if (cb != null) cb.onResult(true, url, null);
                } else {
                    String err = t.getException() != null ? t.getException().getMessage() : "banner upload failed";
                    FileLog.e("SweetgramBanner upload failed: " + err);
                    if (cb != null) cb.onResult(false, null, err);
                }
            });
        } catch (Throwable e) {
            FileLog.e(e);
            if (cb != null) cb.onResult(false, null, e.getMessage());
        }
    }

    /** Читает profiles/<userId>/bannerUrl из RTDB (для чужого пользователя или себя). */
    public static void fetchBannerUrl(long userId, UrlCallback cb) {
        SweetgramFirebaseAuth.ensureSignedIn(() -> fetchBannerUrlInternal(userId, cb));
    }

    private static void fetchBannerUrlInternal(long userId, UrlCallback cb) {
        final String id = idStr(userId);
        try {
            FirebaseDatabase.getInstance().getReference().child("profiles").child(id).child("bannerUrl")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            String url = snapshot.getValue(String.class);
                            if (cb != null) cb.onResult(url);
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

    /** Грузит картинку по URL (Firebase Storage download URL) в фоновом потоке и ставит в ImageView. */
    public static void loadBitmap(String url, ImageView view) {
        new Thread(() -> {
            try {
                InputStream in = new URL(url).openStream();
                Bitmap bmp = BitmapFactory.decodeStream(in);
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ignore) {
                    }
                }
                if (bmp != null) {
                    final Bitmap result = bmp;
                    AndroidUtilities.runOnUIThread(() -> {
                        view.setVisibility(android.view.View.VISIBLE);
                        view.setImageBitmap(result);
                    });
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }).start();
    }
}
