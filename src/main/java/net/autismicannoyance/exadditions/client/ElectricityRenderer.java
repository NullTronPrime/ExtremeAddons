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
 * Enhanced version with configurable complexity and smooth visual transitions
 */
public class ElectricityRenderer {
    private static final Map<Integer, ElectricChain> activeChains = new HashMap<>();
    private static final RandomSource random = RandomSource.create();

    // Configurable parameters for insane electricity effects
    public static int MAX_BRANCH_LEVELS = 4;              // Configurable branch depth (primary->secondary->tertiary->quaternary)
    public static float BRANCH_PROBABILITY = 0.6f;        // High chance for branches
    public static float SECONDARY_BRANCH_PROBABILITY = 0.5f;
    public static float TERTIARY_BRANCH_PROBABILITY = 0.35f;
    public static int SEGMENTS_PER_UNIT = 8;              // More segments for smoother curves
    public static boolean ENABLE_EDGE_SMOOTHING = true;   // Smooth vector transitions

    // Enhanced visual parameters for crazy effects
    private static final float PRIMARY_THICKNESS = 0.16f;
    private static final float SECONDARY_THICKNESS = 0.12f;
    private static final float TERTIARY_THICKNESS = 0.08f;
    private static final float QUATERNARY_THICKNESS = 0.05f;
    private static final float GLOW_MULTIPLIER = 3.5f;

    // Enhanced color scheme with more dramatic effects
    private static final int CORE_COLOR = 0xFFFFFFFF;        // Pure white core
    private static final int PRIMARY_COLOR = 0xFFAADDFF;     // Bright electric blue
    private static final int SECONDARY_COLOR = 0xFF88BBEE;   // Medium blue
    private static final int TERTIARY_COLOR = 0xFF6699DD;    // Dimmer blue
    private static final int QUATERNARY_COLOR = 0xFF4477BB;  // Faint blue
    private static final int GLOW_COLOR = 0x66AAFFFF;        // Stronger glow

    // Enhanced animation parameters for longer, more dramatic effects
    private static final int BOLT_LIFETIME = 20;            // Longer lasting
    private static final int FLICKER_INTERVAL = 2;          // More frequent updates
    private static final float MAX_CHAIN_DISTANCE = 8.0f;   // Longer chains

    // Crazy generation parameters
    private static final float MIN_SEGMENT_LENGTH = 0.15f;   // Smaller segments for detail
    private static final float MAX_SEGMENT_LENGTH = 0.6f;    // Still detailed but not too small
    private static final float BASE_DEVIATION = 0.5f;       // More chaotic
    private static final float DEVIATION_DECAY = 0.9f;      // Less decay = more chaos throughout
    private static final int MIN_SEGMENTS_PER_BOLT = 8;     // More minimum segments
    private static final float SIZE_DECAY_RATE = 0.8f;      // Slower size decrease

    // Edge smoothing parameters
    private static final float SMOOTHING_FACTOR = 0.3f;     // How much to smooth transitions
    private static final int SMOOTHING_ITERATIONS = 2;      // Multiple smoothing passes

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

    /**
     * Configure the complexity of electricity effects
     */
    public static void setComplexityLevel(int level) {
        switch (level) {
            case 1: // Simple
                MAX_BRANCH_LEVELS = 2;
                BRANCH_PROBABILITY = 0.3f;
                SEGMENTS_PER_UNIT = 4;
                break;
            case 2: // Normal
                MAX_BRANCH_LEVELS = 3;
                BRANCH_PROBABILITY = 0.5f;
                SEGMENTS_PER_UNIT = 6;
                break;
            case 3: // Crazy (default)
                MAX_BRANCH_LEVELS = 4;
                BRANCH_PROBABILITY = 0.6f;
                SEGMENTS_PER_UNIT = 8;
                break;
            case 4: // INSANE
                MAX_BRANCH_LEVELS = 5;
                BRANCH_PROBABILITY = 0.8f;
                SEGMENTS_PER_UNIT = 12;
                break;
        }
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

            // Primary bolts from source to each target
            for (LivingEntity target : targets) {
                if (!target.isAlive()) continue;

                Vec3 targetPos = getEntityCenter(target);
                generateCrazyLightningBolt(sourcePos, targetPos, 0);
            }

            // Chain bolts between targets (also primary level)
            for (int i = 0; i < targets.size(); i++) {
                for (int j = i + 1; j < targets.size(); j++) {
                    LivingEntity target1 = targets.get(i);
                    LivingEntity target2 = targets.get(j);

                    if (!target1.isAlive() || !target2.isAlive()) continue;

                    if (target1.distanceTo(target2) <= MAX_CHAIN_DISTANCE * 0.8f) {
                        Vec3 pos1 = getEntityCenter(target1);
                        Vec3 pos2 = getEntityCenter(target2);
                        generateCrazyLightningBolt(pos1, pos2, 0);
                    }
                }
            }
        }

