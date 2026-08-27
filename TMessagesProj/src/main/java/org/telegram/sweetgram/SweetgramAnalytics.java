package org.telegram.sweetgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

/**
 * Лёгкая аналитика форка поверх общего Firebase Realtime Database проекта
 * (того же, что используется для верификации пользователей).
 * <p>
 * Пишем только агрегаты в ветку stats/, чтобы не нарушать приватность:
 * - stats/launches — монотонный счётчик запусков (runTransaction против гонок)
 * - stats/users/<userId> = ServerValue.TIMESTAMP — уникальных считаем по детям
 * - stats/installs — инкремент только при первом запуске на устройстве
 */
public class SweetgramAnalytics {

    private static final String TAG = "SweetgramAnalytics";
    private static final String PREF_NAME = "sweetgram";
    private static final String PREF_INSTALLED = "analytics_installed";

    private static volatile boolean called = false;

    /** Дёрнуть один раз при старте процесса. Повторные вызовы игнорируются. */
    public static void trackLaunch() {
        if (called) {
            return;
        }
        called = true;
        try {
            DatabaseReference root = SweetgramDb.ref("");

            increment(root.child("stats").child("launches"));

            long userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            root.child("stats").child("users").child(String.valueOf(Math.abs(userId)))
                    .setValue(ServerValue.TIMESTAMP, (DatabaseError error, DatabaseReference ref) -> {
                        if (error != null) {
                            Log.e(TAG, "users setValue failed: " + error.getMessage());
                            FileLog.e("SweetgramAnalytics users write error: " + error.getMessage(), error.toException());
                        }
                    });

            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (!prefs.getBoolean(PREF_INSTALLED, false)) {
                increment(root.child("stats").child("installs"));
                prefs.edit().putBoolean(PREF_INSTALLED, true).apply();
            }
        } catch (Throwable e) {
            Log.e(TAG, "trackLaunch failed", e);
            FileLog.e("trackLaunch failed", e);
        }
        Log.i(TAG, "trackLaunch ran, userId=" + UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
    }

    private static void increment(DatabaseReference ref) {
        ref.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                Long value = currentData.getValue(Long.class);
                if (value == null) {
                    value = 0L;
                }
                currentData.setValue(value + 1);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    Log.e(TAG, "increment failed: " + error.getMessage());
                    FileLog.e("SweetgramAnalytics increment error: " + error.getMessage(), error.toException());
                }
            }
        });
    }
}
