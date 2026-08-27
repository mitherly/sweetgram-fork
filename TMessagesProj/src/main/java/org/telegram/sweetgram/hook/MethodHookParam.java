package org.telegram.sweetgram.hook;

import java.lang.reflect.Member;

/**
 * Контекст вызова перехваченного метода или конструктора.
 */
public class MethodHookParam {

    public Member method;
    public Object thisObject;
    public Object[] args;

    private Object result = null;
    private Throwable throwable = null;
    private boolean returnEarly = false;

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
        this.throwable = null;
        this.returnEarly = true;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
        this.result = null;
        this.returnEarly = true;
    }

    public Object getResultOrThrowable() throws Throwable {
        if (throwable != null) {
            throw throwable;
        }
        return result;
    }

    public boolean hasResult() {
        return returnEarly;
    }
}
