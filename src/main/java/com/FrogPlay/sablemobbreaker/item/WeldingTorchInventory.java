package com.FrogPlay.sablemobbreaker.item;

import com.FrogPlay.sablemobbreaker.event.WeldingTorchHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

public class WeldingTorchInventory extends ItemStackHandler {
    private final ItemStack torch;

    public WeldingTorchInventory(ItemStack torch) {
        super(1);
        this.torch = torch;
        setStackInSlot(0, WeldingTorchItem.getBottle(torch));
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        ItemStack current = getStackInSlot(0);
        WeldingTorchItem.setBottle(torch, current);

        if (!current.isEmpty() && WeldingTorchHandler.isOxygenBottle(current)) {
            WeldingTorchItem.setFuse(torch, 20); // 20 тиков = 1 секунда
        } else {
            WeldingTorchItem.setFuse(torch, 0); // Сбрасываем, если вытащили
        }
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return WeldingTorchHandler.isValidBottle(stack);
    }
}