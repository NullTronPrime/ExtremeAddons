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
 * Enhanced version with improved visual fidelity and natural lightning patterns
 */
public class ElectricityRenderer {
    private static final Map<Integer, ElectricChain> activeChains = new HashMap<>();
    private static final RandomSource random = RandomSource.create();

    // Enhanced electricity visual parameters
    private static final float MAIN_BOLT_THICKNESS = 0.12f;
    private static final float BRANCH_THICKNESS = 0.06f;
    private static final float GLOW_THICKNESS = 0.25f;

    // Enhanced color scheme for more realistic electricity
    private static final int CORE_COLOR = 0xFFFFFFFF;        // Pure white core
    private static final int INNER_COLOR = 0xFFAADDFF;       // Bright electric blue
    private static final int OUTER_COLOR = 0xFF6699DD;       // Medium blue
    private static final int GLOW_COLOR = 0x44AAFFFF;        // Soft blue glow
    private static final int BRANCH_COLOR = 0xAA88AAFF;      // Dimmer branch color

    // Enhanced animation parameters
    private static final int BOLT_LIFETIME = 12;
    private static final int FLICKER_INTERVAL = 3;
    private static final float MAX_CHAIN_DISTANCE = 6.0f;

    // Improved generation parameters for more natural lightning
    private static final float MIN_SEGMENT_LENGTH = 0.3f;    // Minimum segment size
    private static final float MAX_SEGMENT_LENGTH = 1.2f;    // Maximum segment size
    private static final float BASE_DEVIATION = 0.4f;       // Base randomness strength
    private static final float DEVIATION_DECAY = 0.85f;     // How deviation decreases over distance
    private static final int MIN_SEGMENTS = 4;              // Minimum segments per bolt
    private static final float BRANCH_PROBABILITY = 0.35f;  // Chance per segment to spawn branch
    private static final int MAX_BRANCH_DEPTH = 2;          // Maximum branch recursion

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
                nextFlicker = age + FLICKER_INTERVAL + random.nextInt(3);
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
            // Generate more natural main path
            List<Vec3> mainPath = generateNaturalLightningPath(start, end);

            if (mainPath.size() < 2) return;

            // Draw layered bolt for better visual effect
            drawLayeredBolt(mainPath, chainLevel);

