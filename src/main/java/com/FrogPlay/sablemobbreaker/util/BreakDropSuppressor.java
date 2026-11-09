package com.FrogPlay.sablemobbreaker.util;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Гасит ItemEntity, которые пытаются заспавниться в только что сломанной
 * позиции. Единственная причина существования этого класса — Copycat-блоки
 * Create, которые при handleBlockChange успевают вернуть мимикрированный
 * предмет через getDrops/getCloneItemStack до отсоединения BlockEntity.
 *
 * Работает поверх RecentBreakTracker из SubLevelDamageHelper: если в момент
 * спавна предмета его позиция ближе 1.5 блока к недавно сломанному блоку —
 * спавн отменяется. Окно 2 секунды, чтобы поймать отложенные дропы.
 */
@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class BreakDropSuppressor {

    private static final double SUPPRESS_RADIUS = 1.5;

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity item)) return;

        try {
            if (SubLevelDamageHelper.RecentBreakTracker.isRecentlyBroken(
                    event.getLevel(), item.blockPosition(), SUPPRESS_RADIUS)) {
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("[SMB] drop suppressor failed: {}", t.toString());
        }
    }
}