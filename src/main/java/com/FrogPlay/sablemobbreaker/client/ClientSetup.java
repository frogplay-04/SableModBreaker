package com.FrogPlay.sablemobbreaker.client;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SableMobBreaker.WELDING_TORCH_MENU.get(), WeldingTorchScreen::new);
    }
}