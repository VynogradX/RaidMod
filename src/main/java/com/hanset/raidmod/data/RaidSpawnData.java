package com.hanset.raidmod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сохраняемые данные о точках спавна игроков в аду (с названиями).
 */
public class RaidSpawnData extends SavedData {
    private static final String SPAWNS_KEY = "spawns";
    private static final String NAME_KEY = "name";
    private static final String POS_KEY = "pos";
    private static final String COOLDOWNS_KEY = "cooldowns";
    private static final String UNTIL_KEY = "until";
    private static final String PROCESSED_KEY = "processed";
    private static final String DATA_ID = "raidmod_spawns";

    private final List<SpawnEntry> spawns = new ArrayList<>();
    private final Map<String, CooldownEntry> cooldowns = new HashMap<>();

    public RaidSpawnData() {
        super();
    }

    public static RaidSpawnData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        RaidSpawnData data = new RaidSpawnData();
        ListTag spawnsList = tag.getList(SPAWNS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < spawnsList.size(); i++) {
            CompoundTag entryTag = spawnsList.getCompound(i);
            try {
                String name = entryTag.getString(NAME_KEY);
                BlockPos pos = readPosition(entryTag, POS_KEY);
                data.spawns.add(new SpawnEntry(name, pos));
            } catch (Exception ignored) {
            }
        }
        ListTag cooldownList = tag.getList(COOLDOWNS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldownList.size(); i++) {
            CompoundTag entryTag = cooldownList.getCompound(i);
            String name = entryTag.getString(NAME_KEY);
            if (!name.isBlank()) {
                data.cooldowns.put(name, new CooldownEntry(
                        entryTag.getLong(UNTIL_KEY),
                        entryTag.getLong(PROCESSED_KEY)
                ));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag spawnsList = new ListTag();
        for (SpawnEntry entry : spawns) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(NAME_KEY, entry.name);
            entryTag.put(POS_KEY, NbtUtils.writeBlockPos(entry.pos));
            spawnsList.add(entryTag);
        }
        tag.put(SPAWNS_KEY, spawnsList);
        ListTag cooldownList = new ListTag();
        for (Map.Entry<String, CooldownEntry> entry : cooldowns.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(NAME_KEY, entry.getKey());
            entryTag.putLong(UNTIL_KEY, entry.getValue().until());
            entryTag.putLong(PROCESSED_KEY, entry.getValue().lastProcessed());
            cooldownList.add(entryTag);
        }
        tag.put(COOLDOWNS_KEY, cooldownList);
        return tag;
    }

    public boolean addSpawn(String name, BlockPos pos) {
        if (getByName(name).isPresent()) {
            return false;
        }
        spawns.add(new SpawnEntry(name, pos));
        setDirty();
        return true;
    }

    public boolean removeSpawn(String name) {
        boolean removed = spawns.removeIf(e -> e.name.equals(name));
        if (removed) {
            cooldowns.remove(name);
            setDirty();
        }
        return removed;
    }

    public void clear() {
        spawns.clear();
        cooldowns.clear();
        setDirty();
    }

    public List<SpawnEntry> getSpawns() {
        return new ArrayList<>(spawns);
    }

    public Optional<SpawnEntry> getByName(String name) {
        return spawns.stream().filter(e -> e.name.equals(name)).findFirst();
    }

    public List<String> getSpawnNames() {
        return spawns.stream().map(e -> e.name).toList();
    }

    public boolean isEmpty() {
        return spawns.isEmpty();
    }

    public Map<String, CooldownEntry> getCooldowns() {
        return new HashMap<>(cooldowns);
    }

    public CooldownEntry getCooldown(String name) {
        return cooldowns.get(name);
    }

    public void putCooldown(String name, long until, long lastProcessed) {
        cooldowns.put(name, new CooldownEntry(until, lastProcessed));
        setDirty();
    }

    public void removeCooldown(String name) {
        if (cooldowns.remove(name) != null) setDirty();
    }

    public void clearCooldowns() {
        if (!cooldowns.isEmpty()) {
            cooldowns.clear();
            setDirty();
        }
    }

    public static String getDataId() {
        return DATA_ID;
    }

    public record SpawnEntry(String name, BlockPos pos) {}

    public record CooldownEntry(long until, long lastProcessed) {}
    private static BlockPos readPosition(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_COMPOUND)) {
            CompoundTag pos = tag.getCompound(key);
            return new BlockPos(pos.getInt("X"), pos.getInt("Y"), pos.getInt("Z"));
        }
        return NbtUtils.readBlockPos(tag, key).orElseThrow();
    }
}
