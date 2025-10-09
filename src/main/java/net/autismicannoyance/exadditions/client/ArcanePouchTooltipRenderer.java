package net.autismicannoyance.exadditions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.autismicannoyance.exadditions.item.custom.ArcanePouchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;

public class ArcanePouchTooltipRenderer implements ClientTooltipComponent {
    private final ArcanePouchItem.ArcanePouchTooltip tooltip;
    private static final int VIEW_SIZE = 160;
    private static final int PADDING = 8;
    private static final double SCALE = 10.0; // 10 blocks = full view width

    public ArcanePouchTooltipRenderer(ArcanePouchItem.ArcanePouchTooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public int getHeight() {
        return VIEW_SIZE + PADDING * 2;
    }

    @Override
    public int getWidth(Font font) {
        return VIEW_SIZE + PADDING * 2;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int bgX = x + PADDING;
        int bgY = y + PADDING;

        // Draw background - darker space-like background
        graphics.fill(bgX, bgY, bgX + VIEW_SIZE, bgY + VIEW_SIZE, 0xFF0A0A1A);

        // Draw border
        graphics.fill(bgX - 1, bgY - 1, bgX + VIEW_SIZE + 1, bgY, 0xFF4A4A6A); // Top
        graphics.fill(bgX - 1, bgY + VIEW_SIZE, bgX + VIEW_SIZE + 1, bgY + VIEW_SIZE + 1, 0xFF4A4A6A); // Bottom
        graphics.fill(bgX - 1, bgY, bgX, bgY + VIEW_SIZE, 0xFF4A4A6A); // Left
        graphics.fill(bgX + VIEW_SIZE, bgY, bgX + VIEW_SIZE + 1, bgY + VIEW_SIZE, 0xFF4A4A6A); // Right

        int centerX = bgX + VIEW_SIZE / 2;
        int centerY = bgY + VIEW_SIZE / 2;

        // Draw grid lines for reference
        int gridSpacing = VIEW_SIZE / 8;
        for (int i = 1; i < 8; i++) {
            int pos = bgX + i * gridSpacing;
            graphics.fill(pos, bgY, pos + 1, bgY + VIEW_SIZE, 0xFF1A1A2E);
            pos = bgY + i * gridSpacing;
            graphics.fill(bgX, pos, bgX + VIEW_SIZE, pos + 1, 0xFF1A1A2E);
        }

        // Draw center platform marker (diamond block at 0,0)
        graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFF00FFFF);
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFFFFD700);

        // Draw range circle (8 block radius)
        int circleRadius = (int) (VIEW_SIZE / 2 * 0.8); // 80% of view
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int px = centerX + (int) (Math.cos(rad) * circleRadius);
            int py = centerY + (int) (Math.sin(rad) * circleRadius);
            graphics.fill(px, py, px + 1, py + 1, 0xFF2A2A4A);
        }

        // Get entity data from client cache
        ListTag entityData = PouchClientData.getPouchData(tooltip.pouchUUID());

        if (entityData.isEmpty()) {
            // No data yet - show loading message
            String msg = "No entities";
            int msgWidth = font.width(msg);
            graphics.drawString(font, msg, centerX - msgWidth / 2, centerY - 4, 0xFF666666, false);
            return;
        }

        // Render entities at their actual positions
        for (int i = 0; i < entityData.size(); i++) {
            try {
                CompoundTag tag = entityData.getCompound(i);

                // Get position from NBT
                ListTag posList = tag.getList("Pos", 6);
                if (posList.size() != 3) continue;

                double entityX = posList.getDouble(0);
                double entityZ = posList.getDouble(2);
                double entityY = posList.getDouble(1);

                // Convert world position to screen position
                // Center of view is 0,0 in world space
                int screenX = centerX + (int) ((entityX / SCALE) * (VIEW_SIZE / 2));
                int screenY = centerY + (int) ((entityZ / SCALE) * (VIEW_SIZE / 2));

                // Only render if within view bounds
                if (screenX < bgX || screenX > bgX + VIEW_SIZE ||
                        screenY < bgY || screenY > bgY + VIEW_SIZE) {
                    continue;
                }

                // Render the entity
                renderEntityAtPosition(graphics, mc, tag, screenX, screenY, entityY);

            } catch (Exception e) {
                // Skip problematic entities
            }
        }

        // Draw compass/labels
        graphics.drawString(font, "N", centerX - 3, bgY + 2, 0xFFAAAAAA, false);
        graphics.drawString(font, "S", centerX - 3, bgY + VIEW_SIZE - 10, 0xFFAAAAAA, false);
        graphics.drawString(font, "W", bgX + 2, centerY - 4, 0xFFAAAAAA, false);
        graphics.drawString(font, "E", bgX + VIEW_SIZE - 8, centerY - 4, 0xFFAAAAAA, false);
    }

    private void renderEntityAtPosition(GuiGraphics graphics, Minecraft mc, CompoundTag tag, int screenX, int screenY, double worldY) {
        try {
            String id = tag.getString("id");
            Optional<EntityType<?>> maybeType = EntityType.byString(id);
            if (maybeType.isEmpty()) {
                // Draw fallback dot
                graphics.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, 0xFFFF0000);
                return;
            }

            EntityType<?> type = maybeType.get();
            Entity entity = type.create(mc.level);
            if (!(entity instanceof LivingEntity living)) {
                graphics.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, 0xFFFF6600);
                return;
            }

            // Load entity data
            CompoundTag loadTag = tag.copy();
            loadTag.remove("Pos");
            loadTag.remove("UUID");
            loadTag.remove("UUIDLeast");
            loadTag.remove("UUIDMost");

            try {
                living.load(loadTag);
                living.tickCount = (int) (mc.level.getGameTime() % 24000);

                // Calculate entity size based on Y position (simulate perspective)
                int size = 16;
                if (worldY > 65) {
                    size = 18; // Slightly larger if above platform
                } else if (worldY < 65) {
                    size = 14; // Slightly smaller if below
                }

                // Render entity model
                RenderSystem.enableBlend();
                // Use current game time for animation
                float mouseX = 0f;
                float mouseY = 0f;
                InventoryScreen.renderEntityInInventoryFollowsAngle(
                        graphics,
                        screenX,
                        screenY + size / 4, // Offset to show feet at the position
                        size / 2,
                        mouseX,
                        mouseY,
                        living
                );
                RenderSystem.disableBlend();

                // Draw position indicator dot beneath entity
                graphics.fill(screenX - 1, screenY - 1, screenX + 1, screenY + 1, 0xFF00FF00);

            } catch (Exception ex) {
                // Fallback to simple dot
                graphics.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, 0xFFFFAA00);
            }
        } catch (Exception ex) {
            // Ultimate fallback
            graphics.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, 0xFFAA0000);
        }
    }
}