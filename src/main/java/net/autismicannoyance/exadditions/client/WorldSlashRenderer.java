package net.autismicannoyance.exadditions.client;

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

    /* ---------------------------
       Curved slash (stationary)
       --------------------------- */
    private static void renderCurvedSlash(Vec3 playerPos, Vec3 lookDirection, double radius) {
        int layers = 8;
        double layerSpacing = 0.03;

        for (int layer = 0; layer < layers; layer++) {
            double layerOffset = layer * layerSpacing;
            double layerAlpha = 1.0 - (layer * 0.1);
            renderSlashLayer(playerPos, lookDirection, radius, layerOffset, layerAlpha);
        }

        renderSlashCore(playerPos, lookDirection, radius);
        renderSlashParticles(playerPos, lookDirection, radius);
    }

    /**
     * Horizontal-plane crescent. Key change:
     * arcDir = forwardFlat * cos(angle) + right * sin(angle)
     * so angle==0 => arcDir points forward (in front of player).
     */
    private static void renderSlashLayer(Vec3 center, Vec3 lookDirection, double radius, double offset, double alphaMultiplier) {
        Vec3 forward = safeNormalize(lookDirection);
        Vec3 worldUp = new Vec3(0, 1, 0);

        // keep the arc parallel to ground
        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        // RIGHT should be forward x up (so it points the expected lateral direction)
        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 up = worldUp;

        // move the whole arc forward so it sits in front of the player
        double forwardOffsetFactor = 0.5;
        Vec3 origin = center.add(forwardFlat.scale(radius * forwardOffsetFactor + offset)).add(up.scale(-0.18));

        List<Vec3> innerArc = new ArrayList<>();
        List<Vec3> outerArc = new ArrayList<>();

        int segments = 40;
        double arcSpan = Math.PI * 0.8;
        double startAngle = -arcSpan / 2.0;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (arcSpan * t);

            double thickness = Math.sin(t * Math.PI) * 0.3 + 0.1;
            double baseRadius = radius;

            double innerRadius = baseRadius * (0.7 - thickness);
            double outerRadius = baseRadius * (0.7 + thickness);

            // IMPORTANT: forwardFlat * cos + right * sin so center faces forward
            Vec3 arcDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));

            double verticalBulge = Math.sin(t * Math.PI) * radius * 0.06;
            Vec3 bulge = up.scale(verticalBulge);

            Vec3 innerPoint = origin.add(arcDir.scale(innerRadius)).add(bulge);
            Vec3 outerPoint = origin.add(arcDir.scale(outerRadius)).add(bulge);

            innerArc.add(innerPoint);
            outerArc.add(outerPoint);
        }

        for (int i = 0; i < segments; i++) {
            Vec3 innerCurrent = innerArc.get(i);
            Vec3 innerNext = innerArc.get(i + 1);
            Vec3 outerCurrent = outerArc.get(i);
            Vec3 outerNext = outerArc.get(i + 1);

            double positionAlpha = 1.0 - (Math.abs(i - segments / 2.0) / (segments / 2.0)) * 0.3;
            int alpha = (int) (255 * alphaMultiplier * positionAlpha * 0.85);

            int r = 200 + (int) (55 * positionAlpha);
            int g = 255;
            int b = 255;
            int color = (alpha << 24) | (r << 16) | (g << 8) | b;

            int[] colors = {color, color, color};

            VectorRenderer.drawPlaneWorld(innerCurrent, outerCurrent, outerNext, colors, true, 40, VectorRenderer.Transform.IDENTITY);
            VectorRenderer.drawPlaneWorld(innerCurrent, outerNext, innerNext, colors, true, 40, VectorRenderer.Transform.IDENTITY);
        }

        if (offset < 0.01) {
            renderSlashEdgeGlow(innerArc, outerArc);
        }
    }

    private static void renderSlashCore(Vec3 center, Vec3 lookDirection, double radius) {
        Vec3 forward = safeNormalize(lookDirection);
        Vec3 worldUp = new Vec3(0, 1, 0);

        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 up = worldUp;

        double forwardOffsetFactor = 0.5;
        Vec3 origin = center.add(forwardFlat.scale(radius * forwardOffsetFactor)).add(up.scale(-0.18));

        List<Vec3> coreLine = new ArrayList<>();
        int segments = 40;
        double arcSpan = Math.PI * 1.1;
        double startAngle = -arcSpan / 2.0;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (arcSpan * t);

            double crescentProfile = Math.sin(t * Math.PI);
            double coreRadius = radius * 0.95 * (0.4 + 0.6 * crescentProfile);
            double forwardOffset = Math.sin(t * Math.PI) * radius * 0.18;

            // again use forwardFlat*cos + right*sin
            Vec3 point = origin
                    .add(forwardFlat.scale(Math.cos(angle) * coreRadius))
                    .add(right.scale(Math.sin(angle) * coreRadius * 0.05))
                    .add(forwardFlat.scale(forwardOffset));

            coreLine.add(point);
        }

        for (int i = 0; i < coreLine.size() - 1; i++) {
            VectorRenderer.drawLineWorld(
                    coreLine.get(i),
                    coreLine.get(i + 1),
                    0xFFFFFFFF,
                    6.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );

            VectorRenderer.drawLineWorld(
                    coreLine.get(i),
                    coreLine.get(i + 1),
                    0x8000FFFF,
                    12.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );
        }
    }

    private static void renderSlashEdgeGlow(List<Vec3> innerArc, List<Vec3> outerArc) {
        for (int i = 0; i < innerArc.size() - 1; i++) {
            VectorRenderer.drawLineWorld(
                    innerArc.get(i),
                    innerArc.get(i + 1),
                    0xCCFFFFFF,
                    3.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );

            VectorRenderer.drawLineWorld(
                    outerArc.get(i),
                    outerArc.get(i + 1),
                    0xCC00FFFF,
                    4.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );
        }

        if (!innerArc.isEmpty() && !outerArc.isEmpty()) {
            VectorRenderer.drawLineWorld(
                    innerArc.get(0),
                    outerArc.get(0),
                    0xAAFFFFFF,
                    3.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );

            VectorRenderer.drawLineWorld(
                    innerArc.get(innerArc.size() - 1),
                    outerArc.get(outerArc.size() - 1),
                    0xAAFFFFFF,
                    3.0f,
                    true,
                    40,
                    VectorRenderer.Transform.IDENTITY
            );
        }
    }

    private static void renderSlashParticles(Vec3 center, Vec3 lookDirection, double radius) {
        Vec3 forward = safeNormalize(lookDirection);
        Vec3 worldUp = new Vec3(0, 1, 0);

        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 up = worldUp;

        double forwardOffsetFactor = 0.5;
        Vec3 origin = center.add(forwardFlat.scale(radius * forwardOffsetFactor)).add(up.scale(-0.18));

        int particleCount = 20;
        double arcSpan = Math.PI * 1.1;
        double startAngle = -arcSpan / 2.0;

        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / (particleCount - 1);
            double angle = startAngle + (arcSpan * t);

            double crescentProfile = Math.sin(t * Math.PI);
            double particleRadius = radius * (0.7 + 0.5 * crescentProfile + Math.random() * 0.3);
            double forwardOffset = Math.sin(t * Math.PI) * radius * 0.12;

            Vec3 particlePos = origin
                    .add(forwardFlat.scale(Math.cos(angle) * particleRadius))
                    .add(right.scale(Math.sin(angle) * particleRadius * 0.02))
                    .add(up.scale(Math.sin(angle) * particleRadius * 0.03))
                    .add(forwardFlat.scale(forwardOffset));

            VectorRenderer.drawSphereWorld(
                    particlePos,
                    0.04f + (float) (Math.random() * 0.03),
                    0xBB00FFFF,
                    6, 8,
                    false,
                    30 + (int) (Math.random() * 20),
                    VectorRenderer.Transform.IDENTITY
            );
        }
    }

    /* ---------------------------
       Flying slash (projectile)
       --------------------------- */
    private static void startFlyingSlash(Vec3 startPos, Vec3 direction, double width, double height, double range) {
        int slashId = nextSlashId++;
        slashStartTimes.put(slashId, System.currentTimeMillis());

        FlyingSlash flyingSlash = new FlyingSlash(slashId, startPos, safeNormalize(direction), width, height, range);
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
            return true;
        }

        Vec3 currentPos = slash.startPos.add(slash.direction.scale(elapsedSeconds * slashSpeed));

        double progress = elapsedSeconds / totalDuration;
        double sizeMultiplier = 1.0 + (progress * 0.5);
        double currentWidth = slash.width * sizeMultiplier;

        renderFlyingSlashEffect(currentPos, slash.direction, currentWidth, progress);

        return false;
    }

    private static void renderFlyingSlashEffect(Vec3 center, Vec3 direction, double width, double progress) {
        Vec3 forward = safeNormalize(direction);
        Vec3 worldUp = new Vec3(0, 1, 0);

        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 up = worldUp;

        Vec3 origin = center.add(forwardFlat.scale(width * 0.6)).add(up.scale(-0.18));

        List<Vec3> innerArc = new ArrayList<>();
        List<Vec3> outerArc = new ArrayList<>();

        int segments = 20;
        double arcSpan = Math.PI * 0.6;
        double startAngle = -arcSpan / 2.0;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (arcSpan * t);

            double thickness = Math.sin(t * Math.PI) * 0.2 + 0.05;
            double baseRadius = width * 0.4;

            double innerRadius = baseRadius * (0.8 - thickness);
            double outerRadius = baseRadius * (0.8 + thickness);

            Vec3 arcDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));

            Vec3 innerPoint = origin.add(arcDir.scale(innerRadius));
            Vec3 outerPoint = origin.add(arcDir.scale(outerRadius));

            innerArc.add(innerPoint);
            outerArc.add(outerPoint);
        }

        int alpha = (int) (255 * (1.0 - progress * 0.5));
        int baseColor = (alpha << 24) | 0x00FFFF;
        for (int i = 0; i < segments; i++) {
            int[] colors = {baseColor, baseColor, baseColor};

            VectorRenderer.drawPlaneWorld(
                    innerArc.get(i), outerArc.get(i), outerArc.get(i + 1),
                    colors, true, 5, VectorRenderer.Transform.IDENTITY
            );
            VectorRenderer.drawPlaneWorld(
                    innerArc.get(i), outerArc.get(i + 1), innerArc.get(i + 1),
                    colors, true, 5, VectorRenderer.Transform.IDENTITY
            );
        }

        for (int i = 0; i < innerArc.size() - 1; i++) {
            Vec3 midPoint = innerArc.get(i).add(outerArc.get(i)).scale(0.5);
            Vec3 midPointNext = innerArc.get(i + 1).add(outerArc.get(i + 1)).scale(0.5);

            VectorRenderer.drawLineWorld(
                    midPoint, midPointNext,
                    (alpha << 24) | 0xFFFFFF,
                    3.0f, true, 5, VectorRenderer.Transform.IDENTITY
            );
        }
    }

    // helper: safely normalize Vec3 (avoid zero-length)
    private static Vec3 safeNormalize(Vec3 v) {
        if (v == null) return new Vec3(0, 0, 1);
        double len = v.length();
        if (len < 1e-6) return new Vec3(0, 0, 1);
        return v.scale(1.0 / len);
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
