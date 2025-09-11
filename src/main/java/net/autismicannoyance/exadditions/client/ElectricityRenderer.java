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
 * Enhanced version with proper storm cloud support for Forge 1.20.1
 * Updated to properly handle sequential chaining from cloud to mob to mob
 * Optimized for better performance and reduced lag
 */
public class ElectricityRenderer {
    private static final Map<Integer, ElectricChain> activeChains = new HashMap<>();
    private static final Map<Integer, StormCloud> activeStormClouds = new HashMap<>();
    private static final RandomSource random = RandomSource.create();

    // Enhanced electricity visual parameters
    private static final float MAIN_BOLT_THICKNESS = 0.15f;
    private static final float PRIMARY_BRANCH_THICKNESS = 0.08f;
    private static final float SECONDARY_BRANCH_THICKNESS = 0.04f;
    private static final float TERTIARY_BRANCH_THICKNESS = 0.02f;
    private static final float GLOW_THICKNESS = 0.35f;

    // Updated color scheme (white to yellow gradient)
    private static final int CORE_COLOR = 0xFFFFFFFF;          // Pure white core
    private static final int INNER_COLOR = 0xFFFFFFF0;         // Very light cream/white
    private static final int MIDDLE_COLOR = 0xFFFFFFDD;        // Light cream
    private static final int OUTER_COLOR = 0xFFFFFF88;         // Medium yellow-cream
    private static final int GLOW_COLOR = 0x44FFFF44;          // Soft yellow glow

    // Branch colors
    private static final int PRIMARY_BRANCH_COLOR = 0xFFFFFFDD;    // Light cream
    private static final int SECONDARY_BRANCH_COLOR = 0xFFFFFF88;  // Medium cream
    private static final int TERTIARY_BRANCH_COLOR = 0xFFFFDD44;   // Yellow-cream

    // Optimized cloud colors - fewer layers for better performance
    private static final int CLOUD_DARK_CORE = 0xCC1A1A1A;
    private static final int CLOUD_DARK = 0xAA2D2D2D;
    private static final int CLOUD_MEDIUM = 0x66535353;
    private static final int CLOUD_LIGHT = 0x44666666;

    // Enhanced animation parameters - faster clearing
    private static final int BOLT_LIFETIME = 8; // Reduced from 12 for faster clearing
    private static final int FLICKER_INTERVAL = 2;
    private static final float MAX_CHAIN_DISTANCE = 6.0f;

    // Optimized cloud parameters - shorter lifetimes for less lag
    private static final int CLOUD_LIFETIME = 160; // Reduced from 200
    private static final float CLOUD_SIZE = 3.5f;
    private static final int CLOUD_RENDER_LIFETIME = 60; // Reduced from 80

    // Improved generation parameters for more natural lightning
    private static final float MIN_SEGMENT_LENGTH = 0.25f;
    private static final float MAX_SEGMENT_LENGTH = 1.0f;
    private static final float BASE_DEVIATION = 0.5f;
    private static final float DEVIATION_DECAY = 0.8f;
    private static final int MIN_SEGMENTS = 5;

    // Multi-level branching parameters
    private static final float PRIMARY_BRANCH_PROBABILITY = 0.35f; // Reduced for performance
    private static final float SECONDARY_BRANCH_PROBABILITY = 0.25f; // Reduced for performance
    private static final float TERTIARY_BRANCH_PROBABILITY = 0.15f; // Reduced for performance
    private static final int MAX_BRANCH_DEPTH = 2; // Reduced from 3

