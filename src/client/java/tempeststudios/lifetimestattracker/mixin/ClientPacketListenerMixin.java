package tempeststudios.lifetimestattracker.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tempeststudios.lifetimestattracker.LifetimeStatsManager;
import tempeststudios.lifetimestattracker.compat.client.StatsPacketCompat;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleAwardStats", at = @At("TAIL"))
    private void lifetimeStatTracker$handleAwardStats(
            ClientboundAwardStatsPacket packet,
            CallbackInfo ci) {
        Minecraft.getInstance().execute(() -> {
            try {
                Object2IntMap<Stat<?>> stats = StatsPacketCompat.stats(packet);
                LifetimeStatsManager.get().onStatsPacket(stats);
            } catch (Throwable t) {
                System.out.println("[LifetimeStatTracker] Failed to read stats packet: " + t);
            }
        });
    }
}
