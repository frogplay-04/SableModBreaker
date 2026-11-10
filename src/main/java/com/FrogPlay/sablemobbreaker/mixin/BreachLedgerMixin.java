package com.FrogPlay.sablemobbreaker.mixin;

import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.compartment.BreachLedger")
public class BreachLedgerMixin {

    @Inject(method = "hasBreach", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sablemobbreaker$checkOurBreach(UUID subId, BlockPos pos,
                                                       CallbackInfoReturnable<Boolean> cir) {
        try {
            if (BlockHealthManager.isDeadAt(subId, pos)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
            // BreachLedger ещё не инициализирован — пропускаем
        }
    }
}