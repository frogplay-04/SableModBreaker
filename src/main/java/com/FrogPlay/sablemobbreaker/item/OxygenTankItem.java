package com.FrogPlay.sablemobbreaker.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class OxygenTankItem extends Item {

    public static final int MAX_AIR = 1500;

    public OxygenTankItem(Properties properties) {
        super(properties);
    }

    public static int getAir(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag().getInt("Air");
    }

    public static void setAir(ItemStack stack, int air) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt("Air", Math.max(0, Math.min(MAX_AIR, air)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getAir(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getAir(stack) / (float) MAX_AIR);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x00AAFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int air = getAir(stack);
        tooltip.add(Component.translatable("tooltip.sablemobbreaker.oxygen_tank.air", air, MAX_AIR)
                .withStyle(air > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));

        if (air > 0) {
            tooltip.add(Component.literal(String.format("(%.0f%%)", (air * 100.0f) / MAX_AIR))
                    .withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}