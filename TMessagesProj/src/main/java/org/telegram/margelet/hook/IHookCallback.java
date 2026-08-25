package org.telegram.margelet.hook;

/**
 * Интерфейс хука для полной совместимости с Chaquopy dynamic_proxy.
 */
public interface IHookCallback {
    void beforeHookedMethod(MethodHookParam param) throws Throwable;
    void afterHookedMethod(MethodHookParam param) throws Throwable;
}
