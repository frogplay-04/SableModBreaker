package com.FrogPlay.sablemobbreaker.item;

import com.FrogPlay.sablemobbreaker.menu.PlasmaCutterMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class PlasmaCutterItem extends Item {

    public PlasmaCutterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player instanceof ServerPlayer sp) {
                sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new PlasmaCutterMenu(id, inv, stack),
                        Component.translatable("container.sablemobbreaker.plasma_cutter")
                ), (RegistryFriendlyByteBuf buf) -> ItemStack.STREAM_CODEC.encode(buf, stack));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static ItemStack getBottle(ItemStack cutter) {
        CustomData customData = cutter.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Bottle")) {
                Tag bottleTag = tag.get("Bottle");
                if (bottleTag != null) {
                    return ItemStack.CODEC.parse(NbtOps.INSTANCE, bottleTag).result().orElse(ItemStack.EMPTY);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setBottle(ItemStack cutter, ItemStack bottle) {
        CompoundTag tag = cutter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (bottle.isEmpty()) {
            tag.remove("Bottle");
        } else {
            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, bottle).result().ifPresent(t -> tag.put("Bottle", t));
        }
        cutter.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isOn(ItemStack cutter) {
        CustomData data = cutter.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("IsOn");
    }

    public static void setOn(ItemStack cutter, boolean on) {
        CompoundTag tag = cutter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean("IsOn", on);
        cutter.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}