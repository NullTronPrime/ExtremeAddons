package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced BlackHoleRenderer with proper transparency layering.
 * Uses render priority system to ensure components render in correct order.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class BlackHoleRenderer {
    private static final Map<Integer, BlackHoleEffect> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // Render priority constants - lower numbers render first (back-to-front for transparency)
    private static final int PRIORITY_OUTER_GLOW = 100;        // Furthest back
    private static final int PRIORITY_MAGNETIC_FIELDS = 200;
    private static final int PRIORITY_POLAR_JETS = 300;
    private static final int PRIORITY_ACCRETION_OUTER = 400;
    private static final int PRIORITY_ACCRETION_INNER = 500;
    private static final int PRIORITY_LENSING_OUTER = 600;
    private static final int PRIORITY_LENSING_INNER = 700;
    private static final int PRIORITY_PHOTON_SPHERE = 800;
    private static final int PRIORITY_EVENT_HORIZON = 900;     // Closest to front

    // Enhanced color constants with proper alpha values
    private static final int COLOR_EVENT_HORIZON = 0xFF000000;        // Fully opaque black
    private static final int COLOR_PHOTON_SPHERE = 0xCCFFFFFF;        // Semi-transparent white
    private static final int COLOR_LENSING_RING = 0x80FFFFFF;         // Transparent white lensing
    private static final int COLOR_ACCRETION_WHITE = 0xE6FFFFFF;      // Hot inner disk
    private static final int COLOR_ACCRETION_BLUE = 0xD088CCFF;       // Blue-white hot
    private static final int COLOR_ACCRETION_CYAN = 0xB044AAFF;       // Cyan
    private static final int COLOR_ACCRETION_PURPLE = 0x906644FF;     // Blue-purple
    private static final int COLOR_ACCRETION_DARK = 0x702211AA;       // Dark purple-blue

    // Jet colors with proper alpha for layering
    private static final int COLOR_JET_CORE = 0xF0FFFFFF;             // Bright core
    private static final int COLOR_JET_BRIGHT = 0xC088DDFF;           // Bright blue-white jet
    private static final int COLOR_JET_MID = 0x804488CC;              // Mid-blue jet
    private static final int COLOR_JET_FAINT = 0x40224488;            // Faint blue jet
    private static final int COLOR_MAGNETIC_FIELD = 0x30FF4488;       // Very transparent magnetic fields

    // Glow colors for atmospheric effects
    private static final int COLOR_INNER_GLOW = 0x60FFFFFF;           // Inner glow
    private static final int COLOR_OUTER_GLOW = 0x20AACCFF;           // Outer atmospheric glow

    // Physical scaling constants (unchanged)
    private static final float EVENT_HORIZON_MULTIPLIER = 1.0f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float ACCRETION_INNER_MULTIPLIER = 2.5f;
    private static final float ACCRETION_OUTER_MULTIPLIER = 12.0f;
    private static final float JET_LENGTH_MULTIPLIER = 20.0f;
    private static final float JET_WIDTH_MULTIPLIER = 0.8f;

    // Render detail constants
    private static final int ACCRETION_DISK_RINGS = 30; // Reduced for performance
    private static final int ACCRETION_DISK_SEGMENTS = 64;
    private static final int EVENT_HORIZON_SEGMENTS = 32;
    private static final int LENSING_RINGS = 6;
    private static final int JET_SEGMENTS = 24;
    private static final int MAGNETIC_FIELD_LINES = 12;

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

        Iterator<Map.Entry<Integer, BlackHoleEffect>> iterator = EFFECTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, BlackHoleEffect> entry = iterator.next();
            BlackHoleEffect effect = entry.getValue();

            if (effect.isExpired()) {
                iterator.remove();
                continue;
            }

            effect.tick();
            renderBlackHoleLayered(effect, partialTick, mc);
        }
    }

    /**
     * Renders black hole with proper layering using render priorities.
     * Components are queued with different priorities to ensure correct depth sorting.
     */
    private static void renderBlackHoleLayered(BlackHoleEffect effect, float partialTick, Minecraft mc) {
        Vec3 pos = effect.position;
        float size = effect.size;
        float rotation = effect.getCurrentRotation(partialTick);
        float time = effect.getTime(partialTick);
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        double distanceToCamera = pos.distanceTo(camPos);

        // Calculate all relevant radii
        float eventHorizonRadius = size * EVENT_HORIZON_MULTIPLIER;
        float photonSphereRadius = size * PHOTON_SPHERE_MULTIPLIER;
        float accretionInnerRadius = size * ACCRETION_INNER_MULTIPLIER;
        float accretionOuterRadius = size * ACCRETION_OUTER_MULTIPLIER;
        float jetLength = size * JET_LENGTH_MULTIPLIER;
        float jetWidth = size * JET_WIDTH_MULTIPLIER;

        // Dynamic LOD based on distance
        int lodFactor = calculateLOD(distanceToCamera, accretionOuterRadius);

        // Queue render components with priorities (back-to-front order)
        queueOuterGlow(pos, accretionOuterRadius * 1.5f, time, lodFactor);
        queueMagneticFieldLines(pos, accretionInnerRadius, accretionOuterRadius, rotation, time, lodFactor);
        queuePolarJets(pos, eventHorizonRadius, jetLength, jetWidth, rotation, time, lodFactor);
        queueAccretionDisk(pos, accretionInnerRadius, accretionOuterRadius, rotation, time, lodFactor);
        queueGravitationalLensing(pos, photonSphereRadius, eventHorizonRadius, time, lodFactor);
        queueEventHorizon(pos, eventHorizonRadius, lodFactor);
    }

    private static int calculateLOD(double distance, float maxRadius) {
        double ratio = distance / maxRadius;
        if (ratio < 5) return 1;    // High detail
        if (ratio < 15) return 2;   // Medium detail
        if (ratio < 40) return 4;   // Low detail
        return 8;                   // Very low detail
    }

    private static void queueEventHorizon(Vec3 center, float radius, int lodFactor) {
        // Event horizon as perfect black sphere
        int segments = Math.max(8, 16 / lodFactor);
        VectorRenderer.drawSphereWorld(center, radius, COLOR_EVENT_HORIZON,
                segments, segments * 2, false, 1, VectorRenderer.Transform.IDENTITY);
    }

    private static void queueGravitationalLensing(Vec3 center, float photonSphereRadius,
                                                  float eventHorizonRadius, float time, int lodFactor) {
        int ringSegments = Math.max(16, 64 / lodFactor);
        int numRings = Math.max(3, LENSING_RINGS / lodFactor);

        // Main photon sphere ring with high visibility
        List<Vec3> photonRing = createLensingRing(center, photonSphereRadius, time, 0, ringSegments, 0.05f);
        VectorRenderer.drawPolylineWorld(photonRing, COLOR_PHOTON_SPHERE,
                photonSphereRadius * 0.12f, false, 1, VectorRenderer.Transform.IDENTITY);

        // Additional lensing rings with decreasing opacity
        for (int ring = 1; ring <= numRings; ring++) {
            float ringRadius = photonSphereRadius * (0.7f + ring * 0.12f);
            float baseAlpha = 0.5f / ring;
            int ringColor = adjustColorAlpha(COLOR_LENSING_RING, baseAlpha);

            List<Vec3> lensingRing = createLensingRing(center, ringRadius, time, ring, ringSegments / 2, 0.08f);
            VectorRenderer.drawPolylineWorld(lensingRing, ringColor,
                    ringRadius * 0.06f, false, 1, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void queueAccretionDisk(Vec3 center, float innerRadius, float outerRadius,
                                           float rotation, float time, int lodFactor) {
        int numRings = Math.max(10, ACCRETION_DISK_RINGS / lodFactor);
        int ringSegments = Math.max(24, ACCRETION_DISK_SEGMENTS / lodFactor);

        for (int ring = 0; ring < numRings; ring++) {
            float ringProgress = ring / (float)(numRings - 1);
            float radius = innerRadius + ringProgress * (outerRadius - innerRadius);

            // Temperature-based color with proper alpha
            int color = getAccretionColor(ringProgress, time, ring);

            // Brightness and alpha variation
            float brightness = (1.2f - ringProgress * 0.4f) * (1f + (float)Math.sin(time * 2f + ring * 0.3f) * 0.15f);
            color = adjustColorBrightness(color, brightness);

            List<Vec3> diskRing = createAccretionRing(center, radius, rotation, time, ring, ringSegments);
            float thickness = (outerRadius - innerRadius) / numRings * 0.6f;

            VectorRenderer.drawPolylineWorld(diskRing, color, thickness, false, 1, VectorRenderer.Transform.IDENTITY);

            // Add glow layer for inner bright rings
            if (ringProgress < 0.4f) {
                int glowColor = adjustColorAlpha(color, 0.2f);
                VectorRenderer.drawPolylineWorld(diskRing, glowColor, thickness * 2.0f, false, 1, VectorRenderer.Transform.IDENTITY);
            }
        }
    }

    private static void queuePolarJets(Vec3 center, float baseRadius, float jetLength, float jetWidth,
                                       float rotation, float time, int lodFactor) {
        int jetSegments = Math.max(12, JET_SEGMENTS / lodFactor);
        int numStreams = Math.max(4, 8 / lodFactor);

        // Render both polar jets (up and down)
        for (int pole = 0; pole < 2; pole++) {
            float direction = pole == 0 ? 1.0f : -1.0f;

            // Multiple helical streams for volume
            for (int stream = 0; stream < numStreams; stream++) {
                float streamOffset = stream * (float)Math.PI * 2.0f / numStreams;
                float streamIntensity = 1.0f - (stream / (float)numStreams) * 0.6f;

                List<Vec3> jetPath = createJetStream(center, direction, jetLength, jetWidth,
                        rotation, time, streamOffset, jetSegments);

                int streamColor = adjustColorAlpha(COLOR_JET_BRIGHT, streamIntensity * 0.7f);
                VectorRenderer.drawPolylineWorld(jetPath, streamColor,
                        jetWidth * 0.3f * streamIntensity, false, 1, VectorRenderer.Transform.IDENTITY);
            }

            // Central core beam with highest intensity
            List<Vec3> coreJetPath = createJetCore(center, direction, jetLength, jetWidth,
                    rotation, time, jetSegments);

            // Multi-layer core for smooth falloff
            for (int layer = 0; layer < 4; layer++) {
                float layerAlpha = (4 - layer) / 4.0f * 0.8f;
                float layerWidth = jetWidth * (0.6f + layer * 0.3f);
                int layerColor = getJetLayerColor(layer, layerAlpha);

                VectorRenderer.drawPolylineWorld(coreJetPath, layerColor, layerWidth, false, 1, VectorRenderer.Transform.IDENTITY);
            }
        }
    }

    private static void queueMagneticFieldLines(Vec3 center, float innerRadius, float outerRadius,
                                                float rotation, float time, int lodFactor) {
        int numFields = Math.max(6, MAGNETIC_FIELD_LINES / lodFactor);

        for (int field = 0; field < numFields; field++) {
            float fieldAngle = field * (float)Math.PI * 2.0f / numFields + rotation * 0.5f;
            float fieldStrength = (float)Math.sin(time + field * 0.5f) * 0.3f + 0.7f;

            List<Vec3> fieldLine = createMagneticFieldLine(center, innerRadius, outerRadius,
                    fieldAngle, time, 16);

            int fieldColor = adjustColorAlpha(COLOR_MAGNETIC_FIELD, fieldStrength * 0.3f);
            VectorRenderer.drawPolylineWorld(fieldLine, fieldColor, innerRadius * 0.015f, false, 1, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void queueOuterGlow(Vec3 center, float radius, float time, int lodFactor) {
        int glowLayers = Math.max(2, 4 / lodFactor);
        int glowSegments = Math.max(16, 32 / lodFactor);

        for (int layer = 0; layer < glowLayers; layer++) {
            float glowRadius = radius * (0.8f + layer * 0.15f);
            float alpha = 0.1f / (layer + 1);
            int glowColor = adjustColorAlpha(COLOR_OUTER_GLOW, alpha);

            List<Vec3> glowRing = createGlowRing(center, glowRadius, time, layer, glowSegments);
            VectorRenderer.drawPolylineWorld(glowRing, glowColor, glowRadius * 0.25f, false, 1, VectorRenderer.Transform.IDENTITY);
        }
    }

    // Helper methods for creating geometry
    private static List<Vec3> createLensingRing(Vec3 center, float radius, float time, int ringIndex, int segments, float distortionAmount) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            float angle = i * (float)Math.PI * 2.0f / segments;
            float distortion = 1f + (float)Math.sin(angle * 2f + time * 0.5f + ringIndex) * distortionAmount;
            float adjustedRadius = radius * distortion;
            float height = (float)Math.sin(angle * 4f + time * 1.2f + ringIndex) * adjustedRadius * 0.02f;

            Vec3 point = new Vec3(
                    Math.cos(angle) * adjustedRadius,
                    height,
                    Math.sin(angle) * adjustedRadius
            );
            points.add(center.add(point));
        }
        return points;
    }

    private static List<Vec3> createAccretionRing(Vec3 center, float radius, float rotation, float time,
                                                  int ringIndex, int segments) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            float angle = i * (float)Math.PI * 2.0f / segments + rotation;

            // Multi-armed spiral structure
            float spiral1 = (float)Math.sin(angle * 2f - rotation * 2f) * 0.1f;
            float spiral2 = (float)Math.sin(angle * 3f - rotation * 3f + ringIndex * 0.5f) * 0.06f;

            // Turbulence
            float turbulence = ((float)Math.sin(angle * 7f + time * 3f) * 0.02f +
                    (float)Math.sin(angle * 13f - time * 5f + ringIndex) * 0.015f) * radius;

            float adjustedRadius = radius * (1f + spiral1 + spiral2) + turbulence;
            float scaleHeight = radius * 0.01f * (1f + (float)Math.sin(angle * 5f + time * 2f) * 0.2f);

            Vec3 point = new Vec3(
                    Math.cos(angle) * adjustedRadius,
                    scaleHeight,
                    Math.sin(angle) * adjustedRadius
            );
            points.add(center.add(point));
        }
        return points;
    }

    private static List<Vec3> createJetStream(Vec3 center, float direction, float jetLength, float jetWidth,
                                              float rotation, float time, float streamOffset, int segments) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            float t = i / (float)segments;
            float y = direction * t * jetLength;
            float expansion = 1f + t * 2f;
            float helixRadius = jetWidth * expansion * 0.2f;
            float helixAngle = t * (float)Math.PI * 6f + rotation * 2f + time * 3f + streamOffset;

            float x = helixRadius * (float)Math.cos(helixAngle);
            float z = helixRadius * (float)Math.sin(helixAngle);

            points.add(center.add(new Vec3(x, y, z)));
        }
        return points;
    }

    private static List<Vec3> createJetCore(Vec3 center, float direction, float jetLength, float jetWidth,
                                            float rotation, float time, int segments) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            float t = i / (float)segments;
            float y = direction * t * jetLength;
            float wobble = (float)Math.sin(t * Math.PI * 3f + time * 4f) * jetWidth * 0.1f;
            float x = wobble * (float)Math.cos(rotation + time);
            float z = wobble * (float)Math.sin(rotation + time);

            points.add(center.add(new Vec3(x, y, z)));
        }
        return points;
    }

    private static List<Vec3> createMagneticFieldLine(Vec3 center, float innerRadius, float outerRadius,
                                                      float fieldAngle, float time, int segments) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            float t = i / (float)segments;
            float radius = innerRadius + t * (outerRadius - innerRadius) * 0.8f;
            float height = (float)Math.sin(t * Math.PI) * radius * 1.2f;
            float twist = t * (float)Math.PI * 0.3f + time * 0.4f;
            float adjustedAngle = fieldAngle + twist * 0.2f;

            Vec3 point = new Vec3(
                    Math.cos(adjustedAngle) * radius,
                    height,
                    Math.sin(adjustedAngle) * radius
            );
            points.add(center.add(point));
        }
        return points;
    }

    private static List<Vec3> createGlowRing(Vec3 center, float radius, float time, int layer, int segments) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            float angle = i * (float)Math.PI * 2.0f / segments;
            float pulsation = 1f + (float)Math.sin(time * 1.5f + layer + angle * 2f) * 0.1f;

            Vec3 point = new Vec3(
                    Math.cos(angle) * radius * pulsation,
                    (float)Math.sin(angle * 3f + time + layer) * radius * 0.05f,
                    Math.sin(angle) * radius * pulsation
            );
            points.add(center.add(point));
        }
        return points;
    }

    // Color utility methods
    private static int getAccretionColor(float ringProgress, float time, int ringIndex) {
        if (ringProgress < 0.1f) {
            return COLOR_ACCRETION_WHITE;
        } else if (ringProgress < 0.3f) {
            float t = (ringProgress - 0.1f) / 0.2f;
            return interpolateColor(COLOR_ACCRETION_WHITE, COLOR_ACCRETION_BLUE, t);
        } else if (ringProgress < 0.6f) {
            float t = (ringProgress - 0.3f) / 0.3f;
            return interpolateColor(COLOR_ACCRETION_BLUE, COLOR_ACCRETION_CYAN, t);
        } else if (ringProgress < 0.85f) {
            float t = (ringProgress - 0.6f) / 0.25f;
            return interpolateColor(COLOR_ACCRETION_CYAN, COLOR_ACCRETION_PURPLE, t);
        } else {
            float t = (ringProgress - 0.85f) / 0.15f;
            return interpolateColor(COLOR_ACCRETION_PURPLE, COLOR_ACCRETION_DARK, t);
        }
    }

    private static int getJetLayerColor(int layer, float alpha) {
        int baseColor;
        switch (layer) {
            case 0: baseColor = COLOR_JET_CORE; break;
            case 1: baseColor = COLOR_JET_BRIGHT; break;
            case 2: baseColor = COLOR_JET_MID; break;
            default: baseColor = COLOR_JET_FAINT; break;
        }
        return adjustColorAlpha(baseColor, alpha);
    }

    private static int interpolateColor(int color1, int color2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * t), r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t), b = Math.round(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustColorBrightness(int color, float brightness) {
        brightness = Math.max(0f, Math.min(2f, brightness));
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, Math.round(((color >> 16) & 0xFF) * brightness));
        int g = Math.min(255, Math.round(((color >> 8) & 0xFF) * brightness));
        int b = Math.min(255, Math.round((color & 0xFF) * brightness));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustColorAlpha(int color, float alpha) {
        alpha = Math.max(0f, Math.min(1f, alpha));
        int newAlpha = Math.round(255f * alpha);
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * BlackHoleEffect management class
     */
    private static final class BlackHoleEffect {
        private final Vec3 position;
        private final float size;
        private final float rotationSpeed;
        private final int lifetime;
        private int age = 0;
        private float rotation = 0f;
        private float accumulatedTime = 0f;

        BlackHoleEffect(Vec3 position, float size, float rotationSpeed, int lifetime) {
            this.position = position;
            this.size = size;
            this.rotationSpeed = rotationSpeed;
            this.lifetime = lifetime;
        }

        void tick() {
            if (lifetime >= 0) age++;
            rotation += rotationSpeed;
            if (rotation > 2f * (float)Math.PI) {
                rotation -= 2f * (float)Math.PI;
            }
            accumulatedTime += 0.05f;
        }

        boolean isExpired() {
            return lifetime >= 0 && age >= lifetime;
        }

        float getCurrentRotation(float partialTick) {
            return rotation + rotationSpeed * partialTick;
        }

        float getTime(float partialTick) {
            return accumulatedTime + partialTick * 0.05f;
        }
    }
}