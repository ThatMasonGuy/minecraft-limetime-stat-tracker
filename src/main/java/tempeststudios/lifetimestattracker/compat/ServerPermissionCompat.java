package tempeststudios.lifetimestattracker.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public final class ServerPermissionCompat {
    private ServerPermissionCompat() {
    }

    public static boolean isOp(MinecraftServer server, ServerPlayer player) {
        Object playerList = server.getPlayerList();
        return isOp(playerList, invoke(player, "nameAndId"))
                || isOp(playerList, invoke(player, "getGameProfile"));
    }

    private static boolean isOp(Object playerList, Object identity) {
        if (playerList == null || identity == null) {
            return false;
        }

        try {
            Method method = findIsOp(playerList.getClass(), identity.getClass());
            if (method == null) {
                return false;
            }
            Object result = method.invoke(playerList, identity);
            return result instanceof Boolean value && value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findIsOp(Class<?> playerListClass, Class<?> identityClass) {
        for (Method method : playerListClass.getMethods()) {
            if (method.getName().equals("isOp")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(identityClass)) {
                return method;
            }
        }
        return null;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
