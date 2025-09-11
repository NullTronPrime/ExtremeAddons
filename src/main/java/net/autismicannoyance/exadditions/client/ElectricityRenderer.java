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

    // Realistic cloud colors - darker grays for storm clouds
    private static final int CLOUD_DARK_CORE = 0xCC1A1A1A;     // Very dark gray core
    private static final int CLOUD_DARK = 0xAA2D2D2D;          // Dark gray
    private static final int CLOUD_MEDIUM_DARK = 0x88404040;   // Medium-dark gray
    private static final int CLOUD_MEDIUM = 0x66535353;        // Medium gray
    private static final int CLOUD_LIGHT = 0x44666666;         // Light gray edges
    private static final int CLOUD_WISPY = 0x22808080;         // Very light wispy edges

    // Enhanced animation parameters
    private static final int BOLT_LIFETIME = 15;
    private static final int FLICKER_INTERVAL = 2;
    private static final float MAX_CHAIN_DISTANCE = 6.0f;

    // Enhanced cloud parameters
    private static final int CLOUD_LIFETIME = 200;
    private static final float CLOUD_SIZE = 3.5f;
    private static final int CLOUD_RENDER_LIFETIME = 80;

    // Improved generation parameters for more natural lightning
    private static final float MIN_SEGMENT_LENGTH = 0.25f;
    private static final float MAX_SEGMENT_LENGTH = 1.0f;
    private static final float BASE_DEVIATION = 0.5f;
    private static final float DEVIATION_DECAY = 0.8f;
    private static final int MIN_SEGMENTS = 5;

    // Multi-level branching parameters
    private static final float PRIMARY_BRANCH_PROBABILITY = 0.45f;
    private static final float SECONDARY_BRANCH_PROBABILITY = 0.35f;
    private static final float TERTIARY_BRANCH_PROBABILITY = 0.25f;
    private static final int MAX_BRANCH_DEPTH = 3;

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
     * Creates a storm cloud visual effect with realistic cloud rendering
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
     * Enhanced storm cloud that looks like an actual storm cloud
     */
    private static class StormCloud {
        private Vec3 basePosition;
        private int age = 0;
        private final int maxAge;
        private final Entity player;
        private int nextCloudUpdate = 0;
        private final List<CloudLayer> cloudLayers;
        private final List<CloudWisp> cloudWisps;

        public StormCloud(Vec3 position, int maxAge, Entity player) {
            this.basePosition = position;
            this.maxAge = maxAge;
            this.player = player;
            this.cloudLayers = new ArrayList<>();
            this.cloudWisps = new ArrayList<>();
            generateCloudStructure();
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

        private void generateCloudStructure() {
            Vec3 center = Vec3.ZERO;

            // Core layers - dense and dark
            for (int i = 0; i < 8; i++) {
                double angle = (2 * Math.PI * i) / 8;
                double radius = 0.8 + random.nextDouble() * 0.4;
                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        (random.nextDouble() - 0.5) * 0.3,
                        Math.sin(angle) * radius
                );
                cloudLayers.add(new CloudLayer(offset, 0.6f + random.nextFloat() * 0.3f, CLOUD_DARK_CORE, 0));
            }

            // Inner layers - dark storm cloud color
            for (int i = 0; i < 12; i++) {
                double angle = (2 * Math.PI * i) / 12;
                double radius = 1.2 + random.nextDouble() * 0.6;
                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        (random.nextDouble() - 0.5) * 0.4,
                        Math.sin(angle) * radius
                );
                cloudLayers.add(new CloudLayer(offset, 0.5f + random.nextFloat() * 0.4f, CLOUD_DARK, 1));
            }

            // Middle layers - medium darkness
            for (int i = 0; i < 16; i++) {
                double angle = (2 * Math.PI * i) / 16;
                double radius = 1.8 + random.nextDouble() * 0.8;
                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        (random.nextDouble() - 0.5) * 0.5,
                        Math.sin(angle) * radius
                );
                cloudLayers.add(new CloudLayer(offset, 0.4f + random.nextFloat() * 0.5f, CLOUD_MEDIUM_DARK, 2));
            }

            // Outer layers - lighter but still stormy
            for (int i = 0; i < 20; i++) {
                double angle = (2 * Math.PI * i) / 20;
                double radius = 2.4 + random.nextDouble() * 1.0;
                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        (random.nextDouble() - 0.5) * 0.6,
                        Math.sin(angle) * radius
                );
                cloudLayers.add(new CloudLayer(offset, 0.3f + random.nextFloat() * 0.6f, CLOUD_MEDIUM, 3));
            }

            // Edge wisps - create cloud-like edges
            for (int i = 0; i < 30; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double radius = 2.8 + random.nextDouble() * 1.5;
                double height = (random.nextDouble() - 0.5) * 0.8;

                Vec3 offset = new Vec3(
                        Math.cos(angle) * radius,
                        height,
                        Math.sin(angle) * radius
                );

                cloudWisps.add(new CloudWisp(offset, 0.2f + random.nextFloat() * 0.4f,
                        random.nextFloat() < 0.3f ? CLOUD_LIGHT : CLOUD_WISPY));
            }
        }

        public boolean tick() {
            age++;

            Vec3 currentPos = getCurrentPosition();

            if (age >= nextCloudUpdate) {
                updateAndRenderCloud(currentPos);
                nextCloudUpdate = age + 2; // Update every 2 ticks for smooth animation
            }

            return age >= maxAge || (player != null && (!player.isAlive() || player.isRemoved()));
        }

        private void updateAndRenderCloud(Vec3 center) {
            double time = age * 0.01;

            // Render cloud layers with layered depth
            for (CloudLayer layer : cloudLayers) {
                // Add gentle movement based on layer depth
                double layerTime = time * (1.0 + layer.depth * 0.1);
                Vec3 movement = new Vec3(
                        Math.sin(layerTime + layer.offset.x) * 0.03 * (4 - layer.depth),
                        Math.sin(layerTime * 1.2 + layer.offset.y) * 0.02,
                        Math.cos(layerTime * 0.9 + layer.offset.z) * 0.03 * (4 - layer.depth)
                );

                Vec3 layerPos = center.add(layer.offset).add(movement);

                // Render as filled polygons for better cloud appearance
                renderCloudBlob(layerPos, layer.size, layer.color);
            }

            // Render wispy edges
            for (CloudWisp wisp : cloudWisps) {
                double wispTime = time * 0.5;
                Vec3 wispMovement = new Vec3(
                        Math.sin(wispTime + wisp.offset.x * 0.1) * 0.08,
                        Math.sin(wispTime * 1.4 + wisp.offset.y * 0.1) * 0.04,
                        Math.cos(wispTime * 0.7 + wisp.offset.z * 0.1) * 0.08
                );

                Vec3 wispPos = center.add(wisp.offset).add(wispMovement);
                VectorRenderer.drawSphereWorld(wispPos, wisp.size, wisp.color,
                        3, 4, true, CLOUD_RENDER_LIFETIME, null);
            }

            // Occasional internal lightning flashes
            if (random.nextFloat() < 0.03f) {
                Vec3 flashCenter = center.add(
                        (random.nextDouble() - 0.5) * 2.0,
                        (random.nextDouble() - 0.5) * 0.4,
                        (random.nextDouble() - 0.5) * 2.0
                );

                // Brief internal glow
                VectorRenderer.drawSphereWorld(flashCenter, 0.3f, 0x66FFFF88,
                        4, 5, true, 12, null);
            }
        }

        private void renderCloudBlob(Vec3 center, float size, int color) {
            // Create a more organic cloud shape using multiple overlapping spheres
            int subdivisions = Math.max(3, (int)(size * 6));

            for (int i = 0; i < subdivisions; i++) {
                double angle = (2 * Math.PI * i) / subdivisions;
                double radius = size * 0.6 * (0.8 + random.nextDouble() * 0.4);

                Vec3 blobCenter = center.add(
                        Math.cos(angle) * radius * 0.3,
                        (random.nextDouble() - 0.5) * size * 0.2,
                        Math.sin(angle) * radius * 0.3
                );

                float blobSize = size * (0.7f + random.nextFloat() * 0.6f);
                VectorRenderer.drawSphereWorld(blobCenter, blobSize, color,
                        4, 6, true, CLOUD_RENDER_LIFETIME, null);
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

        private static class CloudWisp {
            final Vec3 offset;
            final float size;
            final int color;

            CloudWisp(Vec3 offset, float size, int color) {
                this.offset = offset;
                this.size = size;
                this.color = color;
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

            // Adjust thickness and brightness based on chain level
            float thicknessMultiplier = 1.0f / (1.0f + chainLevel * 0.25f);
            float brightnessMultiplier = 1.0f / (1.0f + chainLevel * 0.15f);

            // Draw multiple layers for realistic glow effect with updated colors

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

            float depthFade = (float) Math.pow(0.85, depth);
            float chainFade = 1.0f / (1.0f + chainLevel * 0.1f);
            float totalFade = depthFade * chainFade;

            // Branches get progressively thinner and dimmer but remain more visible
            float branchThickness = thickness * totalFade;

            // Apply smoothing to branch paths as well
            List<Vec3> smoothPath = applySmoothingFilter(path);

            // Enhanced layering for all branch levels with smooth paths
            if (depth <= 1) {
                // Primary and secondary branches get multiple layers
                int branchGlow = adjustColorBrightness(GLOW_COLOR, totalFade * 0.6f);
                VectorRenderer.drawPolylineWorld(smoothPath, branchGlow,
                        branchThickness * 3.0f, false, BOLT_LIFETIME, null);

                int branchOuter = adjustColorBrightness(OUTER_COLOR, totalFade * 0.8f);
                VectorRenderer.drawPolylineWorld(smoothPath, branchOuter,
                        branchThickness * 2.0f, false, BOLT_LIFETIME, null);

                int branchMain = adjustColorBrightness(baseColor, totalFade);
                VectorRenderer.drawPolylineWorld(smoothPath, branchMain,
                        branchThickness, false, BOLT_LIFETIME, null);
            } else {
                // Tertiary and quaternary branches get two layers but remain visible
                int branchGlow = adjustColorBrightness(GLOW_COLOR, totalFade * 0.7f);
                VectorRenderer.drawPolylineWorld(smoothPath, branchGlow,
                        branchThickness * 2.2f, false, BOLT_LIFETIME, null);

                int branchMain = adjustColorBrightness(baseColor, totalFade * 0.95f);
                VectorRenderer.drawPolylineWorld(smoothPath, branchMain,
                        branchThickness, false, BOLT_LIFETIME, null);
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