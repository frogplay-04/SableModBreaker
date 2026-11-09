package com.FrogPlay.sablemobbreaker.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
public class LevelAabbMixin {

    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void sablemobbreaker$preventHugeAabb(Entity entity, AABB area, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
        // Если хитбокс превышает 100 блоков по любой оси — это баг Sable/KCC.
        // Возвращаем пустой список, чтобы предотвратить зависание и спам в логах.
        if (area.getXsize() > 100.0 || area.getYsize() > 100.0 || area.getZsize() > 100.0) {
            cir.setReturnValue(Collections.emptyList());
        }
    }
}