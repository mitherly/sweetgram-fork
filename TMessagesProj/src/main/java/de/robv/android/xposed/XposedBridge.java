package de.robv.android.xposed;

import org.telegram.sweetgram.hook.SweetgramHook;

import java.lang.reflect.Member;

/**
 * XposedBridge Compatibility Layer for Sweetgram plugins.
 */
public class XposedBridge {

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        SweetgramHook.hookMethod(hookMethod, callback);
        return new XC_MethodHook.Unhook(hookMethod, callback);
    }

    public static void unhookMethod(Member hookMethod, XC_MethodHook callback) {
        // Callback list in SweetgramHook removes on unhook call
    }
}
