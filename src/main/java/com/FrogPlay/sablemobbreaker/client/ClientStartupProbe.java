package com.FrogPlay.sablemobbreaker.client;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Emits an unambiguous log marker when Minecraft actually constructs its title screen.
 * This is used by the Windows client smoke test; it does not change gameplay.
 */
@EventBusSubscriber(modid = SableMobBreaker.MOD_ID, value = Dist.CLIENT)
public final class ClientStartupProbe {
    private ClientStartupProbe() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            SableMobBreaker.LOGGER.info("SABLE_AUTOTEST_MAIN_MENU_REACHED");
        }
    }
}
