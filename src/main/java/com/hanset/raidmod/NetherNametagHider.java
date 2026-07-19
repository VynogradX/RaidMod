package com.hanset.raidmod;

import com.hanset.raidmod.config.RaidConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Скрывает ники игроков в аду через scoreboard team (nametagVisibility NEVER).
 * Работает полностью на сервере — клиентский мод не нужен.
 */
public class NetherNametagHider {

    private static final String TEAM_ID = "raidmod_hide";
    private static final Set<UUID> playersInHideTeam = new HashSet<>();

    public static void tick(MinecraftServer server) {
        if (!RaidConfig.get().hide_nether_nametags) {
            removeAllFromTeam(server);
            return;
        }

        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = getOrCreateTeam(scoreboard);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getName().getString();
            boolean inNetherRaid = player.level().dimension() == Level.NETHER
                    && RaidTimer.isInRaid(player.getUUID());
            boolean inTeam = team.getPlayers().contains(name);

            if (inNetherRaid && !inTeam) {
                PlayerTeam existingTeam = scoreboard.getPlayersTeam(name);
                // Не разрушаем команды, префиксы и настройки других модов.
                if (existingTeam == null) {
                    scoreboard.addPlayerToTeam(name, team);
                    playersInHideTeam.add(player.getUUID());
                }
            } else if (!inNetherRaid && inTeam) {
                scoreboard.removePlayerFromTeam(name, team);
                playersInHideTeam.remove(player.getUUID());
            }
        }
    }

    private static PlayerTeam getOrCreateTeam(Scoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_ID);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_ID);
            team.setDisplayName(net.minecraft.network.chat.Component.literal(""));
            team.setNameTagVisibility(Team.Visibility.NEVER);
        }
        return team;
    }

    private static void removeAllFromTeam(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_ID);
        if (team != null) {
            for (String name : team.getPlayers().stream().toList()) {
                scoreboard.removePlayerFromTeam(name, team);
            }
            playersInHideTeam.clear();
        }
    }

    /** При отключении игрока — убрать из team (на случай если он был в аду). */
    public static void onPlayerDisconnect(ServerPlayer player) {
        Scoreboard scoreboard = player.getServer().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_ID);
        if (team != null && team.getPlayers().contains(player.getName().getString())) {
            scoreboard.removePlayerFromTeam(player.getName().getString(), team);
        }
        playersInHideTeam.remove(player.getUUID());
    }
}
