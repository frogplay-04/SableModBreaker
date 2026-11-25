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

import java.util.ArrayList;
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
            if (subLevel.isRemoved() || SubLevelDamageHelper.isRagdoll(subLevel)) continue;
            BoundingBox3dc box = subLevel.boundingBox();
            List<BlockPos> handled = new ArrayList<>();

            for (BlockPos pos : affectedBlocks) {
                Vec3 worldCenter = Vec3.atCenterOf(pos);
                if (!isInside(box, worldCenter)) continue;

                Vec3 local = subLevel.logicalPose().transformPositionInverse(worldCenter);
                BlockPos localPos = BlockPos.containing(local.x, local.y, local.z);
                if (!isBlockInSubLevel(level, localPos)) continue;

                int damage = Math.max(1, Math.round(event.getExplosion().radius() * 10.0F));
                if (SubLevelDamageHelper.damageBlockExact(level, subLevel, worldCenter, damage)) {
                    handled.add(pos);
                }
            }
            affectedBlocks.removeAll(handled);
        }
    }

    private static boolean isInside(BoundingBox3dc box, Vec3 pos) {
        return pos.x >= box.minX() && pos.x <= box.maxX()
                && pos.y >= box.minY() && pos.y <= box.maxY()
                && pos.z >= box.minZ() && pos.z <= box.maxZ();
    }

    private static boolean isBlockInSubLevel(Level level, BlockPos localPos) {
        try {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return false;
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(
                    localPos.getX() >> 4, localPos.getZ() >> 4);
            if (!container.inBounds(cp)) return false;
            var chunk = container.getChunk(cp);
            if (chunk == null) return false;
            BlockState state = chunk.getBlockState(localPos);
            return !state.isAir();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
