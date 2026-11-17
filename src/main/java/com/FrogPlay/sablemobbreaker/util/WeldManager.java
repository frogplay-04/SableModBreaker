package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Set;
public final class WeldManager {
    private static final String DATA_NAME = "sablemobbreaker_welded_blocks";
    private WeldManager() {}

    private static WeldSavedData data(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getDataStorage().computeIfAbsent(WeldSavedData.FACTORY, DATA_NAME);
    }

    public static void weld(Level level, Set<BlockPos> blocks) {
        WeldSavedData data = data(level);
        if (data == null) return;
        for (BlockPos pos : blocks) data.positions().add(pos.asLong());
        data.setDirty();
    }

    public static void unweld(Level level, Set<BlockPos> blocks) {
        WeldSavedData data = data(level);
        if (data == null) return;
        for (BlockPos pos : blocks) data.positions().remove(pos.asLong());
        data.setDirty();
    }

    public static boolean isWelded(Level level, BlockPos pos) {
        WeldSavedData data = data(level);
        if (data == null || !data.positions().contains(pos.asLong())) return false;
        if (!WeldGeometry.isEligible(level.getBlockState(pos))) {
            data.positions().remove(pos.asLong());
            data.setDirty();
            return false;
        }
        return true;
    }
}
