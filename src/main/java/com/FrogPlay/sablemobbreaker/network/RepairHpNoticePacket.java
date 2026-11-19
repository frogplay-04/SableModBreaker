package com.FrogPlay.sablemobbreaker.network;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.client.ClientEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RepairHpNoticePacket(int hp, int maxHp) implements CustomPacketPayload {
    public static final Type<RepairHpNoticePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SableMobBreaker.MOD_ID, "repair_hp_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairHpNoticePacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buf) -> { buf.writeVarInt(packet.hp()); buf.writeVarInt(packet.maxHp()); },
                    buf -> new RepairHpNoticePacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RepairHpNoticePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientEvents.showRepairHp(packet.hp(), packet.maxHp()));
    }
}
