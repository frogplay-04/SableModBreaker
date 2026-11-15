package com.FrogPlay.sablemobbreaker.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

public class PlasmaCutterInventory extends ItemStackHandler {
    private final ItemStack cutter;

    public PlasmaCutterInventory(ItemStack cutter) {
        super(1);
        this.cutter = cutter;
        setStackInSlot(0, PlasmaCutterItem.getBottle(cutter));
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        ItemStack current = getStackInSlot(0);
        PlasmaCutterItem.setBottle(cutter, current);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return stack.getItem() instanceof OxygenTankItem || stack.getItem() instanceof FuelTankItem;
    }
}