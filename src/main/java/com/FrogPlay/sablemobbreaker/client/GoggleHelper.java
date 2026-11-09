package com.FrogPlay.sablemobbreaker.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class GoggleHelper {

    private static final ResourceLocation GOGGLES_ID =
            ResourceLocation.fromNamespaceAndPath("create", "goggles");

    private GoggleHelper() {}

    public static boolean isWearingGoggles(Player player) {
        if (GogglesItem.isWearingGoggles(player)) return true;

        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getInvMethod = curiosApi.getMethod(
                    "getCuriosInventory",
                    net.minecraft.world.entity.LivingEntity.class
            );
            Object opt = getInvMethod.invoke(null, player);
            if (opt == null) return false;

            Boolean present = (Boolean) opt.getClass().getMethod("isPresent").invoke(opt);
            if (present == null || !present) return false;

            Object handler = opt.getClass().getMethod("get").invoke(opt);
            if (handler == null) return false;

            Object curiosMap = handler.getClass().getMethod("getCurios").invoke(handler);
            if (!(curiosMap instanceof java.util.Map<?, ?> map)) return false;

            for (Object stacksHandler : map.values()) {
                Object stacks = stacksHandler.getClass().getMethod("getStacks").invoke(stacksHandler);
                if (stacks == null) continue;

                int slots = (Integer) stacks.getClass().getMethod("getSlots").invoke(stacks);
                var getStackInSlot = stacks.getClass().getMethod("getStackInSlot", int.class);

                for (int i = 0; i < slots; i++) {
                    ItemStack stack = (ItemStack) getStackInSlot.invoke(stacks, i);
                    if (isGoggles(stack)) return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static boolean isGoggles(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.equals(GOGGLES_ID);
    }
}