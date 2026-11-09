package com.FrogPlay.sablemobbreaker.mixin.client;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
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
        String tag = "[SMB CLIENT]";
        SableMobBreaker.LOGGER.info("{} handleBlockUpdate pos={}", tag, pos);

        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            SableMobBreaker.LOGGER.warn("{} container == null", tag);
            return;
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        LevelPlot plot = container.getPlot(chunkPos);
        if (plot == null) {
            SableMobBreaker.LOGGER.warn("{} plot == null for {}", tag, chunkPos);
            return;
        }

        SubLevel subLevel = plot.getSubLevel();
        if (!(subLevel instanceof ClientSubLevel clientSubLevel)) {
            SableMobBreaker.LOGGER.warn("{} not ClientSubLevel", tag);
            return;
        }

        int sx = pos.getX() >> 4;
        int sy = pos.getY() >> 4;
        int sz = pos.getZ() >> 4;

        // 1. Sable render data
        try {
            clientSubLevel.getRenderData().setDirty(sx, sy, sz, true);
            SableMobBreaker.LOGGER.info("{} method=ClientSubLevel.getRenderData().setDirty OK", tag);
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("{} method=setDirty FAIL {}", tag, t.toString());
        }

        // 2. Vanilla LevelRenderer
        try {
            Minecraft.getInstance().levelRenderer.setSectionDirty(sx, sy, sz);
            SableMobBreaker.LOGGER.info("{} method=levelRenderer.setSectionDirty OK", tag);
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.error("{} method=levelRenderer.setSectionDirty FAIL {}", tag, t.toString());
        }
    }
}