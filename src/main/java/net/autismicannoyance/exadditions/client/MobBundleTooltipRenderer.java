package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.item.custom.MobBundleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MobBundle tooltip renderer (Forge 1.20.1, parchment mappings).
 *
 * - Renders all stored mobs (no fixed cap)
 * - Distributes them across concentric rings with ~2x spacing
 * - Auto-scales icons/rings to fit available screen area
 * - Orbit rotates at 3 degrees/tick (2x slower)
 * - Uses InventoryScreen.renderEntityInInventoryFollowsAngle(GuiGraphics, ...)
 *
 * Fix: centralize sizing into computeLayout() so getHeight() and renderImage()
 * share identical layout calculations. This removes large reserved empty space.
 */
public class MobBundleTooltipRenderer implements ClientTooltipComponent {
    private final MobBundleItem.MobTooltip tooltip;

    // Base constants (may be scaled down to fit)
    private static final int ICON_SIZE_BASE = 28;            // base icon size (px)
    private static final int ICON_SIZE_MIN = 10;
    private static final int ICON_SIZE_MAX = 48;
    private static final double SPACING_MULTIPLIER = 2.0;    // "2x as far" spacing requested
    private static final double RING_GAP_MULT = 1.25;       // extra radial gap between rings
    private static final double ROTATION_SPEED_DEG_PER_TICK = 3.0; // 3 deg/tick

    private static final int PADDING = 6;

    // Layout constants used consistently between getHeight() and renderImage()
    private static final int HEADER_HEIGHT = 12;
    private static final int PROGRESS_BAR_HEIGHT = 10; // includes a little gap

    public MobBundleTooltipRenderer(MobBundleItem.MobTooltip tooltip) {
        this.tooltip = tooltip;
    }

    /**
     * Small value-object to return computed layout used by both getHeight() and renderImage().
     */
    private static class Layout {
        final int tooltipWidth;
        final int tooltipHeight;
        final double iconSize;
        final double outerRadius;
        final List<Integer> perRing;
        final int centerXOffset; // half width (used to compute centerX = x + centerXOffset)

        Layout(int tooltipWidth, int tooltipHeight, double iconSize, double outerRadius, List<Integer> perRing, int centerXOffset) {
            this.tooltipWidth = tooltipWidth;
            this.tooltipHeight = tooltipHeight;
            this.iconSize = iconSize;
            this.outerRadius = outerRadius;
            this.perRing = perRing;
            this.centerXOffset = centerXOffset;
        }
    }

    /**
     * Compute layout for current screen/font state. Both getHeight() and renderImage() call this
     * so they are consistent.
     */
    private Layout computeLayout(Font font, int x) {
        Minecraft mc = Minecraft.getInstance();

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int total = tooltip.mobTags().size();

        // compute tooltip width using the same logic as getWidth(font)
        int header = font.width("Mobs: " + tooltip.count() + " / " + MobBundleItem.MAX_MOBS);
        int prefer = Math.max(header + PADDING * 2, ICON_SIZE_BASE * 8 + PADDING * 2);
        prefer = Math.min(prefer, Math.max(200, screenW - 20));
        int tooltipWidth = prefer;

        // quick empty case
        if (total <= 0) {
            int h = ICON_SIZE_BASE + PADDING * 3 + PROGRESS_BAR_HEIGHT;
            return new Layout(tooltipWidth, h, ICON_SIZE_BASE, ICON_SIZE_BASE / 2.0, List.of(), tooltipWidth / 2);
        }

        // initial sizing parameters
        double iconSize = ICON_SIZE_BASE;
        double spacing = iconSize * SPACING_MULTIPLIER;
        double ringGap = iconSize * SPACING_MULTIPLIER * RING_GAP_MULT;

        // usable width inside tooltip
        int usableWidth = Math.max(80, tooltipWidth - PADDING * 2);

        // Max allowed radius (so we don't overflow tooltip or screen)
        int maxAllowedRadius = Math.min(usableWidth / 2, Math.max(16, (screenH - HEADER_HEIGHT - PROGRESS_BAR_HEIGHT - PADDING * 2) / 2));

        // compute perRing with current iconSize
        List<Integer> perRing = computeRingsForIcons(total, iconSize, spacing, ringGap, maxAllowedRadius);
        double outerRadius = perRing.size() * ringGap;

        // If outer radius too big, scale down icon size until fits or until ICON_SIZE_MIN.
        // Use a simple loop reducing icon size by 1 px per step (keeps behavior deterministic).
        while (outerRadius > maxAllowedRadius && iconSize > ICON_SIZE_MIN) {
            iconSize = Math.max(ICON_SIZE_MIN, iconSize - 1.0);
            spacing = iconSize * SPACING_MULTIPLIER;
            ringGap = iconSize * SPACING_MULTIPLIER * RING_GAP_MULT;
            perRing = computeRingsForIcons(total, iconSize, spacing, ringGap, maxAllowedRadius);
            outerRadius = perRing.size() * ringGap;
        }

        // final clamp if still too large
        if (outerRadius > maxAllowedRadius) outerRadius = maxAllowedRadius;

        // compute final tooltip height: padding + header + orbit diameter + progress + padding
        int tooltipHeight = PADDING + HEADER_HEIGHT + (int) Math.ceil(outerRadius * 2.0) + PROGRESS_BAR_HEIGHT + PADDING;

        // centerX offset (so renderImage can compute centerX = x + offset)
        int centerXOffset = tooltipWidth / 2;

        return new Layout(tooltipWidth, tooltipHeight, iconSize, outerRadius, perRing, centerXOffset);
    }

