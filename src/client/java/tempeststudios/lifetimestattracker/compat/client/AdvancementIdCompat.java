package tempeststudios.lifetimestattracker.compat.client;

public final class AdvancementIdCompat {
    private AdvancementIdCompat() {
    }

    public static String idString(Object advancementKey) {
        if (advancementKey == null) {
            return null;
        }

        Object id = invokeNoArg(advancementKey, "id");
        if (id == null) {
            id = invokeNoArg(advancementKey, "getId");
        }
        return id != null ? id.toString() : advancementKey.toString();
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
