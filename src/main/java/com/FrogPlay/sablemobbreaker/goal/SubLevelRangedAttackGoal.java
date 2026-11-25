package com.FrogPlay.sablemobbreaker.goal;

import com.FrogPlay.sablemobbreaker.block.ModBlocks;
import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import com.FrogPlay.sablemobbreaker.util.SubLevelTargetHelper;
import com.FrogPlay.sablemobbreaker.util.WeaponClassifier;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class SubLevelRangedAttackGoal extends Goal {
    private final Mob mob;
    private final double searchRadius;
    private final double attackRange;
    private final int fireCooldownTicks;
    private final boolean nativeRangedAi;

    private int cooldown;
    private int retargetTimer;
    private int drawTicks;
    private boolean drawing;
    private InteractionHand firingHand = InteractionHand.MAIN_HAND;
    private InteractionHand temporaryAmmoHand;
    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;
    private ArmorStand virtualTarget;

    public SubLevelRangedAttackGoal(Mob mob, double searchRadius, double attackRange, int fireCooldownTicks) {
        this.mob = mob;
        this.searchRadius = searchRadius;
        this.attackRange = attackRange;
        this.fireCooldownTicks = fireCooldownTicks;
        this.nativeRangedAi = WeaponClassifier.hasNativeRangedAi(mob);
        // Native ranged goals (Blaze/Ghast/Skeleton/etc.) must be allowed to run their
        // own movement, aiming, draw, firing and animation logic.
        this.setFlags(nativeRangedAi ? EnumSet.noneOf(Flag.class) : EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!WeaponClassifier.isRanged(mob)) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        Optional<SubLevelTargetHelper.Found> found =
                SubLevelTargetHelper.findNearestBlock(level, mob.position(), searchRadius);
        if (found.isEmpty()) return false;
        targetSubLevel = found.get().subLevel();
        targetLocalPos = found.get().localPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetSubLevel == null || targetLocalPos == null || targetSubLevel.isRemoved()) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.getTarget() != virtualTarget) return false;
        Vec3 target = SubLevelTargetHelper.localToWorld(targetSubLevel, targetLocalPos);
        return mob.position().distanceToSqr(target) <= searchRadius * searchRadius;
    }

    @Override
    public void start() {
        cooldown = 0;
        retargetTimer = 0;
        drawTicks = 0;
        drawing = false;
        if (nativeRangedAi || mob instanceof net.minecraft.world.entity.monster.RangedAttackMob) {

            virtualTarget = EntityType.ARMOR_STAND.create(mob.level());
            if (virtualTarget != null) {
                virtualTarget.setInvisible(true);
                virtualTarget.setNoGravity(true);
                virtualTarget.setNoBasePlate(true);
                if (nativeRangedAi) mob.setTarget(virtualTarget);
                moveVirtualTarget();
            }
        }
    }

    @Override
    public void stop() {
        if (mob.getTarget() == virtualTarget) mob.setTarget(null);
        if (virtualTarget != null) {
            virtualTarget.discard();
            virtualTarget = null;
        }
        if (drawing && mob.isUsingItem()) mob.releaseUsingItem();
        clearTemporaryAmmo();
        drawing = false;
        targetSubLevel = null;
        targetLocalPos = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetSubLevel == null || targetLocalPos == null) return;
        if (--retargetTimer <= 0) {
            retargetTimer = 20;
            if (isTargetBlockGone()) {
                retarget();
                if (targetSubLevel == null) return;
            }
        }

        Vec3 targetPos = SubLevelTargetHelper.localToWorld(targetSubLevel, targetLocalPos);
        if (nativeRangedAi) {
            if (virtualTarget == null) return;
            if (mob.getTarget() != null && mob.getTarget() != virtualTarget && mob.getTarget().isAlive()) return;
            mob.setTarget(virtualTarget);
            virtualTarget.setPos(targetPos.x, targetPos.y, targetPos.z);
            virtualTarget.setYRot(mob.getYRot());
            return;
        }

        double distSqr = mob.position().distanceToSqr(targetPos);
        mob.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z);
        if (distSqr > attackRange * attackRange) {
            mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0D);
            return;
        }
        mob.getNavigation().stop();
        if (!SubLevelTargetHelper.hasLineOfSight(mob, targetPos)) return;

        if (mob instanceof net.minecraft.world.entity.monster.RangedAttackMob rangedMob) {
            if (virtualTarget == null) return;
            virtualTarget.setPos(targetPos.x, targetPos.y, targetPos.z);
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            rangedMob.performRangedAttack(virtualTarget, 1.0F);
            mob.swing(InteractionHand.MAIN_HAND);
            cooldown = fireCooldownTicks;
            return;
        }

        if (drawing) {
            drawTicks++;
            int required = mob.getUseItem().getItem() instanceof TridentItem ? 10 : 20;
            if (drawTicks >= required) {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                clearTemporaryAmmo();
                drawing = false;
                cooldown = fireCooldownTicks;
            }
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }
        fireHeldVanillaWeapon(targetPos);
    }

    private void fireHeldVanillaWeapon(Vec3 targetPos) {
        ItemStack main = mob.getMainHandItem();
        firingHand = !main.isEmpty() && WeaponClassifier.isRangedWeapon(main)
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack weapon = mob.getItemInHand(firingHand);

        if (weapon.getItem() instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(weapon)) {
                weapon.set(DataComponents.CHARGED_PROJECTILES,
                        ChargedProjectiles.of(new ItemStack(Items.ARROW)));
            }
            Vec3 direction = targetPos.subtract(mob.getEyePosition()).normalize();
            float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0D);
            float pitch = (float) -Math.toDegrees(Math.asin(direction.y));
            mob.setYRot(yaw);
            mob.setXRot(pitch);
            ((CrossbowItem) weapon.getItem()).performShooting(
                    mob.level(), mob, firingHand, weapon, 3.15F, 1.0F, null);
            mob.swing(firingHand);
            cooldown = fireCooldownTicks;
            return;
        }

        if (weapon.getItem() instanceof BowItem) {
            InteractionHand ammoHand = firingHand == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (mob.getItemInHand(ammoHand).isEmpty()) {
                mob.setItemInHand(ammoHand, new ItemStack(Items.ARROW));
                temporaryAmmoHand = ammoHand;
            }
            if (!mob.getItemInHand(ammoHand).isEmpty()) {
                mob.startUsingItem(firingHand);
                drawing = true;
                drawTicks = 0;
            }
            return;
        }

        if (weapon.getItem() instanceof TridentItem) {
            mob.startUsingItem(firingHand);
            drawing = true;
            drawTicks = 0;
            return;
        }

        cooldown = fireCooldownTicks;
    }

    private void clearTemporaryAmmo() {
        if (temporaryAmmoHand != null) {
            mob.setItemInHand(temporaryAmmoHand, ItemStack.EMPTY);
            temporaryAmmoHand = null;
        }
    }

    private void moveVirtualTarget() {
        if (virtualTarget == null || targetSubLevel == null || targetLocalPos == null) return;
        Vec3 pos = SubLevelTargetHelper.localToWorld(targetSubLevel, targetLocalPos);
        virtualTarget.setPos(pos.x, pos.y, pos.z);
    }

    private boolean isTargetBlockGone() {
        if (targetSubLevel == null || targetLocalPos == null) return true;
        var chunk = targetSubLevel.getPlot().getChunk(new net.minecraft.world.level.ChunkPos(targetLocalPos));
        if (chunk == null) return true;
        var state = chunk.getBlockState(targetLocalPos);
        return state.isAir() || state.is(ModBlocks.BROKEN_BLOCK.get())
                || SubLevelDamageHelper.isIgnoredBlock(state);
    }

    private void retarget() {
        targetSubLevel = null;
        targetLocalPos = null;
        if (!(mob.level() instanceof ServerLevel level)) return;
        var found = SubLevelTargetHelper.findNearestBlock(level, mob.position(), searchRadius);
        if (found.isPresent()) {
            targetSubLevel = found.get().subLevel();
            targetLocalPos = found.get().localPos();
            moveVirtualTarget();
        }
    }
}
