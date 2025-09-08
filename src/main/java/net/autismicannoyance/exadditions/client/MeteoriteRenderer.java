package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side meteorite renderer that creates stunning visual effects with enhanced comet trails
 */
public class MeteoriteRenderer {
    // Track active meteorites to manage their trail systems
    private static final Map<Integer, MeteoriteInstance> activeMeteorites = new ConcurrentHashMap<>();

    public static void spawnMeteorite(Vec3 startPos, Vec3 endPos, Vec3 velocity, float size,
                                      int lifetimeTicks, int meteoriteId, boolean hasTrail,
                                      int coreColor, int trailColor, float intensity) {

        // Create meteorite instance
        MeteoriteInstance meteorite = new MeteoriteInstance(
                startPos, endPos, velocity, size, lifetimeTicks,
                hasTrail, coreColor, trailColor, intensity
        );

        activeMeteorites.put(meteoriteId, meteorite);

        // Start the rendering process
        renderMeteorite(meteorite, lifetimeTicks);
    }

    private static void renderMeteorite(MeteoriteInstance meteorite, int lifetimeTicks) {
        Vec3 direction = meteorite.velocity.normalize();
        double speed = meteorite.velocity.length();

        // Enhanced trail system - create multiple trail segments with different properties
        if (meteorite.hasTrail) {
            renderCometTrail(meteorite, direction, speed, lifetimeTicks);
        }

        // Render the main meteorite core
        renderMeteoriteCore(meteorite, direction, speed, lifetimeTicks);
    }

    private static void renderCometTrail(MeteoriteInstance meteorite, Vec3 direction, double speed, int lifetimeTicks) {
        Vec3 currentPos = meteorite.startPos;

        // Enhanced trail parameters
        int trailSegments = Math.max(15, (int)(meteorite.size * 8)); // More segments for larger meteorites
        float baseTrailLength = meteorite.size * 4.0f; // Trail length based on meteorite size

        // Create layered trail system
        for (int layer = 0; layer < 3; layer++) {
            float layerScale = 1.0f - (layer * 0.3f); // Each layer gets smaller
            float layerAlpha = 1.0f - (layer * 0.4f); // Each layer gets more transparent
            int layerColor = adjustAlpha(meteorite.trailColor, layerAlpha);

            createTrailLayer(currentPos, direction, speed, meteorite.size * layerScale,
                    trailSegments, baseTrailLength * layerScale, lifetimeTicks,
                    layerColor, layer);
        }

        // Add particle trail effects
        createParticleTrail(currentPos, direction, speed, meteorite.size, trailSegments,
                lifetimeTicks, meteorite.trailColor);

        // Add heat distortion trail (very subtle transparent effect)
        createHeatDistortionTrail(currentPos, direction, speed, meteorite.size,
                trailSegments, lifetimeTicks);
    }

    private static void createTrailLayer(Vec3 startPos, Vec3 direction, double speed, float size,
                                         int segments, float trailLength, int lifetimeTicks,
                                         int color, int layerIndex) {

        for (int i = 0; i < segments; i++) {
            float segmentProgress = (float) i / segments;
            float segmentAlpha = 1.0f - (segmentProgress * 0.8f); // Fade along the trail

            // Calculate position along the trail
            Vec3 segmentPos = startPos.add(direction.scale(-segmentProgress * trailLength));

            // Add some slight curve and variation to make the trail more natural
            double curve = Math.sin(segmentProgress * Math.PI) * 0.3 * size;
            Vec3 perpendicular = direction.cross(new Vec3(0, 1, 0));
            if (perpendicular.length() < 0.001) {
                perpendicular = direction.cross(new Vec3(1, 0, 0));
            }
            segmentPos = segmentPos.add(perpendicular.normalize().scale(curve));

            // Size decreases along the trail
            float segmentSize = size * (1.0f - segmentProgress * 0.7f);

            // Color fades along the trail
            int segmentColor = adjustAlpha(color, segmentAlpha);

            // Create transform that moves the trail segment
            VectorRenderer.Transform transform = createMovingTransform(direction, speed, i * 2 + layerIndex * 5);

            // Use different shapes for different layers
            if (layerIndex == 0) {
                // Main trail - elongated spheres
                VectorRenderer.drawSphereWorld(
                        segmentPos,
                        segmentSize,
                        segmentColor,
                        6, 8,
                        false,
                        lifetimeTicks - i,
                        transform
                );
            } else if (layerIndex == 1) {
                // Secondary trail - smaller, more wispy
                VectorRenderer.drawSphereWorld(
                        segmentPos,
                        segmentSize * 0.7f,
                        segmentColor,
                        4, 6,
                        false,
                        lifetimeTicks - i,
                        transform
                );
            } else {
                // Outer trail - very wispy and transparent
                VectorRenderer.drawSphereWorld(
                        segmentPos,
                        segmentSize * 1.2f,
                        adjustAlpha(segmentColor, 0.3f),
                        3, 4,
                        false,
                        lifetimeTicks - i,
                        transform
                );
            }
        }
    }

