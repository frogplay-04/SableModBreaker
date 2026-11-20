package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.block.ModBlockEntities;
import com.FrogPlay.sablemobbreaker.block.OxygenShelfBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public final class OxygenShelfWrenchHandler {
    private static final ResourceLocation CREATE_WRENCH =
            ResourceLocation.fromNamespaceAndPath("create", "wrench");

    private OxygenShelfWrenchHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !event.getEntity().isShiftKeyDown()) return;
        if (!CREATE_WRENCH.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()))) return;

        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof OxygenShelfBlockEntity shelfEntity)) return;

        ItemStack shelfItem = new ItemStack(
                com.FrogPlay.sablemobbreaker.SableMobBreaker.OXYGEN_SHELF_ITEM.get());
        var data = shelfEntity.saveWithoutMetadata(level.registryAccess());
        BlockItem.setBlockEntityData(shelfItem, ModBlockEntities.OXYGEN_SHELF_ENTITY.get(), data);

        if (!event.getEntity().getInventory().add(shelfItem)) {
            event.getEntity().drop(shelfItem, false);
        }

        level.removeBlock(pos, false);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }
}
