package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Handles realistic electricity/lightning rendering with chaining effects
 * Enhanced version with authentic white/yellow lightning and multi-level branching
 */
public class ElectricityRenderer {
    private static final Map<Integer, ElectricChain> activeChains = new HashMap<>();
    private static final RandomSource random = RandomSource.create();

    // Enhanced electricity visual parameters
    private static final float MAIN_BOLT_THICKNESS = 0.15f;
    private static final float PRIMARY_BRANCH_THICKNESS = 0.08f;
    private static final float SECONDARY_BRANCH_THICKNESS = 0.04f;
    private static final float TERTIARY_BRANCH_THICKNESS = 0.02f;
    private static final float GLOW_THICKNESS = 0.35f;

    // Realistic white/yellow lightning color scheme
    private static final int CORE_COLOR = 0xFFFFFFF0;          // Pure white with slight yellow tint
    private static final int INNER_COLOR = 0xFFFFFFDD;         // Bright warm white
    private static final int MIDDLE_COLOR = 0xFFFFFF88;        // Light yellow-white
    private static final int OUTER_COLOR = 0xFFFFDD44;         // Soft yellow glow
    private static final int GLOW_COLOR = 0x44FFEE88;          // Very soft yellow glow

    // Branch colors (dimmer versions)
    private static final int PRIMARY_BRANCH_COLOR = 0xFFFFFFCC;    // Slightly dimmer white
    private static final int SECONDARY_BRANCH_COLOR = 0xFFFFDD99;  // Warm white-yellow
    private static final int TERTIARY_BRANCH_COLOR = 0xFFDDDD66;   // Dimmer yellow-white

    // Enhanced animation parameters
    private static final int BOLT_LIFETIME = 15;
    private static final int FLICKER_INTERVAL = 2;
    private static final float MAX_CHAIN_DISTANCE = 6.0f;

    // Improved generation parameters for more natural lightning
    private static final float MIN_SEGMENT_LENGTH = 0.25f;
    private static final float MAX_SEGMENT_LENGTH = 1.0f;
    private static final float BASE_DEVIATION = 0.5f;
    private static final float DEVIATION_DECAY = 0.8f;
    private static final int MIN_SEGMENTS = 5;

    // Multi-level branching parameters
    private static final float PRIMARY_BRANCH_PROBABILITY = 0.45f;    // Higher chance for main branches
    private static final float SECONDARY_BRANCH_PROBABILITY = 0.35f;  // Medium chance for sub-branches
    private static final float TERTIARY_BRANCH_PROBABILITY = 0.25f;   // Lower chance for minor branches
    private static final int MAX_BRANCH_DEPTH = 3;                    // Three levels: primary, secondary, tertiary

