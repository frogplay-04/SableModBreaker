package com.FrogPlay.sablemobbreaker.goal;

import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RamSubLevelGoal extends Goal {

    private final Mob mob;
    private final SubLevelTarget targetProvider;
    private int ramCooldown = 0;
    private int repathCooldown = 0;

    public RamSubLevelGoal(Mob mob, SubLevelTarget targetProvider) {
        this.mob = mob;
        this.targetProvider = targetProvider;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.mob.isInWater()) return false;
        // Только крупные мобы (ширина или высота > 3)
        if (this.mob.getBbWidth() <= 3.0F && this.mob.getBbHeight() <= 3.0F) return false;

        SubLevel target = this.targetProvider.getTargetSubLevel();
        if (target == null || target.isRemoved()) return false;

        if (SubLevelDamageHelper.isRagdoll(target)) return false;

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mob.isInWater()) return false;
        if (this.mob.getBbWidth() <= 3.0F && this.mob.getBbHeight() <= 3.0F) return false;

        SubLevel target = this.targetProvider.getTargetSubLevel();
        if (target == null || target.isRemoved()) return false;
        if (SubLevelDamageHelper.isRagdoll(target)) return false;

        return true;
    }

    @Override
    public void start() {
        this.ramCooldown = 0;
        this.repathCooldown = 0;
    }

    @Override
    public void tick() {
        SubLevel target = this.targetProvider.getTargetSubLevel();
        if (target == null) return;

        BoundingBox3dc box = target.boundingBox();
        double cx = (box.minX() + box.maxX()) / 2.0;
        double cy = (box.minY() + box.maxY()) / 2.0;
        double cz = (box.minZ() + box.maxZ()) / 2.0;

        this.mob.getLookControl().setLookAt(cx, cy, cz);

        if (--this.repathCooldown <= 0) {
            this.repathCooldown = 10;
            this.mob.getNavigation().moveTo(cx, cy, cz, 1.5D);
        }

        if (this.ramCooldown > 0) {
            this.ramCooldown--;
            return;
        }

        AABB mobBox = this.mob.getBoundingBox().inflate(0.6);
        boolean intersects = (mobBox.maxX >= box.minX() && mobBox.minX <= box.maxX())
                && (mobBox.maxY >= box.minY() && mobBox.minY <= box.maxY())
                && (mobBox.maxZ >= box.minZ() && mobBox.minZ <= box.maxZ());

        if (intersects) {
            Vec3 look = this.mob.getLookAngle();
            Vec3 hitPos = this.mob.position()
                    .add(look.scale(this.mob.getBbWidth() / 2.0))
                    .add(0, this.mob.getBbHeight() / 2.0, 0);

            SubLevelDamageHelper.damageBlock(this.mob.level(), target, hitPos, 20);

            Vec3 reverse = look.reverse().scale(1.8D);
            this.mob.setDeltaMovement(reverse.x, 0.4D, reverse.z);
            this.mob.hurtMarked = true;

            this.mob.level().playSound(null,
                    this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.8F);

            this.ramCooldown = 40;
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.ramCooldown = 0;
        this.repathCooldown = 0;
    }
}