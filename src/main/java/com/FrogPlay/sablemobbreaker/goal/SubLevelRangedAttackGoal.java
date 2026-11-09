package com.FrogPlay.sablemobbreaker.goal;

import com.FrogPlay.sablemobbreaker.util.SubLevelTargetHelper;
import com.FrogPlay.sablemobbreaker.util.WeaponClassifier;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.decoration.ArmorStand;
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

    // Реальный маркер в мире для арбалетчиков и Scorched Guns
    private ArmorStand ghostTarget;

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
        return this.targetSubLevel != null && this.targetLocalPos != null && !this.targetSubLevel.isRemoved();
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

        if (this.ghostTarget != null) {
            this.ghostTarget.discard();
            this.ghostTarget = null;
        }
        if (this.mob.getTarget() != null && this.mob.getTarget().isRemoved()) {
            this.mob.setTarget(null);
        }
    }

    @Override
    public void tick() {
        if (this.targetSubLevel == null || this.targetLocalPos == null) return;

        Vec3 worldPos = SubLevelTargetHelper.localToWorld(this.targetSubLevel, this.targetLocalPos);
        double distSqr = this.mob.position().distanceToSqr(worldPos);

        // Наводим взгляд моба на блок
        this.mob.getLookControl().setLookAt(worldPos.x, worldPos.y, worldPos.z);

        // Если далеко — идем ближе
        if (distSqr > this.attackRange * this.attackRange) {
            this.mob.getNavigation().moveTo(worldPos.x, worldPos.y, worldPos.z, 1.0);
            return;
        }

        this.mob.getNavigation().stop();

        // Проверка видимости (чтобы не стреляли сквозь стены)
        if (!SubLevelTargetHelper.hasLineOfSight(this.mob, worldPos)) return;

        // Кулдаун выстрела
        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        // Ищем новую цель, если эту сломали
        if (--this.retargetTimer <= 0) {
            this.retargetTimer = 20;
            if (isTargetBlockGone()) {
                retarget();
                return;
            }
        }

        // Стреляем!
        this.cooldown = this.fireCooldownTicks;
        fireAttack(worldPos);
    }

    private void fireAttack(Vec3 targetPos) {
        // 1. Арбалетчики (Pillager) и Scorched Guns используют сложный ИИ,
        // поэтому им нужна реальная сущность в мире.
        if (this.mob instanceof Pillager || WeaponClassifier.isHoldingScorchedGunsWeapon(this.mob)) {
            if (ghostTarget == null || ghostTarget.isRemoved()) {
                ghostTarget = new ArmorStand(this.mob.level(), targetPos.x, targetPos.y, targetPos.z);
                ghostTarget.setInvisible(true);
                ghostTarget.setInvulnerable(true);
                ghostTarget.setNoGravity(true);
                ghostTarget.setSilent(true);
                ghostTarget.noPhysics = true;
                this.mob.level().addFreshEntity(ghostTarget); // Реальный спавн для ИИ
            }
            ghostTarget.setPos(targetPos.x, targetPos.y, targetPos.z);
            ghostTarget.setOldPosAndRot();

            // Даем цель, ИИ моба сам натянет арбалет и выстрелит
            if (this.mob.getTarget() != ghostTarget) {
                this.mob.setTarget(ghostTarget);
            }
            return;
        }

        // 2. Обычные стрелки (Скелеты, Блейзы) и врожденные стрелки
        if (this.mob instanceof RangedAttackMob rangedMob) {
            // Защита от краша "Invalid weapon firing an arrow" (взер-скелет с мечом)
            boolean hasValidWeapon = WeaponClassifier.isHoldingRangedWeapon(this.mob) || WeaponClassifier.isNaturalShooter(this.mob);
            if (hasValidWeapon) {
                // Создаем цель ТОЛЬКО В ПАМЯТИ (без addFreshEntity)
                LivingEntity virtualTarget = new ArmorStand(this.mob.level(), targetPos.x, targetPos.y, targetPos.z);
                rangedMob.performRangedAttack(virtualTarget, 1.0F);
                this.mob.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    private boolean isTargetBlockGone() {
        if (this.targetSubLevel == null || this.targetLocalPos == null) return true;
        var chunk = this.targetSubLevel.getPlot().getChunk(new net.minecraft.world.level.ChunkPos(this.targetLocalPos));
        return chunk == null || chunk.getBlockState(this.targetLocalPos).isAir();
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