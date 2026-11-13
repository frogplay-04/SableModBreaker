package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.block.ModBlocks;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class BlockPlaceHandler {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getPlacedBlock().is(ModBlocks.BROKEN_BLOCK.get())) return;

        BlockState oldState = event.getBlockSnapshot().getState();
        if (oldState.is(ModBlocks.BROKEN_BLOCK.get())) {
            Level level = (Level) event.getLevel();
            BlockPos worldPos = event.getPos();

            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                for (SubLevel sl : container.getAllSubLevels()) {
                    if (sl.isRemoved()) continue;
                    UUID id = sl.getUniqueId();
                    if (id == null) continue;

                    Vec3 local = sl.logicalPose().transformPositionInverse(Vec3.atCenterOf(worldPos));
                    BlockPos localPos = BlockPos.containing(local.x, local.y, local.z);

                    if (local.distanceToSqr(Vec3.atCenterOf(localPos)) < 2.0) {
                        BlockHealthManager.removeDestroyedBlock(id, localPos.getX(), localPos.getY(), localPos.getZ());
                        BlockHealthManager.reset(id, localPos.getX(), localPos.getY(), localPos.getZ());

                        SableMobBreaker.LOGGER.debug("[SMB] Игрок поставил блок на место сломанного. Данные удалены.");
                        break;
                    }
                }
            }
        }
    }
}