package org.telegram.sweetgram;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/** Общий доступ к ветке Firebase Realtime Database, чтобы писатели и читатели совпадали. */
public class SweetgramDb {
    private SweetgramDb() {}

    public static DatabaseReference ref(String path) {
        return FirebaseDatabase.getInstance().getReference(path);
    }
}
