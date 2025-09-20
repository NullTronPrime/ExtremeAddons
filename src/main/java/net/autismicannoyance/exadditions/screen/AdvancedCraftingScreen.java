package net.autismicannoyance.exadditions.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AdvancedCraftingScreen extends AbstractContainerScreen<AdvancedCraftingMenu> {
    private static final ResourceLocation SLOT_TEXTURE =
            new ResourceLocation(ExAdditions.MOD_ID, "textures/gui/inventoryslot.png");

    public AdvancedCraftingScreen(AdvancedCraftingMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 176;
        this.imageHeight = 200;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Draw main background panel
        drawPanel(guiGraphics, x, y, imageWidth, imageHeight);

        // Draw crafting area background
        int craftingPanelX = x + 30;
        int craftingPanelY = y + 8;
        int craftingPanelWidth = 116;
        int craftingPanelHeight = 108;
        drawPanel(guiGraphics, craftingPanelX, craftingPanelY, craftingPanelWidth, craftingPanelHeight);

        // Draw 5x5 crafting grid slots
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                int slotX = x + 44 + col * 18;
                int slotY = y + 17 + row * 18;
                drawSlot(guiGraphics, slotX, slotY);
            }
        }

        // Draw result slot with arrow pointing to it
        int resultX = x + 152;
        int resultY = y + 53;
        drawSlot(guiGraphics, resultX, resultY);

        // Draw arrow pointing to result slot
        drawArrow(guiGraphics, resultX - 24, resultY + 1);

        // Draw player inventory background
        int invPanelY = y + 118;
        drawPanel(guiGraphics, x + 8, invPanelY - 4, 160, 76);

        // Draw player inventory slots (3x9 grid)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = x + 8 + col * 18;
                int slotY = y + 118 + row * 18;
                drawSlot(guiGraphics, slotX, slotY);
            }
        }

        // Draw hotbar slots
        for (int col = 0; col < 9; col++) {
            int slotX = x + 8 + col * 18;
            int slotY = y + 176;
            drawSlot(guiGraphics, slotX, slotY);
        }
    }

    private void drawSlot(GuiGraphics guiGraphics, int x, int y) {
        // Draw the 15x15 slot texture, but scale it to 16x16 to fit standard slot size
        guiGraphics.blit(SLOT_TEXTURE, x, y, 0, 0, 16, 16, 15, 15);
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // Draw a simple panel with borders
        // Outer border (dark gray)
        guiGraphics.fill(x, y, x + width, y + height, 0xFF555555);

        // Inner area (light gray)
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFC6C6C6);

        // Top-left highlight
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFFFFFFF);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFFFFFFF);

        // Bottom-right shadow
        guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF8B8B8B);
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xFF8B8B8B);
    }

    private void drawArrow(GuiGraphics guiGraphics, int x, int y) {
        // Draw a simple arrow pointing right using filled rectangles
        // Arrow shaft
        guiGraphics.fill(x, y + 6, x + 16, y + 8, 0xFF8B8B8B);

        // Arrow head
        guiGraphics.fill(x + 16, y + 4, x + 18, y + 6, 0xFF8B8B8B);
        guiGraphics.fill(x + 18, y + 6, x + 20, y + 8, 0xFF8B8B8B);
        guiGraphics.fill(x + 16, y + 8, x + 18, y + 10, 0xFF8B8B8B);
    }
}