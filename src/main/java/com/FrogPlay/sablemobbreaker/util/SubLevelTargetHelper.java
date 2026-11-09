package com.FrogPlay.sablemobbreaker.util;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class SubLevelTargetHelper {

    public record Found(SubLevel subLevel, BlockPos localPos) {}

    /** Находит ближайший целый блок в радиусе от указанной мировой позиции. */
    public static Optional<Found> findNearestBlock(ServerLevel level, Vec3 worldPos, double radius) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return Optional.empty();

        var searchBox = new BoundingBox3d(
                worldPos.x - radius, worldPos.y - radius, worldPos.z - radius,
                worldPos.x + radius, worldPos.y + radius, worldPos.z + radius);

        SubLevel nearestSubLevel = null;
        BlockPos nearestLocalPos = null;
        double nearestDistSqr = radius * radius;

        for (SubLevel subLevel : container.queryIntersecting(searchBox)) {
            if (subLevel.isRemoved()) continue;

            for (var holder : subLevel.getPlot().getLoadedChunks()) {
                var chunk = holder.getChunk();
                if (chunk == null) continue;

                int minY = chunk.getMinBuildHeight();
                int maxY = chunk.getMaxBuildHeight();
                ChunkPos cp = chunk.getPos();

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int ly = minY; ly < maxY; ly++) {
                            BlockPos localPos = new BlockPos(cp.getMinBlockX() + lx, ly, cp.getMinBlockZ() + lz);
                            var state = chunk.getBlockState(localPos);
                            if (state.isAir()) continue;

                            Vec3 blockWorldPos = localToWorld(subLevel, localPos);
                            double distSqr = worldPos.distanceToSqr(blockWorldPos);

                            if (distSqr < nearestDistSqr) {
                                nearestSubLevel = subLevel;
                                nearestLocalPos = localPos;
                                nearestDistSqr = distSqr;
                            }
                        }
                    }
                }
            }
        }

        return nearestSubLevel != null ? Optional.of(new Found(nearestSubLevel, nearestLocalPos)) : Optional.empty();
    }

    /** Переводит локальные координаты блока в мировые. */
    public static Vec3 localToWorld(SubLevel subLevel, BlockPos localPos) {
        Vec3 local = new Vec3(localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5);
        return subLevel.logicalPose().transformPosition(local);
    }

    /** Проверяет, есть ли прямая видимость от глаз моба до целевой позиции. */
    public static boolean hasLineOfSight(net.minecraft.world.entity.Mob mob, Vec3 targetPos) {
        Vec3 eyePos = mob.getEyePosition();
        var clipContext = new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mob);
        var result = mob.level().clip(clipContext);
        if (result.getType() == HitResult.Type.MISS) return true;
        double distToHit = eyePos.distanceToSqr(result.getLocation());
        double distToTarget = eyePos.distanceToSqr(targetPos);
        return distToHit >= distToTarget - 1.0;
    }
}