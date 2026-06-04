package tempeststudios.lifetimestattracker.compat;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkPayloadCompat {
    public static final ResourceLocation WORLD_IDENTITY =
            new ResourceLocation("lifetime_stat_tracker", "world_identity");
    public static final ResourceLocation WORLD_IDENTITY_V2 =
            new ResourceLocation("lifetime_stat_tracker", "world_identity_v2");
    public static final ResourceLocation WORLD_IDENTITY_REQUEST =
            new ResourceLocation("lifetime_stat_tracker", "world_identity_request");

    private static boolean serverReceiverRegistered = false;

    private NetworkPayloadCompat() {
    }

    public static void registerPayloads() {
        // Legacy Fabric networking uses raw play channels instead of typed payload registration.
    }

    public static synchronized void registerServerReceiver(ServerIdentityRequestHandler handler) {
        if (serverReceiverRegistered) {
            return;
        }

        ServerPlayNetworking.registerGlobalReceiver(WORLD_IDENTITY_REQUEST, (server, player, networkHandler, buf, sender) -> {
            int protocolVersion = 0;
            try {
                protocolVersion = buf.readVarInt();
            } catch (Throwable ignored) {
            }
            handler.receive(player, server, protocolVersion);
        });
        serverReceiverRegistered = true;
    }

    public static boolean canSendIdentity(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, WORLD_IDENTITY_V2)
                || ServerPlayNetworking.canSend(player, WORLD_IDENTITY);
    }

    public static int sendWorldIdentity(ServerPlayer player, int protocolVersion, String worldId, String displayName) {
        if (ServerPlayNetworking.canSend(player, WORLD_IDENTITY_V2)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(protocolVersion);
            buf.writeUtf(worldId, 256);
            buf.writeUtf(displayName, 256);
            ServerPlayNetworking.send(player, WORLD_IDENTITY_V2, buf);
            return protocolVersion;
        }

        if (ServerPlayNetworking.canSend(player, WORLD_IDENTITY)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUtf(worldId, 256);
            buf.writeUtf(displayName, 256);
            ServerPlayNetworking.send(player, WORLD_IDENTITY, buf);
            return 0;
        }

        return -1;
    }

    @FunctionalInterface
    public interface ServerIdentityRequestHandler {
        void receive(ServerPlayer player, MinecraftServer server, int protocolVersion);
    }
}
