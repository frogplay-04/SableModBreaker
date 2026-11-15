package com.FrogPlay.sablemobbreaker.block;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.item.OxygenTankItem;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

public class OxygenShelfBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    private static final ResourceLocation OXYGEN_TANK_ID = ResourceLocation.fromNamespaceAndPath(SableMobBreaker.MOD_ID, "oxygen_tank");
    private static final int FE_PER_AIR = 100;

    private final EnergyStorage energyStorage = new EnergyStorage(100_000, 1_000, 1_000, 0) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) setChanged();
            return received;
        }
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0 && !simulate) setChanged();
            return extracted;
        }
    };

    private final ItemStackHandler itemHandler = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id.equals(OXYGEN_TANK_ID);
        }
    };

    public OxygenShelfBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OXYGEN_SHELF_ENTITY.get(), pos, state);
    }

    public EnergyStorage getEnergyStorage() { return energyStorage; }
    public ItemStackHandler getItemHandler() { return itemHandler; }

    public void tick() {
        if (level == null || level.isClientSide) return;

        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof OxygenTankItem)) continue;

            int currentAir = OxygenTankItem.getAir(stack);
            if (currentAir >= OxygenTankItem.MAX_AIR) continue;

            int airNeeded = OxygenTankItem.MAX_AIR - currentAir;
            int energyNeeded = airNeeded * FE_PER_AIR;

            int energyAvailable = energyStorage.extractEnergy(energyNeeded, true);
            if (energyAvailable < FE_PER_AIR) continue;

            int airToAdd = Math.min(airNeeded, energyAvailable / FE_PER_AIR);
            int energyToUse = airToAdd * FE_PER_AIR;

            energyStorage.extractEnergy(energyToUse, false);
            OxygenTankItem.setAir(stack, currentAir + airToAdd);
            itemHandler.setStackInSlot(i, stack);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("gui.goggles.energy_storage"));
        tooltip.add(Component.literal(" " + energyStorage.getEnergyStored() + " FE").withStyle(ChatFormatting.AQUA));

        int tanks = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if (itemHandler.getStackInSlot(i).getItem() instanceof OxygenTankItem) {
                tanks++;
            }
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("tooltip.sablemobbreaker.oxygen_shelf.tanks", tanks, 3).withStyle(ChatFormatting.AQUA));

        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energyStorage.serializeNBT(registries));
        tag.put("Inventory", itemHandler.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) {
            Tag energyTag = tag.get("Energy");
            if (energyTag != null) {
                energyStorage.deserializeNBT(registries, energyTag);
            }
        }
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
    }
}