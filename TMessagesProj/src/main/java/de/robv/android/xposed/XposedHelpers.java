package de.robv.android.xposed;

import org.telegram.sweetgram.hook.SweetgramHook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * XposedHelpers Compatibility Layer.
 */
public class XposedHelpers {

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return SweetgramHook.findClass(className, classLoader);
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        Class<?>[] paramClasses = getParameterClasses(clazz.getClassLoader(), parameterTypes);
        return SweetgramHook.findMethod(clazz, methodName, paramClasses);
    }

    public static Method findMethodExact(String className, ClassLoader classLoader, String methodName, Object... parameterTypes) {
        Class<?> clazz = findClass(className, classLoader);
        return findMethodExact(clazz, methodName, parameterTypes);
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0 || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("no callback provided");
        }
        XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        Method method = findMethodExact(clazz, methodName, getParameters(parameterTypesAndCallback));
        if (method == null) {
            throw new NoSuchMethodError(clazz.getName() + "#" + methodName);
        }
        return XposedBridge.hookMethod(method, callback);
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        return findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return SweetgramHook.getObjectField(obj, fieldName);
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        SweetgramHook.setObjectField(obj, fieldName, value);
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return SweetgramHook.callMethod(obj, methodName, args);
    }

    private static Object[] getParameters(Object[] parameterTypesAndCallback) {
        Object[] params = new Object[parameterTypesAndCallback.length - 1];
        System.arraycopy(parameterTypesAndCallback, 0, params, 0, params.length);
        return params;
    }

    private static Class<?>[] getParameterClasses(ClassLoader classLoader, Object[] parameterTypes) {
        Class<?>[] classes = new Class<?>[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] instanceof Class<?>) {
                classes[i] = (Class<?>) parameterTypes[i];
            } else if (parameterTypes[i] instanceof String) {
                classes[i] = findClass((String) parameterTypes[i], classLoader);
            }
        }
        return classes;
    }
}