    /**
     * Creates a chained electricity effect between entities
     * Updated to handle cloud sources properly with actual cloud position
     */
    public static void createElectricityChain(Level level, Entity source, List<Entity> targets, int duration) {
        if (source == null && targets.isEmpty()) {
            return;
        }

        // Generate unique chain ID
        int chainId = (source != null ? source.getId() : 0) + (int)(level.getGameTime() * 31);

        // Validate targets and ensure they're LivingEntity
        List<LivingEntity> validTargets = new ArrayList<>();
        for (Entity target : targets) {
            if (target instanceof LivingEntity living && target.isAlive()) {
                validTargets.add(living);
            }
        }

        if (!validTargets.isEmpty()) {
            boolean isCloudLightning = source != null && source.getId() < 0;
            Vec3 cloudPosition = null;

            // Get cloud position more reliably
            if (isCloudLightning) {
                int playerId = Math.abs(source.getId());
                StormCloud cloud = activeStormClouds.get(playerId);
                if (cloud != null) {
                    cloudPosition = cloud.getCurrentPosition();
                } else {
                    // Use source entity position if it's our virtual cloud entity
                    cloudPosition = source.position();
                }
            }

            ElectricChain chain = new ElectricChain(source, validTargets, duration, isCloudLightning, cloudPosition);
            activeChains.put(chainId, chain);
            chain.generateBolts(level);

            System.out.println("Created electricity chain: " + (isCloudLightning ? "cloud" : "normal") +
                    " with " + validTargets.size() + " targets, cloud pos: " + cloudPosition);
        }
    }

    /**
     * Creates a chained electricity effect from a cloud position to targets
     * Direct method that bypasses entity system for cloud lightning
     */
    public static void createCloudLightningChain(Level level, Vec3 cloudPosition, List<Entity> targets, int duration) {
        if (targets.isEmpty()) {
            return;
        }

        // Generate unique chain ID
        int chainId = cloudPosition.hashCode() + (int)(level.getGameTime() * 31);

        // Validate targets and ensure they're LivingEntity
        List<LivingEntity> validTargets = new ArrayList<>();
        for (Entity target : targets) {
            if (target instanceof LivingEntity living && target.isAlive()) {
                validTargets.add(living);
            }
        }

        if (!validTargets.isEmpty()) {
            ElectricChain chain = new ElectricChain(null, validTargets, duration, true, cloudPosition);
            activeChains.put(chainId, chain);
            chain.generateBolts(level);

            System.out.println("Created cloud lightning chain from " + cloudPosition +
                    " with " + validTargets.size() + " targets");
        }
    }

    /**
     * Creates a storm cloud visual effect with optimized cloud rendering
     */
    public static void createStormCloud(Level level, Entity player, int duration) {
        if (player == null) return;

        Vec3 cloudPosition = player.position().add(0, 4.0, 0);
        int cloudId = Math.abs(player.getId());

        StormCloud cloud = new StormCloud(cloudPosition, duration, player);
        activeStormClouds.put(cloudId, cloud);

        System.out.println("Created storm cloud visual at: " + cloudPosition);
    }

    public static void tick() {
        // Tick lightning chains
        Iterator<Map.Entry<Integer, ElectricChain>> chainIterator = activeChains.entrySet().iterator();
        while (chainIterator.hasNext()) {
            Map.Entry<Integer, ElectricChain> entry = chainIterator.next();
            ElectricChain chain = entry.getValue();

            if (chain.tick()) {
                chainIterator.remove();
            }
        }

        // Tick storm clouds
        Iterator<Map.Entry<Integer, StormCloud>> cloudIterator = activeStormClouds.entrySet().iterator();
        while (cloudIterator.hasNext()) {
            Map.Entry<Integer, StormCloud> entry = cloudIterator.next();
            StormCloud cloud = entry.getValue();

            if (cloud.tick()) {
                cloudIterator.remove();
            }
        }
    }

    public static void clearAll() {
        activeChains.clear();
        activeStormClouds.clear();
    }

    /**
     * Optimized storm cloud that looks like an actual storm cloud but with better performance
     */
    private static class StormCloud {
        private Vec3 basePosition;
        private int age = 0;
        private final int maxAge;
        private final Entity player;
        private int nextCloudUpdate = 0;
        private final List<CloudLayer> cloudLayers;

        public StormCloud(Vec3 position, int maxAge, Entity player) {
            this.basePosition = position;
            this.maxAge = maxAge;
            this.player = player;
            this.cloudLayers = new ArrayList<>();
            generateOptimizedCloudStructure();
        }

