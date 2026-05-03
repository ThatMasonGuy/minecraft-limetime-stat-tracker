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
    private String serverWorldId = null;
    private String serverWorldDisplayName = null;
    private boolean waitingForServerWorldIdentity = false;

    // throttling
    private long lastRequestMs = 0L;
    private String lastRequestReason = "none";
    private static final long REQUEST_COOLDOWN_MS = 30_000;
    private static final Set<String> NON_ADDITIVE_STATS = Set.of(
            "minecraft:custom:minecraft:time_since_death",
            "minecraft:custom:minecraft:time_since_rest");

    // debug state
    private long lastStatsPacketMs = 0L;
    private int lastStatsPacketKeys = 0;
    private int lastPositiveDeltaKeys = 0;
    private long lastAddedTotal = 0L;
    private int lastNonAdditiveUpdates = 0;

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
        repairLoadedData();

        System.out.println("[LifetimeStatTracker] Init complete @ " + Instant.now()
                + " totalsKeys=" + totals.size()
                + " handles=" + snapshotsByHandle.size()
                + " worlds=" + worldStats.size()
                + " advancementWorlds=" + advancementsByHandle.size());
    }

    public void onJoin() {
        serverWorldId = null;
        serverWorldDisplayName = null;
        waitingForServerWorldIdentity = isConnectedToMultiplayerServer();
        currentHandle = computeHandle();
        waitingForFirstStatsPacket = true;
    }

    public void onDisconnect() {
        waitingForFirstStatsPacket = true;
        serverWorldId = null;
        serverWorldDisplayName = null;
        waitingForServerWorldIdentity = false;
        currentHandle = "unknown";
    }

    public void onServerWorldIdentity(String worldId, String displayName) {
        serverWorldId = cleanIdentity(worldId);
        serverWorldDisplayName = cleanIdentity(displayName);
        waitingForServerWorldIdentity = false;
        currentHandle = computeHandle();
        waitingForFirstStatsPacket = true;
        System.out.println("[LifetimeStatTracker] Server world identity received: "
                + serverWorldId + " (handle=" + currentHandle + ")");
    }

    // ─────────────────────────────────────────────────────────────
    // Stats request
    // ─────────────────────────────────────────────────────────────

    public void requestStatsNow(String reason) {
        requestStatsNow(reason, false);
    }

    public void requestStatsNow(String reason, boolean force) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null)
            return;

        var connection = mc.getConnection();
        if (connection == null)
            return;

        long now = System.currentTimeMillis();
        if (!force && now - lastRequestMs < REQUEST_COOLDOWN_MS)
            return;

        lastRequestMs = now;
        lastRequestReason = reason;

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

        Map<String, Long> packetSnapshot = toSnapshot(packetStats);

        if (packetSnapshot.isEmpty()) {
            System.out.println("[LifetimeStatTracker] Stats packet received but snapshot=0 (handle=" + handle + ")");
            return;
        }

        Map<String, Long> prevSnapshot = snapshotsByHandle.get(handle);
        boolean firstTimeHandle = (prevSnapshot == null);
        Map<String, Long> cumulativeSnapshot = firstTimeHandle
                ? new HashMap<>()
                : new HashMap<>(prevSnapshot);

        // Get or create world stats
        WorldStats ws = worldStats.computeIfAbsent(handle, h -> new WorldStats(displayNameForHandle(h)));
        sanitizeWorldStats(handle, ws);
        ws.lastSeen = System.currentTimeMillis();

        long added = 0;
        int positives = 0;
        int nonAdditiveUpdates = 0;

        if (firstTimeHandle) {
            // First time seeing this handle: seed totals with the current world/server stats.
            for (Map.Entry<String, Long> e : packetSnapshot.entrySet()) {
                String key = e.getKey();
                long v = e.getValue();
                cumulativeSnapshot.put(key, v);
                if (v > 0 && isAdditiveStat(key)) {
                    totals.merge(key, v, Long::sum);
                    ws.stats.merge(key, v, Long::sum);
                    added += v;
                    positives++;
                } else if (!isAdditiveStat(key)) {
                    nonAdditiveUpdates++;
                }
            }
            System.out.println("[LifetimeStatTracker] Baseline-seeded totals ✅ handle=" + handle
                    + " stats=" + packetSnapshot.size()
                    + " seededKeys=" + positives
                    + " seededSum=" + added
                    + " nonAdditiveUpdates=" + nonAdditiveUpdates);
        } else {
            // Dirty stat packets are partial; absent keys must stay in the snapshot.
            for (Map.Entry<String, Long> e : packetSnapshot.entrySet()) {
                String key = e.getKey();
                long cur = e.getValue();
                long prev = prevSnapshot.getOrDefault(key, 0L);
                cumulativeSnapshot.put(key, cur);

                long delta = cur - prev;
                if (delta > 0 && isAdditiveStat(key)) {
                    totals.merge(key, delta, Long::sum);
                    ws.stats.merge(key, delta, Long::sum);
                    added += delta;
                    positives++;
                } else if (!isAdditiveStat(key)) {
                    nonAdditiveUpdates++;
                }
            }
            System.out.println("[LifetimeStatTracker] Delta-flush ✅ handle=" + handle
                    + " stats=" + packetSnapshot.size()
                    + " positives=" + positives
                    + " added=" + added
                    + " nonAdditiveUpdates=" + nonAdditiveUpdates);
        }

        // Persist everything
        snapshotsByHandle.put(handle, cumulativeSnapshot);
        saveSnapshots();
        saveTotals();
        saveWorldStats();

        lastStatsPacketMs = System.currentTimeMillis();
        lastStatsPacketKeys = packetSnapshot.size();
        lastPositiveDeltaKeys = positives;
        lastAddedTotal = added;
        lastNonAdditiveUpdates = nonAdditiveUpdates;

        waitingForFirstStatsPacket = false;
    }

    private Map<String, Long> toSnapshot(Object2IntMap<Stat<?>> packetStats) {
        Map<String, Long> out = new HashMap<>();
        for (Object2IntMap.Entry<Stat<?>> e : packetStats.object2IntEntrySet()) {
            Stat<?> stat = e.getKey();
            int value = e.getIntValue();
            if (value < 0)
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

        if (isRecipeAdvancement(advancementId)) {
            return;
        }

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

    public Map<String, Long> getCurrentSnapshot() {
        return Collections.unmodifiableMap(snapshotsByHandle.getOrDefault(currentHandle, Collections.emptyMap()));
    }

    public String getLastRequestReason() {
        return lastRequestReason;
    }

    public String getServerWorldIdentityDebug() {
        if (waitingForServerWorldIdentity) {
            return "waiting";
        }
        if (serverWorldId != null && !serverWorldId.isBlank()) {
            return serverWorldId;
        }
        return "none";
    }

    public long getLastRequestMs() {
        return lastRequestMs;
    }

    public long getLastStatsPacketMs() {
        return lastStatsPacketMs;
    }

    public int getLastStatsPacketKeys() {
        return lastStatsPacketKeys;
    }

    public int getLastPositiveDeltaKeys() {
        return lastPositiveDeltaKeys;
    }

    public long getLastAddedTotal() {
        return lastAddedTotal;
    }

    public int getLastNonAdditiveUpdates() {
        return lastNonAdditiveUpdates;
    }

    public String clearStoredDataWithBackup() {
        if (!initialized) {
            init();
        }

        try {
            Files.createDirectories(getModDir());
            Path backupDir = getModDir().resolve("backups").resolve("clear-" + System.currentTimeMillis());
            Files.createDirectories(backupDir);
            backupIfExists(getTotalsPath(), backupDir.resolve(TOTALS_FILE));
            backupIfExists(getSnapshotsPath(), backupDir.resolve(SNAPSHOTS_FILE));
            backupIfExists(getAdvancementsPath(), backupDir.resolve(ADVANCEMENTS_FILE));
            backupIfExists(getWorldStatsPath(), backupDir.resolve(WORLD_STATS_FILE));

            totals.clear();
            snapshotsByHandle.clear();
            worldStats.clear();
            advancementsByHandle.clear();
            currentHandle = computeHandle();
            waitingForFirstStatsPacket = true;
            lastStatsPacketMs = 0L;
            lastStatsPacketKeys = 0;
            lastPositiveDeltaKeys = 0;
            lastAddedTotal = 0L;
            lastNonAdditiveUpdates = 0;

            saveTotals();
            saveSnapshots();
            saveAdvancements();
            saveWorldStats();

            return backupDir.toAbsolutePath().toString();
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to clear data: " + e.getMessage());
            return null;
        }
    }

    public String getWorldDisplayName(String handle, WorldStats stats) {
        if (stats != null && stats.displayName != null && !stats.displayName.isBlank()) {
            return stats.displayName;
        }
        return extractDisplayName(handle);
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
            if (serverWorldId == null || serverWorldId.isBlank()) {
                return "unknown";
            }
            return "server:" + server.ip + ":" + sanitizeHandlePart(serverWorldId);
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
        if (handle == null || handle.isBlank()) {
            return "unknown";
        }
        if (handle.startsWith("server:")) {
            String serverHandle = handle.substring(7);
            int lastSeparator = serverHandle.lastIndexOf(':');
            return lastSeparator >= 0 && lastSeparator < serverHandle.length() - 1
                    ? serverHandle.substring(lastSeparator + 1)
                    : serverHandle;
        } else if (handle.startsWith("local:")) {
            String[] parts = handle.substring(6).split(":", 2);
            return parts.length > 0 ? parts[0] : handle;
        }
        return handle;
    }

    private String displayNameForHandle(String handle) {
        if (handle != null && handle.startsWith("server:")
                && serverWorldDisplayName != null && !serverWorldDisplayName.isBlank()) {
            return serverWorldDisplayName;
        }
        return extractDisplayName(handle);
    }

    private boolean isConnectedToMultiplayerServer() {
        try {
            Minecraft mc = Minecraft.getInstance();
            ServerData server = mc.getCurrentServer();
            return server != null && server.ip != null && !server.ip.isBlank();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String cleanIdentity(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String sanitizeHandlePart(String value) {
        String cleaned = cleanIdentity(value);
        if (cleaned == null) {
            return "unknown_server_world";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = Character.toLowerCase(cleaned.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else if (c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else if (Character.isWhitespace(c)) {
                out.append('_');
            }
        }
        return out.isEmpty() ? "unknown_server_world" : out.toString();
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
                    if (isValidHandle(e.getKey()) && e.getValue() != null) {
                        snapshotsByHandle.put(e.getKey(), new HashMap<>(e.getValue()));
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
            boolean changed = false;
            if (loaded != null) {
                advancementsByHandle.clear();
                for (Map.Entry<String, Set<String>> e : loaded.entrySet()) {
                    if (isValidHandle(e.getKey()) && e.getValue() != null) {
                        Set<String> cleaned = new HashSet<>(e.getValue());
                        changed |= cleaned.removeIf(LifetimeStatsManager::isRecipeAdvancement);
                        advancementsByHandle.put(e.getKey(), cleaned);
                    } else {
                        changed = true;
                    }
                }
            }
            if (changed) {
                saveAdvancements();
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
                for (Map.Entry<String, WorldStats> e : loaded.entrySet()) {
                    if (isValidHandle(e.getKey())) {
                        WorldStats stats = e.getValue() != null ? e.getValue()
                                : new WorldStats(extractDisplayName(e.getKey()));
                        sanitizeWorldStats(e.getKey(), stats);
                        worldStats.put(e.getKey(), stats);
                    }
                }
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

    private void backupIfExists(Path source, Path target) throws java.io.IOException {
        if (Files.exists(source)) {
            Files.copy(source, target);
        }
    }

    private void repairLoadedData() {
        boolean worldStatsChanged = false;
        for (String handle : snapshotsByHandle.keySet()) {
            boolean missingWorldStats = !worldStats.containsKey(handle);
            WorldStats stats = worldStats.computeIfAbsent(handle, h -> {
                WorldStats created = new WorldStats(extractDisplayName(h));
                created.stats.putAll(snapshotsByHandle.getOrDefault(h, Collections.emptyMap()));
                return created;
            });
            worldStatsChanged |= missingWorldStats || sanitizeWorldStats(handle, stats);
        }

        boolean advancementsChanged = false;
        Iterator<Map.Entry<String, Set<String>>> advIterator = advancementsByHandle.entrySet().iterator();
        while (advIterator.hasNext()) {
            Map.Entry<String, Set<String>> e = advIterator.next();
            if (!isValidHandle(e.getKey())) {
                advIterator.remove();
                advancementsChanged = true;
                continue;
            }
            if (e.getValue() == null) {
                e.setValue(new HashSet<>());
                advancementsChanged = true;
                continue;
            }
            advancementsChanged |= e.getValue().removeIf(LifetimeStatsManager::isRecipeAdvancement);
        }

        if (worldStatsChanged) {
            saveWorldStats();
        }
        if (advancementsChanged) {
            saveAdvancements();
        }
    }

    private boolean sanitizeWorldStats(String handle, WorldStats stats) {
        boolean changed = false;
        if (stats.displayName == null || stats.displayName.isBlank()) {
            stats.displayName = extractDisplayName(handle);
            changed = true;
        }
        if (stats.stats == null) {
            stats.stats = new HashMap<>();
            changed = true;
        }
        if (stats.firstSeen <= 0L) {
            stats.firstSeen = System.currentTimeMillis();
            changed = true;
        }
        if (stats.lastSeen <= 0L) {
            stats.lastSeen = stats.firstSeen;
            changed = true;
        }
        return changed;
    }

    private static boolean isAdditiveStat(String key) {
        return !NON_ADDITIVE_STATS.contains(key);
    }

    private static boolean isRecipeAdvancement(String advancementId) {
        return advancementId != null && advancementId.contains(":recipes/");
    }

    private static boolean isValidHandle(String handle) {
        return handle != null
                && !handle.isBlank()
                && !handle.equals("unknown")
                && !handle.contains("ResourceKey[");
    }
}
