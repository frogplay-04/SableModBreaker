package com.FrogPlay.sablemobbreaker.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class OxygenShelfBlock extends Block implements EntityBlock {
    public OxygenShelfBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OxygenShelfBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof OxygenShelfBlockEntity shelf) {
                shelf.tick();
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof OxygenShelfBlockEntity be) {
            ItemStackHandler handler = be.getItemHandler();

            if (level.isClientSide) return ItemInteractionResult.SUCCESS;

            if (stack.getItem() instanceof com.FrogPlay.sablemobbreaker.item.OxygenTankItem) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (handler.getStackInSlot(i).isEmpty()) {
                        handler.setStackInSlot(i, stack.copyWithCount(1));
                        stack.shrink(1);
                        return ItemInteractionResult.SUCCESS;
                    }
                }
                return ItemInteractionResult.FAIL;
            }

            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof OxygenShelfBlockEntity be) {
            ItemStackHandler handler = be.getItemHandler();

            if (level.isClientSide) return InteractionResult.SUCCESS;

            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slotStack = handler.getStackInSlot(i);
                if (!slotStack.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(slotStack);
                    handler.setStackInSlot(i, ItemStack.EMPTY);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }
}