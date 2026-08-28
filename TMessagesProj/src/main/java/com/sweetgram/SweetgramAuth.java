package com.sweetgram;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Верификация Sweetgram.
 *
 * Публичная половина — verified_users: читать её может кто угодно, там
 * номер и подпись галочки. Приватная половина — право писать туда. Раньше
 * это право охранял секрет, зашитый в APK, — то есть не охранял вовсе:
 * секрет вытекает из любой сборки. Теперь право проверяют правила базы по
 * auth.uid: записать может только тот, кто вошёл по Firebase Auth и чей
 * номер лежит в admins. Ни пароля, ни секрета в приложении нет.
 *
 * Разовая настройка (владельцу форка):
 *  1. Firebase Console → Authentication → Sign-in method → Email/Password.
 *  2. Там же → Users → добавить админа (почта + пароль).
 *  3. Realtime Database → в узел admins записать admins/<UID> = true,
 *     где UID — скопированный из консоли номер пользователя.
 *  4. Задеплоить database.rules.json из корня репозитория.
 */
public class SweetgramAuth {
    private static volatile SweetgramAuth Instance;

    // Public, readable by anyone: verified_users/<uid> = "verification text".
    private static final String DB_VERIFIED = "verified_users";
    // Allowlist: admins/<uid> = true. Читается только самим этим uid
    // (правилами), приложение сверяется с ним при входе.
    private static final String DB_ADMINS = "admins";
    // Allowlist по телеграм-ID: tg_admins/<телеграм_id> = true.
    private static final String DB_TG_ADMINS = "tg_admins";

    private final Map<Long, String> verifiedUsers = new HashMap<>();
    private DatabaseReference rootReference;

    public static SweetgramAuth getInstance() {
        SweetgramAuth localInstance = Instance;
        if (localInstance == null) {
            synchronized (SweetgramAuth.class) {
                if (localInstance == null) {
                    Instance = localInstance = new SweetgramAuth();
                }
            }
        }
        return localInstance;
    }

    private SweetgramAuth() {
        try {
            FirebaseDatabase db = FirebaseDatabase.getInstance();
            rootReference = db.getReference();
            loadVerifiedUsers();
        } catch (Exception e) {
            Log.e("SweetgramAuth", "Failed to initialize Firebase DB", e);
        }
    }

