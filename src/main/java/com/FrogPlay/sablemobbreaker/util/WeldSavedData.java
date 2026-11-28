package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class WeldSavedData extends SavedData {
    private static final String KEY = "welded_positions";
    private final Set<String> positions = new HashSet<>();

    public static final Factory<WeldSavedData> FACTORY = new Factory<>(
            WeldSavedData::new,
            WeldSavedData::load,
            net.minecraft.util.datafix.DataFixTypes.LEVEL
    );

    public static WeldSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WeldSavedData data = new WeldSavedData();
        // Current format stores dimension + Sable sub-level UUID + packed position.
        if (tag.contains(KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) data.positions.add(list.getString(i));
        } else if (tag.contains(KEY, Tag.TAG_LONG_ARRAY)) {
            // Backward compatibility with releases that stored world positions only.
            for (long value : tag.getLongArray(KEY)) data.positions.add("world:" + value);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String value : positions) list.add(net.minecraft.nbt.StringTag.valueOf(value));
        tag.put(KEY, list);
        return tag;
    }

    public Set<String> positions() {
        return positions;
    }
}
