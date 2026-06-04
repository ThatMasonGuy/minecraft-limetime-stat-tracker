package tempeststudios.lifetimestattracker.compat;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkPayloadCompat {
    private static boolean payloadsRegistered = false;
    private static boolean serverReceiverRegistered = false;

    private NetworkPayloadCompat() {
    }

    public static synchronized void registerPayloads() {
        if (payloadsRegistered) {
            return;
        }

        PayloadTypeRegistry.clientboundPlay().register(WorldIdentityPayload.TYPE, WorldIdentityPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorldIdentityV2Payload.TYPE, WorldIdentityV2Payload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WorldIdentityRequestPayload.TYPE, WorldIdentityRequestPayload.CODEC);
        payloadsRegistered = true;
    }

    public static synchronized void registerServerReceiver(ServerIdentityRequestHandler handler) {
        if (serverReceiverRegistered) {
            return;
        }

        ServerPlayNetworking.registerGlobalReceiver(WorldIdentityRequestPayload.TYPE, (payload, context) ->
                handler.receive(context.player(), context.server(), payload.protocolVersion()));
        serverReceiverRegistered = true;
    }

    public static boolean canSendIdentity(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, WorldIdentityV2Payload.TYPE)
                || ServerPlayNetworking.canSend(player, WorldIdentityPayload.TYPE);
    }

    public static int sendWorldIdentity(ServerPlayer player, int protocolVersion, String worldId, String displayName) {
        if (ServerPlayNetworking.canSend(player, WorldIdentityV2Payload.TYPE)) {
            ServerPlayNetworking.send(player, new WorldIdentityV2Payload(protocolVersion, worldId, displayName));
            return protocolVersion;
        }

        if (ServerPlayNetworking.canSend(player, WorldIdentityPayload.TYPE)) {
            ServerPlayNetworking.send(player, new WorldIdentityPayload(worldId, displayName));
            return 0;
        }

        return -1;
    }

    @FunctionalInterface
    public interface ServerIdentityRequestHandler {
        void receive(ServerPlayer player, MinecraftServer server, int protocolVersion);
    }

    public record WorldIdentityPayload(String worldId, String displayName) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WorldIdentityPayload> TYPE =
                CustomPacketPayload.createType("lifetime_stat_tracker/world_identity");
        public static final StreamCodec<RegistryFriendlyByteBuf, WorldIdentityPayload> CODEC =
                CustomPacketPayload.codec(
                        (payload, buf) -> {
                            buf.writeUtf(payload.worldId(), 256);
                            buf.writeUtf(payload.displayName(), 256);
                        },
                        buf -> new WorldIdentityPayload(buf.readUtf(256), buf.readUtf(256)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WorldIdentityV2Payload(int protocolVersion, String worldId, String displayName)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WorldIdentityV2Payload> TYPE =
                CustomPacketPayload.createType("lifetime_stat_tracker/world_identity_v2");
        public static final StreamCodec<RegistryFriendlyByteBuf, WorldIdentityV2Payload> CODEC =
                CustomPacketPayload.codec(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.protocolVersion());
                            buf.writeUtf(payload.worldId(), 256);
                            buf.writeUtf(payload.displayName(), 256);
                        },
                        buf -> new WorldIdentityV2Payload(buf.readVarInt(), buf.readUtf(256), buf.readUtf(256)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WorldIdentityRequestPayload(int protocolVersion) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WorldIdentityRequestPayload> TYPE =
                CustomPacketPayload.createType("lifetime_stat_tracker/world_identity_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, WorldIdentityRequestPayload> CODEC =
                CustomPacketPayload.codec(
                        (payload, buf) -> buf.writeVarInt(payload.protocolVersion()),
                        buf -> new WorldIdentityRequestPayload(buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
