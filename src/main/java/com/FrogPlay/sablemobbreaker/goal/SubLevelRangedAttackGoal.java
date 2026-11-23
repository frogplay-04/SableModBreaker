package com.FrogPlay.sablemobbreaker.goal;

import com.FrogPlay.sablemobbreaker.util.SubLevelTargetHelper;
import com.FrogPlay.sablemobbreaker.util.WeaponClassifier;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.FrogPlay.sablemobbreaker.block.ModBlocks;
import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class SubLevelRangedAttackGoal extends Goal {

    private final PathfinderMob mob;
    private final double searchRadius;
    private final double attackRange;
    private final int fireCooldownTicks;
    private int cooldown = 0;
    private int retargetTimer = 0;

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;

    public SubLevelRangedAttackGoal(PathfinderMob mob, double searchRadius, double attackRange, int fireCooldownTicks) {
        this.mob = mob;
        this.searchRadius = searchRadius;
        this.attackRange = attackRange;
        this.fireCooldownTicks = fireCooldownTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!WeaponClassifier.isRanged(this.mob)) return false;
        if (!(this.mob.level() instanceof ServerLevel level)) return false;

        Optional<SubLevelTargetHelper.Found> found = SubLevelTargetHelper.findNearestBlock(level, this.mob.position(), this.searchRadius);
        if (found.isEmpty()) return false;

        this.targetSubLevel = found.get().subLevel();
        this.targetLocalPos = found.get().localPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetSubLevel == null || this.targetLocalPos == null || this.targetSubLevel.isRemoved()) return false;
        Vec3 target = SubLevelTargetHelper.localToWorld(this.targetSubLevel, this.targetLocalPos);
        return this.mob.position().distanceToSqr(target) <= this.searchRadius * this.searchRadius;
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.retargetTimer = 0;
    }

    @Override
    public void stop() {
        this.targetSubLevel = null;
        this.targetLocalPos = null;
        this.mob.getNavigation().stop();

    }

    @Override
    public void tick() {
        if (this.targetSubLevel == null || this.targetLocalPos == null) return;

        Vec3 worldPos = SubLevelTargetHelper.localToWorld(this.targetSubLevel, this.targetLocalPos);
        double distSqr = this.mob.position().distanceToSqr(worldPos);

        this.mob.getLookControl().setLookAt(worldPos.x, worldPos.y, worldPos.z);

        if (distSqr > this.attackRange * this.attackRange) {
            this.mob.getNavigation().moveTo(worldPos.x, worldPos.y, worldPos.z, 1.0);
            return;
        }

        this.mob.getNavigation().stop();

        if (!SubLevelTargetHelper.hasLineOfSight(this.mob, worldPos)) return;

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        if (--this.retargetTimer <= 0) {
            this.retargetTimer = 20;
            if (isTargetBlockGone()) {
                retarget();
                return;
            }
        }

        this.cooldown = this.fireCooldownTicks;
        fireAttack(worldPos);
    }

    private void fireAttack(Vec3 targetPos) {
        if (WeaponClassifier.isHoldingScorchedGunsWeapon(this.mob)) {
            fireScgunsProjectile(targetPos);
            this.mob.swing(InteractionHand.MAIN_HAND);
            return;
        }

        if (this.mob instanceof RangedAttackMob && (WeaponClassifier.isHoldingRangedWeapon(this.mob)
                || WeaponClassifier.isNaturalShooter(this.mob) || this.mob instanceof Pillager)) {
            ItemStack firingWeapon = this.mob.getMainHandItem();
            if (firingWeapon.isEmpty()) {
                firingWeapon = this.mob.getOffhandItem();
            }

            if (firingWeapon.isEmpty() || !(firingWeapon.getItem() instanceof ProjectileWeaponItem)) {
                firingWeapon = new ItemStack(Items.BOW);
            }

            Arrow arrow = new Arrow(
                    this.mob.level(),
                    this.mob,
                    new ItemStack(Items.ARROW),
                    firingWeapon.copy()
            );
            Vec3 origin = this.mob.getEyePosition().add(this.mob.getLookAngle().scale(0.35D));
            Vec3 direction = targetPos.subtract(origin).normalize();
            arrow.setPos(origin.x, origin.y, origin.z);
            arrow.shoot(direction.x, direction.y, direction.z, 2.0F, 0.0F);
            this.mob.level().addFreshEntity(arrow);
            this.mob.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void fireScgunsProjectile(Vec3 targetPos) {
        try {
            net.minecraft.world.item.ItemStack weapon = this.mob.getMainHandItem();
            if (weapon.isEmpty()) weapon = this.mob.getOffhandItem();
            ClassLoader loader = this.mob.getClass().getClassLoader();
            Class<?> gunItemClass = Class.forName("top.ribs.scguns.item.GunItem", false, loader);
            if (!gunItemClass.isInstance(weapon.getItem())) return;

            Class<?> gunClass = Class.forName("top.ribs.scguns.common.Gun", false, loader);
            java.lang.reflect.Method canShoot = gunClass.getMethod("canShoot", net.minecraft.world.item.ItemStack.class);
            if (!Boolean.TRUE.equals(canShoot.invoke(null, weapon))) return;
            Object gun = gunItemClass.getMethod("getModifiedGun", net.minecraft.world.item.ItemStack.class)
                    .invoke(weapon.getItem(), weapon);
            if (gun == null) return;

            Class<?> entitiesClass = Class.forName("top.ribs.scguns.init.ModEntities", false, loader);
            Object holder = entitiesClass.getField("PROJECTILE").get(null);
            Object entityType = holder.getClass().getMethod("get").invoke(holder);
            Class<?> projectileClass = Class.forName("top.ribs.scguns.entity.projectile.ProjectileEntity", false, loader);
            java.lang.reflect.Constructor<?> constructor = projectileClass.getConstructor(
                    net.minecraft.world.entity.EntityType.class,
                    net.minecraft.world.level.Level.class,
                    net.minecraft.world.entity.LivingEntity.class,
                    net.minecraft.world.item.ItemStack.class,
                    gunItemClass,
                    gunClass);
            Object projectile = constructor.newInstance(entityType, this.mob.level(), this.mob, weapon, weapon.getItem(), gun);
            if (!(projectile instanceof net.minecraft.world.entity.Entity entity)) return;

            Vec3 origin = this.mob.getEyePosition().add(this.mob.getLookAngle().scale(0.35D));
            Vec3 direction = targetPos.subtract(origin).normalize();
            Object projectileData = gunClass.getMethod("getProjectile").invoke(gun);
            double speed = 3.0D;
            if (projectileData != null) {
                Object rawSpeed = projectileData.getClass().getMethod("getSpeed").invoke(projectileData);
                if (rawSpeed instanceof Number number) speed = Math.max(1.0D, Math.min(8.0D, number.doubleValue()));
            }
            entity.setPos(origin.x, origin.y, origin.z);
            entity.setDeltaMovement(direction.scale(speed));
            entity.setYRot((float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0D));
            entity.setXRot((float) -Math.toDegrees(Math.asin(direction.y)));
            this.mob.level().addFreshEntity(entity);
        } catch (Throwable ignored) {
        }
    }

    private boolean isTargetBlockGone() {
        if (this.targetSubLevel == null || this.targetLocalPos == null) return true;
        var chunk = this.targetSubLevel.getPlot().getChunk(new net.minecraft.world.level.ChunkPos(this.targetLocalPos));
        if (chunk == null) return true;
        var state = chunk.getBlockState(this.targetLocalPos);
        return state.isAir() || state.is(ModBlocks.BROKEN_BLOCK.get())
                || SubLevelDamageHelper.isIgnoredBlock(state);
    }

    private void retarget() {
        this.targetSubLevel = null;
        this.targetLocalPos = null;
        if (!(this.mob.level() instanceof ServerLevel level)) return;

        var found = SubLevelTargetHelper.findNearestBlock(level, this.mob.position(), this.searchRadius);
        if (found.isPresent()) {
            this.targetSubLevel = found.get().subLevel();
            this.targetLocalPos = found.get().localPos();
        }
    }
}