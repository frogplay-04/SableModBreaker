package com.FrogPlay.sablemobbreaker.network;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.event.DoorWeldHandler;
import com.FrogPlay.sablemobbreaker.item.PlasmaCutterItem;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetToolUsePacket(boolean pressed) implements CustomPacketPayload {

    public static final Type<SetToolUsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    SableMobBreaker.MOD_ID, "set_tool_use"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetToolUsePacket> STREAM_CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> buf.writeBoolean(packet.pressed()),
                    buf -> new SetToolUsePacket(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetToolUsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) return;

            if (!(player.getMainHandItem().getItem() instanceof WeldingTorchItem)
                    && !(player.getMainHandItem().getItem() instanceof PlasmaCutterItem)) {
                DoorWeldHandler.setRightUse(player.getUUID(), false);
                return;
            }

            DoorWeldHandler.setRightUse(
                    player.getUUID(),
                    packet.pressed() && !player.isShiftKeyDown()
            );
        });
    }
}