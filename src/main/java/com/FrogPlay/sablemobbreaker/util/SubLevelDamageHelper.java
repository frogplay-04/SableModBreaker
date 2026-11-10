package com.FrogPlay.sablemobbreaker.util;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.block.ModBlocks;
import com.FrogPlay.sablemobbreaker.network.BlockHealthSyncPacket;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class SubLevelDamageHelper {

    private static final String RAGDOLL_NAMESPACE = "sable_player_ragdoll";

    public static boolean damageBlock(Level level, SubLevel subLevel, Vec3 worldPos, int amount) {
        if (level.isClientSide()) return false;
        return damageSingleBlock(level, subLevel, worldPos, amount, false);
    }

    public static boolean damageBlockExact(Level level, SubLevel subLevel, Vec3 worldPos, int amount) {
        if (level.isClientSide()) return false;
        return damageSingleBlock(level, subLevel, worldPos, amount, true);
    }

    private static boolean damageSingleBlock(Level level, SubLevel subLevel, Vec3 worldPos, int amount, boolean exact) {
        try {
            if (subLevel == null || subLevel.isRemoved()) return false;
            if (!(level instanceof ServerLevel serverLevel)) return false;

            Vec3 localPos = subLevel.logicalPose().transformPositionInverse(worldPos);
            BlockPos centerPos = BlockPos.containing(localPos.x, localPos.y, localPos.z);
            int chunkY = centerPos.getY();
            if (chunkY < level.getMinBuildHeight() || chunkY >= level.getMaxBuildHeight()) return false;

            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return false;

            ChunkPos plotGridChunkPos = new ChunkPos(centerPos.getX() >> 4, centerPos.getZ() >> 4);
            if (!container.inBounds(plotGridChunkPos)) return false;

            LevelChunk chunk = container.getChunk(plotGridChunkPos);
            PlotChunkHolder holder = container.getChunkHolder(plotGridChunkPos);
            if (chunk == null || holder == null) return false;

            java.util.UUID id = subLevel.getUniqueId();
            if (id == null) return false;

            BlockPos targetBlock = null;

            if (exact) {
                targetBlock = centerPos;
            } else {
                double closestDist = Double.MAX_VALUE;
                int cx = centerPos.getX() >> 4;
                int cz = centerPos.getZ() >> 4;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos p = centerPos.offset(dx, dy, dz);
                            if ((p.getX() >> 4) != cx || (p.getZ() >> 4) != cz) continue;
                            BlockState state = chunk.getBlockState(p);
                            if (!state.isAir()
                                    && !state.is(ModBlocks.BROKEN_BLOCK.get())
                                    && !isIgnoredBlock(state)) {
                                double dist = localPos.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                                if (dist < closestDist) {
                                    closestDist = dist;
                                    targetBlock = p;
                                }
                            }
                        }
                    }
                }
            }

            if (targetBlock == null) {
                if (exact) BlockHealthManager.reset(id, centerPos.getX(), centerPos.getY(), centerPos.getZ());
                return false;
            }

            BlockState oldState = chunk.getBlockState(targetBlock);
            if (isIgnoredBlock(oldState) || oldState.isAir() || oldState.is(ModBlocks.BROKEN_BLOCK.get())) {
                return false;
            }

            BlockEntity be = chunk.getBlockEntity(targetBlock);
            int oldHp = BlockHealthManager.getHp(id, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), oldState, be);
            int maxHp = BlockHealthManager.getMaxHp(oldState, be);

            if (oldHp <= 0) return false;
            int newHp = BlockHealthManager.damage(id, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), oldState, amount, be);

            if (newHp <= 0) {
                try {
                    if (oldState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                        DoubleBlockHalf half = oldState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? targetBlock.above() : targetBlock.below();
                        BlockState otherState = chunk.getBlockState(otherPos);

                        if (otherState.is(oldState.getBlock())) {
                            damageSingleBlock(level, subLevel, subLevel.logicalPose().transformPosition(
                                    new Vec3(otherPos.getX() + 0.5, otherPos.getY() + 0.5, otherPos.getZ() + 0.5)), 9999, true);
                        }
                    }

                    BlockPos worldBlockPos = BlockPos.containing(
                            subLevel.logicalPose().transformPosition(
                                    new Vec3(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5)));

                    if (!serverLevel.isLoaded(worldBlockPos)) return false;

                    BlockState recheck = chunk.getBlockState(targetBlock);
                    if (recheck != oldState) return false;

                    // Безопасное сохранение NBT
                    CompoundTag savedNbt = null;
                    if (be != null) {
                        try {
                            savedNbt = be.saveWithFullMetadata(level.registryAccess());
                        } catch (Throwable t) {
                            SableMobBreaker.LOGGER.error("[SMB] Failed to save NBT for block at {}: {}", targetBlock, t.toString());
                        }
                    }

                    BlockHealthManager.saveDestroyedBlock(id, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), oldState, savedNbt);

                    try {
                        chunk.removeBlockEntity(targetBlock);
                    } catch (Throwable ignored) {}

                    RecentBreakTracker.record(serverLevel, worldBlockPos);

                    BlockState brokenState = ModBlocks.BROKEN_BLOCK.get().defaultBlockState();

                    // ИСПОЛЬЗУЕМ ТОЛЬКО SABLE API. НИКАКОГО ВМЕШАТЕЛЬСТВА В МАССИВ БЛОКОВ.
                    try {
                        holder.handleBlockChange(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), oldState, brokenState);
                    } catch (Throwable t) {
                        SableMobBreaker.LOGGER.error("[SMB] handleBlockChange failed: {}", t.toString());
                    }

                    BlockHealthManager.setHp(id, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), 0);

                    ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(targetBlock, brokenState);
                    for (ServerPlayer player : serverLevel.players()) {
                        if (player.distanceToSqr(worldBlockPos.getX(), worldBlockPos.getY(), worldBlockPos.getZ()) < 4096) {
                            player.connection.send(packet);
                        }
                    }
                } catch (Throwable t) {
                    SableMobBreaker.LOGGER.error("[SMB] Ошибка удаления: {}", t.toString());
                }
            }

            if (newHp != oldHp) {
                BlockHealthSyncPacket packet = new BlockHealthSyncPacket(id, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), newHp, maxHp);
                for (ServerPlayer player : serverLevel.players()) {
                    if (player.distanceToSqr(worldPos.x, worldPos.y, worldPos.z) < 4096) {
                        PacketDistributor.sendToPlayer(player, packet);
                    }
                }
            }

            SoundType sound = oldState.getSoundType(level, targetBlock, null);
            if (newHp > 0) {
                level.playSound(null, worldPos.x, worldPos.y, worldPos.z, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            } else {
                level.playSound(null, worldPos.x, worldPos.y, worldPos.z, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            }
            return true;
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("[SMB] damageSingleBlock crashed: {}", t.toString());
            return false;
        }
    }

    public static boolean isBlockDead(Level level, SubLevel subLevel, Vec3 worldPos) {
        try {
            if (subLevel == null || subLevel.isRemoved()) return true;
            Vec3 localPos = subLevel.logicalPose().transformPositionInverse(worldPos);
            BlockPos centerPos = BlockPos.containing(localPos.x, localPos.y, localPos.z);
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return true;
            ChunkPos plotGridChunkPos = new ChunkPos(centerPos.getX() >> 4, centerPos.getZ() >> 4);
            if (!container.inBounds(plotGridChunkPos)) return true;
            LevelChunk chunk = container.getChunk(plotGridChunkPos);
            if (chunk == null) return true;

            BlockState state = chunk.getBlockState(centerPos);
            java.util.UUID id = subLevel.getUniqueId();
            if (id == null) return true;

            if (state.isAir() || state.is(ModBlocks.BROKEN_BLOCK.get()) || isIgnoredBlock(state)) {
                BlockHealthManager.reset(id, centerPos.getX(), centerPos.getY(), centerPos.getZ());
                return true;
            }

            BlockEntity be = chunk.getBlockEntity(centerPos);
            int hp = BlockHealthManager.getHp(id, centerPos.getX(), centerPos.getY(), centerPos.getZ(), state, be);
            return hp <= 0;
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean isIgnoredBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.getNamespace().equals(RAGDOLL_NAMESPACE)) {
            String path = id.getPath();
            return path.equals("mob_ragdoll_part") || path.equals("ragdoll_part") || path.equals("ragdoll_seat");
        }
        return false;
    }

    public static boolean isRagdoll(SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) return false;
        try {
            var box = subLevel.boundingBox();
            int minChunkX = (int) Math.floor(box.minX()) >> 4;
            int maxChunkX = (int) Math.floor(box.maxX()) >> 4;
            int minChunkZ = (int) Math.floor(box.minZ()) >> 4;
            int maxChunkZ = (int) Math.floor(box.maxZ()) >> 4;

            if ((maxChunkX - minChunkX) > 1 || (maxChunkZ - minChunkZ) > 1) return false;

            try {
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server == null) return false;
                for (ServerLevel sl : server.getAllLevels()) {
                    var container = SubLevelContainer.getContainer(sl);
                    if (container == null) continue;
                    boolean foundAnyBlock = false;
                    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                            ChunkPos cp = new ChunkPos(cx, cz);
                            if (!container.inBounds(cp)) continue;
                            LevelChunk chunk = container.getChunk(cp);
                            if (chunk == null) continue;
                            int minY = Math.max(sl.getMinBuildHeight(), (int) Math.floor(box.minY()));
                            int maxY = Math.min(sl.getMaxBuildHeight() - 1, (int) Math.ceil(box.maxY()));
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    for (int y = minY; y <= maxY; y++) {
                                        BlockState st = chunk.getBlockState(new BlockPos(cx * 16 + x, y, cz * 16 + z));
                                        if (st.isAir()) continue;
                                        foundAnyBlock = true;
                                        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
                                        if (!id.getNamespace().equals(RAGDOLL_NAMESPACE)) return false;
                                    }
                                }
                            }
                        }
                    }
                    if (foundAnyBlock) return true;
                }
            } catch (Throwable ignored) {}
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static final class RecentBreakTracker {
        private static final int TTL_TICKS = 40;
        private static final java.util.Map<ResourceLocation, java.util.Map<BlockPos, Long>> RECENT = new java.util.concurrent.ConcurrentHashMap<>();

        private RecentBreakTracker() {}

        public static void record(ServerLevel level, BlockPos pos) {
            RECENT.computeIfAbsent(level.dimension().location(), k -> new java.util.concurrent.ConcurrentHashMap<>()).put(pos.immutable(), level.getGameTime());
        }

        public static boolean isRecentlyBroken(Level level, BlockPos itemPos, double radius) {
            var map = RECENT.get(level.dimension().location());
            if (map == null || map.isEmpty()) return false;
            long now = level.getGameTime();
            map.entrySet().removeIf(e -> now - e.getValue() > TTL_TICKS);
            if (map.isEmpty()) return false;
            for (BlockPos broken : map.keySet()) {
                if (broken.closerThan(itemPos, radius)) return true;
            }
            return false;
        }
    }
}