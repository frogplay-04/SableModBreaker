package com.FrogPlay.sablemobbreaker.mixin;

import com.FrogPlay.sablemobbreaker.util.BlockHealthManager;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker")
public class CompartmentTrackerMixin {

    /**
     * Перехватывает метод plugs(UUID), который возвращает набор пробоин.
     * Добавляет в этот набор все блоки с HP=0 из нашего BlockHealthManager.
     */
    @Inject(method = "plugs", at = @At("RETURN"), cancellable = true, remap = false)
    private static void sablemobbreaker$addOurPlugs(UUID id,
                                                    CallbackInfoReturnable<Set<BlockPos>> cir) {
        try {
            Set<BlockPos> original = cir.getReturnValue();
            Set<BlockPos> ourDeadBlocks = BlockHealthManager.getDeadBlocks(id);

            if (!ourDeadBlocks.isEmpty()) {
                Set<BlockPos> combined = new HashSet<>(original);
                combined.addAll(ourDeadBlocks);
                cir.setReturnValue(combined);
            }
        } catch (Throwable ignored) {
            // Deep Seas не установлен или метод не найден — пропускаем
        }
    }
}