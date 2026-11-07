package com.hanset.raidmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Конфиг мода. Файл: config/raidmod.json
 */
public class RaidConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static RaidConfig INSTANCE;

    /** Время нахождения в экспедиции (секунды). По истечении — смерть. */
    public int raid_time_seconds = 60;

    /** Кулдаун на команду /expedition (секунды). 0 = без кулдауна. Не действует на админов (permission 3+) и UUID из raid_cooldown_bypass_uuids. */
    public int raid_cooldown_seconds = 0;

    /** Кулдаун точки спавна после использования (секунды). По умолчанию 5 минут. */
    public int spawn_cooldown_seconds = 300;

    /** Интервал смены переменных выходов (секунды). По умолчанию 30 минут. */
    public int variable_exits_interval_seconds = 1800;

    /** Количество одновременно доступных переменных выходов. */
    public int available_variable_exits = 2;

    /** Время ожидания подтверждения экспедиции. */
    public int confirmation_timeout_seconds = 30;

    /** Максимальное время отсутствия игрока в активной экспедиции. */
    public int offline_timeout_seconds = 3600;

    /** Радиус, в котором игроки приостанавливают кулдаун точки появления. */
    public int spawn_cooldown_pause_radius = 15;

    /** Включить sidebar со списком выходов. */
    public boolean scoreboard_enabled = true;

    /** Включить bossbar оставшегося времени. */
    public boolean bossbar_enabled = true;

    /** Включить частицы у выходов. */
    public boolean exit_particles_enabled = true;

    /** Интервал обновления sidebar в тиках. */
    public int scoreboard_update_interval_ticks = 20;

    /** UUID игроков, которые обходят кулдаун (спец. пермишен). */
    public List<String> raid_cooldown_bypass_uuids = new ArrayList<>();

    /** Открыты ли экспедиции для всех. false = только админы (OP 2) могут использовать /expedition. */
    public boolean raids_open = true;

    /** Скрывать ники игроков в аду (через scoreboard team). Работает без клиентского мода. */
    public boolean hide_nether_nametags = true;

    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("raidmod.json");
    }

    public static RaidConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static RaidConfig load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            RaidConfig config = null;
            try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, RaidConfig.class);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("raidmod").error("Failed to read RaidMod config {}", path, e);
            }
            if (config != null) {
                config.validate();
                config.save();
                return config;
            }
        }
        RaidConfig config = new RaidConfig();
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
            moveAtomically(temp, path);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to save RaidMod config", e);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void setRaidTimeSeconds(int seconds) {
        this.raid_time_seconds = Math.max(1, seconds);
        save();
    }

    public void setRaidCooldownSeconds(int seconds) {
        this.raid_cooldown_seconds = Math.max(0, seconds);
        save();
    }

    public void setSpawnCooldownSeconds(int seconds) {
        this.spawn_cooldown_seconds = Math.max(1, seconds);
        save();
    }

    public void setVariableExitsIntervalSeconds(int seconds) {
        this.variable_exits_interval_seconds = Math.max(60, seconds);
        save();
    }

    public void setAvailableVariableExits(int count) {
        this.available_variable_exits = Math.max(1, Math.min(14, count));
        save();
    }

    public void addRaidCooldownBypass(UUID playerId) {
        if (raid_cooldown_bypass_uuids == null) {
            raid_cooldown_bypass_uuids = new ArrayList<>();
        }
        String id = playerId.toString();
        boolean exists = raid_cooldown_bypass_uuids.stream()
                .anyMatch(u -> u != null && u.equalsIgnoreCase(id));
        if (!exists) {
            raid_cooldown_bypass_uuids.add(id);
            save();
        }
    }

    public boolean removeRaidCooldownBypass(UUID playerId) {
        if (raid_cooldown_bypass_uuids == null) return false;
        boolean removed = raid_cooldown_bypass_uuids.removeIf(
                value -> value != null && value.equalsIgnoreCase(playerId.toString()));
        if (removed) save();
        return removed;
    }

    public void setRaidsOpen(boolean open) {
        this.raids_open = open;
        save();
    }

    public void setHideNetherNametags(boolean hide) {
        this.hide_nether_nametags = hide;
        save();
    }

    private void validate() {
        if (raid_time_seconds < 1) raid_time_seconds = 60;
        if (raid_cooldown_seconds < 0) raid_cooldown_seconds = 0;
        if (spawn_cooldown_seconds < 1) spawn_cooldown_seconds = 300;
        if (variable_exits_interval_seconds < 60) variable_exits_interval_seconds = 1800;
        if (available_variable_exits < 1) available_variable_exits = 1;
        if (available_variable_exits > 14) available_variable_exits = 14;
        if (confirmation_timeout_seconds < 5) confirmation_timeout_seconds = 30;
        if (offline_timeout_seconds < 0) offline_timeout_seconds = 3600;
        if (spawn_cooldown_pause_radius < 0) spawn_cooldown_pause_radius = 15;
        if (scoreboard_update_interval_ticks < 1) scoreboard_update_interval_ticks = 20;
        if (raid_cooldown_bypass_uuids == null) raid_cooldown_bypass_uuids = new ArrayList<>();
    }

    public boolean canBypassCooldown(UUID playerId) {
        if (raid_cooldown_bypass_uuids == null) return false;
        String id = playerId.toString();
        return raid_cooldown_bypass_uuids.stream().anyMatch(u -> u != null && u.equalsIgnoreCase(id));
    }

    /** Перезагружает конфиг с диска и обновляет INSTANCE. */
    public static RaidConfig reload() {
        INSTANCE = load();
        return INSTANCE;
    }
}
