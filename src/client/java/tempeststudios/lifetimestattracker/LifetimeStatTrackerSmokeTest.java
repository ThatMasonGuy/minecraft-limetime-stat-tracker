package tempeststudios.lifetimestattracker;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class LifetimeStatTrackerSmokeTest {
    private static final String SMOKE_TEST_PROPERTY = "lifetimestattracker.smokeTest";
    private static final String[] FORCED_MIXIN_TARGETS = {
        "net.minecraft.client.multiplayer.ClientPacketListener",
        "net.minecraft.client.multiplayer.ClientAdvancements"
    };
    private static final int PASS_AFTER_TICKS = 20;

    private static int ticks;
    private static boolean complete;

    private LifetimeStatTrackerSmokeTest() {
    }

    public static void registerIfEnabled() {
        if (!Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            return;
        }

        System.out.println("[LifetimeStatTracker] Automated smoke test armed.");
        forceLoadMixinTargets();
        ClientTickEvents.END_CLIENT_TICK.register(LifetimeStatTrackerSmokeTest::tick);
    }

    private static void forceLoadMixinTargets() {
        ClassLoader classLoader = LifetimeStatTrackerSmokeTest.class.getClassLoader();
        for (String className : FORCED_MIXIN_TARGETS) {
            try {
                Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Smoke test could not load mixin target " + className, e);
            }
        }
    }

    private static void tick(Minecraft client) {
        if (complete) {
            return;
        }

        ticks++;
        if (ticks < PASS_AFTER_TICKS) {
            return;
        }

        complete = true;
        System.out.println(
            "LIFETIMESTATTRACKER_SMOKE_TEST_PASS minecraftProfile="
                + System.getProperty("lifetimestattracker.smokeMinecraftProfile", "unknown")
                + " gameVersion="
                + System.getProperty("lifetimestattracker.smokeGameVersion", "unknown")
                + " releaseProfile="
                + System.getProperty("lifetimestattracker.smokeReleaseProfile", "unknown")
                + " installSet="
                + System.getProperty("lifetimestattracker.smokeInstallSet", "unknown")
                + " injectedMods="
                + System.getProperty("fabric.addMods", "unknown")
        );
        client.stop();
    }
}
