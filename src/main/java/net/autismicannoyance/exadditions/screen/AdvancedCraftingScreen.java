package net.autismicannoyance.exadditions.screen;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AdvancedCraftingScreen extends AbstractContainerScreen<AdvancedCraftingMenu> {
    // Use Minecraft's widgets texture for authentic vanilla look
    private static final ResourceLocation WIDGETS_LOCATION =
            new ResourceLocation("minecraft", "textures/gui/widgets.png");

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

        // Draw main background panel (vanilla style)
        renderVanillaPanel(guiGraphics, x, y, imageWidth, imageHeight);

        // Remove the crafting area background panel - no longer needed

        // Draw 5x5 crafting slots using vanilla slot texture
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                int slotX = x + 44 + col * 18 - 1; // -1 to account for slot border
                int slotY = y + 17 + row * 18 - 1;
                renderVanillaSlot(guiGraphics, slotX, slotY);
            }
        }

        // Draw result slot
        int resultX = x + 152 - 1;
        int resultY = y + 53 - 1;
        renderVanillaSlot(guiGraphics, resultX, resultY);

        // Draw arrow pointing to result
        renderCraftingArrow(guiGraphics, x + 126, y + 53);

        // Draw player inventory background
        renderVanillaPanel(guiGraphics, x + 7, y + 117, 162, 76);

        // Draw player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = x + 8 + col * 18 - 1;
                int slotY = y + 118 + row * 18 - 1;
                renderVanillaSlot(guiGraphics, slotX, slotY);
            }
        }

        // Draw hotbar slots
        for (int col = 0; col < 9; col++) {
            int slotX = x + 8 + col * 18 - 1;
            int slotY = y + 176 - 1;
            renderVanillaSlot(guiGraphics, slotX, slotY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0x404040, false);
    }

    private void renderVanillaPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // Background
        guiGraphics.fill(x, y, x + width, y + height, 0xFFC6C6C6);

        // Dark outer border
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF555555); // top
        guiGraphics.fill(x, y, x + 1, y + height, 0xFF555555); // left
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFF555555); // right
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF555555); // bottom

        // Light inner highlights
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFFFFFFF); // top highlight
        guiGraphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFFFFFFF); // left highlight

        // Dark inner shadows
        guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF8B8B8B); // bottom shadow
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xFF8B8B8B); // right shadow
    }

    private void renderVanillaSlot(GuiGraphics guiGraphics, int x, int y) {
        // Slot background (18x18)
        guiGraphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);

        // Slot borders
        guiGraphics.fill(x, y, x + 18, y + 1, 0xFF373737); // top
        guiGraphics.fill(x, y, x + 1, y + 18, 0xFF373737); // left
        guiGraphics.fill(x + 17, y, x + 18, y + 18, 0xFFFFFFFF); // right
        guiGraphics.fill(x, y + 17, x + 18, y + 18, 0xFFFFFFFF); // bottom

        // Inner area
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    // Alternative simpler arrow - replace the renderCraftingArrow method with this:
    private void renderCraftingArrow(GuiGraphics guiGraphics, int x, int y) {
        int arrowColor = 0xFF8B8B8B;

        // Simple arrow: horizontal line with triangle
        guiGraphics.fill(x + 3, y + 7, x + 17, y + 9, arrowColor);  // shaft
        guiGraphics.fill(x + 17, y + 5, x + 19, y + 7, arrowColor); // top arrow
        guiGraphics.fill(x + 17, y + 9, x + 19, y + 11, arrowColor); // bottom arrow
        guiGraphics.fill(x + 19, y + 6, x + 21, y + 8, arrowColor); // arrow tip
        guiGraphics.fill(x + 19, y + 8, x + 21, y + 10, arrowColor); // arrow tip
    }
}