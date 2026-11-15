package com.FrogPlay.sablemobbreaker.item;

import com.FrogPlay.sablemobbreaker.util.SubLevelDamageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PlasmaCutterItem extends Item {

    private static final int RADIUS = 5;

    public PlasmaCutterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        ItemStack oxygenTank = findOxygenTank(player);
        if (oxygenTank == null || OxygenTankItem.getAir(oxygenTank) <= 0) {
            return InteractionResultHolder.fail(stack);
        }

        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return InteractionResultHolder.pass(stack);

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        for (var subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            var box = subLevel.boundingBox();
            Vec3 center = new Vec3((box.minX() + box.maxX()) / 2, (box.minY() + box.maxY()) / 2, (box.minZ() + box.maxZ()) / 2);
            if (player.position().distanceToSqr(center) > RADIUS * RADIUS) continue;

            Vec3 hitPos = eyePos.add(lookVec.scale(RADIUS));
            if (SubLevelDamageHelper.damageBlockExact(level, subLevel, hitPos, 9999)) {
                OxygenTankItem.setAir(oxygenTank, OxygenTankItem.getAir(oxygenTank) - 10);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.FLAME, hitPos.x, hitPos.y, hitPos.z, 10, 0.1, 0.1, 0.1, 0.05);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.5f);
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    private ItemStack findOxygenTank(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof OxygenTankItem) return s;
        }
        return null;
    }
}