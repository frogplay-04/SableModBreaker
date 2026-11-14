package com.FrogPlay.sablemobbreaker.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class GoggleHelper {

    private static final ResourceLocation GOGGLES_ID =
            ResourceLocation.fromNamespaceAndPath("create", "goggles");

    private GoggleHelper() {}

    public static boolean isWearingGoggles(Player player) {
        try {
            if (GogglesItem.isWearingGoggles(player)) return true;
        } catch (Throwable ignored) {}

        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (isGoggles(head)) return true;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isGoggles(player.getInventory().getItem(i))) return true;
        }

        return false;
    }

    public static boolean isGoggles(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.equals(GOGGLES_ID)) return true;
        String path = id.getPath();
        return path.contains("goggles") || path.contains("diving_helmet");
    }
}