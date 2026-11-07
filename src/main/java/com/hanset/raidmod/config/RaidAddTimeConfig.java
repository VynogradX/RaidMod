package com.hanset.raidmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Дополнительное время рейда по игрокам. Файл: config/raidmod_addtime.json
 * UUID -> дополнительные секунды (суммируются при повторном addtime).
 */
public class RaidAddTimeConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static RaidAddTimeConfig INSTANCE;

    /** UUID -> дополнительные секунды к каждому рейду. */
    public Map<String, Integer> player_extra_seconds = new HashMap<>();

    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("raidmod_addtime.json");
    }

    public static RaidAddTimeConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static RaidAddTimeConfig load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            RaidAddTimeConfig config = null;
            try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, RaidAddTimeConfig.class);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("raidmod").error("Failed to read RaidMod extra-time config {}", path, e);
            }
            if (config != null && config.player_extra_seconds != null) {
                config.save();
                return config;
            }
        }
        RaidAddTimeConfig config = new RaidAddTimeConfig();
        config.save();
        return config;
    }

    public void save() {
        Path path = getConfigPath();
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temp), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to save RaidAddTime config", e);
        }
    }

    /** Дополнительные секунды для игрока. */
    public int getExtraSeconds(UUID playerId) {
        Integer v = player_extra_seconds.get(playerId.toString());
        return v != null ? Math.max(0, v) : 0;
    }

    /** Добавить секунды (суммируется). */
    public void addExtraSeconds(UUID playerId, int seconds) {
        int current = getExtraSeconds(playerId);
        player_extra_seconds.put(playerId.toString(), current + seconds);
        setDirty();
    }

    public boolean clearExtraSeconds(UUID playerId) {
        boolean removed = player_extra_seconds.remove(playerId.toString()) != null;
        if (removed) setDirty();
        return removed;
    }

    private void setDirty() {
        save();
    }

    public static void reload() {
        INSTANCE = load();
    }
}
