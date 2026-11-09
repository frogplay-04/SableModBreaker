package com.FrogPlay.sablemobbreaker.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public class FuelTankItem extends Item {

    public static final int CAPACITY = 2000;

    public FuelTankItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getFluidAmount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int fluid = getFluidAmount(stack);
        return Math.round(13.0F * fluid / (float) CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x00AAFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int fluid = getFluidAmount(stack);
        tooltip.add(Component.literal("Топливо: " + fluid + " / " + CAPACITY)
                .withStyle(fluid > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));

        if (fluid > 0) {
            tooltip.add(Component.literal(String.format("(%.0f%%)", (fluid * 100.0f) / CAPACITY))
                    .withStyle(ChatFormatting.GRAY));
        }

        super.appendHoverText(stack, context, tooltip, flag);
    }

    public static int getFluidAmount(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains("Fluid")) {
                return FluidStack.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("Fluid"))
                        .result().map(FluidStack::getAmount).orElse(0);
            }
            return Math.min(CAPACITY, tag.getInt("GasStored"));
        }
        return 0;
    }
}