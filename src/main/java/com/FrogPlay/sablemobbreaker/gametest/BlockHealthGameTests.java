package com.FrogPlay.sablemobbreaker.gametest;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.UUID;

/**
 * Server-side regression tests for the core block-health storage used by
 * the plasma-cutter and repair paths. These tests deliberately exercise the
 * shared manager rather than simulating player input.
 */
@GameTestHolder(SableMobBreaker.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockHealthGameTests {
    private BlockHealthGameTests() {}

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void blockHealthDamageAndReset(GameTestHelper helper) {
        UUID subLevelId = UUID.randomUUID();
        BlockPos pos = new BlockPos(2, 3, 4);
        var state = Blocks.IRON_BLOCK.defaultBlockState();

        BlockHealthManager.reset(subLevelId, pos.getX(), pos.getY(), pos.getZ());
        int maxHp = BlockHealthManager.getMaxHp(state);
        int initialHp = BlockHealthManager.getHp(
                subLevelId, pos.getX(), pos.getY(), pos.getZ(), state);

        helper.assertTrue(maxHp > 5, "Iron block should have more than 5 HP for this regression test.");
        helper.assertTrue(initialHp == maxHp, "A new block position should start at maximum HP.");

        int afterDamage = BlockHealthManager.damage(
                subLevelId, pos.getX(), pos.getY(), pos.getZ(), state, 5, null);
        helper.assertTrue(afterDamage == initialHp - 5,
                "One plasma-cutter damage pulse must subtract exactly 5 HP.");
        helper.assertTrue(BlockHealthManager.getHp(
                        subLevelId, pos.getX(), pos.getY(), pos.getZ(), state) == afterDamage,
                "Damaged HP must be retained by the health manager.");

        BlockHealthManager.reset(subLevelId, pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(BlockHealthManager.getHp(
                        subLevelId, pos.getX(), pos.getY(), pos.getZ(), state) == maxHp,
                "Resetting a repaired/replaced block must restore its default HP.");

        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void destroyedBlockDataIsRetained(GameTestHelper helper) {
        UUID subLevelId = UUID.randomUUID();
        BlockPos pos = new BlockPos(1, 2, 3);
        CompoundTag originalData = new CompoundTag();
        originalData.putString("test_payload", "copycat-regression");

        BlockHealthManager.saveDestroyedBlock(
                subLevelId, pos.getX(), pos.getY(), pos.getZ(),
                Blocks.OAK_PLANKS.defaultBlockState(), originalData);

        BlockHealthManager.SavedBlock saved = BlockHealthManager.getDestroyedBlock(
                subLevelId, pos.getX(), pos.getY(), pos.getZ());

        helper.assertTrue(saved != null, "Destroyed block data should be retrievable.");
        helper.assertTrue(saved.state().is(Blocks.OAK_PLANKS),
                "The original block state must be preserved.");
        helper.assertTrue(saved.nbt() != null
                        && "copycat-regression".equals(saved.nbt().getString("test_payload")),
                "The original block-entity payload must be preserved.");

        BlockHealthManager.removeDestroyedBlock(
                subLevelId, pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(BlockHealthManager.getDestroyedBlock(
                subLevelId, pos.getX(), pos.getY(), pos.getZ()) == null,
                "Restored block data should be removable after repair.");

        helper.succeed();
    }
}
