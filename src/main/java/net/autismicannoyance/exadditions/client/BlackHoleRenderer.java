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
 * Black hole renderer that matches the reference images with proper gravitational lensing,
 * polar jets, and realistic accretion disk visualization
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class BlackHoleRenderer {
    private static final Map<Integer, BlackHoleEffect> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // Visual detail constants
    private static final int ACCRETION_DISK_RINGS = 40;
    private static final int ACCRETION_DISK_SEGMENTS = 96;
    private static final int EVENT_HORIZON_SEGMENTS = 48;
    private static final int LENSING_RINGS = 8;
    private static final int JET_SEGMENTS = 32;
    private static final int MAGNETIC_FIELD_LINES = 16;

    // Color palette for realistic black hole visualization
    private static final int COLOR_EVENT_HORIZON = 0xFF000000;        // Pure black
    private static final int COLOR_PHOTON_SPHERE = 0xFFFFFFFF;        // Bright white lensing ring
    private static final int COLOR_LENSING_RING = 0xCCFFFFFF;         // White gravitational lensing
    private static final int COLOR_ACCRETION_WHITE = 0xFFFFFFFF;      // White hot inner disk
    private static final int COLOR_ACCRETION_BLUE = 0xFF88CCFF;       // Blue-white hot
    private static final int COLOR_ACCRETION_CYAN = 0xFF44AAFF;       // Cyan
    private static final int COLOR_ACCRETION_PURPLE = 0xFF6644FF;     // Blue-purple
    private static final int COLOR_ACCRETION_DARK = 0xFF2211AA;       // Dark purple-blue

    // Jet and magnetic field colors
    private static final int COLOR_JET_BRIGHT = 0xFF88DDFF;           // Bright blue-white jet
    private static final int COLOR_JET_MID = 0xFF4488CC;              // Mid-blue jet
    private static final int COLOR_JET_FAINT = 0xFF224488;            // Faint blue jet
    private static final int COLOR_MAGNETIC_FIELD = 0x60FF4488;       // Semi-transparent pink-red

    // Glow colors for atmospheric effects
    private static final int COLOR_INNER_GLOW = 0x80FFFFFF;           // Bright white glow
    private static final int COLOR_OUTER_GLOW = 0x40AACCFF;           // Blue atmospheric glow

    // Physical scaling constants
    private static final float EVENT_HORIZON_MULTIPLIER = 1.0f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float ACCRETION_INNER_MULTIPLIER = 2.5f;
    private static final float ACCRETION_OUTER_MULTIPLIER = 12.0f;
    private static final float JET_LENGTH_MULTIPLIER = 20.0f;
    private static final float JET_WIDTH_MULTIPLIER = 0.8f;

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
            renderBlackHole(effect, partialTick, mc);
        }
    }

    private static void renderBlackHole(BlackHoleEffect effect, float partialTick, Minecraft mc) {
        Vec3 pos = effect.position;
        float size = effect.size;
        float rotation = effect.getCurrentRotation(partialTick);
        float time = effect.getTime(partialTick);
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        // Calculate all relevant radii
        float eventHorizonRadius = size * EVENT_HORIZON_MULTIPLIER;
        float photonSphereRadius = size * PHOTON_SPHERE_MULTIPLIER;
        float accretionInnerRadius = size * ACCRETION_INNER_MULTIPLIER;
        float accretionOuterRadius = size * ACCRETION_OUTER_MULTIPLIER;
        float jetLength = size * JET_LENGTH_MULTIPLIER;
        float jetWidth = size * JET_WIDTH_MULTIPLIER;

        // Render components in order for proper depth and blending
        renderOuterGlow(pos, accretionOuterRadius * 1.5f, time);
        renderMagneticFieldLines(pos, accretionInnerRadius, accretionOuterRadius, rotation, time);
        renderPolarJets(pos, eventHorizonRadius, jetLength, jetWidth, rotation, time);
        renderAccretionDisk(pos, accretionInnerRadius, accretionOuterRadius, rotation, time, camPos);
        renderGravitationalLensing(pos, photonSphereRadius, eventHorizonRadius, time, camPos);
        renderEventHorizon(pos, eventHorizonRadius);
        renderInnerGlow(pos, eventHorizonRadius, photonSphereRadius, time);
    }

    private static void renderEventHorizon(Vec3 center, float radius) {
        // Create perfect black sphere using VectorRenderer
        VectorRenderer.drawSphereWorld(center, radius, COLOR_EVENT_HORIZON, 16, 24, false, 1, VectorRenderer.Transform.IDENTITY);
    }

    private static void renderGravitationalLensing(Vec3 center, float photonSphereRadius, float eventHorizonRadius, float time, Vec3 camPos) {
        // Main bright photon sphere ring (like Image 2) - make it semi-transparent
        List<Vec3> photonRing = new ArrayList<>();
        for (int i = 0; i <= 64; i++) {
            float angle = i * (float)Math.PI * 2.0f / 64.0f;

            // Add subtle distortions due to gravitational effects
            float distortion = 1f + (float)Math.sin(angle * 2f + time * 0.5f) * 0.05f;
            float radius = photonSphereRadius * distortion;

            // Slight vertical oscillation for 3D effect
            float height = (float)Math.sin(angle * 4f + time * 1.2f) * radius * 0.02f;

            Vec3 point = new Vec3(
                    Math.cos(angle) * radius,
                    height,
                    Math.sin(angle) * radius
            );
            photonRing.add(center.add(point));
        }

        // Make main lensing ring semi-transparent instead of fully opaque
        int semiTransparentPhotonColor = adjustColorAlpha(COLOR_PHOTON_SPHERE, 0.8f);
        VectorRenderer.drawPolylineWorld(photonRing, semiTransparentPhotonColor, photonSphereRadius * 0.15f, false, 1, VectorRenderer.Transform.IDENTITY);

        // Additional fainter lensing rings with better transparency
        for (int ring = 1; ring <= LENSING_RINGS; ring++) {
            float ringRadius = photonSphereRadius * (0.7f + ring * 0.15f);
            float alpha = Math.max(0.1f, 0.6f / ring); // Ensure minimum visibility
            int ringColor = adjustColorAlpha(COLOR_LENSING_RING, alpha);

            List<Vec3> lensingRing = new ArrayList<>();
            for (int i = 0; i <= 48; i++) {
                float angle = i * (float)Math.PI * 2.0f / 48.0f;
                float distortion = 1f + (float)Math.sin(angle * 3f + time * 0.3f + ring) * 0.08f;

                Vec3 point = new Vec3(
                        Math.cos(angle) * ringRadius * distortion,
                        (float)Math.sin(angle * 6f + time * ring) * ringRadius * 0.03f,
                        Math.sin(angle) * ringRadius * distortion
                );
                lensingRing.add(center.add(point));
            }

            VectorRenderer.drawPolylineWorld(lensingRing, ringColor, ringRadius * 0.08f, false, 1, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void renderAccretionDisk(Vec3 center, float innerRadius, float outerRadius,
                                            float rotation, float time, Vec3 camPos) {
        // Multi-layered accretion disk with realistic temperature gradient
        for (int ring = 0; ring < ACCRETION_DISK_RINGS; ring++) {
            float ringProgress = ring / (float)(ACCRETION_DISK_RINGS - 1);
            float radius = innerRadius + ringProgress * (outerRadius - innerRadius);

            // Temperature-based color (hotter = whiter, cooler = redder/bluer)
            int color;
            if (ringProgress < 0.1f) {
                // Inner white-hot region
                color = COLOR_ACCRETION_WHITE;
            } else if (ringProgress < 0.3f) {
                float t = (ringProgress - 0.1f) / 0.2f;
                color = interpolateColor(COLOR_ACCRETION_WHITE, COLOR_ACCRETION_BLUE, t);
            } else if (ringProgress < 0.6f) {
                float t = (ringProgress - 0.3f) / 0.3f;
                color = interpolateColor(COLOR_ACCRETION_BLUE, COLOR_ACCRETION_CYAN, t);
            } else if (ringProgress < 0.85f) {
                float t = (ringProgress - 0.6f) / 0.25f;
                color = interpolateColor(COLOR_ACCRETION_CYAN, COLOR_ACCRETION_PURPLE, t);
            } else {
                float t = (ringProgress - 0.85f) / 0.15f;
                color = interpolateColor(COLOR_ACCRETION_PURPLE, COLOR_ACCRETION_DARK, t);
            }

            // Brightness variation based on accretion activity
            float brightness = (1.2f - ringProgress * 0.4f) * (1f + (float)Math.sin(time * 2f + ring * 0.3f) * 0.15f);
            color = adjustColorBrightness(color, brightness);

            // Create spiral structure with turbulence - points are already in world space
            List<Vec3> diskRing = createAccretionRing(center, radius, rotation, time, ring);

            float thickness = (outerRadius - innerRadius) / ACCRETION_DISK_RINGS * 0.7f;
            // Use IDENTITY transform since points are already in correct world positions
            VectorRenderer.drawPolylineWorld(diskRing, color, thickness, false, 1, VectorRenderer.Transform.IDENTITY);

            // Add glow layer for bright inner rings
            if (ringProgress < 0.5f) {
                int glowColor = adjustColorAlpha(color, 0.3f);
                VectorRenderer.drawPolylineWorld(diskRing, glowColor, thickness * 2.5f, false, 1, VectorRenderer.Transform.IDENTITY);
            }
        }
    }

    private static void renderPolarJets(Vec3 center, float baseRadius, float jetLength, float jetWidth,
                                        float rotation, float time) {
        // Render both polar jets (up and down)
        for (int pole = 0; pole < 2; pole++) {
            float direction = pole == 0 ? 1.0f : -1.0f;

            // Create multiple jet streams for smooth edges
            int numStreams = 8; // Number of helical streams

            for (int stream = 0; stream < numStreams; stream++) {
                float streamOffset = stream * (float)Math.PI * 2.0f / numStreams;

                // Create jet path with helical structure
                List<Vec3> jetPath = new ArrayList<>();
                for (int i = 0; i <= JET_SEGMENTS; i++) {
                    float t = i / (float)JET_SEGMENTS;
                    float y = direction * t * jetLength;

                    // Jet expansion with distance
                    float expansion = 1f + t * 2f;
                    float helixRadius = jetWidth * expansion * 0.2f;

                    // Multiple helical components with phase offset
                    float helixAngle = t * (float)Math.PI * 6f + rotation * 2f + time * 3f + streamOffset;
                    float x = helixRadius * (float)Math.cos(helixAngle);
                    float z = helixRadius * (float)Math.sin(helixAngle);

                    jetPath.add(center.add(new Vec3(x, y, z)));
                }

                // Stream intensity based on distance from center
                float streamIntensity = 1.0f - (stream / (float)numStreams) * 0.6f;

                // Core bright stream
                int coreColor = adjustColorAlpha(COLOR_JET_BRIGHT, streamIntensity * 0.9f);
                VectorRenderer.drawPolylineWorld(jetPath, coreColor, jetWidth * 0.3f * streamIntensity, false, 1, VectorRenderer.Transform.IDENTITY);
            }

            // Add central core beam
            List<Vec3> coreJetPath = new ArrayList<>();
            for (int i = 0; i <= JET_SEGMENTS; i++) {
                float t = i / (float)JET_SEGMENTS;
                float y = direction * t * jetLength;

                // Slight wobble for the core
                float wobble = (float)Math.sin(t * Math.PI * 3f + time * 4f) * jetWidth * 0.1f;
                float x = wobble * (float)Math.cos(rotation + time);
                float z = wobble * (float)Math.sin(rotation + time);

                coreJetPath.add(center.add(new Vec3(x, y, z)));
            }

            // Render layered core for smooth falloff
            for (int layer = 0; layer < 5; layer++) {
                float layerAlpha = (5 - layer) / 5.0f * 0.6f;
                float layerWidth = jetWidth * (0.8f + layer * 0.4f);
                int layerColor;

                switch (layer) {
                    case 0: layerColor = adjustColorAlpha(COLOR_JET_BRIGHT, layerAlpha); break;
                    case 1: layerColor = adjustColorAlpha(COLOR_JET_BRIGHT, layerAlpha * 0.8f); break;
                    case 2: layerColor = adjustColorAlpha(COLOR_JET_MID, layerAlpha * 0.6f); break;
                    case 3: layerColor = adjustColorAlpha(COLOR_JET_MID, layerAlpha * 0.4f); break;
                    default: layerColor = adjustColorAlpha(COLOR_JET_FAINT, layerAlpha * 0.3f); break;
                }

                VectorRenderer.drawPolylineWorld(coreJetPath, layerColor, layerWidth, false, 1, VectorRenderer.Transform.IDENTITY);
            }
        }
    }

    private static void renderMagneticFieldLines(Vec3 center, float innerRadius, float outerRadius,
                                                 float rotation, float time) {
        // Magnetic field lines extending from the accretion disk
        for (int field = 0; field < MAGNETIC_FIELD_LINES; field++) {
            float fieldAngle = field * (float)Math.PI * 2.0f / MAGNETIC_FIELD_LINES + rotation * 0.5f;

            // Create curved field lines
            List<Vec3> fieldLine = new ArrayList<>();
            for (int i = 0; i <= 24; i++) {
                float t = i / 24.0f;
                float radius = innerRadius + t * (outerRadius - innerRadius) * 0.8f;

                // Height follows magnetic field geometry
                float height = (float)Math.sin(t * Math.PI) * radius * 1.2f;

                // Add field line twist
                float twist = t * (float)Math.PI * 0.3f + time * 0.4f;
                float adjustedAngle = fieldAngle + twist * 0.2f;

                Vec3 point = new Vec3(
                        Math.cos(adjustedAngle) * radius,
                        height,
                        Math.sin(adjustedAngle) * radius
                );
                fieldLine.add(center.add(point));
            }

            float fieldStrength = (float)Math.sin(time + field * 0.5f) * 0.3f + 0.7f;
            int fieldColor = adjustColorAlpha(COLOR_MAGNETIC_FIELD, fieldStrength * 0.4f);

            VectorRenderer.drawPolylineWorld(fieldLine, fieldColor, innerRadius * 0.02f, false, 1, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void renderOuterGlow(Vec3 center, float radius, float time) {
        // Atmospheric glow around the entire structure
        for (int layer = 0; layer < 4; layer++) {
            float glowRadius = radius * (0.8f + layer * 0.2f);
            float alpha = 0.15f / (layer + 1);
            int glowColor = adjustColorAlpha(COLOR_OUTER_GLOW, alpha);

            List<Vec3> glowRing = new ArrayList<>();
            for (int i = 0; i <= 32; i++) {
                float angle = i * (float)Math.PI * 2.0f / 32.0f;
                float pulsation = 1f + (float)Math.sin(time * 1.5f + layer + angle * 2f) * 0.1f;

                Vec3 point = new Vec3(
                        Math.cos(angle) * glowRadius * pulsation,
                        (float)Math.sin(angle * 3f + time + layer) * glowRadius * 0.05f,
                        Math.sin(angle) * glowRadius * pulsation
                );
                glowRing.add(center.add(point));
            }

            VectorRenderer.drawPolylineWorld(glowRing, glowColor, glowRadius * 0.3f, false, 1, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void renderInnerGlow(Vec3 center, float eventHorizonRadius, float photonSphereRadius, float time) {
        // Much more subtle inner glow - reduce layers and opacity
        for (int i = 0; i < 3; i++) {  // Reduced from 6 to 3 layers
            float glowRadius = eventHorizonRadius * (1.05f + i * 0.06f);
            if (glowRadius > photonSphereRadius * 0.9f) break;

            float alpha = 0.2f / (i + 1);  // Much lower alpha (was 0.8f)
            int glowColor = adjustColorAlpha(COLOR_INNER_GLOW, alpha);

            List<Vec3> innerGlow = new ArrayList<>();
            for (int j = 0; j <= 24; j++) {  // Reduced segments for performance
                float angle = j * (float)Math.PI * 2.0f / 24.0f;
                float fluctuation = 1f + (float)Math.sin(angle * 6f + time * 3f) * 0.04f;  // Reduced fluctuation

                Vec3 point = new Vec3(
                        Math.cos(angle) * glowRadius * fluctuation,
                        (float)Math.sin(angle * 8f + time * 4f) * glowRadius * 0.02f,  // Reduced height variation
                        Math.sin(angle) * glowRadius * fluctuation
                );
                innerGlow.add(center.add(point));
            }

            VectorRenderer.drawPolylineWorld(innerGlow, glowColor, glowRadius * 0.1f, false, 1, VectorRenderer.Transform.IDENTITY);  // Reduced thickness
        }
    }

    private static List<Vec3> createAccretionRing(Vec3 center, float radius, float rotation, float time, int ringIndex) {
        List<Vec3> points = new ArrayList<>();

        for (int i = 0; i <= ACCRETION_DISK_SEGMENTS; i++) {
            float angle = i * (float)Math.PI * 2.0f / ACCRETION_DISK_SEGMENTS + rotation;

            // Multi-armed spiral structure
            float spiral1 = (float)Math.sin(angle * 2f - rotation * 2f) * 0.1f;
            float spiral2 = (float)Math.sin(angle * 3f - rotation * 3f + ringIndex * 0.5f) * 0.06f;

            // Turbulence and density variations
            float turbulence = ((float)Math.sin(angle * 7f + time * 3f) * 0.02f +
                    (float)Math.sin(angle * 13f - time * 5f + ringIndex) * 0.015f) * radius;

            float adjustedRadius = radius * (1f + spiral1 + spiral2) + turbulence;

            // Vertical structure from hydrostatic equilibrium - keep this small for a flat disk
            float scaleHeight = radius * 0.01f * (1f + (float)Math.sin(angle * 5f + time * 2f) * 0.2f);

            Vec3 point = new Vec3(
                    Math.cos(angle) * adjustedRadius,
                    scaleHeight, // Keep this small so disk stays mostly flat
                    Math.sin(angle) * adjustedRadius
            );
            points.add(center.add(point)); // Add center offset here to get world position
        }

        return points;
    }

    // Utility methods
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
     * BlackHoleEffect class for managing individual black hole instances
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