package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class LaserRenderer {
    // Enhanced laser properties
    private static final int LASER_COLOR_CORE = 0xFFFFFFFF;    // Bright white core
    private static final int LASER_COLOR_OUTER = 0xFF00AAFF;   // Cyan outer glow
    private static final int IMPACT_COLOR_CORE = 0xFFFFFFFF;   // White hot core
    private static final int IMPACT_COLOR_OUTER = 0xFFFF4400;  // Orange outer explosion
    private static final int SPARK_COLOR = 0xFFFFAA00;         // Golden sparks

    private static final float LASER_CORE_THICKNESS = 0.05f;   // Thin bright core
    private static final float LASER_OUTER_THICKNESS = 0.25f;  // Thicker outer glow
    private static final int LASER_LIFETIME = 60;              // 3 seconds
    private static final int IMPACT_LIFETIME = 40;             // 2 seconds
    private static final int SPARK_LIFETIME = 30;              // 1.5 seconds

    // Enhanced physics properties
    private static final double PIERCING_THRESHOLD = 0.7;      // Damage reduction per entity pierced
    private static final int MAX_PIERCE_ENTITIES = 10;         // Maximum entities to pierce through
    private static final double REFLECTION_EFFICIENCY = 0.95;  // Energy retained after reflection
    private static final double MIN_REFLECTION_ENERGY = 0.1;   // Minimum energy to continue reflecting

    public static void handleLaserAttack(Vec3 startPos, Vec3 direction, int shooterId,
                                         float baseDamage, int maxBounces, double maxRange) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Level level = mc.level;
        Entity shooter = level.getEntity(shooterId);

        // Calculate enhanced laser path with piercing and better reflections
        List<LaserSegment> segments = calculateEnhancedLaserPath(level, startPos, direction,
                maxBounces, maxRange, baseDamage);

        // Render impressive 3D laser beam
        renderVolumetricLaser(segments);

//        // Apply piercing damage to entities
//        applyPiercingDamage(level, segments, shooter);

        // Enhanced impact effects and sounds
        createImpactEffects(level, segments);
        playEnhancedSounds(level, segments);
    }

    private static List<LaserSegment> calculateEnhancedLaserPath(Level level, Vec3 startPos,
                                                                 Vec3 direction, int maxBounces, double maxRange, float initialEnergy) {
        List<LaserSegment> segments = new ArrayList<>();
        Vec3 currentPos = startPos;
        Vec3 currentDir = direction.normalize();
        double remainingRange = maxRange;
        float remainingEnergy = initialEnergy;
        int bounces = 0;
        Set<BlockPos> hitBlocks = new HashSet<>();

        while (bounces <= maxBounces && remainingRange > 0 && remainingEnergy > MIN_REFLECTION_ENERGY) {
            // Enhanced raycasting with better precision
            Vec3 endPos = currentPos.add(currentDir.scale(remainingRange));
            BlockHitResult hitResult = enhancedRaycast(level, currentPos, endPos, hitBlocks);

            Vec3 hitPos;
            boolean hasBlockHit = hitResult != null;

            if (hasBlockHit) {
                hitPos = hitResult.getLocation();
                // Slightly offset to prevent z-fighting
                hitPos = hitPos.subtract(currentDir.scale(0.001));
            } else {
                hitPos = endPos;
            }

            // Create segment with energy information
            LaserSegment segment = new LaserSegment(currentPos, hitPos, bounces == 0,
                    remainingEnergy, bounces);
            segments.add(segment);

            // Update remaining range
            double segmentLength = currentPos.distanceTo(hitPos);
            remainingRange -= segmentLength;

            // Handle reflections with better physics
            if (hasBlockHit && bounces < maxBounces && remainingRange > 0) {
                BlockState hitState = level.getBlockState(hitResult.getBlockPos());
                float reflectivity = getBlockReflectivity(hitState);

                if (reflectivity > 0) {
                    // Calculate perfect reflection with slight randomization for realism
                    Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());

                    // Add slight surface roughness for more realistic reflections
                    normal = addSurfaceRoughness(normal, reflectivity);

                    // Perfect reflection: R = D - 2(D·N)N
                    Vec3 reflection = currentDir.subtract(normal.scale(2 * currentDir.dot(normal)));

                    // Update position and direction
                    currentPos = hitPos.add(normal.scale(0.02)); // Offset from surface
                    currentDir = reflection.normalize();

                    // Reduce energy based on reflectivity and distance traveled
                    remainingEnergy *= (reflectivity * REFLECTION_EFFICIENCY);
                    bounces++;

                    // Track hit blocks to prevent immediate re-collision
                    hitBlocks.add(hitResult.getBlockPos());

                } else {
                    // Laser absorbed - create absorption effect
                    createAbsorptionEffect(hitPos, remainingEnergy);
                    break;
                }
            } else {
                break;
            }
        }

        return segments;
    }

    private static BlockHitResult enhancedRaycast(Level level, Vec3 start, Vec3 end, Set<BlockPos> excludeBlocks) {
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (distance == 0) return null;

        Vec3 normalizedDir = direction.normalize();
        double step = 0.05; // Smaller step for better precision

        for (double d = 0; d <= distance; d += step) {
            Vec3 currentPos = start.add(normalizedDir.scale(d));
            BlockPos blockPos = BlockPos.containing(currentPos);

            // Skip if we've already hit this block
            if (excludeBlocks.contains(blockPos)) continue;

            BlockState blockState = level.getBlockState(blockPos);

            if (!blockState.isAir() && blockState.isSolid()) {
                // Calculate precise hit normal based on which face was hit
                Direction face = calculateHitFace(currentPos, blockPos, normalizedDir);
                Vec3 preciseHitPos = calculatePreciseHitPosition(currentPos, blockPos, face);

                return new BlockHitResult(preciseHitPos, face, blockPos, false);
            }
        }

        return null;
    }

    private static Direction calculateHitFace(Vec3 hitPos, BlockPos blockPos, Vec3 rayDir) {
        // Calculate which face of the block was hit based on ray direction and position
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        Vec3 toHit = hitPos.subtract(blockCenter);

        double absX = Math.abs(toHit.x);
        double absY = Math.abs(toHit.y);
        double absZ = Math.abs(toHit.z);

        if (absX > absY && absX > absZ) {
            return toHit.x > 0 ? Direction.EAST : Direction.WEST;
        } else if (absY > absZ) {
            return toHit.y > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return toHit.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private static Vec3 calculatePreciseHitPosition(Vec3 rayPos, BlockPos blockPos, Direction face) {
        Vec3 blockMin = Vec3.atLowerCornerOf(blockPos);
        Vec3 blockMax = blockMin.add(1, 1, 1);

        // Calculate exact intersection with the block face
        switch (face) {
            case EAST: return new Vec3(blockMax.x, rayPos.y, rayPos.z);
            case WEST: return new Vec3(blockMin.x, rayPos.y, rayPos.z);
            case UP: return new Vec3(rayPos.x, blockMax.y, rayPos.z);
            case DOWN: return new Vec3(rayPos.x, blockMin.y, rayPos.z);
            case SOUTH: return new Vec3(rayPos.x, rayPos.y, blockMax.z);
            case NORTH: return new Vec3(rayPos.x, rayPos.y, blockMin.z);
            default: return rayPos;
        }
    }

    private static float getBlockReflectivity(BlockState blockState) {
        // Enhanced reflectivity system
        if (blockState.is(Blocks.GLASS) || blockState.is(Blocks.WHITE_STAINED_GLASS) ||
                blockState.is(Blocks.TINTED_GLASS)) {
            return 0.95f; // Excellent reflectivity
        }
        if (blockState.is(Blocks.ICE) || blockState.is(Blocks.PACKED_ICE) ||
                blockState.is(Blocks.BLUE_ICE)) {
            return 0.85f; // Great reflectivity
        }
        if (blockState.is(Blocks.IRON_BLOCK) || blockState.is(Blocks.GOLD_BLOCK) ||
                blockState.is(Blocks.DIAMOND_BLOCK) || blockState.is(Blocks.EMERALD_BLOCK)) {
            return 0.80f; // Good metallic reflectivity
        }
        if (blockState.is(Blocks.WATER)) {
            return 0.70f; // Water reflection
        }
        if (blockState.is(Blocks.QUARTZ_BLOCK) || blockState.is(Blocks.WHITE_CONCRETE) ||
                blockState.is(Blocks.SNOW_BLOCK)) {
            return 0.60f; // Moderate reflectivity
        }
        if (blockState.is(Blocks.STONE) || blockState.is(Blocks.COBBLESTONE) ||
                blockState.is(Blocks.DEEPSLATE)) {
            return 0.30f; // Poor reflectivity
        }
        if (blockState.is(Blocks.OBSIDIAN) || blockState.is(Blocks.BLACK_CONCRETE)) {
            return 0.15f; // Very poor reflectivity
        }

        // Default: most blocks have some reflectivity
        return blockState.isSolid() ? 0.25f : 0.0f;
    }

    private static Vec3 addSurfaceRoughness(Vec3 normal, float reflectivity) {
        // Add slight randomness based on surface quality (smoother = less randomness)
        float roughness = (1.0f - reflectivity) * 0.1f;

        double offsetX = (Math.random() - 0.5) * roughness;
        double offsetY = (Math.random() - 0.5) * roughness;
        double offsetZ = (Math.random() - 0.5) * roughness;

        return normal.add(offsetX, offsetY, offsetZ).normalize();
    }

    private static void renderVolumetricLaser(List<LaserSegment> segments) {
        for (int i = 0; i < segments.size(); i++) {
            LaserSegment segment = segments.get(i);

            // Calculate energy-based properties
            float energyRatio = segment.energy / 5.0f; // Normalize to initial energy of 5
            energyRatio = Mth.clamp(energyRatio, 0.1f, 1.0f);

            // Core laser beam (bright, thin)
            int coreColor = interpolateColor(LASER_COLOR_CORE, 0xFF666666, 1.0f - energyRatio);
            float coreThickness = LASER_CORE_THICKNESS * energyRatio;

            VectorRenderer.drawLineWorld(
                    segment.start,
                    segment.end,
                    coreColor,
                    coreThickness,
                    false, // world units
                    LASER_LIFETIME,
                    null
            );

            // Outer glow (dimmer, thicker)
            int outerColor = interpolateColor(LASER_COLOR_OUTER, 0xFF002244, 1.0f - energyRatio);
            float outerThickness = LASER_OUTER_THICKNESS * energyRatio;

            // Make outer glow more transparent
            outerColor = (outerColor & 0x00FFFFFF) | (((int)(128 * energyRatio)) << 24);

            VectorRenderer.drawLineWorld(
                    segment.start,
                    segment.end,
                    outerColor,
                    outerThickness,
                    false,
                    LASER_LIFETIME,
                    null
            );

            // Add energy particles along the beam
            addEnergyParticles(segment, energyRatio);
        }
    }

    private static void addEnergyParticles(LaserSegment segment, float energy) {
        Vec3 direction = segment.end.subtract(segment.start);
        double length = direction.length();
        direction = direction.normalize();

        // Add glowing particles along the beam path
        int particleCount = (int)(length * 3 * energy); // More particles for higher energy

        for (int i = 0; i < particleCount; i++) {
            double t = (double)i / Math.max(1, particleCount - 1);
            Vec3 particlePos = segment.start.add(direction.scale(length * t));

            // Add slight random offset for realistic energy fluctuation
            double offsetX = (Math.random() - 0.5) * 0.1 * energy;
            double offsetY = (Math.random() - 0.5) * 0.1 * energy;
            double offsetZ = (Math.random() - 0.5) * 0.1 * energy;

            particlePos = particlePos.add(offsetX, offsetY, offsetZ);

            // Create small glowing sphere
            int particleColor = interpolateColor(LASER_COLOR_CORE, LASER_COLOR_OUTER, Math.random());
            particleColor = (particleColor & 0x00FFFFFF) | (((int)(200 * energy)) << 24);

            VectorRenderer.drawSphereWorld(
                    particlePos,
                    0.02f * energy,
                    particleColor,
                    6, 6,
                    false,
                    LASER_LIFETIME / 2,
                    null
            );
        }
    }

    private static void createImpactEffects(Level level, List<LaserSegment> segments) {
        if (segments.isEmpty()) return;

        // Main impact at the end
        LaserSegment finalSegment = segments.get(segments.size() - 1);
        createMajorImpactEffect(finalSegment.end, finalSegment.energy);

        // Reflection points
        for (int i = 1; i < segments.size(); i++) {
            LaserSegment segment = segments.get(i);
            createReflectionEffect(segment.start, segment.energy * 0.6f);
        }

        // Add hit effects for visual feedback (entities get hit server-side)
        addVisualHitEffects(level, segments);
    }

    private static void addVisualHitEffects(Level level, List<LaserSegment> segments) {
        // Add visual hit effects for any entities that might be in the laser path
        // This is purely cosmetic - actual damage is handled server-side
        for (LaserSegment segment : segments) {
            AABB boundingBox = new AABB(segment.start, segment.end).inflate(0.3);
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

            for (Entity entity : entities) {
                if (intersectsEntityVisual(segment, entity)) {
                    // Create visual hit effect
                    createEntityHitEffect(entity.position().add(0, entity.getBbHeight() / 2, 0), segment.energy);
                }
            }
        }
    }

    private static boolean intersectsEntityVisual(LaserSegment segment, Entity entity) {
        AABB entityBounds = entity.getBoundingBox();
        Vec3 segmentDir = segment.getDirection().normalize();
        double segmentLength = segment.getLength();

        Vec3 toEntity = entityBounds.getCenter().subtract(segment.start);
        double projection = toEntity.dot(segmentDir);

        if (projection < 0 || projection > segmentLength) {
            return false;
        }

        Vec3 closestPoint = segment.start.add(segmentDir.scale(projection));
        double distance = closestPoint.distanceTo(entityBounds.getCenter());

        double hitRadius = Math.max(entityBounds.getXsize(), entityBounds.getZsize()) * 0.7;
        return distance <= hitRadius;
    }

    private static void createMajorImpactEffect(Vec3 impactPos, float energy) {
        float size = 0.3f + (energy / 5.0f) * 0.7f; // Scale with energy

        // Core explosion sphere
        VectorRenderer.drawSphereWorld(
                impactPos,
                size,
                IMPACT_COLOR_CORE,
                12, 12,
                false,
                IMPACT_LIFETIME,
                null
        );

        // Outer explosion sphere
        VectorRenderer.drawSphereWorld(
                impactPos,
                size * 1.5f,
                (IMPACT_COLOR_OUTER & 0x00FFFFFF) | 0x80000000, // Semi-transparent
                10, 10,
                false,
                IMPACT_LIFETIME,
                null
        );

        // Explosive sparks in all directions
        int sparkCount = (int)(16 * (energy / 5.0f));
        for (int i = 0; i < sparkCount; i++) {
            // Random direction
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.random() * Math.PI;

            Vec3 sparkDir = new Vec3(
                    Math.sin(phi) * Math.cos(theta),
                    Math.cos(phi),
                    Math.sin(phi) * Math.sin(theta)
            ).normalize();

            double sparkLength = 1.0 + Math.random() * 2.0;
            Vec3 sparkEnd = impactPos.add(sparkDir.scale(sparkLength));

            int sparkColor = interpolateColor(SPARK_COLOR, IMPACT_COLOR_OUTER, Math.random());

            VectorRenderer.drawLineWorld(
                    impactPos,
                    sparkEnd,
                    sparkColor,
                    0.05f + (float)(Math.random() * 0.1),
                    false,
                    SPARK_LIFETIME,
                    null
            );
        }
    }

    private static void createReflectionEffect(Vec3 reflectionPos, float energy) {
        float size = 0.15f + (energy / 5.0f) * 0.25f;

        // Reflection flash
        VectorRenderer.drawSphereWorld(
                reflectionPos,
                size,
                (LASER_COLOR_CORE & 0x00FFFFFF) | 0xC0000000,
                8, 8,
                false,
                IMPACT_LIFETIME / 2,
                null
        );

        // Small reflection sparks
        int sparkCount = (int)(6 * (energy / 5.0f));
        for (int i = 0; i < sparkCount; i++) {
            double angle = (i * Math.PI * 2) / sparkCount;
            Vec3 sparkDir = new Vec3(Math.cos(angle), 0.3, Math.sin(angle)).normalize();
            Vec3 sparkEnd = reflectionPos.add(sparkDir.scale(0.5));

            VectorRenderer.drawLineWorld(
                    reflectionPos,
                    sparkEnd,
                    LASER_COLOR_OUTER,
                    0.03f,
                    false,
                    SPARK_LIFETIME / 2,
                    null
            );
        }
    }

    private static void createEntityHitEffect(Vec3 hitPos, float energy) {
        // Blood/energy splash effect
        VectorRenderer.drawSphereWorld(
                hitPos,
                0.2f * (energy / 5.0f),
                0xFFFF0000, // Red
                8, 8,
                false,
                IMPACT_LIFETIME / 3,
                null
        );
    }

    private static void createAbsorptionEffect(Vec3 absorptionPos, float energy) {
        // Dark energy absorption effect
        VectorRenderer.drawSphereWorld(
                absorptionPos,
                0.25f * (energy / 5.0f),
                0xFF220011, // Dark red
                8, 8,
                false,
                IMPACT_LIFETIME,
                null
        );
    }

    private static void playEnhancedSounds(Level level, List<LaserSegment> segments) {
        if (!segments.isEmpty()) {
            Vec3 finalImpact = segments.get(segments.size() - 1).end;

            // Main impact sound
            level.playLocalSound(finalImpact.x, finalImpact.y, finalImpact.z,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                    1.0f, 1.8f, false);

            // Reflection sounds
            for (int i = 1; i < segments.size(); i++) {
                Vec3 reflectionPos = segments.get(i).start;
                level.playLocalSound(reflectionPos.x, reflectionPos.y, reflectionPos.z,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        0.5f, 2.0f, false);
            }
        }
    }

    // Utility methods
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

    private static class LaserSegment {
        public final Vec3 start;
        public final Vec3 end;
        public final boolean isPrimary;
        public final float energy;
        public final int bounceIndex;

        public LaserSegment(Vec3 start, Vec3 end, boolean isPrimary, float energy, int bounceIndex) {
            this.start = start;
            this.end = end;
            this.isPrimary = isPrimary;
            this.energy = energy;
            this.bounceIndex = bounceIndex;
        }

        public Vec3 getDirection() {
            return end.subtract(start);
        }

        public double getLength() {
            return start.distanceTo(end);
        }
    }
}