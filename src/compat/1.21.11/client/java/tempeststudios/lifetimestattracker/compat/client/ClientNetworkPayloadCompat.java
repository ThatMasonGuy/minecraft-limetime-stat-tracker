package tempeststudios.lifetimestattracker.compat.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

        ClientPlayNetworking.registerGlobalReceiver(
                NetworkPayloadCompat.WorldIdentityPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    LifetimeStatsManager.get().onServerWorldIdentity(payload.worldId(), payload.displayName());
                    LifetimeStatsManager.get().requestStatsNow("server-world-identity", true);
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                NetworkPayloadCompat.WorldIdentityV2Payload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    LifetimeStatsManager.get().onServerWorldIdentity(payload.worldId(), payload.displayName());
                    LifetimeStatsManager.get().requestStatsNow("server-world-identity-v" + payload.protocolVersion(), true);
                }));
        clientReceiversRegistered = true;
    }

    public static boolean requestServerWorldIdentity(int protocolVersion) {
        if (!ClientPlayNetworking.canSend(NetworkPayloadCompat.WorldIdentityRequestPayload.TYPE)) {
            return false;
        }
        ClientPlayNetworking.send(new NetworkPayloadCompat.WorldIdentityRequestPayload(protocolVersion));
        return true;
    }
}