        public Vec3 getCurrentPosition() {
            if (player != null && player.isAlive()) {
                double time = age * 0.05;
                Vec3 drift = new Vec3(
                        Math.sin(time) * 0.3,
                        Math.sin(time * 1.3) * 0.1,
                        Math.cos(time * 0.8) * 0.3
                );
                return player.position().add(0, 4.0, 0).add(drift);
            }
            return basePosition;
        }

        private void generateOptimizedCloudStructure() {
            // Simplified cloud structure - fewer layers for better performance

            // Core layers - dense and dark (reduced count)
            for (int i = 0; i < 4; i++) {
                double angle = (2 * Math.PI * i) / 4;
                double radius = 0.8 + random.nextDouble() * 0.4;
                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        (random.nextDouble() - 0.5) * 0.3,
                        Math.sin(angle) * radius
                );
                cloudLayers.add(new CloudLayer(offset, 0.6f + random.nextFloat() * 0.3f, CLOUD_DARK_CORE, 0));
            }

            // Inner layers - dark storm cloud color (reduced count)
            for (int i = 0; i < 6; i++) {
                double angle = (2 * Math.PI * i) / 6;
                double radius = 1.2 + random.nextDouble() * 0.6;
                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        (random.nextDouble() - 0.5) * 0.4,
                        Math.sin(angle) * radius
                );
                cloudLayers.add(new CloudLayer(offset, 0.5f + random.nextFloat() * 0.4f, CLOUD_DARK, 1));
            }

