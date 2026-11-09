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

public record SetTorchStatePacket(boolean state) implements CustomPacketPayload {
    public static final Type<SetTorchStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableMobBreaker.MOD_ID, "set_torch_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTorchStatePacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeBoolean(packet.state()),
            buf -> new SetTorchStatePacket(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetTorchStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof WeldingTorchItem) {
                    boolean wasOn = WeldingTorchItem.isOn(stack);
                    if (wasOn != packet.state()) {
                        WeldingTorchItem.setOn(stack, packet.state());
                        // Звук при включении/выключении
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                packet.state() ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRE_EXTINGUISH,
                                SoundSource.PLAYERS, 0.5F, packet.state() ? 1.0F : 1.5F);
                    }
                }
            }
        });
    }
}