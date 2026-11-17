package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.item.PlasmaCutterItem;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import com.FrogPlay.sablemobbreaker.util.WeldGeometry;
import com.FrogPlay.sablemobbreaker.util.WeldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.BitSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID)
public final class DoorWeldHandler {
    private static final int SAMPLES_PER_EDGE = 32;
    private static final double TRACE_RADIUS_SQR = 0.075 * 0.075;
    private static final double REACH = 6.0;
    private static final Map<UUID, TraceSession> SESSIONS = new ConcurrentHashMap<>();

    private static final class TraceSession {
        final String assemblyKey;
        final BitSet traced = new BitSet(4 * SAMPLES_PER_EDGE);
        final boolean welding;
        final Set<BlockPos> blocks;
        final AABB bounds;
        int missedTargetTicks;
        TraceSession(String key, boolean welding, Set<BlockPos> blocks, AABB bounds) {
            this.assemblyKey = key;
            this.welding = welding;
            this.blocks = Set.copyOf(blocks);
            this.bounds = bounds;
        }
    }

    private DoorWeldHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        ItemStack held = player.getMainHandItem();
        boolean welding = held.getItem() instanceof WeldingTorchItem && WeldingTorchItem.isOn(held);
        boolean cutting = held.getItem() instanceof PlasmaCutterItem && PlasmaCutterItem.isOn(held);
        if (!welding && !cutting) { SESSIONS.remove(player.getUUID()); return; }

        Level level = player.level();
        UUID id = player.getUUID();
        TraceSession session = SESSIONS.get(id);
        HitResult hit = player.pick(REACH, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            Set<BlockPos> assembly = WeldGeometry.findAssembly(level, hitPos);
            if (assembly.isEmpty()) { SESSIONS.remove(id); return; }
            boolean alreadyWelded = WeldManager.isWelded(level, hitPos);
            if ((welding && alreadyWelded) || (cutting && !alreadyWelded)) { SESSIONS.remove(id); return; }
            String key = level.dimension().location() + ":" + assembly.stream().mapToLong(BlockPos::asLong).min().orElse(0L);
            if (session == null || !session.assemblyKey.equals(key) || session.welding != welding) {
                session = new TraceSession(key, welding, assembly, WeldGeometry.bounds(level, assembly));
                SESSIONS.put(id, session);
            }
            session.missedTargetTicks = 0;
        } else {
            if (session == null || session.welding != welding || ++session.missedTargetTicks > 100) {
                SESSIONS.remove(id); return;
            }
        }

        Vec3 eye = player.getEyePosition();
        Vec3 rayEnd = eye.add(player.getLookAngle().scale(REACH));
        Vec3[][] edges = WeldGeometry.visibleContour(session.bounds, eye);
        for (int edge = 0; edge < edges.length; edge++) {
            for (int sample = 0; sample < SAMPLES_PER_EDGE; sample++) {
                Vec3 point = edges[edge][0].lerp(edges[edge][1], (sample + 0.5) / SAMPLES_PER_EDGE);
                if (distanceToSegmentSqr(point, eye, rayEnd) <= TRACE_RADIUS_SQR) {
                    session.traced.set(edge * SAMPLES_PER_EDGE + sample);
                }
            }
        }

        if (session.traced.cardinality() >= 4 * SAMPLES_PER_EDGE) {
            BlockPos soundPos = session.blocks.iterator().next();
            if (welding) {
                WeldManager.weld(level, session.blocks);
                level.playSound(null, soundPos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.3F);
            } else {
                WeldManager.unweld(level, session.blocks);
                level.playSound(null, soundPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            SESSIONS.remove(id);
        }
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-9) return point.distanceToSqr(start);
        double t = point.subtract(start).dot(segment) / lengthSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        return point.distanceToSqr(start.add(segment.scale(t)));
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();
        if (WeldManager.isWelded(level, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        boolean activeTool = (stack.getItem() instanceof WeldingTorchItem && WeldingTorchItem.isOn(stack))
                || (stack.getItem() instanceof PlasmaCutterItem && PlasmaCutterItem.isOn(stack));
        if (activeTool && WeldGeometry.isEligible(level.getBlockState(pos))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
