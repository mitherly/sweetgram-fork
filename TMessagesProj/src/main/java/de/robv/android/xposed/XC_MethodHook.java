package de.robv.android.xposed;

import org.telegram.margelet.hook.HookCallback;
import org.telegram.margelet.hook.MethodHookParam;

import java.lang.reflect.Member;

/**
 * Xposed API Compatibility Layer for Margelet.
 */
public abstract class XC_MethodHook extends HookCallback {

    public static class MethodHookParam extends org.telegram.margelet.hook.MethodHookParam {
    }

    public void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
    }

    public void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
    }

    @Override
    public void beforeHookedMethod(org.telegram.margelet.hook.MethodHookParam param) throws Throwable {
        if (param instanceof XC_MethodHook.MethodHookParam) {
            beforeHookedMethod((XC_MethodHook.MethodHookParam) param);
        } else {
            XC_MethodHook.MethodHookParam p = new XC_MethodHook.MethodHookParam();
            p.method = param.method;
            p.thisObject = param.thisObject;
            p.args = param.args;
            beforeHookedMethod(p);
            if (p.hasResult()) {
                if (p.getThrowable() != null) {
                    param.setThrowable(p.getThrowable());
                } else {
                    param.setResult(p.getResult());
                }
            }
        }
    }

    @Override
    public void afterHookedMethod(org.telegram.margelet.hook.MethodHookParam param) throws Throwable {
        if (param instanceof XC_MethodHook.MethodHookParam) {
            afterHookedMethod((XC_MethodHook.MethodHookParam) param);
        } else {
            XC_MethodHook.MethodHookParam p = new XC_MethodHook.MethodHookParam();
            p.method = param.method;
            p.thisObject = param.thisObject;
            p.args = param.args;
            if (param.getThrowable() != null) {
                p.setThrowable(param.getThrowable());
            } else {
                p.setResult(param.getResult());
            }
            afterHookedMethod(p);
            if (p.hasResult()) {
                if (p.getThrowable() != null) {
                    param.setThrowable(p.getThrowable());
                } else {
                    param.setResult(p.getResult());
                }
            }
        }
    }

    public static class Unhook {
        private final Member hookMethod;
        private final XC_MethodHook callback;

        public Unhook(Member hookMethod, XC_MethodHook callback) {
            this.hookMethod = hookMethod;
            this.callback = callback;
        }

        public Member getHookedMethod() {
            return hookMethod;
        }

        public XC_MethodHook getCallback() {
            return callback;
        }

        public void unhook() {
            XposedBridge.unhookMethod(hookMethod, callback);
        }
    }

    public XC_MethodHook() {
        super(50);
    }

    public XC_MethodHook(int priority) {
        super(priority);
    }
}
