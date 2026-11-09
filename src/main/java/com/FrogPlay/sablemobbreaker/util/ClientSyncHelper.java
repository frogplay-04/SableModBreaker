package com.FrogPlay.sablemobbreaker.util;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

public class ClientSyncHelper {

    public static void forceUpdate(
            Level level,
            SubLevel subLevel,
            PlotChunkHolder holder,
            LevelChunk chunk,
            BlockPos plotPos,
            BlockPos blockInChunk,
            BlockState oldState,
            BlockState newState,
            Vec3 worldPos
    ) {
        String tag = "[SMB SYNC]";
        SableMobBreaker.LOGGER.info("{} START pos={} state={}", tag, plotPos, newState);

        // 1. Sable API
        try {
            holder.handleBlockChange(blockInChunk.getX(), blockInChunk.getY(), blockInChunk.getZ(), oldState, newState);
            SableMobBreaker.LOGGER.info("{} method=holder.handleBlockChange OK", tag);
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("{} method=holder.handleBlockChange FAIL {}", tag, t.toString());
        }

        // 2. Прямая запись в чанк
        try {
            chunk.setBlockState(blockInChunk, newState, false);
            SableMobBreaker.LOGGER.info("{} method=chunk.setBlockState OK", tag);
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("{} method=chunk.setBlockState FAIL {}", tag, t.toString());
        }

        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 worldCenter = subLevel.logicalPose().transformPosition(
                new Vec3(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5)
        );
        BlockPos worldBlockPos = BlockPos.containing(worldCenter);

        // 3. sendBlockUpdated
        try {
            serverLevel.sendBlockUpdated(worldBlockPos, oldState, newState, 3);
            SableMobBreaker.LOGGER.info("{} method=serverLevel.sendBlockUpdated OK", tag);
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("{} method=serverLevel.sendBlockUpdated FAIL {}", tag, t.toString());
        }

        // 4. blockChanged
        try {
            serverLevel.getChunkSource().blockChanged(worldBlockPos);
            SableMobBreaker.LOGGER.info("{} method=serverLevel.getChunkSource().blockChanged OK", tag);
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("{} method=blockChanged FAIL {}", tag, t.toString());
        }

        // 5. Прямой setBlock (опасно, но ты просил все методы)
        try {
            serverLevel.setBlock(worldBlockPos, newState, 3);
            SableMobBreaker.LOGGER.info("{} method=serverLevel.setBlock OK", tag);
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("{} method=serverLevel.setBlock FAIL {}", tag, t.toString());
        }

        // 6. Пакеты
        try {
            ClientboundBlockUpdatePacket worldPacket = new ClientboundBlockUpdatePacket(worldBlockPos, newState);
            ClientboundBlockUpdatePacket plotPacket = new ClientboundBlockUpdatePacket(plotPos, newState);

            SectionPos plotSectionPos = SectionPos.of(plotPos);
            short plotRel = SectionPos.sectionRelativePos(plotPos);
            ShortSet plotSet = new ShortOpenHashSet();
            plotSet.add(plotRel);
            LevelChunkSection plotSection = chunk.getSection(chunk.getSectionIndex(plotPos.getY()));
            ClientboundSectionBlocksUpdatePacket plotSectionPacket =
                    new ClientboundSectionBlocksUpdatePacket(plotSectionPos, plotSet, plotSection);

            SectionPos worldSectionPos = SectionPos.of(worldBlockPos);
            short worldRel = SectionPos.sectionRelativePos(worldBlockPos);
            ShortSet worldSet = new ShortOpenHashSet();
            worldSet.add(worldRel);
            LevelChunkSection worldSection = chunk.getSection(chunk.getSectionIndex(plotPos.getY()));
            ClientboundSectionBlocksUpdatePacket worldSectionPacket =
                    new ClientboundSectionBlocksUpdatePacket(worldSectionPos, worldSet, worldSection);

            ClientboundLevelChunkWithLightPacket chunkPacket =
                    new ClientboundLevelChunkWithLightPacket(chunk, serverLevel.getLightEngine(), null, null);

            for (ServerPlayer p : serverLevel.players()) {
                if (p.distanceToSqr(worldPos.x, worldPos.y, worldPos.z) >= 4096) continue;

                p.connection.send(worldPacket);
                p.connection.send(plotPacket);
                p.connection.send(plotSectionPacket);
                p.connection.send(worldSectionPacket);
                p.connection.send(chunkPacket);

                for (Direction dir : Direction.values()) {
                    BlockPos nWorld = worldBlockPos.relative(dir);
                    BlockPos nPlot = plotPos.relative(dir);
                    BlockState nState = chunk.getBlockState(nPlot);
                    p.connection.send(new ClientboundBlockUpdatePacket(nWorld, nState));
                    p.connection.send(new ClientboundBlockUpdatePacket(nPlot, nState));
                }
            }
            SableMobBreaker.LOGGER.info("{} method=packets OK", tag);
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("{} method=packets FAIL {}", tag, t.toString());
        }

        SableMobBreaker.LOGGER.info("{} END pos={}", tag, plotPos);
    }
}