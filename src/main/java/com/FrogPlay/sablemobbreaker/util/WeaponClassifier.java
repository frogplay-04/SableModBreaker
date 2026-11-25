package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

public final class WeaponClassifier {
    private WeaponClassifier() {}

    private static final TagKey<Item> SCGUNS_GUN_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("scguns", "enchantable/gun"));

    public static boolean isRanged(Mob mob) {
        return isHoldingRangedWeapon(mob) || isNaturalShooter(mob);
    }

    public static boolean isHoldingRangedWeapon(Mob mob) {
        return isRangedWeapon(mob.getMainHandItem()) || isRangedWeapon(mob.getOffhandItem());
    }

    public static boolean isHoldingScorchedGunsWeapon(Mob mob) {
        return isScorchedGunsWeapon(mob.getMainHandItem()) || isScorchedGunsWeapon(mob.getOffhandItem());
    }

    public static boolean isScorchedGunsWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(SCGUNS_GUN_TAG)) return true;
        try {
            Class<?> gunItem = Class.forName("top.ribs.scguns.item.GunItem", false,
                    stack.getItem().getClass().getClassLoader());
            return gunItem.isInstance(stack.getItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) return true;
        if (isScorchedGunsWeapon(stack)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("spartanweaponry")
                && (id.getPath().contains("bow") || id.getPath().contains("crossbow")
                || id.getPath().contains("spear") || id.getPath().contains("javelin")
                || id.getPath().contains("throwing"));
    }

    public static boolean isNaturalShooter(Mob mob) {
        if (mob instanceof RangedAttackMob) return true;

        // A number of vanilla/modded shooters (Blaze, Ghast, Shulker, etc.) use
        // dedicated AI goals instead of implementing RangedAttackMob.
        return hasNativeRangedGoal(mob);
    }

    public static boolean hasNativeRangedGoal(Mob mob) {
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            String name = wrapped.getGoal().getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("rangedattackgoal")
                    || name.contains("bowattackgoal")
                    || name.contains("crossbowattackgoal")
                    || name.contains("blazeattackgoal")
                    || name.contains("ghastattackgoal")
                    || name.contains("shulkerattackgoal")
                    || name.contains("witchattackgoal")
                    || name.contains("guardianattackgoal")
                    || name.contains("snowgolemattackgoal")
                    || name.contains("projectileattackgoal")
                    || name.contains("shootattackgoal")
                    || name.contains("shootgoal")
                    || name.contains("fireballgoal")
                    || name.contains("attackspellgoal")) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasNativeRangedAi(Mob mob) {
        return hasNativeRangedGoal(mob);
    }
}
