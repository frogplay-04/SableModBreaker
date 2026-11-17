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
        if (state.getBlock() instanceof DoorBlock) return !state.is(BlockTags.WOODEN_DOORS);
        if (state.getBlock() instanceof TrapDoorBlock) return !state.is(BlockTags.WOODEN_TRAPDOORS);
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
                if (candidate.getBlock() == first.getBlock() && isEligible(candidate)) queue.addLast(next.immutable());
            }
        }
        return Set.copyOf(found);
    }

    private static final BlockPos[] DIRECTIONS = {
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
    };
    public static AABB bounds(Level level, Set<BlockPos> blocks) {
        AABB result = null;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            if (!isEligible(state)) continue;
            AABB local = state.getShape(level, pos).bounds();
            if (local.getXsize() < 1.0E-5 || local.getYsize() < 1.0E-5 || local.getZsize() < 1.0E-5) continue;
            AABB world = local.move(pos);
            result = result == null ? world : result.minmax(world);
        }
        return result == null ? new AABB(0, 0, 0, 0, 0, 0) : result.inflate(0.004);
    }
    public static Vec3[][] visibleContour(AABB b, Vec3 viewer) {
        Vec3 center = b.getCenter();
        double dx = viewer.x - center.x, dy = viewer.y - center.y, dz = viewer.z - center.z;
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        int normalAxis = ax >= ay && ax >= az ? 0 : (ay >= ax && ay >= az ? 1 : 2);
        if (normalAxis == 0) {
            double x = dx >= 0 ? b.maxX : b.minX;
            Vec3 a = new Vec3(x, b.minY, b.minZ), c = new Vec3(x, b.maxY, b.minZ);
            Vec3 d = new Vec3(x, b.maxY, b.maxZ), e = new Vec3(x, b.minY, b.maxZ);
            return new Vec3[][]{{a,c},{c,d},{d,e},{e,a}};
        }
        if (normalAxis == 1) {
            double y = dy >= 0 ? b.maxY : b.minY;
            Vec3 a = new Vec3(b.minX,y,b.minZ), c = new Vec3(b.maxX,y,b.minZ);
            Vec3 d = new Vec3(b.maxX,y,b.maxZ), e = new Vec3(b.minX,y,b.maxZ);
            return new Vec3[][]{{a,c},{c,d},{d,e},{e,a}};
        }
        double z = dz >= 0 ? b.maxZ : b.minZ;
        Vec3 a = new Vec3(b.minX,b.minY,z), c = new Vec3(b.maxX,b.minY,z);
        Vec3 d = new Vec3(b.maxX,b.maxY,z), e = new Vec3(b.minX,b.maxY,z);
        return new Vec3[][]{{a,c},{c,d},{d,e},{e,a}};
    }
}
