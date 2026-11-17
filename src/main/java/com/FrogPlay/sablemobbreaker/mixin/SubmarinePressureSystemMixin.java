package com.FrogPlay.sablemobbreaker.mixin;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem")
public class SubmarinePressureSystemMixin {

    @Inject(method = "onBlockBroken", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sablemobbreaker$onBlockBroken(BlockEvent.BreakEvent event, CallbackInfo ci) {
        try {
            BlockPos pos = event.getPos();
            if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

            SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null) return;

            for (var subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) continue;
                var subId = subLevel.getUniqueId();
                if (subId == null) continue;

                var localPos = subLevel.logicalPose().transformPositionInverse(Vec3.atCenterOf(pos));
                BlockPos plotPos = BlockPos.containing(localPos.x, localPos.y, localPos.z);

                if (BlockHealthManager.isDeadAt(subId, plotPos)) {
                    event.setCanceled(true);
                    ci.cancel();
                    return;
                }

                SubLevelDamageHelper.damageBlockExact(serverLevel, subLevel, Vec3.atCenterOf(pos), 9999);

                event.setCanceled(true);
                ci.cancel();
                return;
            }
        } catch (Throwable t) {
            SableMobBreaker.LOGGER.warn("[SMB] Deep Seas mixin error: {}", t.toString());
        }
    }
}