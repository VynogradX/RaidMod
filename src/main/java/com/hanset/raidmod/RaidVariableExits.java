package com.hanset.raidmod;

import com.hanset.raidmod.command.RaidCommands;
import com.hanset.raidmod.config.RaidConfig;
import com.hanset.raidmod.data.RaidDoorData;
import com.hanset.raidmod.data.RaidVariableExitsData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Тик-логика смены переменных выходов каждые N минут.
 */
public class RaidVariableExits {

    /** Вызывается при загрузке измерения Nether или по команде — инициализирует/обновляет переменные выходы. */
    public static void initialize(MinecraftServer server, ServerLevel nether) {
        initialize(server, nether, false);
    }

    /** @param forceRefresh если true — всегда обновить, даже если данные валидны */
    public static void initialize(MinecraftServer server, ServerLevel nether, boolean forceRefresh) {
        RaidDoorData doorData = RaidCommands.getRaidDoorData(nether);
        RaidVariableExitsData varData = RaidCommands.getRaidVariableExitsData(nether);
        List<RaidDoorData.DoorEntry> nonStatic = doorData.getNonStaticDoors();
        int expectedCount = Math.min(RaidConfig.get().available_variable_exits, nonStatic.size());
        boolean needInit = forceRefresh
                || varData.getVariableExits().size() != expectedCount
                || hasStalePositions(doorData, varData);
        if (needInit) {
            varData.setVariableExits(nonStatic);
        }
    }

    private static boolean hasStalePositions(RaidDoorData doorData, RaidVariableExitsData varData) {
        java.util.Set<net.minecraft.core.BlockPos> seen = new java.util.HashSet<>();
        for (net.minecraft.core.BlockPos pos : varData.getVariableExits()) {
            RaidDoorData.DoorEntry door = doorData.getDoorAt(pos).orElse(null);
            if (door == null || door.isStatic() || !seen.add(door.pos())) return true;
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        RaidDoorData doorData = RaidCommands.getRaidDoorData(nether);
        RaidVariableExitsData varData = RaidCommands.getRaidVariableExitsData(nether);
        int intervalSec = RaidConfig.get().variable_exits_interval_seconds;

        long setTime = varData.getSetTimeMs();
        long now = System.currentTimeMillis();
        long elapsedSec = (now - setTime) / 1000;

        // Первый запуск, данные пустые или устаревшие — инициализируем
        List<RaidDoorData.DoorEntry> nonStatic = doorData.getNonStaticDoors();
        int expectedCount = Math.min(RaidConfig.get().available_variable_exits, nonStatic.size());
        if (setTime == 0 || varData.getVariableExits().size() != expectedCount
                || hasStalePositions(doorData, varData)) {
            varData.setVariableExits(nonStatic);
            return;
        }

        // Проверяем, пора ли менять
        if (elapsedSec >= intervalSec && !nonStatic.isEmpty()) {
            varData.setVariableExits(nonStatic);
        }
    }
}