            // Outer layers - medium (reduced count and simplified)
            for (int i = 0; i < 8; i++) {
                double angle = (2 * Math.PI * i) / 8;
                double radius = 1.8 + random.nextDouble() * 0.8;
                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        (random.nextDouble() - 0.5) * 0.5,
                        Math.sin(angle) * radius
                );
                cloudLayers.add(new CloudLayer(offset, 0.4f + random.nextFloat() * 0.5f, CLOUD_MEDIUM, 2));
            }

            // Edge wisps - simplified (reduced count)
            for (int i = 0; i < 12; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double radius = 2.4 + random.nextDouble() * 1.0;
                double height = (random.nextDouble() - 0.5) * 0.6;

                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        height,
                        Math.sin(angle) * radius
                );

                cloudLayers.add(new CloudLayer(offset, 0.3f + random.nextFloat() * 0.4f, CLOUD_LIGHT, 3));
            }
        }

        public boolean tick() {
            age++;

            Vec3 currentPos = getCurrentPosition();

            // Update less frequently for better performance
            if (age >= nextCloudUpdate) {
                updateAndRenderCloudOptimized(currentPos);
                nextCloudUpdate = age + 4; // Update every 4 ticks instead of 2
            }

            return age >= maxAge || (player != null && (!player.isAlive() || player.isRemoved()));
        }

        private void updateAndRenderCloudOptimized(Vec3 center) {
            double time = age * 0.01;

            // Render cloud layers with optimized approach
            for (CloudLayer layer : cloudLayers) {
                // Add gentle movement based on layer depth
                double layerTime = time * (1.0 + layer.depth * 0.1);
                Vec3 movement = new Vec3(
                        Math.sin(layerTime + layer.offset.x) * 0.03 * (4 - layer.depth),
                        Math.sin(layerTime * 1.2 + layer.offset.y) * 0.02,
                        Math.cos(layerTime * 0.9 + layer.offset.z) * 0.03 * (4 - layer.depth)
                );

                Vec3 layerPos = center.add(layer.offset).add(movement);

                // Render as single sphere instead of multiple overlapping ones
                VectorRenderer.drawSphereWorld(layerPos, layer.size, layer.color,
                        3, 4, true, CLOUD_RENDER_LIFETIME, null);
            }

            // Occasional internal lightning flashes (reduced frequency)
            if (random.nextFloat() < 0.015f) {
                Vec3 flashCenter = center.add(
                        (random.nextDouble() - 0.5) * 2.0,
                        (random.nextDouble() - 0.5) * 0.4,
                        (random.nextDouble() - 0.5) * 2.0
                );

                // Brief internal glow
                VectorRenderer.drawSphereWorld(flashCenter, 0.3f, 0x66FFFF88,
                        3, 4, true, 8, null); // Shorter lifetime
            }
        }

        private static class CloudLayer {
            final Vec3 offset;
            final float size;
            final int color;
            final int depth; // 0 = innermost, 3 = outermost

            CloudLayer(Vec3 offset, float size, int color, int depth) {
                this.offset = offset;
                this.size = size;
                this.color = color;
                this.depth = depth;
            }
        }
    }

    private static class ElectricChain {
        private final Entity source;
        private final List<LivingEntity> targets;
        private int duration;
        private int age = 0;
        private int nextFlicker = 0;
        private final boolean fromCloud;
        private final Vec3 cloudPosition; // Store actual cloud position

        public ElectricChain(Entity source, List<LivingEntity> targets, int duration, boolean fromCloud, Vec3 cloudPosition) {
            this.source = source;
            this.targets = new ArrayList<>(targets);
            this.duration = duration;
            this.fromCloud = fromCloud;
            this.cloudPosition = cloudPosition;
        }

        public boolean tick() {
            age++;

            if (age >= nextFlicker) {
                generateBolts(source != null ? source.level() : targets.get(0).level());
                nextFlicker = age + FLICKER_INTERVAL + random.nextInt(2);
            }

            return age >= duration;
        }

        public void generateBolts(Level level) {
            // Validate all entities still exist and are alive
            List<LivingEntity> validTargets = new ArrayList<>();
            for (LivingEntity target : targets) {
                if (target != null && target.isAlive() && !target.isRemoved()) {
                    validTargets.add(target);
                }
            }

            if (validTargets.isEmpty()) return;

            if (fromCloud && cloudPosition != null) {
                drawCloudLightning(validTargets, cloudPosition);
            } else if (source != null) {
                Vec3 sourcePos = getEntityCenter(source);
                if (sourcePos != null) {
                    drawSequentialChain(sourcePos, validTargets);
                }
            }
        }

        /**
         * Draws lightning from storm cloud to targets using stored cloud position
         */
        private void drawCloudLightning(List<LivingEntity> validTargets, Vec3 cloudPos) {
            // Draw lightning from cloud to first target, then chain between targets
            Vec3 previousPos = cloudPos;

            for (int i = 0; i < validTargets.size(); i++) {
                LivingEntity currentTarget = validTargets.get(i);
                Vec3 currentPos = getEntityCenter(currentTarget);

                if (currentPos == null) continue;

                // For cloud lightning, all connections are considered primary level
                generateAdvancedLightningBolt(previousPos, currentPos, 0);

                // Update previous position for next iteration
                previousPos = currentPos;
            }
        }

        /**
         * Draws the sequential chain from source -> first mob -> second mob -> etc.
         */
        private void drawSequentialChain(Vec3 sourcePos, List<LivingEntity> validTargets) {
            Vec3 previousPos = sourcePos;

            for (int i = 0; i < validTargets.size(); i++) {
                LivingEntity currentTarget = validTargets.get(i);
                Vec3 currentPos = getEntityCenter(currentTarget);

                if (currentPos == null) continue;

                // First connection is from player (chainLevel = 0)
                // Subsequent connections are between mobs (chainLevel = 1)
                int chainLevel = (i == 0) ? 0 : 1;

                generateAdvancedLightningBolt(previousPos, currentPos, chainLevel);

                // Update previous position for next iteration
                previousPos = currentPos;
            }
        }

        private Vec3 getEntityCenter(Entity entity) {
            if (entity == null || entity.isRemoved()) {
                return null;
            }
            return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        }

        private void generateAdvancedLightningBolt(Vec3 start, Vec3 end, int chainLevel) {
            // Generate more natural main path with smoother curves
            List<Vec3> mainPath = generateSmoothLightningPath(start, end);

            if (mainPath.size() < 2) return;

            // Draw layered bolt for better visual effect
            drawLayeredBolt(mainPath, chainLevel);

            // Generate multi-level branching system (reduced complexity)
            generateOptimizedBranches(mainPath, 0, chainLevel);
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
                double momentum = 0.7;
                Vec3 newRandomDeviation = new Vec3(
                        (random.nextGaussian()) * deviationStrength * 0.8,
                        (random.nextGaussian()) * deviationStrength * 0.5,
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

            // Apply smoothing to the path for better visual quality
            List<Vec3> smoothPath = applySmoothingFilter(path);

            // Adjust thickness and brightness based on chain level
            float thicknessMultiplier = 1.0f / (1.0f + chainLevel * 0.25f);
            float brightnessMultiplier = 1.0f / (1.0f + chainLevel * 0.15f);

            // Draw multiple layers for realistic glow effect with updated colors

            // Outermost glow layer (very soft and wide)
            int softGlow = adjustColorBrightness(GLOW_COLOR, brightnessMultiplier * 0.4f);
            VectorRenderer.drawPolylineWorld(smoothPath, softGlow,
                    GLOW_THICKNESS * thicknessMultiplier * 2.2f, false, BOLT_LIFETIME, null);

            // Outer glow layer
            int outerGlow = adjustColorBrightness(OUTER_COLOR, brightnessMultiplier * 0.7f);
            VectorRenderer.drawPolylineWorld(smoothPath, outerGlow,
                    GLOW_THICKNESS * thicknessMultiplier * 1.5f, false, BOLT_LIFETIME, null);

            // Middle layer
            int middleColor = adjustColorBrightness(MIDDLE_COLOR, brightnessMultiplier * 0.85f);
            VectorRenderer.drawPolylineWorld(smoothPath, middleColor,
                    MAIN_BOLT_THICKNESS * thicknessMultiplier * 1.8f, false, BOLT_LIFETIME, null);

            // Inner layer
            int innerColor = adjustColorBrightness(INNER_COLOR, brightnessMultiplier * 0.95f);
            VectorRenderer.drawPolylineWorld(smoothPath, innerColor,
                    MAIN_BOLT_THICKNESS * thicknessMultiplier * 1.2f, false, BOLT_LIFETIME, null);

            // Core layer (brightest, thinnest)
            int coreColor = adjustColorBrightness(CORE_COLOR, brightnessMultiplier);
            VectorRenderer.drawPolylineWorld(smoothPath, coreColor,
                    MAIN_BOLT_THICKNESS * thicknessMultiplier * 0.6f, false, BOLT_LIFETIME, null);
        }

        /**
         * Applies a smoothing filter to reduce sharp angles in lightning paths
         */
        private List<Vec3> applySmoothingFilter(List<Vec3> originalPath) {
            if (originalPath.size() <= 2) {
                return new ArrayList<>(originalPath);
            }

            List<Vec3> smoothedPath = new ArrayList<>();
            smoothedPath.add(originalPath.get(0)); // Keep first point unchanged

            // Apply moving average smoothing to intermediate points
            for (int i = 1; i < originalPath.size() - 1; i++) {
                Vec3 prev = originalPath.get(i - 1);
                Vec3 current = originalPath.get(i);
                Vec3 next = originalPath.get(i + 1);

                // Weighted average: 20% previous, 60% current, 20% next
                Vec3 smoothed = prev.scale(0.2)
                        .add(current.scale(0.6))
                        .add(next.scale(0.2));

                smoothedPath.add(smoothed);
            }

            smoothedPath.add(originalPath.get(originalPath.size() - 1)); // Keep last point unchanged
            return smoothedPath;
        }

        private void generateOptimizedBranches(List<Vec3> mainPath, int depth, int chainLevel) {
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
                default: // Secondary branches
                    branchProbability = SECONDARY_BRANCH_PROBABILITY;
                    branchColor = SECONDARY_BRANCH_COLOR;
                    branchThickness = SECONDARY_BRANCH_THICKNESS;
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
                    double branchAngle = (random.nextGaussian() * 0.3) * Math.PI;
                    double branchElevation = (random.nextGaussian() * 0.15) * Math.PI;

                    Vec3 branchDir = new Vec3(
                            Math.cos(branchAngle) * Math.cos(branchElevation),
                            Math.sin(branchElevation),
                            Math.sin(branchAngle) * Math.cos(branchElevation)
                    ).normalize();

                    // Branch length decreases with depth, but varies more naturally
                    double baseBranchLength = 0.6 + random.nextGaussian() * 0.3;
                    double branchLength = Math.abs(baseBranchLength) * Math.pow(0.65, depth);
                    branchLength = Math.max(0.2, Math.min(2.0, branchLength));

                    // Generate smoother branch path
                    Vec3 branchEnd = branchStart.add(branchDir.scale(branchLength));
                    List<Vec3> branchPath = generateSmoothLightningPath(branchStart, branchEnd);

                    if (branchPath.size() >= 2) {
                        // Draw branch with appropriate style for its level
                        drawBranchBolt(branchPath, depth, chainLevel, branchColor, branchThickness);

                        // Recursive branching with reduced probability
                        if (depth < MAX_BRANCH_DEPTH - 1 && random.nextFloat() < 0.3f) {
                            generateOptimizedBranches(branchPath, depth + 1, chainLevel);
                        }
                    }
                }
            }
        }

        private void drawBranchBolt(List<Vec3> path, int depth, int chainLevel, int baseColor, float thickness) {
            if (path.size() < 2) return;

            // Apply ultra-smoothing to all branch paths for perfect alignment
            List<Vec3> ultraSmoothPath = applySmoothingFilter(path);

            // Smoother scaling factors
            float depthFade = (float) Math.pow(0.85, depth);
            float chainFade = (float) Math.pow(0.92, chainLevel); // Smoother chain fade
            float totalFade = depthFade * chainFade;

            // Branches get progressively thinner with smoother scaling
            float branchThickness = thickness * totalFade;

            // Enhanced layering based on branch depth - all use ultra-smooth path
            switch (depth) {
                case 0: // Primary branches - full layering with smooth scaling
                {
                    int branchGlow = adjustColorBrightness(GLOW_COLOR, totalFade * 0.7f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchGlow,
                            branchThickness * 3.5f, false, BOLT_LIFETIME, null);

                    int branchOuter = adjustColorBrightness(OUTER_COLOR, totalFade * 0.85f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchOuter,
                            branchThickness * 2.5f, false, BOLT_LIFETIME, null);

                    int branchMain = adjustColorBrightness(baseColor, totalFade * 0.95f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchMain,
                            branchThickness * 1.2f, false, BOLT_LIFETIME, null);
                }
                break;

                case 1: // Secondary branches - smooth two-layer
                {
                    int branchGlow = adjustColorBrightness(GLOW_COLOR, totalFade * 0.75f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchGlow,
                            branchThickness * 2.8f, false, BOLT_LIFETIME, null);

                    int branchMain = adjustColorBrightness(baseColor, totalFade * 0.92f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchMain,
                            branchThickness * 1.1f, false, BOLT_LIFETIME, null);
                }
                break;

                case 2: // Tertiary branches - refined
                {
                    int branchGlow = adjustColorBrightness(GLOW_COLOR, totalFade * 0.65f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchGlow,
                            branchThickness * 2.3f, false, BOLT_LIFETIME, null);

                    int branchMain = adjustColorBrightness(baseColor, totalFade * 0.88f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchMain,
                            branchThickness * 0.9f, false, BOLT_LIFETIME, null);
                }
                break;

                default: // Quaternary branches - subtle but visible
                {
                    int branchGlow = adjustColorBrightness(GLOW_COLOR, totalFade * 0.55f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchGlow,
                            branchThickness * 2.0f, false, BOLT_LIFETIME, null);

                    int branchMain = adjustColorBrightness(baseColor, totalFade * 0.82f);
                    VectorRenderer.drawPolylineWorld(ultraSmoothPath, branchMain,
                            branchThickness * 0.75f, false, BOLT_LIFETIME, null);
                }
                break;
            }
        }

        private int adjustColorBrightness(int color, float multiplier) {
            int a = (color >> 24) & 0xFF;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            // Apply brightness multiplier while preserving the color tint
            r = Math.min(255, Math.max(0, (int)(r * multiplier)));
            g = Math.min(255, Math.max(0, (int)(g * multiplier)));
            b = Math.min(255, Math.max(0, (int)(b * multiplier)));
            a = Math.min(255, Math.max(0, (int)(a * Math.min(1.2f, multiplier + 0.3f))));

            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}