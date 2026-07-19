package com.hanset.raidmod;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * API для интеграции с другими модами (например, Battle Pass).
 * Вызывается при событиях рейда.
 */
public class RaidEventsAPI {
    private static final List<Consumer<ServerPlayer>> raidEnterCallbacks = new ArrayList<>();
    private static final List<BiConsumer<ServerPlayer, ServerPlayer>> raidKillCallbacks = new ArrayList<>();
    private static final List<Consumer<ServerPlayer>> raidExitCallbacks = new ArrayList<>();

    public static void onRaidEnter(Consumer<ServerPlayer> callback) {
        raidEnterCallbacks.add(callback);
    }

    public static void onRaidKill(BiConsumer<ServerPlayer, ServerPlayer> callback) {
        raidKillCallbacks.add(callback);
    }

    public static void onRaidExit(Consumer<ServerPlayer> callback) {
        raidExitCallbacks.add(callback);
    }

    static void fireRaidEnter(ServerPlayer player) {
        for (var cb : raidEnterCallbacks) {
            try {
                cb.accept(player);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("raidmod").error("Raid enter callback failed", e);
            }
        }
    }

    static void fireRaidKill(ServerPlayer killer, ServerPlayer victim) {
        for (var cb : raidKillCallbacks) {
            try {
                cb.accept(killer, victim);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("raidmod").error("Raid kill callback failed", e);
            }
        }
    }

    static void fireRaidExit(ServerPlayer player) {
        for (var cb : raidExitCallbacks) {
            try {
                cb.accept(player);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("raidmod").error("Raid exit callback failed", e);
            }
        }
    }
}
