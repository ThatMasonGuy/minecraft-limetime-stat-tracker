package tempeststudios.lifetimestattracker;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class LifetimeStatTrackerNetworking {
    public static final int PROTOCOL_VERSION = 1;

    private static boolean payloadsRegistered = false;

    private LifetimeStatTrackerNetworking() {
    }

    public static synchronized void registerPayloads() {
        if (payloadsRegistered) {
            return;
        }

        PayloadTypeRegistry.playS2C().register(WorldIdentityPayload.TYPE, WorldIdentityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WorldIdentityV2Payload.TYPE, WorldIdentityV2Payload.CODEC);
        PayloadTypeRegistry.playC2S().register(WorldIdentityRequestPayload.TYPE, WorldIdentityRequestPayload.CODEC);
        payloadsRegistered = true;
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
        public Type<? extends CustomPacketPayload> type() {
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
        public Type<? extends CustomPacketPayload> type() {
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
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
