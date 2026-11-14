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

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof Level level)) return;

        BlockState oldState = event.getBlockSnapshot().getState();
        BlockState newState = event.getPlacedBlock();

        if (!oldState.is(ModBlocks.BROKEN_BLOCK.get())) return;

        BlockPos worldPos = event.getPos();
        SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) return;

        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl.isRemoved()) continue;
            UUID id = sl.getUniqueId();
            if (id == null) continue;

            Vec3 local = sl.logicalPose().transformPositionInverse(Vec3.atCenterOf(worldPos));
            BlockPos localPos = BlockPos.containing(local.x, local.y, local.z);

            if (local.distanceToSqr(Vec3.atCenterOf(localPos)) < 2.0) {
                ChunkPos cp = new ChunkPos(localPos.getX() >> 4, localPos.getZ() >> 4);
                PlotChunkHolder holder = container.getChunkHolder(cp);
                LevelChunk chunk = container.getChunk(cp);

                event.setCanceled(true);

                if (holder != null && chunk != null) {
                    try {
                        holder.handleBlockChange(
                                localPos.getX(), localPos.getY(), localPos.getZ(),
                                oldState, newState
                        );

                        int sectionIndex = chunk.getSectionIndex(localPos.getY());
                        LevelChunkSection section = chunk.getSection(sectionIndex);
                        if (section != null) {
                            section.setBlockState(
                                    localPos.getX() & 15,
                                    localPos.getY() & 15,
                                    localPos.getZ() & 15,
                                    newState
                            );
                            chunk.setUnsaved(true);
                        }

                        BlockHealthManager.removeDestroyedBlock(id, localPos.getX(), localPos.getY(), localPos.getZ());
                        BlockHealthManager.reset(id, localPos.getX(), localPos.getY(), localPos.getZ());

                        if (level instanceof ServerLevel serverLevel) {
                            ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(worldPos, newState);
                            for (ServerPlayer player : serverLevel.players()) {
                                if (player.distanceToSqr(Vec3.atCenterOf(worldPos)) < 4096) {
                                    player.connection.send(packet);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        SableMobBreaker.LOGGER.error("[SMB] Ошибка установки блока на место сломанного: {}", t.toString());
                    }
                }
                return;
            }
        }
    }
}