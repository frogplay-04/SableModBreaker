package com.FrogPlay.sablemobbreaker.client;

import com.FrogPlay.sablemobbreaker.menu.WeldingTorchMenu;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class WeldingTorchScreen extends AbstractContainerScreen<WeldingTorchMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/hopper.png");

    public WeldingTorchScreen(WeldingTorchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 133;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 40; // Надпись "Инвентарь" ровно над слотами
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // 1. Рисуем текстуру воронки (инвентарь внизу останется нетронутым)
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // 2. Закрашиваем верхнюю часть (где были 5 слотов) чистым фоном
        guiGraphics.fill(x + 1, y + 1, x + 175, y + 37, 0xFFC6C6C6);

        // Рисуем рамки, чтобы это выглядело как единый интерфейс
        guiGraphics.fill(x + 1, y + 1, x + 175, y + 2, 0xFF373737); // Верхняя рамка
        guiGraphics.fill(x + 1, y + 1, x + 2, y + 37, 0xFF373737); // Левая
        guiGraphics.fill(x + 174, y + 1, x + 175, y + 37, 0xFF373737); // Правая

        // 3. Бирка для названия (как у печки)
        guiGraphics.fill(x + 6, y + 4, x + 170, y + 16, 0xFF8B8B8B); // Фон
        guiGraphics.fill(x + 6, y + 4, x + 170, y + 5, 0xFF373737); // Верхняя тень
        guiGraphics.fill(x + 6, y + 15, x + 170, y + 16, 0xFF373737); // Нижняя тень
        guiGraphics.fill(x + 6, y + 4, x + 7, y + 16, 0xFF373737); // Левая тень
        guiGraphics.fill(x + 169, y + 4, x + 170, y + 16, 0xFF373737); // Правая тень

        // 4. Один слот по центру
        guiGraphics.fill(x + 79, y + 19, x + 97, y + 37, 0xFF373737); // Внешняя тень слота
        guiGraphics.fill(x + 80, y + 20, x + 96, y + 36, 0xFF8B8B8B); // Внутренний фон слота
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        // Уменьшаем текст до 80% и центрируем в бирке
        float scale = 0.8f;
        poseStack.scale(scale, scale, 1.0f);

        int textWidth = this.font.width(this.title);
        int startX = (int)((this.imageWidth / 2.0f - textWidth * scale / 2.0f) / scale);

        guiGraphics.drawString(this.font, this.title, startX, (int)(8 / scale), 4210752, false);

        poseStack.popPose();

        // Надпись "Инвентарь"
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }
}