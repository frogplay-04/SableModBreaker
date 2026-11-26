package com.FrogPlay.sablemobbreaker.event;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.item.PlasmaCutterItem;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import com.FrogPlay.sablemobbreaker.util.WeldGeometry;
import com.FrogPlay.sablemobbreaker.util.WeldManager;
import com.FrogPlay.sablemobbreaker.util.SubLevelRaycast;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final double TRACE_RADIUS_SQR = 0.40 * 0.40;
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
        final SubLevel subLevel;
        int missedTargetTicks;

        TraceSession(String assemblyKey, boolean welding, Set<BlockPos> blocks,
                     AABB bounds, Vec3 eye, SubLevel subLevel) {
            this.assemblyKey = assemblyKey;
            this.welding = welding;
            this.blocks = Set.copyOf(blocks);
            this.bounds = bounds;
            this.edges = WeldGeometry.visibleFaceEdges(bounds, eye);
            this.subLevel = subLevel;
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

        Level level = player.level();
        UUID id = player.getUUID();
        TraceSession session = SESSIONS.get(id);

        Vec3 worldEye = player.getEyePosition();
        Vec3 worldDirection = player.getLookAngle();
        SubLevelRaycast.Hit subHit = SubLevelRaycast.trace(
                level, worldEye, worldDirection, REACH,
                state -> !state.isAir()
        );

        HitResult vanillaHit = player.pick(REACH, 1.0F, false);
        double vanillaDistance = vanillaHit instanceof BlockHitResult blockHit
                ? worldEye.distanceTo(blockHit.getLocation()) : Double.MAX_VALUE;

        SubLevel targetSubLevel = null;
        BlockPos targetPos = null;
        if (subHit != null && subHit.distance() <= vanillaDistance + 0.20D) {
            targetSubLevel = subHit.subLevel();
            targetPos = subHit.localPos();
        } else if (vanillaHit instanceof BlockHitResult blockHit) {
            targetPos = blockHit.getBlockPos();
        }

        if (targetPos != null) {
            BlockState targetState = WeldGeometry.getBlockState(level, targetSubLevel, targetPos);
            if (!WeldGeometry.isEligible(targetState)) {
                SESSIONS.remove(id);
                return;
            }

            Set<BlockPos> assembly = WeldGeometry.findAssembly(level, targetSubLevel, targetPos);
            if (assembly.isEmpty()) {
                SESSIONS.remove(id);
                return;
            }

            boolean alreadyWelded = WeldManager.isWelded(level, targetPos, targetState);
            if ((welding && alreadyWelded) || (cutting && !alreadyWelded)) {
                SESSIONS.remove(id);
                return;
            }

            String subLevelKey = targetSubLevel == null ? "world" : targetSubLevel.getUniqueId().toString();
            String assemblyKey = level.dimension().location() + ":" + subLevelKey + ":"
                    + assembly.stream().mapToLong(BlockPos::asLong).min().orElse(0L);

            Vec3 localEye = targetSubLevel == null
                    ? worldEye
                    : targetSubLevel.logicalPose().transformPositionInverse(worldEye);

            if (session == null || !session.assemblyKey.equals(assemblyKey)
                    || session.welding != welding) {
                session = new TraceSession(
                        assemblyKey, welding, assembly,
                        WeldGeometry.bounds(level, targetSubLevel, assembly),
                        localEye, targetSubLevel);
                SESSIONS.put(id, session);
            }
            session.missedTargetTicks = 0;
        } else {
            if (session == null || session.welding != welding || ++session.missedTargetTicks > 20) {
                SESSIONS.remove(id);
                return;
            }
        }

        Vec3 eye = session.subLevel == null
                ? worldEye
                : session.subLevel.logicalPose().transformPositionInverse(worldEye);
        Vec3 worldEnd = worldEye.add(worldDirection.scale(REACH));
        Vec3 rayEnd = session.subLevel == null
                ? worldEnd
                : session.subLevel.logicalPose().transformPositionInverse(worldEnd);

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

        if (session.traced.cardinality() >= (int) Math.ceil(4 * SAMPLES_PER_EDGE * 0.50D)) {
            BlockPos soundPos = session.blocks.iterator().next();
            Vec3 soundLocation = new Vec3(soundPos.getX() + 0.5D, soundPos.getY() + 0.5D, soundPos.getZ() + 0.5D);
            if (session.subLevel != null) {
                soundLocation = session.subLevel.logicalPose().transformPosition(soundLocation);
            }
            if (welding) {
                WeldManager.weld(level, session.blocks);
                level.playSound(null, soundLocation.x, soundLocation.y, soundLocation.z,
                        SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.3F);
            } else {
                WeldManager.unweld(level, session.blocks);
                level.playSound(null, soundLocation.x, soundLocation.y, soundLocation.z,
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
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
        ItemStack stack = event.getItemStack();
        boolean toolHeld = stack.getItem() instanceof WeldingTorchItem
                || stack.getItem() instanceof PlasmaCutterItem;
        boolean cutterUnwelding = stack.getItem() instanceof PlasmaCutterItem
                && isRightUse(event.getEntity().getUUID());

        if (toolHeld && event.getEntity().isShiftKeyDown()) return;

        SubLevelRaycast.Hit subHit = SubLevelRaycast.trace(
                level, event.getEntity().getEyePosition(), event.getEntity().getLookAngle(), REACH,
                state -> !state.isAir()
        );

        BlockPos targetPos = subHit == null ? event.getPos() : subHit.localPos();
        BlockState targetState = subHit == null
                ? level.getBlockState(targetPos)
                : subHit.state();

        if (WeldManager.isWelded(level, targetPos, targetState)) {
            event.setCanceled(true);
            event.setCancellationResult(cutterUnwelding ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            return;
        }

        if (WeldGeometry.isEligible(targetState)
                && (stack.getItem() instanceof WeldingTorchItem || cutterUnwelding)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
