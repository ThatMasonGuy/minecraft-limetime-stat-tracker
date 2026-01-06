package tempeststudios.lifetimestattracker.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tempeststudios.lifetimestattracker.LifetimeStatsManager;

import java.util.Map;

@Mixin(ClientAdvancements.class)
public class ClientAdvancementsMixin {

    @Shadow
    @Final
    private Map<AdvancementHolder, AdvancementProgress> progress;

    @Inject(method = "update", at = @At("TAIL"))
    private void lifetimeStatTracker$onAdvancementUpdate(
            ClientboundUpdateAdvancementsPacket packet,
            CallbackInfo ci) {
        Minecraft.getInstance().execute(() -> {
            try {
                for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progress.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().isDone()) {
                        String advId = entry.getKey().id().toString();
                        
                        // Skip recipe unlocks - only track actual advancements
                        if (advId.contains("recipes/")) {
                            continue;
                        }
                        
                        LifetimeStatsManager.get().onAdvancementEarned(advId);
                    }
                }
            } catch (Throwable t) {
                System.out.println("[LifetimeStatTracker] Failed to track advancement: " + t);
            }
        });
    }
}