    @Override
    public int getHeight() {
        // Use computeLayout with Minecraft font to get an exact height
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return PADDING + HEADER_HEIGHT + ICON_SIZE_BASE * 2 + PROGRESS_BAR_HEIGHT + PADDING;
        }
        Layout layout = computeLayout(mc.font, 0);
        return layout.tooltipHeight;
    }

    @Override
    public int getWidth(Font font) {
        int header = font.width("Mobs: " + tooltip.count() + " / " + MobBundleItem.MAX_MOBS);
        int prefer = Math.max(header + PADDING * 2, ICON_SIZE_BASE * 8 + PADDING * 2);
        // clamp to a fraction of screen width to avoid ridiculously wide tooltips
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        prefer = Math.min(prefer, Math.max(200, screenW - 20));
        return prefer;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Draw header
        int headerY = y + PADDING;
        graphics.drawString(font, "Mobs: " + tooltip.count() + " / " + MobBundleItem.MAX_MOBS, x + PADDING, headerY, 0xFFFFFF, false);

        // compute layout using exact same logic as getHeight()
        Layout layout = computeLayout(font, x);
        int width = layout.tooltipWidth;
        double iconSize = layout.iconSize;
        double outerRadius = layout.outerRadius;
        List<Integer> perRing = layout.perRing;

        int centerX = x + layout.centerXOffset;
        // top of orbit area
        int orbitTop = y + PADDING + HEADER_HEIGHT;
        int centerY = orbitTop + (int) Math.round(outerRadius);

        List<CompoundTag> tags = tooltip.mobTags();
        int total = tags.size();

        if (total == 0) {
            // placeholder & progress
            int drawX = x + PADDING;
            graphics.fill(drawX, centerY - ICON_SIZE_BASE / 2, drawX + ICON_SIZE_BASE, centerY + ICON_SIZE_BASE / 2, 0x88000000);
            int barY = centerY + ICON_SIZE_BASE / 2 + 8;
            drawProgressBar(graphics, font, x, barY, width);
            return;
        }

        // time-based base rotation (smooth with partial ticks)
        double time = mc.level.getGameTime() + mc.getFrameTime();
        double baseAngleDeg = (time * ROTATION_SPEED_DEG_PER_TICK) % 360.0;

        // render icons ring-by-ring (keeps behaviour of computeRingsForIcons)
        int tagIndex = 0;
        for (int ringIndex = 0; ringIndex < perRing.size(); ringIndex++) {
            int countInRing = perRing.get(ringIndex);
            if (countInRing <= 0) continue;
            double ringRadius = (ringIndex + 1) * (iconSize * SPACING_MULTIPLIER * RING_GAP_MULT);

            for (int i = 0; i < countInRing; i++) {
                if (tagIndex >= tags.size()) break;
                CompoundTag tag = tags.get(tagIndex++);
                double phaseDeg = (360.0 / Math.max(1, countInRing)) * i;
                double angleDeg = (baseAngleDeg + phaseDeg) % 360.0;
                double angleRad = Math.toRadians(angleDeg);

                int iconCx = centerX + (int) Math.round(Math.cos(angleRad) * ringRadius);
                int iconCy = centerY + (int) Math.round(Math.sin(angleRad) * ringRadius);

                renderEntityIcon(graphics, mc, tag, iconCx, iconCy, (int) Math.round(iconSize), angleDeg, time);
            }
        }

        // progress bar directly below the rings
        int barY = centerY + (int) Math.round(outerRadius) + 6;
        drawProgressBar(graphics, font, x, barY, width);
    }

    /**
     * Compute list of counts per ring for the given icon size, spacing and ringGap.
     * This method produces rings until all icons are assigned. It does not scale icons;
     * scaling is handled by caller if needed.
     */
    private List<Integer> computeRingsForIcons(int totalIcons, double iconSize, double spacing, double ringGap, int maxAllowedRadius) {
        List<Integer> perRing = new ArrayList<>();
        if (totalIcons <= 0) return perRing;

        // single icon
        if (totalIcons == 1) {
            perRing.add(1);
            return perRing;
        }

        int remaining = totalIcons;
        int ring = 1;
        while (remaining > 0 && ring < 256) {
            double radius = ring * ringGap;
            int cap = Math.max(1, (int) Math.floor((2.0 * Math.PI * Math.max(1.0, radius)) / Math.max(1.0, spacing)));
            int place = Math.min(remaining, cap);
            perRing.add(place);
            remaining -= place;
            if (radius > maxAllowedRadius && remaining > 0) {
                perRing.add(remaining);
                remaining = 0;
                break;
            }
            ring++;
        }

        if (remaining > 0) {
            perRing.add(remaining);
        }
        return perRing;
    }

    private void renderEntityIcon(GuiGraphics graphics, Minecraft mc, CompoundTag tag, int iconCx, int iconCy, int iconSize, double angleDeg, double time) {
        try {
            String id = tag.getString("id");
            Optional<EntityType<?>> maybeType = EntityType.byString(id);
            if (maybeType.isEmpty()) {
                drawPlaceholder(graphics, iconCx - iconSize / 2, iconCy - iconSize / 2, iconSize);
                return;
            }

            EntityType<?> type = maybeType.get();
            Entity entity = type.create(mc.level);
            if (!(entity instanceof LivingEntity living)) {
                drawPlaceholder(graphics, iconCx - iconSize / 2, iconCy - iconSize / 2, iconSize);
                return;
            }

            CompoundTag loadTag = tag.copy();
            loadTag.remove("Pos");
            loadTag.remove("UUID");
            loadTag.remove("UUIDLeast");
            loadTag.remove("UUIDMost");
            try {
                living.load(loadTag);
            } catch (Exception ex) {
                drawPlaceholder(graphics, iconCx - iconSize / 2, iconCy - iconSize / 2, iconSize);
                return;
            }

            try {
                living.tickCount = (int) Math.floor(time);
                float facingYaw = (float) ((angleDeg + 90.0) % 360.0);
                living.setYRot(facingYaw);
                living.setXRot(0f);
                living.setYHeadRot(facingYaw);
            } catch (Throwable ignored) {
            }

            int scaleParam = computeGuiScaleForEntity(living, iconSize);

            try {
                InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, iconCx, iconCy, scaleParam, 0f, 0f, living);
            } catch (Exception ex) {
                drawPlaceholder(graphics, iconCx - iconSize / 2, iconCy - iconSize / 2, iconSize);
            }
        } catch (Exception ex) {
            drawPlaceholder(graphics, iconCx - iconSize / 2, iconCy - iconSize / 2, iconSize);
        }
    }

    private void drawPlaceholder(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0x88000000);
    }

    private void drawProgressBar(GuiGraphics g, Font font, int x, int barY, int width) {
        int barX = x + PADDING;
        int barW = Math.max(30, width - PADDING * 2);
        // background
        g.fill(barX, barY, barX + barW, barY + 6, 0xFF1F1F1F);
        double filled = (double) tooltip.count() / (double) MobBundleItem.MAX_MOBS;
        int filledW = (int) (Math.max(0.0, Math.min(1.0, filled)) * barW);
        for (int i = 0; i < filledW; i++) {
            float t = (float) i / Math.max(1, barW - 1);
            int color = getRainbowColor(t);
            g.fill(barX + i, barY, barX + i + 1, barY + 6, color);
        }
    }

    private int computeGuiScaleForEntity(LivingEntity entity, int iconSize) {
        double width = Math.max(0.01, entity.getBbWidth());
        double height = Math.max(0.01, entity.getBbHeight());
        double maxDim = Math.max(width, height);
        double baseFactor = 32.0;
        int scale = (int) Math.round((iconSize / maxDim) * (baseFactor / 16.0));
        scale = Math.max(8, Math.min(300, scale));
        return scale;
    }

    private int getRainbowColor(float progress) {
        float hue = Math.max(0f, Math.min(1f, progress)) * 0.8f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }
}
