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
    private static final int SAMPLES_PER_EDGE = 12;
    private static final double TRACE_RADIUS_SQR = 0.30 * 0.30;
    private static final double REACH = 6.0;
    private static final Map<UUID, TraceSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Set<UUID> RIGHT_USE = ConcurrentHashMap.newKeySet();

    public static void setRightUse(UUID playerId, boolean pressed) {
        if (pressed) RIGHT_USE.add(playerId);
        else {
            RIGHT_USE.remove(playerId);
            SESSIONS.remove(playerId);
        }
    }

    public static boolean isRightUse(UUID playerId) {
        return RIGHT_USE.contains(playerId);
    }

    private static final class TraceSession {
        final String assemblyKey;
        final BitSet traced = new BitSet(4 * SAMPLES_PER_EDGE);
        final boolean welding;
        final Set<BlockPos> blocks;
        final AABB bounds;
        final Vec3[][] edges;
        int missedTargetTicks;

        TraceSession(String assemblyKey, boolean welding, Set<BlockPos> blocks, AABB bounds, Vec3 eye) {
            this.assemblyKey = assemblyKey;
            this.welding = welding;
            this.blocks = Set.copyOf(blocks);
            this.bounds = bounds;
            this.edges = WeldGeometry.visibleFaceEdges(bounds, eye);
        }
    }

    private DoorWeldHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        ItemStack held = player.getMainHandItem();
        boolean rightUse = isRightUse(player.getUUID());
        boolean welding = rightUse && held.getItem() instanceof WeldingTorchItem;
        boolean cutting = rightUse && held.getItem() instanceof PlasmaCutterItem;
        if (!welding && !cutting) {
            SESSIONS.remove(player.getUUID());
            return;
        }

        HitResult hit = player.pick(REACH, 1.0F, false);
        Level level = player.level();
        UUID id = player.getUUID();
        TraceSession session = SESSIONS.get(id);

        if (hit instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            Set<BlockPos> assembly = WeldGeometry.findAssembly(level, hitPos);
            if (assembly.isEmpty()) {
                SESSIONS.remove(id);
                return;
            }

            boolean alreadyWelded = WeldManager.isWelded(level, hitPos);
            if ((welding && alreadyWelded) || (cutting && !alreadyWelded)) {
                SESSIONS.remove(id);
                return;
            }

            String assemblyKey = level.dimension().location() + ":" + assembly.stream()
                    .mapToLong(BlockPos::asLong).min().orElse(0L);
            if (session == null || !session.assemblyKey.equals(assemblyKey) || session.welding != welding) {
                session = new TraceSession(assemblyKey, welding, assembly, WeldGeometry.bounds(level, assembly), player.getEyePosition());
                SESSIONS.put(id, session);
            }
            session.missedTargetTicks = 0;
        } else {
            if (session == null || session.welding != welding || ++session.missedTargetTicks > 20) {
                SESSIONS.remove(id);
                return;
            }
        }

        Vec3 eye = player.getEyePosition();
        Vec3 rayEnd = eye.add(player.getLookAngle().scale(REACH));
        Vec3[][] edges = session.edges;
        for (int edge = 0; edge < edges.length; edge++) {
            Vec3 from = edges[edge][0];
            Vec3 to = edges[edge][1];
            for (int sample = 0; sample < SAMPLES_PER_EDGE; sample++) {
                double t = (sample + 0.5) / SAMPLES_PER_EDGE;
                Vec3 point = from.lerp(to, t);
                if (distanceToSegmentSqr(point, eye, rayEnd) <= TRACE_RADIUS_SQR) {
                    session.traced.set(edge * SAMPLES_PER_EDGE + sample);
                }
            }
        }

        if (session.traced.cardinality() >= (int) Math.ceil(4 * SAMPLES_PER_EDGE * 0.60D)) {
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

        boolean toolHeld = stack.getItem() instanceof WeldingTorchItem
                || stack.getItem() instanceof PlasmaCutterItem;
        boolean cutterUnwelding = stack.getItem() instanceof PlasmaCutterItem
                && isRightUse(event.getEntity().getUUID());

        if (toolHeld && event.getEntity().isShiftKeyDown()) return;

        if (WeldManager.isWelded(level, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(cutterUnwelding ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            return;
        }

        if (toolHeld && !event.getEntity().isShiftKeyDown()
                && WeldGeometry.isEligible(level.getBlockState(pos))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
