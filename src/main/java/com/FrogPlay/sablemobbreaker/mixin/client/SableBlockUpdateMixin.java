package com.FrogPlay.sablemobbreaker.mixin.client;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SableBlockUpdateMixin {

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void sablemobbreaker$forceSubLevelMeshUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockPos pos = packet.getPos();
        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        ChunkPos chunkPos = new ChunkPos(pos);
        LevelPlot plot = container.getPlot(chunkPos);
        if (plot == null) return;

        SubLevel subLevel = plot.getSubLevel();
        if (!(subLevel instanceof ClientSubLevel clientSubLevel)) return;

        int sx = pos.getX() >> 4;
        int sy = pos.getY() >> 4;
        int sz = pos.getZ() >> 4;

        try {
            clientSubLevel.getRenderData().setDirty(sx, sy, sz, true);
            Minecraft.getInstance().levelRenderer.setSectionDirty(sx, sy, sz);
        } catch (Throwable ignored) {}
    }
}