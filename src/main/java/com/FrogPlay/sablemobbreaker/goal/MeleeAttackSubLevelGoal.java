package com.FrogPlay.sablemobbreaker.goal;

import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import com.FrogPlay.sablemobbreaker.util.WeaponClassifier;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class MeleeAttackSubLevelGoal extends Goal {

    private final Mob mob;
    private final double speedModifier;
    private final SubLevelTarget targetProvider;

    private int pathRecalcCooldown = 0;
    private int attackCooldown = 0;

    public MeleeAttackSubLevelGoal(Mob mob, double speedModifier, SubLevelTarget targetProvider) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.targetProvider = targetProvider;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (WeaponClassifier.isRanged(mob)) return false;

        SubLevel target = this.targetProvider.getTargetSubLevel();
        if (target == null || target.isRemoved()) return false;
        if (SubLevelDamageHelper.isRagdoll(target)) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.pathRecalcCooldown = 0;
        this.attackCooldown = 0;
        SubLevel target = this.targetProvider.getTargetSubLevel();
        if (target != null) {
            org.joml.Vector3d pos = target.logicalPose().position();
            this.mob.getNavigation().moveTo(pos.x, pos.y, pos.z, this.speedModifier);
        }
    }

    @Override
    public void tick() {
        SubLevel target = this.targetProvider.getTargetSubLevel();
        if (target == null) return;

        BoundingBox3dc box = target.boundingBox();
        AABB mobAABB = this.mob.getBoundingBox();

        double dx = Math.max(0, Math.max(box.minX() - mobAABB.maxX, mobAABB.minX - box.maxX()));
        double dy = Math.max(0, Math.max(box.minY() - mobAABB.maxY, mobAABB.minY - box.maxY()));
        double dz = Math.max(0, Math.max(box.minZ() - mobAABB.maxZ, mobAABB.minZ - box.maxZ()));
        double distSq = dx * dx + dy * dy + dz * dz;

        double mobCx = (mobAABB.minX + mobAABB.maxX) / 2.0;
        double mobCy = (mobAABB.minY + mobAABB.maxY) / 2.0;
        double mobCz = (mobAABB.minZ + mobAABB.maxZ) / 2.0;

        double targetX = Mth.clamp(mobCx, box.minX(), box.maxX());
        double targetY = Mth.clamp(mobCy, box.minY(), box.maxY());
        double targetZ = Mth.clamp(mobCz, box.minZ(), box.maxZ());

        this.mob.getLookControl().setLookAt(targetX, targetY, targetZ);

        if (--this.pathRecalcCooldown <= 0) {
            this.pathRecalcCooldown = 10;
            if (distSq > 4.0D) {
                this.mob.getNavigation().moveTo(targetX, targetY, targetZ, this.speedModifier);
            } else {
                this.mob.getNavigation().stop();
            }
        }

        if (distSq <= 4.0D) {
            if (--this.attackCooldown <= 0) {
                this.attackCooldown = 20;

                Vec3 eyePos = this.mob.getEyePosition();
                Vec3 lookVec = this.mob.getLookAngle();
                Vec3 hitPos = eyePos.add(lookVec.scale(1.5));

                if (SubLevelDamageHelper.isBlockDead(this.mob.level(), target, hitPos)) {
                    Vec3 alt = findAlivePoint(target, hitPos);
                    if (alt != null) {
                        hitPos = alt;
                    } else {
                        double rx = targetX + (this.mob.getRandom().nextDouble() - 0.5) * 6;
                        double rz = targetZ + (this.mob.getRandom().nextDouble() - 0.5) * 6;
                        this.mob.getNavigation().moveTo(rx, targetY, rz, this.speedModifier);
                        return;
                    }
                }

                SubLevelDamageHelper.damageBlock(this.mob.level(), target, hitPos, 5);

                this.mob.swing(InteractionHand.MAIN_HAND);
                this.mob.level().playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                        net.minecraft.sounds.SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
            }
        }
    }

    private Vec3 findAlivePoint(SubLevel target, Vec3 base) {
        for (int r = 1; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        Vec3 candidate = new Vec3(base.x + dx, base.y + dy, base.z + dz);
                        if (!SubLevelDamageHelper.isBlockDead(this.mob.level(), target, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.attackCooldown = 0;
        this.pathRecalcCooldown = 0;
    }
}