            // Generate organic branches
            generateOrganicBranches(mainPath, 0, chainLevel);
        }

        private List<Vec3> generateNaturalLightningPath(Vec3 start, Vec3 end) {
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

            // Generate path with decreasing deviation and more natural curves
            Vec3 currentDeviation = Vec3.ZERO;

            for (int i = 1; i < segments; i++) {
                double t = (double) i / segments;
                Vec3 basePoint = start.add(direction.scale(totalDistance * t));

                // Calculate deviation that decreases as we approach the target
                double deviationStrength = BASE_DEVIATION * Math.pow(DEVIATION_DECAY, t * 2);

                // Add some continuity to the deviation (lightning tends to curve smoothly)
                double momentum = 0.6; // How much previous deviation affects current
                Vec3 newRandomDeviation = new Vec3(
                        (random.nextDouble() - 0.5) * deviationStrength,
                        (random.nextDouble() - 0.5) * deviationStrength * 0.7, // Less vertical deviation
                        (random.nextDouble() - 0.5) * deviationStrength
                );

                currentDeviation = currentDeviation.scale(momentum).add(newRandomDeviation.scale(1 - momentum));

                // Apply perpendicular deviation
                Vec3 deviatedPoint = basePoint
                        .add(perpendicular1.scale(currentDeviation.x))
                        .add(perpendicular2.scale(currentDeviation.z))
                        .add(0, currentDeviation.y, 0);

                path.add(deviatedPoint);
            }

            path.add(end);
            return path;
        }
        //
        private void drawLayeredBolt(List<Vec3> path, int chainLevel) {
            if (path.size() < 2) return;

            // Adjust thickness and brightness based on chain level
            float thicknessMultiplier = 1.0f / (1.0f + chainLevel * 0.3f);
            float brightnessMultiplier = 1.0f / (1.0f + chainLevel * 0.2f);

            // Draw multiple layers for realistic glow effect

            // Outermost glow layer (very soft and wide)
            int softGlow = adjustColorBrightness(GLOW_COLOR, brightnessMultiplier * 0.3f);
            VectorRenderer.drawPolylineWorld(path, softGlow,
                    GLOW_THICKNESS * thicknessMultiplier * 1.8f, false, BOLT_LIFETIME, null);

            // Middle glow layer
            int mediumGlow = adjustColorBrightness(OUTER_COLOR, brightnessMultiplier * 0.7f);
            VectorRenderer.drawPolylineWorld(path, mediumGlow,
                    GLOW_THICKNESS * thicknessMultiplier, false, BOLT_LIFETIME, null);

            // Main bolt layer
            int mainColor = adjustColorBrightness(INNER_COLOR, brightnessMultiplier);
            VectorRenderer.drawPolylineWorld(path, mainColor,
                    MAIN_BOLT_THICKNESS * thicknessMultiplier, false, BOLT_LIFETIME, null);

            // Core layer (brightest, thinnest)
            int coreColor = adjustColorBrightness(CORE_COLOR, brightnessMultiplier);
            VectorRenderer.drawPolylineWorld(path, coreColor,
                    MAIN_BOLT_THICKNESS * thicknessMultiplier * 0.4f, false, BOLT_LIFETIME, null);
        }

        private void generateOrganicBranches(List<Vec3> mainPath, int depth, int chainLevel) {
            if (depth >= MAX_BRANCH_DEPTH || mainPath.size() < 3) return;

            for (int i = 1; i < mainPath.size() - 1; i++) {
                // Variable branch probability that decreases with depth and chain level
                float branchChance = BRANCH_PROBABILITY * (1.0f / (1.0f + depth * 0.7f + chainLevel * 0.3f));

                if (random.nextFloat() < branchChance) {
                    Vec3 branchStart = mainPath.get(i);

                    // Calculate main direction for reference
                    Vec3 mainDir = mainPath.get(i + 1).subtract(mainPath.get(i - 1)).normalize();

                    // Create branch direction with more natural variation
                    double branchAngle = (random.nextDouble() - 0.5) * Math.PI; // -90 to +90 degrees
                    double branchElevation = (random.nextDouble() - 0.5) * Math.PI * 0.4; // Less vertical spread

                    Vec3 branchDir = new Vec3(
                            Math.cos(branchAngle) * Math.cos(branchElevation),
                            Math.sin(branchElevation),
                            Math.sin(branchAngle) * Math.cos(branchElevation)
                    ).normalize();

                    // Branch length decreases with depth
                    double branchLength = (0.8 + random.nextDouble() * 1.0) * Math.pow(0.7, depth);

                    // Generate shorter branch path
                    Vec3 branchEnd = branchStart.add(branchDir.scale(branchLength));
                    List<Vec3> branchPath = generateNaturalLightningPath(branchStart, branchEnd);

                    if (branchPath.size() >= 2) {
                        // Draw branch with reduced intensity
                        drawBranchBolt(branchPath, depth, chainLevel);

                        // Recursive branching (with lower probability)
                        if (depth < MAX_BRANCH_DEPTH - 1 && random.nextFloat() < 0.3f) {
                            generateOrganicBranches(branchPath, depth + 1, chainLevel);
                        }
                    }
                }
            }
        }

        private void drawBranchBolt(List<Vec3> path, int depth, int chainLevel) {
            if (path.size() < 2) return;

            float depthFade = (float) Math.pow(0.75, depth);
            float chainFade = 1.0f / (1.0f + chainLevel * 0.2f);
            float totalFade = depthFade * chainFade;

            // Branches are thinner and dimmer
            float branchThickness = BRANCH_THICKNESS * totalFade;

            // Draw fewer layers for branches to maintain performance
            int branchGlow = adjustColorBrightness(GLOW_COLOR, totalFade * 0.4f);
            VectorRenderer.drawPolylineWorld(path, branchGlow,
                    branchThickness * 2.0f, false, BOLT_LIFETIME, null);

            int branchMain = adjustColorBrightness(BRANCH_COLOR, totalFade);
            VectorRenderer.drawPolylineWorld(path, branchMain,
                    branchThickness, false, BOLT_LIFETIME, null);
        }

        private int adjustColorBrightness(int color, float multiplier) {
            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            // Apply brightness multiplier while preserving alpha
            r = Math.min(255, (int)(r * multiplier));
            g = Math.min(255, (int)(g * multiplier));
            b = Math.min(255, (int)(b * multiplier));
            a = Math.min(255, (int)(a * Math.min(1.0f, multiplier + 0.2f))); // Slightly less alpha reduction

            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}