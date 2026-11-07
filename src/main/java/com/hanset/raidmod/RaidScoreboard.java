package com.hanset.raidmod;

import com.hanset.raidmod.command.RaidCommands;
import com.hanset.raidmod.config.RaidConfig;
import com.hanset.raidmod.data.RaidDoorData;
import com.hanset.raidmod.data.RaidVariableExitsData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scoreboard с информацией о выходах. Отображается только у игроков в экспедиции.
 * Использует отдельный Scoreboard и отправляет пакеты только expedition-игрокам.
 */
public class RaidScoreboard {

    private static final String OBJECTIVE_ID = "raidmod_exits";
    private static final net.minecraft.world.scores.DisplaySlot SIDEBAR_SLOT = net.minecraft.world.scores.DisplaySlot.SIDEBAR;
    private static final int MAX_LINES = 14;
    private static final int MAX_HOLDER_LENGTH = 60;

    private static final Map<UUID, Set<String>> sentHolders = new HashMap<>();
    private static final Set<UUID> objectiveVisible = new HashSet<>();

    public static void tick(MinecraftServer server) {
        RaidConfig config = RaidConfig.get();
        if (!config.scoreboard_enabled) {
            clearAll(server);
            return;
        }
        if (server.getTickCount() % config.scoreboard_update_interval_ticks != 0) return;

        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        List<ServerPlayer> raidPlayers = server.getPlayerList().getPlayers().stream()
                .filter(p -> p.level().dimension() == Level.NETHER && p.isAlive() && RaidTimer.isInRaid(p.getUUID()))
                .toList();

        Set<UUID> currentRaidUuids = new HashSet<>();
        for (ServerPlayer p : raidPlayers) {
            currentRaidUuids.add(p.getUUID());
        }

        for (UUID uuid : new HashSet<>(objectiveVisible)) {
            if (!currentRaidUuids.contains(uuid)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    clearScoreboardForPlayer(server, player);
                } else {
                    objectiveVisible.remove(uuid);
                    sentHolders.remove(uuid);
                }
            }
        }

        if (raidPlayers.isEmpty()) {
            return;
        }

        RaidDoorData doorData = RaidCommands.getRaidDoorData(nether);
        RaidVariableExitsData varData = RaidCommands.getRaidVariableExitsData(nether);
        List<RaidDoorData.DoorEntry> activeDoors = buildActiveExits(doorData, varData);

        // Используем отдельный Scoreboard — не трогаем серверный
        Scoreboard scoreboard = new Scoreboard();
        Objective oldObj = scoreboard.getObjective(OBJECTIVE_ID);
        if (oldObj != null) {
            scoreboard.removeObjective(oldObj);
        }

        Objective objective = scoreboard.addObjective(
                OBJECTIVE_ID,
                ObjectiveCriteria.DUMMY,
                com.hanset.raidmod.config.MessagesConfig.msg("scoreboard.title"),
                ObjectiveCriteria.RenderType.INTEGER, false, null
        );

        List<ScoreLine> lines = new ArrayList<>();

        if (activeDoors.isEmpty()) {
            addEntry(scoreboard, objective, lines,
                    com.hanset.raidmod.config.MessagesConfig.get().get("scoreboard.no_active"), 10);
        } else {
            int score = 10;
            Set<String> usedNames = new HashSet<>();
            for (int i = 0; i < activeDoors.size() && i < MAX_LINES; i++) {
                RaidDoorData.DoorEntry door = activeDoors.get(i);
                String typeStr = com.hanset.raidmod.config.MessagesConfig.get()
                        .get(door.requiredPlayers() >= 2 ? "scoreboard.type_paired" : "scoreboard.type_single");
                String staticMark = door.isStatic()
                        ? com.hanset.raidmod.config.MessagesConfig.get().get("scoreboard.static_suffix")
                        : "";
                String name = sanitize(door.name());
                String line = com.hanset.raidmod.config.MessagesConfig.get()
                        .get("scoreboard.exit_line", name, typeStr, staticMark);
                if (usedNames.contains(line)) {
                    line = line + " §8(" + (i + 1) + ")";
                } else {
                    usedNames.add(line);
                }
                if (line.length() > MAX_HOLDER_LENGTH) {
                    line = line.substring(0, MAX_HOLDER_LENGTH - 3) + "§7...";
                }
                addEntry(scoreboard, objective, lines, line, score--);
            }
            long setTime = varData.getSetTimeMs();
            int intervalSec = RaidConfig.get().variable_exits_interval_seconds;
            long elapsedSec = setTime > 0 ? (System.currentTimeMillis() - setTime) / 1000 : 0;
            int remainingSec = Math.max(0, intervalSec - (int) elapsedSec);
            int min = remainingSec / 60;
            int sec = remainingSec % 60;
            String formattedTime = min + ":" + String.format("%02d", sec);
            String timerLine = com.hanset.raidmod.config.MessagesConfig.get()
                    .get("scoreboard.rotation_timer", formattedTime);
            addEntry(scoreboard, objective, lines, timerLine, score);
        }

