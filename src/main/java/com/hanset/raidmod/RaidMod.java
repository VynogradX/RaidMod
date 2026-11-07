package com.hanset.raidmod;
import com.hanset.raidmod.command.RaidCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Mod(RaidMod.MOD_ID)
public class RaidMod {
    public static final String MOD_ID = "raidmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public RaidMod() {
        RaidStatsTracker.register();
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> RaidCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            var server = event.getServer();
            RaidVariableExits.tick(server);
            RaidExitCountdown.tick(server);
            RaidTimer.tick(server);
            RaidScoreboard.tick(server);
            NetherNametagHider.tick(server);
            RaidSpawnCooldown.tick(server);
            RaidConfirmation.tick(server);
            RaidPersistence.tick(server);
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.NETHER)
                RaidVariableExits.initialize(level.getServer(), level);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> RaidPersistence.onServerStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) RaidPersistence.onPlayerJoin(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RaidPersistence.onPlayerDisconnect(player);
                NetherNametagHider.onPlayerDisconnect(player);
            }
        });
    }
}
