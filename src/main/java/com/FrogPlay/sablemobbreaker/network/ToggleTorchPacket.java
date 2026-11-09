package com.FrogPlay.sablemobbreaker.network;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleTorchPacket() implements CustomPacketPayload {
    public static final Type<ToggleTorchPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableMobBreaker.MOD_ID, "toggle_torch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleTorchPacket> STREAM_CODEC = StreamCodec.unit(new ToggleTorchPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleTorchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof WeldingTorchItem) {
                    boolean isOn = WeldingTorchItem.isOn(stack);
                    WeldingTorchItem.setOn(stack, !isOn);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            !isOn ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRE_EXTINGUISH,
                            SoundSource.PLAYERS, 0.5F, !isOn ? 1.0F : 1.5F);
                }
            }
        });
    }
}