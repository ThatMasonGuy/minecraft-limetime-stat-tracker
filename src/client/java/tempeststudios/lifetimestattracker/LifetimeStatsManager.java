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
import tempeststudios.lifetimestattracker.compat.ServerPathCompat;
import tempeststudios.lifetimestattracker.compat.client.RegistryKeyCompat;

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
    private static final String AGGREGATE_WORLD_ID = "aggregate";
    private static final String MANUAL_WORLD_MARKER = ":manual:";

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
    private String lastKnownHandle = "unknown";
    private long disconnectGraceUntilMs = 0L;
    private long serverIdentityFallbackAtMs = 0L;
    private String preIdentityAggregateHandle = null;
    private boolean preIdentityAggregateCreated = false;
    private String pendingManualSeedName = null;
    private String pendingManualSeedHandle = null;

    // throttling
    private long lastRequestMs = 0L;
    private String lastRequestReason = "none";
    private static final long REQUEST_COOLDOWN_MS = 10_000;
    private static final Set<String> NON_ADDITIVE_STATS = Set.of(
            "minecraft:custom:minecraft:time_since_death",
            "minecraft:custom:minecraft:time_since_rest");

    // debug state
    private long lastStatsPacketMs = 0L;
    private int lastStatsPacketKeys = 0;
    private int lastPositiveDeltaKeys = 0;
    private long lastAddedTotal = 0L;
    private int lastNonAdditiveUpdates = 0;
    private int lastHighWaterSkips = 0;

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
        serverIdentityFallbackAtMs = waitingForServerWorldIdentity ? System.currentTimeMillis() + 3_000L : 0L;
        disconnectGraceUntilMs = 0L;
        lastKnownHandle = "unknown";
        preIdentityAggregateHandle = null;
        preIdentityAggregateCreated = false;
        pendingManualSeedName = null;
        pendingManualSeedHandle = null;
        currentHandle = computeHandle();
        waitingForFirstStatsPacket = true;
    }

    public void onDisconnect() {
        if (isValidHandle(currentHandle)) {
            lastKnownHandle = currentHandle;
            disconnectGraceUntilMs = System.currentTimeMillis() + 5_000L;
        }
        waitingForFirstStatsPacket = true;
    }

    public void expireDisconnectGrace() {
        if (disconnectGraceUntilMs > 0L && System.currentTimeMillis() > disconnectGraceUntilMs) {
            disconnectGraceUntilMs = 0L;
            lastKnownHandle = "unknown";
            serverWorldId = null;
            serverWorldDisplayName = null;
            waitingForServerWorldIdentity = false;
            serverIdentityFallbackAtMs = 0L;
            currentHandle = "unknown";
        }
    }

    public void onServerWorldIdentity(String worldId, String displayName) {
        serverWorldId = cleanIdentity(worldId);
        serverWorldDisplayName = cleanIdentity(displayName);
        waitingForServerWorldIdentity = false;
        serverIdentityFallbackAtMs = 0L;
        currentHandle = computeHandle();
        reconcilePreIdentityAggregate(currentHandle);
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
        lastKnownHandle = handle;

        Map<String, Long> packetSnapshot = toSnapshot(packetStats);

        if (packetSnapshot.isEmpty()) {
            System.out.println("[LifetimeStatTracker] Stats packet received but snapshot=0 (handle=" + handle + ")");
            return;
        }

        if (pendingManualSeedHandle != null && isAggregateHandle(handle)) {
            applyPendingManualSeed(packetSnapshot);
            saveSnapshots();
            saveTotals();
            saveWorldStats();
            lastStatsPacketMs = System.currentTimeMillis();
            lastStatsPacketKeys = packetSnapshot.size();
            lastPositiveDeltaKeys = 0;
            lastAddedTotal = 0L;
            lastNonAdditiveUpdates = 0;
            lastHighWaterSkips = 0;
            waitingForFirstStatsPacket = false;
            return;
        }

        Map<String, Long> prevSnapshot = snapshotsByHandle.get(handle);
        boolean firstTimeHandle = (prevSnapshot == null);
        if (waitingForServerWorldIdentity && isAggregateHandle(handle)) {
            preIdentityAggregateHandle = handle;
            preIdentityAggregateCreated = firstTimeHandle;
        }
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
        int highWaterSkips = 0;

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

                long delta = cur - prev;
                if (delta > 0 && isAdditiveStat(key)) {
                    cumulativeSnapshot.put(key, cur);
                    totals.merge(key, delta, Long::sum);
                    ws.stats.merge(key, delta, Long::sum);
                    added += delta;
                    positives++;
                } else if (!isAdditiveStat(key)) {
                    cumulativeSnapshot.put(key, cur);
                    nonAdditiveUpdates++;
                } else if (usesHighWaterSnapshots(handle)) {
                    highWaterSkips++;
                } else {
                    cumulativeSnapshot.put(key, cur);
                }
            }
            System.out.println("[LifetimeStatTracker] Delta-flush ✅ handle=" + handle
                    + " stats=" + packetSnapshot.size()
                    + " positives=" + positives
                    + " added=" + added
                    + " nonAdditiveUpdates=" + nonAdditiveUpdates
                    + " highWaterSkips=" + highWaterSkips);
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
        lastHighWaterSkips = highWaterSkips;

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

            String typeId = RegistryKeyCompat.keyString(
                    net.minecraft.core.registries.BuiltInRegistries.STAT_TYPE,
                    type,
                    "unknown_stat_type");
            String valueId = RegistryKeyCompat.keyString(type.getRegistry(), value, "unknown_value");

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

    public OperationResult queueManualSeed(String name) {
        if (!initialized) {
            init();
        }

        String aggregateHandle = computeHandle();
        if (!isAggregateHandle(aggregateHandle)) {
            return OperationResult.error("Manual seed is only available on unmodded server/Realm aggregate handles.");
        }

        String cleanedName = cleanIdentity(name);
        if (cleanedName == null) {
            return OperationResult.error("Seed name cannot be blank.");
        }

        pendingManualSeedName = cleanedName;
        pendingManualSeedHandle = manualHandleForAggregate(aggregateHandle, cleanedName);
        requestStatsNow("manual-seed-" + sanitizeHandlePart(cleanedName), true);
        return OperationResult.ok("Queued seed for '" + cleanedName + "'. It will apply to the next stats packet.");
    }

    public OperationResult removeWorld(String query) {
        if (!initialized) {
            init();
        }

        String cleanedQuery = cleanIdentity(query);
        if (cleanedQuery == null) {
            return OperationResult.error("World name cannot be blank.");
        }

        List<String> matches = findMatchingWorldHandles(cleanedQuery);
        if (matches.isEmpty()) {
            return OperationResult.error("No tracked world found matching: " + cleanedQuery);
        }
        if (matches.size() > 1) {
            return OperationResult.error("Ambiguous world name. Be more specific: " + String.join(", ", matches));
        }

        String handle = matches.get(0);
        try {
            Path backupDir = backupDataFiles("remove-" + System.currentTimeMillis());
            WorldStats removedStats = worldStats.remove(handle);
            snapshotsByHandle.remove(handle);
            advancementsByHandle.remove(handle);

            long subtracted = 0L;
            int keys = 0;
            if (removedStats != null && removedStats.stats != null) {
                for (Map.Entry<String, Long> e : removedStats.stats.entrySet()) {
                    String key = e.getKey();
                    long value = e.getValue() != null ? e.getValue() : 0L;
                    if (value <= 0 || !isAdditiveStat(key)) {
                        continue;
                    }
                    long current = totals.getOrDefault(key, 0L);
                    long updated = Math.max(0L, current - value);
                    if (updated == 0L) {
                        totals.remove(key);
                    } else {
                        totals.put(key, updated);
                    }
                    subtracted += Math.min(current, value);
                    keys++;
                }
            }

            if (handle.equals(currentHandle)) {
                currentHandle = "unknown";
            }
            saveTotals();
            saveSnapshots();
            saveAdvancements();
            saveWorldStats();
            return OperationResult.ok("Removed " + handle + ", subtracted " + subtracted
                    + " across " + keys + " stat keys. Backup: " + backupDir.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("[LifetimeStatTracker] Failed to remove world: " + e.getMessage());
            return OperationResult.error("Remove failed. Check latest.log.");
        }
    }

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

    public int getLastHighWaterSkips() {
        return lastHighWaterSkips;
    }

    public String clearStoredDataWithBackup() {
        if (!initialized) {
            init();
        }

        try {
            Path backupDir = backupDataFiles("clear-" + System.currentTimeMillis());

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
            lastHighWaterSkips = 0;

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
                if (waitingForServerWorldIdentity && System.currentTimeMillis() < serverIdentityFallbackAtMs) {
                    return "unknown";
                }
                return "server:" + sanitizeHandlePart(server.ip) + ":" + AGGREGATE_WORLD_ID;
            }
            return "server:" + server.ip + ":" + sanitizeHandlePart(serverWorldId);
        }

        // Singleplayer / integrated: include world name + seed if possible
        MinecraftServer integrated = mc.getSingleplayerServer();
        if (integrated != null) {
            try {
                String levelName = integrated.getWorldData().getLevelName();
                Long seed = integratedSeed(integrated);
                String stableId = integratedLevelId(integrated);
                String handle = "local:" + sanitizeHandlePart(stableId != null ? stableId : levelName);
                if (seed != null) {
                    migrateLocalHandleAlias("local:" + levelName + ":" + seed, handle);
                }
                return handle;
            } catch (Throwable ignored) {
            }
        }

        if (isValidHandle(lastKnownHandle) && disconnectGraceUntilMs > 0L
                && System.currentTimeMillis() <= disconnectGraceUntilMs) {
            return lastKnownHandle;
        }
        return "unknown";
    }

    private String extractDisplayName(String handle) {
        if (handle == null || handle.isBlank()) {
            return "unknown";
        }
        if (handle.startsWith("server:")) {
            String serverHandle = handle.substring(7);
            int manualIndex = serverHandle.indexOf(MANUAL_WORLD_MARKER);
            if (manualIndex >= 0) {
                return serverHandle.substring(manualIndex + MANUAL_WORLD_MARKER.length());
            }
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
        if (isAggregateHandle(handle)) {
            String serverName = currentServerName();
            if (serverName != null) {
                return serverName + " (aggregate)";
            }
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

    private void applyPendingManualSeed(Map<String, Long> packetSnapshot) {
        String handle = pendingManualSeedHandle;
        String name = pendingManualSeedName;
        pendingManualSeedHandle = null;
        pendingManualSeedName = null;

        if (handle == null || packetSnapshot.isEmpty()) {
            return;
        }

        Map<String, Long> prevSnapshot = snapshotsByHandle.get(handle);
        Map<String, Long> cumulativeSnapshot = prevSnapshot == null ? new HashMap<>() : new HashMap<>(prevSnapshot);
        WorldStats ws = worldStats.computeIfAbsent(handle, h -> new WorldStats(name));
        ws.displayName = name;
        ws.lastSeen = System.currentTimeMillis();

        long added = 0L;
        int positives = 0;
        for (Map.Entry<String, Long> e : packetSnapshot.entrySet()) {
            String key = e.getKey();
            long cur = e.getValue();
            long prev = cumulativeSnapshot.getOrDefault(key, 0L);
            if (!isAdditiveStat(key)) {
                cumulativeSnapshot.put(key, cur);
                continue;
            }
            if (cur > prev) {
                long delta = cur - prev;
                cumulativeSnapshot.put(key, cur);
                totals.merge(key, delta, Long::sum);
                ws.stats.merge(key, delta, Long::sum);
                added += delta;
                positives++;
            }
        }

        snapshotsByHandle.put(handle, cumulativeSnapshot);
        System.out.println("[LifetimeStatTracker] Manual seed applied handle=" + handle
                + " positives=" + positives + " added=" + added);
    }

    private List<String> findMatchingWorldHandles(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> exact = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        for (Map.Entry<String, WorldStats> e : worldStats.entrySet()) {
            String handle = e.getKey();
            String display = getWorldDisplayName(handle, e.getValue());
            String handleLower = handle.toLowerCase(Locale.ROOT);
            String displayLower = display.toLowerCase(Locale.ROOT);
            if (handleLower.equals(normalized) || displayLower.equals(normalized)) {
                exact.add(handle);
            } else if (handleLower.contains(normalized) || displayLower.contains(normalized)) {
                partial.add(handle);
            }
        }
        return !exact.isEmpty() ? exact : partial;
    }

    private Path backupDataFiles(String name) throws java.io.IOException {
        Files.createDirectories(getModDir());
        Path backupDir = getModDir().resolve("backups").resolve(name);
        Files.createDirectories(backupDir);
        backupIfExists(getTotalsPath(), backupDir.resolve(TOTALS_FILE));
        backupIfExists(getSnapshotsPath(), backupDir.resolve(SNAPSHOTS_FILE));
        backupIfExists(getAdvancementsPath(), backupDir.resolve(ADVANCEMENTS_FILE));
        backupIfExists(getWorldStatsPath(), backupDir.resolve(WORLD_STATS_FILE));
        return backupDir;
    }

    private static boolean usesHighWaterSnapshots(String handle) {
        return isAggregateHandle(handle) || isManualSeedHandle(handle);
    }

    private static boolean isAggregateHandle(String handle) {
        return handle != null && handle.startsWith("server:") && handle.endsWith(":" + AGGREGATE_WORLD_ID);
    }

    private static boolean isManualSeedHandle(String handle) {
        return handle != null && handle.startsWith("server:") && handle.contains(MANUAL_WORLD_MARKER);
    }

    private static String manualHandleForAggregate(String aggregateHandle, String name) {
        String base = aggregateHandle.substring(0, aggregateHandle.length() - AGGREGATE_WORLD_ID.length());
        return base + "manual:" + sanitizeHandlePart(name);
    }

    private String currentServerName() {
        try {
            ServerData server = Minecraft.getInstance().getCurrentServer();
            if (server != null && server.name != null && !server.name.isBlank()) {
                return server.name;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String integratedLevelId(MinecraftServer server) {
        try {
            java.lang.reflect.Field storageSourceField = MinecraftServer.class.getDeclaredField("storageSource");
            storageSourceField.setAccessible(true);
            Object storageSource = storageSourceField.get(server);
            Object levelId = storageSource.getClass().getMethod("getLevelId").invoke(storageSource);
            if (levelId instanceof String id && !id.isBlank()) {
                return id;
            }
        } catch (Throwable ignored) {
        }

        try {
            String id = ServerPathCompat.serverDirectoryName(server);
            if (id != null && !id.isBlank()) {
                return id;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Long integratedSeed(MinecraftServer server) {
        try {
            Object worldData = server.getWorldData();
            Object worldGenOptions = worldData.getClass().getMethod("worldGenOptions").invoke(worldData);
            Object seed = worldGenOptions.getClass().getMethod("seed").invoke(worldGenOptions);
            if (seed instanceof Long value) {
                return value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void migrateLocalHandleAlias(String oldHandle, String newHandle) {
        if (oldHandle == null || oldHandle.equals(newHandle) || snapshotsByHandle.containsKey(newHandle)) {
            return;
        }
        if (!snapshotsByHandle.containsKey(oldHandle) && !worldStats.containsKey(oldHandle)
                && !advancementsByHandle.containsKey(oldHandle)) {
            return;
        }

        Map<String, Long> snapshot = snapshotsByHandle.remove(oldHandle);
        if (snapshot != null) {
            snapshotsByHandle.put(newHandle, snapshot);
            saveSnapshots();
        }
        WorldStats stats = worldStats.remove(oldHandle);
        if (stats != null) {
            worldStats.put(newHandle, stats);
            saveWorldStats();
        }
        Set<String> advancements = advancementsByHandle.remove(oldHandle);
        if (advancements != null) {
            advancementsByHandle.put(newHandle, advancements);
            saveAdvancements();
        }
        System.out.println("[LifetimeStatTracker] Migrated local handle " + oldHandle + " -> " + newHandle);
    }

    private void reconcilePreIdentityAggregate(String moddedHandle) {
        String aggregateHandle = preIdentityAggregateHandle;
        boolean aggregateCreated = preIdentityAggregateCreated;
        preIdentityAggregateHandle = null;
        preIdentityAggregateCreated = false;

        if (!isValidHandle(moddedHandle) || aggregateHandle == null || moddedHandle.equals(aggregateHandle)
                || snapshotsByHandle.containsKey(moddedHandle)) {
            return;
        }

        Map<String, Long> aggregateSnapshot = snapshotsByHandle.get(aggregateHandle);
        if (aggregateSnapshot == null) {
            return;
        }

        if (aggregateCreated) {
            Map<String, Long> snapshot = snapshotsByHandle.remove(aggregateHandle);
            if (snapshot != null) {
                snapshotsByHandle.put(moddedHandle, snapshot);
            }
            WorldStats stats = worldStats.remove(aggregateHandle);
            if (stats != null) {
                stats.displayName = displayNameForHandle(moddedHandle);
                worldStats.put(moddedHandle, stats);
            }
            Set<String> advancements = advancementsByHandle.remove(aggregateHandle);
            if (advancements != null) {
                advancementsByHandle.put(moddedHandle, advancements);
            }
            System.out.println("[LifetimeStatTracker] Promoted pre-identity aggregate handle "
                    + aggregateHandle + " -> " + moddedHandle);
        } else {
            snapshotsByHandle.put(moddedHandle, new HashMap<>(aggregateSnapshot));
            worldStats.put(moddedHandle, new WorldStats(displayNameForHandle(moddedHandle)));
            System.out.println("[LifetimeStatTracker] Seeded modded handle snapshot from pre-identity aggregate "
                    + aggregateHandle + " -> " + moddedHandle);
        }

        saveSnapshots();
        saveAdvancements();
        saveWorldStats();
    }

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
        boolean totalsChanged = removeNonAdditiveStats(totals);
        boolean worldStatsChanged = false;
        for (String handle : snapshotsByHandle.keySet()) {
            boolean missingWorldStats = !worldStats.containsKey(handle);
            WorldStats stats = worldStats.computeIfAbsent(handle, h -> {
                WorldStats created = new WorldStats(extractDisplayName(h));
                created.stats.putAll(snapshotsByHandle.getOrDefault(h, Collections.emptyMap()));
                return created;
            });
            worldStatsChanged |= removeNonAdditiveStats(stats.stats);
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
        if (totalsChanged) {
            saveTotals();
        }
        if (advancementsChanged) {
            saveAdvancements();
        }
    }

    private static boolean removeNonAdditiveStats(Map<String, Long> stats) {
        if (stats == null) {
            return false;
        }
        boolean changed = false;
        for (String key : NON_ADDITIVE_STATS) {
            changed |= stats.remove(key) != null;
        }
        return changed;
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

    public static class OperationResult {
        public final boolean success;
        public final String message;

        private OperationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static OperationResult ok(String message) {
            return new OperationResult(true, message);
        }

        public static OperationResult error(String message) {
            return new OperationResult(false, message);
        }
    }
}
