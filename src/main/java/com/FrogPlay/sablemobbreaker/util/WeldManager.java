package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.BlockPos;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WeldManager {
    private static final Set<BlockPos> WELDED_BLOCKS = ConcurrentHashMap.newKeySet();

    public static void weld(BlockPos pos) {
        WELDED_BLOCKS.add(pos.immutable());
    }

    public static void unweld(BlockPos pos) {
        WELDED_BLOCKS.remove(pos.immutable());
    }

    public static boolean isWelded(BlockPos pos) {
        return WELDED_BLOCKS.contains(pos.immutable());
    }
}