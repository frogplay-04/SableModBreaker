package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockHealthManager {

    private static final TagKey<Block> SAB_LIGHT = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("s_a_b", "light"));
    private static final TagKey<Block> SAB_BASIC = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("s_a_b", "basic"));
    private static final TagKey<Block> SAB_CUT = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("s_a_b", "cut"));
    private static final TagKey<Block> SAB_HARD = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("s_a_b", "hard"));

    private static final Map<String, Integer> HP = new ConcurrentHashMap<>();
    private static final Map<String, SavedBlock> SAVED_BLOCKS = new ConcurrentHashMap<>();

    public record SavedBlock(BlockState state, CompoundTag nbt) {}

    private BlockHealthManager() {}

    public static int getMaxHp(BlockState state) {
        if (state.is(SAB_HARD)) return 100;
        if (state.is(SAB_CUT)) return 70;
        if (state.is(SAB_BASIC)) return 50;
        if (state.is(SAB_LIGHT)) return 30;

        if (state.is(BlockTags.BEACON_BASE_BLOCKS)) return 50;
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 75;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 40;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 25;

        if (state.is(BlockTags.PLANKS)) return 10;
        if (state.is(BlockTags.LOGS)) return 15;

        if (state.is(BlockTags.WOOL)) return 5;
        if (state.is(BlockTags.WOOL_CARPETS)) return 3;

        if (state.is(BlockTags.IMPERMEABLE)) return 8;

        if (state.is(BlockTags.STONE_BRICKS)) return 30;
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) return 30;

        return 20;
    }

    // ============================================================
    // Определение HP для Copycat блоков (с учетом текстуры)
    // ============================================================

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

    // ============================================================
    // Основные методы HP
    // ============================================================

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