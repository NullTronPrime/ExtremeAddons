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
       Curved slash (stationary) - Enhanced to match the flowing wave image
       --------------------------- */
    private static void renderCurvedSlash(Vec3 playerPos, Vec3 lookDirection, double radius) {
        // Create multiple flowing wave layers with different timings
        int totalLayers = 12;
        for (int layer = 0; layer < totalLayers; layer++) {
            double layerDelay = layer * 2; // Stagger the layers over time
            double layerAlpha = Math.max(0.1, 1.0 - (layer * 0.08));
            double layerScale = 1.0 + (layer * 0.05);

            // Schedule each layer to appear slightly delayed
            scheduleSlashLayer(playerPos, lookDirection, radius, layer, layerAlpha, layerScale, layerDelay);
        }

        // Core energy trail
        renderSlashEnergyCore(playerPos, lookDirection, radius);

        // Bright particles and energy effects
        renderSlashEnergyParticles(playerPos, lookDirection, radius);
    }

    private static void scheduleSlashLayer(Vec3 center, Vec3 lookDirection, double radius, int layerIndex,
                                           double alphaMultiplier, double scale, double delay) {
        // For now, render immediately - in a real implementation you'd use a scheduler
        renderFlowingSlashLayer(center, lookDirection, radius, layerIndex, alphaMultiplier, scale);
    }

    /**
     * Creates a flowing wave effect similar to the reference image
     */
    private static void renderFlowingSlashLayer(Vec3 center, Vec3 lookDirection, double radius,
                                                int layerIndex, double alphaMultiplier, double scale) {
        Vec3 forward = safeNormalize(lookDirection);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 up = worldUp;

        // Position the wave in front of the player
        Vec3 origin = center.add(forwardFlat.scale(radius * 0.3)).add(up.scale(-0.1));

        // Create flowing wave geometry
        List<Vec3> innerWave = new ArrayList<>();
        List<Vec3> outerWave = new ArrayList<>();
        List<Vec3> coreWave = new ArrayList<>();

        int segments = 60; // More segments for smoother curves
        double waveSpan = Math.PI * 1.4; // Wider wave
        double startAngle = -waveSpan / 2.0;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (waveSpan * t);

            // Create flowing wave profile - multiple sine waves for complexity
            double waveProfile = Math.sin(t * Math.PI); // Base wave shape
            double flowProfile = Math.sin(t * Math.PI * 2.0) * 0.3; // Secondary wave
            double energyProfile = Math.sin(t * Math.PI * 0.5) * 0.2; // Energy variation

            double combinedProfile = waveProfile + flowProfile + energyProfile;
            combinedProfile = Math.max(0.1, combinedProfile); // Ensure minimum thickness

            // Dynamic thickness based on position and layer
            double thickness = combinedProfile * 0.4 + 0.1;
            double baseRadius = radius * scale;

            // Create three wave paths for layered effect
            double innerRadius = baseRadius * (0.5 - thickness * 0.3);
            double coreRadius = baseRadius * 0.6;
            double outerRadius = baseRadius * (0.8 + thickness * 0.5);

            // Direction vector for wave
            Vec3 waveDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));

            // Add vertical flow and curvature
            double verticalFlow = Math.sin(t * Math.PI * 1.5) * radius * 0.08;
            double forwardFlow = Math.sin(t * Math.PI) * radius * 0.15;
            Vec3 flowOffset = up.scale(verticalFlow).add(forwardFlat.scale(forwardFlow));

            Vec3 innerPoint = origin.add(waveDir.scale(innerRadius)).add(flowOffset);
            Vec3 corePoint = origin.add(waveDir.scale(coreRadius)).add(flowOffset);
            Vec3 outerPoint = origin.add(waveDir.scale(outerRadius)).add(flowOffset);

            innerWave.add(innerPoint);
            coreWave.add(corePoint);
            outerWave.add(outerPoint);
        }

        // Render wave geometry with gradient colors
        renderWaveGeometry(innerWave, coreWave, outerWave, layerIndex, alphaMultiplier, segments);
    }

    private static void renderWaveGeometry(List<Vec3> innerWave, List<Vec3> coreWave, List<Vec3> outerWave,
                                           int layerIndex, double alphaMultiplier, int segments) {
        for (int i = 0; i < segments; i++) {
            double positionFactor = 1.0 - (Math.abs(i - segments / 2.0) / (segments / 2.0)) * 0.4;

            // Color gradient from bright core to darker edges
            int baseAlpha = (int) (255 * alphaMultiplier * positionFactor);

            // Bright cyan-blue core colors
            int coreR = 100 + (int) (155 * positionFactor);
            int coreG = 200 + (int) (55 * positionFactor);
            int coreB = 255;
            int coreColor = (baseAlpha << 24) | (coreR << 16) | (coreG << 8) | coreB;

            // Darker outer colors
            int outerAlpha = (int) (baseAlpha * 0.6);
            int outerR = 50 + (int) (100 * positionFactor);
            int outerG = 150 + (int) (80 * positionFactor);
            int outerB = 220;
            int outerColor = (outerAlpha << 24) | (outerR << 16) | (outerG << 8) | outerB;

            // Render wave segments
            Vec3 innerCurrent = innerWave.get(i);
            Vec3 innerNext = innerWave.get(i + 1);
            Vec3 coreCurrent = coreWave.get(i);
            Vec3 coreNext = coreWave.get(i + 1);
            Vec3 outerCurrent = outerWave.get(i);
            Vec3 outerNext = outerWave.get(i + 1);

            // Inner to core segment (bright)
            int[] coreColors = {coreColor, coreColor, coreColor};
            VectorRenderer.drawPlaneWorld(innerCurrent, coreCurrent, coreNext, coreColors, true, 60, VectorRenderer.Transform.IDENTITY);
            VectorRenderer.drawPlaneWorld(innerCurrent, coreNext, innerNext, coreColors, true, 60, VectorRenderer.Transform.IDENTITY);

            // Core to outer segment (gradient)
            int[] gradientColors = {coreColor, outerColor, outerColor};
            VectorRenderer.drawPlaneWorld(coreCurrent, outerCurrent, outerNext, gradientColors, true, 60, VectorRenderer.Transform.IDENTITY);
            VectorRenderer.drawPlaneWorld(coreCurrent, outerNext, coreNext, gradientColors, true, 60, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void renderSlashEnergyCore(Vec3 center, Vec3 lookDirection, double radius) {
        Vec3 forward = safeNormalize(lookDirection);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 up = worldUp;
        Vec3 origin = center.add(forwardFlat.scale(radius * 0.3)).add(up.scale(-0.1));

        // Create bright energy core line following the wave
        List<Vec3> energyCoreLine = new ArrayList<>();
        int segments = 50;
        double waveSpan = Math.PI * 1.4;
        double startAngle = -waveSpan / 2.0;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (waveSpan * t);

            double waveProfile = Math.sin(t * Math.PI);
            double coreRadius = radius * 0.7 * (0.5 + 0.5 * waveProfile);

            Vec3 waveDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
            double verticalFlow = Math.sin(t * Math.PI * 1.5) * radius * 0.08;
            double forwardFlow = Math.sin(t * Math.PI) * radius * 0.15;

            Vec3 point = origin
                    .add(waveDir.scale(coreRadius))
                    .add(up.scale(verticalFlow))
                    .add(forwardFlat.scale(forwardFlow));

            energyCoreLine.add(point);
        }

        // Render bright core trail with multiple thickness layers
        for (int i = 0; i < energyCoreLine.size() - 1; i++) {
            Vec3 current = energyCoreLine.get(i);
            Vec3 next = energyCoreLine.get(i + 1);

            // Multiple line thicknesses for glow effect
            VectorRenderer.drawLineWorld(current, next, 0xFFFFFFFF, 8.0f, true, 80, VectorRenderer.Transform.IDENTITY);
            VectorRenderer.drawLineWorld(current, next, 0xDDAAFFFF, 12.0f, true, 80, VectorRenderer.Transform.IDENTITY);
            VectorRenderer.drawLineWorld(current, next, 0x8800DDFF, 16.0f, true, 80, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void renderSlashEnergyParticles(Vec3 center, Vec3 lookDirection, double radius) {
        Vec3 forward = safeNormalize(lookDirection);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 up = worldUp;
        Vec3 origin = center.add(forwardFlat.scale(radius * 0.3)).add(up.scale(-0.1));

        // Energy particles along the wave path
        int particleCount = 40;
        double waveSpan = Math.PI * 1.4;
        double startAngle = -waveSpan / 2.0;

        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / (particleCount - 1);
            double angle = startAngle + (waveSpan * t);

            double waveProfile = Math.sin(t * Math.PI);
            double particleRadius = radius * (0.5 + 0.4 * waveProfile + Math.random() * 0.3);

            Vec3 waveDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
            double verticalFlow = Math.sin(t * Math.PI * 1.5) * radius * 0.08;
            double forwardFlow = Math.sin(t * Math.PI) * radius * 0.15;

            Vec3 particlePos = origin
                    .add(waveDir.scale(particleRadius))
                    .add(up.scale(verticalFlow + Math.random() * 0.1 - 0.05))
                    .add(forwardFlat.scale(forwardFlow));

            // Different particle types based on position
            float particleSize = 0.03f + (float) (Math.random() * 0.04);
            int particleLifetime = 40 + (int) (Math.random() * 30);

            if (i % 3 == 0) {
                // Bright energy particles
                VectorRenderer.drawSphereWorld(particlePos, particleSize, 0xFFFFFFFF, 6, 8, false, particleLifetime, VectorRenderer.Transform.IDENTITY);
            } else if (i % 3 == 1) {
                // Blue energy particles
                VectorRenderer.drawSphereWorld(particlePos, particleSize, 0xFF00DDFF, 6, 8, false, particleLifetime, VectorRenderer.Transform.IDENTITY);
            } else {
                // Cyan energy particles
                VectorRenderer.drawSphereWorld(particlePos, particleSize * 0.8f, 0xDDAAFFFF, 4, 6, false, particleLifetime, VectorRenderer.Transform.IDENTITY);
            }
        }

        // Add some trailing energy wisps
        for (int i = 0; i < 15; i++) {
            double t = Math.random();
            double angle = startAngle + (waveSpan * t);

            Vec3 waveDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
            double wispRadius = radius * (0.3 + Math.random() * 0.6);
            double verticalOffset = (Math.random() - 0.5) * 0.4;

            Vec3 wispPos = origin
                    .add(waveDir.scale(wispRadius))
                    .add(up.scale(verticalOffset));

            Vec3 wispEnd = wispPos.add(waveDir.scale(0.3 + Math.random() * 0.4));

            VectorRenderer.drawLineWorld(wispPos, wispEnd, 0xBB88DDFF, 2.0f, true, 50, VectorRenderer.Transform.IDENTITY);
        }
    }

    /* ---------------------------
       Flying slash (projectile) - Enhanced
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
        double slashSpeed = 1.2; // Faster movement
        double totalDuration = slash.range / slashSpeed;

        if (elapsedSeconds > totalDuration) {
            return true;
        }

        Vec3 currentPos = slash.startPos.add(slash.direction.scale(elapsedSeconds * slashSpeed));
        double progress = elapsedSeconds / totalDuration;
        double sizeMultiplier = 1.0 + (progress * 0.8);
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
        Vec3 origin = center.add(forwardFlat.scale(width * 0.4)).add(up.scale(-0.15));

        // Create flowing projectile wave
        List<Vec3> innerWave = new ArrayList<>();
        List<Vec3> outerWave = new ArrayList<>();
        List<Vec3> coreWave = new ArrayList<>();

        int segments = 30;
        double waveSpan = Math.PI * 0.8;
        double startAngle = -waveSpan / 2.0;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = startAngle + (waveSpan * t);

            double waveProfile = Math.sin(t * Math.PI);
            double thickness = waveProfile * 0.3 + 0.1;
            double baseRadius = width * 0.5;

            double innerRadius = baseRadius * (0.4 - thickness * 0.2);
            double coreRadius = baseRadius * 0.5;
            double outerRadius = baseRadius * (0.7 + thickness * 0.3);

            Vec3 waveDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
            double verticalFlow = Math.sin(t * Math.PI) * width * 0.05;

            Vec3 innerPoint = origin.add(waveDir.scale(innerRadius)).add(up.scale(verticalFlow));
            Vec3 corePoint = origin.add(waveDir.scale(coreRadius)).add(up.scale(verticalFlow));
            Vec3 outerPoint = origin.add(waveDir.scale(outerRadius)).add(up.scale(verticalFlow));

            innerWave.add(innerPoint);
            coreWave.add(corePoint);
            outerWave.add(outerPoint);
        }

        // Render flying wave with fade-out
        int alpha = (int) (255 * (1.0 - progress * 0.6));
        for (int i = 0; i < segments; i++) {
            int coreColor = (alpha << 24) | 0xFFFFFF;
            int outerColor = ((alpha / 2) << 24) | 0x00DDFF;

            int[] coreColors = {coreColor, coreColor, coreColor};
            int[] outerColors = {outerColor, outerColor, outerColor};

            VectorRenderer.drawPlaneWorld(innerWave.get(i), coreWave.get(i), coreWave.get(i + 1), coreColors, true, 15, VectorRenderer.Transform.IDENTITY);
            VectorRenderer.drawPlaneWorld(innerWave.get(i), coreWave.get(i + 1), innerWave.get(i + 1), coreColors, true, 15, VectorRenderer.Transform.IDENTITY);

            VectorRenderer.drawPlaneWorld(coreWave.get(i), outerWave.get(i), outerWave.get(i + 1), outerColors, true, 15, VectorRenderer.Transform.IDENTITY);
            VectorRenderer.drawPlaneWorld(coreWave.get(i), outerWave.get(i + 1), coreWave.get(i + 1), outerColors, true, 15, VectorRenderer.Transform.IDENTITY);
        }

        // Bright core trail
        for (int i = 0; i < coreWave.size() - 1; i++) {
            VectorRenderer.drawLineWorld(coreWave.get(i), coreWave.get(i + 1), (alpha << 24) | 0xFFFFFF, 4.0f, true, 10, VectorRenderer.Transform.IDENTITY);
        }
    }

    // Helper method
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