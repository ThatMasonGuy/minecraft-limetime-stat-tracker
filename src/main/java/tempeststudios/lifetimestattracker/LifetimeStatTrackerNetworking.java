package tempeststudios.lifetimestattracker;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class LifetimeStatTrackerNetworking {
    private static boolean payloadsRegistered = false;

    private LifetimeStatTrackerNetworking() {
    }

    public static synchronized void registerPayloads() {
        if (payloadsRegistered) {
            return;
        }

        PayloadTypeRegistry.playS2C().register(WorldIdentityPayload.TYPE, WorldIdentityPayload.CODEC);
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
}
