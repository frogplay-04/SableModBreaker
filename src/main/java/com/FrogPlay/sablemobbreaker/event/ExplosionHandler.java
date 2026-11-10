package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class ExplosionHandler {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        if (affectedBlocks.isEmpty()) return;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            if (SubLevelDamageHelper.isRagdoll(subLevel)) continue;

            BoundingBox3dc box = subLevel.boundingBox();

            List<BlockPos> toRemove = new java.util.ArrayList<>();

            for (BlockPos pos : affectedBlocks) {
                Vec3 center = Vec3.atCenterOf(pos);
                if (!isInside(box, center)) continue;

                Vec3 localPos = subLevel.logicalPose().transformPositionInverse(center);
                BlockPos plotPos = BlockPos.containing(localPos.x, localPos.y, localPos.z);

                if (!isBlockInSubLevel(level, subLevel, plotPos)) continue;

                float explosionPower = event.getExplosion().radius();
                int damage = Math.round(explosionPower * 10.0F);
                SubLevelDamageHelper.damageBlock(
                        level, subLevel, center, damage);

                toRemove.add(pos);
            }

            affectedBlocks.removeAll(toRemove);
        }
    }

    private static boolean isInside(BoundingBox3dc box, Vec3 pos) {
        return pos.x >= box.minX() && pos.x <= box.maxX()
                && pos.y >= box.minY() && pos.y <= box.maxY()
                && pos.z >= box.minZ() && pos.z <= box.maxZ();
    }

    private static boolean isBlockInSubLevel(Level level, SubLevel subLevel, BlockPos plotPos) {
        try {
            var container = SubLevelContainer.getContainer(level);
            if (container == null) return false;
            var cp = new net.minecraft.world.level.ChunkPos(
                    plotPos.getX() >> 4, plotPos.getZ() >> 4);
            if (!container.inBounds(cp)) return false;
            var chunk = container.getChunk(cp);
            if (chunk == null) return false;
            BlockState state = chunk.getBlockState(plotPos);
            return !state.isAir();
        } catch (Throwable t) {
            return false;
        }
    }
}