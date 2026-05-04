package tempeststudios.lifetimestattracker;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LifetimeStatTracker implements DedicatedServerModInitializer {
    private static final List<ScheduledIdentitySend> SCHEDULED_SENDS = new ArrayList<>();

    @Override
    public void onInitializeServer() {
        LifetimeStatTrackerNetworking.registerPayloads();

        ServerPlayNetworking.registerGlobalReceiver(
                LifetimeStatTrackerNetworking.WorldIdentityRequestPayload.TYPE,
                (payload, context) -> context.server().execute(() -> sendWorldIdentity(
                        context.player(),
                        context.server(),
                        "client-request-v" + payload.protocolVersion())));

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
                        || source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId()))
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
        if (!ServerPlayNetworking.canSend(player, LifetimeStatTrackerNetworking.WorldIdentityPayload.TYPE)
                && !ServerPlayNetworking.canSend(player, LifetimeStatTrackerNetworking.WorldIdentityV2Payload.TYPE)) {
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
        boolean canSendV2 = ServerPlayNetworking.canSend(player, LifetimeStatTrackerNetworking.WorldIdentityV2Payload.TYPE);
        boolean canSendV1 = ServerPlayNetworking.canSend(player, LifetimeStatTrackerNetworking.WorldIdentityPayload.TYPE);
        if (!canSendV1 && !canSendV2) {
            return;
        }

        IdentityInfo identity = currentWorldIdentity(server);
        if (canSendV2) {
            ServerPlayNetworking.send(player,
                    new LifetimeStatTrackerNetworking.WorldIdentityV2Payload(
                            LifetimeStatTrackerNetworking.PROTOCOL_VERSION,
                            identity.worldId(),
                            identity.displayName()));
        } else {
            ServerPlayNetworking.send(player,
                    new LifetimeStatTrackerNetworking.WorldIdentityPayload(identity.worldId(), identity.displayName()));
        }
        System.out.println("[LifetimeStatTracker] Sent world identity to "
                + player.getName().getString() + ": " + identity.worldId()
                + " (" + identity.displayName() + ")"
                + " reason=" + reason
                + " protocol=" + (canSendV2 ? LifetimeStatTrackerNetworking.PROTOCOL_VERSION : 0)
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
            Path fileName = server.getServerDirectory().getFileName();
            if (fileName != null) {
                String serverDir = fileName.toString();
                if (!serverDir.isBlank()) {
                    return new IdValue(serverDir, "serverDirectory");
                }
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
