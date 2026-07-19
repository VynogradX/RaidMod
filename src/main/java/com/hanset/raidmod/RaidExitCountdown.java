package com.hanset.raidmod;

import com.hanset.raidmod.command.RaidCommands;
import com.hanset.raidmod.data.RaidDoorData;
import com.hanset.raidmod.data.RaidVariableExitsData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import com.hanset.raidmod.config.MessagesConfig;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Управляет выходом из ада: обнаружение игроков у дверей, таймеры, телепортация.
 * Выход начинается при подходе к двери (в радиусе, перед дверью). Поддержка совместных дверей.
 */
public class RaidExitCountdown {

    private static final Map<BlockPos, ActiveCountdown> activeCountdowns = new HashMap<>();

    public static void tick(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        RaidDoorData doorData = RaidCommands.getRaidDoorData(nether);
        RaidVariableExitsData varData = RaidCommands.getRaidVariableExitsData(nether);
        List<RaidDoorData.DoorEntry> activeDoors = buildActiveExits(doorData, varData);
        List<ServerPlayer> netherPlayers = server.getPlayerList().getPlayers().stream()
                .filter(p -> p.level().dimension() == Level.NETHER && p.isAlive())
                .filter(p -> RaidTimer.isInRaid(p.getUUID()))
                .toList();

        long now = System.currentTimeMillis();

        // Проверяем активные таймеры
        activeCountdowns.entrySet().removeIf(entry -> {
            BlockPos doorPos = entry.getKey();
            ActiveCountdown countdown = entry.getValue();
            RaidDoorData.DoorEntry door = doorData.getDoorAt(doorPos).orElse(null);
            if (door == null) return true;

            // Проверяем, все ли игроки ещё в радиусе (без проверки стены — выход уже активирован)
            Set<UUID> stillValid = new HashSet<>();
            for (UUID pid : countdown.playerIds) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p != null && isPlayerValidAtDoor(p, doorPos, door.radius(), nether, false)) {
                    stillValid.add(pid);
                }
            }

            if (stillValid.size() < door.requiredPlayers()) {
                for (UUID pid : countdown.playerIds) {
                    ServerPlayer p = server.getPlayerList().getPlayer(pid);
                    if (p != null) {
                        p.displayClientMessage(MessagesConfig.msg("exit.cancelled"), true);
                    }
                }
                return true;
            }

            long elapsed = now - countdown.startTime;
            int remainingSeconds = (int) Math.ceil((door.exitTime() * 1000L - elapsed) / 1000.0);

            if (remainingSeconds <= 0) {
                for (UUID pid : countdown.playerIds) {
                    ServerPlayer p = server.getPlayerList().getPlayer(pid);
                    if (p != null) {
                        long entryTime = RaidTimer.getEntryTime(pid);
                        if (entryTime > 0) {
                            com.hanset.raidmod.RaidStatsTracker.onRaidSuccessExit(p, entryTime);
                        }
                        RaidTimer.leaveRaid(p);
                        teleportToSpawn(p);
                    }
                }
                return true;
            }

            // Партиклы и сообщение над хотбаром
            if (com.hanset.raidmod.config.RaidConfig.get().exit_particles_enabled) {
                spawnBoundaryParticles(nether, doorPos, door.radius());
            }
            String exitName = door.name();
            for (UUID pid : countdown.playerIds) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p != null) {
                    p.displayClientMessage(MessagesConfig.msg("exit.countdown", exitName, String.valueOf(remainingSeconds)), true);
                }
            }
            return false;
        });

        // Зелёные эффекты вокруг активных выходов (кроме тех, где идёт таймер — там красные)
        Set<BlockPos> doorsInCountdown = activeCountdowns.keySet();
        if (com.hanset.raidmod.config.RaidConfig.get().exit_particles_enabled
                && !netherPlayers.isEmpty() && server.getTickCount() % 10 == 0) {
            for (RaidDoorData.DoorEntry door : activeDoors) {
                if (!doorsInCountdown.contains(door.pos())) {
                    spawnActiveExitParticles(nether, door.pos(), door.radius());
                }
            }
        }

        // Ищем новых игроков у дверей (только активные: статичный + 2 переменных)
        for (RaidDoorData.DoorEntry door : activeDoors) {
            if (doorsInCountdown.contains(door.pos())) continue;

            List<ServerPlayer> validPlayers = netherPlayers.stream()
                    .filter(p -> isPlayerValidAtDoor(p, door.pos(), door.radius(), nether, true))
                    .filter(p -> !isInAnyCountdown(p.getUUID()))
                    .toList();

            if (validPlayers.size() >= door.requiredPlayers()) {
                Set<UUID> playerIds = validPlayers.stream().limit(door.requiredPlayers()).map(ServerPlayer::getUUID).collect(Collectors.toSet());
                activeCountdowns.put(door.pos(), new ActiveCountdown(playerIds, now));
            } else if (validPlayers.size() > 0 && door.requiredPlayers() >= 2) {
                // Один игрок у выхода на 2+ — подсказка над хотбаром
                for (ServerPlayer p : validPlayers) {
                    p.displayClientMessage(MessagesConfig.msg("exit.need_more", door.name()), true);
                }
            }
        }
    }

    private static boolean isInAnyCountdown(UUID playerId) {
        return activeCountdowns.values().stream().anyMatch(c -> c.playerIds.contains(playerId));
    }

    /** Игрок в процессе выхода через дверь (при отключении — засчитать выход). */
    public static boolean isPlayerInExitCountdown(UUID playerId) {
        return isInAnyCountdown(playerId);
    }

    /**
     * Игрок в радиусе выхода. При checkLineOfSight=true — проверяется прямая видимость (не за стеной).
     * При активированном выходе (checkLineOfSight=false) — только радиус.
     */
    private static boolean isPlayerValidAtDoor(ServerPlayer player, BlockPos doorPos, int radius, ServerLevel level, boolean checkLineOfSight) {
        if (!RaidTimer.isInRaid(player.getUUID()) || player.level().dimension() != Level.NETHER || !player.isAlive()) {
            return false;
        }

        double cx = doorPos.getX() + 0.5;
        double cy = doorPos.getY() + 0.5;
        double cz = doorPos.getZ() + 0.5;
        double dx = player.getX() - cx;
        double dy = player.getY() - cy;
        double dz = player.getZ() - cz;
        double distanceSquared = checkLineOfSight
                ? dx * dx + dz * dz
                : dx * dx + dy * dy + dz * dz;
        if (distanceSquared > radius * radius) return false;

        if (!checkLineOfSight) return true;

        // Проверка: игрок перед дверью (есть прямая видимость)
        Vec3 from = player.getEyePosition(1);
        Vec3 to = new Vec3(cx, doorPos.getY() + 0.5, cz);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        BlockPos hitPos = hit.getBlockPos();
        return hitPos.equals(doorPos) || hitPos.equals(doorPos.above());
    }

    /** Зелёные частицы вокруг рабочего выхода: горизонтальные кольца + вертикальные (когда таймер не активен). */
    private static void spawnActiveExitParticles(ServerLevel level, BlockPos doorPos, int radius) {
        double cx = doorPos.getX() + 0.5;
        double cy = doorPos.getY() + 0.5;
        double cz = doorPos.getZ() + 0.5;
        DustParticleOptions greenDust = new DustParticleOptions(new Vector3f(0.2f, 1f, 0.2f), 0.8f);
        int points = Math.max(10, radius * 3);

        // Горизонтальные кольца на разных высотах (снизу, середина, сверху)
        double[] ringHeights = {cy - 0.5, cy, cy + 0.5, cy + 1.0, cy + 1.5};
        for (double ry : ringHeights) {
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double x = cx + radius * Math.cos(angle);
                double z = cz + radius * Math.sin(angle);
                level.sendParticles(greenDust, x, ry, z, 1, 0.04, 0.04, 0.04, 0.015);
            }
        }

        // Вертикальные кольца — создают объёмный эффект «клетки» вокруг выхода
        int vertPoints = Math.max(8, radius * 2);
        double r = Math.min(radius * 0.8, 1.2); // высота вертикальных колец ~2 блока
        for (int i = 0; i < vertPoints; i++) {
            double angle = 2 * Math.PI * i / vertPoints;
            // Кольцо в вертикальной плоскости YZ (вид сбоку)
            double y1 = cy + r * Math.cos(angle);
            double z1 = cz + r * Math.sin(angle);
            level.sendParticles(greenDust, cx, y1, z1, 1, 0.03, 0.03, 0.03, 0.01);
            // Кольцо в вертикальной плоскости XY (вид спереди)
            double x2 = cx + r * Math.cos(angle);
            double y2 = cy + r * Math.sin(angle);
            level.sendParticles(greenDust, x2, y2, cz, 1, 0.03, 0.03, 0.03, 0.01);
        }
    }

    private static void spawnBoundaryParticles(ServerLevel level, BlockPos doorPos, int radius) {
        double cx = doorPos.getX() + 0.5;
        double cy = doorPos.getY();
        double cz = doorPos.getZ() + 0.5;
        DustParticleOptions redDust = new DustParticleOptions(new Vector3f(1, 0, 0), 1.0f);

        int points = Math.max(12, radius * 4);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            level.sendParticles(redDust, x, cy, z, 2, 0.1, 0.1, 0.1, 0);
        }
    }

    private static void teleportToSpawn(ServerPlayer player) {
        var spawnPos = player.getRespawnPosition();
        ServerLevel spawnLevel = player.server.getLevel(player.getRespawnDimension());

        if (spawnPos != null && spawnLevel != null) {
            player.teleportTo(spawnLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        } else {
            ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                BlockPos overworldSpawn = overworld.getSharedSpawnPos();
                player.teleportTo(overworld, overworldSpawn.getX() + 0.5, overworldSpawn.getY(), overworldSpawn.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
            }
        }
        player.displayClientMessage(MessagesConfig.msg("exit.returned"), false);
    }

    /** Собирает активные выходы: статичный (если есть) + 2 переменных. */
    private static List<RaidDoorData.DoorEntry> buildActiveExits(RaidDoorData doorData, RaidVariableExitsData varData) {
        Map<BlockPos, RaidDoorData.DoorEntry> result = new LinkedHashMap<>();
        doorData.getStaticDoor().ifPresent(door -> result.put(door.pos(), door));
        for (BlockPos pos : varData.getVariableExits()) {
            doorData.getDoorAt(pos).ifPresent(door -> result.putIfAbsent(door.pos(), door));
        }
        return new ArrayList<>(result.values());
    }

    private record ActiveCountdown(Set<UUID> playerIds, long startTime) {}
}