        for (ServerPlayer player : raidPlayers) {
            sendScoreboardToPlayer(player, objective, lines);
        }
    }

    private static void sendScoreboardToPlayer(ServerPlayer player, Objective objective, List<ScoreLine> lines) {
        UUID playerId = player.getUUID();
        Set<String> currentHolders = new HashSet<>();
        for (ScoreLine line : lines) currentHolders.add(line.holder());

        Set<String> previous = sentHolders.getOrDefault(playerId, Set.of());
        for (String oldHolder : previous) {
            if (!currentHolders.contains(oldHolder)) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundResetScorePacket(oldHolder, OBJECTIVE_ID));
            }
        }

        if (objectiveVisible.add(playerId)) {
            player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(SIDEBAR_SLOT, objective));
        } else {
            player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_CHANGE));
        }

        for (ScoreLine line : lines) {
            player.connection.send(new ClientboundSetScorePacket(
                    line.holder(),
                    OBJECTIVE_ID,
                    line.score(), java.util.Optional.empty(), java.util.Optional.empty()
            ));
        }
        sentHolders.put(playerId, currentHolders);
    }

    private static void clearScoreboardForPlayer(MinecraftServer server, ServerPlayer player) {
        Scoreboard dummyBoard = new Scoreboard();
        Objective dummyObjective = new Objective(
                dummyBoard,
                OBJECTIVE_ID,
                ObjectiveCriteria.DUMMY,
                net.minecraft.network.chat.Component.literal(""),
                ObjectiveCriteria.RenderType.INTEGER, false, null
        );
        player.connection.send(new ClientboundSetObjectivePacket(dummyObjective, ClientboundSetObjectivePacket.METHOD_REMOVE));
        Objective serverSidebar = server.getScoreboard().getDisplayObjective(SIDEBAR_SLOT);
        player.connection.send(new ClientboundSetDisplayObjectivePacket(SIDEBAR_SLOT, serverSidebar));
        objectiveVisible.remove(player.getUUID());
        sentHolders.remove(player.getUUID());
    }

    private static void clearAll(MinecraftServer server) {
        for (UUID uuid : new HashSet<>(objectiveVisible)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) clearScoreboardForPlayer(server, player);
        }
        objectiveVisible.clear();
        sentHolders.clear();
    }

    private static void addEntry(Scoreboard scoreboard, Objective objective, List<ScoreLine> lines,
                                 String holder, int scoreValue) {

        lines.add(new ScoreLine(holder, scoreValue));
    }

    private static List<RaidDoorData.DoorEntry> buildActiveExits(RaidDoorData doorData, RaidVariableExitsData varData) {
        List<RaidDoorData.DoorEntry> result = new java.util.ArrayList<>();
        doorData.getStaticDoor().ifPresent(result::add);
        for (BlockPos pos : varData.getVariableExits()) {
            doorData.getDoorAt(pos).ifPresent(result::add);
        }
        return result;
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "?";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c != 0x200B && c != 0x200C && c != 0x200D && c != 0x2060 && c != 0xFEFF) {
                sb.append(c);
            }
        }
        return sb.length() > 0 ? sb.toString() : "?";
    }

    private record ScoreLine(String holder, int score) {}
}
