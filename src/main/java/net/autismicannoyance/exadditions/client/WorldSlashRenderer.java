package net.autismicannoyance.exadditions.client;
import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.autismicannoyance.exadditions.network.WorldSlashPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class WorldSlashRenderer {

    public static void handleSlashPacket(WorldSlashPacket packet) {
        switch (packet.getSlashType()) {
            case CURVED:
                renderCurvedSlash(packet.getStartPos(), packet.getDirection(), packet.getParam1());
                break;
            case FLYING:
                renderFlyingSlash(packet.getStartPos(), packet.getDirection(),
                        packet.getParam1(), packet.getParam2(), packet.getParam3());
                break;
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

    private static void renderFlyingSlash(Vec3 startPos, Vec3 direction, double width, double height, double range) {
        VectorRenderer.Transform slashTransform = VectorRenderer.Transform.IDENTITY;

        Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = direction.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = right.cross(direction).normalize();

        double slashSpeed = 0.15;
        int totalLifetime = (int)(range / slashSpeed);

        long currentTime = System.currentTimeMillis();
        double timeOffset = (currentTime % (totalLifetime * 50)) / 50.0;

        if (timeOffset < totalLifetime) {
            Vec3 currentPos = startPos.add(direction.scale(timeOffset * slashSpeed));

            double sizeMultiplier = 1.0 + (timeOffset * 0.3 / totalLifetime);
            double currentWidth = width * sizeMultiplier;
            double currentHeight = height * sizeMultiplier;

            List<Vec3> slashArc = generateTravelingSlashArc(currentPos, right, up, direction, currentWidth, currentHeight);

            for (int i = 0; i < slashArc.size() - 2; i += 2) {
                Vec3 p1 = slashArc.get(i);
                Vec3 p2 = slashArc.get(i + 1);
                Vec3 p3 = slashArc.get(i + 2);

                int tick = (int)timeOffset;
                int color = tick % 2 == 0 ? 0xE0000000 : 0xE0FFFFFF;
                int[] colors = {color, color, color};

                VectorRenderer.drawPlaneWorld(p1, p2, p3, colors, true, 3, slashTransform);
            }

            int coreColor = ((int)timeOffset) % 2 == 0 ? 0xA0FFFFFF : 0xA0000000;
            VectorRenderer.drawSphereWorld(currentPos, 0.4f, coreColor, 6, 4, false, 3, slashTransform);
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

    private static List<Vec3> generateTravelingSlashArc(Vec3 center, Vec3 right, Vec3 up, Vec3 forward, double width, double height) {
        List<Vec3> points = new ArrayList<>();

        int segments = 20;
        double thickness = 1.0;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;

            double angle = Math.PI * (t - 0.5) * 1.4;

            double sharpnessFactor = 1.0 - Math.pow(Math.abs(t - 0.5) * 2, 1.5);
            double extendFactor = 0.7 + sharpnessFactor * 0.5;

            double x = Math.sin(angle) * width * 0.5 * extendFactor;
            double forwardCurve = Math.cos(angle) * height * 0.3;

            double heightVariation = Math.sin(t * Math.PI) * width * 0.1;

            Vec3 arcCenter = center
                    .add(right.scale(x))
                    .add(forward.scale(forwardCurve))
                    .add(up.scale(heightVariation));

            double currentThickness = thickness * (0.6 + sharpnessFactor * 0.4);
            Vec3 innerPoint = arcCenter.add(up.scale(-currentThickness * 0.5));
            Vec3 outerPoint = arcCenter.add(up.scale(currentThickness * 0.5));

            points.add(innerPoint);
            points.add(outerPoint);
        }

        return points;
    }
}