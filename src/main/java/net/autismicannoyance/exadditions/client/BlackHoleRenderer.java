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
 * Ultra-realistic black hole renderer with accretion disk, event horizon,
 * gravitational lensing, relativistic jets, and Hawking radiation effects
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class BlackHoleRenderer {
    private static final Map<Integer, BlackHoleEffect> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // Visual constants - increased quality
    private static final int EVENT_HORIZON_SEGMENTS = 64;
    private static final int ACCRETION_DISK_RINGS = 16;
    private static final int ACCRETION_DISK_SEGMENTS = 128;
    private static final int GRAVITATIONAL_LENS_RINGS = 8;
    private static final int PHOTON_SPHERE_DETAIL = 48;

    // Enhanced color palette with glow effects
    private static final int COLOR_EVENT_HORIZON = 0xFF000000;
    private static final int COLOR_PHOTON_SPHERE = 0x60FF8800; // Orange glow
    private static final int COLOR_ACCRETION_INNER = 0xFFFFFFFF; // White hot
    private static final int COLOR_ACCRETION_MID_HOT = 0xFFFFDD88; // Yellow-white
    private static final int COLOR_ACCRETION_MID = 0xFFFF8800; // Orange
    private static final int COLOR_ACCRETION_OUTER = 0xFF880000; // Deep red
    private static final int COLOR_JET_CORE = 0xFFCCDDFF; // Blue-white
    private static final int COLOR_JET_MID = 0xC088AAFF; // Blue
    private static final int COLOR_JET_OUTER = 0x4066AAFF; // Dim blue
    private static final int COLOR_HAWKING_RADIATION = 0x30FFFFFF; // Faint white glow
    private static final int COLOR_GRAVITATIONAL_LENS = 0x20AACCFF; // Subtle blue distortion

    // Physics constants
    private static final float SCHWARZSCHILD_RADIUS_MULTIPLIER = 1.0f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float INNERMOST_STABLE_ORBIT_MULTIPLIER = 3.0f;
    private static final float ACCRETION_DISK_INNER_MULTIPLIER = 3.5f;
    private static final float ACCRETION_DISK_OUTER_MULTIPLIER = 12.0f;
    private static final float JET_LENGTH_MULTIPLIER = 20.0f;
    private static final float JET_WIDTH_MULTIPLIER = 0.5f;

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
            renderBlackHole(effect, partialTick);
        }
    }

    private static void renderBlackHole(BlackHoleEffect effect, float partialTick) {
        Vec3 pos = effect.position;
        float size = effect.size;
        float rotation = effect.getCurrentRotation(partialTick);
        float time = effect.getTime(partialTick);

        // Calculate all radii
        float eventHorizonRadius = size * SCHWARZSCHILD_RADIUS_MULTIPLIER;
        float photonSphereRadius = size * PHOTON_SPHERE_MULTIPLIER;
        float innermostOrbitRadius = size * INNERMOST_STABLE_ORBIT_MULTIPLIER;
        float accretionInnerRadius = size * ACCRETION_DISK_INNER_MULTIPLIER;
        float accretionOuterRadius = size * ACCRETION_DISK_OUTER_MULTIPLIER;
        float jetLength = size * JET_LENGTH_MULTIPLIER;

        // Create rotating transform for disk
        Quaternionf diskRotation = new Quaternionf()
                .rotateY(rotation)
                .rotateX((float)Math.sin(time * 0.1f) * 0.1f); // Slight wobble for precession

        VectorRenderer.Transform diskTransform = VectorRenderer.Transform.fromQuaternion(
                Vec3.ZERO, diskRotation, 1.0f, Vec3.ZERO
        );

        // Render order (back to front for proper transparency)
        renderHawkingRadiation(pos, eventHorizonRadius, time);
        renderGravitationalLensing(pos, photonSphereRadius, accretionOuterRadius, time);
        renderRelativisticJets(pos, size, jetLength, rotation, time, diskTransform);
        renderAccretionDisk(pos, innermostOrbitRadius, accretionInnerRadius, accretionOuterRadius, rotation, time, diskTransform);
        renderPhotonSphere(pos, photonSphereRadius, time);
        renderEventHorizon(pos, eventHorizonRadius);
        renderInnerGlow(pos, eventHorizonRadius, photonSphereRadius, time);
    }

    private static void renderEventHorizon(Vec3 center, float radius) {
        // Create perfect black sphere with subtle edge distortion
        List<Vec3> spherePoints = createSphere(center, radius, EVENT_HORIZON_SEGMENTS);

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

    private static void renderPhotonSphere(Vec3 center, float radius, float time) {
        // Create glowing photon sphere with orbiting light paths
        VectorRenderer.Wireframe photonSphere = VectorRenderer.createWireframe();

        // Horizontal rings with time-based rotation
        for (int ring = 0; ring < 12; ring++) {
            float y = (ring - 5.5f) * radius * 0.18f;
            float ringRadius = (float) Math.sqrt(Math.max(0.0f, radius * radius - y * y));

            for (int i = 0; i < PHOTON_SPHERE_DETAIL; i++) {
                float angle1 = (float)(i * (float)Math.PI * 2.0f / PHOTON_SPHERE_DETAIL) + time * 0.5f;
                float angle2 = (float)(((i + 1) * (float)Math.PI * 2.0f / PHOTON_SPHERE_DETAIL)) + time * 0.5f;

                // Add wave distortion
                float distortion = (float)Math.sin(angle1 * 4f + time * 2f) * 0.05f;
                float r1 = ringRadius * (1f + distortion);
                float r2 = ringRadius * (1f + distortion);

                Vec3 p1 = new Vec3(
                        Math.cos(angle1) * r1,
                        y,
                        Math.sin(angle1) * r1
                );
                Vec3 p2 = new Vec3(
                        Math.cos(angle2) * r2,
                        y,
                        Math.sin(angle2) * r2
                );

                photonSphere.addLine(p1, p2);
            }
        }

        // Spiraling photon paths
        for (int i = 0; i < 8; i++) {
            float startAngle = i * (float)Math.PI * 2.0f / 8.0f;
            List<Vec3> spiralPath = new ArrayList<>();

            for (int j = 0; j <= 32; j++) {
                float t = j / 32.0f;
                float angle = startAngle + t * (float)Math.PI * 4f + time;
                float height = radius * (1f - 2f * t);
                float r = (float) Math.sqrt(Math.max(0.0f, radius * radius - height * height));

                Vec3 point = new Vec3(
                        Math.cos(angle) * r,
                        height,
                        Math.sin(angle) * r
                );
                spiralPath.add(point);
            }

            for (int j = 0; j < spiralPath.size() - 1; j++) {
                photonSphere.addLine(spiralPath.get(j), spiralPath.get(j + 1));
            }
        }

        VectorRenderer.drawWireframeWorld(
                photonSphere, center, COLOR_PHOTON_SPHERE, 0.015f, false, true, 1, VectorRenderer.Transform.IDENTITY
        );
    }

    private static void renderAccretionDisk(Vec3 center, float innermostOrbit, float innerRadius,
                                            float outerRadius, float rotation, float time, VectorRenderer.Transform transform) {
        // Multi-layered accretion disk with realistic temperature gradient
        for (int ring = 0; ring < ACCRETION_DISK_RINGS; ring++) {
            float ringProgress = ring / (float) (ACCRETION_DISK_RINGS - 1);
            float radius = innerRadius + ringProgress * (outerRadius - innerRadius);

            // Skip the innermost unstable region
            if (radius < innermostOrbit) continue;

            // Temperature-based color with multiple gradients
            int color;
            if (ringProgress < 0.2f) {
                color = interpolateColor(COLOR_ACCRETION_INNER, COLOR_ACCRETION_MID_HOT, ringProgress * 5f);
            } else if (ringProgress < 0.5f) {
                color = interpolateColor(COLOR_ACCRETION_MID_HOT, COLOR_ACCRETION_MID, (ringProgress - 0.2f) * 3.33f);
            } else {
                color = interpolateColor(COLOR_ACCRETION_MID, COLOR_ACCRETION_OUTER, (ringProgress - 0.5f) * 2f);
            }

            // Brightness falloff
            float brightness = (float)Math.pow(1.0f - ringProgress, 1.5f);
            color = adjustColorBrightness(color, brightness);

            // Create spiral arms with density waves
            List<Vec3> ringPoints = new ArrayList<>();
            for (int i = 0; i <= ACCRETION_DISK_SEGMENTS; i++) {
                float angle = i * (float)Math.PI * 2.0f / ACCRETION_DISK_SEGMENTS;

                // Multiple spiral arms
                float spiral1 = (float)Math.sin(angle * 2f - rotation * 3f - radius * 0.3f) * 0.15f;
                float spiral2 = (float)Math.sin(angle * 3f - rotation * 5f + radius * 0.2f) * 0.1f;
                float spiral3 = (float)Math.sin(angle * 5f - rotation * 7f - radius * 0.1f) * 0.05f;
                float spiralOffset = (spiral1 + spiral2 + spiral3) * ringProgress;

                float adjustedRadius = radius * (1f + spiralOffset);

                // Vertical turbulence increases with radius
                float turbulence = ((float)Math.sin(angle * 7f + time * 3f) * 0.02f +
                        (float)Math.sin(angle * 13f - time * 5f) * 0.01f) * radius * ringProgress;

                // Doppler shift simulation (approaching vs receding)
                float dopplerFactor = (float)Math.sin(angle + rotation) * 0.3f + 1.0f;

                Vec3 point = new Vec3(
                        Math.cos(angle) * adjustedRadius,
                        turbulence,
                        Math.sin(angle) * adjustedRadius
                );
                ringPoints.add(point);
            }

            // Main ring
            float thickness = (outerRadius - innerRadius) / ACCRETION_DISK_RINGS * 0.7f;
            VectorRenderer.drawPolylineWorld(
                    ringPoints, color, thickness, false, 1, transform
            );

            // Inner glow layers
            if (ring < ACCRETION_DISK_RINGS / 2) {
                // First glow layer
                int glowColor1 = adjustColorAlpha(color, 0.4f);
                VectorRenderer.drawPolylineWorld(
                        ringPoints, glowColor1, thickness * 1.5f, false, 1, transform
                );

                // Second glow layer
                int glowColor2 = adjustColorAlpha(color, 0.2f);
                VectorRenderer.drawPolylineWorld(
                        ringPoints, glowColor2, thickness * 2.5f, false, 1, transform
                );

                // Outer bloom for innermost rings
                if (ring < 3) {
                    int bloomColor = adjustColorAlpha(COLOR_ACCRETION_INNER, 0.1f);
                    VectorRenderer.drawPolylineWorld(
                            ringPoints, bloomColor, thickness * 4.0f, false, 1, transform
                    );
                }
            }
        }

        // Add hot spots and flares
        renderDiskHotSpots(center, innerRadius, outerRadius, rotation, time, transform);

        // Add magnetic field lines
        renderMagneticFieldLines(center, innerRadius, outerRadius, rotation, time, transform);
    }

    private static void renderDiskHotSpots(Vec3 center, float innerRadius, float outerRadius,
                                           float rotation, float time, VectorRenderer.Transform transform) {
        // Create bright hot spots in the disk
        for (int i = 0; i < 8; i++) {
            float angle = i * (float)Math.PI * 2.0f / 8.0f + rotation * 2f + time * 0.3f;
            float radius = innerRadius + RAND.nextFloat() * (outerRadius - innerRadius) * 0.5f;

            // Pulsing brightness (use float math only)
            float pulse = (float)Math.sin(time * 5f + i * 2f) * 0.5f + 0.5f;

            List<Vec3> flarePoints = new ArrayList<>();
            for (int j = 0; j < 16; j++) {
                float flareAngle = j * (float)Math.PI * 2.0f / 16.0f;
                float flareRadius = radius + pulse * radius * 0.1f;

                Vec3 point = new Vec3(
                        Math.cos(angle + flareAngle * 0.1f) * flareRadius,
                        0,
                        Math.sin(angle + flareAngle * 0.1f) * flareRadius
                );
                flarePoints.add(point);
            }

            int flareColor = interpolateColor(COLOR_ACCRETION_INNER, COLOR_ACCRETION_MID_HOT, pulse);
            flareColor = adjustColorAlpha(flareColor, pulse * 0.7f);

            VectorRenderer.drawPolylineWorld(
                    flarePoints, flareColor, 0.05f, false, 1, transform
            );
        }
    }

    private static void renderMagneticFieldLines(Vec3 center, float innerRadius, float outerRadius,
                                                 float rotation, float time, VectorRenderer.Transform transform) {
        // Twisted magnetic field lines
        for (int i = 0; i < 6; i++) {
            float baseAngle = i * (float)Math.PI * 2.0f / 6.0f + rotation;
            List<Vec3> fieldLine = new ArrayList<>();

            for (int j = 0; j < 20; j++) {
                float t = j / 19.0f;
                float radius = innerRadius + t * (outerRadius - innerRadius);
                float twist = t * (float)Math.PI * 2.0f + time * 0.5f;
                float height = (float)Math.sin(t * (float)Math.PI) * radius * 0.3f;

                float angle = baseAngle + twist * 0.5f;

                Vec3 point = new Vec3(
                        Math.cos(angle) * radius,
                        height,
                        Math.sin(angle) * radius
                );
                fieldLine.add(point);
            }

            int fieldColor = adjustColorAlpha(0xFF4488FF, 0.2f);
            VectorRenderer.drawPolylineWorld(
                    fieldLine, fieldColor, 0.02f, false, 1, transform
            );
        }
    }

    private static void renderRelativisticJets(Vec3 center, float blackHoleSize, float jetLength,
                                               float rotation, float time, VectorRenderer.Transform transform) {
        float jetRadius = blackHoleSize * JET_WIDTH_MULTIPLIER;

        for (int pole = 0; pole < 2; pole++) {
            float direction = pole == 0 ? 1.0f : -1.0f;

            // Multi-layered jet structure
            for (int layer = 0; layer < 3; layer++) {
                float layerRadius = jetRadius * (1f + layer * 0.5f);
                float layerAlpha = 1.0f / (layer + 1);

                // Jet core with helical structure
                List<Vec3> jetCore = new ArrayList<>();
                for (int i = 0; i <= 40; i++) {
                    float t = i / 40.0f;
                    float y = direction * t * jetLength;

                    // Jet expands then collimates
                    float expansionFactor;
                    if (t < 0.1f) {
                        expansionFactor = 1.0f + t * 5.0f; // Quick expansion
                    } else if (t < 0.3f) {
                        expansionFactor = 1.5f - (t - 0.1f) * 2.0f; // Collimation
                    } else {
                        expansionFactor = 1.1f - t * 0.1f; // Slow taper
                    }

                    float radius = layerRadius * expansionFactor;

                    // Helical motion with increasing pitch
                    float helixAngle = t * (float)Math.PI * 8.0f + time * 3.0f + layer * (float)Math.PI / 3.0f;
                    float helixRadius = radius * 0.2f * (1f - t);

                    Vec3 point = new Vec3(
                            Math.cos(helixAngle) * helixRadius,
                            y,
                            Math.sin(helixAngle) * helixRadius
                    );
                    jetCore.add(point);
                }

                int jetColor;
                if (layer == 0) {
                    jetColor = COLOR_JET_CORE;
                } else if (layer == 1) {
                    jetColor = adjustColorAlpha(COLOR_JET_MID, 0.5f);
                } else {
                    jetColor = adjustColorAlpha(COLOR_JET_OUTER, 0.3f);
                }

                VectorRenderer.drawPolylineWorld(
                        jetCore, jetColor, layerRadius * 0.3f, false, 1, transform
                );
            }

            // Shock fronts / knots in the jet
            for (int knot = 0; knot < 5; knot++) {
                float knotPosition = 0.2f + knot * 0.15f;
                float knotY = direction * knotPosition * jetLength;
                float knotRadius = jetRadius * 2f * (1f - knotPosition * 0.3f);

                // Pulsing knots
                float knotPulse = (float)Math.sin(time * 4f + knot * 2f) * 0.3f + 0.7f;

                List<Vec3> knotRing = new ArrayList<>();
                for (int i = 0; i <= 32; i++) {
                    float angle = i * (float)Math.PI * 2.0f / 32.0f;
                    Vec3 point = new Vec3(
                            Math.cos(angle) * knotRadius * knotPulse,
                            knotY,
                            Math.sin(angle) * knotRadius * knotPulse
                    );
                    knotRing.add(point);
                }

                int knotColor = adjustColorAlpha(COLOR_JET_CORE, 0.3f * knotPulse);
                VectorRenderer.drawPolylineWorld(
                        knotRing, knotColor, knotRadius * 0.15f, false, 1, transform
                );
            }
        }
    }

    private static void renderGravitationalLensing(Vec3 center, float photonRadius, float maxRadius, float time) {
        // Multiple layers of gravitational distortion
        for (int layer = 0; layer < GRAVITATIONAL_LENS_RINGS; layer++) {
            float layerRadius = photonRadius * (1.5f + layer * 0.5f);
            if (layerRadius > maxRadius * 1.5f) break;

            float distortionStrength = 1.0f / (layer + 1);
            int ringColor = adjustColorAlpha(COLOR_GRAVITATIONAL_LENS, 0.15f * distortionStrength);

            VectorRenderer.Wireframe lensRing = VectorRenderer.createWireframe();

            // Create distorted ring with Einstein ring effect
            for (int i = 0; i < 96; i++) {
                float angle1 = i * (float)Math.PI * 2.0f / 96.0f;
                float angle2 = (i + 1) * (float)Math.PI * 2.0f / 96.0f;

                // Complex distortion pattern
                float distortion1 = 1.0f + ((float)Math.sin(angle1 * 3f + time) * 0.1f +
                        (float)Math.sin(angle1 * 7f - time * 2f) * 0.05f +
                        (float)Math.sin(angle1 * 11f + time * 3f) * 0.02f) * distortionStrength;

                float distortion2 = 1.0f + ((float)Math.sin(angle2 * 3f + time) * 0.1f +
                        (float)Math.sin(angle2 * 7f - time * 2f) * 0.05f +
                        (float)Math.sin(angle2 * 11f + time * 3f) * 0.02f) * distortionStrength;

                // Vertical displacement for 3D effect
                float height1 = (float)Math.sin(angle1 * 4f + time * 2f) * layerRadius * 0.05f * distortionStrength;
                float height2 = (float)Math.sin(angle2 * 4f + time * 2f) * layerRadius * 0.05f * distortionStrength;

                Vec3 p1 = new Vec3(
                        Math.cos(angle1) * layerRadius * distortion1,
                        height1,
                        Math.sin(angle1) * layerRadius * distortion1
                );
                Vec3 p2 = new Vec3(
                        Math.cos(angle2) * layerRadius * distortion2,
                        height2,
                        Math.sin(angle2) * layerRadius * distortion2
                );

                lensRing.addLine(p1, p2);
            }

            // Add radial distortion lines
            for (int i = 0; i < 16; i++) {
                float angle = i * (float)Math.PI * 2.0f / 16.0f + time * 0.2f;
                Vec3 inner = new Vec3(
                        Math.cos(angle) * layerRadius * 0.9f,
                        0,
                        Math.sin(angle) * layerRadius * 0.9f
                );
                Vec3 outer = new Vec3(
                        Math.cos(angle) * layerRadius * 1.1f,
                        0,
                        Math.sin(angle) * layerRadius * 1.1f
                );
                lensRing.addLine(inner, outer);
            }

            VectorRenderer.drawWireframeWorld(
                    lensRing, center, ringColor, 0.008f, false, true, 1, VectorRenderer.Transform.IDENTITY
            );
        }
    }

    private static void renderHawkingRadiation(Vec3 center, float eventHorizonRadius, float time) {
        // Faint particle emission from the event horizon
        VectorRenderer.Wireframe radiation = VectorRenderer.createWireframe();

        for (int i = 0; i < 24; i++) {
            float angle = i * (float)Math.PI * 2.0f / 24.0f;
            float particleTime = (time * 2f + i * 0.3f) % 3.0f;

            if (particleTime < 2.0f) {
                float t = particleTime / 2.0f;
                float startRadius = eventHorizonRadius * 1.05f;
                float currentRadius = startRadius + t * eventHorizonRadius * 0.5f;

                // Particle path with slight curve
                float curve = (float)Math.sin(t * (float)Math.PI) * 0.2f;
                float adjustedAngle = angle + curve;

                Vec3 p1 = new Vec3(
                        Math.cos(adjustedAngle) * currentRadius,
                        t * eventHorizonRadius * 0.3f,
                        Math.sin(adjustedAngle) * currentRadius
                );

                float nextRadius = currentRadius + eventHorizonRadius * 0.05f;
                Vec3 p2 = new Vec3(
                        Math.cos(adjustedAngle) * nextRadius,
                        (t + 0.05f) * eventHorizonRadius * 0.3f,
                        Math.sin(adjustedAngle) * nextRadius
                );

                radiation.addLine(p1, p2);
            }
        }

        VectorRenderer.drawWireframeWorld(
                radiation, center, COLOR_HAWKING_RADIATION, 0.005f, false, true, 1, VectorRenderer.Transform.IDENTITY
        );
    }

    private static void renderInnerGlow(Vec3 center, float eventHorizonRadius, float photonSphereRadius, float time) {
        // Glowing aura around the event horizon
        for (int shell = 0; shell < 3; shell++) {
            float shellRadius = eventHorizonRadius + (photonSphereRadius - eventHorizonRadius) * shell * 0.3f;
            float alpha = 0.2f / (shell + 1);

            List<Vec3> glowRing = new ArrayList<>();
            for (int i = 0; i <= 64; i++) {
                float angle = i * (float)Math.PI * 2.0f / 64.0f;

                // Pulsing glow
                float pulse = (float)Math.sin(time * 2f + shell) * 0.1f + 1.0f;

                Vec3 point = new Vec3(
                        Math.cos(angle) * shellRadius * pulse,
                        0,
                        Math.sin(angle) * shellRadius * pulse
                );
                glowRing.add(point);
            }

            int glowColor = adjustColorAlpha(COLOR_PHOTON_SPHERE, alpha);
            VectorRenderer.drawPolylineWorld(
                    glowRing, glowColor, shellRadius * 0.2f, false, 1, VectorRenderer.Transform.IDENTITY
            );
        }
    }

    // Utility methods
    private static List<Vec3> createSphere(Vec3 center, float radius, int segments) {
        List<Vec3> points = new ArrayList<>();

        for (int i = 0; i <= segments; i++) {
            double lat = Math.PI * (-0.5 + (double) i / (double) segments);
            float y = (float) Math.sin(lat);
            float xz = (float) Math.cos(lat);

            for (int j = 0; j <= segments; j++) {
                double lon = 2.0 * Math.PI * (double) j / (double) segments;
                float x = xz * (float) Math.cos(lon);
                float z = xz * (float) Math.sin(lon);

                points.add(center.add(new Vec3(x * radius, y * radius, z * radius)));
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
            if (rotation > 2f * (float)Math.PI) rotation -= 2f * (float)Math.PI;
        }

        boolean isExpired() {
            return lifetime >= 0 && age >= lifetime;
        }

        float getCurrentRotation(float partialTick) {
            return rotation + rotationSpeed * partialTick;
        }

        float getTime(float partialTick) {
            return (age + partialTick) * 0.05f; // Convert to seconds-like time
        }
    }
}
