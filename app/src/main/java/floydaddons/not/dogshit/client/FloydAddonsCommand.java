package floydaddons.not.dogshit.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;

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

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name) {
        return ClientCommandManager.literal(name)
                .executes(FloydAddonsCommand::openGui)
                .then(ClientCommandManager.literal("stalk")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .executes(FloydAddonsCommand::stalkPlayer))
                        .executes(FloydAddonsCommand::stalkToggle))
                .then(ClientCommandManager.literal("s")
                        .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                                .executes(FloydAddonsCommand::stalkPlayer))
                        .executes(FloydAddonsCommand::stalkToggle))
                .then(ClientCommandManager.literal("mob-esp")
                        .then(ClientCommandManager.literal("reload")
                                .executes(FloydAddonsCommand::mobEspReload))
                        .executes(FloydAddonsCommand::mobEspToggle))
                .then(ClientCommandManager.literal("me")
                        .then(ClientCommandManager.literal("reload")
                                .executes(FloydAddonsCommand::mobEspReload))
                        .executes(FloydAddonsCommand::mobEspToggle))
                .then(ClientCommandManager.literal("mob-esp-debug")
                        .executes(FloydAddonsCommand::mobEspDebug));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildStalkCommand() {
        return ClientCommandManager.literal("stalk")
                .then(ClientCommandManager.argument("ign", StringArgumentType.word())
                        .executes(FloydAddonsCommand::stalkPlayer))
                .executes(FloydAddonsCommand::stalkToggle);
    }

    private static int openGui(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new FloydAddonsScreen(Text.literal("FloydAddons"))));
        return Command.SINGLE_SUCCESS;
    }

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
}