    private static void createParticleTrail(Vec3 startPos, Vec3 direction, double speed, float size,
                                            int segments, int lifetimeTicks, int baseColor) {

        // Create small particles scattered along the trail
        int particleCount = segments / 2;

        for (int i = 0; i < particleCount; i++) {
            float progress = (float) i / particleCount;
            Vec3 particlePos = startPos.add(direction.scale(-progress * size * 3));

            // Add random offset to particles
            Vec3 randomOffset = new Vec3(
                    (Math.random() - 0.5) * size * 0.5,
                    (Math.random() - 0.5) * size * 0.5,
                    (Math.random() - 0.5) * size * 0.5
            );
            particlePos = particlePos.add(randomOffset);

            float particleSize = size * 0.1f * (1.0f - progress * 0.5f);
            float particleAlpha = 0.8f - progress * 0.6f;
            int particleColor = adjustAlpha(brightenColor(baseColor, 1.2f), particleAlpha);

            VectorRenderer.Transform transform = createMovingTransform(direction, speed * 0.9, i * 3);

            VectorRenderer.drawSphereWorld(
                    particlePos,
                    particleSize,
                    particleColor,
                    3, 4,
                    false,
                    lifetimeTicks - i * 2,
                    transform
            );
        }
    }

    private static void createHeatDistortionTrail(Vec3 startPos, Vec3 direction, double speed, float size,
                                                  int segments, int lifetimeTicks) {

        // Create subtle heat distortion effect behind the meteorite
        for (int i = 0; i < segments / 3; i++) {
            float progress = (float) i / (segments / 3);
            Vec3 heatPos = startPos.add(direction.scale(-progress * size * 2));

            float heatSize = size * (1.5f - progress * 0.5f);
            float heatAlpha = 0.15f - progress * 0.1f;

            // Very subtle transparent effect
            int heatColor = adjustAlpha(0xFFFFAAAA, heatAlpha); // Slight yellow tint

            VectorRenderer.Transform transform = createMovingTransform(direction, speed * 1.1, i);

            VectorRenderer.drawSphereWorld(
                    heatPos,
                    heatSize,
                    heatColor,
                    4, 6,
                    false,
                    lifetimeTicks - i,
                    transform
            );
        }
    }

    private static void renderMeteoriteCore(MeteoriteInstance meteorite, Vec3 direction, double speed, int lifetimeTicks) {
        Vec3 corePos = meteorite.startPos;

        // Multi-layered core for maximum visual impact
        VectorRenderer.Transform coreTransform = createMovingTransform(direction, speed, 0);

        // Outer heat glow - largest, most transparent
        VectorRenderer.drawSphereWorld(
                corePos,
                meteorite.size * 1.8f,
                adjustAlpha(meteorite.coreColor, 0.2f),
                8, 10,
                false,
                lifetimeTicks,
                coreTransform
        );

        // Middle glow layer
        VectorRenderer.drawSphereWorld(
                corePos,
                meteorite.size * 1.3f,
                adjustAlpha(meteorite.coreColor, 0.4f),
                10, 12,
                false,
                lifetimeTicks,
                coreTransform
        );

        // Main solid core
        VectorRenderer.drawSphereWorld(
                corePos,
                meteorite.size,
                meteorite.coreColor,
                12, 16,
                false,
                lifetimeTicks,
                coreTransform
        );

        // Bright inner core
        VectorRenderer.drawSphereWorld(
                corePos,
                meteorite.size * 0.6f,
                brightenColor(meteorite.coreColor, 1.8f),
                8, 12,
                false,
                lifetimeTicks,
                coreTransform
        );

        // Ultra-bright center point
        VectorRenderer.drawSphereWorld(
                corePos,
                meteorite.size * 0.3f,
                brightenColor(meteorite.coreColor, 2.5f),
                6, 8,
                false,
                lifetimeTicks,
                coreTransform
        );

        // Add some debris around the core
        createCoreDebris(corePos, direction, speed, meteorite.size, lifetimeTicks, meteorite.coreColor);
    }

