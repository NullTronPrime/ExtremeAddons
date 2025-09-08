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
    // Enhanced photon beam colors with spectrum simulation
    private static final int PRIMARY_CORE_COLOR = 0xFFFFFFFF;     // Pure white core
    private static final int PRIMARY_OUTER_COLOR = 0xFF00DDFF;    // Cyan outer glow
    private static final int SPLIT_CORE_COLOR = 0xFFFF88FF;       // Magenta splits
    private static final int SPLIT_OUTER_COLOR = 0xFF8800FF;      // Purple split glow
    private static final int SCATTERED_COLOR = 0xFF88DDFF;        // Light blue scattered
    private static final int FRESNEL_COLOR = 0xFFFFCC00;          // Golden Fresnel effect
    private static final int ABSORPTION_COLOR = 0xFFFF4400;       // Orange absorption

    // Enhanced visual properties
    private static final float PRIMARY_CORE_THICKNESS = 0.04f;
    private static final float PRIMARY_OUTER_THICKNESS = 0.3f;
    private static final float SPLIT_CORE_THICKNESS = 0.025f;
    private static final float SPLIT_OUTER_THICKNESS = 0.15f;
    private static final float SCATTERED_THICKNESS = 0.02f;

    // Lifetime configurations
    private static final int PRIMARY_LIFETIME = 80;
    private static final int SPLIT_LIFETIME = 60;
    private static final int SCATTERED_LIFETIME = 40;
    private static final int PARTICLE_LIFETIME = 30;
    private static final int FRESNEL_LIFETIME = 20;

    // Particle effects
    private static final int PHOTON_PARTICLE_DENSITY = 8;
    private static final int ABSORPTION_PARTICLE_COUNT = 12;

    public static void handleOptimizedPulsarAttack(Vec3 startPos, Vec3 direction, int shooterId,
                                                   float baseDamage, int maxBounces, double maxRange,
                                                   List<PulsarCannonItem.OptimizedSegment> preCalculatedSegments) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Level level = mc.level;
        Entity shooter = level.getEntity(shooterId);

        // Render the advanced photon beam system
        renderAdvancedPhotonBeam(preCalculatedSegments, level);
        createAdvancedParticleEffects(level, preCalculatedSegments);
        playAdvancedPhotonSounds(level, preCalculatedSegments);
    }

    private static void renderAdvancedPhotonBeam(List<PulsarCannonItem.OptimizedSegment> segments, Level level) {
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

            // Render each type with specific properties
            for (Map.Entry<PulsarCannonItem.PhotonType, List<PulsarCannonItem.OptimizedSegment>> entry : byType.entrySet()) {
                renderPhotonType(entry.getKey(), entry.getValue(), gen);
            }
        }
    }

    private static void renderPhotonType(PulsarCannonItem.PhotonType type,
                                         List<PulsarCannonItem.OptimizedSegment> segments, int generation) {
        PhotonRenderProperties props = getPhotonRenderProperties(type, generation);

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            float energyRatio = Mth.clamp(segment.energy / 30.0f, 0.1f, 1.0f);
            float intensityMultiplier = (float) Math.max(0.3, segment.intensity);

            // Dynamic beam properties based on energy and intensity
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

            // Add spectral dispersion effects for high-energy beams
            if (energyRatio > 0.7f && type == PulsarCannonItem.PhotonType.PRIMARY) {
                addSpectralDispersion(segment, energyRatio);
            }

            // Add interference patterns for split beams
            if (segment.isSplit && generation > 0) {
                addInterferencePattern(segment, generation);
            }

            // Photon particle trail
            addPhotonParticleTrail(segment, props, energyRatio);
        }
    }

    private static PhotonRenderProperties getPhotonRenderProperties(PulsarCannonItem.PhotonType type, int generation) {
        float generationFactor = Math.max(0.5f, 1.0f - generation * 0.1f);

        return switch (type) {
            case PRIMARY -> new PhotonRenderProperties(
                    PRIMARY_CORE_COLOR, PRIMARY_OUTER_COLOR,
                    PRIMARY_CORE_THICKNESS, PRIMARY_OUTER_THICKNESS,
                    PRIMARY_LIFETIME
            );
            case SPLIT -> new PhotonRenderProperties(
                    SPLIT_CORE_COLOR, SPLIT_OUTER_COLOR,
                    SPLIT_CORE_THICKNESS * generationFactor, SPLIT_OUTER_THICKNESS * generationFactor,
                    (int)(SPLIT_LIFETIME * generationFactor)
            );
            case SCATTERED -> new PhotonRenderProperties(
                    SCATTERED_COLOR, SCATTERED_COLOR,
                    SCATTERED_THICKNESS * generationFactor, SCATTERED_THICKNESS * 2 * generationFactor,
                    (int)(SCATTERED_LIFETIME * generationFactor)
            );
        };
    }

    private static void addSpectralDispersion(PulsarCannonItem.OptimizedSegment segment, float energyRatio) {
        Vec3 direction = segment.end.subtract(segment.start).normalize();
        Vec3 perpendicular = getPerpendicular(direction).scale(0.05 * energyRatio);

        // Create rainbow dispersion effect
        int[] spectrumColors = {0xFFFF0000, 0xFFFF8800, 0xFFFFFF00, 0xFF00FF00, 0xFF0088FF, 0xFF0000FF, 0xFF8800FF};

        for (int i = 0; i < spectrumColors.length; i++) {
            double offset = (i - 3) * 0.02 * energyRatio;
            Vec3 dispersedStart = segment.start.add(perpendicular.scale(offset));
            Vec3 dispersedEnd = segment.end.add(perpendicular.scale(offset));

            int color = (spectrumColors[i] & 0x00FFFFFF) | (((int)(120 * energyRatio)) << 24);

            VectorRenderer.drawLineWorld(
                    dispersedStart, dispersedEnd,
                    color, 0.01f, false,
                    PRIMARY_LIFETIME / 2, null
            );
        }
    }

    private static void addInterferencePattern(PulsarCannonItem.OptimizedSegment segment, int generation) {
        Vec3 direction = segment.end.subtract(segment.start).normalize();
        double length = segment.start.distanceTo(segment.end);

        // Create wave interference pattern
        int waveCount = Math.min(8, (int)(length * 2));
        for (int i = 0; i < waveCount; i++) {
            double t = (double)i / waveCount;
            Vec3 wavePos = segment.start.add(direction.scale(length * t));

            float amplitude = 0.1f / (generation + 1);
            int waveColor = interpolateColor(SPLIT_CORE_COLOR, FRESNEL_COLOR, Math.sin(t * Math.PI * 4));
            waveColor = (waveColor & 0x00FFFFFF) | (((int)(100 / (generation + 1))) << 24);

            VectorRenderer.drawSphereWorld(
                    wavePos, amplitude,
                    waveColor, 6, 6, false,
                    FRESNEL_LIFETIME, null
            );
        }
    }

    private static void addPhotonParticleTrail(PulsarCannonItem.OptimizedSegment segment,
                                               PhotonRenderProperties props, float energyRatio) {
        Vec3 direction = segment.end.subtract(segment.start);
        double length = direction.length();
        direction = direction.normalize();

        int particleCount = (int)(length * PHOTON_PARTICLE_DENSITY * energyRatio);
        particleCount = Math.min(particleCount, 50);

        for (int i = 0; i < particleCount; i++) {
            double t = (double)i / Math.max(1, particleCount - 1);
            Vec3 particlePos = segment.start.add(direction.scale(length * t));

            // Add quantum uncertainty to position
            double uncertainty = 0.05 * energyRatio;
            particlePos = particlePos.add(
                    (Math.random() - 0.5) * uncertainty,
                    (Math.random() - 0.5) * uncertainty,
                    (Math.random() - 0.5) * uncertainty
            );

            float particleSize = 0.015f * energyRatio * (0.8f + (float)Math.random() * 0.4f);
            int particleColor = interpolateColor(props.coreColor, props.outerColor, Math.random());
            particleColor = (particleColor & 0x00FFFFFF) | (((int)(220 * energyRatio)) << 24);

            VectorRenderer.drawSphereWorld(
                    particlePos, particleSize,
                    particleColor, 4, 4, false,
                    PARTICLE_LIFETIME, null
            );
        }
    }

    private static void createAdvancedParticleEffects(Level level, List<PulsarCannonItem.OptimizedSegment> segments) {
        Map<Vec3, Float> impactPoints = findImpactPoints(segments);
        Map<Vec3, Integer> reflectionPoints = findReflectionPoints(segments);

        // Create impact effects at end points
        for (Map.Entry<Vec3, Float> impact : impactPoints.entrySet()) {
            createPhotonImpactEffect(level, impact.getKey(), impact.getValue());
        }

        // Create Fresnel effects at reflection points
        for (Map.Entry<Vec3, Integer> reflection : reflectionPoints.entrySet()) {
            createFresnelReflectionEffect(level, reflection.getKey(), reflection.getValue());
        }

        // Create quantum effects for high-energy segments
        createQuantumEffects(level, segments);
    }

    private static void createPhotonImpactEffect(Level level, Vec3 impactPos, float energy) {
        float intensity = Math.min(1.0f, energy / 30.0f);

        // Central impact sphere
        VectorRenderer.drawSphereWorld(
                impactPos, 0.4f * intensity,
                ABSORPTION_COLOR, 12, 12, false,
                PRIMARY_LIFETIME, null
        );

        // Expanding energy wave
        VectorRenderer.drawSphereWorld(
                impactPos, 0.8f * intensity,
                (ABSORPTION_COLOR & 0x00FFFFFF) | 0x60000000,
                16, 16, false,
                PRIMARY_LIFETIME / 2, null
        );

        // Energy burst particles
        int burstCount = (int)(ABSORPTION_PARTICLE_COUNT * intensity);
        for (int i = 0; i < burstCount; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.random() * Math.PI;
            double distance = 1.0 + Math.random() * 2.0 * intensity;

            Vec3 particleDir = new Vec3(
                    Math.sin(phi) * Math.cos(theta),
                    Math.cos(phi),
                    Math.sin(phi) * Math.sin(theta)
            );

            Vec3 particleEnd = impactPos.add(particleDir.scale(distance));
            int burstColor = interpolateColor(ABSORPTION_COLOR, PRIMARY_CORE_COLOR, Math.random());

            VectorRenderer.drawLineWorld(
                    impactPos, particleEnd,
                    burstColor, 0.03f + (float)Math.random() * 0.05f, false,
                    PARTICLE_LIFETIME, null
            );
        }

        // Add Minecraft particles
        addMinecraftParticles(level, impactPos, intensity);
    }

    private static void createFresnelReflectionEffect(Level level, Vec3 reflectionPos, int bounceCount) {
        float intensity = Math.max(0.3f, 1.0f - bounceCount * 0.05f);

        // Fresnel reflection sphere
        VectorRenderer.drawSphereWorld(
                reflectionPos, 0.2f * intensity,
                (FRESNEL_COLOR & 0x00FFFFFF) | (((int)(180 * intensity)) << 24),
                8, 8, false,
                FRESNEL_LIFETIME, null
        );

        // Reflection rays
        int rayCount = Math.max(3, 6 - bounceCount / 5);
        for (int i = 0; i < rayCount; i++) {
            double angle = (2.0 * Math.PI * i) / rayCount;
            Vec3 rayDir = new Vec3(Math.cos(angle), 0.2, Math.sin(angle)).normalize();
            Vec3 rayEnd = reflectionPos.add(rayDir.scale(0.6 * intensity));

            VectorRenderer.drawLineWorld(
                    reflectionPos, rayEnd,
                    FRESNEL_COLOR, 0.02f, false,
                    FRESNEL_LIFETIME, null
            );
        }
    }

    private static void createQuantumEffects(Level level, List<PulsarCannonItem.OptimizedSegment> segments) {
        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            if (segment.energy > 20.0f && segment.bounceCount > 10) {
                Vec3 midpoint = segment.start.add(segment.end).scale(0.5);

                // Quantum uncertainty visualization
                for (int i = 0; i < 4; i++) {
                    Vec3 uncertaintyPos = midpoint.add(
                            (Math.random() - 0.5) * 0.3,
                            (Math.random() - 0.5) * 0.3,
                            (Math.random() - 0.5) * 0.3
                    );

                    int quantumColor = interpolateColor(PRIMARY_CORE_COLOR, SPLIT_CORE_COLOR, Math.random());
                    quantumColor = (quantumColor & 0x00FFFFFF) | 0x80000000;

                    VectorRenderer.drawSphereWorld(
                            uncertaintyPos, 0.05f,
                            quantumColor, 6, 6, false,
                            PARTICLE_LIFETIME / 2, null
                    );
                }
            }
        }
    }

    private static void addMinecraftParticles(Level level, Vec3 pos, float intensity) {
        // Electric spark particles
        for (int i = 0; i < intensity * 8; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + (Math.random() - 0.5) * 0.5,
                    pos.y + (Math.random() - 0.5) * 0.5,
                    pos.z + (Math.random() - 0.5) * 0.5,
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.2);
        }

        // Soul fire flame particles for high energy
        if (intensity > 0.7f) {
            for (int i = 0; i < intensity * 4; i++) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + (Math.random() - 0.5) * 0.3,
                        pos.y + (Math.random() - 0.5) * 0.3,
                        pos.z + (Math.random() - 0.5) * 0.3,
                        0, 0.02, 0);
            }
        }
    }

    private static void playAdvancedPhotonSounds(Level level, List<PulsarCannonItem.OptimizedSegment> segments) {
        if (segments.isEmpty()) return;

        // Count different types of interactions
        int totalBounces = segments.stream().mapToInt(s -> s.bounceCount).max().orElse(0);
        int splitCount = (int) segments.stream().filter(s -> s.isSplit).count();

        // Main beam sound at start
        Vec3 startPos = segments.get(0).start;
        level.playLocalSound(startPos.x, startPos.y, startPos.z,
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                2.0f, 0.6f + totalBounces * 0.01f, false);

        // Reflection chimes
        if (totalBounces > 5) {
            level.playLocalSound(startPos.x, startPos.y, startPos.z,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    1.5f, 1.2f + totalBounces * 0.002f, false);
        }

        // Splitting sound for high split counts
        if (splitCount > 3) {
            level.playLocalSound(startPos.x, startPos.y, startPos.z,
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
                    1.0f, 1.8f, false);
        }

        // Final impact sound
        Vec3 finalPos = segments.get(segments.size() - 1).end;
        level.playLocalSound(finalPos.x, finalPos.y, finalPos.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                1.5f + splitCount * 0.1f, 1.5f + totalBounces * 0.005f, false);
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
        float heatFactor = Math.max(0.3f, energyRatio);
        float bounceFactor = Math.max(0.5f, 1.0f - bounceCount * 0.02f);

        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        // Shift toward red as energy decreases
        if (energyRatio < 0.7f) {
            r = Math.min(255, (int)(r + (1.0f - energyRatio) * 100));
            b = Math.max(50, (int)(b * energyRatio));
        }

        // Dim with bounces
        r = (int)(r * bounceFactor);
        g = (int)(g * bounceFactor);
        b = (int)(b * bounceFactor);

        return (baseColor & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // Data class
    private static class PhotonRenderProperties {
        public final int coreColor;
        public final int outerColor;
        public final float coreThickness;
        public final float outerThickness;
        public final int lifetime;

        public PhotonRenderProperties(int coreColor, int outerColor, float coreThickness,
                                      float outerThickness, int lifetime) {
            this.coreColor = coreColor;
            this.outerColor = outerColor;
            this.coreThickness = coreThickness;
            this.outerThickness = outerThickness;
            this.lifetime = lifetime;
        }
    }
}