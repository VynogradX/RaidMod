package com.hanset.raidmod;

import com.hanset.raidmod.config.MessagesConfig;
import com.hanset.raidmod.data.RaidSpawnData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Подтверждение выхода в экспедицию. Игрок вводит /expedition или /expedition with,
 * получает запрос подтверждения. /expedition confirm — принять, /expedition cancel — отменить.
 * Кулдаун не тратится при отмене.
 */
public class RaidConfirmation {

    private static long timeoutMs() {
        return com.hanset.raidmod.config.RaidConfig.get().confirmation_timeout_seconds * 1000L;
    }

    private record PendingSingle(UUID playerId, RaidSpawnData.SpawnEntry spawn, long timestamp) {}

    private record PendingPaired(UUID playerA, UUID playerB, RaidSpawnData.SpawnEntry spawn,
                                 boolean confirmedA, boolean confirmedB, long timestamp) {}

    /** Одиночные ожидающие рейды: playerId -> PendingSingle */
    private static final Map<UUID, PendingSingle> pendingSingle = new ConcurrentHashMap<>();

    /** Парные ожидающие: оба игрока ссылаются на один PendingPaired */
    private static final Map<UUID, PendingPaired> pendingPairedByPlayer = new ConcurrentHashMap<>();

    /** Зарезервированные точки спавна (имя -> до какого времени резерв, мс) */
    private static final Map<String, Long> reservedSpawns = new ConcurrentHashMap<>();

    /** Зарезервировать точку спавна. */
    public static synchronized void reserveSpawn(String spawnName) {
        reservedSpawns.put(spawnName, System.currentTimeMillis() + timeoutMs());
    }

    /** Освободить резерв. */
    public static synchronized void releaseSpawn(String spawnName) {
        reservedSpawns.remove(spawnName);
    }

    /** Занята ли точка (зарезервирована). */
    public static synchronized boolean isReserved(String spawnName) {
        Long until = reservedSpawns.get(spawnName);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            reservedSpawns.remove(spawnName);
            return false;
        }
        return true;
    }

    /** Создать ожидающий одиночный рейд. Возвращает true если создан. */
    public static synchronized boolean createSingle(UUID playerId, RaidSpawnData.SpawnEntry spawn) {
        if (pendingSingle.containsKey(playerId) || pendingPairedByPlayer.containsKey(playerId)
                || isReserved(spawn.name())) return false;
        reserveSpawn(spawn.name());
        pendingSingle.put(playerId, new PendingSingle(playerId, spawn, System.currentTimeMillis()));
        return true;
    }

    /** Создать ожидающий парный рейд. Оба игрока должны подтвердить. */
    public static synchronized boolean createPaired(UUID playerA, UUID playerB, RaidSpawnData.SpawnEntry spawn) {
        if (playerA.equals(playerB)
                || pendingSingle.containsKey(playerA) || pendingSingle.containsKey(playerB)
                || pendingPairedByPlayer.containsKey(playerA) || pendingPairedByPlayer.containsKey(playerB)
                || isReserved(spawn.name())) return false;
        reserveSpawn(spawn.name());
        PendingPaired p = new PendingPaired(playerA, playerB, spawn, false, false, System.currentTimeMillis());
        pendingPairedByPlayer.put(playerA, p);
        pendingPairedByPlayer.put(playerB, p);
        return true;
    }

    /** Подтвердить рейд. Возвращает результат: EXECUTE_SINGLE, EXECUTE_PAIRED, PENDING (ждём второго), null если нет ожидания. */
    public static synchronized ConfirmResult confirm(UUID playerId) {
        PendingSingle ps = pendingSingle.remove(playerId);
        if (ps != null) {
            releaseSpawn(ps.spawn.name());
            return new ConfirmResult(ConfirmType.EXECUTE_SINGLE, ps.spawn, ps.playerId, null);
        }

        PendingPaired pp = pendingPairedByPlayer.get(playerId);
        if (pp == null) return null;

        boolean isA = pp.playerA.equals(playerId);
        PendingPaired updated = isA
                ? new PendingPaired(pp.playerA, pp.playerB, pp.spawn, true, pp.confirmedB, pp.timestamp)
                : new PendingPaired(pp.playerA, pp.playerB, pp.spawn, pp.confirmedA, true, pp.timestamp);

        if (updated.confirmedA && updated.confirmedB) {
            pendingPairedByPlayer.remove(pp.playerA);
            pendingPairedByPlayer.remove(pp.playerB);
            releaseSpawn(pp.spawn.name());
            return new ConfirmResult(ConfirmType.EXECUTE_PAIRED, pp.spawn, pp.playerA, pp.playerB);
        } else {
            pendingPairedByPlayer.put(pp.playerA, updated);
            pendingPairedByPlayer.put(pp.playerB, updated);
            return new ConfirmResult(ConfirmType.PENDING, pp.spawn, null, null);
        }
    }

    /** Отменить рейд. Возвращает true если было что отменить. */
    public static synchronized CancelResult cancel(UUID playerId) {
        PendingSingle ps = pendingSingle.remove(playerId);
        if (ps != null) {
            releaseSpawn(ps.spawn.name());
            return new CancelResult(true, false, null);
        }

        PendingPaired pp = pendingPairedByPlayer.remove(playerId);
        if (pp == null) return new CancelResult(false, false, null);

        pendingPairedByPlayer.remove(pp.playerA);
        pendingPairedByPlayer.remove(pp.playerB);
        releaseSpawn(pp.spawn.name());

        UUID otherId = pp.playerA.equals(playerId) ? pp.playerB : pp.playerA;
        return new CancelResult(true, true, otherId);
    }

    /** Есть ли у игрока ожидающее подтверждение. */
    public static synchronized boolean hasPending(UUID playerId) {
        if (pendingSingle.containsKey(playerId)) return true;
        PendingPaired pp = pendingPairedByPlayer.get(playerId);
        if (pp == null) return false;
        return System.currentTimeMillis() - pp.timestamp < timeoutMs();
    }

    public static synchronized void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        pendingSingle.entrySet().removeIf(e -> {
            if (now - e.getValue().timestamp >= timeoutMs()) {
                releaseSpawn(e.getValue().spawn.name());
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) p.sendSystemMessage(MessagesConfig.msg("confirm.expired_single"));
                return true;
            }
            return false;
        });

        Set<PendingPaired> toRemove = new HashSet<>();
        for (PendingPaired pp : new HashSet<>(pendingPairedByPlayer.values())) {
            if (now - pp.timestamp >= timeoutMs()) {
                toRemove.add(pp);
            }
        }
        for (PendingPaired pp : toRemove) {
            pendingPairedByPlayer.remove(pp.playerA);
            pendingPairedByPlayer.remove(pp.playerB);
            releaseSpawn(pp.spawn.name());
            for (UUID id : List.of(pp.playerA, pp.playerB)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) p.sendSystemMessage(MessagesConfig.msg("confirm.expired_paired"));
            }
        }

        reservedSpawns.entrySet().removeIf(e -> now >= e.getValue());
    }

    public enum ConfirmType { EXECUTE_SINGLE, EXECUTE_PAIRED, PENDING }

    public record ConfirmResult(ConfirmType type, RaidSpawnData.SpawnEntry spawn, UUID playerA, UUID playerB) {}

    public record CancelResult(boolean hadPending, boolean wasPaired, UUID otherPlayerId) {}
}
