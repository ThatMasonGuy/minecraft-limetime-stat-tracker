package tempeststudios.lifetimestattracker;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

public final class LifetimeStatTrackerServerSmokeTest {
    private static final String SMOKE_TEST_PROPERTY = "lifetimestattracker.smokeTest";
    private static final int PASS_AFTER_TICKS = 20;

    private static int ticks;
    private static boolean complete;

    private LifetimeStatTrackerServerSmokeTest() {
    }

    public static void registerIfEnabled() {
        if (!Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            return;
        }

        System.out.println("[LifetimeStatTracker] Automated server smoke test armed.");
        ServerTickEvents.END_SERVER_TICK.register(LifetimeStatTrackerServerSmokeTest::tick);
    }

    private static void tick(MinecraftServer server) {
        if (complete) {
            return;
        }

        ticks++;
        if (ticks < PASS_AFTER_TICKS) {
            return;
        }

        complete = true;
        String identitySummary = LifetimeStatTracker.smokeWorldIdentitySummary(server);
        if (!hasCommand(server, "lstserver")) {
            throw new IllegalStateException("Server smoke test could not find /lstserver in the command dispatcher.");
        }

        System.out.println(
                "LIFETIMESTATTRACKER_SERVER_SMOKE_TEST_PASS minecraftProfile="
                        + System.getProperty("lifetimestattracker.smokeMinecraftProfile", "unknown")
                        + " gameVersion="
                        + System.getProperty("lifetimestattracker.smokeGameVersion", "unknown")
                        + " releaseProfile="
                        + System.getProperty("lifetimestattracker.smokeReleaseProfile", "unknown")
                        + " installSet="
                        + System.getProperty("lifetimestattracker.smokeInstallSet", "unknown")
                        + " " + identitySummary
                        + " commandRegistered=true"
                        + " injectedMods="
                        + System.getProperty("fabric.addMods", "unknown")
        );
        stopServer(server);
    }

    private static boolean hasCommand(MinecraftServer server, String commandName) {
        try {
            Object commands = invokeNoArg(server, "getCommands");
            Object dispatcher = invokeNoArg(commands, "getDispatcher");
            Object root = invokeNoArg(dispatcher, "getRoot");
            Method getChild = root.getClass().getMethod("getChild", String.class);
            return getChild.invoke(root, commandName) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static void stopServer(MinecraftServer server) {
        if (invokeStop(server, "halt", new Class<?>[]{boolean.class}, new Object[]{false})) {
            return;
        }
        if (invokeStop(server, "stopServer", new Class<?>[0], new Object[0])) {
            return;
        }
        if (invokeStop(server, "stop", new Class<?>[0], new Object[0])) {
            return;
        }
        throw new IllegalStateException("Server smoke test could not stop the Minecraft server.");
    }

    private static boolean invokeStop(
            MinecraftServer server,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments) {
        try {
            Method method = findMethod(methodName, parameterTypes);
            method.invoke(server, arguments);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(String methodName, Class<?>[] parameterTypes) throws NoSuchMethodException {
        try {
            return MinecraftServer.class.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            Method method = MinecraftServer.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }
}
