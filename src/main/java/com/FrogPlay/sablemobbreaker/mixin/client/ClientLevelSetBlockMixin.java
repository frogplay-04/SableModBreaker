package com.FrogPlay.sablemobbreaker.mixin.client;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelSetBlockMixin {

    @Inject(method = "setBlock", at = @At("TAIL"))
    private void sablemobbreaker$forceSubLevelMeshUpdate(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        ClientLevel level = (ClientLevel) (Object) this;
        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        ChunkPos chunkPos = new ChunkPos(pos);
        LevelPlot plot = container.getPlot(chunkPos);
        if (plot == null) return;

        SubLevel subLevel = plot.getSubLevel();
        if (subLevel instanceof ClientSubLevel clientSubLevel) {
            int sectionX = pos.getX() >> 4;
            int sectionY = pos.getY() >> 4;
            int sectionZ = pos.getZ() >> 4;

            clientSubLevel.getRenderData().setDirty(sectionX, sectionY, sectionZ, true);
            SableMobBreaker.LOGGER.debug("[SMB] setBlock marked dirty at {}, {}, {}", sectionX, sectionY, sectionZ);
        }
    }
}