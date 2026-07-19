package com.hanset.raidmod;

import com.hanset.raidmod.config.RaidConfig;
import com.hanset.raidmod.data.RaidSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Кулдауны точек спавна. Кулдаун не тикает, пока в радиусе 15 блоков (по горизонтали) есть игроки в аду.
 */
public class RaidSpawnCooldown {

    public static void tick(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        long now = System.currentTimeMillis();

        List<ServerPlayer> netherPlayers = server.getPlayerList().getPlayers().stream()
                .filter(p -> p.level().dimension() == Level.NETHER && p.isAlive())
                .toList();

        RaidSpawnData spawnData = com.hanset.raidmod.command.RaidCommands.getRaidSpawnData(nether);
        Map<String, RaidSpawnData.CooldownEntry> cooldowns = spawnData.getCooldowns();
        if (cooldowns.isEmpty()) return;

        for (Map.Entry<String, RaidSpawnData.CooldownEntry> entry : cooldowns.entrySet()) {
            String name = entry.getKey();
            long until = entry.getValue().until();
            long lastProcessed = entry.getValue().lastProcessed() > 0
                    ? entry.getValue().lastProcessed() : now;
            long elapsedMs = Math.max(0L, now - lastProcessed);

            RaidSpawnData.SpawnEntry spawn = spawnData.getByName(name).orElse(null);
            if (spawn == null) {
                spawnData.removeCooldown(name);
                continue;
            }

            boolean hasPlayersNearby = netherPlayers.stream()
                    .anyMatch(p -> isInHorizontalRadius(
                            p.blockPosition(), spawn.pos(), RaidConfig.get().spawn_cooldown_pause_radius));

            if (hasPlayersNearby) {
                until += elapsedMs;
            }

            if (now >= until) {
                spawnData.removeCooldown(name);
            } else {
                spawnData.putCooldown(name, until, now);
            }
        }
    }

    private static boolean isInHorizontalRadius(BlockPos a, BlockPos b, int radius) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Ставит точку на кулдаун. */
    public static void putOnCooldown(ServerLevel nether, String spawnName) {
        long duration = RaidConfig.get().spawn_cooldown_seconds * 1000L;
        long now = System.currentTimeMillis();
        com.hanset.raidmod.command.RaidCommands.getRaidSpawnData(nether)
                .putCooldown(spawnName, now + duration, now);
    }

    /** Доступна ли точка (не на кулдауне). */
    public static boolean isAvailable(RaidSpawnData spawnData, String spawnName) {
        RaidSpawnData.CooldownEntry cooldown = spawnData.getCooldown(spawnName);
        return cooldown == null || System.currentTimeMillis() >= cooldown.until();
    }

    /** Возвращает список доступных точек спавна. */
    public static List<RaidSpawnData.SpawnEntry> getAvailableSpawns(RaidSpawnData spawnData) {
        return spawnData.getSpawns().stream()
                .filter(s -> isAvailable(spawnData, s.name()))
                .toList();
    }

    /** Сбрасывает кулдауны всех точек. */
    public static void clearAllCooldowns(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            com.hanset.raidmod.command.RaidCommands.getRaidSpawnData(nether).clearCooldowns();
        }
    }
}
