package com.hanset.raidmod.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Статистика рейдов по игрокам. Хранится в мире (overworld).
 * raids, successExits, totalTimeMs, kills, deaths, lastKnownName
 */
public class RaidStatsData extends SavedData {

    private static final String ENTRIES_KEY = "entries";
    private static final String UUID_KEY = "uuid";
    private static final String NAME_KEY = "name";
    private static final String RAIDS_KEY = "raids";
    private static final String SUCCESS_KEY = "success";
    private static final String TIME_KEY = "time";
    private static final String KILLS_KEY = "kills";
    private static final String DEATHS_KEY = "deaths";
    private static final String DATA_ID = "raidmod_stats";

    private final Map<UUID, PlayerStats> stats = new HashMap<>();

    public RaidStatsData() {
        super();
    }

    public static RaidStatsData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        RaidStatsData data = new RaidStatsData();
        ListTag list = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString(UUID_KEY));
                String name = entry.getString(NAME_KEY);
                int raids = entry.getInt(RAIDS_KEY);
                int success = entry.getInt(SUCCESS_KEY);
                long time = entry.getLong(TIME_KEY);
                int kills = entry.getInt(KILLS_KEY);
                int deaths = entry.getInt(DEATHS_KEY);
                data.stats.put(uuid, new PlayerStats(name, raids, success, time, kills, deaths));
            } catch (Exception ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(UUID_KEY, e.getKey().toString());
            entry.putString(NAME_KEY, e.getValue().name);
            entry.putInt(RAIDS_KEY, e.getValue().raids);
            entry.putInt(SUCCESS_KEY, e.getValue().successExits);
            entry.putLong(TIME_KEY, e.getValue().totalTimeMs);
            entry.putInt(KILLS_KEY, e.getValue().kills);
            entry.putInt(DEATHS_KEY, e.getValue().deaths);
            list.add(entry);
        }
        tag.put(ENTRIES_KEY, list);
        return tag;
    }

    public PlayerStats getOrCreate(UUID uuid, String name) {
        PlayerStats s = stats.get(uuid);
        if (s == null) {
            s = new PlayerStats(name != null ? name : "?", 0, 0, 0, 0, 0);
            stats.put(uuid, s);
            setDirty();
        } else if (name != null && !name.equals(s.name)) {
            s = new PlayerStats(name, s.raids, s.successExits, s.totalTimeMs, s.kills, s.deaths);
            stats.put(uuid, s);
            setDirty();
        }
        return s;
    }

    public PlayerStats get(UUID uuid) {
        return stats.get(uuid);
    }

    public void addRaid(UUID uuid, String name) {
        PlayerStats s = getOrCreate(uuid, name);
        stats.put(uuid, new PlayerStats(s.name, s.raids + 1, s.successExits, s.totalTimeMs, s.kills, s.deaths));
        setDirty();
    }

    public void addSuccessExit(UUID uuid, String name, long timeSpentMs) {
        PlayerStats s = getOrCreate(uuid, name);
        stats.put(uuid, new PlayerStats(s.name, s.raids, s.successExits + 1, s.totalTimeMs + timeSpentMs, s.kills, s.deaths));
        setDirty();
    }

    public void addDeath(UUID uuid, String name) {
        PlayerStats s = getOrCreate(uuid, name);
        stats.put(uuid, new PlayerStats(s.name, s.raids, s.successExits, s.totalTimeMs, s.kills, s.deaths + 1));
        setDirty();
    }

    public void addKill(UUID uuid, String name) {
        PlayerStats s = getOrCreate(uuid, name);
        stats.put(uuid, new PlayerStats(s.name, s.raids, s.successExits, s.totalTimeMs, s.kills + 1, s.deaths));
        setDirty();
    }

    public void addTimeSpent(UUID uuid, String name, long timeMs) {
        PlayerStats s = getOrCreate(uuid, name);
        stats.put(uuid, new PlayerStats(s.name, s.raids, s.successExits, s.totalTimeMs + timeMs, s.kills, s.deaths));
        setDirty();
    }

    /** Все записи для топа. */
    public Map<UUID, PlayerStats> getAll() {
        return new HashMap<>(stats);
    }

    public void clearAll() {
        stats.clear();
        setDirty();
    }

    public boolean clear(UUID uuid) {
        if (stats.remove(uuid) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean clearByName(String name) {
        UUID toRemove = null;
        for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
            if (e.getValue().name != null && e.getValue().name.equalsIgnoreCase(name)) {
                toRemove = e.getKey();
                break;
            }
        }
        if (toRemove != null) {
            stats.remove(toRemove);
            setDirty();
            return true;
        }
        return false;
    }

    public static String getDataId() {
        return DATA_ID;
    }

    public record PlayerStats(String name, int raids, int successExits, long totalTimeMs, int kills, int deaths) {}
}
