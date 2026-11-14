package com.FrogPlay.sablemobbreaker.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class GoggleHelper {

    private static final ResourceLocation GOGGLES_ID = ResourceLocation.fromNamespaceAndPath("create", "goggles");
    private static final TagKey<Item> CURIO_HEAD = TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", "head"));

    private GoggleHelper() {}

    public static boolean isWearingGoggles(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (isGoggles(head)) return true;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isGoggles(stack)) return true;
        }

        return false;
    }

    public static boolean isGoggles(ItemStack stack) {
        if (stack.isEmpty()) return false;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.equals(GOGGLES_ID)) return true;

        if (stack.is(CURIO_HEAD)) return true;

        String path = id.getPath();
        return path.contains("goggles") || path.contains("diving_helmet");
    }
}