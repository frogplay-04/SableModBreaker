package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class WeldSavedData extends SavedData {
    private static final String KEY = "welded_positions";
    private final Set<Long> positions = new HashSet<>();

    public static final Factory<WeldSavedData> FACTORY = new Factory<>(
            WeldSavedData::new,
            WeldSavedData::load,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
    );

    public static WeldSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WeldSavedData data = new WeldSavedData();
        if (tag.contains(KEY, Tag.TAG_LONG_ARRAY)) {
            for (long value : tag.getLongArray(KEY)) data.positions.add(value);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] values = new long[positions.size()];
        int index = 0;
        for (long value : positions) values[index++] = value;
        tag.putLongArray(KEY, values);
        return tag;
    }

    public Set<Long> positions() {
        return positions;
    }
}
