package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.item.custom.ArcanePouchItem;
import net.autismicannoyance.exadditions.world.dimension.ArcanePouchDimensionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ArcanePouchTooltipRenderer implements ClientTooltipComponent {
    private final ArcanePouchItem.ArcanePouchTooltip tooltip;
    private static final int VIEW_SIZE = 128;
    private static final int PADDING = 6;
    private static final double VIEW_RADIUS = 10.0;

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
        ArcanePouchDimensionManager.tickPouchDimension(tooltip.pouchUUID());
        int bgX = x + PADDING;
        int bgY = y + PADDING;
        graphics.fill(bgX, bgY, bgX + VIEW_SIZE, bgY + VIEW_SIZE, 0xFF000000);
        graphics.fill(bgX + 1, bgY + 1, bgX + VIEW_SIZE - 1, bgY + VIEW_SIZE - 1, 0xFF1A1A2E);
        int centerX = bgX + VIEW_SIZE / 2;
        int centerY = bgY + VIEW_SIZE / 2;
        int circleRadius = (int) (VIEW_SIZE * 0.4);
        for (int angle = 0; angle < 360; angle += 3) {
            double rad = Math.toRadians(angle);
            int px = centerX + (int) (Math.cos(rad) * circleRadius);
            int py = centerY + (int) (Math.sin(rad) * circleRadius);
            graphics.fill(px, py, px + 1, py + 1, 0xFF4A4A6A);
        }
        graphics.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFFFFD700);
        if (mc.getSingleplayerServer() != null) {
            ServerLevel pouchLevel = ArcanePouchDimensionManager.getOrCreatePouchDimension(
                    mc.getSingleplayerServer().overworld(), tooltip.pouchUUID());
            if (pouchLevel != null) {
                AABB searchBox = new AABB(-VIEW_RADIUS, 60, -VIEW_RADIUS, VIEW_RADIUS, 75, VIEW_RADIUS);
                List<Entity> entities = pouchLevel.getEntities((Entity) null, searchBox, e -> e instanceof LivingEntity);
                for (Entity entity : entities) {
                    if (entity instanceof LivingEntity living) {
                        Vec3 pos = entity.position();
                        double relX = pos.x / VIEW_RADIUS;
                        double relZ = pos.z / VIEW_RADIUS;
                        if (Math.abs(relX) <= 1.0 && Math.abs(relZ) <= 1.0) {
                            int screenX = centerX + (int) (relX * circleRadius);
                            int screenY = centerY + (int) (relZ * circleRadius);
                            int iconSize = 12;
                            try {
                                living.tickCount = (int) (mc.level.getGameTime() % 24000);
                                InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, screenX, screenY, iconSize / 2,
                                        0f, 0f, living);
                            } catch (Exception e) {
                                graphics.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, 0xFFFF0000);
                            }
                        }
                    }
                }
            }
        } else {
            for (int i = 0; i < tooltip.mobTags().size(); i++) {
                CompoundTag tag = tooltip.mobTags().get(i);
                double angle = (double) i / tooltip.mobTags().size() * Math.PI * 2;
                int px = centerX + (int) (Math.cos(angle) * circleRadius * 0.7);
                int py = centerY + (int) (Math.sin(angle) * circleRadius * 0.7);
                renderMobIcon(graphics, mc, tag, px, py, 12);
            }
        }
    }

    private void renderMobIcon(GuiGraphics graphics, Minecraft mc, CompoundTag tag, int x, int y, int size) {
        try {
            String id = tag.getString("id");
            Optional<EntityType<?>> maybeType = EntityType.byString(id);
            if (maybeType.isEmpty()) {
                graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, 0xFF333333);
                return;
            }
            EntityType<?> type = maybeType.get();
            Entity entity = type.create(mc.level);
            if (!(entity instanceof LivingEntity living)) {
                graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, 0xFF333333);
                return;
            }
            CompoundTag loadTag = tag.copy();
            loadTag.remove("Pos");
            loadTag.remove("UUID");
            loadTag.remove("UUIDLeast");
            loadTag.remove("UUIDMost");
            try {
                living.load(loadTag);
                living.tickCount = (int) (mc.level.getGameTime() % 24000);
                InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, size / 2, 0f, 0f, living);
            } catch (Exception ex) {
                graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, 0xFF333333);
            }
        } catch (Exception ex) {
            graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, 0xFF333333);
        }
    }
}