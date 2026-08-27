package org.telegram.sweetgram.hook;

/**
 * Обработчик вызова метода до (before) и после (after) его выполнения.
 */
public abstract class HookCallback implements IHookCallback {

    public final int priority;

    public HookCallback() {
        this(50);
    }

    public HookCallback(int priority) {
        this.priority = priority;
    }

    @Override
    public void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    @Override
    public void afterHookedMethod(MethodHookParam param) throws Throwable {
    }
}
