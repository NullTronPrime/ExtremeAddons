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
 */
public class ElectricityRenderer {
    private static final Map<Integer, ElectricChain> activeChains = new HashMap<>();
    private static final RandomSource random = RandomSource.create();

    // Electricity visual parameters
    private static final float MAIN_BOLT_THICKNESS = 0.08f;
    private static final float BRANCH_THICKNESS = 0.04f;
    private static final int MAIN_BOLT_COLOR = 0xFFAADDFF; // Bright electric blue
    private static final int BRANCH_COLOR = 0x88AADDFF; // Semi-transparent blue
    private static final int CORE_COLOR = 0xFFFFFFFF; // White core

    // Animation parameters
    private static final int BOLT_LIFETIME = 8; // ticks
    private static final int FLICKER_INTERVAL = 2; // ticks between flickers
    private static final float MAX_CHAIN_DISTANCE = 6.0f;
    private static final int MAX_SEGMENT_LENGTH = 2; // blocks per segment
    private static final float DEVIATION_STRENGTH = 0.3f; // How erratic the lightning is
    private static final int BRANCHES_PER_BOLT = 3; // Number of side branches

    /**
     * Creates a chained electricity effect between entities
     */
    public static void createElectricityChain(Level level, Entity source, List<Entity> targets, int duration) {
        if (targets.isEmpty()) return;

        int chainId = source.getId() + (int)(level.getGameTime() * 31); // Unique chain ID
        List<LivingEntity> validTargets = new ArrayList<>();

        // Filter for living entities within range
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

    /**
     * Updates all active electricity chains
     */
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

    /**
     * Clears all electricity effects
     */
    public static void clearAll() {
        activeChains.clear();
    }

    /**
     * Represents a chain of electricity between entities
     */
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

            // Regenerate bolts periodically for flickering effect
            if (age >= nextFlicker) {
                generateBolts(source.level());
                nextFlicker = age + FLICKER_INTERVAL + random.nextInt(2);
            }

            return age >= duration;
        }

        public void generateBolts(Level level) {
            // Generate main bolts between source and each target
            Vec3 sourcePos = getEntityCenter(source);

            for (LivingEntity target : targets) {
                if (!target.isAlive()) continue;

                Vec3 targetPos = getEntityCenter(target);
                generateLightningBolt(sourcePos, targetPos);
            }

            // Generate inter-target bolts for chaining effect
            for (int i = 0; i < targets.size(); i++) {
                for (int j = i + 1; j < targets.size(); j++) {
                    LivingEntity target1 = targets.get(i);
                    LivingEntity target2 = targets.get(j);

                    if (target1.distanceTo(target2) <= MAX_CHAIN_DISTANCE) {
                        Vec3 pos1 = getEntityCenter(target1);
                        Vec3 pos2 = getEntityCenter(target2);
                        generateLightningBolt(pos1, pos2);
                    }
                }
            }
        }

        private Vec3 getEntityCenter(Entity entity) {
            return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        }

        private void generateLightningBolt(Vec3 start, Vec3 end) {
            // Create the main zigzag bolt
            List<Vec3> mainPath = generateZigzagPath(start, end);

            // Draw the main bolt with multiple layers for glow effect
            drawBoltLayers(mainPath);

            // Generate side branches
            generateBranches(mainPath);
        }

