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
        VectorRenderer.Transform slashTransform = VectorRenderer.Transform.IDENTITY;

        // Create the horizontal curved slash - flat like the reference image
        List<Vec3> arcPoints = generateFlatCrescentArc(playerPos, lookDirection, radius);

        // Render as a series of connected quads to form the flat crescent shape
        renderFlatSlash(arcPoints, 0xDDFFFFFF, 60, slashTransform);

        // Add bright glowing edges
        renderSlashEdges(arcPoints, 0xFFFFFFFF, 3.0f, 60, slashTransform);
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

        // Generate flying slash arc
        List<Vec3> slashArc = generateFlyingSlashArc(currentPos, slash.direction, currentWidth);

        // Render the flying slash
        int color = (int)(elapsedSeconds * 10) % 2 == 0 ? 0xC0FFFFFF : 0xC000FFFF;
        renderFlatSlash(slashArc, color, 10, VectorRenderer.Transform.IDENTITY);

        return false; // Not expired
    }

    private static List<Vec3> generateFlatCrescentArc(Vec3 center, Vec3 lookDirection, double radius) {
        List<Vec3> points = new ArrayList<>();

        // Calculate perpendicular vector for the horizontal arc
        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }

        // Create a flat crescent arc in the horizontal plane
        int segments = 40; // High resolution for smooth curve
        double arcSpan = Math.PI * 0.8; // About 144 degrees
        double startAngle = -arcSpan / 2;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (arcSpan * t);

            // Create the crescent shape - wider in the middle, tapered at ends
            double distanceFromCenter = Math.sin(t * Math.PI); // 0 at ends, 1 in middle
            double currentRadius = radius * (0.3 + 0.7 * distanceFromCenter);

            // Position on the arc
            double x = Math.cos(angle) * currentRadius;
            double z = Math.sin(angle) * currentRadius;

            Vec3 arcPoint = center
                    .add(lookDirection.scale(x))  // Forward/back movement
                    .add(right.scale(z));         // Left/right movement

            points.add(arcPoint);
        }

        return points;
    }

    private static List<Vec3> generateFlyingSlashArc(Vec3 center, Vec3 direction, double width) {
        List<Vec3> points = new ArrayList<>();

        // Create perpendicular vector
        Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = direction.cross(new Vec3(1, 0, 0)).normalize();
        }

        // Create horizontal arc for flying slash
        int segments = 24;
        double arcSpan = Math.PI * 0.6; // Smaller arc for flying slash
        double startAngle = -arcSpan / 2;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (arcSpan * t);

            // Tapered crescent
            double distanceFromCenter = Math.sin(t * Math.PI);
            double currentWidth = width * 0.5 * (0.2 + 0.8 * distanceFromCenter);

            double x = Math.cos(angle) * currentWidth;
            double z = Math.sin(angle) * currentWidth;

            Vec3 arcPoint = center
                    .add(direction.scale(x * 0.3))  // Slight forward curve
                    .add(right.scale(z));

            points.add(arcPoint);
        }

        return points;
    }

    private static void renderFlatSlash(List<Vec3> points, int color, int lifetime, VectorRenderer.Transform transform) {
        if (points.size() < 3) return;

        double slashThickness = 0.08; // Very thin slash

        // Create the flat slash by connecting adjacent points with thin quads
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);

            // Calculate a perpendicular vector for thickness
            Vec3 direction = p2.subtract(p1).normalize();
            Vec3 thickness = new Vec3(0, 1, 0).cross(direction).normalize().scale(slashThickness);

            // Create a thin quad between the two points
            Vec3 p1Top = p1.add(thickness);
            Vec3 p1Bottom = p1.subtract(thickness);
            Vec3 p2Top = p2.add(thickness);
            Vec3 p2Bottom = p2.subtract(thickness);

            // Two triangles to form the quad
            int[] colors = {color, color, color};

            // Top triangle
            VectorRenderer.drawPlaneWorld(p1Top, p2Top, p2Bottom, colors, true, lifetime, transform);
            // Bottom triangle
            VectorRenderer.drawPlaneWorld(p1Top, p2Bottom, p1Bottom, colors, true, lifetime, transform);
        }
    }

    private static void renderSlashEdges(List<Vec3> points, int edgeColor, float thickness, int lifetime, VectorRenderer.Transform transform) {
        // Draw the outer edge of the crescent
        for (int i = 0; i < points.size() - 1; i++) {
            VectorRenderer.drawLineWorld(points.get(i), points.get(i + 1), edgeColor, thickness, true, lifetime, transform);
        }

        // Add some inner glow lines for extra effect
        if (points.size() > 4) {
            int quarterPoint = points.size() / 4;
            int threeQuarterPoint = (3 * points.size()) / 4;

            // Inner curved lines
            for (int i = quarterPoint; i < threeQuarterPoint - 1; i++) {
                Vec3 p1 = points.get(i);
                Vec3 p2 = points.get(i + 1);
                Vec3 center = p1.add(p2).scale(0.5);

                // Slightly inward offset
                Vec3 inwardPoint = center.scale(0.9);
                VectorRenderer.drawLineWorld(p1, inwardPoint, edgeColor & 0x7FFFFFFF, thickness * 0.5f, true, lifetime, transform);
                VectorRenderer.drawLineWorld(inwardPoint, p2, edgeColor & 0x7FFFFFFF, thickness * 0.5f, true, lifetime, transform);
            }
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