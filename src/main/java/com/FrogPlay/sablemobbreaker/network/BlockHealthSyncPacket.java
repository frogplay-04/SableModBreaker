package com.FrogPlay.sablemobbreaker.network;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.client.BlockHealthClientData;
import com.FrogPlay.sablemobbreaker.client.ClientEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record BlockHealthSyncPacket(UUID subLevelId, int x, int y, int z, int hp, int maxHp)
        implements CustomPacketPayload {

    public static final Type<BlockHealthSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableMobBreaker.MOD_ID, "block_health_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockHealthSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(BlockHealthSyncPacket::encode, BlockHealthSyncPacket::new);

    public BlockHealthSyncPacket(RegistryFriendlyByteBuf buf) {
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

    public static void handle(BlockHealthSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                BlockHealthClientData.put(
                        packet.subLevelId(), packet.x(), packet.y(), packet.z(),
                        packet.hp(), packet.maxHp()
                );

                // === ВРЕМЕННО ОТКЛЮЧЕНО ===
                // Скрытие блока на клиенте через setBlockState ломает Sable-регион
                // даже в single-player. Вернёмся к этому через Mixin в рендер.
                // if (packet.hp() <= 0) {
                //     ClientBlockHider.queueHide(packet.subLevelId(), packet.x(), packet.y(), packet.z());
                // }

                ClientEvents.markDirty();
            } catch (Throwable t) {
                SableMobBreaker.LOGGER.warn("[SMB][CLIENT] handle error: {}", t.toString());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}