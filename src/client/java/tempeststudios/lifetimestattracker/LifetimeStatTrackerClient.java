package tempeststudios.lifetimestattracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.Set;

public class LifetimeStatTrackerClient implements ClientModInitializer {

    private int requestCountdownTicks = -1;

    @Override
    public void onInitializeClient() {
        LifetimeStatsManager.get().init();

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LifetimeStatsManager.get().onJoin();
            System.out.println("[LifetimeStatTracker] JOIN detected. Requesting stats...");

            // Request immediately
            LifetimeStatsManager.get().requestStatsNow("join-immediate");

            // And again ~2s later
            requestCountdownTicks = 40;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LifetimeStatsManager.get().onDisconnect();
            System.out.println("[LifetimeStatTracker] DISCONNECT detected.");
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // delayed request
            if (requestCountdownTicks >= 0) {
                requestCountdownTicks--;
                if (requestCountdownTicks == 0) {
                    LifetimeStatsManager.get().requestStatsNow("join-delayed");
                    requestCountdownTicks = -1;
                }
            }

            // periodic request (throttled inside manager)
            if (client.player != null) {
                LifetimeStatsManager.get().requestStatsNow("periodic");
            }
        });

        System.out.println("[LifetimeStatTracker] Loaded ✅");
        System.out.println("[LifetimeStatTracker] Writing to: " + LifetimeStatsManager.get().getModDir().toAbsolutePath());
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            ClientCommandManager.literal("lifetimestats")
                .executes(this::showSummary)
                .then(ClientCommandManager.literal("time")
                    .executes(this::showPlayTime))
                .then(ClientCommandManager.literal("worlds")
                    .executes(this::showWorlds))
                .then(ClientCommandManager.literal("world")
                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(this::showWorldStats)))
                .then(ClientCommandManager.literal("advancements")
                    .executes(this::showAdvancements))
                .then(ClientCommandManager.literal("current")
                    .executes(this::showCurrentWorld))
                .then(ClientCommandManager.literal("help")
                    .executes(this::showHelp))
        );

        // Short alias
        dispatcher.register(
            ClientCommandManager.literal("lst")
                .executes(this::showSummary)
                .then(ClientCommandManager.literal("time")
                    .executes(this::showPlayTime))
                .then(ClientCommandManager.literal("worlds")
                    .executes(this::showWorlds))
                .then(ClientCommandManager.literal("world")
                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(this::showWorldStats)))
                .then(ClientCommandManager.literal("advancements")
                    .executes(this::showAdvancements))
                .then(ClientCommandManager.literal("current")
                    .executes(this::showCurrentWorld))
                .then(ClientCommandManager.literal("help")
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
                long worldTime = ws.stats.getOrDefault("minecraft:custom:minecraft:play_time", 0L);
                if (worldTime > 0) {
                    ctx.getSource().sendFeedback(Component.literal("  " + ws.displayName + ": ").withStyle(ChatFormatting.GRAY)
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
            long playTime = ws.stats.getOrDefault("minecraft:custom:minecraft:play_time", 0L);
            String timeStr = LifetimeStatsManager.formatPlayTime(playTime);

            boolean isLocal = handle.startsWith("local:");
            ChatFormatting typeColor = isLocal ? ChatFormatting.GREEN : ChatFormatting.BLUE;
            String typePrefix = isLocal ? "[Local] " : "[Server] ";

            ctx.getSource().sendFeedback(Component.literal(typePrefix).withStyle(typeColor)
                .append(Component.literal(ws.displayName).withStyle(ChatFormatting.WHITE))
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
            if (entry.getValue().displayName.toLowerCase().contains(searchName) ||
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

        MutableComponent header = Component.literal("═══ " + ws.displayName + " ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        ctx.getSource().sendFeedback(header);

        // Show key stats
        long playTime = ws.stats.getOrDefault("minecraft:custom:minecraft:play_time", 0L);
        ctx.getSource().sendFeedback(Component.literal("⏱ Play Time: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(LifetimeStatsManager.formatPlayTime(playTime)).withStyle(ChatFormatting.WHITE)));

        long distance = ws.stats.getOrDefault("minecraft:custom:minecraft:walk_one_cm", 0L);
        ctx.getSource().sendFeedback(Component.literal("🚶 Distance: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(LifetimeStatsManager.formatDistance(distance)).withStyle(ChatFormatting.WHITE)));

        long jumps = ws.stats.getOrDefault("minecraft:custom:minecraft:jump", 0L);
        ctx.getSource().sendFeedback(Component.literal("⬆ Jumps: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.format("%,d", jumps)).withStyle(ChatFormatting.WHITE)));

        long mobKills = ws.stats.getOrDefault("minecraft:custom:minecraft:mob_kills", 0L);
        ctx.getSource().sendFeedback(Component.literal("⚔ Mob Kills: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(String.format("%,d", mobKills)).withStyle(ChatFormatting.WHITE)));

        long deaths = ws.stats.getOrDefault("minecraft:custom:minecraft:deaths", 0L);
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

        ctx.getSource().sendFeedback(Component.literal("/lst advancements").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show advancement summary").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal("/lst current").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - Show current world handle").withStyle(ChatFormatting.GRAY)));

        ctx.getSource().sendFeedback(Component.literal(""));
        ctx.getSource().sendFeedback(Component.literal("Stats are saved to: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(mgr.getModDir().toString()).withStyle(ChatFormatting.WHITE)));

        return 1;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
