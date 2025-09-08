package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Echo Rifle beam renderer inspired by Warden sonic booms and Deep Dark aesthetics.
 * Creates layered sculk-themed beams with pulsing energy, distortion effects, and menacing visuals.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class EchoBeamRenderer {

    // Deep Dark / Sculk color palette - more menacing and atmospheric
    private static final int BEAM_CORE_COLOR = 0xFF00E6FF;        // Bright cyan-blue core (soul fire blue)
    private static final int BEAM_INNER_COLOR = 0xDD1A4D4D;       // Dark teal with transparency
    private static final int BEAM_OUTER_COLOR = 0xBB0A2A2A;       // Very dark teal/black
    private static final int SOUL_GLOW_COLOR = 0xEE4DAAFF;        // Soul fire glow
    private static final int DARKNESS_COLOR = 0x99000816;         // Almost black with slight blue tint
    private static final int ENERGY_CRACKLE_COLOR = 0xFF66DDFF;   // Electric blue crackles
    private static final int VOID_COLOR = 0x77000000;             // Pure darkness
    private static final int SCULK_PULSE_COLOR = 0xCC003366;      // Deep purple-blue pulse

    // Enhanced beam properties
    private static final int BEAM_SEGMENTS = 20;                  // More detailed circular cross-section
    private static final int DISTORTION_RINGS = 12;              // Distortion rings along beam
    private static final int CRACKLE_BOLTS = 8;                  // Electric-style energy bolts
    private static final double RING_SPACING = 1.5;              // Closer ring spacing for density
    private static final double PULSE_FREQUENCY = 0.3;           // Pulsing animation speed

    /**
     * Renders an impressive sculk-themed echo beam with multiple visual layers and effects
     */
    public static void renderEchoBeam(Vec3 start, Vec3 end, double beamWidth, int hitCount) {
        Vec3 direction = end.subtract(start);
        double beamLength = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Enhanced duration based on power and hits - longer for more impact
        int baseDuration = 25; // 1.25 seconds base
        int bonusDuration = Math.min(hitCount * 4, 30); // +0.2s per hit, max +1.5s
        int totalDuration = baseDuration + bonusDuration;

        // 1. Darkness void beam - creates menacing hollow center
        renderVoidCore(start, end, beamWidth, totalDuration);

        // 2. Layered sculk beam cores with pulsing animation
        renderSculkBeamLayers(start, end, beamWidth, totalDuration);

        // 3. Distortion rings with sculk-like pulsing
        renderSculkDistortionRings(start, normalizedDir, beamLength, beamWidth, totalDuration);

        // 4. Energy crackles - chaotic electric arcs around the beam
        renderEnergyCrackles(start, end, beamWidth, totalDuration);

        // 5. Soul fire spiral - mystical energy wrapping the beam
        renderSoulFireSpiral(start, end, beamWidth, totalDuration);

        // 6. Void tendrils - dark tentacle-like extensions
        renderVoidTendrils(start, end, beamWidth, totalDuration);

        // 7. Enhanced impact effects with sculk explosion
        if (hitCount > 0) {
            renderSculkImpactExplosion(end, beamWidth, hitCount, totalDuration);
        }

        // 8. Atmospheric darkness particles
        renderDarknessAtmosphere(start, end, beamWidth, totalDuration);
    }

    /**
     * Creates a menacing dark void core that makes the beam look like it tears reality
     */
    private static void renderVoidCore(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start).normalize();
        float distance = (float) start.distanceTo(end);

        // Create a dark void cylinder in the center
        float voidRadius = (float)(width * 0.1);

        VectorRenderer.drawCylinderWorld(
                start, direction, voidRadius, distance,
                16, 3, VOID_COLOR, false, duration, null
        );
    }

    /**
     * Creates layered sculk beam cores with pulsing animation
     */
    private static void renderSculkBeamLayers(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start).normalize();
        float distance = (float) start.distanceTo(end);

        // Start the beam layers slightly forward from the firing position
        Vec3 adjustedStart = start.add(direction.scale(0.5)); // Start 0.5 blocks forward
        float adjustedDistance = distance - 0.5f;

        if (adjustedDistance <= 0) return; // Don't render if too short

        // Multiple pulsing layers for depth and menace
        float[] layerRadii = {
                (float)(width * 0.15),  // Inner core
                (float)(width * 0.25),  // Mid layer
                (float)(width * 0.35),  // Outer layer
                (float)(width * 0.45)   // Outermost layer
        };

        int[] layerColors = {
                BEAM_CORE_COLOR,
                SOUL_GLOW_COLOR,
                BEAM_INNER_COLOR,
                DARKNESS_COLOR
        };

        for (int i = 0; i < layerRadii.length; i++) {
            // Stagger the start times for a wave effect
            int layerDelay = i * 2;

            VectorRenderer.drawCylinderWorld(
                    adjustedStart, direction, layerRadii[i], adjustedDistance,
                    BEAM_SEGMENTS, 4, layerColors[i], false,
                    duration + layerDelay, null
            );
        }
    }

    /**
     * Creates sculk-style distortion rings with pulsing effects
     */
    private static void renderSculkDistortionRings(Vec3 start, Vec3 direction, double length, double width, int duration) {
        int numRings = (int) Math.ceil(length / RING_SPACING);

        for (int ring = 0; ring < numRings; ring++) {
            double ringDistance = ring * RING_SPACING;
            if (ringDistance > length) break;

            Vec3 ringCenter = start.add(direction.scale(ringDistance));

            // Create multiple expanding rings with sculk theming
            for (int expansion = 0; expansion < 4; expansion++) {
                double baseRadius = width * 0.4;
                double expandedRadius = baseRadius + (expansion * width * 0.15);

                // Alternate between sculk colors for layered effect
                int ringColor = (expansion % 2 == 0) ? SCULK_PULSE_COLOR : DARKNESS_COLOR;

                // Make rings pulse by varying their timing
                int pulseDelay = (ring * 3) + (expansion * 2);

                renderPulsingRing(ringCenter, direction, expandedRadius, ringColor,
                        duration + pulseDelay);
            }
        }
    }

    /**
     * Renders a single pulsing sculk ring
     */
    private static void renderPulsingRing(Vec3 center, Vec3 beamDirection, double radius, int color, int duration) {
        int particlesPerRing = 16; // More particles for smoother rings

        Vec3 perpendicular1 = beamDirection.cross(new Vec3(0, 1, 0));
        if (perpendicular1.length() < 0.1) {
            perpendicular1 = beamDirection.cross(new Vec3(1, 0, 0));
        }
        perpendicular1 = perpendicular1.normalize();
        Vec3 perpendicular2 = beamDirection.cross(perpendicular1).normalize();

        for (int i = 0; i < particlesPerRing; i++) {
            double angle = (2 * Math.PI * i) / particlesPerRing;
            Vec3 offset = perpendicular1.scale(Math.cos(angle) * radius)
                    .add(perpendicular2.scale(Math.sin(angle) * radius));
            Vec3 particlePos = center.add(offset);

            // Vary particle size for organic feel
            float particleSize = 0.2f + (float)(Math.random() * 0.15f);

            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize, color,
                    8, 8, false, duration, null
            );
        }
    }

    /**
     * Creates chaotic energy crackles around the beam like lightning
     */
    private static void renderEnergyCrackles(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Create jagged energy bolts around the beam
        for (int bolt = 0; bolt < CRACKLE_BOLTS; bolt++) {
            List<Vec3> boltPoints = new ArrayList<>();

            // Generate a chaotic path around the beam
            int segments = 8 + (int)(Math.random() * 6);
            double boltRadius = width * (0.6 + Math.random() * 0.3);

            for (int i = 0; i < segments; i++) {
                double t = (double) i / (segments - 1);

                // Base position along beam
                Vec3 basePos = start.add(normalizedDir.scale(length * t));

                // Add random offset for chaotic movement
                double angle = Math.random() * 2 * Math.PI;
                double distance = boltRadius * (0.5 + Math.random() * 0.5);
                double heightOffset = (Math.random() - 0.5) * width * 0.4;

                Vec3 cracklePos = basePos.add(
                        Math.cos(angle) * distance,
                        heightOffset,
                        Math.sin(angle) * distance
                );

                boltPoints.add(cracklePos);
            }

            // Render the bolt as a polyline
            VectorRenderer.drawPolylineWorld(
                    boltPoints, ENERGY_CRACKLE_COLOR,
                    0.12f, false, duration + bolt, null
            );
        }
    }

    /**
     * Creates a mystical soul fire spiral around the beam
     */
    private static void renderSoulFireSpiral(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        Vec3 normalizedDir = direction.normalize();

        // Create dual counter-rotating spirals for more mystical effect
        for (int spiral = 0; spiral < 2; spiral++) {
            int spiralSegments = (int)(length * 3);
            double spiralRadius = width * 0.8;
            double spiralTurns = 4.0;
            boolean clockwise = spiral == 0;

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
                if (!clockwise) angle = -angle; // Counter-rotate second spiral

                // Vary radius along the spiral for organic feel
                double currentRadius = spiralRadius * (0.7 + 0.3 * Math.sin(t * Math.PI * 3));

                Vec3 basePoint = start.add(normalizedDir.scale(length * t));
                Vec3 spiralOffset = perpendicular1.scale(Math.cos(angle) * currentRadius)
                        .add(perpendicular2.scale(Math.sin(angle) * currentRadius));

                spiralPoints.add(basePoint.add(spiralOffset));
            }

            // Use different colors for each spiral
            int spiralColor = spiral == 0 ? SOUL_GLOW_COLOR : SCULK_PULSE_COLOR;

            VectorRenderer.drawPolylineWorld(
                    spiralPoints, spiralColor,
                    0.15f, false, duration + spiral * 3, null
            );
        }
    }

    /**
     * Creates dark void tendrils extending from the beam
     */
    private static void renderVoidTendrils(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();

        // Create several dark tendrils extending from random points along the beam
        int numTendrils = 6 + (int)(Math.random() * 4);

        for (int tendril = 0; tendril < numTendrils; tendril++) {
            // Random point along the beam
            double t = 0.2 + Math.random() * 0.6; // Avoid start/end
            Vec3 tendrilStart = start.add(direction.scale(t));

            // Random direction for tendril
            Vec3 tendrilDir = new Vec3(
                    (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 2
            ).normalize();

            double tendrilLength = width * (1.5 + Math.random() * 1.0);
            Vec3 tendrilEnd = tendrilStart.add(tendrilDir.scale(tendrilLength));

            // Create curved tendril path
            List<Vec3> tendrilPoints = new ArrayList<>();
            int segments = 5;

            for (int i = 0; i <= segments; i++) {
                double segmentT = (double) i / segments;

                // Add curve using sine wave
                Vec3 basePos = tendrilStart.add(tendrilDir.scale(tendrilLength * segmentT));
                Vec3 curveOffset = tendrilDir.cross(direction.normalize()).normalize()
                        .scale(Math.sin(segmentT * Math.PI) * width * 0.3);

                tendrilPoints.add(basePos.add(curveOffset));
            }

            VectorRenderer.drawPolylineWorld(
                    tendrilPoints, VOID_COLOR,
                    0.2f, false, duration + tendril * 2, null
            );
        }
    }

    /**
     * Creates an impressive sculk-themed explosion at impact
     */
    private static void renderSculkImpactExplosion(Vec3 impact, double width, int hitCount, int duration) {
        // Main sculk explosion blast
        VectorRenderer.drawSphereWorld(
                impact, (float)(width * 1.5), BEAM_CORE_COLOR,
                16, 12, false, duration / 2, null
        );

        // Expanding darkness waves
        for (int wave = 0; wave < 5; wave++) {
            float delay = wave * 3f;
            float waveSize = (float)(width * (2.0 + wave * 1.2));
            int waveAlpha = Math.max(0x22, 0x88 - wave * 0x15);
            int waveColor = (waveAlpha << 24) | (DARKNESS_COLOR & 0xFFFFFF);

            VectorRenderer.drawSphereWorld(
                    impact, waveSize, waveColor,
                    20, 15, false, (duration / 3) + (int)delay, null
            );
        }

        // Sculk spikes shooting outward
        int spikeCount = Math.min(hitCount * 4, 20);
        for (int spike = 0; spike < spikeCount; spike++) {
            // Random direction for spike
            double theta = Math.random() * 2 * Math.PI;
            double phi = Math.random() * Math.PI;
            double spikeLength = width * (2.0 + Math.random() * 2.0);

            Vec3 spikeDir = new Vec3(
                    Math.sin(phi) * Math.cos(theta),
                    Math.cos(phi),
                    Math.sin(phi) * Math.sin(theta)
            );

            Vec3 spikeEnd = impact.add(spikeDir.scale(spikeLength));

            VectorRenderer.drawLineWorld(
                    impact, spikeEnd, SCULK_PULSE_COLOR,
                    0.25f, false, duration + spike, null
            );
        }

        // Floating void fragments
        int fragmentCount = hitCount * 6;
        for (int frag = 0; frag < fragmentCount; frag++) {
            Vec3 fragPos = impact.add(
                    (Math.random() - 0.5) * width * 3,
                    (Math.random() - 0.5) * width * 3,
                    (Math.random() - 0.5) * width * 3
            );

            float fragSize = 0.15f + (float)(Math.random() * 0.25f);
            int fragColor = Math.random() > 0.5 ? VOID_COLOR : DARKNESS_COLOR;

            VectorRenderer.drawSphereWorld(
                    fragPos, fragSize, fragColor,
                    6, 6, false, duration + (int)(Math.random() * 15), null
            );
        }
    }

    /**
     * Creates atmospheric darkness particles around the entire beam
     */
    private static void renderDarknessAtmosphere(Vec3 start, Vec3 end, double width, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();

        // Dense atmospheric particles
        int numParticles = (int)(length * 2); // Two particles per block

        for (int i = 0; i < numParticles; i++) {
            double t = Math.random();
            Vec3 basePos = start.add(direction.scale(t));

            // Create layered atmosphere around the beam
            double atmosphereRadius = width * (1.2 + Math.random() * 0.8);
            double angle = Math.random() * 2 * Math.PI;
            double height = (Math.random() - 0.5) * width * 1.5;

            Vec3 particlePos = basePos.add(
                    Math.cos(angle) * atmosphereRadius,
                    height,
                    Math.sin(angle) * atmosphereRadius
            );

            float particleSize = 0.08f + (float)(Math.random() * 0.12f);

            // Vary colors for atmospheric depth
            int particleColor;
            double rand = Math.random();
            if (rand < 0.3) particleColor = VOID_COLOR;
            else if (rand < 0.6) particleColor = DARKNESS_COLOR;
            else particleColor = SCULK_PULSE_COLOR;

            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize, particleColor,
                    6, 6, false, duration + (int)(Math.random() * 20), null
            );
        }
    }
}