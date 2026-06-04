package tempeststudios.lifetimestattracker.compat.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import tempeststudios.lifetimestattracker.LifetimeStatsManager;
import tempeststudios.lifetimestattracker.compat.NetworkPayloadCompat;

public final class ClientNetworkPayloadCompat {
    private static boolean clientReceiversRegistered = false;

    private ClientNetworkPayloadCompat() {
    }

    public static synchronized void registerClientReceivers() {
        if (clientReceiversRegistered) {
            return;
        }

        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloadCompat.WORLD_IDENTITY, (client, networkHandler, buf, sender) -> {
            String worldId = buf.readUtf(256);
            String displayName = buf.readUtf(256);
            client.execute(() -> {
                LifetimeStatsManager.get().onServerWorldIdentity(worldId, displayName);
                LifetimeStatsManager.get().requestStatsNow("server-world-identity", true);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloadCompat.WORLD_IDENTITY_V2, (client, networkHandler, buf, sender) -> {
            int protocolVersion = buf.readVarInt();
            String worldId = buf.readUtf(256);
            String displayName = buf.readUtf(256);
            client.execute(() -> {
                LifetimeStatsManager.get().onServerWorldIdentity(worldId, displayName);
                LifetimeStatsManager.get().requestStatsNow("server-world-identity-v" + protocolVersion, true);
            });
        });
        clientReceiversRegistered = true;
    }

    public static boolean requestServerWorldIdentity(int protocolVersion) {
        if (!ClientPlayNetworking.canSend(NetworkPayloadCompat.WORLD_IDENTITY_REQUEST)) {
            return false;
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(protocolVersion);
        ClientPlayNetworking.send(NetworkPayloadCompat.WORLD_IDENTITY_REQUEST, buf);
        return true;
    }
}
