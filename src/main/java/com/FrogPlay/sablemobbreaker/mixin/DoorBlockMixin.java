package com.FrogPlay.sablemobbreaker.mixin;

import com.FrogPlay.sablemobbreaker.util.WeldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DoorBlock.class)
public class DoorBlockMixin {
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void sablemobbreaker$preventRedstone(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving, CallbackInfo ci) {
        if (WeldManager.isWelded(level, pos)) {
            ci.cancel();
        }
    }
}