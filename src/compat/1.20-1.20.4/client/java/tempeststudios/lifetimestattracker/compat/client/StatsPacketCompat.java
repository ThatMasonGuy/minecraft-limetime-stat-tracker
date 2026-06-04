package tempeststudios.lifetimestattracker.compat.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.stats.Stat;

import java.util.Map;

public final class StatsPacketCompat {
    private StatsPacketCompat() {
    }

    public static Object2IntMap<Stat<?>> stats(ClientboundAwardStatsPacket packet) {
        Object2IntOpenHashMap<Stat<?>> stats = new Object2IntOpenHashMap<>();
        for (Map.Entry<Stat<?>, Integer> entry : packet.getStats().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                stats.put(entry.getKey(), entry.getValue());
            }
        }
        return stats;
    }
}
