package com.FrogPlay.sablemobbreaker.mixin;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem")
public class SubmarinePressureSystemMixin {

    @Inject(method = "onBlockBroken", at = @At("HEAD"), remap = false)
    private static void sablemobbreaker$onBlockBroken(
            net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event,
            CallbackInfo ci) {
        try {
            BlockPos pos = event.getPos();
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sl);
                if (container == null) return;

                for (var subLevel : container.getAllSubLevels()) {
                    if (subLevel.isRemoved()) continue;
                    var subId = subLevel.getUniqueId();
                    if (subId == null) continue;

                    var localPos = subLevel.logicalPose().transformPositionInverse(
                            net.minecraft.world.phys.Vec3.atCenterOf(pos));
                    BlockPos plotPos = BlockPos.containing(localPos.x, localPos.y, localPos.z);

                    if (BlockHealthManager.isDeadAt(subId, plotPos)) {
                        SableMobBreaker.LOGGER.info("[SMB] Deep Seas: пробоина {} ({}:{})",
                                pos, subId, plotPos);
                    }
                }
            }
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.warn("[SMB] Deep Seas mixin error: {}", t.toString());
        }
    }
}