package com.FrogPlay.sablemobbreaker.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BrokenBlockEntity extends BlockEntity {
    private BlockState originalState;
    private CompoundTag originalNbt;

    public BrokenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BROKEN_BLOCK_ENTITY.get(), pos, state);
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    public void setOriginalState(BlockState originalState) {
        this.originalState = originalState;
        setChanged();
    }

    public CompoundTag getOriginalNbt() {
        return originalNbt;
    }

    public void setOriginalNbt(CompoundTag originalNbt) {
        this.originalNbt = originalNbt;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (originalState != null) {
            tag.put("OriginalState", NbtUtils.writeBlockState(originalState));
        }
        if (originalNbt != null) {
            tag.put("OriginalNbt", originalNbt);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("OriginalState")) {
            originalState = NbtUtils.readBlockState(
                    BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("OriginalState")
            );
        }
        if (tag.contains("OriginalNbt")) {
            originalNbt = tag.getCompound("OriginalNbt");
        }
    }
}