        private Vec3 getEntityCenter(Entity entity) {
            return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        }

        private void generateCrazyLightningBolt(Vec3 start, Vec3 end, int level) {
            // Generate highly detailed main path
            List<Vec3> mainPath = generateDetailedLightningPath(start, end, level);

            if (mainPath.size() < 2) return;

            // Apply edge smoothing if enabled
            if (ENABLE_EDGE_SMOOTHING) {
                mainPath = applySmoothingPasses(mainPath);
            }

            // Draw layered bolt with level-appropriate thickness
            drawLeveledBolt(mainPath, level);

            // Generate crazy amount of branches
            generateCrazyBranches(mainPath, level);
        }

        private List<Vec3> generateDetailedLightningPath(Vec3 start, Vec3 end, int level) {
            List<Vec3> path = new ArrayList<>();
            path.add(start);

            Vec3 direction = end.subtract(start);
            double totalDistance = direction.length();

            if (totalDistance < 0.1) {
                path.add(end);
                return path;
            }

            direction = direction.normalize();

            // Much more segments based on distance and detail level
            int baseSegments = (int)(totalDistance * SEGMENTS_PER_UNIT);
            int segments = Math.max(MIN_SEGMENTS_PER_BOLT, baseSegments + random.nextInt(baseSegments / 2 + 1));

            // Create perpendicular vectors for deviation
            Vec3 perpendicular1 = direction.cross(new Vec3(0, 1, 0));
            if (perpendicular1.length() < 0.1) {
                perpendicular1 = direction.cross(new Vec3(1, 0, 0));
            }
            perpendicular1 = perpendicular1.normalize();
            Vec3 perpendicular2 = direction.cross(perpendicular1).normalize();

            // Generate chaotic path with level-based deviation
            Vec3 currentDeviation = Vec3.ZERO;
            float levelDeviation = BASE_DEVIATION * (float)Math.pow(SIZE_DECAY_RATE, level);

            for (int i = 1; i < segments; i++) {
                double t = (double) i / segments;
                Vec3 basePoint = start.add(direction.scale(totalDistance * t));

                // More chaos, less decay
                double deviationStrength = levelDeviation * Math.pow(DEVIATION_DECAY, t);

                // Higher momentum for smoother curves
                double momentum = 0.7 + level * 0.05; // Slightly more momentum at higher levels
                Vec3 newRandomDeviation = new Vec3(
                        (random.nextDouble() - 0.5) * deviationStrength * 2,
                        (random.nextDouble() - 0.5) * deviationStrength * 1.2, // More vertical variation
                        (random.nextDouble() - 0.5) * deviationStrength * 2
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

        private List<Vec3> applySmoothingPasses(List<Vec3> originalPath) {
            if (originalPath.size() < 3) return originalPath;

            List<Vec3> smoothed = new ArrayList<>(originalPath);

            // Multiple smoothing iterations
            for (int iteration = 0; iteration < SMOOTHING_ITERATIONS; iteration++) {
                List<Vec3> newPath = new ArrayList<>();
                newPath.add(smoothed.get(0)); // Keep start point

                // Smooth intermediate points
                for (int i = 1; i < smoothed.size() - 1; i++) {
                    Vec3 prev = smoothed.get(i - 1);
                    Vec3 current = smoothed.get(i);
                    Vec3 next = smoothed.get(i + 1);

                    // Weighted average for smoothing
                    Vec3 smoothPoint = current.scale(1.0f - SMOOTHING_FACTOR)
                            .add(prev.add(next).scale(SMOOTHING_FACTOR * 0.5));

                    newPath.add(smoothPoint);
                }

                newPath.add(smoothed.get(smoothed.size() - 1)); // Keep end point
                smoothed = newPath;
            }

            return smoothed;
        }

        private void drawLeveledBolt(List<Vec3> path, int level) {
            if (path.size() < 2) return;

            float[] thicknesses = {PRIMARY_THICKNESS, SECONDARY_THICKNESS, TERTIARY_THICKNESS, QUATERNARY_THICKNESS};
            int[] colors = {PRIMARY_COLOR, SECONDARY_COLOR, TERTIARY_COLOR, QUATERNARY_COLOR};

            float thickness = level < thicknesses.length ? thicknesses[level] : QUATERNARY_THICKNESS * 0.7f;
            int mainColor = level < colors.length ? colors[level] : QUATERNARY_COLOR;

            // Size reduction based on level
            float levelMultiplier = (float)Math.pow(SIZE_DECAY_RATE, level);
            thickness *= levelMultiplier;

            // Draw multiple layers for each level

            // Outer glow (widest, most transparent)
            int softGlow = adjustColorBrightness(GLOW_COLOR, levelMultiplier * 0.4f);
            VectorRenderer.drawPolylineWorld(path, softGlow,
                    thickness * GLOW_MULTIPLIER * 2.0f, false, BOLT_LIFETIME, null);

            // Medium glow layer
            int mediumGlow = adjustColorBrightness(mainColor, levelMultiplier * 0.6f);
            VectorRenderer.drawPolylineWorld(path, mediumGlow,
                    thickness * GLOW_MULTIPLIER, false, BOLT_LIFETIME, null);

            // Main bolt layer
            int fullColor = adjustColorBrightness(mainColor, levelMultiplier * 0.9f);
            VectorRenderer.drawPolylineWorld(path, fullColor,
                    thickness, false, BOLT_LIFETIME, null);

            // Core layer (only for primary and secondary bolts)
            if (level <= 1) {
                int coreColor = adjustColorBrightness(CORE_COLOR, levelMultiplier);
                VectorRenderer.drawPolylineWorld(path, coreColor,
                        thickness * 0.3f, false, BOLT_LIFETIME, null);
            }
        }

        private void generateCrazyBranches(List<Vec3> mainPath, int level) {
            if (level >= MAX_BRANCH_LEVELS || mainPath.size() < 3) return;

            // Determine branch probability based on level
            float currentBranchProb = BRANCH_PROBABILITY;
            if (level == 1) currentBranchProb = SECONDARY_BRANCH_PROBABILITY;
            else if (level >= 2) currentBranchProb = TERTIARY_BRANCH_PROBABILITY * (float)Math.pow(0.8, level - 2);

            // Generate MANY more branches
            for (int i = 2; i < mainPath.size() - 2; i += 1) { // Check every point for branches
                if (random.nextFloat() < currentBranchProb) {
                    Vec3 branchStart = mainPath.get(i);

                    // Generate multiple branches from this point (2-4 branches)
                    int numBranches = 1 + random.nextInt(level == 0 ? 3 : 2); // More branches on primary

                    for (int b = 0; b < numBranches; b++) {
                        // Calculate more varied branch directions
                        Vec3 mainDir = mainPath.get(i + 1).subtract(mainPath.get(i - 1)).normalize();

                        // Create more extreme branch angles
                        double branchAngle = random.nextDouble() * Math.PI * 2; // Full 360 degrees
                        double branchElevation = (random.nextDouble() - 0.5) * Math.PI * 0.6; // More elevation

                        Vec3 branchDir = new Vec3(
                                Math.cos(branchAngle) * Math.cos(branchElevation),
                                Math.sin(branchElevation),
                                Math.sin(branchAngle) * Math.cos(branchElevation)
                        ).normalize();

                        // Longer branches that decay less aggressively
                        double branchLength = (1.2 + random.nextDouble() * 1.8) * Math.pow(SIZE_DECAY_RATE, level * 0.7);

                        // Generate detailed branch path
                        Vec3 branchEnd = branchStart.add(branchDir.scale(branchLength));
                        List<Vec3> branchPath = generateDetailedLightningPath(branchStart, branchEnd, level + 1);

                        if (branchPath.size() >= 2) {
                            // Apply smoothing to branches too
                            if (ENABLE_EDGE_SMOOTHING && branchPath.size() > 2) {
                                branchPath = applySmoothingPasses(branchPath);
                            }

                            // Draw branch
                            drawLeveledBolt(branchPath, level + 1);

                            // Recursive branching (more aggressive)
                            if (level < MAX_BRANCH_LEVELS - 1) {
                                generateCrazyBranches(branchPath, level + 1);
                            }
                        }
                    }
                }
            }
        }

        private int adjustColorBrightness(int color, float multiplier) {
            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            // Apply brightness multiplier
            r = Math.min(255, Math.max(0, (int)(r * multiplier)));
            g = Math.min(255, Math.max(0, (int)(g * multiplier)));
            b = Math.min(255, Math.max(0, (int)(b * multiplier)));
            a = Math.min(255, Math.max(16, (int)(a * Math.min(1.2f, multiplier + 0.3f)))); // Keep some minimum alpha

            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}