    private void loadVerifiedUsers() {
        if (rootReference == null) return;
        rootReference.child(DB_VERIFIED).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                verifiedUsers.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        long userId = Long.parseLong(child.getKey());
                        String text = child.getValue(String.class);
                        if (!TextUtils.isEmpty(text)) {
                            verifiedUsers.put(userId, text);
                        } else {
                            verifiedUsers.remove(userId);
                        }
                    } catch (Exception e) {
                        Log.e("SweetgramAuth", "Error parsing user", e);
                    }
                }
                org.telegram.messenger.FileLog.d("SweetgramAuth: loaded " + verifiedUsers.size() + " verified users");
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    try {
                        NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.mainUserInfoChanged);
                        org.telegram.messenger.AccountInstance.getInstance(a).getNotificationCenter().postNotificationName(
                                org.telegram.messenger.NotificationCenter.updateInterfaces,
                                org.telegram.messenger.MessagesController.UPDATE_MASK_NAME | org.telegram.messenger.MessagesController.UPDATE_MASK_AVATAR);
                    } catch (Throwable ignore) {
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("SweetgramAuth", "Database error: " + error.getMessage());
                org.telegram.messenger.FileLog.e("SweetgramAuth: database read failed - " + error.getMessage());
            }
        });
    }

    public boolean isUserVerified(long userId) {
        return !TextUtils.isEmpty(verifiedUsers.get(userId));
    }

    public String getVerificationText(long userId) {
        String text = verifiedUsers.get(userId);
        return text != null ? text : "";
    }

    /** Результат записи в базу — чтобы админка могла показать ошибку, а не молчать. */
    public interface WriteCallback {
        void onResult(boolean ok, String error);
    }

    // --- вход админа ---

    private FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    /**
     * Есть ли живая сессия Firebase Auth. Это проверка «вошёл ли», а не
     * «админ ли»: список админов живёт в базе и проверяется при входе.
     */
    public boolean isAdminSignedIn() {
        try {
            return auth().getCurrentUser() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Вход по почте и паролю. Успех ещё не значит «админ»: сразу сверяемся
     * с admins/ — чужого выходим и отказываем.
     */
    public void adminSignIn(String email, String password, WriteCallback cb) {
        if (rootReference == null) {
            if (cb != null) cb.onResult(false, "Firebase is not initialized");
            return;
        }
        try {
            auth().signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        final FirebaseUser user = auth().getCurrentUser();
                        if (user == null) {
                            if (cb != null) cb.onResult(false, "no user after sign-in");
                            return;
                        }
                        rootReference.child(DB_ADMINS).child(user.getUid()).get()
                                .addOnSuccessListener(snapshot -> {
                                    if (snapshot.exists()) {
                                        ensureTelegramAdmin(cb);
                                    } else {
                                        auth().signOut();
                                        if (cb != null) cb.onResult(false, "not in the admins list");
                                    }
                                })
                                .addOnFailureListener(error -> {
                                    auth().signOut();
                                    if (cb != null) cb.onResult(false, error.getMessage());
                                });
                    })
                    .addOnFailureListener(error -> {
                        if (cb != null) cb.onResult(false, error.getMessage());
                    });
        } catch (Exception e) {
            if (cb != null) cb.onResult(false, e.getMessage());
        }
    }

    /**
     * Проверка по телеграм-ID. Список живёт в tg_admins/<телеграм_id>, и
     * войти может только тот, чей номер активного аккаунта там записан.
     *
     * Проверка эта клиентская, и врать ей может только распатченный клиент:
     * свой телеграм-ID приложение знает из входа в телеграм, а не со слов
     * человека. Сервер телеграм-ID проверить не может вообще, поэтому право
     * записи по-прежнему держат правила базы через вход Firebase — проверка
     * здесь отвечает на вопрос «чья панель», а правила — «кто может писать».
     */
    public void ensureTelegramAdmin(WriteCallback cb) {
        if (rootReference == null) {
            if (cb != null) cb.onResult(false, "Firebase is not initialized");
            return;
        }
        final long myId = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;
        if (myId == 0) {
            if (cb != null) cb.onResult(false, "no telegram account on this device");
            return;
        }
        try {
            rootReference.child(DB_TG_ADMINS).child(String.valueOf(myId)).get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            if (cb != null) cb.onResult(true, null);
                        } else {
                            auth().signOut();
                            if (cb != null) cb.onResult(false,
                                    "Telegram ID " + myId + " is not in tg_admins");
                        }
                    })
                    .addOnFailureListener(error -> {
                        auth().signOut();
                        if (cb != null) cb.onResult(false, error.getMessage());
                    });
        } catch (Exception e) {
            if (cb != null) cb.onResult(false, e.getMessage());
        }
    }

    /** Телеграм-ID активного аккаунта — панель показывает его в подсказке. */
    public static long myTelegramId() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;
        } catch (Exception e) {
            return 0;
        }
    }

    public void adminSignOut() {
        try {
            auth().signOut();
        } catch (Exception ignored) {
        }
    }

    // --- выдача и отзыв ---

    public void grantVerification(long userId, String text) {
        write(userId, text, null);
    }

    public void grantVerification(long userId, String text, WriteCallback cb) {
        write(userId, text, cb);
    }

    public void revokeVerification(long userId) {
        write(userId, "", null);
    }

    public void revokeVerification(long userId, WriteCallback cb) {
        write(userId, "", cb);
    }

    /**
     * Пишет напрямую в verified_users. Право решают правила базы: без
     * admins/<uid> = true у вошедшего запись не пройдёт, и приложение
     * честно покажет ошибку.
     */
    private void write(long userId, String text, WriteCallback cb) {
        if (rootReference == null) {
            if (cb != null) cb.onResult(false, "Firebase is not initialized");
            return;
        }
        String id = String.valueOf(userId);
        org.telegram.messenger.FileLog.d("SweetgramAuth: writing " + DB_VERIFIED + "/" + id);
        rootReference.child(DB_VERIFIED).child(id).setValue(text, (error, ref) -> {
            if (error != null) {
                org.telegram.messenger.FileLog.e("SweetgramAuth: write failed - " + error.getMessage());
                if (cb != null) cb.onResult(false, error.getMessage());
            } else {
                org.telegram.messenger.FileLog.d("SweetgramAuth: write OK for " + id);
                if (cb != null) cb.onResult(true, null);
            }
        });
    }
}
