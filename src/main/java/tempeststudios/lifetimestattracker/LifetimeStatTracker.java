package tempeststudios.lifetimestattracker;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import tempeststudios.lifetimestattracker.compat.NetworkPayloadCompat;
import tempeststudios.lifetimestattracker.compat.ServerPermissionCompat;
import tempeststudios.lifetimestattracker.compat.ServerPathCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LifetimeStatTracker implements DedicatedServerModInitializer {
    private static final List<ScheduledIdentitySend> SCHEDULED_SENDS = new ArrayList<>();

    @Override
    public void onInitializeServer() {
        NetworkPayloadCompat.registerPayloads();

        NetworkPayloadCompat.registerServerReceiver((player, server, protocolVersion) ->
                server.execute(() -> sendWorldIdentity(player, server, "client-request-v" + protocolVersion)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            sendWorldIdentity(player, server, "join");
            scheduleWorldIdentity(player, server, 20, "join-delayed-1s");
            scheduleWorldIdentity(player, server, 60, "join-delayed-3s");
        });

        ServerTickEvents.END_SERVER_TICK.register(this::sendScheduledIdentities);
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    private void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("lstserver")
                .requires(source -> source.getPlayer() == null
                        || ServerPermissionCompat.isOp(source.getServer(), source.getPlayer()))
                .then(Commands.literal("identity")
                        .executes(ctx -> {
                            IdentityInfo identity = currentWorldIdentity(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "LifetimeStatTracker identity: " + identity.worldId()
                                            + " (" + identity.displayName()
                                            + "), idSource=" + identity.idSource()
                                            + ", nameSource=" + identity.nameSource()
                                            + ", protocol=" + LifetimeStatTrackerNetworking.PROTOCOL_VERSION),
                                    false);
                            return 1;
                        })));
    }

    private void scheduleWorldIdentity(ServerPlayer player, MinecraftServer server, int delayTicks, String reason) {
        if (!NetworkPayloadCompat.canSendIdentity(player)) {
            return;
        }
        SCHEDULED_SENDS.add(new ScheduledIdentitySend(player.getUUID(), server.getTickCount() + delayTicks, reason));
    }

    private void sendScheduledIdentities(MinecraftServer server) {
        Iterator<ScheduledIdentitySend> iterator = SCHEDULED_SENDS.iterator();
        while (iterator.hasNext()) {
            ScheduledIdentitySend send = iterator.next();
            if (server.getTickCount() < send.tick()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(send.playerUuid());
            if (player != null) {
                sendWorldIdentity(player, server, send.reason());
            }
        }
    }

    private static void sendWorldIdentity(ServerPlayer player, MinecraftServer server, String reason) {
        IdentityInfo identity = currentWorldIdentity(server);
        int sentProtocol = NetworkPayloadCompat.sendWorldIdentity(
                player,
                LifetimeStatTrackerNetworking.PROTOCOL_VERSION,
                identity.worldId(),
                identity.displayName());
        if (sentProtocol < 0) {
            return;
        }

        System.out.println("[LifetimeStatTracker] Sent world identity to "
                + player.getName().getString() + ": " + identity.worldId()
                + " (" + identity.displayName() + ")"
                + " reason=" + reason
                + " protocol=" + sentProtocol
                + " idSource=" + identity.idSource()
                + " nameSource=" + identity.nameSource());
    }

    private static IdentityInfo currentWorldIdentity(MinecraftServer server) {
        IdValue id = currentWorldId(server);
        IdValue name = currentWorldName(server);
        return new IdentityInfo(id.value(), name.value(), id.source(), name.source());
    }

    private static IdValue currentWorldId(MinecraftServer server) {
        try {
            java.lang.reflect.Field storageSourceField = MinecraftServer.class.getDeclaredField("storageSource");
            storageSourceField.setAccessible(true);
            Object storageSource = storageSourceField.get(server);
            Object levelId = storageSource.getClass().getMethod("getLevelId").invoke(storageSource);
            if (levelId instanceof String id && !id.isBlank()) {
                return new IdValue(id, "storageSource.levelId");
            }
        } catch (Throwable ignored) {
        }

        IdValue name = currentWorldName(server);
        return new IdValue(name.value(), "fallback." + name.source());
    }

    private static IdValue currentWorldName(MinecraftServer server) {
        try {
            String levelName = server.getWorldData().getLevelName();
            if (levelName != null && !levelName.isBlank()) {
                return new IdValue(levelName, "worldData.levelName");
            }
        } catch (Throwable ignored) {
        }

        try {
            String serverDir = ServerPathCompat.serverDirectoryName(server);
            if (serverDir != null && !serverDir.isBlank()) {
                return new IdValue(serverDir, "serverDirectory");
            }
        } catch (Throwable ignored) {
        }

        return new IdValue("unknown_server_world", "fallback.literal");
    }

    private record IdentityInfo(String worldId, String displayName, String idSource, String nameSource) {
    }

    private record IdValue(String value, String source) {
    }

    private record ScheduledIdentitySend(java.util.UUID playerUuid, int tick, String reason) {
    }
}
