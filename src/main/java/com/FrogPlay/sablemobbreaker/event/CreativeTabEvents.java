package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class CreativeTabEvents {

    @SubscribeEvent
    public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SableMobBreaker.WELDING_TORCH.get());
            event.accept(SableMobBreaker.FUEL_TANK.get());
        }

        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(SableMobBreaker.CLOCK_00.get());
            event.accept(SableMobBreaker.BUCKET.get());
            event.accept(SableMobBreaker.ONE_OAK_SIGN.get());
            event.accept(SableMobBreaker.STRING.get());
            event.accept(SableMobBreaker.OAK_SIGN.get());
            event.accept(SableMobBreaker.SPYGLASS.get());
        }
    }
}