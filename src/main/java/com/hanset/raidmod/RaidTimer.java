package com.hanset.raidmod;

import com.hanset.raidmod.command.RaidCommands;
import com.hanset.raidmod.config.RaidConfig;
import com.hanset.raidmod.data.RaidPlayerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Таймер рейда: ограниченное время в аду, boss bar, смерть по истечении.
 */
public class RaidTimer {

    private static final Map<UUID, RaidEntry> activeRaids = new HashMap<>();
    private static final Set<UUID> endedRaids = new HashSet<>();

    public static void enterRaid(ServerPlayer player) {
        enterRaidWithTime(player, System.currentTimeMillis());
    }

    /** Восстановление рейда после рестарта (с сохранённым временем входа). */
    public static void enterRaidWithTime(ServerPlayer player, long entryTime) {
        endedRaids.remove(player.getUUID());
        activeRaids.put(player.getUUID(), new RaidEntry(entryTime));
        syncToPlayerData(player.getServer(), player.getUUID(), entryTime);
    }

    public static void leaveRaid(ServerPlayer player) {
        endedRaids.remove(player.getUUID());
        RaidEntry entry = activeRaids.remove(player.getUUID());
        if (entry != null && entry.bossBar != null) {
            entry.bossBar.removeAllPlayers();
            player.server.getCustomBossEvents().remove(entry.bossBar);
        }
        syncRemoveFromPlayerData(player.getServer(), player.getUUID());
    }

    /** Выход из рейда по UUID (при отключении во время выхода через дверь). */
    public static void leaveRaidByUuid(UUID uuid, MinecraftServer server) {
        endedRaids.remove(uuid);
        RaidEntry entry = activeRaids.remove(uuid);
        if (entry != null && entry.bossBar != null && server != null) {
            entry.bossBar.removeAllPlayers();
            server.getCustomBossEvents().remove(entry.bossBar);
        }
        syncRemoveFromPlayerData(server, uuid);
    }

    public static boolean isInRaid(UUID uuid) {
        return activeRaids.containsKey(uuid) && !endedRaids.contains(uuid);
    }

    /** Немедленно очищает сохранённое состояние после фактической смерти игрока. */
    public static void clearPersistentState(ServerPlayer player) {
        if (player != null) {
            endedRaids.add(player.getUUID());
            syncRemoveFromPlayerData(player.getServer(), player.getUUID());
        }
    }

    /** Время входа в рейд (мс) или 0 если не в рейде. */
    public static long getEntryTime(UUID uuid) {
        if (endedRaids.contains(uuid)) return 0;
        RaidEntry e = activeRaids.get(uuid);
        return e != null ? e.entryTime : 0;
    }

    /** Для персистенции: UUID -> entryTime (ms). */
    public static Map<UUID, Long> getRaidsForPersistence() {
        Map<UUID, Long> out = new HashMap<>();
        for (Map.Entry<UUID, RaidEntry> e : activeRaids.entrySet()) {
            if (!endedRaids.contains(e.getKey())) {
                out.put(e.getKey(), e.getValue().entryTime);
            }
        }
        return out;
    }

    private static void syncToPlayerData(MinecraftServer server, UUID uuid, long entryTime) {
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            RaidPlayerData data = RaidCommands.getRaidPlayerData(overworld);
            data.setPlayerInRaid(uuid, entryTime);
        }
    }

    private static void syncRemoveFromPlayerData(MinecraftServer server, UUID uuid) {
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            RaidCommands.getRaidPlayerData(overworld).removePlayer(uuid);
        }
    }

    public static void tick(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        long now = System.currentTimeMillis();

        activeRaids.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            RaidEntry raid = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (endedRaids.remove(playerId)) {
                syncRemoveFromPlayerData(server, playerId);
                cleanupBossBar(server, raid);
                return true;
            }

            if (player == null || !player.isAlive()) {
                if (player == null) {
                    syncToPlayerData(server, playerId, raid.entryTime);
                } else {
                    syncRemoveFromPlayerData(server, playerId);
                }
                cleanupBossBar(server, raid);
                return true;
            }

            if (player.level().dimension() != Level.NETHER) {
                syncRemoveFromPlayerData(server, playerId);
                cleanupBossBar(server, raid);
                return true;
            }

            int raidTimeSeconds = com.hanset.raidmod.config.RaidConfig.get().raid_time_seconds
                    + com.hanset.raidmod.config.RaidAddTimeConfig.get().getExtraSeconds(playerId);
            long elapsedMs = now - raid.entryTime;
            long remainingMs = raidTimeSeconds * 1000L - elapsedMs;
            int remainingSeconds = (int) Math.ceil(remainingMs / 1000.0);

            if (remainingSeconds <= 0) {
                player.hurt(player.damageSources().generic(), Float.MAX_VALUE);
                player.displayClientMessage(com.hanset.raidmod.config.MessagesConfig.msg("raid.time_expired"), false);
                // Смерть учитывает AFTER_DEATH. Если урон пережит (например, тотемом),
                // рейд остаётся активным и таймер повторит попытку на следующем тике.
                if (!player.isAlive()) {
                    endedRaids.remove(playerId);
                    syncRemoveFromPlayerData(server, playerId);
                    cleanupBossBar(server, raid);
                    return true;
                }
                return false;
            }

            if (RaidConfig.get().bossbar_enabled) {
                CustomBossEvent bossBar = getOrCreateBossBar(server, raid, playerId);
                bossBar.setMax(raidTimeSeconds);
                bossBar.setValue(remainingSeconds);
                bossBar.setName(com.hanset.raidmod.config.MessagesConfig.msg(
                        "raid.bossbar", formatTime(remainingSeconds)));
            } else if (raid.bossBar != null) {
                cleanupBossBar(server, raid);
                raid.bossBar = null;
            }

            return false;
        });
    }

    private static void cleanupBossBar(MinecraftServer server, RaidEntry raid) {
        if (raid.bossBar != null) {
            raid.bossBar.removeAllPlayers();
            server.getCustomBossEvents().remove(raid.bossBar);
        }
    }

    private static CustomBossEvent getOrCreateBossBar(MinecraftServer server, RaidEntry raid, UUID playerId) {
        if (raid.bossBar == null) {
            ResourceLocation id = new ResourceLocation(RaidMod.MOD_ID, "raid_" + playerId);
            raid.bossBar = server.getCustomBossEvents().create(id, com.hanset.raidmod.config.MessagesConfig.msg("raid.bossbar_title"));
            raid.bossBar.setColor(net.minecraft.world.BossEvent.BossBarColor.RED);
            raid.bossBar.setOverlay(net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            raid.bossBar.setDarkenScreen(false);
            raid.bossBar.setPlayBossMusic(false);
            raid.bossBar.setCreateWorldFog(false);

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                raid.bossBar.addPlayer(player);
            }
        }
        return raid.bossBar;
    }

    private static String formatTime(int totalSeconds) {
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        return String.format("%d:%02d", min, sec);
    }

    private static class RaidEntry {
        final long entryTime;
        CustomBossEvent bossBar;

        RaidEntry(long entryTime) {
            this.entryTime = entryTime;
        }
    }
}
