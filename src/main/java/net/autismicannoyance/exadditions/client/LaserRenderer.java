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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class LaserRenderer {
    private static final int LASER_COLOR = 0xFF00FFFF; // Bright cyan
    private static final int IMPACT_COLOR = 0xFFFF4444; // Bright red
    private static final float LASER_THICKNESS = 2.0f;
    private static final int LASER_LIFETIME = 40; // 2 seconds
    private static final int IMPACT_LIFETIME = 20; // 1 second

    public static void handleLaserAttack(Vec3 startPos, Vec3 direction, int shooterId,
                                         float baseDamage, int maxBounces, double maxRange) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Level level = mc.level;
        Entity shooter = level.getEntity(shooterId);

        // Calculate laser path with bounces
        List<LaserSegment> segments = calculateLaserPath(level, startPos, direction, maxBounces, maxRange);

        // Render laser segments using VectorRenderer
        renderLaserSegments(segments);

        // Apply damage to entities hit by laser
        applyLaserDamage(level, segments, shooter, baseDamage);

        // Play impact sounds
        playImpactSounds(level, segments);
    }

    private static List<LaserSegment> calculateLaserPath(Level level, Vec3 startPos, Vec3 direction,
                                                         int maxBounces, double maxRange) {
        List<LaserSegment> segments = new ArrayList<>();
        Vec3 currentPos = startPos;
        Vec3 currentDir = direction.normalize();
        double remainingRange = maxRange;
        int bounces = 0;

        while (bounces <= maxBounces && remainingRange > 0) {
            // Cast ray to find next hit using simple raycasting
            Vec3 endPos = currentPos.add(currentDir.scale(remainingRange));

            BlockHitResult hitResult = raycastBlocks(level, currentPos, endPos);

            Vec3 hitPos;
            boolean hasBlockHit = hitResult != null;

            if (hasBlockHit) {
                hitPos = hitResult.getLocation();
            } else {
                hitPos = endPos;
            }

            // Create segment
            LaserSegment segment = new LaserSegment(currentPos, hitPos, bounces == 0);
            segments.add(segment);

            // Update remaining range
            double segmentLength = currentPos.distanceTo(hitPos);
            remainingRange -= segmentLength;

            // If we hit a block, calculate reflection
            if (hasBlockHit && bounces < maxBounces && remainingRange > 0) {
                Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());

                // Check if the block is reflective
                BlockState hitState = level.getBlockState(hitResult.getBlockPos());
                if (canReflectOff(hitState)) {
                    // Calculate reflection: R = D - 2(D·N)N
                    Vec3 reflection = currentDir.subtract(normal.scale(2 * currentDir.dot(normal)));

                    // Slightly offset the new position to prevent immediate re-collision
                    currentPos = hitPos.add(normal.scale(0.01));
                    currentDir = reflection.normalize();
                    bounces++;
                } else {
                    // Laser absorbed by block
                    break;
                }
            } else {
                // No more bounces or out of range
                break;
            }
        }

        return segments;
    }

    // Simple raycasting that works with 1.20.1
    private static BlockHitResult raycastBlocks(Level level, Vec3 start, Vec3 end) {
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (distance == 0) return null;

        Vec3 normalizedDir = direction.normalize();
        double step = 0.1; // Step size for raycasting

        for (double d = 0; d <= distance; d += step) {
            Vec3 currentPos = start.add(normalizedDir.scale(d));
            BlockPos blockPos = BlockPos.containing(currentPos);
            BlockState blockState = level.getBlockState(blockPos);

            if (!blockState.isAir() && blockState.isSolid()) {
                // We hit a block, determine the face
                Direction face = getFaceFromDirection(normalizedDir.scale(-1)); // Reverse direction for face
                return new BlockHitResult(currentPos, face, blockPos, false);
            }
        }

        return null; // No hit
    }

    private static Direction getFaceFromDirection(Vec3 direction) {
        // Find the face most aligned with the opposite of ray direction
        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);

        if (absX > absY && absX > absZ) {
            return direction.x > 0 ? Direction.EAST : Direction.WEST;
        } else if (absY > absZ) {
            return direction.y > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return direction.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private static boolean canReflectOff(BlockState blockState) {
        // Most solid blocks can reflect lasers
        return blockState.isSolid() && !blockState.isAir();
    }

    private static void renderLaserSegments(List<LaserSegment> segments) {
        for (int i = 0; i < segments.size(); i++) {
            LaserSegment segment = segments.get(i);

            // Vary color slightly for each bounce
            int color = LASER_COLOR;
            if (i > 0) {
                // Slightly dim bounced segments
                int alpha = Math.max(128, 255 - (i * 20));
                color = (alpha << 24) | (LASER_COLOR & 0x00FFFFFF);
            }

            // Draw laser beam using VectorRenderer
            VectorRenderer.drawLineWorld(
                    segment.start,
                    segment.end,
                    color,
                    LASER_THICKNESS,
                    true, // thickness in pixels
                    LASER_LIFETIME,
                    null // no transform
            );

            // Draw impact effect at end of final segment
            if (i == segments.size() - 1) {
                drawImpactEffect(segment.end);
            }
        }
    }

    private static void drawImpactEffect(Vec3 impactPos) {
        // Create impact sphere using VectorRenderer
        VectorRenderer.drawSphereWorld(
                impactPos,
                0.5f, // radius
                IMPACT_COLOR,
                8, 8, // segments
                false, // not double sided
                IMPACT_LIFETIME,
                null // no transform
        );

        // Add spark lines radiating outward
        for (int i = 0; i < 6; i++) {
            double angle = (i * Math.PI * 2) / 6;
            Vec3 sparkDir = new Vec3(Math.cos(angle), 0.2, Math.sin(angle)).normalize();
            Vec3 sparkEnd = impactPos.add(sparkDir.scale(0.8));

            VectorRenderer.drawLineWorld(
                    impactPos,
                    sparkEnd,
                    0xFFFFAA00, // Orange sparks
                    1.0f,
                    true,
                    IMPACT_LIFETIME,
                    null
            );
        }
    }

    private static void applyLaserDamage(Level level, List<LaserSegment> segments, Entity shooter, float baseDamage) {
        for (int i = 0; i < segments.size(); i++) {
            LaserSegment segment = segments.get(i);

            // Calculate damage (diminishing with each bounce)
            float segmentDamage = baseDamage * (float) Math.pow(0.8, i);

            // Find entities along this segment
            AABB boundingBox = new AABB(segment.start, segment.end).inflate(0.5);
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

            for (Entity entity : entities) {
                if (entity == shooter) continue; // Don't damage the shooter
                if (!(entity instanceof LivingEntity)) continue;

                // Check if laser actually intersects with entity
                if (intersectsEntity(segment, entity)) {
                    // Apply damage
                    DamageSource damageSource;
                    if (shooter instanceof Player player) {
                        damageSource = level.damageSources().playerAttack(player);
                    } else {
                        damageSource = level.damageSources().magic();
                    }

                    entity.hurt(damageSource, segmentDamage);

                    // Add knockback effect
                    Vec3 knockback = segment.getDirection().normalize().scale(0.3);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
                }
            }
        }
    }

    private static boolean intersectsEntity(LaserSegment segment, Entity entity) {
        // Simple cylinder intersection test
        AABB entityBounds = entity.getBoundingBox();
        Vec3 segmentDir = segment.getDirection().normalize();
        double segmentLength = segment.getLength();

        // Project entity center onto laser line
        Vec3 toEntity = entityBounds.getCenter().subtract(segment.start);
        double projection = toEntity.dot(segmentDir);

        // Check if projection is within segment bounds
        if (projection < 0 || projection > segmentLength) {
            return false;
        }

        // Find closest point on laser to entity center
        Vec3 closestPoint = segment.start.add(segmentDir.scale(projection));
        double distance = closestPoint.distanceTo(entityBounds.getCenter());

        // Check if within reasonable hit distance
        double hitRadius = Math.max(entityBounds.getXsize(), entityBounds.getZsize()) * 0.6;
        return distance <= hitRadius;
    }

    private static void playImpactSounds(Level level, List<LaserSegment> segments) {
        if (!segments.isEmpty()) {
            Vec3 finalImpact = segments.get(segments.size() - 1).end;
            level.playLocalSound(finalImpact.x, finalImpact.y, finalImpact.z,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                    0.5f, 1.5f, false);
        }
    }

    private static class LaserSegment {
        public final Vec3 start;
        public final Vec3 end;
        public final boolean isPrimary;

        public LaserSegment(Vec3 start, Vec3 end, boolean isPrimary) {
            this.start = start;
            this.end = end;
            this.isPrimary = isPrimary;
        }

        public Vec3 getDirection() {
            return end.subtract(start);
        }

        public double getLength() {
            return start.distanceTo(end);
        }
    }
}