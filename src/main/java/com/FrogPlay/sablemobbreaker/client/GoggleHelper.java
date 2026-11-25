package com.FrogPlay.sablemobbreaker.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

public final class GoggleHelper {
    private static final ResourceLocation GOGGLES_ID =
            ResourceLocation.fromNamespaceAndPath("create", "goggles");

    private GoggleHelper() {}

    public static boolean isWearingGoggles(Player player) {
        if (player == null) return false;
        try {
            if (GogglesItem.isWearingGoggles(player)) return true;
        } catch (Throwable ignored) {}

        if (isGoggles(player.getItemBySlot(EquipmentSlot.HEAD))) return true;

        // Curios 9.x returns Optional<ICuriosItemHandler>, not LazyOptional.
        // Calling resolve() on the returned Optional was the reason head curios were missed.
        if (isInCuriosHeadSlot(player)) return true;

        return false;
    }

    private static boolean isInCuriosHeadSlot(Player player) {
        try {
            if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
            ClassLoader loader = player.getClass().getClassLoader();
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi", false, loader);
            Object result = api.getMethod("getCuriosInventory",
                    net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return false;

            Class<?> handlerApi = Class.forName(
                    "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler", false, loader);
            Method findFirst = handlerApi.getMethod("findFirstCurio", Predicate.class, String.class);
            Object found = findFirst.invoke(optional.get(), (Predicate<ItemStack>) GoggleHelper::isGoggles, "head");
            if (!(found instanceof Optional<?> foundOptional) || foundOptional.isEmpty()) return false;
            Object slotResult = foundOptional.get();
            ItemStack stack = (ItemStack) slotResult.getClass().getMethod("stack").invoke(slotResult);
            return isGoggles(stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isGoggles(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (GOGGLES_ID.equals(id)) return true;
        String path = id.getPath();
        return path.contains("goggles") || path.contains("diving_helmet");
    }
}
