package tempeststudios.lifetimestattracker.compat.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.stats.Stat;

public final class StatsPacketCompat {
    private StatsPacketCompat() {
    }

    public static Object2IntMap<Stat<?>> stats(ClientboundAwardStatsPacket packet) {
        return packet.stats();
    }
}
