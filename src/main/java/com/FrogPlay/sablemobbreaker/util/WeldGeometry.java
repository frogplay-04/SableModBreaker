package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
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
    public static Vec3[][] visibleFaceEdges(AABB b, Vec3 eye) {
        double sx = b.getXsize();
        double sy = b.getYsize();
        double sz = b.getZsize();
        int axis = sx <= sy && sx <= sz ? 0 : (sy <= sx && sy <= sz ? 1 : 2);

        double cx = (b.minX + b.maxX) * 0.5D;
        double cy = (b.minY + b.maxY) * 0.5D;
        double cz = (b.minZ + b.maxZ) * 0.5D;

        if (axis == 0) {
            double x = eye.x >= cx ? b.maxX : b.minX;
            Vec3 a = new Vec3(x, b.minY, b.minZ);
            Vec3 c = new Vec3(x, b.minY, b.maxZ);
            Vec3 d = new Vec3(x, b.maxY, b.maxZ);
            Vec3 e = new Vec3(x, b.maxY, b.minZ);
            return new Vec3[][]{{a,c},{c,d},{d,e},{e,a}};
        } else if (axis == 1) {
            double y = eye.y >= cy ? b.maxY : b.minY;
            Vec3 a = new Vec3(b.minX, y, b.minZ);
            Vec3 c = new Vec3(b.minX, y, b.maxZ);
            Vec3 d = new Vec3(b.maxX, y, b.maxZ);
            Vec3 e = new Vec3(b.maxX, y, b.minZ);
            return new Vec3[][]{{a,c},{c,d},{d,e},{e,a}};
        } else {
            double z = eye.z >= cz ? b.maxZ : b.minZ;
            Vec3 a = new Vec3(b.minX, b.minY, z);
            Vec3 c = new Vec3(b.maxX, b.minY, z);
            Vec3 d = new Vec3(b.maxX, b.maxY, z);
            Vec3 e = new Vec3(b.minX, b.maxY, z);
            return new Vec3[][]{{a,c},{c,d},{d,e},{e,a}};
        }
    }

    public static AABB bounds(Level level, Set<BlockPos> blocks) {
        if (blocks.isEmpty()) return new AABB(0, 0, 0, 0, 0, 0);

        AABB result = null;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            AABB local = state.getShape(level, pos).bounds();
            if (local.getXsize() <= 0.0D || local.getYsize() <= 0.0D || local.getZsize() <= 0.0D) {
                continue;
            }
            AABB world = local.move(pos);
            result = result == null ? world : result.minmax(world);
        }

        // Fallback only for an unusual block whose outline shape is empty.
        if (result == null) {
            BlockPos first = blocks.iterator().next();
            result = new AABB(first);
        }
        return result.inflate(0.002D);
    }
}
