package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.item.custom.PulsarCannonItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class PulsarRenderer {
    // Enhanced photon beam colors with laser-like split rendering
    private static final int PRIMARY_CORE_COLOR = 0xFFFFFFFF;     // Pure white core
    private static final int PRIMARY_OUTER_COLOR = 0xFF00DDFF;    // Cyan outer glow

    // Enhanced laser-like split colors
    private static final int SPLIT_CORE_COLOR = 0xFFFF0000;       // Bright red laser core
    private static final int SPLIT_OUTER_COLOR = 0xFFFF4400;      // Orange-red laser glow
    private static final int SPLIT_INNER_COLOR = 0xFFFFDD00;      // Yellow inner beam

    private static final int SCATTERED_COLOR = 0xFF88DDFF;        // Light blue scattered
    private static final int FRESNEL_COLOR = 0xFFFFCC00;          // Golden Fresnel effect
    private static final int ABSORPTION_COLOR = 0xFFFF4400;       // Orange absorption
    private static final int LASER_SPARK_COLOR = 0xFFFFFFFF;      // White laser sparks

    // Enhanced visual properties with laser-specific settings
    private static final float PRIMARY_CORE_THICKNESS = 0.04f;
    private static final float PRIMARY_OUTER_THICKNESS = 0.3f;

    // Laser-like split beam properties
    private static final float SPLIT_CORE_THICKNESS = 0.06f;      // Thicker laser core
    private static final float SPLIT_OUTER_THICKNESS = 0.25f;     // Substantial laser glow
    private static final float SPLIT_INNER_THICKNESS = 0.03f;     // Inner laser beam

    private static final float SCATTERED_THICKNESS = 0.02f;

    // Enhanced lifetime configurations
    private static final int PRIMARY_LIFETIME = 90;
    private static final int SPLIT_LIFETIME = 80;                 // Longer laser lifetime
    private static final int SCATTERED_LIFETIME = 50;
    private static final int PARTICLE_LIFETIME = 40;
    private static final int FRESNEL_LIFETIME = 25;
    private static final int LASER_SPARK_LIFETIME = 15;

    // Enhanced particle effects
    private static final int PHOTON_PARTICLE_DENSITY = 10;
    private static final int LASER_PARTICLE_DENSITY = 12;         // More particles for laser splits
    private static final int ABSORPTION_PARTICLE_COUNT = 15;
    private static final int LASER_SPARK_COUNT = 8;

    public static void handleOptimizedPulsarAttack(Vec3 startPos, Vec3 direction, int shooterId,
                                                   float baseDamage, int maxBounces, double maxRange,
                                                   List<PulsarCannonItem.OptimizedSegment> preCalculatedSegments) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Level level = mc.level;
        Entity shooter = level.getEntity(shooterId);

        // Render the enhanced laser-photon hybrid beam system
        renderEnhancedLaserPhotonBeam(preCalculatedSegments, level);
        createEnhancedLaserParticleEffects(level, preCalculatedSegments);
        playEnhancedLaserSounds(level, preCalculatedSegments);
    }

    private static void renderEnhancedLaserPhotonBeam(List<PulsarCannonItem.OptimizedSegment> segments, Level level) {
        // Group segments by generation for proper layering
        Map<Integer, List<PulsarCannonItem.OptimizedSegment>> segmentsByGeneration =
                groupSegmentsByGeneration(segments);

        // Render in order of generation for proper layering
        for (int gen = 0; gen <= getMaxGeneration(segmentsByGeneration); gen++) {
            List<PulsarCannonItem.OptimizedSegment> genSegments = segmentsByGeneration.get(gen);
            if (genSegments == null) continue;

            // Group by photon type for efficient rendering
            Map<PulsarCannonItem.PhotonType, List<PulsarCannonItem.OptimizedSegment>> byType =
                    groupSegmentsByType(genSegments);

            // Render each type with enhanced laser-specific properties
            for (Map.Entry<PulsarCannonItem.PhotonType, List<PulsarCannonItem.OptimizedSegment>> entry : byType.entrySet()) {
                renderEnhancedPhotonType(entry.getKey(), entry.getValue(), gen);
            }
        }
    }

    private static void renderEnhancedPhotonType(PulsarCannonItem.PhotonType type,
                                                 List<PulsarCannonItem.OptimizedSegment> segments, int generation) {
        EnhancedPhotonRenderProperties props = getEnhancedPhotonRenderProperties(type, generation);

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            float energyRatio = Mth.clamp(segment.energy / 30.0f, 0.15f, 1.0f);
            float intensityMultiplier = (float) Math.max(0.4, segment.intensity);

            // Enhanced beam properties based on energy and intensity
            float coreThickness = props.coreThickness * energyRatio * intensityMultiplier;
            float outerThickness = props.outerThickness * energyRatio * intensityMultiplier;

            if (type == PulsarCannonItem.PhotonType.SPLIT) {
                // Render laser-like split beams with multiple layers
                renderLaserSplitBeam(segment, props, energyRatio, intensityMultiplier);
            } else {
                // Render standard photon beam
                renderStandardPhotonBeam(segment, props, energyRatio, intensityMultiplier);
            }

            // Add enhanced spectral dispersion effects for high-energy beams
            if (energyRatio > 0.6f && type == PulsarCannonItem.PhotonType.PRIMARY) {
                addEnhancedSpectralDispersion(segment, energyRatio);
            }

            // Add laser interference patterns for split beams
            if (type == PulsarCannonItem.PhotonType.SPLIT && generation > 0) {
                addLaserInterferencePattern(segment, generation);
            }

            // Enhanced photon particle trail
            addEnhancedPhotonParticleTrail(segment, props, energyRatio, type);
        }
    }

    private static void renderLaserSplitBeam(PulsarCannonItem.OptimizedSegment segment,
                                             EnhancedPhotonRenderProperties props,
                                             float energyRatio, float intensityMultiplier) {
        // Multi-layer laser rendering for realistic laser appearance

        // 1. Outer laser glow (largest, most transparent)
        int outerColor = interpolateEnergyColor(props.outerColor, energyRatio * 0.7f, segment.bounceCount);
        outerColor = (outerColor & 0x00FFFFFF) | (((int)(120 * energyRatio * intensityMultiplier)) << 24);

        VectorRenderer.drawLineWorld(
                segment.start, segment.end,
                outerColor, props.outerThickness * energyRatio * intensityMultiplier, false,
                props.lifetime, null
        );

        // 2. Middle laser beam (medium thickness, bright)
        int middleColor = interpolateEnergyColor(props.innerColor, energyRatio * 0.9f, segment.bounceCount);
        middleColor = (middleColor & 0x00FFFFFF) | (((int)(200 * energyRatio * intensityMultiplier)) << 24);

        VectorRenderer.drawLineWorld(
                segment.start, segment.end,
                middleColor, props.innerThickness * energyRatio * intensityMultiplier, false,
                props.lifetime, null
        );

        // 3. Inner laser core (thinnest, brightest)
        int coreColor = interpolateEnergyColor(props.coreColor, energyRatio, segment.bounceCount);
        coreColor = (coreColor & 0x00FFFFFF) | 0xFF000000; // Full opacity for core

        VectorRenderer.drawLineWorld(
                segment.start, segment.end,
                coreColor, props.coreThickness * energyRatio * intensityMultiplier, false,
                props.lifetime, null
        );

        // 4. Laser spark effects along the beam
        addLaserSparkEffects(segment, energyRatio);
    }

    private static void renderStandardPhotonBeam(PulsarCannonItem.OptimizedSegment segment,
                                                 EnhancedPhotonRenderProperties props,
                                                 float energyRatio, float intensityMultiplier) {
        // Standard photon beam rendering
        float coreThickness = props.coreThickness * energyRatio * intensityMultiplier;
        float outerThickness = props.outerThickness * energyRatio * intensityMultiplier;

        // Core beam with energy-dependent color
        int coreColor = interpolateEnergyColor(props.coreColor, energyRatio, segment.bounceCount);
        VectorRenderer.drawLineWorld(
                segment.start, segment.end,
                coreColor, coreThickness, false,
                props.lifetime, null
        );

        // Outer glow with transparency
        int outerColor = interpolateEnergyColor(props.outerColor, energyRatio * 0.8f, segment.bounceCount);
        outerColor = (outerColor & 0x00FFFFFF) | (((int)(180 * energyRatio * intensityMultiplier)) << 24);

        VectorRenderer.drawLineWorld(
                segment.start, segment.end,
                outerColor, outerThickness, false,
                props.lifetime, null
        );
    }

    private static void addLaserSparkEffects(PulsarCannonItem.OptimizedSegment segment, float energyRatio) {
        Vec3 direction = segment.end.subtract(segment.start);
        double length = direction.length();
        direction = direction.normalize();

        int sparkCount = (int)(length * 0.5 * energyRatio);
        sparkCount = Math.min(sparkCount, LASER_SPARK_COUNT);

        for (int i = 0; i < sparkCount; i++) {
            double t = Math.random();
            Vec3 sparkPos = segment.start.add(direction.scale(length * t));

            // Add some randomness to spark position
            sparkPos = sparkPos.add(
                    (Math.random() - 0.5) * 0.1,
                    (Math.random() - 0.5) * 0.1,
                    (Math.random() - 0.5) * 0.1
            );

            float sparkSize = 0.02f + (float)Math.random() * 0.03f;
            int sparkColor = LASER_SPARK_COLOR;
            sparkColor = (sparkColor & 0x00FFFFFF) | (((int)(255 * energyRatio)) << 24);

            VectorRenderer.drawSphereWorld(
                    sparkPos, sparkSize,
                    sparkColor, 4, 4, false,
                    LASER_SPARK_LIFETIME, null
            );
        }
    }

    private static EnhancedPhotonRenderProperties getEnhancedPhotonRenderProperties(PulsarCannonItem.PhotonType type, int generation) {
        float generationFactor = Math.max(0.6f, 1.0f - generation * 0.08f);

        return switch (type) {
            case PRIMARY -> new EnhancedPhotonRenderProperties(
                    PRIMARY_CORE_COLOR, PRIMARY_OUTER_COLOR, PRIMARY_CORE_COLOR,
                    PRIMARY_CORE_THICKNESS, PRIMARY_OUTER_THICKNESS, PRIMARY_CORE_THICKNESS,
                    PRIMARY_LIFETIME
            );
            case SPLIT -> new EnhancedPhotonRenderProperties(
                    SPLIT_CORE_COLOR, SPLIT_OUTER_COLOR, SPLIT_INNER_COLOR,
                    SPLIT_CORE_THICKNESS * generationFactor,
                    SPLIT_OUTER_THICKNESS * generationFactor,
                    SPLIT_INNER_THICKNESS * generationFactor,
                    (int)(SPLIT_LIFETIME * generationFactor)
            );
            case SCATTERED -> new EnhancedPhotonRenderProperties(
                    SCATTERED_COLOR, SCATTERED_COLOR, SCATTERED_COLOR,
                    SCATTERED_THICKNESS * generationFactor,
                    SCATTERED_THICKNESS * 2 * generationFactor,
                    SCATTERED_THICKNESS * generationFactor,
                    (int)(SCATTERED_LIFETIME * generationFactor)
            );
        };
    }

    private static void addEnhancedSpectralDispersion(PulsarCannonItem.OptimizedSegment segment, float energyRatio) {
        Vec3 direction = segment.end.subtract(segment.start).normalize();
        Vec3 perpendicular = getPerpendicular(direction).scale(0.06 * energyRatio);

        // Enhanced rainbow dispersion effect with more colors
        int[] spectrumColors = {
                0xFFFF0000, 0xFFFF4000, 0xFFFF8000, 0xFFFFBF00, 0xFFFFFF00, 0xFFBFFF00,
                0xFF80FF00, 0xFF40FF00, 0xFF00FF00, 0xFF00FF40, 0xFF00FF80, 0xFF00FFBF,
                0xFF00FFFF, 0xFF00BFFF, 0xFF0080FF, 0xFF0040FF, 0xFF0000FF, 0xFF4000FF,
                0xFF8000FF, 0xFFBF00FF
        };

        for (int i = 0; i < spectrumColors.length; i++) {
            double offset = (i - spectrumColors.length/2.0) * 0.015 * energyRatio;
            Vec3 dispersedStart = segment.start.add(perpendicular.scale(offset));
            Vec3 dispersedEnd = segment.end.add(perpendicular.scale(offset));

            int color = (spectrumColors[i] & 0x00FFFFFF) | (((int)(100 * energyRatio)) << 24);

            VectorRenderer.drawLineWorld(
                    dispersedStart, dispersedEnd,
                    color, 0.008f, false,
                    PRIMARY_LIFETIME / 3, null
            );
        }
    }

    private static void addLaserInterferencePattern(PulsarCannonItem.OptimizedSegment segment, int generation) {
        Vec3 direction = segment.end.subtract(segment.start).normalize();
        double length = segment.start.distanceTo(segment.end);

        // Create laser interference pattern with higher frequency
        int waveCount = Math.min(12, (int)(length * 3));
        for (int i = 0; i < waveCount; i++) {
            double t = (double)i / waveCount;
            Vec3 wavePos = segment.start.add(direction.scale(length * t));

            float amplitude = 0.08f / Math.max(1, generation);
            int waveColor = interpolateColor(SPLIT_CORE_COLOR, SPLIT_INNER_COLOR, Math.sin(t * Math.PI * 6));
            waveColor = (waveColor & 0x00FFFFFF) | (((int)(150 / Math.max(1, generation))) << 24);

            // Create cross-shaped interference pattern
            VectorRenderer.drawSphereWorld(
                    wavePos, amplitude,
                    waveColor, 8, 8, false,
                    FRESNEL_LIFETIME, null
            );
        }
    }

    private static void addEnhancedPhotonParticleTrail(PulsarCannonItem.OptimizedSegment segment,
                                                       EnhancedPhotonRenderProperties props,
                                                       float energyRatio, PulsarCannonItem.PhotonType type) {
        Vec3 direction = segment.end.subtract(segment.start);
        double length = direction.length();
        direction = direction.normalize();

        int particleDensity = type == PulsarCannonItem.PhotonType.SPLIT ? LASER_PARTICLE_DENSITY : PHOTON_PARTICLE_DENSITY;
        int particleCount = (int)(length * particleDensity * energyRatio);
        particleCount = Math.min(particleCount, 60);

        for (int i = 0; i < particleCount; i++) {
            double t = (double)i / Math.max(1, particleCount - 1);
            Vec3 particlePos = segment.start.add(direction.scale(length * t));

            // Enhanced quantum uncertainty with type-specific behavior
            double uncertainty = type == PulsarCannonItem.PhotonType.SPLIT ? 0.03 * energyRatio : 0.05 * energyRatio;
            particlePos = particlePos.add(
                    (Math.random() - 0.5) * uncertainty,
                    (Math.random() - 0.5) * uncertainty,
                    (Math.random() - 0.5) * uncertainty
            );

            float particleSize = getParticleSizeForType(type, energyRatio);
            int particleColor = getParticleColorForType(type, props, Math.random());
            particleColor = (particleColor & 0x00FFFFFF) | (((int)(240 * energyRatio)) << 24);

            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize,
                    particleColor, 6, 6, false,
                    PARTICLE_LIFETIME, null
            );
        }
    }

    private static float getParticleSizeForType(PulsarCannonItem.PhotonType type, float energyRatio) {
        return switch (type) {
            case PRIMARY -> 0.015f * energyRatio * (0.8f + (float)Math.random() * 0.4f);
            case SPLIT -> 0.022f * energyRatio * (0.9f + (float)Math.random() * 0.2f); // Larger laser particles
            case SCATTERED -> 0.012f * energyRatio * (0.7f + (float)Math.random() * 0.6f);
        };
    }

    private static int getParticleColorForType(PulsarCannonItem.PhotonType type,
                                               EnhancedPhotonRenderProperties props, double randomFactor) {
        return switch (type) {
            case PRIMARY -> interpolateColor(props.coreColor, props.outerColor, randomFactor);
            case SPLIT -> interpolateColor(props.coreColor, props.innerColor, randomFactor); // Laser colors
            case SCATTERED -> interpolateColor(props.coreColor, props.outerColor, randomFactor * 0.7);
        };
    }

    private static void createEnhancedLaserParticleEffects(Level level, List<PulsarCannonItem.OptimizedSegment> segments) {
        Map<Vec3, Float> impactPoints = findImpactPoints(segments);
        Map<Vec3, Integer> reflectionPoints = findReflectionPoints(segments);
        Map<Vec3, Integer> laserSplitPoints = findLaserSplitPoints(segments);

        // Create enhanced impact effects at end points
        for (Map.Entry<Vec3, Float> impact : impactPoints.entrySet()) {
            createEnhancedPhotonImpactEffect(level, impact.getKey(), impact.getValue());
        }

        // Create enhanced Fresnel effects at reflection points
        for (Map.Entry<Vec3, Integer> reflection : reflectionPoints.entrySet()) {
            createEnhancedFresnelReflectionEffect(level, reflection.getKey(), reflection.getValue());
        }

        // Create laser split effects
        for (Map.Entry<Vec3, Integer> laserSplit : laserSplitPoints.entrySet()) {
            createLaserSplitEffect(level, laserSplit.getKey(), laserSplit.getValue());
        }

        // Create enhanced quantum effects for high-energy segments
        createEnhancedQuantumEffects(level, segments);
    }

    private static void createLaserSplitEffect(Level level, Vec3 splitPos, int splitCount) {
        float intensity = Math.min(1.0f, splitCount / 3.0f);

        // Central laser split core
        VectorRenderer.drawSphereWorld(
                splitPos, 0.3f * intensity,
                SPLIT_CORE_COLOR, 16, 16, false,
                SPLIT_LIFETIME, null
        );

        // Expanding laser energy ring
        VectorRenderer.drawSphereWorld(
                splitPos, 0.6f * intensity,
                (SPLIT_OUTER_COLOR & 0x00FFFFFF) | 0x80000000,
                20, 20, false,
                SPLIT_LIFETIME / 2, null
        );

        // Laser split rays
        int rayCount = Math.max(4, Math.min(12, splitCount * 2));
        for (int i = 0; i < rayCount; i++) {
            double angle = (2.0 * Math.PI * i) / rayCount;
            double elevation = (Math.random() - 0.5) * Math.PI / 4;

            Vec3 rayDir = new Vec3(
                    Math.cos(angle) * Math.cos(elevation),
                    Math.sin(elevation),
                    Math.sin(angle) * Math.cos(elevation)
            ).normalize();

            Vec3 rayEnd = splitPos.add(rayDir.scale(0.8 * intensity));
            int rayColor = interpolateColor(SPLIT_CORE_COLOR, SPLIT_INNER_COLOR, Math.random());

            VectorRenderer.drawLineWorld(
                    splitPos, rayEnd,
                    rayColor, 0.04f, false,
                    SPLIT_LIFETIME / 2, null
            );
        }
    }

    private static Map<Vec3, Integer> findLaserSplitPoints(List<PulsarCannonItem.OptimizedSegment> segments) {
        Map<Vec3, Integer> laserSplits = new HashMap<>();

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            if (segment.type == PulsarCannonItem.PhotonType.SPLIT && segment.hitBlock) {
                Vec3 roundedPos = new Vec3(
                        Math.round(segment.end.x * 100.0) / 100.0,
                        Math.round(segment.end.y * 100.0) / 100.0,
                        Math.round(segment.end.z * 100.0) / 100.0
                );
                laserSplits.merge(roundedPos, 1, Integer::sum);
            }
        }

        return laserSplits;
    }

    private static void createEnhancedPhotonImpactEffect(Level level, Vec3 impactPos, float energy) {
        float intensity = Math.min(1.0f, energy / 25.0f);

        // Enhanced central impact sphere
        VectorRenderer.drawSphereWorld(
                impactPos, 0.5f * intensity,
                ABSORPTION_COLOR, 16, 16, false,
                PRIMARY_LIFETIME, null
        );

        // Multiple expanding energy waves
        for (int wave = 0; wave < 3; wave++) {
            VectorRenderer.drawSphereWorld(
                    impactPos, (1.0f + wave * 0.4f) * intensity,
                    (ABSORPTION_COLOR & 0x00FFFFFF) | ((120 - wave * 30) << 24),
                    20, 20, false,
                    PRIMARY_LIFETIME - wave * 10, null
            );
        }

        // Enhanced energy burst particles
        int burstCount = (int)(ABSORPTION_PARTICLE_COUNT * intensity * 1.2f);
        for (int i = 0; i < burstCount; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.random() * Math.PI;
            double distance = 1.5 + Math.random() * 2.5 * intensity;

            Vec3 particleDir = new Vec3(
                    Math.sin(phi) * Math.cos(theta),
                    Math.cos(phi),
                    Math.sin(phi) * Math.sin(theta)
            );

            Vec3 particleEnd = impactPos.add(particleDir.scale(distance));
            int burstColor = interpolateColor(ABSORPTION_COLOR, PRIMARY_CORE_COLOR, Math.random());

            VectorRenderer.drawLineWorld(
                    impactPos, particleEnd,
                    burstColor, 0.04f + (float)Math.random() * 0.06f, false,
                    PARTICLE_LIFETIME, null
            );
        }

        // Add enhanced Minecraft particles
        addEnhancedMinecraftParticles(level, impactPos, intensity);
    }

    private static void createEnhancedFresnelReflectionEffect(Level level, Vec3 reflectionPos, int bounceCount) {
        float intensity = Math.max(0.4f, 1.0f - bounceCount * 0.04f);

        // Enhanced Fresnel reflection sphere with pulsing effect
        VectorRenderer.drawSphereWorld(
                reflectionPos, 0.25f * intensity,
                (FRESNEL_COLOR & 0x00FFFFFF) | (((int)(200 * intensity)) << 24),
                12, 12, false,
                FRESNEL_LIFETIME, null
        );

        // Outer reflection ring
        VectorRenderer.drawSphereWorld(
                reflectionPos, 0.4f * intensity,
                (FRESNEL_COLOR & 0x00FFFFFF) | (((int)(100 * intensity)) << 24),
                16, 16, false,
                FRESNEL_LIFETIME / 2, null
        );

        // Enhanced reflection rays
        int rayCount = Math.max(4, 8 - bounceCount / 8);
        for (int i = 0; i < rayCount; i++) {
            double angle = (2.0 * Math.PI * i) / rayCount;
            double elevation = Math.sin(angle * 2) * 0.3; // Wavy pattern
            Vec3 rayDir = new Vec3(Math.cos(angle), elevation, Math.sin(angle)).normalize();
            Vec3 rayEnd = reflectionPos.add(rayDir.scale(0.8 * intensity));

            int rayColor = interpolateColor(FRESNEL_COLOR, PRIMARY_CORE_COLOR, Math.random() * 0.5);

            VectorRenderer.drawLineWorld(
                    reflectionPos, rayEnd,
                    rayColor, 0.025f, false,
                    FRESNEL_LIFETIME, null
            );
        }
    }

    private static void createEnhancedQuantumEffects(Level level, List<PulsarCannonItem.OptimizedSegment> segments) {
        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            if (segment.energy > 15.0f && segment.bounceCount > 8) {
                Vec3 midpoint = segment.start.add(segment.end).scale(0.5);

                // Enhanced quantum uncertainty visualization
                int uncertaintyCount = segment.type == PulsarCannonItem.PhotonType.SPLIT ? 6 : 4;
                for (int i = 0; i < uncertaintyCount; i++) {
                    Vec3 uncertaintyPos = midpoint.add(
                            (Math.random() - 0.5) * 0.4,
                            (Math.random() - 0.5) * 0.4,
                            (Math.random() - 0.5) * 0.4
                    );

                    int quantumColor = switch (segment.type) {
                        case PRIMARY -> interpolateColor(PRIMARY_CORE_COLOR, PRIMARY_OUTER_COLOR, Math.random());
                        case SPLIT -> interpolateColor(SPLIT_CORE_COLOR, SPLIT_INNER_COLOR, Math.random());
                        case SCATTERED -> interpolateColor(SCATTERED_COLOR, PRIMARY_CORE_COLOR, Math.random());
                    };
                    quantumColor = (quantumColor & 0x00FFFFFF) | 0x90000000;

                    float quantumSize = segment.type == PulsarCannonItem.PhotonType.SPLIT ? 0.07f : 0.05f;

                    VectorRenderer.drawSphereWorld(
                            uncertaintyPos, quantumSize,
                            quantumColor, 8, 8, false,
                            PARTICLE_LIFETIME / 2, null
                    );
                }
            }
        }
    }

    private static void addEnhancedMinecraftParticles(Level level, Vec3 pos, float intensity) {
        // Enhanced electric spark particles
        for (int i = 0; i < intensity * 12; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + (Math.random() - 0.5) * 0.6,
                    pos.y + (Math.random() - 0.5) * 0.6,
                    pos.z + (Math.random() - 0.5) * 0.6,
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3);
        }

        // Soul fire flame particles for high energy
        if (intensity > 0.6f) {
            for (int i = 0; i < intensity * 6; i++) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + (Math.random() - 0.5) * 0.4,
                        pos.y + (Math.random() - 0.5) * 0.4,
                        pos.z + (Math.random() - 0.5) * 0.4,
                        0, 0.03, 0);
            }
        }

        // Add laser-specific particles for very high intensity
        if (intensity > 0.8f) {
            for (int i = 0; i < intensity * 4; i++) {
                level.addParticle(ParticleTypes.END_ROD,
                        pos.x + (Math.random() - 0.5) * 0.3,
                        pos.y + (Math.random() - 0.5) * 0.3,
                        pos.z + (Math.random() - 0.5) * 0.3,
                        (Math.random() - 0.5) * 0.1,
                        (Math.random() - 0.5) * 0.1,
                        (Math.random() - 0.5) * 0.1);
            }
        }
    }

    private static void playEnhancedLaserSounds(Level level, List<PulsarCannonItem.OptimizedSegment> segments) {
        if (segments.isEmpty()) return;

        // Count different types of interactions
        int totalBounces = segments.stream().mapToInt(s -> s.bounceCount).max().orElse(0);
        int splitCount = (int) segments.stream().filter(s -> s.isSplit).count();
        int laserSplitCount = (int) segments.stream()
                .filter(s -> s.type == PulsarCannonItem.PhotonType.SPLIT).count();

        // Enhanced main beam sound at start
        Vec3 startPos = segments.get(0).start;
        level.playLocalSound(startPos.x, startPos.y, startPos.z,
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                3.0f, 0.5f + totalBounces * 0.008f, false);

        // Enhanced reflection chimes
        if (totalBounces > 3) {
            level.playLocalSound(startPos.x, startPos.y, startPos.z,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    1.8f, 1.3f + totalBounces * 0.001f, false);
        }

        // Laser splitting sound for laser splits
        if (laserSplitCount > 2) {
            level.playLocalSound(startPos.x, startPos.y, startPos.z,
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                    1.2f + laserSplitCount * 0.1f, 1.9f, false);

            // Additional laser-specific sound
            level.playLocalSound(startPos.x, startPos.y, startPos.z,
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                    1.0f, 2.0f, false);
        }

        // Enhanced splitting sound for high split counts
        if (splitCount > 5) {
            level.playLocalSound(startPos.x, startPos.y, startPos.z,
                    SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS,
                    1.5f, 1.6f + splitCount * 0.02f, false);
        }

        // Enhanced final impact sound
        Vec3 finalPos = segments.get(segments.size() - 1).end;
        float impactVolume = 1.8f + splitCount * 0.08f + laserSplitCount * 0.12f;
        float impactPitch = 1.4f + totalBounces * 0.003f + laserSplitCount * 0.05f;

        level.playLocalSound(finalPos.x, finalPos.y, finalPos.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                impactVolume, impactPitch, false);
    }

    // Utility methods
    private static Map<Integer, List<PulsarCannonItem.OptimizedSegment>> groupSegmentsByGeneration(
            List<PulsarCannonItem.OptimizedSegment> segments) {
        Map<Integer, List<PulsarCannonItem.OptimizedSegment>> grouped = new HashMap<>();

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            grouped.computeIfAbsent(segment.generation, k -> new ArrayList<>()).add(segment);
        }

        return grouped;
    }

    private static Map<PulsarCannonItem.PhotonType, List<PulsarCannonItem.OptimizedSegment>> groupSegmentsByType(
            List<PulsarCannonItem.OptimizedSegment> segments) {
        Map<PulsarCannonItem.PhotonType, List<PulsarCannonItem.OptimizedSegment>> grouped = new HashMap<>();

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            PulsarCannonItem.PhotonType type = segment.type != null ? segment.type : PulsarCannonItem.PhotonType.PRIMARY;
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(segment);
        }

        return grouped;
    }

    private static int getMaxGeneration(Map<Integer, List<PulsarCannonItem.OptimizedSegment>> map) {
        return map.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static Map<Vec3, Float> findImpactPoints(List<PulsarCannonItem.OptimizedSegment> segments) {
        Map<Vec3, Float> impacts = new HashMap<>();

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            if (segment.hitBlock) {
                Vec3 roundedPos = new Vec3(
                        Math.round(segment.end.x * 100.0) / 100.0,
                        Math.round(segment.end.y * 100.0) / 100.0,
                        Math.round(segment.end.z * 100.0) / 100.0
                );
                impacts.merge(roundedPos, segment.energy, Float::sum);
            }
        }

        return impacts;
    }

    private static Map<Vec3, Integer> findReflectionPoints(List<PulsarCannonItem.OptimizedSegment> segments) {
        Map<Vec3, Integer> reflections = new HashMap<>();

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            if (segment.bounceCount > 0 && segment.hitBlock) {
                Vec3 roundedPos = new Vec3(
                        Math.round(segment.end.x * 100.0) / 100.0,
                        Math.round(segment.end.y * 100.0) / 100.0,
                        Math.round(segment.end.z * 100.0) / 100.0
                );
                reflections.merge(roundedPos, 1, Integer::sum);
            }
        }

        return reflections;
    }

    private static Vec3 getPerpendicular(Vec3 vector) {
        Vec3 candidate = new Vec3(0, 1, 0);
        if (Math.abs(vector.dot(candidate)) > 0.9) {
            candidate = new Vec3(1, 0, 0);
        }
        return vector.cross(candidate).normalize();
    }

    private static int interpolateColor(int color1, int color2, double t) {
        t = Mth.clamp(t, 0.0, 1.0);

        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF,
                g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF,
                g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;

        int a = (int)(a1 + t * (a2 - a1));
        int r = (int)(r1 + t * (r2 - r1));
        int g = (int)(g1 + t * (g2 - g1));
        int b = (int)(b1 + t * (b2 - b1));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolateEnergyColor(int baseColor, float energyRatio, int bounceCount) {
        float heatFactor = Math.max(0.4f, energyRatio);
        float bounceFactor = Math.max(0.6f, 1.0f - bounceCount * 0.015f);

        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        // Enhanced energy-based color shifting
        if (energyRatio < 0.8f) {
            r = Math.min(255, (int)(r + (1.0f - energyRatio) * 80));
            b = Math.max(60, (int)(b * energyRatio));
        }

        // Less aggressive dimming with bounces
        r = (int)(r * bounceFactor);
        g = (int)(g * bounceFactor);
        b = (int)(b * bounceFactor);

        return (baseColor & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // Enhanced data class for laser-like rendering
    private static class EnhancedPhotonRenderProperties {
        public final int coreColor;
        public final int outerColor;
        public final int innerColor;    // Additional color for laser inner beam
        public final float coreThickness;
        public final float outerThickness;
        public final float innerThickness; // Additional thickness for laser inner beam
        public final int lifetime;

        public EnhancedPhotonRenderProperties(int coreColor, int outerColor, int innerColor,
                                              float coreThickness, float outerThickness, float innerThickness,
                                              int lifetime) {
            this.coreColor = coreColor;
            this.outerColor = outerColor;
            this.innerColor = innerColor;
            this.coreThickness = coreThickness;
            this.outerThickness = outerThickness;
            this.innerThickness = innerThickness;
            this.lifetime = lifetime;
        }
    }
}