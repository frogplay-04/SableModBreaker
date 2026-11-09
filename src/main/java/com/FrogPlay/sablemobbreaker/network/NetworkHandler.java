package com.FrogPlay.sablemobbreaker.network;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class NetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Твой старый пакет для синхронизации ХП
        registrar.playToClient(
                BlockHealthSyncPacket.TYPE,
                BlockHealthSyncPacket.STREAM_CODEC,
                BlockHealthSyncPacket::handle
        );

        // Новый пакет для принудительной установки состояния
        registrar.playToServer(
                SetTorchStatePacket.TYPE,
                SetTorchStatePacket.STREAM_CODEC,
                SetTorchStatePacket::handle
        );
    }
}