    /**
     * Creates a chained electricity effect between entities
     */
    public static void createElectricityChain(Level level, Entity source, List<Entity> targets, int duration) {
        if (targets.isEmpty()) return;

        int chainId = source.getId() + (int)(level.getGameTime() * 31);
        List<LivingEntity> validTargets = new ArrayList<>();

        for (Entity target : targets) {
            if (target instanceof LivingEntity living &&
                    source.distanceTo(target) <= MAX_CHAIN_DISTANCE) {
                validTargets.add(living);
            }
        }

        if (!validTargets.isEmpty()) {
            ElectricChain chain = new ElectricChain(source, validTargets, duration);
            activeChains.put(chainId, chain);
            chain.generateBolts(level);
        }
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, ElectricChain>> iterator = activeChains.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ElectricChain> entry = iterator.next();
            ElectricChain chain = entry.getValue();

            if (chain.tick()) {
                iterator.remove();
            }
        }
    }

    public static void clearAll() {
        activeChains.clear();
    }

    private static class ElectricChain {
        private final Entity source;
        private final List<LivingEntity> targets;
        private int duration;
        private int age = 0;
        private int nextFlicker = 0;

        public ElectricChain(Entity source, List<LivingEntity> targets, int duration) {
            this.source = source;
            this.targets = new ArrayList<>(targets);
            this.duration = duration;
        }

        public boolean tick() {
            age++;

            if (age >= nextFlicker) {
                generateBolts(source.level());
                nextFlicker = age + FLICKER_INTERVAL + random.nextInt(2);
            }

            return age >= duration;
        }

        public void generateBolts(Level level) {
            Vec3 sourcePos = getEntityCenter(source);

            // Main bolts from source to each target
            for (LivingEntity target : targets) {
                if (!target.isAlive()) continue;

                Vec3 targetPos = getEntityCenter(target);
                generateAdvancedLightningBolt(sourcePos, targetPos, 0);
            }

            // Chain bolts between targets
            for (int i = 0; i < targets.size(); i++) {
                for (int j = i + 1; j < targets.size(); j++) {
                    LivingEntity target1 = targets.get(i);
                    LivingEntity target2 = targets.get(j);

                    if (!target1.isAlive() || !target2.isAlive()) continue;

                    if (target1.distanceTo(target2) <= MAX_CHAIN_DISTANCE) {
                        Vec3 pos1 = getEntityCenter(target1);
                        Vec3 pos2 = getEntityCenter(target2);
                        // Chain bolts are slightly dimmer and thinner
                        generateAdvancedLightningBolt(pos1, pos2, 1);
                    }
                }
            }
        }

        private Vec3 getEntityCenter(Entity entity) {
            return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        }

        private void generateAdvancedLightningBolt(Vec3 start, Vec3 end, int chainLevel) {
            // Generate more natural main path with smoother curves
            List<Vec3> mainPath = generateSmoothLightningPath(start, end);

            if (mainPath.size() < 2) return;

            // Draw layered bolt for better visual effect
            drawLayeredBolt(mainPath, chainLevel);

            // Generate multi-level branching system
            generateMultiLevelBranches(mainPath, 0, chainLevel);
        }

        private List<Vec3> generateSmoothLightningPath(Vec3 start, Vec3 end) {
            List<Vec3> path = new ArrayList<>();
            path.add(start);

            Vec3 direction = end.subtract(start);
            double totalDistance = direction.length();

            if (totalDistance < 0.1) {
                path.add(end);
                return path;
            }

            direction = direction.normalize();

            // Calculate adaptive segment count based on distance
            float segmentLength = MIN_SEGMENT_LENGTH + random.nextFloat() * (MAX_SEGMENT_LENGTH - MIN_SEGMENT_LENGTH);
            int segments = Math.max(MIN_SEGMENTS, (int)(totalDistance / segmentLength));

            // Create perpendicular vectors for deviation
            Vec3 perpendicular1 = direction.cross(new Vec3(0, 1, 0));
            if (perpendicular1.length() < 0.1) {
                perpendicular1 = direction.cross(new Vec3(1, 0, 0));
            }
            perpendicular1 = perpendicular1.normalize();
            Vec3 perpendicular2 = direction.cross(perpendicular1).normalize();

            // Generate path with smooth curves and decreasing deviation
            Vec3 currentDeviation = Vec3.ZERO;
            Vec3 previousPoint = start;

            for (int i = 1; i < segments; i++) {
                double t = (double) i / segments;
                Vec3 basePoint = start.add(direction.scale(totalDistance * t));

                // Smooth deviation that decreases towards target
                double deviationStrength = BASE_DEVIATION * Math.pow(DEVIATION_DECAY, t * 1.5);

                // Add momentum to create smoother, more natural curves
                double momentum = 0.7; // Higher momentum for smoother curves
                Vec3 newRandomDeviation = new Vec3(
                        (random.nextGaussian()) * deviationStrength * 0.8,
                        (random.nextGaussian()) * deviationStrength * 0.5, // Less vertical deviation
                        (random.nextGaussian()) * deviationStrength * 0.8
                );

                currentDeviation = currentDeviation.scale(momentum).add(newRandomDeviation.scale(1 - momentum));

                // Apply perpendicular deviation with smoothing
                Vec3 deviatedPoint = basePoint
                        .add(perpendicular1.scale(currentDeviation.x))
                        .add(perpendicular2.scale(currentDeviation.z))
                        .add(0, currentDeviation.y, 0);

                // Additional smoothing pass to reduce sharp angles
                if (path.size() > 1) {
                    Vec3 smoothingVector = previousPoint.subtract(path.get(path.size() - 2));
                    if (smoothingVector.length() > 0) {
                        smoothingVector = smoothingVector.normalize().scale(0.1 * deviationStrength);
                        deviatedPoint = deviatedPoint.add(smoothingVector);
                    }
                }

                path.add(deviatedPoint);
                previousPoint = deviatedPoint;
            }

            path.add(end);
            return path;
        }

        private void drawLayeredBolt(List<Vec3> path, int chainLevel) {
            if (path.size() < 2) return;

            // Adjust thickness and brightness based on chain level
            float thicknessMultiplier = 1.0f / (1.0f + chainLevel * 0.25f);
            float brightnessMultiplier = 1.0f / (1.0f + chainLevel * 0.15f);

            // Draw multiple layers for realistic glow effect with proper white/yellow colors

            // Outermost glow layer (very soft and wide)
            int softGlow = adjustColorBrightness(GLOW_COLOR, brightnessMultiplier * 0.4f);
            VectorRenderer.drawPolylineWorld(path, softGlow,
                    GLOW_THICKNESS * thicknessMultiplier * 2.2f, false, BOLT_LIFETIME, null);

            // Outer glow layer
            int outerGlow = adjustColorBrightness(OUTER_COLOR, brightnessMultiplier * 0.7f);
            VectorRenderer.drawPolylineWorld(path, outerGlow,
                    GLOW_THICKNESS * thicknessMultiplier * 1.5f, false, BOLT_LIFETIME, null);

            // Middle layer
            int middleColor = adjustColorBrightness(MIDDLE_COLOR, brightnessMultiplier * 0.85f);
            VectorRenderer.drawPolylineWorld(path, middleColor,
                    MAIN_BOLT_THICKNESS * thicknessMultiplier * 1.8f, false, BOLT_LIFETIME, null);

            // Inner layer
            int innerColor = adjustColorBrightness(INNER_COLOR, brightnessMultiplier * 0.95f);
            VectorRenderer.drawPolylineWorld(path, innerColor,
                    MAIN_BOLT_THICKNESS * thicknessMultiplier * 1.2f, false, BOLT_LIFETIME, null);

            // Core layer (brightest, thinnest)
            int coreColor = adjustColorBrightness(CORE_COLOR, brightnessMultiplier);
            VectorRenderer.drawPolylineWorld(path, coreColor,
                    MAIN_BOLT_THICKNESS * thicknessMultiplier * 0.6f, false, BOLT_LIFETIME, null);
        }

        private void generateMultiLevelBranches(List<Vec3> mainPath, int depth, int chainLevel) {
            if (depth >= MAX_BRANCH_DEPTH || mainPath.size() < 3) return;

            // Determine branch probability and color based on depth
            float branchProbability;
            int branchColor;
            float branchThickness;

            switch (depth) {
                case 0: // Primary branches
                    branchProbability = PRIMARY_BRANCH_PROBABILITY;
                    branchColor = PRIMARY_BRANCH_COLOR;
                    branchThickness = PRIMARY_BRANCH_THICKNESS;
                    break;
                case 1: // Secondary branches
                    branchProbability = SECONDARY_BRANCH_PROBABILITY;
                    branchColor = SECONDARY_BRANCH_COLOR;
                    branchThickness = SECONDARY_BRANCH_THICKNESS;
                    break;
                default: // Tertiary branches
                    branchProbability = TERTIARY_BRANCH_PROBABILITY;
                    branchColor = TERTIARY_BRANCH_COLOR;
                    branchThickness = TERTIARY_BRANCH_THICKNESS;
                    break;
            }

            // Reduce probability for chain level and depth
            branchProbability *= (1.0f / (1.0f + depth * 0.4f + chainLevel * 0.2f));

            for (int i = 1; i < mainPath.size() - 1; i++) {
                if (random.nextFloat() < branchProbability) {
                    Vec3 branchStart = mainPath.get(i);

                    // Calculate main direction for reference
                    Vec3 mainDir = mainPath.get(i + 1).subtract(mainPath.get(i - 1)).normalize();

                    // Create branch direction with more natural variation
                    double branchAngle = (random.nextGaussian() * 0.3) * Math.PI; // Gaussian distribution for more natural angles
                    double branchElevation = (random.nextGaussian() * 0.15) * Math.PI;

                    Vec3 branchDir = new Vec3(
                            Math.cos(branchAngle) * Math.cos(branchElevation),
                            Math.sin(branchElevation),
                            Math.sin(branchAngle) * Math.cos(branchElevation)
                    ).normalize();

                    // Branch length decreases with depth, but varies more naturally
                    double baseBranchLength = 0.6 + random.nextGaussian() * 0.3;
                    double branchLength = Math.abs(baseBranchLength) * Math.pow(0.65, depth);
                    branchLength = Math.max(0.2, Math.min(2.0, branchLength)); // Clamp to reasonable range

                    // Generate smoother branch path
                    Vec3 branchEnd = branchStart.add(branchDir.scale(branchLength));
                    List<Vec3> branchPath = generateSmoothLightningPath(branchStart, branchEnd);

                    if (branchPath.size() >= 2) {
                        // Draw branch with appropriate style for its level
                        drawBranchBolt(branchPath, depth, chainLevel, branchColor, branchThickness);

                        // Recursive branching with decreasing probability
                        if (depth < MAX_BRANCH_DEPTH - 1 && random.nextFloat() < 0.4f) {
                            generateMultiLevelBranches(branchPath, depth + 1, chainLevel);
                        }
                    }
                }
            }
        }

        private void drawBranchBolt(List<Vec3> path, int depth, int chainLevel, int baseColor, float thickness) {
            if (path.size() < 2) return;

            float depthFade = (float) Math.pow(0.8, depth);
            float chainFade = 1.0f / (1.0f + chainLevel * 0.15f);
            float totalFade = depthFade * chainFade;

            // Branches get progressively thinner and dimmer
            float branchThickness = thickness * totalFade;

            // Draw fewer layers for branches to maintain performance while keeping them visible
            if (depth == 0) {
                // Primary branches get two layers
                int branchGlow = adjustColorBrightness(GLOW_COLOR, totalFade * 0.5f);
                VectorRenderer.drawPolylineWorld(path, branchGlow,
                        branchThickness * 2.5f, false, BOLT_LIFETIME, null);

                int branchMain = adjustColorBrightness(baseColor, totalFade);
                VectorRenderer.drawPolylineWorld(path, branchMain,
                        branchThickness, false, BOLT_LIFETIME, null);
            } else {
                // Secondary and tertiary branches get single layer but remain visible
                int branchMain = adjustColorBrightness(baseColor, totalFade * 0.9f);
                VectorRenderer.drawPolylineWorld(path, branchMain,
                        branchThickness, false, BOLT_LIFETIME, null);
            }
        }

        private int adjustColorBrightness(int color, float multiplier) {
            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            // Apply brightness multiplier while preserving the white/yellow tint
            r = Math.min(255, Math.max(0, (int)(r * multiplier)));
            g = Math.min(255, Math.max(0, (int)(g * multiplier)));
            b = Math.min(255, Math.max(0, (int)(b * multiplier)));
            a = Math.min(255, Math.max(0, (int)(a * Math.min(1.2f, multiplier + 0.3f)))); // Keep alpha more visible

            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}