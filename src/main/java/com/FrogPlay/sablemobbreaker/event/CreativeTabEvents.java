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
            event.accept(SableMobBreaker.PLASMA_CUTTER.get());
            event.accept(SableMobBreaker.OXYGEN_TANK.get());
        }

        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(SableMobBreaker.FLOW_METER.get());
            event.accept(SableMobBreaker.ELECTRODE.get());
            event.accept(SableMobBreaker.WIRE.get());
            event.accept(SableMobBreaker.DISPLAY_SCREEN.get());
            event.accept(SableMobBreaker.NOZZLE.get());
            event.accept(SableMobBreaker.CONTROL_PANEL.get());
            event.accept(SableMobBreaker.LARGE_WELDING_HANDLE.get());
            event.accept(SableMobBreaker.WELDING_HANDLE.get());
            event.accept(SableMobBreaker.LARGE_PLASMA_HANDLE.get());
            event.accept(SableMobBreaker.PLASMA_HANDLE.get());
            event.accept(SableMobBreaker.HULL.get());
        }

        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SableMobBreaker.OXYGEN_SHELF_ITEM.get());
        }
    }
}