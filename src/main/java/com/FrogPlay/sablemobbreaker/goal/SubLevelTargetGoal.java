package com.FrogPlay.sablemobbreaker.goal;

import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class SubLevelTargetGoal extends Goal implements SubLevelTarget {
    private final Mob mob;
    private final double searchRadius;
    private SubLevel targetSubLevel;

    public SubLevelTargetGoal(Mob mob, double searchRadius) {
        this.mob = mob;
        this.searchRadius = searchRadius;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getType().getCategory() != MobCategory.MONSTER) return false;
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) return false;

        this.targetSubLevel = findNearestSubLevel();
        return this.targetSubLevel != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetSubLevel != null && !this.targetSubLevel.isRemoved() && !SubLevelDamageHelper.isRagdoll(this.targetSubLevel);
    }

    @Override
    public void stop() {
        this.targetSubLevel = null;
    }

    @Override
    public SubLevel getTargetSubLevel() {
        return this.targetSubLevel;
    }

    private SubLevel findNearestSubLevel() {
        Level level = this.mob.level();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        Vec3 mobPos = this.mob.position();
        double nearestDistSq = this.searchRadius * this.searchRadius;
        SubLevel nearest = null;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            if (SubLevelDamageHelper.isRagdoll(subLevel)) continue;

            BoundingBox3dc box = subLevel.boundingBox();
            double dx = Math.max(0, Math.max(box.minX() - mobPos.x, mobPos.x - box.maxX()));
            double dy = Math.max(0, Math.max(box.minY() - mobPos.y, mobPos.y - box.maxY()));
            double dz = Math.max(0, Math.max(box.minZ() - mobPos.z, mobPos.z - box.maxZ()));
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = subLevel;
            }
        }
        return nearest;
    }
}