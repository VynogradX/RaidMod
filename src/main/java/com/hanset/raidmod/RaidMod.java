package com.hanset.raidmod;

import com.hanset.raidmod.command.RaidCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaidMod implements ModInitializer {
	public static final String MOD_ID = "raidmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RaidStatsTracker.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				RaidCommands.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(RaidVariableExits::tick);
		ServerTickEvents.END_SERVER_TICK.register(RaidExitCountdown::tick);
		ServerTickEvents.END_SERVER_TICK.register(RaidTimer::tick);
		ServerTickEvents.END_SERVER_TICK.register(RaidScoreboard::tick);
		ServerTickEvents.END_SERVER_TICK.register(NetherNametagHider::tick);
		ServerTickEvents.END_SERVER_TICK.register(RaidSpawnCooldown::tick);
		ServerTickEvents.END_SERVER_TICK.register(RaidConfirmation::tick);
		ServerTickEvents.END_SERVER_TICK.register(RaidPersistence::tick);

		ServerWorldEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.NETHER) {
				RaidVariableExits.initialize(server, (ServerLevel) level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(RaidPersistence::onServerStopping);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				RaidPersistence.onPlayerJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RaidPersistence.onPlayerDisconnect(handler.player);
			NetherNametagHider.onPlayerDisconnect(handler.player);
		});

		LOGGER.info("RaidMod initialized");
	}
}