    private static void createCoreDebris(Vec3 corePos, Vec3 direction, double speed, float size,
                                         int lifetimeTicks, int baseColor) {

        int debrisCount = Math.max(4, (int)(size * 3));

        for (int i = 0; i < debrisCount; i++) {
            // Create debris in a rough sphere around the meteorite
            double angle = Math.PI * 2 * i / debrisCount;
            double height = (Math.random() - 0.5) * size * 1.5;
            double radius = size * (0.8 + Math.random() * 0.6);

            Vec3 debrisOffset = new Vec3(
                    Math.cos(angle) * radius,
                    height,
                    Math.sin(angle) * radius
            );

            Vec3 debrisPos = corePos.add(debrisOffset);
            float debrisSize = size * 0.08f * (0.5f + (float)Math.random() * 0.5f);

            // Debris moves slightly differently than the main meteorite
            Vec3 debrisDirection = direction.add(
                    (Math.random() - 0.5) * 0.15,
                    (Math.random() - 0.5) * 0.15,
                    (Math.random() - 0.5) * 0.15
            ).normalize();

            double debrisSpeed = speed * (0.85 + Math.random() * 0.3);

            VectorRenderer.Transform debrisTransform = createMovingTransform(debrisDirection, debrisSpeed, i * 7);

            VectorRenderer.drawSphereWorld(
                    debrisPos,
                    debrisSize,
                    adjustAlpha(darkenColor(baseColor, 0.6f), 0.7f),
                    4, 6,
                    false,
                    lifetimeTicks - (int)(Math.random() * 15),
                    debrisTransform
            );
        }
    }

    private static VectorRenderer.Transform createMovingTransform(Vec3 direction, double speed, int phase) {
        // Create a transform that moves the object over time based on velocity
        Vec3 translation = direction.scale(speed * 0.05); // Adjust movement scale

        return VectorRenderer.Transform.fromEuler(
                translation,
                phase * 2.0f, // Slight rotation variation
                0,
                0,
                1.0f,
                Vec3.ZERO
        );
    }

    // Color utility methods
    private static int adjustAlpha(int color, float alphaMultiplier) {
        int alpha = (color >> 24) & 0xFF;
        int newAlpha = Math.max(0, Math.min(255, (int)(alpha * alphaMultiplier)));
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    private static int brightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int)(((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int)(((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int)((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int)(((color >> 16) & 0xFF) * factor);
        int g = (int)(((color >> 8) & 0xFF) * factor);
        int b = (int)((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Meteorite instance data class
    private static class MeteoriteInstance {
        final Vec3 startPos;
        final Vec3 endPos;
        final Vec3 velocity;
        final float size;
        final int lifetimeTicks;
        final boolean hasTrail;
        final int coreColor;
        final int trailColor;
        final float intensity;

        MeteoriteInstance(Vec3 startPos, Vec3 endPos, Vec3 velocity, float size, int lifetimeTicks,
                          boolean hasTrail, int coreColor, int trailColor, float intensity) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.velocity = velocity;
            this.size = size;
            this.lifetimeTicks = lifetimeTicks;
            this.hasTrail = hasTrail;
            this.coreColor = coreColor;
            this.trailColor = trailColor;
            this.intensity = intensity;
        }
    }
}