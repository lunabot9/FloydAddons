package floydaddons.not.dogshit.client;
import floydaddons.not.dogshit.client.*;
import floydaddons.not.dogshit.client.config.*;
import floydaddons.not.dogshit.client.gui.*;
import floydaddons.not.dogshit.client.features.hud.*;
import floydaddons.not.dogshit.client.features.visual.*;
import floydaddons.not.dogshit.client.features.cosmetic.*;
import floydaddons.not.dogshit.client.features.misc.*;
import floydaddons.not.dogshit.client.esp.*;
import floydaddons.not.dogshit.client.skin.*;
import floydaddons.not.dogshit.client.util.*;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Registers /floydaddons and /fa client commands.
 */
public final class FloydAddonsCommand {
    private FloydAddonsCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(buildCommand("floydaddons"));
            dispatcher.register(buildCommand("fa"));
            dispatcher.register(buildStalkCommand());
        });
    }

    // ── Suggestion providers ──

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            Set<String> names = new LinkedHashSet<>();
            for (var entry : mc.getNetworkHandler().getPlayerList()) {
                String name = entry.getProfile().name();
                if (name != null && name.matches("[a-zA-Z0-9_]{3,16}")) {
                    names.add(name);
                }
            }
            return CommandSource.suggestMatching(names, builder);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestNearbyEntityNames(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.player != null) {
            Set<String> names = new LinkedHashSet<>();
            for (Entity entity : mc.world.getEntities()) {
                if (entity == mc.player) continue;
                if (entity.squaredDistanceTo(mc.player) > 2500) continue;
                String name = stripColorCodes(entity.getName().getString());
                if (!name.isEmpty()) names.add(name);
                if (entity.hasCustomName() && entity.getCustomName() != null) {
                    String custom = stripColorCodes(entity.getCustomName().getString());
                    if (!custom.isEmpty()) names.add(custom);
                }
                String cached = NpcTracker.getCachedName(entity);
                if (cached != null) names.add(cached);
            }
            return CommandSource.suggestMatching(names, builder);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestNearbyEntityTypes(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.player != null) {
            Set<String> types = new LinkedHashSet<>();
            for (Entity entity : mc.world.getEntities()) {
                if (entity == mc.player) continue;
                if (entity.squaredDistanceTo(mc.player) > 2500) continue;
                types.add(EntityType.getId(entity.getType()).toString());
            }
            return CommandSource.suggestMatching(types, builder);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestNameMappingKeys(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(NickHiderConfig.getNameMappings().keySet(), builder);
    }

    private static CompletableFuture<Suggestions> suggestMobEspNames(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(MobEspManager.getNameFilters(), builder);
    }

    private static CompletableFuture<Suggestions> suggestMobEspTypes(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        Set<String> types = new LinkedHashSet<>();
        for (Identifier id : MobEspManager.getTypeFilters()) {
            types.add(id.toString());
        }
        return CommandSource.suggestMatching(types, builder);
    }

    private static CompletableFuture<Suggestions> suggestNearbyBlocks(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.player != null) {
            Set<String> blocks = new LinkedHashSet<>();
            BlockPos center = mc.player.getBlockPos();
            for (int x = -8; x <= 8; x++) {
                for (int y = -8; y <= 8; y++) {
                    for (int z = -8; z <= 8; z++) {
                        BlockState state = mc.world.getBlockState(center.add(x, y, z));
                        if (!state.isAir()) {
                            blocks.add(Registries.BLOCK.getId(state.getBlock()).toString());
                        }
                    }
                }
            }
            return CommandSource.suggestMatching(blocks, builder);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestXrayOpaqueBlocks(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(RenderConfig.getXrayOpaqueBlocks(), builder);
    }

    // ── Command tree ──

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name) {
        return ClientCommandManager.literal(name)
                .executes(FloydAddonsCommand::openGui)
                .then(buildNameSubcommand())
                .then(buildStalkSubcommand("stalk"))
                .then(buildStalkSubcommand("s"))
                .then(buildMobEspSubcommand("mob-esp"))
                .then(buildMobEspSubcommand("me"))
                .then(ClientCommandManager.literal("mob-esp-debug")
                        .executes(FloydAddonsCommand::mobEspDebug))
                .then(ClientCommandManager.literal("debug")
                        .executes(FloydAddonsCommand::debugInfo))
                .then(buildXraySubcommand());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildNameSubcommand() {
        return ClientCommandManager.literal("name")
                .then(ClientCommandManager.literal("self")
                        .then(ClientCommandManager.argument("fakename", StringArgumentType.greedyString())
                                .executes(FloydAddonsCommand::nameSelf)))
                .then(ClientCommandManager.literal("other")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .suggests(FloydAddonsCommand::suggestOnlinePlayers)
                                .then(ClientCommandManager.argument("fakename", StringArgumentType.greedyString())
                                        .executes(FloydAddonsCommand::nameOther))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .suggests(FloydAddonsCommand::suggestNameMappingKeys)
                                .executes(FloydAddonsCommand::nameRemove)))
                .then(ClientCommandManager.literal("list")
                        .executes(FloydAddonsCommand::nameList))
                .then(ClientCommandManager.literal("clear")
                        .executes(FloydAddonsCommand::nameClear));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildStalkSubcommand(String literal) {
        return ClientCommandManager.literal(literal)
                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                        .suggests(FloydAddonsCommand::suggestOnlinePlayers)
                        .executes(FloydAddonsCommand::stalkPlayer))
                .executes(FloydAddonsCommand::stalkToggle);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildMobEspSubcommand(String literal) {
        return ClientCommandManager.literal(literal)
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.literal("name")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(FloydAddonsCommand::suggestNearbyEntityNames)
                                        .executes(FloydAddonsCommand::mobEspAddName)))
                        .then(ClientCommandManager.literal("type")
                                .then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
                                        .suggests(FloydAddonsCommand::suggestNearbyEntityTypes)
                                        .executes(FloydAddonsCommand::mobEspAddType))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.literal("name")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(FloydAddonsCommand::suggestMobEspNames)
                                        .executes(FloydAddonsCommand::mobEspRemoveName)))
                        .then(ClientCommandManager.literal("type")
                                .then(ClientCommandManager.argument("type", IdentifierArgumentType.identifier())
                                        .suggests(FloydAddonsCommand::suggestMobEspTypes)
                                        .executes(FloydAddonsCommand::mobEspRemoveType))))
                .then(ClientCommandManager.literal("list")
                        .executes(FloydAddonsCommand::mobEspList))
                .then(ClientCommandManager.literal("clear")
                        .executes(FloydAddonsCommand::mobEspClear))
                .then(ClientCommandManager.literal("reload")
                        .executes(FloydAddonsCommand::mobEspReload))
                .executes(FloydAddonsCommand::mobEspToggle);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildXraySubcommand() {
        return ClientCommandManager.literal("xray")
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("block", IdentifierArgumentType.identifier())
                                .suggests(FloydAddonsCommand::suggestNearbyBlocks)
                                .executes(FloydAddonsCommand::xrayAdd)))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("block", IdentifierArgumentType.identifier())
                                .suggests(FloydAddonsCommand::suggestXrayOpaqueBlocks)
                                .executes(FloydAddonsCommand::xrayRemove)))
                .then(ClientCommandManager.literal("list")
                        .executes(FloydAddonsCommand::xrayList))
                .then(ClientCommandManager.literal("clear")
                        .executes(FloydAddonsCommand::xrayClear))
                .executes(FloydAddonsCommand::xrayToggle);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildStalkCommand() {
        return ClientCommandManager.literal("stalk")
                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                        .suggests(FloydAddonsCommand::suggestOnlinePlayers)
                        .executes(FloydAddonsCommand::stalkPlayer))
                .executes(FloydAddonsCommand::stalkToggle);
    }

    // ── Command handlers ──

    private static int openGui(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new FloydAddonsScreen(Text.literal("FloydAddons"))));
        return Command.SINGLE_SUCCESS;
    }

    // -- Name commands --

    private static int nameSelf(CommandContext<FabricClientCommandSource> context) {
        String fakeName = StringArgumentType.getString(context, "fakename");
        NickHiderConfig.setNickname(fakeName);
        FloydAddonsConfig.save();
        context.getSource().sendFeedback(Text.literal("\u00a7aDefault nick set to \u00a7f" + fakeName));
        return Command.SINGLE_SUCCESS;
    }

    private static int nameOther(CommandContext<FabricClientCommandSource> context) {
        String ign = StringArgumentType.getString(context, "ign");
        String fakeName = StringArgumentType.getString(context, "fakename");
        NickHiderConfig.addNameMapping(ign, fakeName);
        FloydAddonsConfig.saveNameMappings();
        context.getSource().sendFeedback(Text.literal(
                "\u00a7aAdded name mapping: \u00a7f" + ign + " \u00a7a\u2192 \u00a7f" + fakeName));
        return Command.SINGLE_SUCCESS;
    }

    private static int nameRemove(CommandContext<FabricClientCommandSource> context) {
        String ign = StringArgumentType.getString(context, "ign");
        if (NickHiderConfig.removeNameMapping(ign)) {
            FloydAddonsConfig.saveNameMappings();
            context.getSource().sendFeedback(Text.literal("\u00a7aRemoved name mapping for \u00a7f" + ign));
        } else {
            context.getSource().sendFeedback(Text.literal("\u00a7cNo mapping found for \u00a7f" + ign));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int nameList(CommandContext<FabricClientCommandSource> context) {
        Map<String, String> mappings = NickHiderConfig.getNameMappings();
        context.getSource().sendFeedback(Text.literal("\u00a7e--- Name Mappings ---"));
        context.getSource().sendFeedback(Text.literal(
                "\u00a77Default nick: \u00a7f" + NickHiderConfig.getNickname()));
        if (mappings.isEmpty()) {
            context.getSource().sendFeedback(Text.literal("\u00a77No player mappings configured."));
        } else {
            for (var entry : mappings.entrySet()) {
                context.getSource().sendFeedback(Text.literal(
                        "\u00a7f" + entry.getKey() + " \u00a77\u2192 \u00a7f" + entry.getValue()));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int nameClear(CommandContext<FabricClientCommandSource> context) {
        NickHiderConfig.clearNameMappings();
        FloydAddonsConfig.saveNameMappings();
        context.getSource().sendFeedback(Text.literal("\u00a7aCleared all name mappings."));
        return Command.SINGLE_SUCCESS;
    }

    // -- Stalk commands --

    private static int stalkPlayer(CommandContext<FabricClientCommandSource> context) {
        String ign = StringArgumentType.getString(context, "ign");
        StalkManager.setTarget(ign);
        context.getSource().sendFeedback(Text.literal("\u00a7aStalking \u00a7f" + ign));
        return Command.SINGLE_SUCCESS;
    }

    private static int stalkToggle(CommandContext<FabricClientCommandSource> context) {
        if (StalkManager.isEnabled()) {
            String old = StalkManager.getTargetName();
            StalkManager.disable();
            context.getSource().sendFeedback(Text.literal("\u00a7cStopped stalking \u00a7f" + old));
        } else {
            context.getSource().sendFeedback(Text.literal("\u00a7cUsage: /fa stalk <ign>"));
        }
        return Command.SINGLE_SUCCESS;
    }

    // -- Mob ESP commands --

    private static int mobEspToggle(CommandContext<FabricClientCommandSource> context) {
        MobEspManager.toggle();
        boolean on = MobEspManager.isEnabled();
        int names = MobEspManager.getNameFilters().size();
        int types = MobEspManager.getTypeFilters().size();
        context.getSource().sendFeedback(Text.literal(
                on ? "\u00a7aMob ESP \u00a7fenabled \u00a77(" + names + " names, " + types + " types)"
                   : "\u00a7cMob ESP \u00a7fdisabled"));
        return Command.SINGLE_SUCCESS;
    }

    private static int mobEspAddName(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        MobEspManager.addNameFilter(name);
        FloydAddonsConfig.saveMobEsp();
        context.getSource().sendFeedback(Text.literal("\u00a7aAdded mob ESP name filter: \u00a7f" + name));
        return Command.SINGLE_SUCCESS;
    }

    private static int mobEspAddType(CommandContext<FabricClientCommandSource> context) {
        Identifier type = context.getArgument("type", Identifier.class);
        MobEspManager.addTypeFilter(type.toString());
        FloydAddonsConfig.saveMobEsp();
        context.getSource().sendFeedback(Text.literal("\u00a7aAdded mob ESP type filter: \u00a7f" + type));
        return Command.SINGLE_SUCCESS;
    }

    private static int mobEspRemoveName(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (MobEspManager.removeNameFilter(name)) {
            FloydAddonsConfig.saveMobEsp();
            context.getSource().sendFeedback(Text.literal("\u00a7aRemoved mob ESP name filter: \u00a7f" + name));
        } else {
            context.getSource().sendFeedback(Text.literal("\u00a7cName filter not found: \u00a7f" + name));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int mobEspRemoveType(CommandContext<FabricClientCommandSource> context) {
        Identifier type = context.getArgument("type", Identifier.class);
        if (MobEspManager.removeTypeFilter(type.toString())) {
            FloydAddonsConfig.saveMobEsp();
            context.getSource().sendFeedback(Text.literal("\u00a7aRemoved mob ESP type filter: \u00a7f" + type));
        } else {
            context.getSource().sendFeedback(Text.literal("\u00a7cType filter not found: \u00a7f" + type));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int mobEspList(CommandContext<FabricClientCommandSource> context) {
        Set<String> names = MobEspManager.getNameFilters();
        Set<Identifier> types = MobEspManager.getTypeFilters();
        context.getSource().sendFeedback(Text.literal("\u00a7e--- Mob ESP Filters ---"));
        if (names.isEmpty() && types.isEmpty()) {
            context.getSource().sendFeedback(Text.literal("\u00a77No filters configured."));
        } else {
            for (String name : names) {
                context.getSource().sendFeedback(Text.literal("\u00a77name: \u00a7f" + name));
            }
            for (Identifier type : types) {
                context.getSource().sendFeedback(Text.literal("\u00a77type: \u00a7f" + type));
            }
        }
        context.getSource().sendFeedback(Text.literal(
                "\u00a77Star mobs: \u00a7f" + (RenderConfig.isMobEspStarMobs() ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int mobEspClear(CommandContext<FabricClientCommandSource> context) {
        MobEspManager.clearFilters();
        FloydAddonsConfig.saveMobEsp();
        context.getSource().sendFeedback(Text.literal("\u00a7aCleared all mob ESP filters."));
        return Command.SINGLE_SUCCESS;
    }

    private static int mobEspReload(CommandContext<FabricClientCommandSource> context) {
        FloydAddonsConfig.loadMobEsp();
        context.getSource().sendFeedback(Text.literal("\u00a7aMob ESP config reloaded"));
        return Command.SINGLE_SUCCESS;
    }

    private static int mobEspDebug(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return Command.SINGLE_SUCCESS;

        MobEspManager.setDebugActive(true);
        context.getSource().sendFeedback(Text.literal("\u00a7e--- Mob ESP Debug --- \u00a77(in-world labels for 10s)"));
        context.getSource().sendFeedback(Text.literal("\u00a77Enabled: \u00a7f" + MobEspManager.isEnabled()
                + " \u00a77HasFilters: \u00a7f" + MobEspManager.hasFilters()));
        context.getSource().sendFeedback(Text.literal("\u00a77Names: \u00a7f" + MobEspManager.getNameFilters()
                + " \u00a77Types: \u00a7f" + MobEspManager.getTypeFilters()));

        int count = 0;
        int matched = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            count++;
            String typeId = EntityType.getId(entity.getType()).toString();
            String name = entity.getName().getString();
            String custom = entity.hasCustomName() && entity.getCustomName() != null
                    ? entity.getCustomName().getString() : "(none)";
            String cached = NpcTracker.getCachedName(entity);
            boolean match = MobEspManager.matches(entity);
            if (match) matched++;

            // Only print entities within 50 blocks
            double dist = entity.squaredDistanceTo(mc.player);
            if (dist < 2500) { // 50 blocks squared
                String color = match ? "\u00a7a" : "\u00a77";
                String cachedStr = cached != null ? " npc=\"" + cached + "\"" : "";
                String posStr = String.format(" \u00a78[%.1f, %.1f, %.1f]",
                        entity.getX(), entity.getY(), entity.getZ());
                context.getSource().sendFeedback(Text.literal(
                        color + typeId + " \u00a7fname=\"" + name + "\" custom=\"" + custom
                                + "\"" + cachedStr + posStr
                                + " \u00a77dist=" + String.format("%.0f", Math.sqrt(dist))
                                + (match ? " \u00a7aMATCH" : "")));
            }
        }
        context.getSource().sendFeedback(Text.literal(
                "\u00a77Total: \u00a7f" + count
                        + " \u00a77Matched: \u00a7a" + matched));
        return Command.SINGLE_SUCCESS;
    }

    // -- Debug command --

    private static int debugInfo(CommandContext<FabricClientCommandSource> context) {
        var src = context.getSource();

        src.sendFeedback(Text.literal("\u00a7e--- Server ID Hider Debug ---"));
        src.sendFeedback(Text.literal("\u00a77Enabled: \u00a7f" + RenderConfig.isServerIdHiderEnabled()));
        src.sendFeedback(Text.literal("\u00a77Replacement: \u00a7ffL0YD"));

        // Current server ID
        String currentDisplay = ServerIdTracker.getCurrentFullIdForDisplay();
        if (currentDisplay.isEmpty()) {
            src.sendFeedback(Text.literal("\u00a77Current server: \u00a7c(none detected)"));
        } else {
            src.sendFeedback(Text.literal("\u00a77Current server: \u00a7a" + currentDisplay));
        }

        // Accumulated IDs
        String[] displayIds = ServerIdTracker.getCachedIdsForDisplay();
        int totalAccumulated = ServerIdTracker.getAccumulatedCount();
        if (displayIds.length == 0) {
            src.sendFeedback(Text.literal("\u00a77Accumulated IDs: \u00a7c(none - nothing will be hidden)"));
        } else {
            StringBuilder sb = new StringBuilder("\u00a77Accumulated IDs (" + totalAccumulated + "): ");
            for (int i = 0; i < displayIds.length; i++) {
                if (i > 0) sb.append("\u00a77, ");
                sb.append("\u00a7a\"").append(displayIds[i]).append("\"");
            }
            src.sendFeedback(Text.literal(sb.toString()));
        }

        // Known tab UUID status
        java.util.UUID tabUUID = ServerIdTracker.getKnownTabUUID();
        src.sendFeedback(Text.literal("\u00a77Known tab UUID: \u00a7f"
                + (tabUUID != null ? tabUUID.toString().substring(0, 8) + "..." : "(none)")));

        // Rapid scan status
        src.sendFeedback(Text.literal("\u00a77Rapid scan: \u00a7f"
                + (ServerIdTracker.isRapidScanning() ? "\u00a7aACTIVE" : "inactive")));

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            src.sendFeedback(Text.literal("\u00a7e--- Tab List (showing matches only) ---"));
            java.util.regex.Pattern serverPattern = java.util.regex.Pattern.compile(
                    "(?i)Server:\\s*(?:mini|mega|lobby|limbo|housing|prototype|node|legacylobby)\\d{1,4}[a-z]{0,4}");
            java.util.regex.Pattern generalPattern = ServerIdTracker.getFullPattern();
            int count = 0;
            for (var entry : mc.getNetworkHandler().getPlayerList()) {
                net.minecraft.text.Text displayName = entry.getDisplayName();
                if (displayName == null) continue;
                String text = displayName.getString();
                if (text.isEmpty()) continue;
                count++;

                java.util.regex.Matcher sm = serverPattern.matcher(text);
                java.util.regex.Matcher gm = generalPattern.matcher(text);
                if (sm.find()) {
                    src.sendFeedback(Text.literal("\u00a7a[" + count + "] \u00a7f" + text
                            + " \u00a7a<< SERVER LINE"));
                } else if (gm.find()) {
                    src.sendFeedback(Text.literal("\u00a7e[" + count + "] \u00a7f" + text
                            + " \u00a7e<< general match: " + gm.group()));
                }
            }
            src.sendFeedback(Text.literal("\u00a77Total tab entries: \u00a7f" + count));
        }

        return Command.SINGLE_SUCCESS;
    }

    // -- Xray commands --

    private static int xrayToggle(CommandContext<FabricClientCommandSource> context) {
        RenderConfig.toggleXray();
        boolean on = RenderConfig.isXrayEnabled();
        context.getSource().sendFeedback(Text.literal(
                on ? "\u00a7aX-Ray \u00a7fenabled" : "\u00a7cX-Ray \u00a7fdisabled"));
        return Command.SINGLE_SUCCESS;
    }

    private static int xrayAdd(CommandContext<FabricClientCommandSource> context) {
        Identifier block = context.getArgument("block", Identifier.class);
        String blockId = block.toString();
        if (RenderConfig.addXrayOpaqueBlock(blockId)) {
            FloydAddonsConfig.saveXrayOpaque();
            context.getSource().sendFeedback(Text.literal("\u00a7aAdded xray opaque block: \u00a7f" + blockId));
        } else {
            context.getSource().sendFeedback(Text.literal("\u00a7eBlock already in opaque list: \u00a7f" + blockId));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int xrayRemove(CommandContext<FabricClientCommandSource> context) {
        Identifier block = context.getArgument("block", Identifier.class);
        String blockId = block.toString();
        if (RenderConfig.removeXrayOpaqueBlock(blockId)) {
            FloydAddonsConfig.saveXrayOpaque();
            context.getSource().sendFeedback(Text.literal("\u00a7aRemoved xray opaque block: \u00a7f" + blockId));
        } else {
            context.getSource().sendFeedback(Text.literal("\u00a7cBlock not in opaque list: \u00a7f" + blockId));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int xrayList(CommandContext<FabricClientCommandSource> context) {
        Set<String> blocks = RenderConfig.getXrayOpaqueBlocks();
        context.getSource().sendFeedback(Text.literal("\u00a7e--- Xray Opaque Blocks (" + blocks.size() + ") ---"));
        for (String block : blocks) {
            context.getSource().sendFeedback(Text.literal("\u00a7f" + block));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int xrayClear(CommandContext<FabricClientCommandSource> context) {
        RenderConfig.setXrayOpaqueBlocks(java.util.Collections.emptySet());
        RenderConfig.rebuildChunks();
        FloydAddonsConfig.saveXrayOpaque();
        context.getSource().sendFeedback(Text.literal("\u00a7aCleared all xray opaque blocks."));
        return Command.SINGLE_SUCCESS;
    }

    // ── Utility ──

    private static String stripColorCodes(String s) {
        return s.replaceAll("\u00a7.", "");
    }
}