        private List<Vec3> generateZigzagPath(Vec3 start, Vec3 end) {
            List<Vec3> path = new ArrayList<>();
            path.add(start);

            Vec3 direction = end.subtract(start);
            double totalDistance = direction.length();
            direction = direction.normalize();

            // Calculate number of segments based on distance
            int segments = Math.max(1, (int)(totalDistance / MAX_SEGMENT_LENGTH));

            for (int i = 1; i < segments; i++) {
                double t = (double) i / segments;
                Vec3 basePoint = start.add(direction.scale(totalDistance * t));

                // Add random deviation perpendicular to the main direction
                Vec3 perpendicular1 = direction.cross(new Vec3(0, 1, 0));
                if (perpendicular1.length() < 0.1) {
                    perpendicular1 = direction.cross(new Vec3(1, 0, 0));
                }
                perpendicular1 = perpendicular1.normalize();
                Vec3 perpendicular2 = direction.cross(perpendicular1).normalize();

                double deviation1 = (random.nextDouble() - 0.5) * DEVIATION_STRENGTH * 2;
                double deviation2 = (random.nextDouble() - 0.5) * DEVIATION_STRENGTH * 2;
                double verticalDev = (random.nextDouble() - 0.5) * DEVIATION_STRENGTH;

                Vec3 deviatedPoint = basePoint
                        .add(perpendicular1.scale(deviation1))
                        .add(perpendicular2.scale(deviation2))
                        .add(0, verticalDev, 0);

                path.add(deviatedPoint);
            }

            path.add(end);
            return path;
        }

        private void drawBoltLayers(List<Vec3> path) {
            // Draw outer glow (thick, transparent)
            VectorRenderer.drawPolylineWorld(path, 0x44AADDFF, MAIN_BOLT_THICKNESS * 3, false, BOLT_LIFETIME, null);

            // Draw main bolt (medium thickness, bright)
            VectorRenderer.drawPolylineWorld(path, MAIN_BOLT_COLOR, MAIN_BOLT_THICKNESS, false, BOLT_LIFETIME, null);

            // Draw inner core (thin, white hot)
            VectorRenderer.drawPolylineWorld(path, CORE_COLOR, MAIN_BOLT_THICKNESS * 0.3f, false, BOLT_LIFETIME, null);
        }

        private void generateBranches(List<Vec3> mainPath) {
            if (mainPath.size() < 2) return;

            for (int i = 1; i < mainPath.size() - 1; i++) {
                // Chance to spawn a branch at each segment
                if (random.nextFloat() < 0.4f) { // 40% chance per segment
                    Vec3 branchStart = mainPath.get(i);

                    // Generate random branch direction
                    Vec3 mainDir = mainPath.get(i + 1).subtract(mainPath.get(i - 1)).normalize();
                    Vec3 perpendicular = mainDir.cross(new Vec3(0, 1, 0));
                    if (perpendicular.length() < 0.1) {
                        perpendicular = mainDir.cross(new Vec3(1, 0, 0));
                    }
                    perpendicular = perpendicular.normalize();

                    // Rotate perpendicular randomly
                    double angle = random.nextDouble() * Math.PI * 2;
                    Vec3 branchDir = new Vec3(
                            perpendicular.x * Math.cos(angle) + mainDir.x * Math.sin(angle),
                            perpendicular.y * Math.cos(angle) + mainDir.y * Math.sin(angle) + (random.nextDouble() - 0.5) * 0.5,
                            perpendicular.z * Math.cos(angle) + mainDir.z * Math.sin(angle)
                    ).normalize();

                    // Generate branch path
                    double branchLength = 0.5 + random.nextDouble() * 1.5; // 0.5-2 blocks
                    Vec3 branchEnd = branchStart.add(branchDir.scale(branchLength));

                    List<Vec3> branchPath = new ArrayList<>();
                    branchPath.add(branchStart);

                    // Add 1-2 intermediate points for the branch
                    int branchSegments = 1 + random.nextInt(2);
                    for (int j = 1; j < branchSegments + 1; j++) {
                        double t = (double) j / (branchSegments + 1);
                        Vec3 point = branchStart.add(branchDir.scale(branchLength * t));

                        // Add some randomness to branch segments
                        point = point.add(
                                (random.nextDouble() - 0.5) * 0.2,
                                (random.nextDouble() - 0.5) * 0.2,
                                (random.nextDouble() - 0.5) * 0.2
                        );

                        branchPath.add(point);
                    }

                    branchPath.add(branchEnd);

                    // Draw the branch (thinner and more transparent)
                    VectorRenderer.drawPolylineWorld(branchPath, BRANCH_COLOR, BRANCH_THICKNESS, false, BOLT_LIFETIME, null);
                }
            }
        }
    }
}