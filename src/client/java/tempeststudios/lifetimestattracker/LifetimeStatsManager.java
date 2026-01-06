package tempeststudios.lifetimestattracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class LifetimeStatsManager {

    private static final String MOD_FOLDER = "lifetime-stat-tracker";
    private static final String TOTALS_FILE = "totals.json";
    private static final String SNAPSHOTS_FILE = "snapshots.json";
    private static final String ADVANCEMENTS_FILE = "advancements.json";
    private static final String WORLD_STATS_FILE = "world_stats.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_LONG_TYPE = new TypeToken<Map<String, Long>>() {
    }.getType();
    private static final Type SNAPSHOT_ROOT_TYPE = new TypeToken<Map<String, Map<String, Long>>>() {
    }.getType();
    private static final Type ADVANCEMENT_TYPE = new TypeToken<Map<String, Set<String>>>() {
    }.getType();
    private static final Type WORLD_STATS_TYPE = new TypeToken<Map<String, WorldStats>>() {
    }.getType();

    private static final LifetimeStatsManager INSTANCE = new LifetimeStatsManager();

    public static LifetimeStatsManager get() {
        return INSTANCE;
    }

    // lifetime totals (append-only)
    private final Map<String, Long> totals = new HashMap<>();

    // per-handle absolute last snapshot (for delta calculation)
    private final Map<String, Map<String, Long>> snapshotsByHandle = new HashMap<>();

    // per-handle cumulative stats (what we've added to totals from each world)
    private final Map<String, WorldStats> worldStats = new HashMap<>();

    // advancement tracking: handle -> set of advancement IDs earned
    private final Map<String, Set<String>> advancementsByHandle = new HashMap<>();

    // session state
    private String currentHandle = "unknown";
    private boolean waitingForFirstStatsPacket = true;

    // throttling
    private long lastRequestMs = 0L;
    private static final long REQUEST_COOLDOWN_MS = 30_000;

    private boolean initialized = false;

    // ─────────────────────────────────────────────────────────────
    // World Stats container
    // ─────────────────────────────────────────────────────────────

    public static class WorldStats {
        public String displayName;
        public long firstSeen;
        public long lastSeen;
        public Map<String, Long> stats = new HashMap<>();

        public WorldStats() {
            this.firstSeen = System.currentTimeMillis();
            this.lastSeen = this.firstSeen;
        }

        public WorldStats(String displayName) {
            this();
            this.displayName = displayName;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────

    public void init() {
        if (initialized)
            return;
        initialized = true;

        ensureFilesExist();
        loadTotals();
        loadSnapshots();
        loadAdvancements();
        loadWorldStats();

        System.out.println("[LifetimeStatTracker] Init complete @ " + Instant.now()
                + " totalsKeys=" + totals.size()
                + " handles=" + snapshotsByHandle.size()
                + " worlds=" + worldStats.size()
                + " advancementWorlds=" + advancementsByHandle.size());
    }

    public void onJoin() {
        currentHandle = computeHandle();
        waitingForFirstStatsPacket = true;
    }

    public void onDisconnect() {
        waitingForFirstStatsPacket = true;
        currentHandle = "unknown";
    }

    // ─────────────────────────────────────────────────────────────
    // Stats request
    // ─────────────────────────────────────────────────────────────

    public void requestStatsNow(String reason) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null)
            return;

        var connection = mc.getConnection();
        if (connection == null)
            return;

        long now = System.currentTimeMillis();
        if (now - lastRequestMs < REQUEST_COOLDOWN_MS)
            return;

        lastRequestMs = now;

        try {
            connection.send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.REQUEST_STATS));
            System.out.println("[LifetimeStatTracker] Requested stats (" + reason + ")");
        } catch (Throwable t) {
            System.out.println("[LifetimeStatTracker] Failed to request stats: " + t);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Stats packet handling
    // ─────────────────────────────────────────────────────────────

    public void onStatsPacket(Object2IntMap<Stat<?>> packetStats) {
        if (!initialized)
            init();

        String handle = computeHandle();

        // Skip invalid handles (happens when joining before server is ready)
        if (handle.equals("unknown") || handle.contains("ResourceKey[")) {
            System.out.println("[LifetimeStatTracker] Skipping stats packet for invalid handle: " + handle);
            return;
        }

        currentHandle = handle;

        Map<String, Long> snapshot = toSnapshot(packetStats);

        if (snapshot.isEmpty()) {
            System.out.println("[LifetimeStatTracker] Stats packet received but snapshot=0 (handle=" + handle + ")");
            return;
        }

        Map<String, Long> prevSnapshot = snapshotsByHandle.get(handle);
        boolean firstTimeHandle = (prevSnapshot == null);

        // Get or create world stats
        WorldStats ws = worldStats.computeIfAbsent(handle, h -> new WorldStats(extractDisplayName(h)));
        ws.lastSeen = System.currentTimeMillis();

        long added = 0;
        int positives = 0;

        if (firstTimeHandle) {
            // First time seeing this handle: seed totals with the baseline snapshot
            for (Map.Entry<String, Long> e : snapshot.entrySet()) {
                long v = e.getValue();
                if (v > 0) {
                    totals.merge(e.getKey(), v, Long::sum);
                    ws.stats.merge(e.getKey(), v, Long::sum);
                    added += v;
                    positives++;
                }
            }
            System.out.println("[LifetimeStatTracker] Baseline-seeded totals ✅ handle=" + handle
                    + " stats=" + snapshot.size()
                    + " seededKeys=" + positives
                    + " seededSum=" + added);
        } else {
            // Normal mode: compute deltas vs previous snapshot
            for (Map.Entry<String, Long> e : snapshot.entrySet()) {
                String key = e.getKey();
                long cur = e.getValue();
                long prev = prevSnapshot.getOrDefault(key, 0L);

                long delta = cur - prev;
                if (delta > 0) {
                    totals.merge(key, delta, Long::sum);
                    ws.stats.merge(key, delta, Long::sum);
                    added += delta;
                    positives++;
                }
            }
            System.out.println("[LifetimeStatTracker] Delta-flush ✅ handle=" + handle
                    + " stats=" + snapshot.size()
                    + " positives=" + positives
                    + " added=" + added);
        }

        // Persist everything
        snapshotsByHandle.put(handle, snapshot);
        saveSnapshots();
        saveTotals();
        saveWorldStats();

        waitingForFirstStatsPacket = false;
    }

    private Map<String, Long> toSnapshot(Object2IntMap<Stat<?>> packetStats) {
        Map<String, Long> out = new HashMap<>();
        for (Object2IntMap.Entry<Stat<?>> e : packetStats.object2IntEntrySet()) {
            Stat<?> stat = e.getKey();
            int value = e.getIntValue();
            if (value <= 0)
                continue;

            String key = statKey(stat);
            out.put(key, (long) value);
        }
        return out;
    }

    private String statKey(Stat<?> stat) {
        try {
            StatType<?> type = stat.getType();
            Object value = stat.getValue();

            String typeId;
            try {
                Object typeKey = net.minecraft.core.registries.BuiltInRegistries.STAT_TYPE.getKey(type);
                typeId = String.valueOf(typeKey);
            } catch (Throwable ignored) {
                try {
                    Object typeIdObj = net.minecraft.core.registries.BuiltInRegistries.STAT_TYPE.getId(type);
                    typeId = String.valueOf(typeIdObj);
                } catch (Throwable ignored2) {
                    typeId = "unknown_stat_type";
                }
            }

            @SuppressWarnings("unchecked")
            var valueRegistry = (net.minecraft.core.Registry<Object>) type.getRegistry();

            String valueId;
            try {
                Object key = valueRegistry.getKey(value);
                valueId = String.valueOf(key);
            } catch (Throwable ignored) {
                try {
                    Object id = valueRegistry.getId(value);
                    valueId = String.valueOf(id);
                } catch (Throwable ignored2) {
                    valueId = "unknown_value";
                }
            }

            return typeId + ":" + valueId;
        } catch (Throwable t) {
            return stat.toString();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Advancement tracking
    // ─────────────────────────────────────────────────────────────

    public void onAdvancementEarned(String advancementId) {
        if (!initialized)
            init();

        String handle = computeHandle();
        if (handle.equals("unknown") || handle.contains("ResourceKey[")) {
            return;
        }

        Set<String> advancements = advancementsByHandle.computeIfAbsent(handle, h -> new HashSet<>());

        if (advancements.add(advancementId)) {
            System.out
                    .println("[LifetimeStatTracker] Advancement earned: " + advancementId + " (handle=" + handle + ")");
            saveAdvancements();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Public API for commands
    // ─────────────────────────────────────────────────────────────

    public String getCurrentHandle() {
        return currentHandle;
    }

    public Map<String, Long> getTotals() {
        return Collections.unmodifiableMap(totals);
    }

    public Map<String, WorldStats> getWorldStats() {
        return Collections.unmodifiableMap(worldStats);
    }

    public Map<String, Set<String>> getAdvancements() {
        return Collections.unmodifiableMap(advancementsByHandle);
    }

    public Set<String> getAllAdvancements() {
        Set<String> all = new HashSet<>();
        for (Set<String> set : advancementsByHandle.values()) {
            all.addAll(set);
        }
        return all;
    }

    public long getTotalStat(String key) {
        return totals.getOrDefault(key, 0L);
    }

    public long getTotalPlayTimeTicks() {
        return totals.getOrDefault("minecraft:custom:minecraft:play_time", 0L);
    }

    public long getTotalTimeSinceDeathTicks() {
        return totals.getOrDefault("minecraft:custom:minecraft:time_since_death", 0L);
    }

    public long getTotalWalkDistanceCm() {
        return totals.getOrDefault("minecraft:custom:minecraft:walk_one_cm", 0L);
    }

    public long getTotalMobKills() {
        return totals.getOrDefault("minecraft:custom:minecraft:mob_kills", 0L);
    }

    public long getTotalDeaths() {
        return totals.getOrDefault("minecraft:custom:minecraft:deaths", 0L);
    }

    public long getTotalJumps() {
        return totals.getOrDefault("minecraft:custom:minecraft:jump", 0L);
    }

    public int getWorldCount() {
        return worldStats.size();
    }

    /**
     * Format ticks to human-readable time string
     */
    public static String formatPlayTime(long ticks) {
        long totalSeconds = ticks / 20;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(days).append("d ");
        if (hours > 0 || days > 0)
            sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0)
            sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Format distance in cm to human-readable string
     */
    public static String formatDistance(long cm) {
        if (cm < 100) {
            return cm + " cm";
        } else if (cm < 100000) {
            return String.format("%.1f m", cm / 100.0);
        } else {
            return String.format("%.2f km", cm / 100000.0);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Handle computation
    // ─────────────────────────────────────────────────────────────

    private String computeHandle() {
        Minecraft mc = Minecraft.getInstance();

        // Multiplayer: server ip:port
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server:" + server.ip;
        }

        // Singleplayer / integrated: include world name + seed if possible
        MinecraftServer integrated = mc.getSingleplayerServer();
        if (integrated != null) {
            try {
                String levelName = integrated.getWorldData().getLevelName();
                long seed = integrated.getWorldData().worldGenOptions().seed();
                return "local:" + levelName + ":" + seed;
            } catch (Throwable ignored) {
            }
        }

        return "unknown";
    }

    private String extractDisplayName(String handle) {
        if (handle.startsWith("server:")) {
            return handle.substring(7);
        } else if (handle.startsWith("local:")) {
            String[] parts = handle.substring(6).split(":");
            return parts.length > 0 ? parts[0] : handle;
        }
        return handle;
    }

    // ─────────────────────────────────────────────────────────────
    // Disk I/O
    // ─────────────────────────────────────────────────────────────

    public Path getModDir() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve(MOD_FOLDER);
    }

    private Path getTotalsPath() {
        return getModDir().resolve(TOTALS_FILE);
    }

    private Path getSnapshotsPath() {
        return getModDir().resolve(SNAPSHOTS_FILE);
    }

    private Path getAdvancementsPath() {
        return getModDir().resolve(ADVANCEMENTS_FILE);
    }

    private Path getWorldStatsPath() {
        return getModDir().resolve(WORLD_STATS_FILE);
    }

    private void ensureFilesExist() {
        try {
            Files.createDirectories(getModDir());
            if (!Files.exists(getTotalsPath())) {
                Files.writeString(getTotalsPath(), "{}", StandardCharsets.UTF_8);
            }
            if (!Files.exists(getSnapshotsPath())) {
                Files.writeString(getSnapshotsPath(), "{}", StandardCharsets.UTF_8);
            }
            if (!Files.exists(getAdvancementsPath())) {
                Files.writeString(getAdvancementsPath(), "{}", StandardCharsets.UTF_8);
            }
            if (!Files.exists(getWorldStatsPath())) {
                Files.writeString(getWorldStatsPath(), "{}", StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to create config files: " + e.getMessage());
        }
    }

    private void loadTotals() {
        try {
            String json = Files.readString(getTotalsPath(), StandardCharsets.UTF_8);
            Map<String, Long> loaded = GSON.fromJson(json, MAP_LONG_TYPE);
            if (loaded != null) {
                totals.clear();
                totals.putAll(loaded);
            }
            System.out.println("[LifetimeStatTracker] totals loaded: " + totals.size() + " keys");
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to load totals: " + e.getMessage());
        }
    }

    private void saveTotals() {
        try {
            Files.writeString(getTotalsPath(), GSON.toJson(totals, MAP_LONG_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to save totals: " + e.getMessage());
        }
    }

    private void loadSnapshots() {
        try {
            String json = Files.readString(getSnapshotsPath(), StandardCharsets.UTF_8);
            Map<String, Map<String, Long>> loaded = GSON.fromJson(json, SNAPSHOT_ROOT_TYPE);
            if (loaded != null) {
                snapshotsByHandle.clear();
                // Filter out invalid handles during load
                for (Map.Entry<String, Map<String, Long>> e : loaded.entrySet()) {
                    if (!e.getKey().contains("ResourceKey[") && !e.getKey().equals("unknown")) {
                        snapshotsByHandle.put(e.getKey(), e.getValue());
                    }
                }
            }
            System.out.println("[LifetimeStatTracker] snapshots loaded: " + snapshotsByHandle.size() + " handles");
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to load snapshots: " + e.getMessage());
        }
    }

    private void saveSnapshots() {
        try {
            Files.writeString(getSnapshotsPath(), GSON.toJson(snapshotsByHandle, SNAPSHOT_ROOT_TYPE),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to save snapshots: " + e.getMessage());
        }
    }

    private void loadAdvancements() {
        try {
            String json = Files.readString(getAdvancementsPath(), StandardCharsets.UTF_8);
            Map<String, Set<String>> loaded = GSON.fromJson(json, ADVANCEMENT_TYPE);
            if (loaded != null) {
                advancementsByHandle.clear();
                advancementsByHandle.putAll(loaded);
            }
            System.out
                    .println("[LifetimeStatTracker] advancements loaded: " + advancementsByHandle.size() + " handles");
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to load advancements: " + e.getMessage());
        }
    }

    private void saveAdvancements() {
        try {
            Files.writeString(getAdvancementsPath(), GSON.toJson(advancementsByHandle, ADVANCEMENT_TYPE),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to save advancements: " + e.getMessage());
        }
    }

    private void loadWorldStats() {
        try {
            String json = Files.readString(getWorldStatsPath(), StandardCharsets.UTF_8);
            Map<String, WorldStats> loaded = GSON.fromJson(json, WORLD_STATS_TYPE);
            if (loaded != null) {
                worldStats.clear();
                worldStats.putAll(loaded);
            }
            System.out.println("[LifetimeStatTracker] world stats loaded: " + worldStats.size() + " worlds");
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to load world stats: " + e.getMessage());
        }
    }

    private void saveWorldStats() {
        try {
            Files.writeString(getWorldStatsPath(), GSON.toJson(worldStats, WORLD_STATS_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to save world stats: " + e.getMessage());
        }
    }
}
