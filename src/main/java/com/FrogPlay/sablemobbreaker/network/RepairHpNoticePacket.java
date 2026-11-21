package com.FrogPlay.sablemobbreaker.network;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.client.ClientEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RepairHpNoticePacket(UUID subLevelId, int x, int y, int z, int hp, int maxHp)
        implements CustomPacketPayload {
    public static final Type<RepairHpNoticePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SableMobBreaker.MOD_ID, "repair_hp_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairHpNoticePacket> STREAM_CODEC =
            StreamCodec.ofMember(RepairHpNoticePacket::encode, RepairHpNoticePacket::new);

    public RepairHpNoticePacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(subLevelId);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        buf.writeVarInt(hp);
        buf.writeVarInt(maxHp);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RepairHpNoticePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientEvents.showRepairHp(
                packet.subLevelId(), packet.x(), packet.y(), packet.z(), packet.hp(), packet.maxHp()));
    }
}
