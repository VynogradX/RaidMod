package com.hanset.raidmod;

import com.hanset.raidmod.data.RaidStatsData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Отслеживание статистики рейдов: входы, выходы, убийства, смерти.
 */
public class RaidStatsTracker {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(RaidStatsTracker::onEntityDeath);
    }

    /** Вызвать при входе в рейд. */
    public static void onRaidEnter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        RaidStatsData data = getStatsData(server);
        if (data != null) {
            data.addRaid(player.getUUID(), player.getName().getString());
        }
        RaidEventsAPI.fireRaidEnter(player);
    }

    /** Вызвать при успешном выходе через дверь. */
    public static void onRaidSuccessExit(ServerPlayer player, long entryTimeMs) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        RaidStatsData data = getStatsData(server);
        if (data != null) {
            long timeSpent = System.currentTimeMillis() - entryTimeMs;
            data.addSuccessExit(player.getUUID(), player.getName().getString(), timeSpent);
        }
        RaidEventsAPI.fireRaidExit(player);
    }

    /** Вызвать при смерти в рейде (время истекло или убит). */
    public static void onRaidDeath(ServerPlayer player, long entryTimeMs) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        RaidStatsData data = getStatsData(server);
        if (data != null) {
            long timeSpent = System.currentTimeMillis() - entryTimeMs;
            data.addDeath(player.getUUID(), player.getName().getString());
            data.addTimeSpent(player.getUUID(), player.getName().getString(), timeSpent);
        }
    }

    /** Вызвать при убийстве игрока в рейде. */
    public static void onRaidKill(ServerPlayer killer, ServerPlayer victim) {
        MinecraftServer server = killer.getServer();
        if (server == null) return;
        RaidStatsData data = getStatsData(server);
        if (data != null) {
            data.addKill(killer.getUUID(), killer.getName().getString());
        }
        RaidEventsAPI.fireRaidKill(killer, victim);
    }

    private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof ServerPlayer victim)) return;

        MinecraftServer server = victim.getServer();
        if (server == null) return;

        boolean victimInRaid = RaidTimer.isInRaid(victim.getUUID());

        if (victimInRaid) {
            long entryTime = RaidTimer.getEntryTime(victim.getUUID());
            if (entryTime > 0) {
                onRaidDeath(victim, entryTime);
            }
        }

        var killerEntity = damageSource.getEntity();
        if (killerEntity instanceof ServerPlayer killer && victimInRaid && RaidTimer.isInRaid(killer.getUUID())) {
            onRaidKill(killer, victim);
        }

        if (victimInRaid) {
            RaidTimer.clearPersistentState(victim);
        }
    }

    public static RaidStatsData getStatsData(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return null;
        return overworld.getChunkSource().getDataStorage().computeIfAbsent(
                RaidStatsData::load,
                RaidStatsData::new,
                RaidStatsData.getDataId()
        );
    }
}
