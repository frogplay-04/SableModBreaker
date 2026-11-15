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
import org.jetbrains.annotations.NotNull;

public class OxygenShelfRenderer implements BlockEntityRenderer<OxygenShelfBlockEntity> {

    private final ItemRenderer itemRenderer;

    public OxygenShelfRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(@NotNull OxygenShelfBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        for (int i = 0; i < 3; i++) {
            ItemStack stack = blockEntity.getItemHandler().getStackInSlot(i);
            if (stack.isEmpty()) continue;

            poseStack.pushPose();

            poseStack.translate(0.5D, 0.3D, 0.5D);

            poseStack.translate((i - 1) * 0.3D, 0.0D, 0.0D);

            poseStack.mulPose(Axis.XP.rotationDegrees(90));

            poseStack.scale(1F, 1F, 1F);

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
    public @NotNull AABB getRenderBoundingBox(@NotNull OxygenShelfBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(2.0);
    }
}