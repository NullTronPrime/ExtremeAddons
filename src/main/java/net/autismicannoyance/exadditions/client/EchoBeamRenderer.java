package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplified Echo Rifle beam renderer - NO CYLINDERS, NO WEIRD ORIENTATIONS
 * Creates sculk-themed beams using only lines, spheres, and particles that orient correctly
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class EchoBeamRenderer {

    // Deep Dark / Sculk color palette
    private static final int BEAM_CORE_COLOR = 0xFF00E6FF;        // Bright cyan-blue core
    private static final int BEAM_INNER_COLOR = 0xDD1A4D4D;       // Dark teal
    private static final int SOUL_GLOW_COLOR = 0xEE4DAAFF;        // Soul fire glow
    private static final int DARKNESS_COLOR = 0x99000816;         // Almost black
    private static final int ENERGY_CRACKLE_COLOR = 0xFF66DDFF;   // Electric blue
    private static final int VOID_COLOR = 0x77000000;             // Pure darkness
    private static final int SCULK_PULSE_COLOR = 0xCC003366;      // Deep purple-blue

    /**
     * Renders a sculk-themed echo beam using only safe rendering methods
     */
    public static void renderEchoBeam(Vec3 start, Vec3 end, double beamWidth, int hitCount) {
        Vec3 direction = end.subtract(start);
        double beamLength = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Enhanced duration based on hits
        int baseDuration = 25;
        int bonusDuration = Math.min(hitCount * 4, 30);
        int totalDuration = baseDuration + bonusDuration;

        // Start the beam slightly forward to avoid issues at the firing position
        Vec3 beamStart = start.add(normalizedDir.scale(1.0)); // Start 1 block forward
        Vec3 beamEnd = end;
        double adjustedLength = beamStart.distanceTo(beamEnd);

        if (adjustedLength <= 0) return;

        // 1. Simple core beam using connected spheres
        renderBeamCore(beamStart, beamEnd, beamWidth, totalDuration);

        // 2. Particle rings along the beam
        renderParticleRings(beamStart, normalizedDir, adjustedLength, beamWidth, totalDuration);

        // 3. Energy crackles around the beam
        renderEnergyCrackles(beamStart, beamEnd, beamWidth, totalDuration);

        // 4. Impact explosion
        if (hitCount > 0) {
            renderImpactExplosion(beamEnd, beamWidth, hitCount, totalDuration);
        }

        // 5. Atmospheric particles
        renderAtmosphereParticles(beamStart, beamEnd, beamWidth, totalDuration);
    }

    /**
     * Creates the main beam using connected spheres - guaranteed to work correctly
     */
    private static void renderBeamCore(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Create multiple beam layers using spheres
        float[] layerRadii = {
                (float)(width * 0.1),   // Core
                (float)(width * 0.2),   // Inner
                (float)(width * 0.3),   // Outer
        };

        int[] layerColors = {
                BEAM_CORE_COLOR,
                SOUL_GLOW_COLOR,
                BEAM_INNER_COLOR
        };

        for (int layer = 0; layer < layerRadii.length; layer++) {
            float radius = layerRadii[layer];
            int color = layerColors[layer];

            // Create beam using series of spheres
            int numSpheres = (int)(length / 0.4) + 1;

            for (int i = 0; i < numSpheres; i++) {
                double t = (double) i / Math.max(1, numSpheres - 1);
                Vec3 spherePos = start.add(normalizedDir.scale(length * t));

                VectorRenderer.drawSphereWorld(
                        spherePos, radius, color,
                        8, 6, false, duration + layer * 2, null
                );
            }
        }
    }

    /**
     * Creates particle rings along the beam path
     */
    private static void renderParticleRings(Vec3 start, Vec3 direction, double length, double width, int duration) {
        int numRings = (int)(length / 2.0) + 1; // One ring every 2 blocks

        for (int ring = 0; ring < numRings; ring++) {
            double ringDistance = ring * 2.0;
            if (ringDistance > length) break;

            Vec3 ringCenter = start.add(direction.scale(ringDistance));

            // Create expanding rings
            for (int expansion = 0; expansion < 3; expansion++) {
                double ringRadius = width * (0.4 + expansion * 0.2);
                int ringColor = expansion == 0 ? SCULK_PULSE_COLOR : DARKNESS_COLOR;

                renderParticleRing(ringCenter, direction, ringRadius, ringColor, duration + ring * 2 + expansion);
            }
        }
    }

    /**
     * Renders a single particle ring perpendicular to the beam
     */
    private static void renderParticleRing(Vec3 center, Vec3 beamDirection, double radius, int color, int duration) {
        int particlesPerRing = 12;

        // Find perpendicular vectors
        Vec3 perpendicular1 = beamDirection.cross(new Vec3(0, 1, 0));
        if (perpendicular1.length() < 0.1) {
            perpendicular1 = beamDirection.cross(new Vec3(1, 0, 0));
        }
        perpendicular1 = perpendicular1.normalize();
        Vec3 perpendicular2 = beamDirection.cross(perpendicular1).normalize();

        // Create ring particles
        for (int i = 0; i < particlesPerRing; i++) {
            double angle = (2 * Math.PI * i) / particlesPerRing;
            Vec3 offset = perpendicular1.scale(Math.cos(angle) * radius)
                    .add(perpendicular2.scale(Math.sin(angle) * radius));
            Vec3 particlePos = center.add(offset);

            float particleSize = 0.15f + (float)(Math.random() * 0.1f);
            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize, color,
                    6, 6, false, duration, null
            );
        }
    }

    /**
     * Creates chaotic energy crackles around the beam
     */
    private static void renderEnergyCrackles(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Create energy bolts around the beam
        int numBolts = 6;

        for (int bolt = 0; bolt < numBolts; bolt++) {
            List<Vec3> boltPoints = new ArrayList<>();

            // Generate chaotic path
            int segments = 6;
            double boltRadius = width * (0.5 + Math.random() * 0.3);

            for (int i = 0; i < segments; i++) {
                double t = (double) i / (segments - 1);

                // Base position along beam
                Vec3 basePos = start.add(normalizedDir.scale(length * t));

                // Add random offset
                double angle = Math.random() * 2 * Math.PI;
                double distance = boltRadius * Math.random();
                double heightOffset = (Math.random() - 0.5) * width * 0.3;

                Vec3 cracklePos = basePos.add(
                        Math.cos(angle) * distance,
                        heightOffset,
                        Math.sin(angle) * distance
                );

                boltPoints.add(cracklePos);
            }

            // Render bolt as connected lines
            for (int i = 0; i < boltPoints.size() - 1; i++) {
                VectorRenderer.drawLineWorld(
                        boltPoints.get(i), boltPoints.get(i + 1),
                        ENERGY_CRACKLE_COLOR, 0.1f, false, duration + bolt, null
                );
            }
        }
    }

    /**
     * Creates impact explosion effects
     */
    private static void renderImpactExplosion(Vec3 impact, double width, int hitCount, int duration) {
        // Main blast sphere
        VectorRenderer.drawSphereWorld(
                impact, (float)(width * 1.2), BEAM_CORE_COLOR,
                12, 8, false, duration / 2, null
        );

        // Expanding shockwaves
        for (int wave = 0; wave < 4; wave++) {
            float delay = wave * 3f;
            float waveSize = (float)(width * (1.5 + wave * 0.8));
            int waveAlpha = Math.max(0x33, 0xFF - wave * 0x33);
            int waveColor = (waveAlpha << 24) | (DARKNESS_COLOR & 0xFFFFFF);

            VectorRenderer.drawSphereWorld(
                    impact, waveSize, waveColor,
                    16, 12, false, (duration / 3) + (int)delay, null
            );
        }

        // Burst particles
        int burstParticles = Math.min(hitCount * 2, 12);
        for (int i = 0; i < burstParticles; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi = Math.random() * Math.PI;
            double distance = width * (0.8 + Math.random() * 1.2);

            Vec3 particleOffset = new Vec3(
                    Math.sin(phi) * Math.cos(theta) * distance,
                    Math.cos(phi) * distance,
                    Math.sin(phi) * Math.sin(theta) * distance
            );

            Vec3 particlePos = impact.add(particleOffset);
            float particleSize = 0.2f + (float)(Math.random() * 0.2f);
            int particleColor = i % 2 == 0 ? SCULK_PULSE_COLOR : VOID_COLOR;

            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize, particleColor,
                    6, 6, false, duration + (int)(Math.random() * 8), null
            );
        }
    }

    /**
     * Creates atmospheric particles around the beam
     */
    private static void renderAtmosphereParticles(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        int numParticles = (int)(length / 2.0); // One particle every 2 blocks

        for (int i = 0; i < numParticles; i++) {
            double t = Math.random();
            Vec3 basePos = start.add(direction.scale(t));

            // Random offset around beam
            double particleDistance = width * (0.8 + Math.random() * 0.6);
            double angle = Math.random() * 2 * Math.PI;
            double height = (Math.random() - 0.5) * width;

            Vec3 particlePos = basePos.add(
                    Math.cos(angle) * particleDistance,
                    height,
                    Math.sin(angle) * particleDistance
            );

            float particleSize = 0.08f + (float)(Math.random() * 0.15f);
            int particleColor = Math.random() > 0.5 ? VOID_COLOR : DARKNESS_COLOR;

            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize, particleColor,
                    6, 6, false, duration + (int)(Math.random() * 12), null
            );
        }
    }
}