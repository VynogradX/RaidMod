package com.hanset.raidmod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Два переменных выхода, меняющихся каждые N минут.
 */
public class RaidVariableExitsData extends SavedData {

    private static final String POS1_KEY = "pos1";
    private static final String POS2_KEY = "pos2";
    private static final String POSITIONS_KEY = "positions";
    private static final String SET_TIME_KEY = "setTime";
    private static final String DATA_ID = "raidmod_variable_exits";

    private static final Random RANDOM = new Random();

    private final List<BlockPos> positions = new ArrayList<>();
    private long setTimeMs;

    public RaidVariableExitsData() {
        super();
    }

    public static RaidVariableExitsData load(CompoundTag tag) {
        RaidVariableExitsData data = new RaidVariableExitsData();
        if (tag.contains(POSITIONS_KEY, Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList(POSITIONS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.positions.add(NbtUtils.readBlockPos(list.getCompound(i)));
            }
        } else {
            // Миграция старого формата с двумя фиксированными выходами.
            if (tag.contains(POS1_KEY)) {
                data.positions.add(NbtUtils.readBlockPos(tag.getCompound(POS1_KEY)));
            }
            if (tag.contains(POS2_KEY)) {
                data.positions.add(NbtUtils.readBlockPos(tag.getCompound(POS2_KEY)));
            }
        }
        data.setTimeMs = tag.getLong(SET_TIME_KEY);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (BlockPos position : positions) {
            list.add(NbtUtils.writeBlockPos(position));
        }
        tag.put(POSITIONS_KEY, list);
        tag.putLong(SET_TIME_KEY, setTimeMs);
        return tag;
    }

    public List<BlockPos> getVariableExits() {
        return new ArrayList<>(positions);
    }

    public long getSetTimeMs() {
        return setTimeMs;
    }

    public void setVariableExits(List<RaidDoorData.DoorEntry> exits) {
        positions.clear();
        if (!exits.isEmpty()) {
            List<RaidDoorData.DoorEntry> shuffled = new ArrayList<>(exits);
            Collections.shuffle(shuffled, RANDOM);
            int count = Math.min(
                    com.hanset.raidmod.config.RaidConfig.get().available_variable_exits,
                    shuffled.size()
            );
            for (int i = 0; i < count; i++) {
                positions.add(shuffled.get(i).pos());
            }
        }
        setTimeMs = System.currentTimeMillis();
        setDirty();
    }

    public static String getDataId() {
        return DATA_ID;
    }
}
