package tempeststudios.lifetimestattracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import tempeststudios.lifetimestattracker.compat.NetworkPayloadCompat;
import tempeststudios.lifetimestattracker.compat.client.ClientCommandCompat;
import tempeststudios.lifetimestattracker.compat.client.ClientNetworkPayloadCompat;

import java.util.Map;
import java.util.Set;

public class LifetimeStatTrackerClient implements ClientModInitializer {

    private int requestCountdownTicks = -1;
    private int identityRequestCountdownTicks = -1;
    private int identityRequestSecondCountdownTicks = -1;

    @Override
    public void onInitializeClient() {
        NetworkPayloadCompat.registerPayloads();
        ClientNetworkPayloadCompat.registerClientReceivers();
        LifetimeStatsManager.get().init();
        LifetimeStatTrackerSmokeTest.registerIfEnabled();

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LifetimeStatsManager.get().onJoin();
            System.out.println("[LifetimeStatTracker] JOIN detected. Requesting stats...");

            requestServerWorldIdentity("join-immediate");
            identityRequestCountdownTicks = 20;
            identityRequestSecondCountdownTicks = 60;

            // Request immediately
            LifetimeStatsManager.get().requestStatsNow("join-immediate", true);

            // And again ~2s later
            requestCountdownTicks = 40;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LifetimeStatsManager.get().requestStatsNow("disconnect-final", true);
            LifetimeStatsManager.get().onDisconnect();
            System.out.println("[LifetimeStatTracker] DISCONNECT detected.");
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // delayed request
            if (requestCountdownTicks >= 0) {
                requestCountdownTicks--;
                if (requestCountdownTicks == 0) {
                    LifetimeStatsManager.get().requestStatsNow("join-delayed", true);
                    requestCountdownTicks = -1;
                }
            }

            if (identityRequestCountdownTicks >= 0) {
                identityRequestCountdownTicks--;
                if (identityRequestCountdownTicks == 0) {
                    requestServerWorldIdentity("join-delayed-1s");
                    identityRequestCountdownTicks = -1;
                }
            }

            if (identityRequestSecondCountdownTicks >= 0) {
                identityRequestSecondCountdownTicks--;
                if (identityRequestSecondCountdownTicks == 0) {
                    requestServerWorldIdentity("join-delayed-3s");
                    identityRequestSecondCountdownTicks = -1;
                }
            }

            // periodic request (throttled inside manager)
            if (client.player != null) {
                LifetimeStatsManager.get().requestStatsNow("periodic");
            }
            LifetimeStatsManager.get().expireDisconnectGrace();
        });

        System.out.println("[LifetimeStatTracker] Loaded ✅");
        System.out.println("[LifetimeStatTracker] Writing to: " + LifetimeStatsManager.get().getModDir().toAbsolutePath());
    }

    private void requestServerWorldIdentity(String reason) {
        try {
            if (ClientNetworkPayloadCompat.requestServerWorldIdentity(LifetimeStatTrackerNetworking.PROTOCOL_VERSION)) {
                System.out.println("[LifetimeStatTracker] Requested server world identity (" + reason + ")");
            }
        } catch (Throwable t) {
            System.out.println("[LifetimeStatTracker] Failed to request server world identity: " + t);
        }
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            ClientCommandCompat.literal("lifetimestats")
                .executes(this::showSummary)
                .then(ClientCommandCompat.literal("time")
                    .executes(this::showPlayTime))
                .then(ClientCommandCompat.literal("worlds")
                    .executes(this::showWorlds))
                .then(ClientCommandCompat.literal("world")
                    .then(ClientCommandCompat.argument("name", StringArgumentType.greedyString())
                        .executes(this::showWorldStats)))
                .then(ClientCommandCompat.literal("seed")
                    .then(ClientCommandCompat.literal("world")
                        .then(ClientCommandCompat.argument("name", StringArgumentType.greedyString())
                            .executes(this::seedWorld))))
                .then(ClientCommandCompat.literal("remove")
                    .then(ClientCommandCompat.literal("world")
                        .then(ClientCommandCompat.argument("name", StringArgumentType.greedyString())
                            .executes(this::removeWorld))))
                .then(ClientCommandCompat.literal("advancements")
                    .executes(this::showAdvancements))
                .then(ClientCommandCompat.literal("current")
                    .executes(this::showCurrentWorld))
                .then(ClientCommandCompat.literal("debug")
                    .executes(this::showDebug))
                .then(ClientCommandCompat.literal("clear")
                    .executes(this::clearStoredData))
                .then(ClientCommandCompat.literal("help")
                    .executes(this::showHelp))
        );

        // Short alias
        dispatcher.register(
            ClientCommandCompat.literal("lst")
                .executes(this::showSummary)
                .then(ClientCommandCompat.literal("time")
                    .executes(this::showPlayTime))
                .then(ClientCommandCompat.literal("worlds")
                    .executes(this::showWorlds))
                .then(ClientCommandCompat.literal("world")
                    .then(ClientCommandCompat.argument("name", StringArgumentType.greedyString())
                        .executes(this::showWorldStats)))
                .then(ClientCommandCompat.literal("seed")
                    .then(ClientCommandCompat.literal("world")
                        .then(ClientCommandCompat.argument("name", StringArgumentType.greedyString())
                            .executes(this::seedWorld))))
                .then(ClientCommandCompat.literal("remove")
                    .then(ClientCommandCompat.literal("world")
                        .then(ClientCommandCompat.argument("name", StringArgumentType.greedyString())
                            .executes(this::removeWorld))))
                .then(ClientCommandCompat.literal("advancements")
                    .executes(this::showAdvancements))
                .then(ClientCommandCompat.literal("current")
                    .executes(this::showCurrentWorld))
                .then(ClientCommandCompat.literal("debug")
                    .executes(this::showDebug))
                .then(ClientCommandCompat.literal("clear")
                    .executes(this::clearStoredData))
                .then(ClientCommandCompat.literal("help")
                    .executes(this::showHelp))
        );
    }

    private int showSummary(CommandContext<FabricClientCommandSource> ctx) {
        LifetimeStatsManager mgr = LifetimeStatsManager.get();

        MutableComponent header = Component.literal("═══ Lifetime Stats ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        ctx.getSource().sendFeedback(header);

        long playTime = mgr.getTotalPlayTimeTicks();
        ctx.getSource().sendFeedback(Component.literal("⏱ Play Time: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(LifetimeStatsManager.formatPlayTime(playTime)).withStyle(ChatFormatting.WHITE)));

        long distance = mgr.getTotalWalkDistanceCm();
        ctx.getSource().sendFeedback(Component.literal("🚶 Distance Walked: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(LifetimeStatsManager.formatDistance(distance)).withStyle(ChatFormatting.WHITE)));

        long jumps = mgr.getTotalJumps();
        ctx.getSource().sendFeedback(Component.literal("⬆ Jumps: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.format("%,d", jumps)).withStyle(ChatFormatting.WHITE)));

        long mobKills = mgr.getTotalMobKills();
        ctx.getSource().sendFeedback(Component.literal("⚔ Mob Kills: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.format("%,d", mobKills)).withStyle(ChatFormatting.WHITE)));

        long deaths = mgr.getTotalDeaths();
        ctx.getSource().sendFeedback(Component.literal("💀 Deaths: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.format("%,d", deaths)).withStyle(ChatFormatting.WHITE)));

        int worldCount = mgr.getWorldCount();
        ctx.getSource().sendFeedback(Component.literal("🌍 Worlds Played: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.valueOf(worldCount)).withStyle(ChatFormatting.WHITE)));

        int advCount = mgr.getAllAdvancements().size();
        ctx.getSource().sendFeedback(Component.literal("🏆 Unique Advancements: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.valueOf(advCount)).withStyle(ChatFormatting.WHITE)));

        ctx.getSource().sendFeedback(Component.literal("Use /lst help for more commands").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        return 1;
    }

    private int showPlayTime(CommandContext<FabricClientCommandSource> ctx) {
        LifetimeStatsManager mgr = LifetimeStatsManager.get();

        MutableComponent header = Component.literal("═══ Play Time Breakdown ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        ctx.getSource().sendFeedback(header);

        long totalPlayTime = mgr.getTotalPlayTimeTicks();
        ctx.getSource().sendFeedback(Component.literal("Total Play Time: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(LifetimeStatsManager.formatPlayTime(totalPlayTime)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));

        // Show per-world breakdown
        Map<String, LifetimeStatsManager.WorldStats> worlds = mgr.getWorldStats();
        if (!worlds.isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal(""));
            ctx.getSource().sendFeedback(Component.literal("By World:").withStyle(ChatFormatting.YELLOW));

            worlds.forEach((handle, ws) -> {
                Map<String, Long> stats = safeStats(ws);
                long worldTime = stats.getOrDefault("minecraft:custom:minecraft:play_time", 0L);
                if (worldTime > 0) {
                    String displayName = mgr.getWorldDisplayName(handle, ws);
                    ctx.getSource().sendFeedback(Component.literal("  " + displayName + ": ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(LifetimeStatsManager.formatPlayTime(worldTime)).withStyle(ChatFormatting.WHITE)));
                }
            });
        }

        return 1;
    }

    private int showWorlds(CommandContext<FabricClientCommandSource> ctx) {
        LifetimeStatsManager mgr = LifetimeStatsManager.get();
        Map<String, LifetimeStatsManager.WorldStats> worlds = mgr.getWorldStats();

        MutableComponent header = Component.literal("═══ Tracked Worlds (" + worlds.size() + ") ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        ctx.getSource().sendFeedback(header);

        if (worlds.isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal("No worlds tracked yet.").withStyle(ChatFormatting.GRAY));
            return 1;
        }

        worlds.forEach((handle, ws) -> {
            Map<String, Long> stats = safeStats(ws);
            long playTime = stats.getOrDefault("minecraft:custom:minecraft:play_time", 0L);
            String timeStr = LifetimeStatsManager.formatPlayTime(playTime);
            String displayName = mgr.getWorldDisplayName(handle, ws);

            boolean isLocal = handle.startsWith("local:");
            ChatFormatting typeColor = isLocal ? ChatFormatting.GREEN : ChatFormatting.BLUE;
            String typePrefix = isLocal ? "[Local] " : "[Server] ";

            ctx.getSource().sendFeedback(Component.literal(typePrefix).withStyle(typeColor)
                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" - " + timeStr).withStyle(ChatFormatting.GRAY)));
        });

        ctx.getSource().sendFeedback(Component.literal("Use /lst world <name> for details").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        return 1;
    }

    private int showWorldStats(CommandContext<FabricClientCommandSource> ctx) {
        String searchName = StringArgumentType.getString(ctx, "name").toLowerCase();
        LifetimeStatsManager mgr = LifetimeStatsManager.get();
        Map<String, LifetimeStatsManager.WorldStats> worlds = mgr.getWorldStats();

        // Find matching world
        Map.Entry<String, LifetimeStatsManager.WorldStats> match = null;
        for (Map.Entry<String, LifetimeStatsManager.WorldStats> entry : worlds.entrySet()) {
            String displayName = mgr.getWorldDisplayName(entry.getKey(), entry.getValue()).toLowerCase();
            if (displayName.contains(searchName) ||
                entry.getKey().toLowerCase().contains(searchName)) {
                match = entry;
                break;
            }
        }

        if (match == null) {
            ctx.getSource().sendFeedback(Component.literal("No world found matching: " + searchName).withStyle(ChatFormatting.RED));
            return 0;
        }

        String handle = match.getKey();
        LifetimeStatsManager.WorldStats ws = match.getValue();
        Map<String, Long> stats = safeStats(ws);
        String displayName = mgr.getWorldDisplayName(handle, ws);

        MutableComponent header = Component.literal("═══ " + displayName + " ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        ctx.getSource().sendFeedback(header);

        // Show key stats
        long playTime = stats.getOrDefault("minecraft:custom:minecraft:play_time", 0L);
        ctx.getSource().sendFeedback(Component.literal("⏱ Play Time: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(LifetimeStatsManager.formatPlayTime(playTime)).withStyle(ChatFormatting.WHITE)));

        long distance = stats.getOrDefault("minecraft:custom:minecraft:walk_one_cm", 0L);
        ctx.getSource().sendFeedback(Component.literal("🚶 Distance: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(LifetimeStatsManager.formatDistance(distance)).withStyle(ChatFormatting.WHITE)));

        long jumps = stats.getOrDefault("minecraft:custom:minecraft:jump", 0L);
        ctx.getSource().sendFeedback(Component.literal("⬆ Jumps: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.format("%,d", jumps)).withStyle(ChatFormatting.WHITE)));

        long mobKills = stats.getOrDefault("minecraft:custom:minecraft:mob_kills", 0L);
        ctx.getSource().sendFeedback(Component.literal("⚔ Mob Kills: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.format("%,d", mobKills)).withStyle(ChatFormatting.WHITE)));

        long deaths = stats.getOrDefault("minecraft:custom:minecraft:deaths", 0L);
        ctx.getSource().sendFeedback(Component.literal("💀 Deaths: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.format("%,d", deaths)).withStyle(ChatFormatting.WHITE)));

        // Advancements for this world
        Map<String, Set<String>> advs = mgr.getAdvancements();
        Set<String> worldAdvs = advs.get(handle);
        int advCount = worldAdvs != null ? worldAdvs.size() : 0;
        ctx.getSource().sendFeedback(Component.literal("🏆 Advancements: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.valueOf(advCount)).withStyle(ChatFormatting.WHITE)));

        return 1;
    }

    private int showAdvancements(CommandContext<FabricClientCommandSource> ctx) {
        LifetimeStatsManager mgr = LifetimeStatsManager.get();
        Set<String> allAdvs = mgr.getAllAdvancements();

        MutableComponent header = Component.literal("═══ Unique Advancements (" + allAdvs.size() + ") ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        ctx.getSource().sendFeedback(header);

        if (allAdvs.isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal("No advancements tracked yet.").withStyle(ChatFormatting.GRAY));
            ctx.getSource().sendFeedback(Component.literal("Advancements are tracked as you earn them.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return 1;
        }

        // Group by category
        Map<String, Integer> categories = new java.util.TreeMap<>();
        for (String adv : allAdvs) {
            String category = "other";
            if (adv.contains("story/")) category = "story";
            else if (adv.contains("nether/")) category = "nether";
            else if (adv.contains("end/")) category = "end";
            else if (adv.contains("adventure/")) category = "adventure";
            else if (adv.contains("husbandry/")) category = "husbandry";

            categories.merge(category, 1, Integer::sum);
        }

        categories.forEach((cat, count) -> {
            ctx.getSource().sendFeedback(Component.literal("  " + capitalize(cat) + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.WHITE)));
        });

        return 1;
    }

    private int seedWorld(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        LifetimeStatsManager.OperationResult result = LifetimeStatsManager.get().queueManualSeed(name);
        ctx.getSource().sendFeedback(Component.literal(result.message)
            .withStyle(result.success ? ChatFormatting.GREEN : ChatFormatting.RED));
        return result.success ? 1 : 0;
    }

    private int removeWorld(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        LifetimeStatsManager.OperationResult result = LifetimeStatsManager.get().removeWorld(name);
        ctx.getSource().sendFeedback(Component.literal(result.message)
            .withStyle(result.success ? ChatFormatting.GREEN : ChatFormatting.RED));
        return result.success ? 1 : 0;
    }

    private int showCurrentWorld(CommandContext<FabricClientCommandSource> ctx) {
        LifetimeStatsManager mgr = LifetimeStatsManager.get();
        String handle = mgr.getCurrentHandle();

        ctx.getSource().sendFeedback(Component.literal("Current World Handle: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(handle).withStyle(ChatFormatting.WHITE)));

        return 1;
    }

    private int showHelp(CommandContext<FabricClientCommandSource> ctx) {
        LifetimeStatsManager mgr = LifetimeStatsManager.get();
        
        MutableComponent header = Component.literal("═══ Lifetime Stats Help ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        ctx.getSource().sendFeedback(header);

        ctx.getSource().sendFeedback(Component.literal("/lst").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show summary of all lifetime stats").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst time").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show play time breakdown by world").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst worlds").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - List all tracked worlds").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst world <name>").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show stats for a specific world").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst seed world <name>").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Manually seed an unmodded server world").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst remove world <name>").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Remove a tracked world after backing up data").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst advancements").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show advancement summary").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst current").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show current world handle").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst debug").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show temporary debug values").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst clear").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Back up and clear tracker data").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal(""));
        ctx.getSource().sendFeedback(Component.literal("Stats are saved to: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(mgr.getModDir().toString()).withStyle(ChatFormatting.WHITE)));

        return 1;
    }

    private int showDebug(CommandContext<FabricClientCommandSource> ctx) {
        LifetimeStatsManager mgr = LifetimeStatsManager.get();
        String handle = mgr.getCurrentHandle();
        Map<String, Long> totals = mgr.getTotals();
        Map<String, Long> snapshot = mgr.getCurrentSnapshot();
        LifetimeStatsManager.WorldStats currentWorld = mgr.getWorldStats().get(handle);
        Map<String, Long> worldStats = safeStats(currentWorld);

        ctx.getSource().sendFeedback(Component.literal("═══ LST Debug ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ctx.getSource().sendFeedback(Component.literal("Handle: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(shorten(handle, 46)).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendFeedback(Component.literal("World ID: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(shorten(mgr.getServerWorldIdentityDebug(), 46)).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendFeedback(Component.literal("Requests: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(mgr.getLastRequestReason() + " " + ageSeconds(mgr.getLastRequestMs()) + "s ago").withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendFeedback(Component.literal("Packet: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(mgr.getLastStatsPacketKeys() + " keys, +" + mgr.getLastAddedTotal()
                + ", deltas " + mgr.getLastPositiveDeltaKeys()
                + ", skip " + mgr.getLastNonAdditiveUpdates()
                + ", high-water " + mgr.getLastHighWaterSkips()
                + ", " + ageSeconds(mgr.getLastStatsPacketMs()) + "s ago").withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendFeedback(Component.literal("Totals J/W/P: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(formatDebugTriple(totals)).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendFeedback(Component.literal("Snap J/W/P: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(formatDebugTriple(snapshot)).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendFeedback(Component.literal("World J/W/P: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(formatDebugTriple(worldStats)).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendFeedback(Component.literal("Counts: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal("worlds " + mgr.getWorldCount()
                + ", adv " + mgr.getAllAdvancements().size()
                + ", totalKeys " + totals.size()
                + ", snapKeys " + snapshot.size()).withStyle(ChatFormatting.WHITE)));

        return 1;
    }

    private int clearStoredData(CommandContext<FabricClientCommandSource> ctx) {
        LifetimeStatsManager mgr = LifetimeStatsManager.get();
        String backupPath = mgr.clearStoredDataWithBackup();
        if (backupPath == null) {
            ctx.getSource().sendFeedback(Component.literal("LST clear failed. Check latest.log.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ctx.getSource().sendFeedback(Component.literal("LST data cleared.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        ctx.getSource().sendFeedback(Component.literal("Backup: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(backupPath).withStyle(ChatFormatting.WHITE)));
        ctx.getSource().sendFeedback(Component.literal("Run /lst debug after moving/jumping.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String formatDebugTriple(Map<String, Long> stats) {
        return "J " + stats.getOrDefault("minecraft:custom:minecraft:jump", 0L)
            + " / W " + stats.getOrDefault("minecraft:custom:minecraft:walk_one_cm", 0L)
            + " / P " + stats.getOrDefault("minecraft:custom:minecraft:play_time", 0L);
    }

    private static long ageSeconds(long timestampMs) {
        if (timestampMs <= 0L) {
            return -1L;
        }
        return Math.max(0L, (System.currentTimeMillis() - timestampMs) / 1000L);
    }

    private static String shorten(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private static Map<String, Long> safeStats(LifetimeStatsManager.WorldStats worldStats) {
        if (worldStats == null || worldStats.stats == null) {
            return Map.of();
        }
        return worldStats.stats;
    }
}
