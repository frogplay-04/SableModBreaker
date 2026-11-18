package com.FrogPlay.sablemobbreaker.util;

import com.FrogPlay.sablemobbreaker.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockHealthManager {

    private static final Map<String, Integer> HP = new ConcurrentHashMap<>();
    private static final Map<String, SavedBlock> SAVED_BLOCKS = new ConcurrentHashMap<>();

    public record SavedBlock(BlockState state, CompoundTag nbt) {}

    private BlockHealthManager() {}

    public static int getMaxHp(BlockState state) {
        if (state == null || state.isAir()) return 0;

        return hardnessBasedHp(state);
    }

    private static int hardnessBasedHp(BlockState state) {
        float hardness;
        try {
            hardness = state.getDestroySpeed(null, BlockPos.ZERO);
        } catch (Throwable ignored) {
            hardness = 1.0F;
        }
        if (!Float.isFinite(hardness) || hardness < 0.0F) {
            return ModConfig.MAX_BLOCK_HP.get();
        }
        double raw = (hardness + 0.5D) * ModConfig.HP_PER_HARDNESS.get();
        int min = ModConfig.MIN_BLOCK_HP.get();
        int max = Math.max(min, ModConfig.MAX_BLOCK_HP.get());
        return (int) Math.max(min, Math.min(max, Math.round(raw)));
    }

    public static int getMaxHp(BlockState state, BlockEntity be) {
        BlockState mimicked = getMimickedState(be);
        if (mimicked != null) return getMaxHp(mimicked);
        return getMaxHp(state);
    }

    public static int getMaxHp(BlockState state, CompoundTag nbt) {
        if (nbt != null && nbt.contains("material")) {
            String mat = nbt.getString("material");
            if (!mat.isEmpty()) {
                try {
                    var result = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                            BuiltInRegistries.BLOCK.asLookup(), mat, false);
                    if (result != null && result.blockState() != null && !result.blockState().isAir()) {
                        return getMaxHp(result.blockState());
                    }
                } catch (Throwable ignored) {}
            }
        }
        return getMaxHp(state);
    }

    private static BlockState getMimickedState(BlockEntity be) {
        if (be == null) return null;
        try {
            for (java.lang.reflect.Method m : be.getClass().getMethods()) {
                if ((m.getName().equals("getMaterial") || m.getName().equals("getMimickedState") ||
                        m.getName().equals("getState") || m.getName().equals("getMimic"))
                        && m.getParameterCount() == 0 && BlockState.class.isAssignableFrom(m.getReturnType())) {
                    BlockState mimicked = (BlockState) m.invoke(be);
                    if (mimicked != null && !mimicked.isAir()) {
                        return mimicked;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String key(UUID subLevelId, int x, int y, int z) {
        return subLevelId + ":" + x + ":" + y + ":" + z;
    }

    public static int getHp(UUID subLevelId, int x, int y, int z, BlockState state, BlockEntity be) {
        return HP.getOrDefault(key(subLevelId, x, y, z), getMaxHp(state, be));
    }

    public static int getHp(UUID subLevelId, int x, int y, int z, BlockState state) {
        return HP.getOrDefault(key(subLevelId, x, y, z), getMaxHp(state));
    }

    public static void setHp(UUID subLevelId, int x, int y, int z, int hp) {
        HP.put(key(subLevelId, x, y, z), Math.max(hp, 0));
    }

    public static int damage(UUID subLevelId, int x, int y, int z, BlockState state, int amount, BlockEntity be) {
        int current = getHp(subLevelId, x, y, z, state, be);
        int newHp = Math.max(0, current - amount);
        setHp(subLevelId, x, y, z, newHp);
        return newHp;
    }

    public static boolean isDead(UUID subLevelId, int x, int y, int z, BlockState state) {
        return getHp(subLevelId, x, y, z, state) <= 0;
    }

    public static void reset(UUID subLevelId, int x, int y, int z) {
        HP.remove(key(subLevelId, x, y, z));
    }

    public static boolean isDeadAt(UUID subLevelId, int x, int y, int z) {
        if (subLevelId == null) return false;
        Integer hp = HP.get(key(subLevelId, x, y, z));
        return hp != null && hp <= 0;
    }

    public static boolean isDeadAt(UUID subLevelId, BlockPos pos) {
        if (pos == null) return false;
        return isDeadAt(subLevelId, pos.getX(), pos.getY(), pos.getZ());
    }

    public static Set<BlockPos> getDeadBlocks(UUID subLevelId) {
        Set<BlockPos> result = new HashSet<>();
        if (subLevelId == null) return result;
        String prefix = subLevelId + ":";
        for (Map.Entry<String, Integer> entry : HP.entrySet()) {
            if (entry.getValue() > 0) continue;
            String k = entry.getKey();
            if (!k.startsWith(prefix)) continue;
            String[] parts = k.substring(prefix.length()).split(":");
            if (parts.length != 3) continue;
            try {
                result.add(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    public static void saveDestroyedBlock(UUID subId, int x, int y, int z, BlockState state, CompoundTag nbt) {
        SAVED_BLOCKS.put(key(subId, x, y, z), new SavedBlock(state, nbt));
    }

    public static SavedBlock getDestroyedBlock(UUID subId, int x, int y, int z) {
        return SAVED_BLOCKS.get(key(subId, x, y, z));
    }

    public static void removeDestroyedBlock(UUID subId, int x, int y, int z) {
        SAVED_BLOCKS.remove(key(subId, x, y, z));
    }
}