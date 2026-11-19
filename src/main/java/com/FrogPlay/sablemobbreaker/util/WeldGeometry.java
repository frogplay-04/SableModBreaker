package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;

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
        double sx = b.getXsize(), sy = b.getYsize(), sz = b.getZsize();
        int axis = sx <= sy && sx <= sz ? 0 : (sy <= sx && sy <= sz ? 1 : 2);
        Vec3 center = new Vec3((b.minX + b.maxX) * 0.5, (b.minY + b.maxY) * 0.5,
                (b.minZ + b.maxZ) * 0.5);
        boolean positive = axis == 0 ? eye.x >= center.x : axis == 1 ? eye.y >= center.y : eye.z >= center.z;
        double plane = axis == 0 ? (positive ? b.maxX : b.minX)
                : axis == 1 ? (positive ? b.maxY : b.minY)
                : (positive ? b.maxZ : b.minZ);
        Vec3 a, c, d, e;
        if (axis == 0) {
            a = new Vec3(plane, b.minY, b.minZ); c = new Vec3(plane, b.minY, b.maxZ);
            d = new Vec3(plane, b.maxY, b.maxZ); e = new Vec3(plane, b.maxY, b.minZ);
        } else if (axis == 1) {
            a = new Vec3(b.minX, plane, b.minZ); c = new Vec3(b.minX, plane, b.maxZ);
            d = new Vec3(b.maxX, plane, b.maxZ); e = new Vec3(b.maxX, plane, b.minZ);
        } else {
            a = new Vec3(b.minX, b.minY, plane); c = new Vec3(b.maxX, b.minY, plane);
            d = new Vec3(b.maxX, b.maxY, plane); e = new Vec3(b.minX, b.maxY, plane);
        }
        return new Vec3[][]{{a,c},{c,d},{d,e},{e,a}};
    }

    public static AABB bounds(Level level, Set<BlockPos> blocks) {
        AABB result = null;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos, CollisionContext.empty());
            for (AABB local : shape.toAabbs()) {
                AABB world = local.move(pos);
                result = result == null ? world : result.minmax(world);
            }
        }
        return result == null ? bounds(blocks).inflate(0.001) : result.inflate(0.001);
    }

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
