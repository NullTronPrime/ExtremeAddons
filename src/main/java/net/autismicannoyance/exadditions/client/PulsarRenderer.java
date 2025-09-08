package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.item.custom.PulsarCannonItem;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class PulsarRenderer {
    // Optimized pulsar visual properties
    private static final int PULSAR_COLOR_CORE = 0xFFFFFFFF;       // Pure white core
    private static final int PULSAR_COLOR_PRIMARY = 0xFF00DDFF;    // Cyan-magenta primary
    private static final int PULSAR_COLOR_SPLIT = 0xFFFF00AA;      // Purple for splits
    private static final int IMPACT_COLOR_CORE = 0xFFFFFFFF;       // White hot core
    private static final int IMPACT_COLOR_OUTER = 0xFFFF4400;      // Orange explosion
    private static final int QUANTUM_SPARK_COLOR = 0xFFAAFFFF;     // Quantum blue

    // Optimized thickness and lifetime values
    private static final float CORE_THICKNESS = 0.04f;
    private static final float OUTER_THICKNESS = 0.2f;
    private static final float SPLIT_THICKNESS = 0.12f;
    private static final int BASE_LIFETIME = 80;          // 4 seconds
    private static final int IMPACT_LIFETIME = 60;        // 3 seconds
    private static final int SPARK_LIFETIME = 40;         // 2 seconds

    // Performance limits
    private static final int MAX_PARTICLES_PER_SEGMENT = 3;
    private static final int MAX_SPARKS_PER_IMPACT = 8;
    private static final int MAX_RENDER_DISTANCE_SQ = 10000; // 100 blocks squared

    public static void handleOptimizedPulsarAttack(Vec3 startPos, Vec3 direction, int shooterId,
                                                   float baseDamage, int maxBounces, double maxRange,
                                                   List<PulsarCannonItem.OptimizedSegment> preCalculatedSegments) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Level level = mc.level;
        Entity shooter = level.getEntity(shooterId);
        Vec3 playerPos = mc.player.position();

        // Group segments by generation and type for efficient rendering
        Map<Integer, List<PulsarCannonItem.OptimizedSegment>> segmentsByGeneration = new HashMap<>();
        List<PulsarCannonItem.OptimizedSegment> primarySegments = new ArrayList<>();
        List<PulsarCannonItem.OptimizedSegment> splitSegments = new ArrayList<>();

        for (PulsarCannonItem.OptimizedSegment segment : preCalculatedSegments) {
            // Skip segments too far from player for performance
            double distSq = Math.min(
                    segment.start.distanceToSqr(playerPos),
                    segment.end.distanceToSqr(playerPos)
            );
            if (distSq > MAX_RENDER_DISTANCE_SQ) continue;

            segmentsByGeneration.computeIfAbsent(segment.generation, k -> new ArrayList<>()).add(segment);

            if (segment.isSplit) {
                splitSegments.add(segment);
            } else {
                primarySegments.add(segment);
            }
        }

        // Render in order: primary beams, then split beams by generation
        renderOptimizedBeamSegments(primarySegments, false, playerPos);

        for (int generation = 1; generation <= 5; generation++) { // Limit generations rendered
            List<PulsarCannonItem.OptimizedSegment> genSegments = segmentsByGeneration.get(generation);
            if (genSegments != null && !genSegments.isEmpty()) {
                renderOptimizedBeamSegments(genSegments, true, playerPos);
            }
        }

        // Create optimized impact effects
        createOptimizedImpactEffects(level, preCalculatedSegments, playerPos);

        // Play optimized sound effects
        playOptimizedPulsarSounds(level, preCalculatedSegments, playerPos);
    }

    private static void renderOptimizedBeamSegments(List<PulsarCannonItem.OptimizedSegment> segments,
                                                    boolean isSplit, Vec3 playerPos) {
        if (segments.isEmpty()) return;

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            float energyRatio = Mth.clamp(segment.energy / 25.0f, 0.1f, 1.0f);

            // Distance-based LOD
            double avgDistSq = (segment.start.distanceToSqr(playerPos) + segment.end.distanceToSqr(playerPos)) * 0.5;
            boolean isClose = avgDistSq < 625; // 25 blocks squared
            boolean isMedium = avgDistSq < 2500; // 50 blocks squared

            // Core beam - always render
            int coreColor = isSplit ? PULSAR_COLOR_SPLIT : PULSAR_COLOR_CORE;
            float coreThickness = (isSplit ? SPLIT_THICKNESS : CORE_THICKNESS) * energyRatio;

            VectorRenderer.drawLineWorld(
                    segment.start, segment.end, coreColor, coreThickness, false, BASE_LIFETIME, null
            );

            // Outer glow - render based on distance
            if (isMedium) {
                int outerColor = isSplit ? PULSAR_COLOR_SPLIT : PULSAR_COLOR_PRIMARY;
                float outerThickness = OUTER_THICKNESS * energyRatio * (isSplit ? 0.7f : 1.0f);
                int glowColor = (outerColor & 0x00FFFFFF) | (((int)(120 * energyRatio)) << 24);

                VectorRenderer.drawLineWorld(
                        segment.start, segment.end, glowColor, outerThickness, false, BASE_LIFETIME, null
                );
            }

            // Energy particles - only for close segments
            if (isClose && segment.bounceCount < 50) { // Limit particles for high bounce counts
                addOptimizedEnergyParticles(segment, energyRatio, isSplit);
            }

            // Split effect - only at generation start
            if (isSplit && segment.bounceCount == 0 && isClose) {
                createOptimizedSplitEffect(segment.start, energyRatio);
            }
        }
    }

    private static void addOptimizedEnergyParticles(PulsarCannonItem.OptimizedSegment segment,
                                                    float energy, boolean isSplit) {
        Vec3 direction = segment.end.subtract(segment.start);
        double length = direction.length();

        if (length < 1.0) return; // Skip tiny segments

        direction = direction.normalize();
        int particleCount = Math.min(MAX_PARTICLES_PER_SEGMENT, (int)(length * energy));

        for (int i = 0; i < particleCount; i++) {
            double t = (double)i / Math.max(1, particleCount - 1);
            Vec3 particlePos = segment.start.add(direction.scale(length * t));

            // Smaller quantum fluctuation for performance
            double fluctuation = 0.08 * energy;
            particlePos = particlePos.add(
                    (Math.random() - 0.5) * fluctuation,
                    (Math.random() - 0.5) * fluctuation,
                    (Math.random() - 0.5) * fluctuation
            );

            int particleColor = isSplit ? PULSAR_COLOR_SPLIT : QUANTUM_SPARK_COLOR;
            particleColor = (particleColor & 0x00FFFFFF) | (((int)(180 * energy)) << 24);

            VectorRenderer.drawSphereWorld(
                    particlePos, 0.025f * energy, particleColor, 4, 4, false, SPARK_LIFETIME, null
            );
        }
    }

    private static void createOptimizedSplitEffect(Vec3 splitPos, float energy) {
        // Central split orb
        VectorRenderer.drawSphereWorld(
                splitPos, 0.15f * energy, PULSAR_COLOR_SPLIT, 8, 8, false, IMPACT_LIFETIME, null
        );

        // Simple split rings
        for (int ring = 0; ring < 2; ring++) {
            float ringRadius = 0.2f + ring * 0.1f;
            int ringAlpha = (int)(100 * energy) - ring * 30;
            int ringColor = (PULSAR_COLOR_SPLIT & 0x00FFFFFF) | (ringAlpha << 24);

            createSimpleRing(splitPos, ringRadius, ringColor, IMPACT_LIFETIME - ring * 15);
        }
    }

    private static void createOptimizedImpactEffects(Level level,
                                                     List<PulsarCannonItem.OptimizedSegment> segments,
                                                     Vec3 playerPos) {
        if (segments.isEmpty()) return;

        // Find end points and create impacts
        Map<Vec3, Float> impactPoints = new HashMap<>();

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            // Only create impacts for segments that hit blocks or are at the end of a beam
            if (segment.hitBlock || isEndSegment(segment, segments)) {
                Vec3 impactPos = segment.end;

                // Skip distant impacts
                if (impactPos.distanceToSqr(playerPos) > MAX_RENDER_DISTANCE_SQ) continue;

                Float existingEnergy = impactPoints.get(impactPos);
                if (existingEnergy == null || segment.energy > existingEnergy) {
                    impactPoints.put(impactPos, segment.energy);
                }
            }
        }

        // Create impact effects with limited count
        int impactCount = 0;
        for (Map.Entry<Vec3, Float> entry : impactPoints.entrySet()) {
            if (impactCount++ > 20) break; // Limit total impacts

            Vec3 pos = entry.getKey();
            float energy = entry.getValue();
            createOptimizedImpact(pos, energy, pos.distanceToSqr(playerPos) < 625);
        }

        // Add visual entity hits (limited count)
        addOptimizedEntityHitEffects(level, segments, playerPos);
    }

    private static boolean isEndSegment(PulsarCannonItem.OptimizedSegment segment,
                                        List<PulsarCannonItem.OptimizedSegment> allSegments) {
        Vec3 segmentEnd = segment.end;

        // Check if any other segment starts where this one ends
        for (PulsarCannonItem.OptimizedSegment other : allSegments) {
            if (other != segment && other.start.distanceToSqr(segmentEnd) < 0.01) {
                return false; // This segment continues
            }
        }
        return true; // This is an end segment
    }

    private static void createOptimizedImpact(Vec3 impactPos, float energy, boolean isClose) {
        float intensity = Mth.clamp(energy / 25.0f, 0.2f, 1.0f);

        // Core impact
        VectorRenderer.drawSphereWorld(
                impactPos, 0.2f * intensity, IMPACT_COLOR_CORE,
                isClose ? 10 : 6, isClose ? 10 : 6, false, IMPACT_LIFETIME, null
        );

        if (isClose) {
            // Outer shockwave for close impacts only
            VectorRenderer.drawSphereWorld(
                    impactPos, 0.4f * intensity,
                    (IMPACT_COLOR_OUTER & 0x00FFFFFF) | 0x60000000,
                    8, 8, false, IMPACT_LIFETIME, null
            );

            // Energy sparks
            int sparkCount = Math.min(MAX_SPARKS_PER_IMPACT, (int)(8 * intensity));
            for (int i = 0; i < sparkCount; i++) {
                double angle = (i * Math.PI * 2) / sparkCount;
                Vec3 sparkEnd = impactPos.add(
                        Math.cos(angle) * 0.8 * intensity,
                        (Math.random() - 0.5) * 0.4 * intensity,
                        Math.sin(angle) * 0.8 * intensity
                );

                VectorRenderer.drawLineWorld(
                        impactPos, sparkEnd, QUANTUM_SPARK_COLOR, 0.03f,
                        false, SPARK_LIFETIME, null
                );
            }
        }
    }

    private static void addOptimizedEntityHitEffects(Level level,
                                                     List<PulsarCannonItem.OptimizedSegment> segments,
                                                     Vec3 playerPos) {
        int hitEffectCount = 0;

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            if (hitEffectCount > 15) break; // Limit entity hit effects

            AABB boundingBox = new AABB(segment.start, segment.end).inflate(0.5);

            // Skip distant segments
            Vec3 segmentCenter = segment.start.add(segment.end).scale(0.5);
            if (segmentCenter.distanceToSqr(playerPos) > MAX_RENDER_DISTANCE_SQ) continue;

            List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

            for (Entity entity : entities) {
                if (hitEffectCount > 15) break;

                if (simpleEntityIntersection(segment, entity)) {
                    Vec3 hitPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
                    createSimpleEntityHitEffect(hitPos, segment.energy, segment.isSplit);
                    hitEffectCount++;
                }
            }
        }
    }

    private static boolean simpleEntityIntersection(PulsarCannonItem.OptimizedSegment segment, Entity entity) {
        AABB entityBounds = entity.getBoundingBox();
        Vec3 segmentCenter = segment.start.add(segment.end).scale(0.5);
        Vec3 entityCenter = entityBounds.getCenter();

        double segmentHalfLength = segment.start.distanceTo(segment.end) * 0.5;
        double entityRadius = Math.max(entityBounds.getXsize(), entityBounds.getZsize()) * 0.5;

        return segmentCenter.distanceTo(entityCenter) <= (segmentHalfLength + entityRadius + 0.5);
    }

    private static void createSimpleEntityHitEffect(Vec3 hitPos, float energy, boolean isSplit) {
        float intensity = energy / 25.0f;
        int hitColor = isSplit ? PULSAR_COLOR_SPLIT : 0xFFFF0033;

        VectorRenderer.drawSphereWorld(
                hitPos, 0.15f * intensity, hitColor, 6, 6, false, IMPACT_LIFETIME / 2, null
        );
    }

    private static void createSimpleRing(Vec3 center, float radius, int color, int lifetime) {
        int segments = Math.max(8, (int)(radius * 16)); // Fewer segments for performance

        for (int i = 0; i < segments; i++) {
            double angle1 = (i * Math.PI * 2) / segments;
            double angle2 = ((i + 1) * Math.PI * 2) / segments;

            Vec3 point1 = center.add(Math.cos(angle1) * radius, 0, Math.sin(angle1) * radius);
            Vec3 point2 = center.add(Math.cos(angle2) * radius, 0, Math.sin(angle2) * radius);

            VectorRenderer.drawLineWorld(point1, point2, color, 0.03f, false, lifetime, null);
        }
    }

    private static void playOptimizedPulsarSounds(Level level,
                                                  List<PulsarCannonItem.OptimizedSegment> segments,
                                                  Vec3 playerPos) {
        if (segments.isEmpty()) return;

        // Play only the most important sounds to avoid audio spam

        // Find the furthest impact for main sound
        double maxDistanceFromStart = 0;
        Vec3 mainImpactPos = null;

        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            double distFromStart = segment.start.distanceTo(segments.get(0).start);
            if (distFromStart > maxDistanceFromStart) {
                maxDistanceFromStart = distFromStart;
                mainImpactPos = segment.end;
            }
        }

        // Main impact sound
        if (mainImpactPos != null && mainImpactPos.distanceToSqr(playerPos) < MAX_RENDER_DISTANCE_SQ) {
            level.playLocalSound(mainImpactPos.x, mainImpactPos.y, mainImpactPos.z,
                    SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.5f, 0.8f, false);
        }

        // Limited reflection sounds (more for epic bouncing)
        int reflectionSoundCount = 0;
        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            if (reflectionSoundCount >= 8) break; // More reflection sounds
            if (segment.bounceCount > 0 && segment.bounceCount <= 20) { // More early bounces get sounds
                if (segment.start.distanceToSqr(playerPos) < 2500) { // 50 blocks
                    level.playLocalSound(segment.start.x, segment.start.y, segment.start.z,
                            SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS,
                            0.3f, 1.2f + (reflectionSoundCount * 0.1f), false);
                    reflectionSoundCount++;
                }
            }
        }

        // Multiple split sounds for epic splitting
        int splitSoundCount = 0;
        for (PulsarCannonItem.OptimizedSegment segment : segments) {
            if (splitSoundCount >= 4 && segment.isSplit && segment.bounceCount == 0) break; // More split sounds
            if (segment.isSplit && segment.bounceCount == 0) {
                if (segment.start.distanceToSqr(playerPos) < 3600) { // 60 blocks
                    level.playLocalSound(segment.start.x, segment.start.y, segment.start.z,
                            SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS,
                            0.6f, 1.8f + (splitSoundCount * 0.2f), false);
                    splitSoundCount++;
                }
            }
        }
    }
}