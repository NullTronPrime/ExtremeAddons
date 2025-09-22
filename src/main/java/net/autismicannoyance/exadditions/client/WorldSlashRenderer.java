package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.autismicannoyance.exadditions.network.WorldSlashPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorldSlashRenderer {

    // Flying slash management
    private static final ConcurrentLinkedQueue<FlyingSlash> flyingSlashes = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<Integer, Long> slashStartTimes = new ConcurrentHashMap<>();
    private static int nextSlashId = 0;

    public static void handleSlashPacket(WorldSlashPacket packet) {
        switch (packet.getSlashType()) {
            case CURVED:
                renderCurvedSlash(packet.getStartPos(), packet.getDirection(), packet.getParam1());
                break;
            case FLYING:
                startFlyingSlash(packet.getStartPos(), packet.getDirection(),
                        packet.getParam1(), packet.getParam2(), packet.getParam3());
                break;
        }
    }

    private static void renderCurvedSlash(Vec3 playerPos, Vec3 lookDirection, double radius) {
        // Create multiple layers for a 3D effect like in the reference
        int layers = 8;
        double layerSpacing = 0.03;

        for (int layer = 0; layer < layers; layer++) {
            double layerOffset = layer * layerSpacing;
            double layerAlpha = 1.0 - (layer * 0.1); // Fade outer layers

            // Main curved slash body
            renderSlashLayer(playerPos, lookDirection, radius, layerOffset, layerAlpha);
        }

        // Add bright core glow
        renderSlashCore(playerPos, lookDirection, radius);

        // Add energy particles along the arc
        renderSlashParticles(playerPos, lookDirection, radius);
    }

    private static void renderSlashLayer(Vec3 center, Vec3 lookDirection, double radius, double offset, double alphaMultiplier) {
        // Calculate perpendicular vectors for proper 3D orientation
        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = right.cross(lookDirection).normalize();

        // Generate the curved slash arc points
        List<Vec3> innerArc = new ArrayList<>();
        List<Vec3> outerArc = new ArrayList<>();

        int segments = 60; // High resolution for smooth curve
        double arcAngle = Math.PI * 0.7; // About 126 degrees for a nice curved sweep
        double startAngle = -arcAngle / 2;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (arcAngle * t);

            // Create the crescent shape with proper 3D curvature
            double crescentProfile = Math.sin(t * Math.PI); // Peak in middle
            double innerRadius = radius * 0.7 * (0.2 + 0.8 * crescentProfile);
            double outerRadius = radius * (0.3 + 0.7 * crescentProfile);

            // Apply slight forward curve for 3D effect
            double forwardOffset = Math.sin(angle) * radius * 0.15;

            // Calculate positions with 3D rotation
            Vec3 basePoint = center
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius * 0.3)) // Slight vertical curve
                    .add(lookDirection.scale(forwardOffset + offset));

            // Inner edge point
            Vec3 innerPoint = center
                    .add(right.scale(Math.cos(angle) * innerRadius))
                    .add(up.scale(Math.sin(angle) * innerRadius * 0.3))
                    .add(lookDirection.scale(forwardOffset * 0.7 + offset));

            // Outer edge point
            Vec3 outerPoint = center
                    .add(right.scale(Math.cos(angle) * outerRadius))
                    .add(up.scale(Math.sin(angle) * outerRadius * 0.3))
                    .add(lookDirection.scale(forwardOffset * 1.2 + offset));

            innerArc.add(innerPoint);
            outerArc.add(outerPoint);
        }

        // Render the slash surface with gradient coloring
        for (int i = 0; i < segments; i++) {
            Vec3 innerCurrent = innerArc.get(i);
            Vec3 innerNext = innerArc.get(i + 1);
            Vec3 outerCurrent = outerArc.get(i);
            Vec3 outerNext = outerArc.get(i + 1);

            // Calculate gradient alpha based on position
            double positionAlpha = 1.0 - (Math.abs(i - segments/2.0) / (segments/2.0)) * 0.3;
            int alpha = (int)(255 * alphaMultiplier * positionAlpha * 0.85);

            // Cyan-white gradient color like the reference
            int r = 200 + (int)(55 * positionAlpha);
            int g = 255;
            int b = 255;
            int color = (alpha << 24) | (r << 16) | (g << 8) | b;

            // Create quad with per-vertex colors for gradient
            int[] colors = {color, color, color};

            // First triangle
            VectorRenderer.drawPlaneWorld(innerCurrent, outerCurrent, outerNext, colors, true, 40, VectorRenderer.Transform.IDENTITY);
            // Second triangle
            VectorRenderer.drawPlaneWorld(innerCurrent, outerNext, innerNext, colors, true, 40, VectorRenderer.Transform.IDENTITY);
        }

        // Add edge glow lines
        if (offset < 0.01) { // Only on the main layer
            renderSlashEdgeGlow(innerArc, outerArc);
        }
    }

    private static void renderSlashCore(Vec3 center, Vec3 lookDirection, double radius) {
        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = right.cross(lookDirection).normalize();

        // Create bright white core line through the middle
        List<Vec3> coreLine = new ArrayList<>();
        int segments = 40;
        double arcAngle = Math.PI * 0.65;
        double startAngle = -arcAngle / 2;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (arcAngle * t);

            double crescentProfile = Math.sin(t * Math.PI);
            double coreRadius = radius * 0.85 * (0.3 + 0.7 * crescentProfile);
            double forwardOffset = Math.sin(angle) * radius * 0.1;

            Vec3 corePoint = center
                    .add(right.scale(Math.cos(angle) * coreRadius))
                    .add(up.scale(Math.sin(angle) * coreRadius * 0.3))
                    .add(lookDirection.scale(forwardOffset));

            coreLine.add(corePoint);
        }

        // Render bright white core
        for (int i = 0; i < coreLine.size() - 1; i++) {
            VectorRenderer.drawLineWorld(
                    coreLine.get(i),
                    coreLine.get(i + 1),
                    0xFFFFFFFF, // Pure white
                    4.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );

            // Add secondary glow line
            VectorRenderer.drawLineWorld(
                    coreLine.get(i),
                    coreLine.get(i + 1),
                    0x8000FFFF, // Semi-transparent cyan
                    8.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );
        }
    }

    private static void renderSlashEdgeGlow(List<Vec3> innerArc, List<Vec3> outerArc) {
        // Bright edge highlights
        for (int i = 0; i < innerArc.size() - 1; i++) {
            // Inner edge glow
            VectorRenderer.drawLineWorld(
                    innerArc.get(i),
                    innerArc.get(i + 1),
                    0xCCFFFFFF, // Bright white-cyan
                    2.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );

            // Outer edge glow
            VectorRenderer.drawLineWorld(
                    outerArc.get(i),
                    outerArc.get(i + 1),
                    0xCC00FFFF, // Bright cyan
                    3.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );
        }

        // Add end caps
        if (!innerArc.isEmpty() && !outerArc.isEmpty()) {
            // Start cap
            VectorRenderer.drawLineWorld(
                    innerArc.get(0),
                    outerArc.get(0),
                    0xAAFFFFFF,
                    2.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );

            // End cap
            VectorRenderer.drawLineWorld(
                    innerArc.get(innerArc.size() - 1),
                    outerArc.get(outerArc.size() - 1),
                    0xAAFFFFFF,
                    2.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );
        }
    }

    private static void renderSlashParticles(Vec3 center, Vec3 lookDirection, double radius) {
        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = right.cross(lookDirection).normalize();

        // Add energy particles along the slash
        int particleCount = 15;
        double arcAngle = Math.PI * 0.7;
        double startAngle = -arcAngle / 2;

        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / (particleCount - 1);
            double angle = startAngle + (arcAngle * t);

            double crescentProfile = Math.sin(t * Math.PI);
            double particleRadius = radius * (0.5 + 0.5 * crescentProfile + Math.random() * 0.2);
            double forwardOffset = Math.sin(angle) * radius * 0.1;

            Vec3 particlePos = center
                    .add(right.scale(Math.cos(angle) * particleRadius))
                    .add(up.scale(Math.sin(angle) * particleRadius * 0.3))
                    .add(lookDirection.scale(forwardOffset));

            // Small glowing sphere particles
            VectorRenderer.drawSphereWorld(
                    particlePos,
                    0.03f + (float)(Math.random() * 0.02),
                    0xBB00FFFF, // Cyan glow
                    6, 8,
                    false,
                    30 + (int)(Math.random() * 20),
                    VectorRenderer.Transform.IDENTITY
            );
        }
    }

    private static void startFlyingSlash(Vec3 startPos, Vec3 direction, double width, double height, double range) {
        int slashId = nextSlashId++;
        slashStartTimes.put(slashId, System.currentTimeMillis());

        FlyingSlash flyingSlash = new FlyingSlash(slashId, startPos, direction, width, height, range);
        flyingSlashes.add(flyingSlash);
    }

    public static void updateFlyingSlashes() {
        long currentTime = System.currentTimeMillis();
        flyingSlashes.removeIf(slash -> {
            boolean expired = updateFlyingSlash(slash, currentTime);
            if (expired) {
                slashStartTimes.remove(slash.id);
            }
            return expired;
        });
    }

    private static boolean updateFlyingSlash(FlyingSlash slash, long currentTime) {
        Long startTime = slashStartTimes.get(slash.id);
        if (startTime == null) return true;

        double elapsedSeconds = (currentTime - startTime) / 1000.0;
        double slashSpeed = 0.8;
        double totalDuration = slash.range / slashSpeed;

        if (elapsedSeconds > totalDuration) {
            return true; // Expired
        }

        // Calculate current position
        Vec3 currentPos = slash.startPos.add(slash.direction.scale(elapsedSeconds * slashSpeed));

        // Size grows over time
        double progress = elapsedSeconds / totalDuration;
        double sizeMultiplier = 1.0 + (progress * 0.5);
        double currentWidth = slash.width * sizeMultiplier;

        // Render the flying slash with similar style
        renderFlyingSlashEffect(currentPos, slash.direction, currentWidth, progress);

        return false; // Not expired
    }

    private static void renderFlyingSlashEffect(Vec3 center, Vec3 direction, double width, double progress) {
        // Create a smaller version of the curved slash that flies forward
        Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = direction.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = right.cross(direction).normalize();

        // Generate flying slash arc
        List<Vec3> innerArc = new ArrayList<>();
        List<Vec3> outerArc = new ArrayList<>();

        int segments = 30;
        double arcAngle = Math.PI * 0.5; // Smaller arc for flying slash
        double startAngle = -arcAngle / 2;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (arcAngle * t);

            double crescentProfile = Math.sin(t * Math.PI);
            double innerRadius = width * 0.3 * (0.2 + 0.8 * crescentProfile);
            double outerRadius = width * 0.5 * (0.3 + 0.7 * crescentProfile);

            Vec3 innerPoint = center
                    .add(right.scale(Math.cos(angle) * innerRadius))
                    .add(up.scale(Math.sin(angle) * innerRadius * 0.2));

            Vec3 outerPoint = center
                    .add(right.scale(Math.cos(angle) * outerRadius))
                    .add(up.scale(Math.sin(angle) * outerRadius * 0.2));

            innerArc.add(innerPoint);
            outerArc.add(outerPoint);
        }

        // Render with fading alpha based on progress
        int alpha = (int)(255 * (1.0 - progress * 0.5));
        int color = (alpha << 24) | 0x00FFFF; // Cyan

        for (int i = 0; i < segments; i++) {
            int[] colors = {color, color, color};

            VectorRenderer.drawPlaneWorld(
                    innerArc.get(i), outerArc.get(i), outerArc.get(i + 1),
                    colors, true, 5, VectorRenderer.Transform.IDENTITY
            );
            VectorRenderer.drawPlaneWorld(
                    innerArc.get(i), outerArc.get(i + 1), innerArc.get(i + 1),
                    colors, true, 5, VectorRenderer.Transform.IDENTITY
            );
        }

        // Add core glow
        for (int i = 0; i < innerArc.size() - 1; i++) {
            Vec3 midPoint = innerArc.get(i).add(outerArc.get(i)).scale(0.5);
            Vec3 midPointNext = innerArc.get(i + 1).add(outerArc.get(i + 1)).scale(0.5);

            VectorRenderer.drawLineWorld(
                    midPoint, midPointNext,
                    (alpha << 24) | 0xFFFFFF, // White core
                    3.0f, true, 5, VectorRenderer.Transform.IDENTITY
            );
        }
    }

    // Flying slash data class
    private static class FlyingSlash {
        final int id;
        final Vec3 startPos;
        final Vec3 direction;
        final double width;
        final double height;
        final double range;

        FlyingSlash(int id, Vec3 startPos, Vec3 direction, double width, double height, double range) {
            this.id = id;
            this.startPos = startPos;
            this.direction = direction;
            this.width = width;
            this.height = height;
            this.range = range;
        }
    }
}