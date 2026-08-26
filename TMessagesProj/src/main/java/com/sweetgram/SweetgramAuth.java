package com.sweetgram;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;
import java.util.Map;

public class SweetgramAuth {
    private static volatile SweetgramAuth Instance;

    // Public, readable by anyone: verified_users/<uid> = "verification text".
    private static final String DB_VERIFIED = "verified_users";
    // Private, never readable: verified_meta/<uid>/k holds the write secret.
    private static final String DB_META = "verified_meta";

    // Secret that must accompany every write. It is sent in verified_meta/<uid>/k,
    // which has ".read": false, so it never leaks to readers. The database rules
    // only allow writing verified_users/<uid> when verified_meta/<uid>/k equals
    // this value (within the same atomic update), so verifications cannot be
    // granted by writing to the database directly without knowing the secret.
    // NOTE: this lives in the APK, so it stops casual abuse, not a determined
    // reverse-engineer. For real protection, move grant/revoke to a server.
    private static final String ADMIN_SECRET = "sweetgram_admin_secret_9f3a21c7";

    private final Map<Long, String> verifiedUsers = new HashMap<>();
    private DatabaseReference rootReference;

    public static SweetgramAuth getInstance() {
        SweetgramAuth localInstance = Instance;
        if (localInstance == null) {
            synchronized (SweetgramAuth.class) {
                localInstance = Instance;
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

    public void grantVerification(long userId, String text) {
        write(userId, text);
    }

    public void grantVerification(long userId, String text, WriteCallback cb) {
        write(userId, text, cb);
    }

    public void revokeVerification(long userId) {
        write(userId, "");
    }

    public void revokeVerification(long userId, WriteCallback cb) {
        write(userId, "", cb);
    }

    private void write(long userId, String text) {
        write(userId, text, null);
    }

    private void write(long userId, String text, WriteCallback cb) {
        if (rootReference == null) {
            if (cb != null) cb.onResult(false, "Firebase is not initialized");
            return;
        }
        String id = String.valueOf(userId);
        Map<String, Object> updates = new HashMap<>();
        updates.put(DB_VERIFIED + "/" + id, text);
        updates.put(DB_META + "/" + id + "/k", ADMIN_SECRET);
        org.telegram.messenger.FileLog.d("SweetgramAuth: writing " + DB_VERIFIED + "/" + id);
        rootReference.updateChildren(updates, (error, ref) -> {
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
