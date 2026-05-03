package tempeststudios.lifetimestattracker;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class LifetimeStatTracker implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        LifetimeStatTrackerNetworking.registerPayloads();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (!ServerPlayNetworking.canSend(player, LifetimeStatTrackerNetworking.WorldIdentityPayload.TYPE)) {
                return;
            }

            String worldId = currentWorldId(server);
            String worldName = currentWorldName(server);
            ServerPlayNetworking.send(player,
                    new LifetimeStatTrackerNetworking.WorldIdentityPayload(worldId, worldName));
            System.out.println("[LifetimeStatTracker] Sent world identity to "
                    + player.getName().getString() + ": " + worldId + " (" + worldName + ")");
        });
    }

    private static String currentWorldId(MinecraftServer server) {
        try {
            java.lang.reflect.Field storageSourceField = MinecraftServer.class.getDeclaredField("storageSource");
            storageSourceField.setAccessible(true);
            Object storageSource = storageSourceField.get(server);
            Object levelId = storageSource.getClass().getMethod("getLevelId").invoke(storageSource);
            if (levelId instanceof String id && !id.isBlank()) {
                return id;
            }
        } catch (Throwable ignored) {
        }

        return currentWorldName(server);
    }

    private static String currentWorldName(MinecraftServer server) {
        try {
            String levelName = server.getWorldData().getLevelName();
            if (levelName != null && !levelName.isBlank()) {
                return levelName;
            }
        } catch (Throwable ignored) {
        }

        try {
            String serverDir = server.getServerDirectory().getFileName().toString();
            if (serverDir != null && !serverDir.isBlank()) {
                return serverDir;
            }
        } catch (Throwable ignored) {
        }

        return "unknown_server_world";
    }
}
