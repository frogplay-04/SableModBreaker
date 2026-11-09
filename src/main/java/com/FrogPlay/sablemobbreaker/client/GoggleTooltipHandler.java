package com.FrogPlay.sablemobbreaker.client;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID, value = Dist.CLIENT)
public class GoggleTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        BlockState state = blockItem.getBlock().defaultBlockState();
        if (state.isAir()) return;

        // Извлекаем NBT предмета через DataComponents (новый API 1.21.1)
        CompoundTag nbt = null;
        var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (beData != null) {
            nbt = beData.copyTag();
        }

        int maxHp = BlockHealthManager.getMaxHp(state, nbt);
        if (maxHp <= 0) return;

        ChatFormatting color = maxHp >= 50 ? ChatFormatting.GOLD
                : maxHp >= 25 ? ChatFormatting.YELLOW
                : ChatFormatting.GRAY;

        event.getToolTip().add(
                Component.literal("HP: " + maxHp).withStyle(color)
        );
    }
}