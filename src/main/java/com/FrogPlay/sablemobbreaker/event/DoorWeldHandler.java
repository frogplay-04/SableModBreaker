package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.item.PlasmaCutterItem;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import com.FrogPlay.sablemobbreaker.util.WeldManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class DoorWeldHandler {

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        ItemStack stack = event.getItemStack();
        boolean isDoorOrTrapdoor = state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock;

        if (WeldManager.isWelded(event.getPos())) {
            if (stack.getItem() instanceof PlasmaCutterItem && PlasmaCutterItem.isOn(stack)) {
                WeldManager.unweld(event.getPos());
                event.getLevel().playSound(null, event.getPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (stack.getItem() instanceof WeldingTorchItem && WeldingTorchItem.isOn(stack)) {
            if (isDoorOrTrapdoor && !state.is(BlockTags.WOODEN_DOORS)) {
                WeldManager.weld(event.getPos());
                event.getLevel().playSound(null, event.getPos(), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.5f, 1.5f);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}