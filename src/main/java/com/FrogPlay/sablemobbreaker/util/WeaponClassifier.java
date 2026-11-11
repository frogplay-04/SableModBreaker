package com.FrogPlay.sablemobbreaker.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
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
        return !stack.isEmpty() && stack.is(SCGUNS_GUN_TAG);
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) {
            return true;
        }
        if (isScorchedGunsWeapon(stack)) {
            return true;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("spartanweaponry");
    }

    public static boolean isNaturalShooter(Mob mob) {
        if (mob instanceof AbstractSkeleton || mob instanceof Illusioner || mob instanceof Pillager) {
            return false;
        }
        return mob instanceof RangedAttackMob;
    }
}