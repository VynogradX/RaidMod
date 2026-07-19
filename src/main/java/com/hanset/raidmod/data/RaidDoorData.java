package com.hanset.raidmod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сохраняемые данные о выходных дверях в аду.
 * Каждая дверь: радиус активации, время выхода, требуется ли 2 игрока.
 */
public class RaidDoorData extends SavedData {
    private static final String DOORS_KEY = "doors";
    private static final String POS_KEY = "pos";
    private static final String NAME_KEY = "name";
    private static final String RADIUS_KEY = "radius";
    private static final String EXIT_TIME_KEY = "exitTime";
    private static final String REQUIRED_PLAYERS_KEY = "requiredPlayers";
    private static final String STATIC_KEY = "static";
    private static final String DATA_ID = "raidmod_doors";

    private final List<DoorEntry> doors = new ArrayList<>();

    public RaidDoorData() {
        super();
    }

    public static RaidDoorData load(CompoundTag tag) {
        RaidDoorData data = new RaidDoorData();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        boolean hasStaticDoor = false;
        ListTag doorsList = tag.getList(DOORS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < doorsList.size(); i++) {
            CompoundTag entryTag = doorsList.getCompound(i);
            try {
                BlockPos pos = NbtUtils.readBlockPos(entryTag.getCompound(POS_KEY));
                if (seen.contains(pos) || seen.contains(pos.above()) || seen.contains(pos.below())) continue;
                seen.add(pos);
                seen.add(pos.above());
                seen.add(pos.below());
                String name = sanitizeName(entryTag.contains(NAME_KEY) ? entryTag.getString(NAME_KEY) : "Выход");
                int radius = entryTag.getInt(RADIUS_KEY);
                int exitTime = entryTag.getInt(EXIT_TIME_KEY);
                int requiredPlayers = entryTag.contains(REQUIRED_PLAYERS_KEY) ? entryTag.getInt(REQUIRED_PLAYERS_KEY) : 1;
                boolean isStatic = entryTag.getBoolean(STATIC_KEY) && !hasStaticDoor;
                if (isStatic) hasStaticDoor = true;
                if (radius <= 0) radius = 4;
                if (exitTime <= 0) exitTime = 10;
                if (name == null || name.isBlank()) name = "Выход";
                data.doors.add(new DoorEntry(pos, name, radius, exitTime, requiredPlayers, isStatic));
            } catch (Exception ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag doorsList = new ListTag();
        for (DoorEntry entry : doors) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put(POS_KEY, NbtUtils.writeBlockPos(entry.pos));
            entryTag.putString(NAME_KEY, entry.name);
            entryTag.putInt(RADIUS_KEY, entry.radius);
            entryTag.putInt(EXIT_TIME_KEY, entry.exitTime);
            entryTag.putInt(REQUIRED_PLAYERS_KEY, entry.requiredPlayers);
            entryTag.putBoolean(STATIC_KEY, entry.isStatic);
            doorsList.add(entryTag);
        }
        tag.put(DOORS_KEY, doorsList);
        return tag;
    }

    public boolean addDoor(BlockPos pos, String name, int radius, int exitTime, int requiredPlayers) {
        return addDoor(pos, name, radius, exitTime, requiredPlayers, false);
    }

    public boolean addDoor(BlockPos pos, String name, int radius, int exitTime, int requiredPlayers, boolean isStatic) {
        if (getDoorAt(pos).isPresent()) return false;
        String safeName = sanitizeName(name == null || name.isBlank() ? "Выход" : name);
        if (isStatic) {
            for (int i = 0; i < doors.size(); i++) {
                DoorEntry e = doors.get(i);
                if (e.isStatic) {
                    doors.set(i, new DoorEntry(e.pos, e.name, e.radius, e.exitTime, e.requiredPlayers, false));
                }
            }
        }
        doors.add(new DoorEntry(pos, safeName, radius, exitTime, requiredPlayers, isStatic));
        setDirty();
        return true;
    }

    public boolean setDoorStatic(BlockPos pos, boolean isStatic) {
        boolean found = false;
        boolean changed = false;
        for (int i = 0; i < doors.size(); i++) {
            DoorEntry e = doors.get(i);
            boolean matches = e.pos.equals(pos) || e.pos.above().equals(pos) || e.pos.below().equals(pos);
            if (matches) found = true;

            // При назначении новой статичной двери прежняя автоматически становится переменной.
            boolean newStatic = isStatic ? matches : (matches ? false : e.isStatic);
            if (newStatic != e.isStatic) {
                doors.set(i, new DoorEntry(e.pos, e.name, e.radius, e.exitTime, e.requiredPlayers, newStatic));
                changed = true;
            }
        }
        if (changed) setDirty();
        return found;
    }

    public boolean removeDoor(BlockPos pos) {
        boolean removed = doors.removeIf(d -> d.pos.equals(pos) || d.pos.above().equals(pos) || d.pos.below().equals(pos));
        if (removed) setDirty();
        return removed;
    }

    public Optional<DoorEntry> getDoorAt(BlockPos pos) {
        return doors.stream()
                .filter(d -> d.pos.equals(pos) || d.pos.above().equals(pos) || d.pos.below().equals(pos))
                .findFirst();
    }

    public Optional<DoorEntry> getStaticDoor() {
        return getDoors().stream().filter(d -> d.isStatic).findFirst();
    }

    /** Нестатичные двери (для случайного выбора). */
    public List<DoorEntry> getNonStaticDoors() {
        return getDoors().stream().filter(d -> !d.isStatic).toList();
    }

    /** Возвращает уникальные двери (без дубликатов по позиции). */
    public List<DoorEntry> getDoors() {
        List<DoorEntry> result = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        for (DoorEntry e : doors) {
            BlockPos key = e.pos;
            if (seen.contains(key) || seen.contains(key.above()) || seen.contains(key.below())) continue;
            seen.add(key);
            seen.add(key.above());
            seen.add(key.below());
            result.add(e);
        }
        return result;
    }

    private static String sanitizeName(String s) {
        if (s == null || s.isEmpty()) return "Выход";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c != 0x200B && c != 0x200C && c != 0x200D && c != 0x2060 && c != 0xFEFF) {
                sb.append(c);
            }
        }
        return sb.length() > 0 ? sb.toString() : "Выход";
    }

    public int clear() {
        int count = doors.size();
        doors.clear();
        if (count > 0) setDirty();
        return count;
    }

    public static String getDataId() {
        return DATA_ID;
    }

    public record DoorEntry(BlockPos pos, String name, int radius, int exitTime, int requiredPlayers, boolean isStatic) {
        public DoorEntry(BlockPos pos, String name, int radius, int exitTime, int requiredPlayers) {
            this(pos, name, radius, exitTime, requiredPlayers, false);
        }
    }
}
