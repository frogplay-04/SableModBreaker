package com.FrogPlay.sablemobbreaker.util;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class WeldManager {
    private static final String DATA_NAME = "sablemobbreaker_welded_blocks";
    private WeldManager() {}

    private static WeldSavedData data(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getDataStorage().computeIfAbsent(WeldSavedData.FACTORY, DATA_NAME);
    }

    private static String key(SubLevel subLevel, BlockPos pos) {
        return (subLevel == null ? "world" : "sublevel:" + subLevel.getUniqueId()) + ":" + pos.asLong();
    }

    public static void weld(Level level, Set<BlockPos> blocks) {
        weld(level, null, blocks);
    }

    public static void weld(Level level, SubLevel subLevel, Set<BlockPos> blocks) {
        WeldSavedData data = data(level);
        if (data == null) return;
        for (BlockPos pos : blocks) data.positions().add(key(subLevel, pos));
        data.setDirty();
    }

    public static void unweld(Level level, Set<BlockPos> blocks) {
        unweld(level, null, blocks);
    }

    public static void unweld(Level level, SubLevel subLevel, Set<BlockPos> blocks) {
        WeldSavedData data = data(level);
        if (data == null) return;
        for (BlockPos pos : blocks) data.positions().remove(key(subLevel, pos));
        data.setDirty();
    }

    public static boolean isWelded(Level level, BlockPos pos) {
        return isWelded(level, null, pos, level.getBlockState(pos));
    }

    public static boolean isWelded(Level level, BlockPos pos, BlockState state) {
        return isWelded(level, null, pos, state);
    }

    public static boolean isWelded(Level level, SubLevel subLevel, BlockPos pos, BlockState state) {
        WeldSavedData data = data(level);
        if (data == null) return false;
        String key = key(subLevel, pos);
        if (!data.positions().contains(key)) return false;
        if (!WeldGeometry.isEligible(state)) {
            data.positions().remove(key);
            data.setDirty();
            return false;
        }
        return true;
    }
}
