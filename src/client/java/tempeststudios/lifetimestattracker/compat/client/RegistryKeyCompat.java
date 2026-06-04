package tempeststudios.lifetimestattracker.compat.client;

import java.lang.reflect.Method;

public final class RegistryKeyCompat {
    private RegistryKeyCompat() {
    }

    public static String keyString(Object registry, Object value, String fallback) {
        Object key = invokeRegistryMethod(registry, "getKey", value);
        if (key == null) {
            key = invokeRegistryMethod(registry, "getId", value);
        }
        return key != null ? key.toString() : fallback;
    }

    private static Object invokeRegistryMethod(Object registry, String methodName, Object value) {
        if (registry == null || value == null) {
            return null;
        }

        try {
            Method method = findOneArgMethod(registry.getClass(), methodName);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(registry, value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findOneArgMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }
}
