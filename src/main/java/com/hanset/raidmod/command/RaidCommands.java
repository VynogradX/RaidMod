package com.hanset.raidmod.command;

import com.hanset.raidmod.RaidVariableExits;
import com.hanset.raidmod.config.RaidConfig;
import com.hanset.raidmod.data.RaidDoorData;
import com.hanset.raidmod.util.PermissionHelper;
import com.hanset.raidmod.data.RaidPlayerData;
import com.hanset.raidmod.data.RaidSpawnData;
import com.hanset.raidmod.data.RaidStatsData;
import com.hanset.raidmod.data.RaidVariableExitsData;
import com.hanset.raidmod.RaidConfirmation;
import com.hanset.raidmod.config.MessagesConfig;
import com.hanset.raidmod.RaidSpawnCooldown;
import com.hanset.raidmod.RaidStatsTracker;
import com.hanset.raidmod.RaidTimer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class RaidCommands {

    private static final Random RANDOM = new Random();

    /** Сообщение с кликабельными кнопками подтверждения экспедиции. */
    private static Component createRaidConfirmMessage(String prefix) {
        return Component.literal(prefix)
                .append(Component.literal(MessagesConfig.get().get("confirm.btn_confirm")).withStyle(s -> s
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/expedition confirm"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MessagesConfig.msg("confirm.hover_confirm")))))
                .append(Component.literal(MessagesConfig.get().get("confirm.or")))
                .append(Component.literal(MessagesConfig.get().get("confirm.btn_cancel")).withStyle(s -> s
                        .withColor(ChatFormatting.RED)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/expedition cancel"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MessagesConfig.msg("confirm.hover_cancel")))))
                .append(Component.literal(MessagesConfig.get().get("confirm.suffix")));
    }

    private static final SuggestionProvider<CommandSourceStack> SPAWN_NAME_SUGGESTIONS = (context, builder) -> {
        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether != null) {
            RaidSpawnData data = getRaidSpawnData(nether);
            for (String name : data.getSpawnNames()) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("expedition")
                        .then(Commands.literal("doorcreate")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                        .then(Commands.argument("exitTime", IntegerArgumentType.integer(1, 120))
                                                .then(Commands.argument("shared", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            b.suggest("false");
                                                            b.suggest("true");
                                                            return b.buildFuture();
                                                        })
                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                .executes(RaidCommands::doorCreate))))))
                        .then(Commands.literal("spawncreate")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                .executes(RaidCommands::spawnCreate))))))
                        .then(Commands.literal("spawnremove")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(SPAWN_NAME_SUGGESTIONS)
                                        .executes(RaidCommands::spawnRemove)))
                        .then(Commands.literal("spawnclear")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::spawnClear))
                        .then(Commands.literal("spawnlist")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::spawnList))
                        .then(Commands.literal("doorstatic")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::doorStatic))
                        .then(Commands.literal("doorremove")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::doorRemove))
                        .then(Commands.literal("doorclear")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::doorClear))
                        .then(Commands.literal("reload")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::reload))
                        .then(Commands.literal("timeraid")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                        .executes(RaidCommands::timeraid)))
                        .then(Commands.literal("servercooldown")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                                        .executes(RaidCommands::serverCooldown)))
                        .then(Commands.literal("spawncooldown")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                        .executes(RaidCommands::spawnCooldown)))
                        .then(Commands.literal("spawncooldownclear")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::spawnCooldownClear))
                        .then(Commands.literal("variableexitsinterval")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(60, 86400))
                                        .executes(RaidCommands::variableExitsInterval)))
                        .then(Commands.literal("availableexits")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 14))
                                        .executes(RaidCommands::availableExits)))
                        .then(Commands.literal("variableexitsrefresh")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::variableExitsRefresh))
                        .then(Commands.literal("open")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::raidOpen))
                        .then(Commands.literal("close")
                                .requires(PermissionHelper::hasAdminPermission)
                                .executes(RaidCommands::raidClose))
                        .then(Commands.literal("hidenametags")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            b.suggest("true");
                                            b.suggest("false");
                                            return b.buildFuture();
                                        })
                                        .executes(RaidCommands::hideNametags)))
                        .then(Commands.literal("addtime")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                                .executes(RaidCommands::raidAddTime))))
                        .then(Commands.literal("addtimeclear")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(RaidCommands::raidAddTimeClear)))
                        .then(Commands.literal("nocd")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(RaidCommands::raidNoCooldown)))
                        .then(Commands.literal("nocdremove")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(RaidCommands::raidNoCooldownRemove)))
                        .then(Commands.literal("statclear")
                                .requires(PermissionHelper::hasAdminPermission)
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            b.suggest("all");
                                            for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                                                b.suggest(p.getName().getString());
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(RaidCommands::raidStatClear)))
                        .then(Commands.literal("with")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(RaidCommands::raidWith)))
                        .then(Commands.literal("confirm")
                                .executes(RaidCommands::raidConfirm))
                        .then(Commands.literal("cancel")
                                .executes(RaidCommands::raidCancel))
                        .then(Commands.literal("stat")
                                .executes(RaidCommands::raidStatSelf)
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(RaidCommands::raidStatOther)))
                        .then(Commands.literal("top")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            b.suggest("expedition");
                                            b.suggest("kill");
                                            return b.buildFuture();
                                        })
                                        .executes(RaidCommands::raidTop)))
                        .executes(RaidCommands::raid)
        );
    }

    private static int raid(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.player_only"));
            return 0;
        }

        if (!RaidConfig.get().raids_open && !PermissionHelper.hasAdminPermission(context.getSource())) {
            context.getSource().sendFailure(MessagesConfig.msg("error.raids_closed"));
            return 0;
        }

        if (player.level().dimension() == Level.NETHER) {
            context.getSource().sendFailure(MessagesConfig.msg("error.no_raid_in_nether"));
            return 0;
        }

        int cooldown = RaidConfig.get().raid_cooldown_seconds;
        boolean bypassCooldown = context.getSource().hasPermission(3)
                || RaidConfig.get().canBypassCooldown(player.getUUID())
                || PermissionHelper.hasPermission(player, PermissionHelper.RAID_COOLDOWN_BYPASS);
        if (cooldown > 0 && !bypassCooldown) {
            Long last = getLastRaidTime(context.getSource().getServer(), player.getUUID());
            long now = System.currentTimeMillis();
            if (last != null) {
                long elapsed = (now - last) / 1000;
                if (elapsed < cooldown) {
                    int remaining = (int) (cooldown - elapsed);
                    context.getSource().sendFailure(MessagesConfig.msg("error.cooldown", String.valueOf(remaining)));
                    return 0;
                }
            }
        }

        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_unavailable"));
            return 0;
        }

        RaidSpawnData spawnData = getRaidSpawnData(nether);
        if (spawnData.isEmpty()) {
            context.getSource().sendFailure(MessagesConfig.msg("error.no_spawns"));
            return 0;
        }

        List<RaidSpawnData.SpawnEntry> available = RaidSpawnCooldown.getAvailableSpawns(spawnData).stream()
                .filter(s -> !RaidConfirmation.isReserved(s.name()))
                .toList();
        if (available.isEmpty()) {
            context.getSource().sendFailure(MessagesConfig.msg("error.no_available_spawns"));
            context.getSource().sendFailure(MessagesConfig.msg("error.no_spawn_consumed"));
            return 0;
        }

        RaidSpawnData.SpawnEntry spawn = available.get(RANDOM.nextInt(available.size()));
        if (!RaidConfirmation.createSingle(player.getUUID(), spawn)) {
            context.getSource().sendFailure(MessagesConfig.msg("error.pending_exists"));
            return 0;
        }

        player.sendSystemMessage(createRaidConfirmMessage(MessagesConfig.get().get("confirm.single_prefix")));
        return 1;
    }

    private static int doorCreate(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.player_only"));
            return 0;
        }

        if (player.level().dimension() != Level.NETHER) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_only"));
            return 0;
        }

        BlockPos lookingAt = getTargetedBlock(player, 5);
        if (lookingAt == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.aim_at_door"));
            return 0;
        }

        BlockState state = player.level().getBlockState(lookingAt);
        if (!(state.getBlock() instanceof DoorBlock)) {
            context.getSource().sendFailure(MessagesConfig.msg("error.aim_at_door_block"));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(context, "radius");
        int exitTime = IntegerArgumentType.getInteger(context, "exitTime");
        boolean shared = "true".equalsIgnoreCase(StringArgumentType.getString(context, "shared"));
        String name = StringArgumentType.getString(context, "name");
        int requiredPlayers = shared ? 2 : 1;

        ServerLevel nether = (ServerLevel) player.level();
        RaidDoorData data = getRaidDoorData(nether);

        BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? lookingAt : lookingAt.below();

        if (data.addDoor(lowerPos, name, radius, exitTime, requiredPlayers)) {
            context.getSource().sendSuccess(
                    () -> MessagesConfig.msg("door.created", name, String.valueOf(radius), String.valueOf(exitTime), shared ? MessagesConfig.get().get("door.shared_suffix") : ""),
                    true);
            return 1;
        } else {
            context.getSource().sendFailure(MessagesConfig.msg("door.already_marked"));
            return 0;
        }
    }

    private static int spawnCreate(CommandContext<CommandSourceStack> context) {
        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_unavailable"));
            return 0;
        }

        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = new BlockPos(x, y, z);
        RaidSpawnData data = getRaidSpawnData(nether);

        if (data.addSpawn(name, pos)) {
            context.getSource().sendSuccess(
                    () -> MessagesConfig.msg("spawn.added", name, String.valueOf(x), String.valueOf(y), String.valueOf(z)),
                    true);
            return 1;
        } else {
            context.getSource().sendFailure(MessagesConfig.msg("spawn.exists"));
            return 0;
        }
    }

    private static int spawnRemove(CommandContext<CommandSourceStack> context) {
        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_unavailable"));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        RaidSpawnData data = getRaidSpawnData(nether);

        if (data.removeSpawn(name)) {
            context.getSource().sendSuccess(() -> MessagesConfig.msg("spawn.removed", name), true);
            return 1;
        } else {
            context.getSource().sendFailure(MessagesConfig.msg("spawn.not_found"));
            return 0;
        }
    }

    private static int doorClear(CommandContext<CommandSourceStack> context) {
        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_unavailable"));
            return 0;
        }
        RaidDoorData data = getRaidDoorData(nether);
        int count = data.clear();
        context.getSource().sendSuccess(() -> MessagesConfig.msg("door.cleared", String.valueOf(count)), true);
        return 1;
    }

    private static int doorStatic(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.player_only"));
            return 0;
        }
        if (player.level().dimension() != Level.NETHER) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_only"));
            return 0;
        }
        BlockPos lookingAt = getTargetedBlock(player, 5);
        if (lookingAt == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.aim_at_door"));
            return 0;
        }
        ServerLevel nether = (ServerLevel) player.level();
        RaidDoorData data = getRaidDoorData(nether);
        var entry = data.getDoorAt(lookingAt);
        if (entry.isEmpty()) {
            context.getSource().sendFailure(MessagesConfig.msg("door.no_marked"));
            return 0;
        }
        boolean newStatic = !entry.get().isStatic();
        data.setDoorStatic(lookingAt, newStatic);
        getRaidVariableExitsData(nether).setVariableExits(data.getNonStaticDoors());
        context.getSource().sendSuccess(() -> MessagesConfig.msg(newStatic ? "door.static_on" : "door.static_off"), true);
        return 1;
    }

    private static int doorRemove(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.player_only"));
            return 0;
        }

        if (player.level().dimension() != Level.NETHER) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_only"));
            return 0;
        }

        BlockPos lookingAt = getTargetedBlock(player, 5);
        if (lookingAt == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.aim_at_door"));
            return 0;
        }

        ServerLevel nether = (ServerLevel) player.level();
        RaidDoorData data = getRaidDoorData(nether);

        if (data.removeDoor(lookingAt)) {
            context.getSource().sendSuccess(() -> MessagesConfig.msg("door.removed"), true);
            return 1;
        } else {
            context.getSource().sendFailure(MessagesConfig.msg("door.no_marked"));
            return 0;
        }
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        RaidConfig.reload();
        MessagesConfig.reload();
        com.hanset.raidmod.config.RaidAddTimeConfig.reload();
        RaidConfig cfg = RaidConfig.get();
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("reload.done", String.valueOf(cfg.raid_time_seconds),
                        String.valueOf(cfg.raid_cooldown_seconds),
                        String.valueOf(cfg.spawn_cooldown_seconds),
                        String.valueOf(cfg.available_variable_exits)),
                true);
        return 1;
    }

    private static int timeraid(CommandContext<CommandSourceStack> context) {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        RaidConfig.get().setRaidTimeSeconds(seconds);
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("timeraid.set", String.valueOf(seconds)),
                true);
        return 1;
    }

    private static int serverCooldown(CommandContext<CommandSourceStack> context) {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        RaidConfig.get().setRaidCooldownSeconds(seconds);
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("servercooldown.set", String.valueOf(seconds)),
                true);
        return 1;
    }

    private static int spawnCooldown(CommandContext<CommandSourceStack> context) {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        RaidConfig.get().setSpawnCooldownSeconds(seconds);
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("spawncooldown.set", String.valueOf(seconds)),
                true);
        return 1;
    }

    private static int spawnCooldownClear(CommandContext<CommandSourceStack> context) {
        RaidSpawnCooldown.clearAllCooldowns(context.getSource().getServer());
        context.getSource().sendSuccess(() -> MessagesConfig.msg("spawncooldown.cleared"), true);
        return 1;
    }

    private static int variableExitsInterval(CommandContext<CommandSourceStack> context) {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        RaidConfig.get().setVariableExitsIntervalSeconds(seconds);
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("variableexits.set", String.valueOf(seconds), String.valueOf(seconds / 60)),
                true);
        return 1;
    }

    private static int availableExits(CommandContext<CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        RaidConfig.get().setAvailableVariableExits(count);
        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether != null) RaidVariableExits.initialize(context.getSource().getServer(), nether, true);
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("variableexits.count_set", String.valueOf(count)), true);
        return 1;
    }

    private static int raidAddTime(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        com.hanset.raidmod.config.RaidAddTimeConfig.get().addExtraSeconds(target.getUUID(), seconds);
        int total = com.hanset.raidmod.config.RaidAddTimeConfig.get().getExtraSeconds(target.getUUID());
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("addtime.done", target.getName().getString(), String.valueOf(seconds), String.valueOf(total)),
                true);
        return 1;
    }

    private static int raidAddTimeClear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        com.hanset.raidmod.config.RaidAddTimeConfig.get().clearExtraSeconds(target.getUUID());
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("addtime.cleared", target.getName().getString()), true);
        return 1;
    }

    private static int raidOpen(CommandContext<CommandSourceStack> context) {
        RaidConfig.get().setRaidsOpen(true);
        net.minecraft.network.chat.Component msg = MessagesConfig.msg("lock.open");
        context.getSource().getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
        return 1;
    }

    private static int raidClose(CommandContext<CommandSourceStack> context) {
        RaidConfig.get().setRaidsOpen(false);
        net.minecraft.network.chat.Component msg = MessagesConfig.msg("lock.close");
        context.getSource().getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
        return 1;
    }

    private static int hideNametags(CommandContext<CommandSourceStack> context) {
        boolean hide = "true".equalsIgnoreCase(StringArgumentType.getString(context, "enabled"));
        RaidConfig.get().setHideNetherNametags(hide);
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg(hide ? "hidenametags.enabled" : "hidenametags.disabled"),
                true);
        return 1;
    }

    private static int variableExitsRefresh(CommandContext<CommandSourceStack> context) {
        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_unavailable"));
            return 0;
        }
        RaidVariableExits.initialize(context.getSource().getServer(), nether, true);
        context.getSource().sendSuccess(() -> MessagesConfig.msg("variableexits.refreshed"), true);
        return 1;
    }

    private static int raidWith(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer inviter = context.getSource().getPlayer();
        if (inviter == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.player_only"));
            return 0;
        }
        if (!RaidConfig.get().raids_open && !PermissionHelper.hasAdminPermission(context.getSource())) {
            context.getSource().sendFailure(MessagesConfig.msg("error.raids_closed"));
            return 0;
        }
        if (inviter.level().dimension() == Level.NETHER) {
            context.getSource().sendFailure(MessagesConfig.msg("error.no_raid_in_nether"));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (target.getUUID().equals(inviter.getUUID())) {
            context.getSource().sendFailure(MessagesConfig.msg("with.self"));
            return 0;
        }
        if (target.level().dimension() == Level.NETHER) {
            context.getSource().sendFailure(MessagesConfig.msg("with.target_in_nether", target.getName().getString()));
            return 0;
        }

        int cooldown = RaidConfig.get().raid_cooldown_seconds;
        boolean bypassInviter = context.getSource().hasPermission(3) || RaidConfig.get().canBypassCooldown(inviter.getUUID())
                || PermissionHelper.hasPermission(inviter, PermissionHelper.RAID_COOLDOWN_BYPASS);
        boolean bypassTarget = target.createCommandSourceStack().hasPermission(3) || RaidConfig.get().canBypassCooldown(target.getUUID())
                || PermissionHelper.hasPermission(target, PermissionHelper.RAID_COOLDOWN_BYPASS);
        if (cooldown > 0 && !bypassInviter) {
            Long last = getLastRaidTime(context.getSource().getServer(), inviter.getUUID());
            if (last != null && (System.currentTimeMillis() - last) / 1000 < cooldown) {
                context.getSource().sendFailure(MessagesConfig.msg("with.cooldown_self"));
                return 0;
            }
        }
        if (cooldown > 0 && !bypassTarget) {
            Long last = getLastRaidTime(context.getSource().getServer(), target.getUUID());
            if (last != null && (System.currentTimeMillis() - last) / 1000 < cooldown) {
                context.getSource().sendFailure(MessagesConfig.msg("with.cooldown_target", target.getName().getString()));
                return 0;
            }
        }

        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_unavailable"));
            return 0;
        }

        RaidSpawnData spawnData = getRaidSpawnData(nether);
        List<RaidSpawnData.SpawnEntry> available = RaidSpawnCooldown.getAvailableSpawns(spawnData).stream()
                .filter(s -> !RaidConfirmation.isReserved(s.name()))
                .toList();
        if (available.isEmpty()) {
            context.getSource().sendFailure(MessagesConfig.msg("error.no_available_spawns"));
            return 0;
        }

        RaidSpawnData.SpawnEntry spawn = available.get(RANDOM.nextInt(available.size()));
        if (!RaidConfirmation.createPaired(inviter.getUUID(), target.getUUID(), spawn)) {
            context.getSource().sendFailure(MessagesConfig.msg("with.pending_both"));
            return 0;
        }

        inviter.sendSystemMessage(createRaidConfirmMessage(MessagesConfig.get().get("confirm.paired_inviter", target.getName().getString())));
        target.sendSystemMessage(createRaidConfirmMessage(MessagesConfig.get().get("confirm.paired_target", inviter.getName().getString())));
        return 1;
    }

    private static int raidConfirm(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.player_only"));
            return 0;
        }
        if (player.level().dimension() == Level.NETHER) {
            context.getSource().sendFailure(MessagesConfig.msg("error.already_in_nether"));
            return 0;
        }

        RaidConfirmation.ConfirmResult result = RaidConfirmation.confirm(player.getUUID());
        if (result == null) {
            context.getSource().sendFailure(MessagesConfig.msg("confirm.no_pending"));
            return 0;
        }
        if (!RaidConfig.get().raids_open && !PermissionHelper.hasAdminPermission(context.getSource())) {
            context.getSource().sendFailure(MessagesConfig.msg("error.raids_closed"));
            return 0;
        }

        switch (result.type()) {
            case PENDING -> {
                context.getSource().sendSuccess(() -> MessagesConfig.msg("confirm.pending"), true);
                return 1;
            }
            case EXECUTE_SINGLE -> {
                executeSingleRaid(context.getSource().getServer(), player, result.spawn());
                return 1;
            }
            case EXECUTE_PAIRED -> {
                ServerPlayer playerA = context.getSource().getServer().getPlayerList().getPlayer(result.playerA());
                ServerPlayer playerB = context.getSource().getServer().getPlayerList().getPlayer(result.playerB());
                if (playerA == null || playerB == null || !playerA.isAlive() || !playerB.isAlive()
                        || playerA.level().dimension() == Level.NETHER || playerB.level().dimension() == Level.NETHER) {
                    context.getSource().sendFailure(MessagesConfig.msg("confirm.other_unavailable"));
                    return 0;
                }
                executePairedRaid(context.getSource().getServer(), playerA, playerB, result.spawn());
                return 1;
            }
        }
        return 0;
    }

    private static int raidCancel(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.player_only"));
            return 0;
        }

        RaidConfirmation.CancelResult result = RaidConfirmation.cancel(player.getUUID());
        if (!result.hadPending()) {
            context.getSource().sendFailure(MessagesConfig.msg("confirm.no_pending"));
            return 0;
        }

        context.getSource().sendSuccess(() -> MessagesConfig.msg("cancel.done"), true);
        if (result.wasPaired() && result.otherPlayerId() != null) {
            ServerPlayer other = context.getSource().getServer().getPlayerList().getPlayer(result.otherPlayerId());
            if (other != null) {
                other.sendSystemMessage(MessagesConfig.msg("cancel.paired", player.getName().getString()));
            }
        }
        return 1;
    }

    private static int raidStatSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.player_only"));
            return 0;
        }
        return sendStatMessage(context, player.getUUID(), player.getName().getString());
    }

    private static int raidStatOther(CommandContext<CommandSourceStack> context) {
        String nickname = StringArgumentType.getString(context, "player");
        MinecraftServer server = context.getSource().getServer();
        RaidStatsData data = RaidStatsTracker.getStatsData(server);
        if (data == null) {
            context.getSource().sendFailure(MessagesConfig.msg("stat.no_data"));
            return 0;
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(nickname);
        UUID uuid;
        String name;
        if (online != null) {
            uuid = online.getUUID();
            name = online.getName().getString();
        } else {
            Optional<UUID> found = data.getAll().entrySet().stream()
                    .filter(e -> e.getValue().name().equalsIgnoreCase(nickname))
                    .map(Map.Entry::getKey)
                    .findFirst();
            if (found.isEmpty()) {
                context.getSource().sendFailure(MessagesConfig.msg("stat.player_not_found", nickname));
                return 0;
            }
            uuid = found.get();
            name = data.get(uuid).name();
        }
        return sendStatMessage(context, uuid, name);
    }

    private static int sendStatMessage(CommandContext<CommandSourceStack> context, UUID uuid, String name) {
        RaidStatsData data = RaidStatsTracker.getStatsData(context.getSource().getServer());
        if (data == null) {
            context.getSource().sendFailure(MessagesConfig.msg("stat.no_data"));
            return 0;
        }
        RaidStatsData.PlayerStats stats = data.getOrCreate(uuid, name);
        long totalMin = stats.totalTimeMs() / 60_000;
        String timeStr = (totalMin / 60) + ":" + String.format("%02d", totalMin % 60);

        context.getSource().sendSuccess(() -> MessagesConfig.msg("stat.header", stats.name()), false);
        context.getSource().sendSuccess(() -> MessagesConfig.msg("stat.raids", String.valueOf(stats.raids())), false);
        context.getSource().sendSuccess(() -> MessagesConfig.msg("stat.success", String.valueOf(stats.successExits())), false);
        context.getSource().sendSuccess(() -> MessagesConfig.msg("stat.time", timeStr), false);
        context.getSource().sendSuccess(() -> MessagesConfig.msg("stat.kd", String.valueOf(stats.kills()), String.valueOf(stats.deaths())), false);
        return 1;
    }

    private static int raidTop(CommandContext<CommandSourceStack> context) {
        String type = StringArgumentType.getString(context, "type");
        RaidStatsData data = RaidStatsTracker.getStatsData(context.getSource().getServer());
        if (data == null) {
            context.getSource().sendFailure(MessagesConfig.msg("stat.no_data"));
            return 0;
        }
        List<RaidStatsData.PlayerStats> sorted;
        if ("kill".equalsIgnoreCase(type)) {
            sorted = data.getAll().values().stream()
                    .filter(s -> s.kills() > 0)
                    .sorted(Comparator.comparingInt(RaidStatsData.PlayerStats::kills).reversed())
                    .limit(10)
                    .toList();
        } else {
            sorted = data.getAll().values().stream()
                    .filter(s -> s.raids() > 0)
                    .sorted(Comparator.comparingInt(RaidStatsData.PlayerStats::raids).reversed())
                    .limit(10)
                    .toList();
        }
        context.getSource().sendSuccess(() -> MessagesConfig.msg("kill".equalsIgnoreCase(type) ? "top.header_kill" : "top.header_raid"), false);
        for (int i = 0; i < sorted.size(); i++) {
            RaidStatsData.PlayerStats s = sorted.get(i);
            int val = "kill".equalsIgnoreCase(type) ? s.kills() : s.raids();
            int place = i + 1;
            String medal = place == 1 ? "§6🥇" : place == 2 ? "§7🥈" : place == 3 ? "§c🥉" : "§f" + place + ".";
            context.getSource().sendSuccess(
                    () -> MessagesConfig.msg("top.line", medal, s.name(), String.valueOf(val)), false);
        }
        if (sorted.isEmpty()) {
            context.getSource().sendSuccess(() -> MessagesConfig.msg("top.no_data"), false);
        }
        return 1;
    }

    /** Телепортирует одного игрока в экспедицию. */
    private static void executeSingleRaid(MinecraftServer server, ServerPlayer player, RaidSpawnData.SpawnEntry spawn) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        Vec3 spawnVec = Vec3.atCenterOf(spawn.pos()).add(0, 0.5, 0);
        RaidSpawnCooldown.putOnCooldown(nether, spawn.name());

        player.teleportTo(nether, spawnVec.x, spawnVec.y, spawnVec.z, player.getYRot(), player.getXRot());
        player.sendSystemMessage(MessagesConfig.msg("raid.teleported_single", spawn.name()));
        RaidTimer.enterRaid(player);
        RaidStatsTracker.onRaidEnter(player);

        int cooldown = RaidConfig.get().raid_cooldown_seconds;
        boolean bypass = player.createCommandSourceStack().hasPermission(3)
                || RaidConfig.get().canBypassCooldown(player.getUUID())
                || PermissionHelper.hasPermission(player, PermissionHelper.RAID_COOLDOWN_BYPASS);
        if (cooldown > 0 && !bypass) {
            setLastRaidTime(server, player.getUUID(), System.currentTimeMillis());
        }
    }

    /** Телепортирует пару игроков на одну точку спавна. */
    private static void executePairedRaid(MinecraftServer server, ServerPlayer inviter, ServerPlayer acceptor, RaidSpawnData.SpawnEntry spawn) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        Vec3 spawnVec = Vec3.atCenterOf(spawn.pos()).add(0, 0.5, 0);
        RaidSpawnCooldown.putOnCooldown(nether, spawn.name());

        int cooldown = RaidConfig.get().raid_cooldown_seconds;
        boolean bypassInviter = inviter.createCommandSourceStack().hasPermission(3) || RaidConfig.get().canBypassCooldown(inviter.getUUID())
                || PermissionHelper.hasPermission(inviter, PermissionHelper.RAID_COOLDOWN_BYPASS);
        boolean bypassAcceptor = acceptor.createCommandSourceStack().hasPermission(3) || RaidConfig.get().canBypassCooldown(acceptor.getUUID())
                || PermissionHelper.hasPermission(acceptor, PermissionHelper.RAID_COOLDOWN_BYPASS);

        inviter.teleportTo(nether, spawnVec.x, spawnVec.y, spawnVec.z, inviter.getYRot(), inviter.getXRot());
        acceptor.teleportTo(nether, spawnVec.x + 1, spawnVec.y, spawnVec.z, acceptor.getYRot(), acceptor.getXRot());

        inviter.sendSystemMessage(MessagesConfig.msg("raid.teleported_paired", acceptor.getName().getString(), spawn.name()));
        acceptor.sendSystemMessage(MessagesConfig.msg("raid.teleported_paired", inviter.getName().getString(), spawn.name()));

        RaidTimer.enterRaid(inviter);
        RaidTimer.enterRaid(acceptor);
        RaidStatsTracker.onRaidEnter(inviter);
        RaidStatsTracker.onRaidEnter(acceptor);

        long now = System.currentTimeMillis();
        if (cooldown > 0 && !bypassInviter) setLastRaidTime(server, inviter.getUUID(), now);
        if (cooldown > 0 && !bypassAcceptor) setLastRaidTime(server, acceptor.getUUID(), now);
    }

    private static int spawnClear(CommandContext<CommandSourceStack> context) {
        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_unavailable"));
            return 0;
        }

        RaidSpawnData data = getRaidSpawnData(nether);
        int count = data.getSpawns().size();
        data.clear();
        context.getSource().sendSuccess(() -> MessagesConfig.msg("spawn.cleared", String.valueOf(count)), true);
        return 1;
    }

    private static int raidNoCooldown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        RaidConfig.get().addRaidCooldownBypass(target.getUUID());
        context.getSource().sendSuccess(() -> MessagesConfig.msg("nocd.added", target.getName().getString()), true);
        return 1;
    }

    private static int raidNoCooldownRemove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        RaidConfig.get().removeRaidCooldownBypass(target.getUUID());
        context.getSource().sendSuccess(
                () -> MessagesConfig.msg("nocd.removed", target.getName().getString()), true);
        return 1;
    }

    private static int raidStatClear(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        RaidStatsData data = RaidStatsTracker.getStatsData(server);
        if (data == null) {
            return 0;
        }

        String target = StringArgumentType.getString(context, "target");
        if ("all".equalsIgnoreCase(target)) {
            data.clearAll();
            context.getSource().sendSuccess(() -> MessagesConfig.msg("statclear.all"), true);
            return 1;
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(target);
        if (player != null) {
            data.clear(player.getUUID());
            context.getSource().sendSuccess(() -> MessagesConfig.msg("statclear.player", player.getName().getString()), true);
            return 1;
        }

        if (data.clearByName(target)) {
            context.getSource().sendSuccess(() -> MessagesConfig.msg("statclear.player", target), true);
            return 1;
        }

        context.getSource().sendFailure(MessagesConfig.msg("stat.player_not_found", target));
        return 0;
    }

    private static int spawnList(CommandContext<CommandSourceStack> context) {
        ServerLevel nether = context.getSource().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            context.getSource().sendFailure(MessagesConfig.msg("error.nether_unavailable"));
            return 0;
        }

        RaidSpawnData data = getRaidSpawnData(nether);
        List<RaidSpawnData.SpawnEntry> spawns = data.getSpawns();
        if (spawns.isEmpty()) {
            context.getSource().sendFailure(MessagesConfig.msg("error.no_spawns"));
            return 0;
        }

        context.getSource().sendSuccess(() -> MessagesConfig.msg("spawnlist.header", String.valueOf(spawns.size())), false);
        for (int i = 0; i < spawns.size(); i++) {
            final int num = i + 1;
            final RaidSpawnData.SpawnEntry entry = spawns.get(i);
            context.getSource().sendSuccess(() -> MessagesConfig.msg("spawnlist.line",
                    String.valueOf(num), entry.name(),
                    String.valueOf(entry.pos().getX()), String.valueOf(entry.pos().getY()), String.valueOf(entry.pos().getZ())), false);
        }
        return 1;
    }

    private static BlockPos getTargetedBlock(ServerPlayer player, double range) {
        var hitResult = player.pick(range, 0, false);
        if (hitResult instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    public static RaidDoorData getRaidDoorData(ServerLevel level) {
        return level.getChunkSource().getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(RaidDoorData::new, RaidDoorData::load, null),
                RaidDoorData.getDataId()
        );
    }

    public static RaidSpawnData getRaidSpawnData(ServerLevel level) {
        return level.getChunkSource().getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(RaidSpawnData::new, RaidSpawnData::load, null),
                RaidSpawnData.getDataId()
        );
    }

    public static RaidPlayerData getRaidPlayerData(ServerLevel level) {
        return level.getChunkSource().getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(RaidPlayerData::new, RaidPlayerData::load, null),
                RaidPlayerData.getDataId()
        );
    }

    public static RaidVariableExitsData getRaidVariableExitsData(ServerLevel level) {
        return level.getChunkSource().getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(RaidVariableExitsData::new, RaidVariableExitsData::load, null),
                RaidVariableExitsData.getDataId()
        );
    }

    private static Long getLastRaidTime(MinecraftServer server, UUID playerId) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld == null ? null : getRaidPlayerData(overworld).getLastRaidTime(playerId);
    }

    private static void setLastRaidTime(MinecraftServer server, UUID playerId, long timestamp) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) getRaidPlayerData(overworld).setLastRaidTime(playerId, timestamp);
    }

}
