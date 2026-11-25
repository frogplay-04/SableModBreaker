package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.block.ModBlocks;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class BlockPlaceHandler {
    private BlockPlaceHandler() {}

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof Level level)) return;

        BlockState newState = event.getPlacedBlock();
        if (newState.isAir() || newState.is(ModBlocks.BROKEN_BLOCK.get())) return;

        BlockPos worldPos = event.getPos();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;

            Vec3 local = subLevel.logicalPose().transformPositionInverse(Vec3.atCenterOf(worldPos));
            BlockPos localPos = BlockPos.containing(local.x, local.y, local.z);
            if (local.distanceToSqr(Vec3.atCenterOf(localPos)) >= 2.0D) continue;

            ChunkPos cp = new ChunkPos(localPos.getX() >> 4, localPos.getZ() >> 4);
            PlotChunkHolder holder = container.getChunkHolder(cp);
            LevelChunk chunk = container.getChunk(cp);
            if (holder == null || chunk == null) continue;

            BlockState oldState = chunk.getBlockState(localPos);
            if (!oldState.is(ModBlocks.BROKEN_BLOCK.get())) continue;

            event.setCanceled(true);
            try {
                holder.handleBlockChange(localPos.getX(), localPos.getY(), localPos.getZ(), oldState, newState);
                chunk.removeBlockEntity(localPos);

                int sectionIndex = chunk.getSectionIndex(localPos.getY());
                LevelChunkSection[] sections = chunk.getSections();
                if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) {
                    SableMobBreaker.LOGGER.warn("[SMB] Cannot replace broken block at {}: missing chunk section", localPos);
                    return;
                }
                sections[sectionIndex].setBlockState(
                        localPos.getX() & 15, localPos.getY() & 15, localPos.getZ() & 15, newState);

                if (newState.hasBlockEntity() && newState.getBlock() instanceof EntityBlock entityBlock) {
                    BlockEntity replacementEntity = entityBlock.newBlockEntity(localPos, newState);
                    if (replacementEntity != null) chunk.setBlockEntity(replacementEntity);
                }

                chunk.setUnsaved(true);
                UUID subLevelId = subLevel.getUniqueId();
                BlockHealthManager.removeDestroyedBlock(subLevelId, localPos.getX(), localPos.getY(), localPos.getZ());
                BlockHealthManager.reset(subLevelId, localPos.getX(), localPos.getY(), localPos.getZ());

                if (level instanceof ServerLevel serverLevel) {
                    ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(worldPos, newState);
                    for (ServerPlayer player : serverLevel.players()) {
                        if (player.distanceToSqr(Vec3.atCenterOf(worldPos)) < 4096.0D) {
                            player.connection.send(packet);
                        }
                    }
                }
            } catch (Throwable t) {
                SableMobBreaker.LOGGER.error("[SMB] Failed to replace a broken Sable block at {}", localPos, t);
            }
            return;
        }
    }
}
