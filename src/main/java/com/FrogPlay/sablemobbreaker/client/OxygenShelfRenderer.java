package com.FrogPlay.sablemobbreaker.client;

import com.FrogPlay.sablemobbreaker.block.OxygenShelfBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class OxygenShelfRenderer implements BlockEntityRenderer<OxygenShelfBlockEntity> {

    private final ItemRenderer itemRenderer;

    public OxygenShelfRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(OxygenShelfBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        for (int i = 0; i < 3; i++) {
            ItemStack stack = blockEntity.getItemHandler().getStackInSlot(i);
            if (stack.isEmpty()) continue;

            poseStack.pushPose();
            poseStack.translate(0.5D, 0.15D, 0.5D);
            poseStack.translate((i - 1) * 0.25D, 0.0D, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.scale(0.65F, 0.65F, 0.65F);

            this.itemRenderer.renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT,
                    packedOverlay,
                    poseStack,
                    buffer,
                    blockEntity.getLevel(),
                    0
            );
            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(OxygenShelfBlockEntity blockEntity) {
        var pos = blockEntity.getBlockPos();
        return new AABB(
                pos.getX() - 1.0, pos.getY() - 1.0, pos.getZ() - 1.0,
                pos.getX() + 2.0, pos.getY() + 2.0, pos.getZ() + 2.0
        );
    }
}