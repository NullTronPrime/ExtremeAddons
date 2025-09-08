package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.LaserAttackPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LaserRifleItem extends Item {
    // Enhanced laser rifle properties
    private static final int COOLDOWN_TICKS = 25;     // 1.25 seconds (faster firing)
    private static final int ENERGY_COST = 2;         // Higher energy cost per shot
    private static final float BASE_DAMAGE = 8.0f;    // Higher base damage
    private static final int MAX_BOUNCES = 25;        // Much more bounces for mirror-like reflections
    private static final double MAX_RANGE = 200.0;    // Much longer range
    private static final double ACCURACY = 0.98;      // High accuracy (less spread)

    // Server-side physics constants
    private static final double PIERCING_THRESHOLD = 0.7;
    private static final int MAX_PIERCE_ENTITIES = 10;
    private static final double REFLECTION_EFFICIENCY = 0.95;
    private static final double MIN_REFLECTION_ENERGY = 0.1;

    public LaserRifleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        // Check cooldown
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(itemstack);
        }

        // Check energy/durability
        if (itemstack.getDamageValue() >= itemstack.getMaxDamage() - ENERGY_COST) {
            // Play empty energy sound
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.8f);
            }
            return InteractionResultHolder.fail(itemstack);
        }

        if (!level.isClientSide) {
            // Calculate enhanced firing direction with slight accuracy variation
            Vec3 startPos = player.getEyePosition();
            Vec3 baseLookVec = player.getLookAngle();

            // Add slight inaccuracy for realism (very small for high-tech weapon)
            double spread = (1.0 - ACCURACY) * 0.1; // Convert accuracy to spread
            Vec3 lookVec = addSpread(baseLookVec, spread);

            // SERVER-SIDE: Handle actual damage and physics
            handleServerSideLaser((ServerLevel) level, startPos, lookVec, player,
                    BASE_DAMAGE, MAX_BOUNCES, MAX_RANGE);

            // CLIENT-SIDE: Send visual effects packet to all nearby clients
            LaserAttackPacket packet = new LaserAttackPacket(
                    startPos,
                    lookVec,
                    player.getId(),
                    BASE_DAMAGE,      // Much higher damage
                    MAX_BOUNCES,      // Many more bounces
                    MAX_RANGE         // Much longer range
            );

            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    packet
            );

            // Enhanced firing sound with reverb effect
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 2.2f);

            // Add secondary sound for more impact
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.3f, 3.0f);
        }

        // Apply cooldown and durability damage
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        itemstack.hurtAndBreak(ENERGY_COST, player, (p) -> p.broadcastBreakEvent(hand));

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    // ==== SERVER-SIDE LASER HANDLING ====

    private void handleServerSideLaser(ServerLevel level, Vec3 startPos, Vec3 direction,
                                       Entity shooter, float baseDamage, int maxBounces, double maxRange) {

        // Calculate laser path with server-side physics
        List<LaserSegment> segments = calculateLaserPath(level, startPos, direction,
                maxBounces, maxRange, baseDamage);

        // Apply damage to entities hit by laser segments
        applyLaserDamage(level, segments, shooter);

        // Play server-side sounds
        playLaserSounds(level, segments);
    }

    private List<LaserSegment> calculateLaserPath(ServerLevel level, Vec3 startPos,
                                                  Vec3 direction, int maxBounces, double maxRange, float initialEnergy) {

        List<LaserSegment> segments = new ArrayList<>();
        Vec3 currentPos = startPos;
        Vec3 currentDir = direction.normalize();
        double remainingRange = maxRange;
        float remainingEnergy = initialEnergy;
        int bounces = 0;
        Set<BlockPos> hitBlocks = new HashSet<>();

        while (bounces <= maxBounces && remainingRange > 0 && remainingEnergy > MIN_REFLECTION_ENERGY) {
            // Raycast for next collision
            Vec3 endPos = currentPos.add(currentDir.scale(remainingRange));
            BlockHitResult hitResult = performRaycast(level, currentPos, endPos, hitBlocks);

            Vec3 hitPos;
            boolean hasBlockHit = hitResult != null;

            if (hasBlockHit) {
                hitPos = hitResult.getLocation();
                hitPos = hitPos.subtract(currentDir.scale(0.001)); // Prevent z-fighting
            } else {
                hitPos = endPos;
            }

            // Create segment
            LaserSegment segment = new LaserSegment(currentPos, hitPos, bounces == 0,
                    remainingEnergy, bounces);
            segments.add(segment);

            // Update remaining range
            double segmentLength = currentPos.distanceTo(hitPos);
            remainingRange -= segmentLength;

            // Handle reflections
            if (hasBlockHit && bounces < maxBounces && remainingRange > 0) {
                BlockState hitState = level.getBlockState(hitResult.getBlockPos());
                float reflectivity = getBlockReflectivity(hitState);

                if (reflectivity > 0) {
                    // Calculate reflection
                    Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());
                    Vec3 reflection = currentDir.subtract(normal.scale(2 * currentDir.dot(normal)));

                    // Update for next iteration
                    currentPos = hitPos.add(normal.scale(0.02));
                    currentDir = reflection.normalize();
                    remainingEnergy *= (reflectivity * REFLECTION_EFFICIENCY);
                    bounces++;
                    hitBlocks.add(hitResult.getBlockPos());
                } else {
                    break; // Laser absorbed
                }
            } else {
                break;
            }
        }

        return segments;
    }

    private BlockHitResult performRaycast(ServerLevel level, Vec3 start, Vec3 end, Set<BlockPos> excludeBlocks) {
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (distance == 0) return null;

        Vec3 normalizedDir = direction.normalize();
        double step = 0.05;

        for (double d = 0; d <= distance; d += step) {
            Vec3 currentPos = start.add(normalizedDir.scale(d));
            BlockPos blockPos = BlockPos.containing(currentPos);

            if (excludeBlocks.contains(blockPos)) continue;

            BlockState blockState = level.getBlockState(blockPos);

            if (!blockState.isAir() && blockState.isSolid()) {
                Direction face = calculateHitFace(currentPos, blockPos, normalizedDir);
                Vec3 preciseHitPos = calculatePreciseHitPosition(currentPos, blockPos, face);
                return new BlockHitResult(preciseHitPos, face, blockPos, false);
            }
        }

        return null;
    }

    private Direction calculateHitFace(Vec3 hitPos, BlockPos blockPos, Vec3 rayDir) {
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

    private Vec3 calculatePreciseHitPosition(Vec3 rayPos, BlockPos blockPos, Direction face) {
        Vec3 blockMin = Vec3.atLowerCornerOf(blockPos);
        Vec3 blockMax = blockMin.add(1, 1, 1);

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

    private float getBlockReflectivity(BlockState blockState) {
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

        return blockState.isSolid() ? 0.25f : 0.0f;
    }

    private void applyLaserDamage(ServerLevel level, List<LaserSegment> segments, Entity shooter) {
        Set<Entity> damagedEntities = new HashSet<>();

        for (LaserSegment segment : segments) {
            // Find entities along this segment
            AABB boundingBox = new AABB(segment.start, segment.end).inflate(0.3);
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

            for (Entity entity : entities) {
                if (entity == shooter) continue;
                if (!(entity instanceof LivingEntity livingEntity)) continue;
                if (damagedEntities.contains(entity)) continue;

                // Check intersection
                if (intersectsEntity(segment, entity)) {
                    // Calculate piercing damage
                    float damage = segment.energy * (float)Math.pow(PIERCING_THRESHOLD, damagedEntities.size());

                    // Create appropriate damage source
                    DamageSource damageSource;
                    if (shooter instanceof Player player) {
                        damageSource = level.damageSources().playerAttack(player);
                    } else {
                        damageSource = level.damageSources().magic();
                    }

                    // Apply damage
                    boolean damaged = entity.hurt(damageSource, damage);

                    if (damaged) {
                        damagedEntities.add(entity);

                        // Apply knockback
                        Vec3 knockback = segment.getDirection().normalize()
                                .scale(0.5 * (segment.energy / 5.0));
                        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                        // Play hit sound
                        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.8f, 1.5f);

                        // Stop after max piercing
                        if (damagedEntities.size() >= MAX_PIERCE_ENTITIES) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean intersectsEntity(LaserSegment segment, Entity entity) {
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

    private void playLaserSounds(ServerLevel level, List<LaserSegment> segments) {
        if (!segments.isEmpty()) {
            // Impact sound at final position
            Vec3 finalImpact = segments.get(segments.size() - 1).end;
            level.playSound(null, finalImpact.x, finalImpact.y, finalImpact.z,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.8f);

            // Reflection sounds (limit to first 5 for performance)
            for (int i = 1; i < segments.size() && i <= 5; i++) {
                Vec3 reflectionPos = segments.get(i).start;
                level.playSound(null, reflectionPos.x, reflectionPos.y, reflectionPos.z,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4f, 2.0f);
            }
        }
    }

    // ==== UTILITY METHODS ====

    /**
     * Adds slight spread to the firing direction for realism
     */
    private Vec3 addSpread(Vec3 direction, double spread) {
        if (spread <= 0) return direction;

        // Generate random offset within spread cone
        double offsetX = (Math.random() - 0.5) * spread;
        double offsetY = (Math.random() - 0.5) * spread;
        double offsetZ = (Math.random() - 0.5) * spread;

        return direction.add(offsetX, offsetY, offsetZ).normalize();
    }

    // ==== ITEM PROPERTIES ====

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Enhanced energy bar - cyan to red gradient based on energy level
        float energyLevel = 1.0f - ((float) stack.getDamageValue() / stack.getMaxDamage());

        if (energyLevel > 0.6f) {
            return 0x00FFFF; // Bright cyan for high energy
        } else if (energyLevel > 0.3f) {
            return 0x44AAFF; // Blue for medium energy
        } else if (energyLevel > 0.1f) {
            return 0xFF8800; // Orange for low energy
        } else {
            return 0xFF0000; // Red for critical energy
        }
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        // Enhanced energy bar visualization
        float energyLevel = 1.0f - ((float) stack.getDamageValue() / stack.getMaxDamage());
        return Math.round(energyLevel * 13.0f);
    }

    /**
     * Enhanced tooltip information
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        float energyLevel = 1.0f - ((float) stack.getDamageValue() / stack.getMaxDamage());
        int energyPercent = Math.round(energyLevel * 100);

        tooltip.add(Component.literal("§bEnergy: §f" + energyPercent + "%"));
        tooltip.add(Component.literal("§7Damage: §c" + BASE_DAMAGE));
        tooltip.add(Component.literal("§7Max Range: §e" + (int)MAX_RANGE + "m"));
        tooltip.add(Component.literal("§7Max Bounces: §a" + MAX_BOUNCES));
        tooltip.add(Component.literal("§7Pierce Entities: §6" + MAX_PIERCE_ENTITIES));

        if (energyLevel < 0.2f) {
            tooltip.add(Component.literal("§4§l⚠ LOW ENERGY"));
        } else if (energyLevel > 0.8f) {
            tooltip.add(Component.literal("§b§l✦ FULLY CHARGED"));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Enchanted glow effect when at high energy
        float energyLevel = 1.0f - ((float) stack.getDamageValue() / stack.getMaxDamage());
        return energyLevel > 0.8f;
    }

    // ==== LASER SEGMENT CLASS ====

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