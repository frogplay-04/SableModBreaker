package com.FrogPlay.sablemobbreaker.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;

public class FuelTankFluidHandler implements IFluidHandlerItem {

    private final ItemStack container;
    private static final int CAPACITY = 2000;

    public FuelTankFluidHandler(ItemStack container) {
        this.container = container;
    }

    @Override
    public @NotNull ItemStack getContainer() {
        return container;
    }

    // Читает FluidStack из CustomData
    private FluidStack getFluid() {
        CustomData data = container.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains("Fluid")) {
                return FluidStack.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("Fluid"))
                        .result().orElse(FluidStack.EMPTY);
            }
        }
        return FluidStack.EMPTY;
    }

    private void setFluid(FluidStack stack) {
        CompoundTag tag = container.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (stack.isEmpty()) {
            tag.remove("Fluid");
        } else {
            FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
                    .result().ifPresent(t -> tag.put("Fluid", t));
        }
        container.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private boolean isGasValid(FluidStack stack) {
        if (stack.isEmpty()) return true;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id.getNamespace().equals("tfmg") &&
                (id.getPath().contains("propane") || id.getPath().contains("butane"));
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return CAPACITY;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return isGasValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (!isGasValid(resource)) return 0;

        FluidStack current = getFluid();
        if (!current.isEmpty() && !FluidStack.isSameFluidSameComponents(current, resource)) return 0;

        int filled = Math.min(CAPACITY - current.getAmount(), resource.getAmount());
        if (filled <= 0) return 0;

        if (action.execute()) {
            FluidStack newStack = new FluidStack(resource.getFluid(), current.getAmount() + filled);
            setFluid(newStack);
        }
        return filled;
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        FluidStack current = getFluid();
        if (current.isEmpty() || !FluidStack.isSameFluidSameComponents(current, resource)) return FluidStack.EMPTY;

        int drained = Math.min(current.getAmount(), resource.getAmount());
        if (drained <= 0) return FluidStack.EMPTY;

        if (action.execute()) {
            FluidStack newStack = current.copyWithAmount(current.getAmount() - drained);
            setFluid(newStack);
        }
        return new FluidStack(current.getFluid(), drained);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack current = getFluid();
        if (current.isEmpty()) return FluidStack.EMPTY;

        int drained = Math.min(current.getAmount(), maxDrain);
        if (drained <= 0) return FluidStack.EMPTY;

        if (action.execute()) {
            FluidStack newStack = current.copyWithAmount(current.getAmount() - drained);
            setFluid(newStack);
        }
        return new FluidStack(current.getFluid(), drained);
    }
}