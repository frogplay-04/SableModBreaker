
package com.FrogPlay.sablemobbreaker.util;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Predicate;

/**
 * Performs raycasts against the local block storage of Sable sub-levels.
 *
 * Works even when the ray origin is already inside the sub-level bounds.
 */
public final class SubLevelRaycast {

    private static final double SAMPLE_STEP = 0.025D;

    public record Hit(
            SubLevel subLevel,
            BlockPos localPos,
            BlockState state,
            Vec3 worldLocation,
            double distance
    ) {}

    private SubLevelRaycast() {
    }

    public static Hit trace(
            Level level,
            Vec3 worldStart,
            Vec3 worldDirection,
            double maxDistance,
            Predicate<BlockState> predicate
    ) {
        if (level == null
                || worldStart == null
                || worldDirection == null
                || predicate == null
                || maxDistance <= 0.0D
                || worldDirection.lengthSqr() < 1.0E-9D) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        Vec3 direction = worldDirection.normalize();
        Vec3 worldEnd = worldStart.add(direction.scale(maxDistance));

        Hit closest = null;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }

            Vec3 localStart = subLevel.logicalPose()
                    .transformPositionInverse(worldStart);

            Vec3 localEnd = subLevel.logicalPose()
                    .transformPositionInverse(worldEnd);

            BlockPos previousPos = null;

            for (double distance = 0.0D;
                 distance <= maxDistance;
                 distance += SAMPLE_STEP) {

                if (closest != null && distance >= closest.distance()) {
                    break;
                }

                Vec3 worldSample = worldStart.add(
                        direction.scale(distance)
                );

                Vec3 localSample = subLevel.logicalPose()
                        .transformPositionInverse(worldSample);

                BlockPos localPos = BlockPos.containing(
                        localSample.x,
                        localSample.y,
                        localSample.z
                );

                if (localPos.equals(previousPos)) {
                    continue;
                }

                previousPos = localPos;

                if (localPos.getY() < level.getMinBuildHeight()
                        || localPos.getY() >= level.getMaxBuildHeight()) {
                    continue;
                }

                ChunkPos chunkPos = new ChunkPos(
                        localPos.getX() >> 4,
                        localPos.getZ() >> 4
                );

                if (!container.inBounds(chunkPos)) {
                    continue;
                }

                LevelChunk chunk = container.getChunk(chunkPos);

                if (chunk == null) {
                    continue;
                }

                BlockState state = chunk.getBlockState(localPos);

                if (state.isAir() || !predicate.test(state)) {
                    continue;
                }

                VoxelShape shape = state.getShape(level, localPos);

                BlockHitResult shapeHit = shape.clip(
                        localStart,
                        localEnd,
                        localPos
                );

                if (shapeHit == null) {
                    VoxelShape collisionShape = state.getCollisionShape(
                            level,
                            localPos
                    );

                    shapeHit = collisionShape.clip(
                            localStart,
                            localEnd,
                            localPos
                    );
                }

                if (shapeHit == null) {
                    continue;
                }

                Vec3 worldHit = subLevel.logicalPose()
                        .transformPosition(shapeHit.getLocation());

                double hitDistance = worldStart.distanceTo(worldHit);

                if (hitDistance > maxDistance) {
                    continue;
                }

                if (closest == null
                        || hitDistance < closest.distance()) {

                    closest = new Hit(
                            subLevel,
                            localPos.immutable(),
                            state,
                            worldHit,
                            hitDistance
                    );
                }

                break;
            }
        }

        return closest;
    }
}
