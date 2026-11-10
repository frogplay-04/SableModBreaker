package com.FrogPlay.sablemobbreaker.util;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

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