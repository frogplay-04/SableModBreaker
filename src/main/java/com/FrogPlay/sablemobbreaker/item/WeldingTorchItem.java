package com.FrogPlay.sablemobbreaker.item;

import com.FrogPlay.sablemobbreaker.event.WeldingTorchHandler;
import com.FrogPlay.sablemobbreaker.menu.WeldingTorchMenu;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class WeldingTorchItem extends Item {

    public WeldingTorchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player instanceof ServerPlayer sp) {
                sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new WeldingTorchMenu(id, inv, stack),
                        Component.translatable("container.sablemobbreaker.welding_torch")
                ), (RegistryFriendlyByteBuf buf) -> ItemStack.STREAM_CODEC.encode(buf, stack));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // === Полоска топлива на самом предмете ===
    @Override
    public boolean isBarVisible(ItemStack stack) {
        ItemStack bottle = getBottle(stack);
        return !bottle.isEmpty() && WeldingTorchHandler.getFuelAmount(bottle) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int fuel = WeldingTorchHandler.getFuelAmount(getBottle(stack));
        return Math.round(13.0F * fuel / 2000.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        ItemStack bottle = getBottle(stack);
        if (WeldingTorchHandler.isOxygenBottle(bottle)) return 0xFF0000;
        return 0x00AAFF;
    }

    // === Тултип в инвентаре ===
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ItemStack bottle = getBottle(stack);

        // 1. Состояние (ВКЛ/ВЫКЛ)
        if (isOn(stack)) {
            tooltip.add(Component.literal("Состояние: ВКЛ").withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Состояние: ВЫКЛ").withStyle(ChatFormatting.DARK_GRAY));
        }

        // 2. Информация о баллоне
        if (bottle.isEmpty()) {
            tooltip.add(Component.literal("Баллон: пусто").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("Баллон: ")
                    .append(bottle.getHoverName().copy().withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.GRAY));

            if (WeldingTorchHandler.isOxygenBottle(bottle)) {
                tooltip.add(Component.literal("⚠ ОПАСНО! Кислород взорвётся").withStyle(ChatFormatting.RED));
            } else {
                int fuel = WeldingTorchHandler.getFuelAmount(bottle);
                tooltip.add(Component.literal("Топливо: " + fuel + " / 2000")
                        .withStyle(fuel > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
                if (fuel > 0) {
                    tooltip.add(Component.literal(String.format("(%.0f%%)", (fuel * 100.0f) / 2000))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }

        super.appendHoverText(stack, context, tooltip, flag);
    }

    public static ItemStack getBottle(ItemStack torch) {
        CustomData customData = torch.get(DataComponents.CUSTOM_DATA);
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

    public static void setBottle(ItemStack torch, ItemStack bottle) {
        CompoundTag tag = torch.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (bottle.isEmpty()) {
            tag.remove("Bottle");
        } else {
            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, bottle).result().ifPresent(t -> tag.put("Bottle", t));
        }
        torch.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isOn(ItemStack torch) {
        CustomData data = torch.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("IsOn");
    }

    public static void setOn(ItemStack torch, boolean on) {
        CompoundTag tag = torch.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean("IsOn", on);
        torch.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getFuse(ItemStack torch) {
        CustomData data = torch.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag().getInt("Fuse") : 0;
    }

    public static void setFuse(ItemStack torch, int ticks) {
        CompoundTag tag = torch.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt("Fuse", ticks);
        torch.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}