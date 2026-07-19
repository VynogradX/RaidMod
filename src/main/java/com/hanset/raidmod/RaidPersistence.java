package com.hanset.raidmod;

import com.hanset.raidmod.command.RaidCommands;
import com.hanset.raidmod.config.RaidConfig;
import com.hanset.raidmod.data.RaidPlayerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сохранение и восстановление игроков в рейде после рестарта.
 */
public class RaidPersistence {

    private static final int SAVE_INTERVAL_TICKS = 20 * 60;

    private static int tickCounter = 0;

    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= SAVE_INTERVAL_TICKS) {
            tickCounter = 0;
            syncAndSave(server);
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        syncAndSave(server);
    }

    private static void syncAndSave(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        RaidPlayerData data = RaidCommands.getRaidPlayerData(overworld);
        Map<UUID, Long> merged = new HashMap<>(RaidTimer.getRaidsForPersistence());
        for (Map.Entry<UUID, Long> e : data.getPlayersInRaid().entrySet()) {
            if (!merged.containsKey(e.getKey())) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        data.setAll(merged);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player == null) return;
        RaidConfirmation.CancelResult cancelResult = RaidConfirmation.cancel(player.getUUID());
        if (cancelResult.wasPaired() && cancelResult.otherPlayerId() != null) {
            ServerPlayer other = player.getServer().getPlayerList().getPlayer(cancelResult.otherPlayerId());
            if (other != null) {
                other.sendSystemMessage(com.hanset.raidmod.config.MessagesConfig.msg("paired.disconnect"));
            }
        }
        if (RaidTimer.isInRaid(player.getUUID())) {
            syncAndSave(player.getServer());
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        RaidPlayerData data = RaidCommands.getRaidPlayerData(overworld);
        UUID uuid = player.getUUID();

        if (!data.isInRaid(uuid)) return;

        // Persistent raid имеет смысл только для игрока, который действительно
        // вернулся в Nether. Иначе это устаревшая запись после смерти/телепорта.
        if (player.level().dimension() != Level.NETHER) {
            RaidTimer.leaveRaid(player);
            return;
        }

        Long entryTime = data.getEntryTime(uuid);
        if (entryTime == null) return;

        long now = System.currentTimeMillis();
        int extraSeconds = com.hanset.raidmod.config.RaidAddTimeConfig.get().getExtraSeconds(uuid);
        long raidTimeMs = (RaidConfig.get().raid_time_seconds + extraSeconds) * 1000L;
        long remainingMs = raidTimeMs - (now - entryTime);

        long offlineTimeoutMs = RaidConfig.get().offline_timeout_seconds * 1000L;
        boolean absentTooLong = offlineTimeoutMs > 0 && now - entryTime > offlineTimeoutMs;
        if (remainingMs <= 0 || absentTooLong) {
            RaidTimer.enterRaidWithTime(player, entryTime);
            player.getServer().execute(() -> {
                if (player.isAlive()) {
                    player.hurt(player.damageSources().generic(), Float.MAX_VALUE);
                    player.displayClientMessage(com.hanset.raidmod.config.MessagesConfig.msg(
                            absentTooLong ? "raid.absent_too_long" : "raid.time_expired_offline"), false);
                }
            });
            return;
        }

        RaidTimer.enterRaidWithTime(player, entryTime);
    }
}
