package com.FrogPlay.sablemobbreaker.client;

import com.FrogPlay.sablemobbreaker.SableMobBreaker;
import com.FrogPlay.sablemobbreaker.item.PlasmaCutterItem;
import com.FrogPlay.sablemobbreaker.item.WeldingTorchItem;
import com.FrogPlay.sablemobbreaker.network.SetTorchStatePacket;
import com.FrogPlay.sablemobbreaker.network.SetToolUsePacket;
import com.FrogPlay.sablemobbreaker.util.WeldGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.BitSet;

@EventBusSubscriber(modid = SableMobBreaker.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    private static final double RADIUS = 20.0;
    private static final int RESCAN_INTERVAL = 10;

    private static final int BLOCKS = 10;
    private static final int BLOCK_W = 6;
    private static final int BLOCK_H = 5;
    private static final int GAP = 2;
    private static final int TOTAL_W = BLOCKS * BLOCK_W + (BLOCKS - 1) * GAP;

    private static final int LIME = 0xFF55FF55;
    private static final int RED = 0xFFFF3333;
    private static final int BORDER = 0xFF202020;
    private static final int BG = 0x80000000;

    private record Label(double x, double y, double z, int hp, int maxHp, double distSq) {}

    private static final List<Label> LABELS = new ArrayList<>();
    private static final java.util.Map<BlockHealthClientData.Key, Integer> RECENTLY_REPAIRED = new java.util.concurrent.ConcurrentHashMap<>();
    private static int tickCounter = RESCAN_INTERVAL;
    private static boolean wasAttackDown = false;
    private static boolean wasUseDown = false;
    private static BitSet clientWeldTrace = new BitSet(48);
    private static String clientWeldAssembly = "";
    private static int repairNoticeTicks = 0;
    private static int repairNoticeHp = 0;
    private static int repairNoticeMaxHp = 0;

    public static void showRepairHp(UUID subLevelId, int x, int y, int z, int hp, int maxHp) {
        repairNoticeHp = hp;
        repairNoticeMaxHp = maxHp;
        repairNoticeTicks = 20;
        RECENTLY_REPAIRED.put(new BlockHealthClientData.Key(subLevelId, x, y, z), 40);
        markDirty();
    }

    public static void markDirty() {
        tickCounter = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (repairNoticeTicks > 0) repairNoticeTicks--;
        RECENTLY_REPAIRED.replaceAll((key, ticks) -> ticks - 1);
        RECENTLY_REPAIRED.entrySet().removeIf(entry -> entry.getValue() <= 0);
        if (player == null) {
            wasAttackDown = false;
            wasUseDown = false;
            return;
        }

        boolean isAttackDown = mc.options.keyAttack.isDown();
        boolean isHoldingTool = player.getMainHandItem().getItem() instanceof WeldingTorchItem
                || player.getMainHandItem().getItem() instanceof PlasmaCutterItem;

        if (isAttackDown != wasAttackDown) {
            if (isHoldingTool) {
                PacketDistributor.sendToServer(new SetTorchStatePacket(isAttackDown));
            } else if (wasAttackDown) {
                PacketDistributor.sendToServer(new SetTorchStatePacket(false));
            }
            wasAttackDown = isAttackDown;
        }

        boolean isUseDown = mc.options.keyUse.isDown();
        if (isUseDown != wasUseDown) {
            if (isHoldingTool) {
                PacketDistributor.sendToServer(new SetToolUsePacket(isUseDown));
            } else if (wasUseDown) {
                PacketDistributor.sendToServer(new SetToolUsePacket(false));
            }
            wasUseDown = isUseDown;
        }

        if (--tickCounter <= 0) {
            tickCounter = RESCAN_INTERVAL;
            rescan(player);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        for (var entry : BlockHealthClientData.getAll().entrySet()) {
            var key = entry.getKey();
            var data = entry.getValue();
            if (data.hp() >= data.maxHp()) continue;

            SubLevel sl = getSubLevel(mc.level, key.subLevelId());
            if (sl == null) continue;

            BlockState state = SableMobBreakerClient.getBlockStateAt(key.subLevelId(), key.x(), key.y(), key.z());
            if (state == null || state.isAir()
                    || state.is(com.FrogPlay.sablemobbreaker.block.ModBlocks.BROKEN_BLOCK.get())) continue;

            int progress = (int) (10.0F * (1.0F - (float) data.hp() / data.maxHp()));
            if (progress <= 0) progress = 1;
            if (progress > 9) progress = 9;

            ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_" + progress + ".png");
            RenderType type = RenderType.entityTranslucent(texture);
            VertexConsumer vc = buffer.getBuffer(type);

            poseStack.pushPose();
            Vector3d anchor = sl.logicalPose().position();
            poseStack.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);

            try {
                Object pose = sl.logicalPose();
                for (java.lang.reflect.Method method : pose.getClass().getMethods()) {
                    String name = method.getName();
                    if ((name.equals("orientation") || name.equals("rotation") || name.equals("quaternion"))
                            && method.getParameterCount() == 0) {
                        Object result = method.invoke(pose);
                        if (result instanceof Quaternionf q) {
                            poseStack.mulPose(q);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            poseStack.translate(key.x(), key.y(), key.z());

            renderCube(poseStack, vc);

            poseStack.popPose();
        }

        renderWeldOutline(mc, poseStack, cam, buffer);
        buffer.endBatch();
    }

    private static void renderWeldOutline(Minecraft mc, PoseStack poseStack, Vec3 camera, MultiBufferSource.BufferSource buffer) {
        if (mc.player == null || mc.hitResult == null) {
            clientWeldTrace.clear();
            clientWeldAssembly = "";
            return;
        }
        var held = mc.player.getMainHandItem().getItem();
        boolean welding = held instanceof WeldingTorchItem && WeldingTorchItem.isOn(mc.player.getMainHandItem());
        boolean cutting = held instanceof PlasmaCutterItem && PlasmaCutterItem.isOn(mc.player.getMainHandItem());
        if (!welding && !cutting) {
            clientWeldTrace.clear();
            clientWeldAssembly = "";
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;
        var blocks = WeldGeometry.findAssembly(mc.level, hit.getBlockPos());
        if (blocks.isEmpty()) return;

        String key = blocks.stream().mapToLong(BlockPos::asLong).min().orElse(0L) + ":" + welding;
        if (!key.equals(clientWeldAssembly)) {
            clientWeldAssembly = key;
            clientWeldTrace.clear();
        }

        AABB box = WeldGeometry.bounds(mc.level, blocks);
        Vec3[][] edges = WeldGeometry.visibleFaceEdges(box, mc.player.getEyePosition());
        Vec3 eye = mc.player.getEyePosition();
        Vec3 rayEnd = eye.add(mc.player.getLookAngle().scale(6.0D));
        boolean tracing = mc.options.keyAttack.isDown();
        final int samples = 12;
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(3.0F);
        for (int edge = 0; edge < edges.length; edge++) {
            Vec3 a = edges[edge][0], b = edges[edge][1];
            for (int sample = 0; sample < samples; sample++) {
                double t0 = (double) sample / samples;
                double t1 = (double) (sample + 1) / samples;
                Vec3 p0 = a.lerp(b, t0);
                Vec3 p1 = a.lerp(b, t1);
                Vec3 mid = p0.lerp(p1, 0.5);
                if (tracing && distanceToSegmentSqr(mid, eye, rayEnd) <= 0.30D * 0.30D) {
                    clientWeldTrace.set(edge * samples + sample);
                }
                int color = clientWeldTrace.get(edge * samples + sample)
                        ? (welding ? 0xFF35E66B : 0xFFFF8B35) : 0xF0FFFFFF;
                VertexConsumer lines = buffer.getBuffer(RenderType.lines());
                Matrix4f matrix = poseStack.last().pose();
                lines.addVertex(matrix, (float)(p0.x-camera.x), (float)(p0.y-camera.y), (float)(p0.z-camera.z))
                        .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255).setNormal(0, 1, 0);
                lines.addVertex(matrix, (float)(p1.x-camera.x), (float)(p1.y-camera.y), (float)(p1.z-camera.z))
                        .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255).setNormal(0, 1, 0);
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(1.0F);
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-9) return point.distanceToSqr(start);
        double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(segment) / lengthSqr));
        return point.distanceToSqr(start.add(segment.scale(t)));
    }

    private static void renderCube(PoseStack poseStack, VertexConsumer vc) {
        Matrix4f mat = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        int r = 255, g = 255, b = 255, a = 220;
        int light = LightTexture.FULL_BRIGHT;

        vc.addVertex(mat, 0, 0, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(mat, 1, 0, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(mat, 1, 1, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(mat, 0, 1, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);

        vc.addVertex(mat, 1, 0, 1).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, 0, 0, 1).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, 0, 1, 1).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(mat, 1, 1, 1).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);

        vc.addVertex(mat, 0, 0, 1).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, -1, 0, 0);
        vc.addVertex(mat, 0, 0, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, -1, 0, 0);
        vc.addVertex(mat, 0, 1, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, -1, 0, 0);
        vc.addVertex(mat, 0, 1, 1).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, -1, 0, 0);

        vc.addVertex(mat, 1, 0, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        vc.addVertex(mat, 1, 0, 1).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        vc.addVertex(mat, 1, 1, 1).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);
        vc.addVertex(mat, 1, 1, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 1, 0, 0);

        vc.addVertex(mat, 0, 0, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, -1, 0);
        vc.addVertex(mat, 1, 0, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, -1, 0);
        vc.addVertex(mat, 1, 0, 1).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, -1, 0);
        vc.addVertex(mat, 0, 0, 1).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, -1, 0);

        vc.addVertex(mat, 0, 1, 1).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(mat, 1, 1, 1).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(mat, 1, 1, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(mat, 0, 1, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }

    private static SubLevel getSubLevel(Level level, UUID id) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        for (SubLevel sl : container.getAllSubLevels()) {
            if (!sl.isRemoved() && id.equals(sl.getUniqueId())) return sl;
        }
        return null;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null) return;
        GuiGraphics gfx = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (repairNoticeTicks > 0) {
            gfx.drawCenteredString(mc.font, "HP блока: " + repairNoticeHp + " / " + repairNoticeMaxHp,
                    screenW / 2, screenH / 2 + 22, 0xFFFFFFFF);
        }
        if ((!GoggleHelper.isWearingGoggles(player) && RECENTLY_REPAIRED.isEmpty()) || LABELS.isEmpty()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();

        Quaternionf camRot = camera.rotation();
        Matrix4f view = new Matrix4f();
        view.rotate(camRot.conjugate(new Quaternionf()));
        view.translate((float) -cam.x, (float) -cam.y, (float) -cam.z);

        Matrix4f projection = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get());

        LABELS.sort((a, b) -> Double.compare(b.distSq, a.distSq));

        for (Label l : LABELS) {
            Vector4f p = new Vector4f((float) l.x, (float) l.y, (float) l.z, 1.0f);
            p.mul(view);
            if (p.z() >= 0) continue;
            p.mul(projection);
            if (p.w() <= 0) continue;

            float ndcX = p.x() / p.w();
            float ndcY = p.y() / p.w();
            if (ndcX < -1.05f || ndcX > 1.05f || ndcY < -1.05f || ndcY > 1.05f) continue;

            float screenX = (ndcX + 1.0f) * 0.5f * screenW;
            float screenY = (1.0f - ndcY) * 0.5f * screenH;

            int startX = Math.round(screenX - TOTAL_W / 2f);
            int startY = Math.round(screenY);

            gfx.drawString(mc.font, l.hp + " / " + l.maxHp + " HP", startX, startY - 11, 0xFFFFFFFF, false);
            gfx.fill(startX - 2, startY - 2, startX + TOTAL_W + 2, startY + BLOCK_H + 2, BG);
            gfx.fill(startX - 1, startY - 1, startX + TOTAL_W + 1, startY + BLOCK_H + 1, BORDER);

            float hpPerBlock = l.maxHp / (float) BLOCKS;

            for (int i = 0; i < BLOCKS; i++) {
                int x0 = startX + i * (BLOCK_W + GAP);
                int x1 = x0 + BLOCK_W;

                gfx.fill(x0, startY, x1, startY + BLOCK_H, RED);

                float hpInBlock = l.hp - i * hpPerBlock;
                if (hpInBlock <= 0) continue;
                if (hpInBlock > hpPerBlock) hpInBlock = hpPerBlock;

                float frac = hpInBlock / hpPerBlock;
                int limeWidth = Math.round(BLOCK_W * frac);
                if (limeWidth > 0) {
                    gfx.fill(x0, startY, x0 + limeWidth, startY + BLOCK_H, LIME);
                }
            }
        }
    }

    private static void rescan(Player player) {
        LABELS.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Clean stale entries for every player, not only players wearing Engineer's Goggles.
        var stale = new ArrayList<BlockHealthClientData.Key>();
        for (var key : BlockHealthClientData.getAll().keySet()) {
            BlockState state = SableMobBreakerClient.getBlockStateAt(key.subLevelId(), key.x(), key.y(), key.z());
            if (state == null || state.isAir() || state.is(com.FrogPlay.sablemobbreaker.block.ModBlocks.BROKEN_BLOCK.get())) {
                stale.add(key);
            }
        }
        for (var key : stale) {
            BlockHealthClientData.remove(key);
        }

        boolean wearingGoggles = GoggleHelper.isWearingGoggles(player);
        if (!wearingGoggles && RECENTLY_REPAIRED.isEmpty()) return;

        double radiusSq = RADIUS * RADIUS;
        for (var e : BlockHealthClientData.getAll().entrySet()) {
            var key = e.getKey();
            var entry = e.getValue();
            if (!wearingGoggles && !RECENTLY_REPAIRED.containsKey(key)) continue;
            if (entry.hp() >= entry.maxHp()) continue;

            Vec3 wp = SableMobBreakerClient.getBlockWorldPos(key.subLevelId(), key.x(), key.y(), key.z());
            if (wp == null) continue;

            double dSq = wp.distanceToSqr(player.position());
            if (dSq > radiusSq) continue;

            LABELS.add(new Label(wp.x, wp.y + 0.5, wp.z, entry.hp(), entry.maxHp(), dSq));
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var item = mc.player.getMainHandItem().getItem();
                if (item instanceof WeldingTorchItem) {
                    event.setCanceled(true);
                } else if (item instanceof PlasmaCutterItem && !(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult)) {
                    event.setCanceled(true);
                }
            }
        }
    }
}