package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-quality black hole renderer with accretion disk, event horizon, and gravitational lensing effects
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class BlackHoleRenderer {
    private static final Map<Integer, BlackHoleEffect> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // Visual constants
    private static final int EVENT_HORIZON_SEGMENTS = 32;
    private static final int ACCRETION_DISK_RINGS = 8;
    private static final int ACCRETION_DISK_SEGMENTS = 64;
    private static final int GRAVITATIONAL_LENS_RINGS = 4;

    // Colors (ARGB)
    private static final int COLOR_EVENT_HORIZON = 0xFF000000; // Pure black
    private static final int COLOR_PHOTON_SPHERE = 0x40404040; // Dark gray with transparency
    private static final int COLOR_ACCRETION_INNER = 0xFFFFFFFF; // White hot center
    private static final int COLOR_ACCRETION_OUTER = 0xFF880000; // Deep red
    private static final int COLOR_JET_CORE = 0xFFFFFFFF; // White hot jets
    private static final int COLOR_JET_OUTER = 0x80AAAAFF; // Blue outer glow

    // Physics constants (simplified for visual effect)
    private static final float SCHWARZSCHILD_RADIUS_MULTIPLIER = 0.8f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float ACCRETION_DISK_INNER_MULTIPLIER = 3.0f;
    private static final float ACCRETION_DISK_OUTER_MULTIPLIER = 8.0f;
    private static final float JET_LENGTH_MULTIPLIER = 12.0f;

    public static void addEffect(int entityId, Vec3 position, float size, float rotationSpeed, int lifetime) {
        EFFECTS.put(entityId, new BlackHoleEffect(position, size, rotationSpeed, lifetime));
    }

    public static void removeEffect(int entityId) {
        EFFECTS.remove(entityId);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (EFFECTS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partialTick = event.getPartialTick();

        // Update and render all black hole effects
        Iterator<Map.Entry<Integer, BlackHoleEffect>> iterator = EFFECTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, BlackHoleEffect> entry = iterator.next();
            BlackHoleEffect effect = entry.getValue();

            if (effect.isExpired()) {
                iterator.remove();
                continue;
            }

            effect.tick();
            renderBlackHole(effect, partialTick);
        }
    }

    private static void renderBlackHole(BlackHoleEffect effect, float partialTick) {
        Vec3 pos = effect.position;
        float size = effect.size;
        float rotation = effect.getCurrentRotation(partialTick);

        // Calculate derived sizes
        float eventHorizonRadius = size * SCHWARZSCHILD_RADIUS_MULTIPLIER;
        float photonSphereRadius = size * PHOTON_SPHERE_MULTIPLIER;
        float accretionInnerRadius = size * ACCRETION_DISK_INNER_MULTIPLIER;
        float accretionOuterRadius = size * ACCRETION_DISK_OUTER_MULTIPLIER;
        float jetLength = size * JET_LENGTH_MULTIPLIER;

        // Create transform for rotation
        Quaternionf diskRotation = new Quaternionf().rotateY(rotation);
        VectorRenderer.Transform diskTransform = VectorRenderer.Transform.fromQuaternion(
                Vec3.ZERO, diskRotation, 1.0f, Vec3.ZERO
        );

        // Render components in order (back to front for transparency)
        renderPolarJets(pos, size, jetLength, diskTransform);
        renderAccretionDisk(pos, accretionInnerRadius, accretionOuterRadius, rotation, diskTransform);
        renderGravitationalLensing(pos, photonSphereRadius);
        renderPhotonSphere(pos, photonSphereRadius);
        renderEventHorizon(pos, eventHorizonRadius);
    }

    private static void renderEventHorizon(Vec3 center, float radius) {
        // Create a perfect black sphere
        List<Vec3> spherePoints = createSphere(center, radius, EVENT_HORIZON_SEGMENTS);

        // Render as filled polygons to create solid black sphere
        for (int i = 0; i < EVENT_HORIZON_SEGMENTS; i++) {
            for (int j = 0; j < EVENT_HORIZON_SEGMENTS; j++) {
                List<Vec3> quad = new ArrayList<>();

                int idx1 = i * EVENT_HORIZON_SEGMENTS + j;
                int idx2 = i * EVENT_HORIZON_SEGMENTS + ((j + 1) % EVENT_HORIZON_SEGMENTS);
                int idx3 = ((i + 1) % EVENT_HORIZON_SEGMENTS) * EVENT_HORIZON_SEGMENTS + ((j + 1) % EVENT_HORIZON_SEGMENTS);
                int idx4 = ((i + 1) % EVENT_HORIZON_SEGMENTS) * EVENT_HORIZON_SEGMENTS + j;

                if (idx1 < spherePoints.size() && idx2 < spherePoints.size() &&
                        idx3 < spherePoints.size() && idx4 < spherePoints.size()) {
                    quad.add(spherePoints.get(idx1));
                    quad.add(spherePoints.get(idx2));
                    quad.add(spherePoints.get(idx3));
                    quad.add(spherePoints.get(idx4));

                    VectorRenderer.drawFilledPolygonWorld(
                            quad, COLOR_EVENT_HORIZON, true, 1, VectorRenderer.Transform.IDENTITY
                    );
                }
            }
        }
    }

    private static void renderPhotonSphere(Vec3 center, float radius) {
        // Create wireframe sphere for photon sphere
        VectorRenderer.Wireframe photonSphere = VectorRenderer.createWireframe();

        // Add horizontal rings
        for (int ring = 0; ring < 8; ring++) {
            float y = (ring - 3.5f) * radius * 0.25f;
            float ringRadius = (float) Math.sqrt(Math.max(0.0f, radius * radius - y * y));

            for (int i = 0; i < 32; i++) {
                float angle1 = (float) (i * Math.PI * 2.0 / 32.0);
                float angle2 = (float) ((i + 1) * Math.PI * 2.0 / 32.0);

                Vec3 p1 = new Vec3((double) ((float) Math.cos(angle1) * ringRadius),
                        (double) y,
                        (double) ((float) Math.sin(angle1) * ringRadius));
                Vec3 p2 = new Vec3((double) ((float) Math.cos(angle2) * ringRadius),
                        (double) y,
                        (double) ((float) Math.sin(angle2) * ringRadius));

                photonSphere.addLine(p1, p2);
            }
        }

        // Add vertical lines
        for (int i = 0; i < 16; i++) {
            float angle = (float) (i * Math.PI * 2.0 / 16.0);

            for (int j = 0; j < 16; j++) {
                float t1 = j / 16.0f;
                float t2 = (j + 1) / 16.0f;

                float y1 = radius * (1 - 2 * t1);
                float y2 = radius * (1 - 2 * t2);
                float r1 = (float) Math.sqrt(Math.max(0.0f, radius * radius - y1 * y1));
                float r2 = (float) Math.sqrt(Math.max(0.0f, radius * radius - y2 * y2));

                Vec3 p1 = new Vec3((double) ((float) Math.cos(angle) * r1),
                        (double) y1,
                        (double) ((float) Math.sin(angle) * r1));
                Vec3 p2 = new Vec3((double) ((float) Math.cos(angle) * r2),
                        (double) y2,
                        (double) ((float) Math.sin(angle) * r2));

                photonSphere.addLine(p1, p2);
            }
        }

        VectorRenderer.drawWireframeWorld(
                photonSphere, center, COLOR_PHOTON_SPHERE, 0.02f, false, true, 1, VectorRenderer.Transform.IDENTITY
        );
    }

    private static void renderAccretionDisk(Vec3 center, float innerRadius, float outerRadius, float rotation, VectorRenderer.Transform transform) {
        // Create multiple rings for the accretion disk with varying colors and opacity
        for (int ring = 0; ring < ACCRETION_DISK_RINGS; ring++) {
            float ringProgress = ring / (float) (ACCRETION_DISK_RINGS - 1);
            float radius = innerRadius + ringProgress * (outerRadius - innerRadius);
            float thickness = (outerRadius - innerRadius) / ACCRETION_DISK_RINGS * 0.8f;

            // Color interpolation from hot white inner to cool red outer
            int color = interpolateColor(COLOR_ACCRETION_INNER, COLOR_ACCRETION_OUTER, ringProgress);

            // Add temperature-based brightness variation
            float brightness = 1.0f - ringProgress * 0.7f;
            color = adjustColorBrightness(color, brightness);

            // Create spiral pattern for realistic disk structure
            List<Vec3> ringPoints = new ArrayList<>();
            for (int i = 0; i <= ACCRETION_DISK_SEGMENTS; i++) {
                float angle = (float) (i * Math.PI * 2.0 / ACCRETION_DISK_SEGMENTS);

                // Add spiral distortion
                float spiralOffset = (float) (Math.sin(angle * 3.0f + rotation * 2.0f) * 0.1f * ringProgress);
                float adjustedRadius = radius + spiralOffset * radius;

                // Add vertical turbulence
                float turbulence = (float) (Math.sin(angle * 7.0f + rotation * 3.0f) * 0.05f * radius * ringProgress);

                Vec3 point = new Vec3(
                        (double) ((float) Math.cos(angle) * adjustedRadius),
                        (double) turbulence,
                        (double) ((float) Math.sin(angle) * adjustedRadius)
                );
                ringPoints.add(point);
            }

            // Render ring as polyline with emissive-like effect
            VectorRenderer.drawPolylineWorld(
                    ringPoints, color, thickness, false, 1, transform
            );

            // Add inner glow effect
            if (ring < 3) {
                float glowThickness = thickness * 2.0f;
                int glowColor = adjustColorAlpha(color, 0.3f);
                VectorRenderer.drawPolylineWorld(
                        ringPoints, glowColor, glowThickness, false, 1, transform
                );
            }
        }

        // Add particle streams and jets of material
        renderDiskTurbulence(center, innerRadius, outerRadius, rotation, transform);
    }

    private static void renderDiskTurbulence(Vec3 center, float innerRadius, float outerRadius, float rotation, VectorRenderer.Transform transform) {
        // Create turbulent streams within the disk
        for (int i = 0; i < 12; i++) {
            float baseAngle = (float) (i * Math.PI * 2.0 / 12.0 + rotation * 0.5f);
            List<Vec3> streamPoints = new ArrayList<>();

            for (int j = 0; j < 10; j++) {
                float t = j / 9.0f;
                float radius = innerRadius + t * (outerRadius - innerRadius);

                // Add chaotic motion
                float angle = baseAngle + (float) Math.sin(t * Math.PI * 2.0 + rotation * 4.0f) * 0.2f;
                float height = (float) (Math.sin(t * Math.PI * 3.0 + rotation * 2.0f) * 0.1f * radius);

                Vec3 point = new Vec3(
                        (double) ((float) Math.cos(angle) * radius),
                        (double) height,
                        (double) ((float) Math.sin(angle) * radius)
                );
                streamPoints.add(point);
            }

            int streamColor = interpolateColor(COLOR_ACCRETION_INNER, COLOR_ACCRETION_OUTER, RAND.nextFloat());
            streamColor = adjustColorAlpha(streamColor, 0.6f);

            VectorRenderer.drawPolylineWorld(
                    streamPoints, streamColor, 0.03f, false, 1, transform
            );
        }
    }

    private static void renderPolarJets(Vec3 center, float blackHoleSize, float jetLength, VectorRenderer.Transform transform) {
        // Create relativistic jets from the poles
        float jetRadius = blackHoleSize * 0.3f;
        int jetSegments = 32;

        for (int pole = 0; pole < 2; pole++) {
            float direction = pole == 0 ? 1.0f : -1.0f;

            // Create jet core
            List<Vec3> jetCore = new ArrayList<>();
            for (int i = 0; i <= 20; i++) {
                float t = i / 20.0f;
                float y = direction * t * jetLength;
                float radius = jetRadius * (1.0f - t * 0.8f); // Tapers toward the end

                // Add slight helical twist
                float angle = (float) (t * Math.PI * 4.0);
                Vec3 point = new Vec3(
                        (double) ((float) Math.cos(angle) * radius * 0.1f),
                        (double) y,
                        (double) ((float) Math.sin(angle) * radius * 0.1f)
                );
                jetCore.add(point);
            }

            VectorRenderer.drawPolylineWorld(
                    jetCore, COLOR_JET_CORE, jetRadius * 0.2f, false, 1, transform
            );

            // Create jet outer glow
            for (int ring = 0; ring < 3; ring++) {
                List<Vec3> jetRing = new ArrayList<>();
                float ringRadius = jetRadius * (1.0f + ring * 0.3f);

                for (int i = 0; i <= jetSegments; i++) {
                    float angle = (float) (i * Math.PI * 2.0 / jetSegments);
                    float height = direction * jetLength * 0.1f;

                    Vec3 point = new Vec3(
                            (double) ((float) Math.cos(angle) * ringRadius),
                            (double) height,
                            (double) ((float) Math.sin(angle) * ringRadius)
                    );
                    jetRing.add(point);
                }

                int glowColor = adjustColorAlpha(COLOR_JET_OUTER, 0.2f / (ring + 1));
                VectorRenderer.drawPolylineWorld(
                        jetRing, glowColor, ringRadius * 0.1f, false, 1, transform
                );
            }
        }
    }

    private static void renderGravitationalLensing(Vec3 center, float radius) {
        // Create distortion rings to simulate gravitational lensing
        for (int ring = 0; ring < GRAVITATIONAL_LENS_RINGS; ring++) {
            float ringRadius = radius * (1.2f + ring * 0.3f);
            int ringColor = adjustColorAlpha(0x40FFFFFF, 0.1f / (ring + 1));

            VectorRenderer.Wireframe lensRing = VectorRenderer.createWireframe();

            for (int i = 0; i < 64; i++) {
                float angle1 = (float) (i * Math.PI * 2.0 / 64.0);
                float angle2 = (float) ((i + 1) * Math.PI * 2.0 / 64.0);

                // Add lensing distortion
                float distortion1 = 1.0f + (float) Math.sin(angle1 * 8.0f) * 0.05f;
                float distortion2 = 1.0f + (float) Math.sin(angle2 * 8.0f) * 0.05f;

                Vec3 p1 = new Vec3(
                        (double) ((float) Math.cos(angle1) * ringRadius * distortion1),
                        (double) 0.0f,
                        (double) ((float) Math.sin(angle1) * ringRadius * distortion1)
                );
                Vec3 p2 = new Vec3(
                        (double) ((float) Math.cos(angle2) * ringRadius * distortion2),
                        (double) 0.0f,
                        (double) ((float) Math.sin(angle2) * ringRadius * distortion2)
                );

                lensRing.addLine(p1, p2);
            }

            VectorRenderer.drawWireframeWorld(
                    lensRing, center, ringColor, 0.01f, false, true, 1, VectorRenderer.Transform.IDENTITY
            );
        }
    }

    // Utility methods
    private static List<Vec3> createSphere(Vec3 center, float radius, int segments) {
        List<Vec3> points = new ArrayList<>();

        for (int i = 0; i <= segments; i++) {
            double latD = Math.PI * (-0.5 + (double) i / (double) segments);
            float lat = (float) latD;
            float y = (float) Math.sin(latD);
            float xz = (float) Math.cos(latD);

            for (int j = 0; j <= segments; j++) {
                double lonD = 2.0 * Math.PI * (double) j / (double) segments;
                float lon = (float) lonD;
                float x = xz * (float) Math.cos(lonD);
                float z = xz * (float) Math.sin(lonD);

                points.add(center.add(new Vec3((double) (x * radius), (double) (y * radius), (double) (z * radius))));
            }
        }

        return points;
    }

    private static int interpolateColor(int color1, int color2, float t) {
        t = Math.max(0, Math.min(1, t));

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustColorBrightness(int color, float brightness) {
        brightness = Math.max(0, Math.min(1, brightness));

        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * brightness);
        int g = (int) (((color >> 8) & 0xFF) * brightness);
        int b = (int) ((color & 0xFF) * brightness);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustColorAlpha(int color, float alpha) {
        alpha = Math.max(0, Math.min(1, alpha));

        int newAlpha = (int) (255 * alpha);
        return (newAlpha << 24) | (color & 0xFFFFFF);
    }

    private static final class BlackHoleEffect {
        private final Vec3 position;
        private final float size;
        private final float rotationSpeed;
        private final int lifetime;
        private int age = 0;
        private float rotation = 0;

        BlackHoleEffect(Vec3 position, float size, float rotationSpeed, int lifetime) {
            this.position = position;
            this.size = size;
            this.rotationSpeed = rotationSpeed;
            this.lifetime = lifetime;
        }

        void tick() {
            if (lifetime >= 0) age++;
            rotation += rotationSpeed;
            if (rotation > Math.PI * 2.0f) rotation -= (float) (Math.PI * 2.0);
        }

        boolean isExpired() {
            return lifetime >= 0 && age >= lifetime;
        }

        float getCurrentRotation(float partialTick) {
            return rotation + rotationSpeed * partialTick;
        }
    }
}
