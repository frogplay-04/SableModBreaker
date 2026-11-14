package com.FrogPlay.sablemobbreaker.mixin.client;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
public class ClientChunkLoadMixin {

    @Shadow @Final private ClientLevel level;

    @Inject(method = "replaceWithPacketData", at = @At("TAIL"))
    private void sablemobbreaker$forceSableRenderUpdate(int x, int z, FriendlyByteBuf buffer, CompoundTag tag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer, CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk chunk = cir.getReturnValue();
        if (chunk == null) return;

        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        ChunkPos pos = new ChunkPos(x, z);
        var plot = container.getPlot(pos);
        if (plot == null) return;

        var subLevel = plot.getSubLevel();
        if (subLevel instanceof ClientSubLevel clientSubLevel) {
            // Помечаем ВСЕ секции этого суб-уровня как грязные (требующие перерисовки)
            for (int sectionY = level.getMinSection(); sectionY < level.getMaxSection(); sectionY++) {
                clientSubLevel.getRenderData().setDirty(x, sectionY, z, true);
            }
            SableMobBreaker.LOGGER.debug("[SMB] Force re-render Sable chunk at {}", pos);
        }
    }
}