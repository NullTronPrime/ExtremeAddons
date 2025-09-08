package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated renderer for Echo Rifle beams inspired by Warden sonic boom attacks.
 * Creates circular beam patterns with particle rings using VectorRenderer.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class EchoBeamRenderer {

    // Deep Dark color palette
    private static final int BEAM_CORE_COLOR = 0xFF00D4FF;      // Bright cyan core
    private static final int BEAM_INNER_COLOR = 0xDD1B5E5E;     // Medium dark teal
    private static final int BEAM_OUTER_COLOR = 0xBB0F2A2A;     // Dark teal
    private static final int PARTICLE_BRIGHT = 0xFF4DFFFF;      // Bright cyan particles
    private static final int PARTICLE_MEDIUM = 0xCC00AAD4;      // Medium cyan particles
    private static final int PARTICLE_DIM = 0x88003D4D;         // Dim teal particles
    private static final int ENERGY_RING_COLOR = 0xEE00FFD4;    // Energy ring color

    // Beam properties
    private static final int BEAM_SEGMENTS = 16;                // Circular segments
    private static final int PARTICLE_RINGS = 8;               // Number of particle rings
    private static final double PARTICLE_RING_SPACING = 2.0;   // Distance between rings

    /**
     * Renders an impressive echo beam effect with circular cross-section and particle rings
     */
    public static void renderEchoBeam(Vec3 start, Vec3 end, double beamWidth, int hitCount) {
        Vec3 direction = end.subtract(start);
        double beamLength = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Calculate beam duration based on power and hits
        int baseDuration = 15; // 0.75 seconds
        int bonusDuration = Math.min(hitCount * 3, 20); // +0.15s per hit, max +1s
        int beamDuration = baseDuration + bonusDuration;

        // 1. Main circular beam core
        renderCircularBeamCore(start, end, beamWidth, beamDuration);

        // 2. Particle rings along the beam (like sonic boom)
        renderParticleRings(start, normalizedDir, beamLength, beamWidth, beamDuration);

        // 3. Energy spiral around the beam
        renderEnergySpiral(start, end, beamWidth, beamDuration);

        // 4. Impact effects at the end
        if (hitCount > 0) {
            renderImpactEffect(end, beamWidth, hitCount, beamDuration);
        }

        // 5. Ambient floating particles
        renderAmbientParticles(start, end, beamWidth, beamDuration);
    }

    /**
     * Creates the main circular beam using multiple concentric cylinders
     */
    private static void renderCircularBeamCore(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start).normalize();
        float distance = (float) start.distanceTo(end);

        // Create concentric circular beam layers
        float coreRadius = (float)(width * 0.15);     // Bright core
        float innerRadius = (float)(width * 0.3);     // Medium layer
        float outerRadius = (float)(width * 0.45);    // Outer layer

        // Core beam - brightest
        VectorRenderer.drawCylinderWorld(
                start, direction, coreRadius, distance,
                BEAM_SEGMENTS, 2, BEAM_CORE_COLOR, false, duration, null
        );

        // Inner layer - medium brightness
        VectorRenderer.drawCylinderWorld(
                start, direction, innerRadius, distance,
                BEAM_SEGMENTS, 2, BEAM_INNER_COLOR, false, duration + 2, null
        );

        // Outer layer - dimmer
        VectorRenderer.drawCylinderWorld(
                start, direction, outerRadius, distance,
                BEAM_SEGMENTS, 2, BEAM_OUTER_COLOR, false, duration + 4, null
        );
    }

    /**
     * Creates particle rings along the beam path similar to Warden sonic boom
     */
    private static void renderParticleRings(Vec3 start, Vec3 direction, double length, double width, int duration) {
        int numRings = (int) Math.ceil(length / PARTICLE_RING_SPACING);

        for (int ring = 0; ring < numRings; ring++) {
            double ringDistance = ring * PARTICLE_RING_SPACING;
            if (ringDistance > length) break;

            Vec3 ringCenter = start.add(direction.scale(ringDistance));
            double ringRadius = width * 0.6; // Base ring radius

            // Create expanding ring effect
            for (int expansion = 0; expansion < 3; expansion++) {
                double expandedRadius = ringRadius + (expansion * width * 0.2);
                int ringColor = expansion == 0 ? PARTICLE_BRIGHT :
                        expansion == 1 ? PARTICLE_MEDIUM : PARTICLE_DIM;

                renderParticleRing(ringCenter, direction, expandedRadius, ringColor,
                        duration + expansion * 2 + ring);
            }
        }
    }

    /**
     * Renders a single particle ring perpendicular to the beam direction
     */
    private static void renderParticleRing(Vec3 center, Vec3 beamDirection, double radius, int color, int duration) {
        int particlesPerRing = 12;

        // Find two perpendicular vectors to the beam direction
        Vec3 perpendicular1 = beamDirection.cross(new Vec3(0, 1, 0));
        if (perpendicular1.length() < 0.1) {
            perpendicular1 = beamDirection.cross(new Vec3(1, 0, 0));
        }
        perpendicular1 = perpendicular1.normalize();
        Vec3 perpendicular2 = beamDirection.cross(perpendicular1).normalize();

        // Create ring of particles
        for (int i = 0; i < particlesPerRing; i++) {
            double angle = (2 * Math.PI * i) / particlesPerRing;
            Vec3 offset = perpendicular1.scale(Math.cos(angle) * radius)
                    .add(perpendicular2.scale(Math.sin(angle) * radius));
            Vec3 particlePos = center.add(offset);

            // Individual particle
            float particleSize = 0.15f + (float)(Math.random() * 0.1f);
            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize, color,
                    6, 6, false, duration, null
            );
        }
    }

    /**
     * Creates a spiraling energy effect around the main beam
     */
    private static void renderEnergySpiral(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Create spiral parameters
        int spiralSegments = (int)(length * 2); // 2 segments per block
        double spiralRadius = width * 0.7;
        double spiralTurns = 3.0; // Number of complete turns

        // Find perpendicular vectors
        Vec3 perpendicular1 = normalizedDir.cross(new Vec3(0, 1, 0));
        if (perpendicular1.length() < 0.1) {
            perpendicular1 = normalizedDir.cross(new Vec3(1, 0, 0));
        }
        perpendicular1 = perpendicular1.normalize();
        Vec3 perpendicular2 = normalizedDir.cross(perpendicular1).normalize();

        List<Vec3> spiralPoints = new ArrayList<>();

        for (int i = 0; i < spiralSegments; i++) {
            double t = (double) i / (spiralSegments - 1);
            double angle = spiralTurns * 2 * Math.PI * t;
            double currentRadius = spiralRadius * (1.0 - t * 0.3); // Taper towards end

            Vec3 basePoint = start.add(normalizedDir.scale(length * t));
            Vec3 spiralOffset = perpendicular1.scale(Math.cos(angle) * currentRadius)
                    .add(perpendicular2.scale(Math.sin(angle) * currentRadius));

            spiralPoints.add(basePoint.add(spiralOffset));
        }

        // Render spiral as connected line segments
        for (int i = 0; i < spiralPoints.size() - 1; i++) {
            VectorRenderer.drawLineWorld(
                    spiralPoints.get(i),
                    spiralPoints.get(i + 1),
                    ENERGY_RING_COLOR,
                    (float)(width * 0.08), // Thin spiral line
                    false,
                    duration + i / 2, // Slight stagger
                    null
            );
        }
    }

    /**
     * Creates impact effects where the beam ends
     */
    private static void renderImpactEffect(Vec3 impact, double width, int hitCount, int duration) {
        // Main impact blast
        VectorRenderer.drawSphereWorld(
                impact, (float)(width * 1.2), BEAM_CORE_COLOR,
                12, 8, false, duration / 2, null
        );

        // Expanding shockwave rings
        for (int ring = 0; ring < 4; ring++) {
            float delay = ring * 2f;
            float ringSize = (float)(width * (1.5 + ring * 0.8));
            int ringAlpha = Math.max(0x33, 0xFF - ring * 0x33);
            int ringColor = (ringAlpha << 24) | (ENERGY_RING_COLOR & 0xFFFFFF);

            VectorRenderer.drawSphereWorld(
                    impact, ringSize, ringColor,
                    16, 12, false, (duration / 3) + (int)delay, null
            );
        }

        // Energy burst particles based on hit count
        int burstParticles = Math.min(hitCount * 3, 15);
        for (int i = 0; i < burstParticles; i++) {
            // Random direction for particle
            double theta = Math.random() * 2 * Math.PI;
            double phi = Math.random() * Math.PI;
            double distance = width * (0.8 + Math.random() * 1.2);

            Vec3 particleOffset = new Vec3(
                    Math.sin(phi) * Math.cos(theta) * distance,
                    Math.cos(phi) * distance,
                    Math.sin(phi) * Math.sin(theta) * distance
            );

            Vec3 particlePos = impact.add(particleOffset);
            float particleSize = 0.2f + (float)(Math.random() * 0.3f);
            int particleColor = i % 2 == 0 ? PARTICLE_BRIGHT : PARTICLE_MEDIUM;

            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize, particleColor,
                    6, 6, false, duration + (int)(Math.random() * 10), null
            );
        }
    }

    /**
     * Creates ambient floating particles around the beam
     */
    private static void renderAmbientParticles(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        int numParticles = (int)(length / 1.5); // One particle every 1.5 blocks

        for (int i = 0; i < numParticles; i++) {
            double t = Math.random();
            Vec3 basePos = start.add(direction.scale(t));

            // Random offset around the beam
            double particleDistance = width * (0.8 + Math.random() * 0.6);
            double angle = Math.random() * 2 * Math.PI;
            double height = (Math.random() - 0.5) * width;

            Vec3 particlePos = basePos.add(
                    Math.cos(angle) * particleDistance,
                    height,
                    Math.sin(angle) * particleDistance
            );

            float particleSize = 0.1f + (float)(Math.random() * 0.2f);
            int particleColor = Math.random() > 0.6 ? PARTICLE_BRIGHT : PARTICLE_DIM;

            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize, particleColor,
                    6, 6, false, duration + (int)(Math.random() * 15), null
            );
        }
    }
}