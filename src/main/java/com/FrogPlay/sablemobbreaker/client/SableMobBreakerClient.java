package com.FrogPlay.sablemobbreaker.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SableMobBreakerClient {

    private static final Map<UUID, SubLevel> CACHE = new HashMap<>();
    private static long lastCacheRefresh = 0;

    private SableMobBreakerClient() {}

    private static SubLevel findSubLevel(Level level, UUID id) {
        long now = System.currentTimeMillis();
        if (now - lastCacheRefresh > 2000 || CACHE.isEmpty()) {
            CACHE.clear();
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                for (SubLevel sl : container.getAllSubLevels()) {
                    if (sl.isRemoved()) continue;
                    UUID uid = sl.getUniqueId();
                    if (uid != null) CACHE.put(uid, sl);
                }
            }
            lastCacheRefresh = now;
        }
        return CACHE.get(id);
    }

    public static Vec3 getBlockWorldPos(UUID subLevelId, int x, int y, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        SubLevel subLevel = findSubLevel(level, subLevelId);
        if (subLevel == null) return null;
        return subLevel.logicalPose().transformPosition(new Vec3(x + 0.5, y + 0.5, z + 0.5));
    }

    public static BlockState getBlockStateAt(UUID subLevelId, int x, int y, int z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        SubLevel subLevel = findSubLevel(level, subLevelId);
        if (subLevel == null) return null;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
        if (!container.inBounds(cp)) return null;

        LevelChunk chunk = container.getChunk(cp);
        if (chunk == null) return null;

        return chunk.getBlockState(new BlockPos(x & 15, y, z & 15));
    }
}