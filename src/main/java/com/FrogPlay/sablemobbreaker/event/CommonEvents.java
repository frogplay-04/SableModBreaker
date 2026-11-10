package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.goal.*;
import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import com.FrogPlay.sablemobbreaker.util.WeaponClassifier;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Set;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public class CommonEvents {

    private static final Set<ResourceLocation> FORCED_ATTACKERS = Set.of(
            ResourceLocation.fromNamespaceAndPath("alexscaves", "hullbreaker"),
            ResourceLocation.fromNamespaceAndPath("benssharks", "megalodon")
    );

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        boolean isMonster = mob.getType().getCategory() == MobCategory.MONSTER;
        boolean isForced = FORCED_ATTACKERS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
        boolean hasGun = WeaponClassifier.isHoldingScorchedGunsWeapon(mob) || WeaponClassifier.isHoldingRangedWeapon(mob);

        if (!isMonster && !isForced && !hasGun) return;

        boolean alreadyHasTarget = mob.targetSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof SubLevelTargetGoal);
        if (alreadyHasTarget) return;

        SubLevelTargetGoal targetGoal = new SubLevelTargetGoal(mob, 24.0D);
        mob.targetSelector.addGoal(3, targetGoal);

        boolean isBig = mob.getBbWidth() > 3.0F || mob.getBbHeight() > 3.0F;
        if (isBig) {
            mob.goalSelector.addGoal(2, new RamSubLevelGoal(mob, targetGoal));
        }

        if (WeaponClassifier.isRanged(mob) && mob instanceof PathfinderMob pathfinderMob) {
            mob.goalSelector.addGoal(2, new SubLevelRangedAttackGoal(pathfinderMob, 24.0D, 16.0D, 20));
        }

        mob.goalSelector.addGoal(3, new MeleeAttackSubLevelGoal(mob, 1.0D, targetGoal));
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Projectile projectile)) return;

        Level level = projectile.level();
        Vec3 currentPos = projectile.position();
        Vec3 movement = projectile.getDeltaMovement();

        AABB projectileBox = projectile.getBoundingBox().expandTowards(movement.x, movement.y, movement.z);

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            if (SubLevelDamageHelper.isRagdoll(subLevel)) continue;

            BoundingBox3dc box = subLevel.boundingBox();
            AABB slBox = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());

            if (projectileBox.intersects(slBox)) {
                int damage = calculateProjectileDamage(projectile);
                if (damage <= 0) continue;

                boolean hit = false;
                Vec3 hitPos = currentPos;
                double dist = movement.length();

                if (dist > 0) {
                    Vec3 step = movement.normalize().scale(0.2);
                    Vec3 pos = currentPos;
                    for (double d = 0; d <= dist; d += 0.2) {
                        if (SubLevelDamageHelper.damageBlock(level, subLevel, pos, damage)) {
                            hit = true;
                            hitPos = pos;
                            break;
                        }
                        pos = pos.add(step);
                    }
                } else {
                    hit = SubLevelDamageHelper.damageBlock(level, subLevel, currentPos, damage);
                }

                if (hit) {
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 8, 0.1, 0.1, 0.1, 0.2);
                    }
                    projectile.discard();
                    return;
                }
            }
        }
    }

    private static int calculateProjectileDamage(Projectile projectile) {
        float base = getBaseDamage(projectile);
        if (base <= 0) return 0;
        double w = projectile.getBbWidth();
        double h = projectile.getBbHeight();
        double volume = w * w * h;
        float sizeFactor = (float) Math.max(0.5, Math.min(4.0, volume * 8.0));
        double speed = projectile.getDeltaMovement().length();
        float speedFactor = (float) Math.max(0.5, Math.min(3.0, speed * 2.0));
        return Math.round(base * sizeFactor * speedFactor);
    }

    private static float getBaseDamage(Projectile projectile) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        String namespace = id.getNamespace();

        if (namespace.equals("scguns")) return 5.0F;
        if (namespace.equals("createbigcannons")) return 20.0F;

        if (projectile instanceof net.minecraft.world.entity.projectile.ThrownTrident) return 4.0F;
        if (projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow) return 2.0F;

        if (projectile instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile) return 6.0F;
        if (projectile instanceof net.minecraft.world.entity.projectile.Snowball) return 1.0F;

        return 3.0F;
    }
}