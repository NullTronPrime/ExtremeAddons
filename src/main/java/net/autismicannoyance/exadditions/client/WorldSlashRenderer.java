package net.autismicannoyance.exadditions.client;
import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.autismicannoyance.exadditions.network.WorldSlashPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldSlashRenderer {
    // Track active flying slashes with their start time and properties
    private static final Map<String, FlyingSlashData> activeSlashes = new ConcurrentHashMap<>();

    private static class FlyingSlashData {
        final Vec3 startPos;
        final Vec3 direction;
        final double width;
        final double height;
        final double range;
        final long startTime;
        final double speed;

        FlyingSlashData(Vec3 startPos, Vec3 direction, double width, double height, double range) {
            this.startPos = startPos;
            this.direction = direction;
            this.width = width;
            this.height = height;
            this.range = range;
            this.startTime = System.currentTimeMillis();
            this.speed = 0.8; // blocks per tick (20 ticks per second)
        }

        boolean isExpired() {
            long elapsed = System.currentTimeMillis() - startTime;
            double maxTime = (range / speed) * 50; // convert to milliseconds
            return elapsed > maxTime;
        }

        double getCurrentDistance() {
            long elapsed = System.currentTimeMillis() - startTime;
            return (elapsed / 50.0) * speed; // convert back to blocks
        }
    }

    public static void handleSlashPacket(WorldSlashPacket packet) {
        switch (packet.getSlashType()) {
            case CURVED:
                renderCurvedSlash(packet.getStartPos(), packet.getDirection(), packet.getParam1());
                break;
            case FLYING:
                // Create a unique ID for this slash
                String slashId = "slash_" + System.currentTimeMillis() + "_" + Math.random();
                FlyingSlashData slashData = new FlyingSlashData(
                        packet.getStartPos(),
                        packet.getDirection(),
                        packet.getParam1(),
                        packet.getParam2(),
                        packet.getParam3()
                );
                activeSlashes.put(slashId, slashData);
                break;
        }
    }

    // This method should be called every render frame to update flying slashes
    public static void updateFlyingSlashes() {
        // Remove expired slashes
        activeSlashes.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Render active slashes
        for (FlyingSlashData slash : activeSlashes.values()) {
            renderFlyingSlash(slash);
        }
    }

    private static void renderCurvedSlash(Vec3 playerPos, Vec3 lookDirection, double radius) {
        VectorRenderer.Transform slashTransform = VectorRenderer.Transform.IDENTITY;

        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = new Vec3(0, 1, 0);

        List<Vec3> curvePoints = generateCrescentCurve(playerPos, right, lookDirection, up, radius);

        for (int i = 0; i < curvePoints.size() - 2; i += 2) {
            Vec3 p1 = curvePoints.get(i);
            Vec3 p2 = curvePoints.get(i + 1);
            Vec3 p3 = curvePoints.get(i + 2);

            double distanceFromPlayer = p1.distanceTo(playerPos);
            double maxDistance = radius * 1.6;
            double transparencyFactor = Math.min(1.0, distanceFromPlayer / maxDistance);

            int baseAlpha = (int)(0x40 + (0xA0 * transparencyFactor));
            int color = (i / 2) % 2 == 0 ? (baseAlpha << 24) | 0x000000 : (baseAlpha << 24) | 0xFFFFFF;
            int[] colors = {color, color, color};

            VectorRenderer.drawPlaneWorld(p1, p2, p3, colors, true, 80, slashTransform);

            int edgeAlpha = (int)(0x80 + (0x7F * transparencyFactor));
            int edgeColor = (i / 2) % 2 == 0 ? (edgeAlpha << 24) | 0xFFFFFF : (edgeAlpha << 24) | 0x000000;
            VectorRenderer.drawLineWorld(p1, p2, edgeColor, 2.0f, true, 80, slashTransform);
            VectorRenderer.drawLineWorld(p2, p3, edgeColor, 2.0f, true, 80, slashTransform);
        }

        Vec3 tipPos = playerPos.add(right.scale(radius * 1.6));
        int tipColor = 0xFFFFFFFF;
        VectorRenderer.drawSphereWorld(tipPos, 0.3f, tipColor, 6, 4, false, 80, slashTransform);
    }

    private static void renderFlyingSlash(FlyingSlashData slash) {
        VectorRenderer.Transform slashTransform = VectorRenderer.Transform.IDENTITY;

        double currentDistance = slash.getCurrentDistance();
        if (currentDistance > slash.range) return;

        // Calculate current position of the slash
        Vec3 currentPos = slash.startPos.add(slash.direction.scale(currentDistance));

        // Calculate size growth over time (starts smaller, grows as it travels)
        double progress = currentDistance / slash.range;
        double sizeMultiplier = 0.6 + (progress * 0.8); // grows from 60% to 140% of original size
        double currentWidth = slash.width * sizeMultiplier;
        double currentHeight = slash.height * sizeMultiplier;

        // Calculate alpha at method level so it can be used throughout
        double alpha = Math.max(0.4, 1.0 - (progress * 0.6)); // fades as it travels

        Vec3 right = slash.direction.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = slash.direction.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = right.cross(slash.direction).normalize();

        // Generate the flying slash shape (similar to curved slash but oriented forward)
        List<Vec3> slashArc = generateFlyingSlashArc(currentPos, right, up, slash.direction, currentWidth, currentHeight);

        // Create alternating black and white segments like the curved slash
        for (int i = 0; i < slashArc.size() - 2; i += 2) {
            Vec3 p1 = slashArc.get(i);
            Vec3 p2 = slashArc.get(i + 1);
            Vec3 p3 = slashArc.get(i + 2);

            // Alternating colors with transparency that changes over distance
            int baseAlpha = (int)(0x60 + (0x80 * alpha));

            int color = (i / 2) % 2 == 0 ? (baseAlpha << 24) | 0x000000 : (baseAlpha << 24) | 0xFFFFFF;
            int[] colors = {color, color, color};

            VectorRenderer.drawPlaneWorld(p1, p2, p3, colors, true, 3, slashTransform);

            // Add edge lines for better definition
            int edgeAlpha = (int)(0xA0 * alpha);
            int edgeColor = (i / 2) % 2 == 0 ? (edgeAlpha << 24) | 0xFFFFFF : (edgeAlpha << 24) | 0x000000;
            VectorRenderer.drawLineWorld(p1, p2, edgeColor, 1.5f, true, 3, slashTransform);
            VectorRenderer.drawLineWorld(p2, p3, edgeColor, 1.5f, true, 3, slashTransform);
        }

        // Add a glowing core at the center of the slash
        int coreAlpha = (int)(0xC0 * alpha);
        int coreColor = (coreAlpha << 24) | 0xFFFFFF;
        VectorRenderer.drawSphereWorld(currentPos, 0.3f * (float)sizeMultiplier, coreColor, 6, 4, false, 3, slashTransform);

        // Add trailing effect for motion blur
        if (currentDistance > 2.0) {
            Vec3 trailPos = currentPos.subtract(slash.direction.scale(2.0));
            int trailAlpha = (int)(0x40 * alpha);
            int trailColor = (trailAlpha << 24) | 0x808080;
            VectorRenderer.drawSphereWorld(trailPos, 0.2f * (float)sizeMultiplier, trailColor, 4, 3, false, 3, slashTransform);
        }
    }
    private static List<Vec3> generateCrescentCurve(Vec3 center, Vec3 right, Vec3 forward, Vec3 up, double radius) {
        List<Vec3> points = new ArrayList<>();

        double height = 6.0;
        double thickness = 2.0;
        int segments = 24;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;

            double angle = Math.PI * (t - 0.5) * 0.9;

            double sharpnessFactor = 1.0 - Math.pow(Math.abs(t - 0.5) * 2, 1.8);
            double curveRadius = radius * (0.6 + 0.4 * Math.sin(t * Math.PI));
            double extendRadius = curveRadius + (sharpnessFactor * radius * 0.8);

            double x = Math.sin(angle) * extendRadius;
            double z = Math.cos(angle) * extendRadius;

            double heightVariation = Math.sin(t * Math.PI) * height * 0.4;
            double y = heightVariation + (sharpnessFactor * height * 0.3);

            Vec3 curveCenter = center.add(right.scale(x)).add(forward.scale(z)).add(up.scale(y));

            double currentThickness = thickness * (0.5 + sharpnessFactor * 0.8);
            Vec3 innerPoint = curveCenter.add(up.scale(-currentThickness * 0.5));
            Vec3 outerPoint = curveCenter.add(up.scale(currentThickness * 0.5));

            points.add(innerPoint);
            points.add(outerPoint);
        }

        return points;
    }

    private static List<Vec3> generateFlyingSlashArc(Vec3 center, Vec3 right, Vec3 up, Vec3 forward, double width, double height) {
        List<Vec3> points = new ArrayList<>();

        int segments = 20;
        double thickness = 1.5; // Slightly thicker for flying slash

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;

            // Create a crescent/arc shape similar to the curved slash
            double angle = Math.PI * (t - 0.5) * 1.2; // Slightly wider arc

            double sharpnessFactor = 1.0 - Math.pow(Math.abs(t - 0.5) * 2, 1.5);
            double extendFactor = 0.7 + sharpnessFactor * 0.5;

            double x = Math.sin(angle) * width * 0.5 * extendFactor;
            double y = Math.sin(t * Math.PI) * height * 0.3; // Vertical variation

            // Add slight forward curve to make it look more dynamic
            double forwardOffset = Math.cos(angle) * width * 0.1;

            Vec3 arcCenter = center
                    .add(right.scale(x))
                    .add(up.scale(y))
                    .add(forward.scale(forwardOffset));

            double currentThickness = thickness * (0.6 + sharpnessFactor * 0.4);
            Vec3 innerPoint = arcCenter.add(forward.scale(-currentThickness * 0.5));
            Vec3 outerPoint = arcCenter.add(forward.scale(currentThickness * 0.5));

            points.add(innerPoint);
            points.add(outerPoint);
        }

        return points;
    }
}