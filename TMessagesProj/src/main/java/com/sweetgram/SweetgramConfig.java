package com.sweetgram;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public class SweetgramConfig {
    private static final Object sync = new Object();

    public static SharedPreferences preferences;
    public static SharedPreferences.Editor editor;

    public static long localEmojiStatusDoc;
    public static int localEmojiStatusUntil;

    public static void load() {
        synchronized (sync) {
            if (preferences != null || ApplicationLoader.applicationContext == null) {
                return;
            }
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("sweetgramconfig", Activity.MODE_PRIVATE);
            editor = preferences.edit();
            localEmojiStatusDoc = preferences.getLong("localEmojiStatusDoc", 0);
            localEmojiStatusUntil = preferences.getInt("localEmojiStatusUntil", 0);
        }
    }
}
