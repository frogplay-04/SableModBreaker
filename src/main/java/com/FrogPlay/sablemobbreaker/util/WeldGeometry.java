package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/** Finds a connected door/trapdoor assembly made from the same block type. */
public final class WeldGeometry {
    public static final int MAX_BLOCKS = 1024;
    private WeldGeometry() {}

    public static boolean isEligible(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return !state.is(BlockTags.WOODEN_DOORS);
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return !state.is(BlockTags.WOODEN_TRAPDOORS);
        }
        return false;
    }

    public static Set<BlockPos> findAssembly(Level level, BlockPos start) {
        BlockPos origin = start.immutable();
        BlockState first = level.getBlockState(origin);
        if (!isEligible(first)) return Set.of();

        Set<BlockPos> found = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < MAX_BLOCKS) {
            BlockPos pos = queue.removeFirst();
            if (!found.add(pos)) continue;
            for (BlockPos direction : DIRECTIONS) {
                BlockPos next = pos.offset(direction);
                if (found.contains(next)) continue;
                BlockState candidate = level.getBlockState(next);
                if (candidate.getBlock() == first.getBlock() && isEligible(candidate)) {
                    queue.addLast(next.immutable());
                }
            }
        }
        return Set.copyOf(found);
    }

    private static final BlockPos[] DIRECTIONS = {
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
    };

    public static AABB bounds(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) return new AABB(0, 0, 0, 0, 0, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1); maxY = Math.max(maxY, pos.getY() + 1); maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.0125);
    }
}
