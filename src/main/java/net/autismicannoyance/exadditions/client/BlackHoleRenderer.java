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
 * Ultra-realistic black hole renderer with enhanced effects, improved performance,
 * and additional visual features for maximum realism.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class BlackHoleRenderer {
    private static final Map<Integer, BlackHoleEffect> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // Enhanced visual constants for better quality
    private static final int EVENT_HORIZON_SEGMENTS = 96;
    private static final int ACCRETION_DISK_RINGS = 24;
    private static final int ACCRETION_DISK_SEGMENTS = 192;
    private static final int GRAVITATIONAL_LENS_RINGS = 12;
    private static final int PHOTON_SPHERE_DETAIL = 64;
    private static final int MAGNETIC_FIELD_LINES = 12;
    private static final int JET_DETAIL_SEGMENTS = 64;

    // Enhanced color palette with HDR-like values
    private static final int COLOR_EVENT_HORIZON = 0xFF000000;
    private static final int COLOR_PHOTON_SPHERE_CORE = 0xFFFFAA00; // Bright orange core
    private static final int COLOR_PHOTON_SPHERE_GLOW = 0x80FF6600; // Orange glow
    private static final int COLOR_ACCRETION_INNER = 0xFFFFFFFF; // White hot (>10,000K)
    private static final int COLOR_ACCRETION_HOT = 0xFFFFEEAA; // Blue-white hot (~8,000K)
    private static final int COLOR_ACCRETION_WARM = 0xFFFFCC66; // Yellow-white (~6,000K)
    private static final int COLOR_ACCRETION_MID = 0xFFFF8800; // Orange (~4,000K)
    private static final int COLOR_ACCRETION_COOL = 0xFFCC4400; // Red-orange (~3,000K)
    private static final int COLOR_ACCRETION_OUTER = 0xFF880000; // Deep red (~2,000K)
    private static final int COLOR_JET_CORE = 0xFFDDEEFF; // Blue-white synchrotron
    private static final int COLOR_JET_MID = 0xC099CCFF; // Blue synchrotron
    private static final int COLOR_JET_OUTER = 0x6066AAFF; // Faint blue
    private static final int COLOR_HAWKING_RADIATION = 0x20FFFFFF; // Quantum foam
    private static final int COLOR_GRAVITATIONAL_LENS = 0x30AACCFF; // Spacetime distortion
    private static final int COLOR_MAGNETIC_FIELD = 0x40FF4488; // Magnetic field lines

    // Enhanced physics constants
    private static final float SCHWARZSCHILD_RADIUS_MULTIPLIER = 1.0f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float INNERMOST_STABLE_ORBIT_MULTIPLIER = 3.0f;
    private static final float ACCRETION_DISK_INNER_MULTIPLIER = 3.2f;
    private static final float ACCRETION_DISK_OUTER_MULTIPLIER = 15.0f;
    private static final float JET_LENGTH_MULTIPLIER = 25.0f;
    private static final float JET_WIDTH_MULTIPLIER = 0.4f;

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

        // Calculate all physical radii
        float eventHorizonRadius = size * SCHWARZSCHILD_RADIUS_MULTIPLIER;
        float photonSphereRadius = size * PHOTON_SPHERE_MULTIPLIER;
        float innermostOrbitRadius = size * INNERMOST_STABLE_ORBIT_MULTIPLIER;
        float accretionInnerRadius = size * ACCRETION_DISK_INNER_MULTIPLIER;
        float accretionOuterRadius = size * ACCRETION_DISK_OUTER_MULTIPLIER;
        float jetLength = size * JET_LENGTH_MULTIPLIER;

        // Enhanced precession and orbital mechanics
        Quaternionf diskRotation = new Quaternionf()
                .rotateY(rotation)
                .rotateX((float)Math.sin(time * 0.08f) * 0.15f) // Frame dragging precession
                .rotateZ((float)Math.cos(time * 0.12f) * 0.05f); // Lense-Thirring effect

        VectorRenderer.Transform diskTransform = VectorRenderer.Transform.fromQuaternion(
                Vec3.ZERO, diskRotation, 1.0f, Vec3.ZERO
        );

        // Render in proper order for transparency and depth
        renderHawkingRadiation(pos, eventHorizonRadius, time);
        renderGravitationalLensing(pos, photonSphereRadius, accretionOuterRadius, time);
        renderMagneticFieldLines(pos, accretionInnerRadius, accretionOuterRadius, rotation, time, diskTransform);
        renderRelativisticJets(pos, size, jetLength, rotation, time, diskTransform);
        renderAccretionDisk(pos, innermostOrbitRadius, accretionInnerRadius, accretionOuterRadius, rotation, time, diskTransform);
        renderPhotonSphere(pos, photonSphereRadius, time);
        renderEventHorizon(pos, eventHorizonRadius);
        renderInnerGlow(pos, eventHorizonRadius, photonSphereRadius, time);
        renderQuantumEffects(pos, eventHorizonRadius, time);
    }

    private static void renderEventHorizon(Vec3 center, float radius) {
        // Create perfect black sphere with subtle quantum fluctuations
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
        // Enhanced photon sphere with multiple orbital families
        VectorRenderer.Wireframe photonSphere = VectorRenderer.createWireframe();

        // Circular orbits at different inclinations
        for (int orbit = 0; orbit < 16; orbit++) {
            float inclination = orbit * (float)Math.PI / 8.0f;
            float phase = orbit * 0.4f + time * 0.8f;

            for (int i = 0; i < PHOTON_SPHERE_DETAIL; i++) {
                float angle1 = (float)(i * Math.PI * 2.0 / PHOTON_SPHERE_DETAIL) + phase;
                float angle2 = (float)((i + 1) * Math.PI * 2.0 / PHOTON_SPHERE_DETAIL) + phase;

                // Add relativistic precession
                float precession = (float)Math.sin(angle1 * 3f + time * 1.5f) * 0.08f;
                float r1 = radius * (1f + precession);
                float r2 = radius * (1f + precession);

                Vec3 p1 = new Vec3(
                        Math.cos(angle1) * r1 * Math.cos(inclination),
                        Math.sin(inclination) * r1,
                        Math.sin(angle1) * r1 * Math.cos(inclination)
                );
                Vec3 p2 = new Vec3(
                        Math.cos(angle2) * r2 * Math.cos(inclination),
                        Math.sin(inclination) * r2,
                        Math.sin(angle2) * r2 * Math.cos(inclination)
                );

                photonSphere.addLine(p1, p2);
            }
        }

        // Add unstable spiraling photons
        for (int spiral = 0; spiral < 6; spiral++) {
            float startAngle = spiral * (float)Math.PI * 2.0f / 6.0f;
            List<Vec3> spiralPath = new ArrayList<>();

            for (int j = 0; j <= 48; j++) {
                float t = j / 48.0f;
                float angle = startAngle + t * (float)Math.PI * 6f + time * 2f;
                float spiralRadius = radius * (1f - t * 0.3f + (float)Math.sin(t * Math.PI * 4f) * 0.1f);
                float height = radius * (float)Math.sin(t * Math.PI) * 0.4f;

                Vec3 point = new Vec3(
                        Math.cos(angle) * spiralRadius,
                        height,
                        Math.sin(angle) * spiralRadius
                );
                spiralPath.add(point);
            }

            for (int j = 0; j < spiralPath.size() - 1; j++) {
                photonSphere.addLine(spiralPath.get(j), spiralPath.get(j + 1));
            }
        }

        VectorRenderer.drawWireframeWorld(
                photonSphere, center, COLOR_PHOTON_SPHERE_CORE, 0.02f, false, true, 1, VectorRenderer.Transform.IDENTITY
        );

        // Add glowing layer
        VectorRenderer.drawWireframeWorld(
                photonSphere, center, COLOR_PHOTON_SPHERE_GLOW, 0.04f, false, true, 1, VectorRenderer.Transform.IDENTITY
        );
    }

    private static void renderAccretionDisk(Vec3 center, float innermostOrbit, float innerRadius,
                                            float outerRadius, float rotation, float time, VectorRenderer.Transform transform) {
        // Enhanced multi-temperature accretion disk
        for (int ring = 0; ring < ACCRETION_DISK_RINGS; ring++) {
            float ringProgress = ring / (float) (ACCRETION_DISK_RINGS - 1);
            float radius = innerRadius + ringProgress * (outerRadius - innerRadius);

            if (radius < innermostOrbit) continue;

            // Physically accurate temperature distribution (T ∝ r^-3/4)
            float temperature = (float)Math.pow(innerRadius / radius, 0.75f);

            // Enhanced color mapping based on blackbody radiation
            int color;
            if (temperature > 0.9f) {
                color = interpolateColor(COLOR_ACCRETION_INNER, COLOR_ACCRETION_HOT, (temperature - 0.9f) * 10f);
            } else if (temperature > 0.7f) {
                color = interpolateColor(COLOR_ACCRETION_HOT, COLOR_ACCRETION_WARM, (temperature - 0.7f) * 5f);
            } else if (temperature > 0.5f) {
                color = interpolateColor(COLOR_ACCRETION_WARM, COLOR_ACCRETION_MID, (temperature - 0.5f) * 5f);
            } else if (temperature > 0.3f) {
                color = interpolateColor(COLOR_ACCRETION_MID, COLOR_ACCRETION_COOL, (temperature - 0.3f) * 5f);
            } else {
                color = interpolateColor(COLOR_ACCRETION_COOL, COLOR_ACCRETION_OUTER, temperature * 3.33f);
            }

            // Brightness with distance falloff
            float brightness = temperature * (1f + (float)Math.sin(time * 2f + ring * 0.3f) * 0.1f);
            color = adjustColorBrightness(color, brightness);

            // Enhanced spiral structure with multiple density waves
            List<Vec3> ringPoints = new ArrayList<>();
            for (int i = 0; i <= ACCRETION_DISK_SEGMENTS; i++) {
                float angle = i * (float)Math.PI * 2.0f / ACCRETION_DISK_SEGMENTS;

                // Multiple spiral arms with different pitch angles
                float spiral1 = (float)Math.sin(angle * 2f - rotation * 2f - radius * 0.4f) * 0.12f;
                float spiral2 = (float)Math.sin(angle * 3f - rotation * 3f + radius * 0.3f) * 0.08f;
                float spiral3 = (float)Math.sin(angle * 5f - rotation * 5f - radius * 0.2f) * 0.04f;
                float magnetoRotational = (float)Math.sin(angle * 7f - rotation * 7f) * 0.02f;

                float totalSpiral = (spiral1 + spiral2 + spiral3 + magnetoRotational) * ringProgress;
                float adjustedRadius = radius * (1f + totalSpiral);

                // Enhanced turbulence with Reynolds stress
                float turbulence = ((float)Math.sin(angle * 11f + time * 4f) * 0.015f +
                        (float)Math.sin(angle * 17f - time * 6f) * 0.008f +
                        (float)Math.sin(angle * 23f + time * 8f) * 0.004f) *
                        radius * ringProgress * temperature;

                // Vertical structure from hydrostatic equilibrium
                float scaleHeight = radius * 0.05f * (float)Math.sqrt(temperature);
                float verticalOffset = turbulence * scaleHeight;

                Vec3 point = new Vec3(
                        Math.cos(angle) * adjustedRadius,
                        verticalOffset,
                        Math.sin(angle) * adjustedRadius
                );
                ringPoints.add(point);
            }

            float thickness = (outerRadius - innerRadius) / ACCRETION_DISK_RINGS * 0.6f;

            // Main ring
            VectorRenderer.drawPolylineWorld(
                    ringPoints, color, thickness, false, 1, transform
            );

            // Multiple glow layers for HDR effect
            if (temperature > 0.5f) {
                int glow1 = adjustColorAlpha(color, 0.3f);
                VectorRenderer.drawPolylineWorld(
                        ringPoints, glow1, thickness * 1.8f, false, 1, transform
                );

                if (temperature > 0.8f) {
                    int glow2 = adjustColorAlpha(COLOR_ACCRETION_INNER, 0.15f);
                    VectorRenderer.drawPolylineWorld(
                            ringPoints, glow2, thickness * 3.0f, false, 1, transform
                    );
                }
            }
        }

        // Add dynamic hot spots and flares
        renderAccretionFlares(center, innerRadius, outerRadius, rotation, time, transform);
        renderShockWaves(center, innerRadius, outerRadius, rotation, time, transform);
    }

    private static void renderAccretionFlares(Vec3 center, float innerRadius, float outerRadius,
                                              float rotation, float time, VectorRenderer.Transform transform) {
        // Dynamic hot spots from magnetic reconnection events
        for (int flare = 0; flare < 12; flare++) {
            float flarePhase = (time * 3f + flare * 0.8f) % 6.0f;
            if (flarePhase > 2.0f) continue; // Flare duration

            float angle = flare * (float)Math.PI * 2.0f / 12.0f + rotation * 1.5f;
            float radius = innerRadius + RAND.nextFloat() * (outerRadius - innerRadius) * 0.4f;

            float intensity = 1f - flarePhase / 2f; // Fade over time
            float size = intensity * radius * 0.15f;

            List<Vec3> flareRing = new ArrayList<>();
            for (int i = 0; i <= 24; i++) {
                float flareAngle = i * (float)Math.PI * 2.0f / 24.0f;
                float flareRadius = radius + (float)Math.sin(flareAngle * 3f) * size * 0.3f;

                Vec3 point = new Vec3(
                        Math.cos(angle + flareAngle * 0.1f) * flareRadius,
                        (float)Math.sin(flareAngle * 2f) * size * 0.1f,
                        Math.sin(angle + flareAngle * 0.1f) * flareRadius
                );
                flareRing.add(point);
            }

            int flareColor = adjustColorAlpha(COLOR_ACCRETION_INNER, intensity * 0.8f);
            VectorRenderer.drawPolylineWorld(
                    flareRing, flareColor, size * 0.4f, false, 1, transform
            );
        }
    }

    private static void renderShockWaves(Vec3 center, float innerRadius, float outerRadius,
                                         float rotation, float time, VectorRenderer.Transform transform) {
        // Propagating shock waves from instabilities
        for (int wave = 0; wave < 4; wave++) {
            float waveTime = (time * 1.5f + wave * 1.5f) % 8.0f;
            float waveRadius = innerRadius + waveTime * (outerRadius - innerRadius) / 8.0f;

            if (waveRadius > outerRadius) continue;

            float waveStrength = (float)Math.exp(-waveTime * 0.5f); // Exponential decay

            List<Vec3> shockRing = new ArrayList<>();
            for (int i = 0; i <= 64; i++) {
                float angle = i * (float)Math.PI * 2.0f / 64.0f + rotation;
                float modulation = 1f + (float)Math.sin(angle * 4f + wave * (float)Math.PI / 2f) * 0.1f;

                Vec3 point = new Vec3(
                        Math.cos(angle) * waveRadius * modulation,
                        0,
                        Math.sin(angle) * waveRadius * modulation
                );
                shockRing.add(point);
            }

            int shockColor = adjustColorAlpha(COLOR_ACCRETION_HOT, waveStrength * 0.4f);
            VectorRenderer.drawPolylineWorld(
                    shockRing, shockColor, waveRadius * 0.02f, false, 1, transform
            );
        }
    }

    private static void renderMagneticFieldLines(Vec3 center, float innerRadius, float outerRadius,
                                                 float rotation, float time, VectorRenderer.Transform transform) {
        // Enhanced poloidal and toroidal magnetic field structure
        for (int field = 0; field < MAGNETIC_FIELD_LINES; field++) {
            float fieldAngle = field * (float)Math.PI * 2.0f / MAGNETIC_FIELD_LINES + rotation * 0.3f;

            // Poloidal field lines (vertical loops)
            List<Vec3> poloidalField = new ArrayList<>();
            for (int i = 0; i <= 32; i++) {
                float t = i / 32.0f;
                float radius = innerRadius + t * (outerRadius - innerRadius) * 0.6f;
                float height = (float)Math.sin(t * Math.PI) * radius * 0.8f;

                // Magnetic field twist from disk rotation
                float twist = t * (float)Math.PI * 0.5f + time * 0.8f;
                float x = (float)Math.cos(fieldAngle + twist * 0.3f) * radius;
                float z = (float)Math.sin(fieldAngle + twist * 0.3f) * radius;

                poloidalField.add(new Vec3(x, height, z));
            }

            // Toroidal field lines (horizontal loops)
            List<Vec3> toroidalField = new ArrayList<>();
            float toroidalRadius = innerRadius + field * (outerRadius - innerRadius) / MAGNETIC_FIELD_LINES;
            for (int i = 0; i <= 48; i++) {
                float angle = i * (float)Math.PI * 2.0f / 48.0f + time * 0.5f;
                float height = toroidalRadius * 0.1f * (float)Math.sin(angle * 3f + time);

                Vec3 point = new Vec3(
                        Math.cos(angle) * toroidalRadius,
                        height,
                        Math.sin(angle) * toroidalRadius
                );
                toroidalField.add(point);
            }

            float fieldStrength = 1f / (field + 1);
            int fieldColor = adjustColorAlpha(COLOR_MAGNETIC_FIELD, fieldStrength * 0.3f);

            VectorRenderer.drawPolylineWorld(
                    poloidalField, fieldColor, 0.015f, false, 1, transform
            );
            VectorRenderer.drawPolylineWorld(
                    toroidalField, fieldColor, 0.01f, false, 1, transform
            );
        }
    }

    private static void renderRelativisticJets(Vec3 center, float blackHoleSize, float jetLength,
                                               float rotation, float time, VectorRenderer.Transform transform) {
        float jetRadius = blackHoleSize * JET_WIDTH_MULTIPLIER;

        for (int pole = 0; pole < 2; pole++) {
            float direction = pole == 0 ? 1.0f : -1.0f;

            // Multi-component jet structure (core, cocoon, bow shock)
            for (int component = 0; component < 4; component++) {
                float componentRadius = jetRadius * (0.3f + component * 0.4f);
                float componentSpeed = 1f - component * 0.2f; // Inner components move faster

                // Helical jet core with precession
                List<Vec3> jetPath = new ArrayList<>();
                for (int i = 0; i <= JET_DETAIL_SEGMENTS; i++) {
                    float t = i / (float)JET_DETAIL_SEGMENTS;
                    float y = direction * t * jetLength;

                    // Jet opening angle with distance
                    float opening = (float)Math.tanh(t * 3f) * 0.3f;
                    float radius = componentRadius * (1f + opening);

                    // Helical instabilities
                    float helixPhase = t * (float)Math.PI * 6f * componentSpeed + time * 4f + component * (float)Math.PI / 2f;
                    float precessionPhase = t * (float)Math.PI * 0.5f + time * 0.3f;

                    float helixRadius = radius * 0.4f * (1f - t * 0.5f);
                    float x = helixRadius * (float)Math.cos(helixPhase) * (float)Math.cos(precessionPhase);
                    float z = helixRadius * (float)Math.sin(helixPhase) * (float)Math.cos(precessionPhase);

                    // Add Kelvin-Helmholtz instabilities
                    float khInstability = (float)Math.sin(t * (float)Math.PI * 8f + time * 3f) * radius * 0.05f;
                    x += khInstability * (float)Math.cos(precessionPhase + (float)Math.PI / 2f);
                    z += khInstability * (float)Math.sin(precessionPhase + (float)Math.PI / 2f);

                    jetPath.add(new Vec3(x, y, z));
                }

                // Component-specific colors and properties
                int jetColor;
                float thickness;
                if (component == 0) { // Core
                    jetColor = COLOR_JET_CORE;
                    thickness = componentRadius * 0.5f;
                } else if (component == 1) { // Sheath
                    jetColor = adjustColorAlpha(COLOR_JET_MID, 0.7f);
                    thickness = componentRadius * 0.4f;
                } else if (component == 2) { // Cocoon
                    jetColor = adjustColorAlpha(COLOR_JET_OUTER, 0.5f);
                    thickness = componentRadius * 0.3f;
                } else { // Bow shock
                    jetColor = adjustColorAlpha(COLOR_JET_OUTER, 0.3f);
                    thickness = componentRadius * 0.2f;
                }

                VectorRenderer.drawPolylineWorld(
                        jetPath, jetColor, thickness, false, 1, transform
                );
            }

            // Working surfaces (internal shocks)
            renderJetShocks(center, direction, jetLength, jetRadius, rotation, time, transform);
        }
    }

    private static void renderJetShocks(Vec3 center, float direction, float jetLength, float jetRadius,
                                        float rotation, float time, VectorRenderer.Transform transform) {
        // Internal shock structures in jets
        for (int shock = 0; shock < 8; shock++) {
            float shockPosition = 0.1f + shock * 0.11f;
            if (shockPosition > 0.9f) continue;

            float shockY = direction * shockPosition * jetLength;
            float shockRadius = jetRadius * (1.5f + shockPosition);

            // Shock brightness varies with time
            float shockPhase = (time * 2f + shock * 0.7f) % 4.0f;
            float brightness = shockPhase < 2f ? (1f - shockPhase / 2f) : 0f;

            if (brightness <= 0) continue;

            List<Vec3> shockRing = new ArrayList<>();
            for (int i = 0; i <= 32; i++) {
                float angle = i * (float)Math.PI * 2.0f / 32.0f;
                float r = shockRadius * (1f + (float)Math.sin(angle * 4f + time * 3f) * 0.2f);

                Vec3 point = new Vec3(
                        Math.cos(angle) * r,
                        shockY,
                        Math.sin(angle) * r
                );
                shockRing.add(point);
            }

            int shockColor = adjustColorAlpha(COLOR_JET_CORE, brightness * 0.6f);
            VectorRenderer.drawPolylineWorld(
                    shockRing, shockColor, shockRadius * 0.1f, false, 1, transform
            );
        }
    }

    private static void renderGravitationalLensing(Vec3 center, float photonRadius, float maxRadius, float time) {
        // Enhanced Einstein ring and caustic effects
        for (int ring = 0; ring < GRAVITATIONAL_LENS_RINGS; ring++) {
            float ringRadius = photonRadius * (1.2f + ring * 0.3f);
            if (ringRadius > maxRadius * 2f) break;

            // Lensing strength decreases with distance
            float lensStrength = (float)Math.pow(photonRadius / ringRadius, 2f);

            VectorRenderer.Wireframe lensRing = VectorRenderer.createWireframe();

            // Create distorted Einstein ring with caustics
            for (int i = 0; i < 128; i++) {
                float angle1 = i * (float)Math.PI * 2.0f / 128.0f;
                float angle2 = (i + 1) * (float)Math.PI * 2.0f / 128.0f;

                // Multiple lensing effects
                float primaryLens = 1f + (float)Math.sin(angle1 * 2f + time * 0.5f) * 0.1f * lensStrength;
                float secondaryLens = 1f + (float)Math.sin(angle1 * 6f - time * 1.2f) * 0.05f * lensStrength;
                float causticEffect = 1f + (float)Math.sin(angle1 * 10f + time * 2f) * 0.02f * lensStrength;

                float distortion1 = primaryLens * secondaryLens * causticEffect;
                float distortion2 = 1f + (float)Math.sin(angle2 * 2f + time * 0.5f) * 0.1f * lensStrength *
                        (1f + (float)Math.sin(angle2 * 6f - time * 1.2f) * 0.05f) *
                        (1f + (float)Math.sin(angle2 * 10f + time * 2f) * 0.02f);

                // Height variation for 3D lensing effect
                float height1 = (float)Math.sin(angle1 * 3f + time * 1.5f) * ringRadius * 0.08f * lensStrength;
                float height2 = (float)Math.sin(angle2 * 3f + time * 1.5f) * ringRadius * 0.08f * lensStrength;

                Vec3 p1 = new Vec3(
                        Math.cos(angle1) * ringRadius * distortion1,
                        height1,
                        Math.sin(angle1) * ringRadius * distortion1
                );
                Vec3 p2 = new Vec3(
                        Math.cos(angle2) * ringRadius * distortion2,
                        height2,
                        Math.sin(angle2) * ringRadius * distortion2
                );

                lensRing.addLine(p1, p2);
            }

            // Add radial caustics
            for (int i = 0; i < 24; i++) {
                float angle = i * (float)Math.PI * 2.0f / 24.0f + time * 0.3f;
                float causticStrength = (float)Math.sin(time * 2f + i * 0.5f) * lensStrength;

                if (causticStrength > 0) {
                    Vec3 inner = new Vec3(
                            Math.cos(angle) * ringRadius * 0.8f,
                            0,
                            Math.sin(angle) * ringRadius * 0.8f
                    );
                    Vec3 outer = new Vec3(
                            Math.cos(angle) * ringRadius * (1.2f + causticStrength * 0.3f),
                            causticStrength * ringRadius * 0.1f,
                            Math.sin(angle) * ringRadius * (1.2f + causticStrength * 0.3f)
                    );
                    lensRing.addLine(inner, outer);
                }
            }

            int lensColor = adjustColorAlpha(COLOR_GRAVITATIONAL_LENS, lensStrength * 0.4f);
            VectorRenderer.drawWireframeWorld(
                    lensRing, center, lensColor, 0.008f * (1f + lensStrength), false, true, 1, VectorRenderer.Transform.IDENTITY
            );
        }
    }

    private static void renderHawkingRadiation(Vec3 center, float eventHorizonRadius, float time) {
        // Enhanced quantum effects near the event horizon
        VectorRenderer.Wireframe radiation = VectorRenderer.createWireframe();

        // Virtual particle pairs
        for (int pair = 0; pair < 32; pair++) {
            float pairAngle = pair * (float)Math.PI * 2.0f / 32.0f;
            float pairTime = (time * 3f + pair * 0.2f) % 2.5f;

            if (pairTime < 2.0f) {
                float t = pairTime / 2.0f;
                float startRadius = eventHorizonRadius * (1.001f + RAND.nextFloat() * 0.002f);

                // Particle trajectories
                float escapeRadius = startRadius + t * eventHorizonRadius * 0.3f;
                float capturedRadius = startRadius - t * eventHorizonRadius * 0.05f;

                // Slight randomization in trajectories
                float trajectory = (float)Math.sin(t * (float)Math.PI * 2f) * 0.15f;
                float adjustedAngle = pairAngle + trajectory;

                // Escaping particle (positive energy)
                Vec3 escaping = new Vec3(
                        Math.cos(adjustedAngle) * escapeRadius,
                        t * eventHorizonRadius * 0.2f + (float)Math.sin(time * 5f + pair) * 0.02f,
                        Math.sin(adjustedAngle) * escapeRadius
                );

                // Add slight trail
                Vec3 escapingPrev = new Vec3(
                        Math.cos(adjustedAngle) * (escapeRadius - eventHorizonRadius * 0.02f),
                        (t - 0.1f) * eventHorizonRadius * 0.2f,
                        Math.sin(adjustedAngle) * (escapeRadius - eventHorizonRadius * 0.02f)
                );

                if (t > 0.1f) {
                    radiation.addLine(escapingPrev, escaping);
                }
            }
        }

        // Quantum foam effects
        for (int foam = 0; foam < 16; foam++) {
            float foamRadius = eventHorizonRadius * (1.002f + foam * 0.001f);
            float foamTime = (time * 8f + foam * 0.4f) % 1.0f;

            if (foamTime < 0.3f) {
                float intensity = (float)Math.sin(foamTime / 0.3f * (float)Math.PI);
                List<Vec3> foamRing = new ArrayList<>();

                for (int i = 0; i <= 16; i++) {
                    float angle = i * (float)Math.PI * 2.0f / 16.0f + foam * 0.7f;
                    float quantumFluctuation = (float)Math.sin(angle * 7f + time * 12f) * 0.005f;

                    Vec3 point = new Vec3(
                            Math.cos(angle) * (foamRadius + quantumFluctuation),
                            (float)Math.sin(angle * 3f + time * 6f) * eventHorizonRadius * 0.01f,
                            Math.sin(angle) * (foamRadius + quantumFluctuation)
                    );
                    foamRing.add(point);
                }

                int foamColor = adjustColorAlpha(COLOR_HAWKING_RADIATION, intensity * 0.5f);
                VectorRenderer.drawPolylineWorld(
                        foamRing, foamColor, 0.003f, false, 1, VectorRenderer.Transform.IDENTITY
                );
            }
        }

        VectorRenderer.drawWireframeWorld(
                radiation, center, COLOR_HAWKING_RADIATION, 0.004f, false, true, 1, VectorRenderer.Transform.IDENTITY
        );
    }

    private static void renderInnerGlow(Vec3 center, float eventHorizonRadius, float photonSphereRadius, float time) {
        // Enhanced atmospheric glow with multiple scattering
        for (int shell = 0; shell < 5; shell++) {
            float shellRadius = eventHorizonRadius * (1.05f + shell * 0.08f);
            if (shellRadius > photonSphereRadius) break;

            float glowIntensity = (float)Math.pow(0.7f, shell);
            float pulsation = 1f + (float)Math.sin(time * 1.5f + shell * 0.8f) * 0.15f;

            List<Vec3> glowRing = new ArrayList<>();
            for (int i = 0; i <= 72; i++) {
                float angle = i * (float)Math.PI * 2.0f / 72.0f;
                float radiusVariation = 1f + (float)Math.sin(angle * 4f + time * 2f) * 0.05f;
                float radius = shellRadius * radiusVariation * pulsation;

                Vec3 point = new Vec3(
                        Math.cos(angle) * radius,
                        (float)Math.sin(angle * 6f + time * 3f) * shellRadius * 0.03f,
                        Math.sin(angle) * radius
                );
                glowRing.add(point);
            }

            int glowColor = adjustColorAlpha(COLOR_PHOTON_SPHERE_GLOW, glowIntensity * 0.4f);
            VectorRenderer.drawPolylineWorld(
                    glowRing, glowColor, shellRadius * 0.15f, false, 1, VectorRenderer.Transform.IDENTITY
            );

            // Add volumetric scattering effect
            if (shell < 3) {
                int scatterColor = adjustColorAlpha(COLOR_PHOTON_SPHERE_CORE, glowIntensity * 0.1f);
                VectorRenderer.drawPolylineWorld(
                        glowRing, scatterColor, shellRadius * 0.4f, false, 1, VectorRenderer.Transform.IDENTITY
                );
            }
        }
    }

    private static void renderQuantumEffects(Vec3 center, float eventHorizonRadius, float time) {
        // Additional quantum and relativistic effects

        // Vacuum polarization rings
        for (int ring = 0; ring < 3; ring++) {
            float ringRadius = eventHorizonRadius * (1.1f + ring * 0.15f);
            float phaseOffset = ring * (float)Math.PI * 2f / 3f;

            List<Vec3> polarizationRing = new ArrayList<>();
            for (int i = 0; i <= 48; i++) {
                float angle = i * (float)Math.PI * 2.0f / 48.0f + time * 0.8f + phaseOffset;
                float polarizationEffect = (float)Math.sin(angle * 8f + time * 4f) * 0.03f;

                Vec3 point = new Vec3(
                        Math.cos(angle) * ringRadius * (1f + polarizationEffect),
                        (float)Math.sin(angle * 12f + time * 6f) * ringRadius * 0.02f,
                        Math.sin(angle) * ringRadius * (1f + polarizationEffect)
                );
                polarizationRing.add(point);
            }

            int polarizationColor = adjustColorAlpha(0xFF8844FF, 0.2f / (ring + 1));
            VectorRenderer.drawPolylineWorld(
                    polarizationRing, polarizationColor, 0.008f, false, 1, VectorRenderer.Transform.IDENTITY
            );
        }

        // Ergosphere visualization (for rotating black holes)
        float ergosphereRadius = eventHorizonRadius * 1.3f;
        List<Vec3> ergosphere = new ArrayList<>();
        for (int i = 0; i <= 64; i++) {
            float angle = i * (float)Math.PI * 2.0f / 64.0f;
            float frameDegging = (float)Math.sin(angle + time * 2f) * 0.1f;
            float radius = ergosphereRadius * (1f + frameDegging);

            Vec3 point = new Vec3(
                    Math.cos(angle) * radius,
                    (float)Math.sin(angle * 2f + time) * radius * 0.1f,
                    Math.sin(angle) * radius
            );
            ergosphere.add(point);
        }

        int ergosphereColor = adjustColorAlpha(0xFF4488CC, 0.15f);
        VectorRenderer.drawPolylineWorld(
                ergosphere, ergosphereColor, 0.01f, false, 1, VectorRenderer.Transform.IDENTITY
        );
    }

    // Enhanced utility methods
    private static List<Vec3> createSphere(Vec3 center, float radius, int segments) {
        List<Vec3> points = new ArrayList<>();
        int rings = segments / 2;

        for (int ring = 0; ring <= rings; ring++) {
            double ringAngle = Math.PI * ring / rings - Math.PI / 2;
            float y = (float)(radius * Math.sin(ringAngle));
            float ringRadius = (float)(radius * Math.cos(ringAngle));

            int ringSegments = Math.max(3, (int)(segments * Math.cos(ringAngle)));

            for (int seg = 0; seg < ringSegments; seg++) {
                double segAngle = 2.0 * Math.PI * seg / ringSegments;
                float x = (float)(ringRadius * Math.cos(segAngle));
                float z = (float)(ringRadius * Math.sin(segAngle));

                points.add(center.add(new Vec3(x, y, z)));
            }
        }

        return points;
    }

    private static int interpolateColor(int color1, int color2, float t) {
        t = Math.max(0f, Math.min(1f, t));

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustColorBrightness(int color, float brightness) {
        brightness = Math.max(0f, Math.min(2f, brightness)); // Allow overbright

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
     * Enhanced BlackHoleEffect class with additional physics simulation
     */
    private static final class BlackHoleEffect {
        private final Vec3 position;
        private final float size;
        private final float rotationSpeed;
        private final int lifetime;
        private int age = 0;
        private float rotation = 0f;
        private float accumulatedTime = 0f;

        // Physics state
        private float diskTemperature = 1f;
        private float jetActivity = 1f;
        private float magneticFieldStrength = 1f;

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

            accumulatedTime += 0.05f; // Convert ticks to time

            // Simulate physical evolution
            updatePhysics();
        }

        private void updatePhysics() {
            // Disk temperature evolution (accretion rate variations)
            diskTemperature = 0.8f + 0.4f * (float)Math.sin(accumulatedTime * 0.3f) +
                    0.2f * (float)Math.sin(accumulatedTime * 1.2f);

            // Jet activity (magnetic field reconnection events)
            jetActivity = 0.7f + 0.3f * (float)Math.sin(accumulatedTime * 0.8f + Math.PI/4) +
                    0.2f * (float)Math.sin(accumulatedTime * 2.1f);

            // Magnetic field variations
            magneticFieldStrength = 0.9f + 0.2f * (float)Math.sin(accumulatedTime * 0.5f + Math.PI/3);
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

        // Getter methods for physics state
        float getDiskTemperature() { return diskTemperature; }
        float getJetActivity() { return jetActivity; }
        float getMagneticFieldStrength() { return magneticFieldStrength; }
    }
}