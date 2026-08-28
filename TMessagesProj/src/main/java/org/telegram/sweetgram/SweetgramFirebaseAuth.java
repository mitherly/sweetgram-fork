package org.telegram.sweetgram;

import com.google.firebase.auth.FirebaseAuth;

import org.telegram.messenger.FileLog;

/**
 * Firebase по умолчанию блокирует неавторизованные чтение/запись в RTDB и Storage,
 * поэтому все наши операции (баннер, аналитика, верификация) молча падают.
 * Решение — анонимный вход: достаточно одного signInAnonymously на запуск.
 * <p>
 * ВАЖНО: в консоли Firebase (Authentication → Sign-in method) должен быть
 * включён провайдер «Anonymous». Если правила RTDB/Storage требуют
 * request.auth != null, без этого ничего не заработает.
 */
public class SweetgramFirebaseAuth {

    private static boolean started = false;

    public static void ensureSignedIn(Runnable onReady) {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                if (onReady != null) {
                    onReady.run();
                }
                return;
            }
            if (!started) {
                started = true;
                auth.signInAnonymously().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FileLog.d("SweetgramFirebaseAuth: signed in anonymously");
                    } else {
                        FileLog.e("SweetgramFirebaseAuth: anonymous sign-in failed: " +
                                (task.getException() != null ? task.getException().getMessage() : "unknown"));
                    }
                    if (onReady != null) {
                        onReady.run();
                    }
                });
            } else {
                if (onReady != null) {
                    onReady.run();
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            if (onReady != null) {
                onReady.run();
            }
        }
    }
}
