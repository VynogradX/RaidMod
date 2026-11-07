package com.hanset.raidmod.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сохраняемые данные об игроках в рейде (для восстановления после рестарта).
 * UUID -> entryTime (ms).
 */
public class RaidPlayerData extends SavedData {

    private static final String PLAYERS_KEY = "players";
    private static final String COOLDOWNS_KEY = "cooldowns";
    private static final String UUID_KEY = "uuid";
    private static final String ENTRY_KEY = "entry";
    private static final String DATA_ID = "raidmod_raid_players";

    private final Map<UUID, Long> playersInRaid = new HashMap<>();
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();

    public RaidPlayerData() {
        super();
    }

    public static RaidPlayerData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        RaidPlayerData data = new RaidPlayerData();
        ListTag list = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString(UUID_KEY));
                long entryTime = entry.getLong(ENTRY_KEY);
                data.playersInRaid.put(uuid, entryTime);
            } catch (Exception ignored) {
            }
        }
        ListTag cooldowns = tag.getList(COOLDOWNS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag entry = cooldowns.getCompound(i);
            try {
                data.playerCooldowns.put(
                        UUID.fromString(entry.getString(UUID_KEY)),
                        entry.getLong(ENTRY_KEY)
                );
            } catch (Exception ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> e : playersInRaid.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(UUID_KEY, e.getKey().toString());
            entry.putLong(ENTRY_KEY, e.getValue());
            list.add(entry);
        }
        tag.put(PLAYERS_KEY, list);
        ListTag cooldowns = new ListTag();
        for (Map.Entry<UUID, Long> e : playerCooldowns.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(UUID_KEY, e.getKey().toString());
            entry.putLong(ENTRY_KEY, e.getValue());
            cooldowns.add(entry);
        }
        tag.put(COOLDOWNS_KEY, cooldowns);
        return tag;
    }

    public void setPlayerInRaid(UUID uuid, long entryTime) {
        playersInRaid.put(uuid, entryTime);
        setDirty();
    }

    public void removePlayer(UUID uuid) {
        if (playersInRaid.remove(uuid) != null) {
            setDirty();
        }
    }

    public void setAll(Map<UUID, Long> map) {
        playersInRaid.clear();
        playersInRaid.putAll(map);
        setDirty();
    }

    public Map<UUID, Long> getPlayersInRaid() {
        return new HashMap<>(playersInRaid);
    }

    public boolean isInRaid(UUID uuid) {
        return playersInRaid.containsKey(uuid);
    }

    public Long getEntryTime(UUID uuid) {
        return playersInRaid.get(uuid);
    }

    public Long getLastRaidTime(UUID uuid) {
        return playerCooldowns.get(uuid);
    }

    public void setLastRaidTime(UUID uuid, long timestamp) {
        playerCooldowns.put(uuid, timestamp);
        setDirty();
    }

    public void removeLastRaidTime(UUID uuid) {
        if (playerCooldowns.remove(uuid) != null) setDirty();
    }

    public static String getDataId() {
        return DATA_ID;
    }
}
