package com.FrogPlay.sablemobbreaker.gametest;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.item.PlasmaCutterItem;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.UUID;

/**
 * Regression coverage for the shared block-health store and tool item data.
 * These tests exercise server-side logic; they do not simulate mouse input or
 * render the client UI.
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

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void blockHealthIsIsolatedBetweenSubLevels(GameTestHelper helper) {
        UUID firstSubLevel = UUID.randomUUID();
        UUID secondSubLevel = UUID.randomUUID();
        BlockPos pos = new BlockPos(4, 2, 1);
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        int maxHp = BlockHealthManager.getMaxHp(state);

        BlockHealthManager.setHp(firstSubLevel, pos.getX(), pos.getY(), pos.getZ(), 3);
        int secondHp = BlockHealthManager.getHp(
                secondSubLevel, pos.getX(), pos.getY(), pos.getZ(), state);

        helper.assertTrue(secondHp == maxHp,
                "Damage at the same coordinates in another sublevel must not leak across sublevels.");
        helper.assertTrue(BlockHealthManager.getHp(
                        firstSubLevel, pos.getX(), pos.getY(), pos.getZ(), state) == 3,
                "The first sublevel must retain its own HP value.");

        BlockHealthManager.reset(firstSubLevel, pos.getX(), pos.getY(), pos.getZ());
        BlockHealthManager.reset(secondSubLevel, pos.getX(), pos.getY(), pos.getZ());
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void deadBlockTrackingAndHpClamping(GameTestHelper helper) {
        UUID subLevelId = UUID.randomUUID();
        BlockPos pos = new BlockPos(7, 2, 5);

        BlockHealthManager.setHp(subLevelId, pos.getX(), pos.getY(), pos.getZ(), -10);
        helper.assertTrue(BlockHealthManager.isDeadAt(subLevelId, pos),
                "HP below zero must be clamped to zero and marked as dead.");
        helper.assertTrue(BlockHealthManager.getDeadBlocks(subLevelId).contains(pos),
                "Dead block coordinates must be present in the dead-block index.");

        BlockHealthManager.setHp(subLevelId, pos.getX(), pos.getY(), pos.getZ(), 4);
        helper.assertTrue(!BlockHealthManager.isDeadAt(subLevelId, pos),
                "A block with positive HP must no longer be marked dead.");
        helper.assertTrue(!BlockHealthManager.getDeadBlocks(subLevelId).contains(pos),
                "The dead-block index must stop reporting a repaired block.");

        BlockHealthManager.reset(subLevelId, pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(BlockHealthManager.getMaxHp(Blocks.AIR.defaultBlockState()) == 0,
                "Air must not have block health.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void oxygenTankAirIsClamped(GameTestHelper helper) {
        ItemStack tank = new ItemStack(SableMobBreaker.OXYGEN_TANK.get());

        com.FrogPlay.sablemobbreaker.item.OxygenTankItem.setAir(tank, -50);
        helper.assertTrue(com.FrogPlay.sablemobbreaker.item.OxygenTankItem.getAir(tank) == 0,
                "Oxygen tank air must not become negative.");

        com.FrogPlay.sablemobbreaker.item.OxygenTankItem.setAir(tank,
                com.FrogPlay.sablemobbreaker.item.OxygenTankItem.MAX_AIR + 500);
        helper.assertTrue(com.FrogPlay.sablemobbreaker.item.OxygenTankItem.getAir(tank)
                        == com.FrogPlay.sablemobbreaker.item.OxygenTankItem.MAX_AIR,
                "Oxygen tank air must not exceed its capacity.");

        com.FrogPlay.sablemobbreaker.item.OxygenTankItem.setAir(tank, 725);
        helper.assertTrue(com.FrogPlay.sablemobbreaker.item.OxygenTankItem.getAir(tank) == 725,
                "Oxygen tank must retain a valid intermediate air value.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void plasmaCutterSwitchStatePersistsInItemData(GameTestHelper helper) {
        ItemStack cutter = new ItemStack(SableMobBreaker.PLASMA_CUTTER.get());

        PlasmaCutterItem.setOn(cutter, true);
        helper.assertTrue(PlasmaCutterItem.isOn(cutter),
                "The plasma cutter enabled state must be stored in item data.");

        PlasmaCutterItem.setOn(cutter, false);
        helper.assertTrue(!PlasmaCutterItem.isOn(cutter),
                "The plasma cutter enabled state must be removable from item data.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void weldingTorchBottleDataPersists(GameTestHelper helper) {
        ItemStack torch = new ItemStack(SableMobBreaker.WELDING_TORCH.get());
        ItemStack tank = new ItemStack(SableMobBreaker.OXYGEN_TANK.get());
        com.FrogPlay.sablemobbreaker.item.OxygenTankItem.setAir(tank, 900);

        WeldingTorchItem.setBottle(torch, tank);
        ItemStack restored = WeldingTorchItem.getBottle(torch);

        helper.assertTrue(!restored.isEmpty() && restored.is(SableMobBreaker.OXYGEN_TANK.get()),
                "The welding torch must retain the inserted oxygen tank.");
        helper.assertTrue(com.FrogPlay.sablemobbreaker.item.OxygenTankItem.getAir(restored) == 900,
                "The inserted oxygen tank's stored air must survive serialization.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void plasmaCutterBottleDataPersists(GameTestHelper helper) {
        ItemStack cutter = new ItemStack(SableMobBreaker.PLASMA_CUTTER.get());
        ItemStack tank = new ItemStack(SableMobBreaker.OXYGEN_TANK.get());
        com.FrogPlay.sablemobbreaker.item.OxygenTankItem.setAir(tank, 1100);

        PlasmaCutterItem.setBottle(cutter, tank);
        ItemStack restored = PlasmaCutterItem.getBottle(cutter);

        helper.assertTrue(!restored.isEmpty() && restored.is(SableMobBreaker.OXYGEN_TANK.get()),
                "The plasma cutter must retain the inserted oxygen tank.");
        helper.assertTrue(com.FrogPlay.sablemobbreaker.item.OxygenTankItem.getAir(restored) == 1100,
                "The inserted oxygen tank's stored air must survive serialization.");
        helper.succeed();
    }
}
