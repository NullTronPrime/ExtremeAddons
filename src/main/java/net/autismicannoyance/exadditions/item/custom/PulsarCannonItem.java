package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.PulsarAttackPacket;
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

public class PulsarCannonItem extends Item {
    // Optimized pulsar cannon properties
    private static final int COOLDOWN_TICKS = 100;
    private static final int ENERGY_COST = 10;
    private static final float BASE_DAMAGE = 25.0f;
    private static final int MAX_BOUNCES = 1000;
    private static final double MAX_RANGE = 5000.0;
    private static final double ACCURACY = 0.995;
    private static final double DAMAGE_RETENTION = 0.98;
    private static final double MIN_REFLECTION_ENERGY = 0.05;
    private static final double BEAM_SPLIT_CHANCE = 0.15;

    // Performance optimizations
    private static final int MAX_CALCULATION_TIME_MS = 50; // Max 50ms calculation time
    private static final int MAX_SEGMENTS_PER_BEAM = 200; // Limit segments per beam
    private static final int MAX_TOTAL_SEGMENTS = 500; // Total segment limit
    private static final double MIN_SEGMENT_LENGTH = 0.1; // Skip tiny segments

    public PulsarCannonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(itemstack);
        }

        if (itemstack.getDamageValue() >= itemstack.getMaxDamage() - ENERGY_COST) {
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0f, 0.5f);
            }
            return InteractionResultHolder.fail(itemstack);
        }

        if (!level.isClientSide) {
            Vec3 startPos = player.getEyePosition();
            Vec3 lookVec = addSpread(player.getLookAngle(), (1.0 - ACCURACY) * 0.05);

            // Optimized server-side calculation
            PulsarCalculationResult result = calculateOptimizedPulsar((ServerLevel) level, startPos, lookVec, player);

            // Apply damage
            applyPulsarDamage((ServerLevel) level, result.segments, player);

            // Send optimized packet to clients
            PulsarAttackPacket packet = new PulsarAttackPacket(
                    startPos, lookVec, player.getId(), BASE_DAMAGE, MAX_BOUNCES, MAX_RANGE,
                    result.segments // Send pre-calculated segments
            );

            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);

            // Epic firing sounds
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 2.0f, 0.8f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, 0.5f);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        itemstack.hurtAndBreak(ENERGY_COST, player, (p) -> p.broadcastBreakEvent(hand));

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    // ==== OPTIMIZED CALCULATION ====

    private PulsarCalculationResult calculateOptimizedPulsar(ServerLevel level, Vec3 startPos, Vec3 direction, Entity shooter) {
        long startTime = System.currentTimeMillis();
        List<OptimizedSegment> allSegments = new ArrayList<>();
        List<BeamState> activeBeams = new ArrayList<>();

        // Start with primary beam
        activeBeams.add(new BeamState(startPos, direction.normalize(), BASE_DAMAGE, 0, new HashSet<>(), false, 0));

        int totalSegments = 0;
        int generation = 0;

        while (!activeBeams.isEmpty() && totalSegments < MAX_TOTAL_SEGMENTS &&
                generation < 8 && (System.currentTimeMillis() - startTime) < MAX_CALCULATION_TIME_MS) {

            List<BeamState> nextGeneration = new ArrayList<>();

            for (BeamState beam : activeBeams) {
                if (totalSegments >= MAX_TOTAL_SEGMENTS) break;

                List<OptimizedSegment> beamSegments = calculateOptimizedBeamPath(level, beam);
                allSegments.addAll(beamSegments);
                totalSegments += beamSegments.size();

                // Check for splitting (only on significant reflections)
                if (!beamSegments.isEmpty() && beam.energy > MIN_REFLECTION_ENERGY * 3) {
                    OptimizedSegment lastSegment = beamSegments.get(beamSegments.size() - 1);

                    if (lastSegment.bounceCount < MAX_BOUNCES && lastSegment.hitBlock &&
                            Math.random() < (beam.isSplit ? BEAM_SPLIT_CHANCE * 0.6 : BEAM_SPLIT_CHANCE)) { // Higher split chance

                        // Create splits with better angle distribution
                        Vec3 reflectDir = lastSegment.end.subtract(lastSegment.start).normalize();
                        Vec3 perpendicular = getPerpendicular(reflectDir);

                        double splitAngle = Math.toRadians(20); // 20-degree split
                        Vec3 split1 = rotateVector(reflectDir, perpendicular, splitAngle).normalize();
                        Vec3 split2 = rotateVector(reflectDir, perpendicular, -splitAngle).normalize();

                        float splitEnergy = beam.energy * 0.5f;

                        nextGeneration.add(new BeamState(lastSegment.end, split1, splitEnergy,
                                lastSegment.bounceCount + 1, new HashSet<>(beam.hitBlocks), true, generation + 1));
                        nextGeneration.add(new BeamState(lastSegment.end, split2, splitEnergy,
                                lastSegment.bounceCount + 1, new HashSet<>(beam.hitBlocks), true, generation + 1));
                    }
                }
            }

            activeBeams = nextGeneration;
            generation++;
        }

        return new PulsarCalculationResult(allSegments, totalSegments, generation);
    }

    private List<OptimizedSegment> calculateOptimizedBeamPath(ServerLevel level, BeamState beam) {
        List<OptimizedSegment> segments = new ArrayList<>();
        Vec3 currentPos = beam.position;
        Vec3 currentDir = beam.direction;
        double remainingRange = Math.min(MAX_RANGE, 1000); // Limit range in calculations
        float remainingEnergy = beam.energy;
        int bounces = beam.bounces;
        Set<BlockPos> hitBlocks = new HashSet<>(beam.hitBlocks);

        // Adaptive step size based on environment density
        double baseStepSize = 0.5;
        int maxSteps = Math.min(segments.size() > MAX_SEGMENTS_PER_BEAM ? MAX_SEGMENTS_PER_BEAM : 2000,
                (int)(remainingRange / baseStepSize));

        while (bounces <= MAX_BOUNCES && remainingRange > 1.0 && remainingEnergy > MIN_REFLECTION_ENERGY &&
                segments.size() < MAX_SEGMENTS_PER_BEAM) {

            // Optimized raycast with adaptive stepping
            Vec3 endPos = currentPos.add(currentDir.scale(Math.min(remainingRange, 50))); // Max 50 block segments
            BlockHitResult hitResult = optimizedRaycast(level, currentPos, endPos, hitBlocks, baseStepSize);

            Vec3 hitPos = hitResult != null ? hitResult.getLocation() : endPos;
            boolean hasBlockHit = hitResult != null;

            // Skip tiny segments
            double segmentLength = currentPos.distanceTo(hitPos);
            if (segmentLength < MIN_SEGMENT_LENGTH) {
                break;
            }

            // Create optimized segment
            OptimizedSegment segment = new OptimizedSegment(
                    currentPos, hitPos, remainingEnergy, bounces, beam.isSplit, hasBlockHit, beam.generation
            );
            segments.add(segment);

            remainingRange -= segmentLength;

            // Handle reflection
            if (hasBlockHit && bounces < MAX_BOUNCES) {
                BlockState hitState = level.getBlockState(hitResult.getBlockPos());
                float reflectivity = getOptimizedReflectivity(hitState);

                if (reflectivity > 0.1) {
                    Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());
                    Vec3 reflection = currentDir.subtract(normal.scale(2 * currentDir.dot(normal)));

                    currentPos = hitPos.add(normal.scale(0.03));
                    currentDir = reflection.normalize();
                    remainingEnergy *= DAMAGE_RETENTION * reflectivity;
                    bounces++;

                    hitBlocks.add(hitResult.getBlockPos());
                    if (bounces % 20 == 0) hitBlocks.clear(); // Clear more frequently

                    // Adaptive step size based on bounces
                    baseStepSize = Math.max(0.2, 1.0 - bounces * 0.01);
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return segments;
    }

    private BlockHitResult optimizedRaycast(ServerLevel level, Vec3 start, Vec3 end, Set<BlockPos> excludeBlocks, double stepSize) {
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (distance == 0) return null;

        Vec3 normalizedDir = direction.normalize();

        // Use larger steps for performance, but ensure accuracy
        for (double d = 0; d <= distance; d += stepSize) {
            Vec3 currentPos = start.add(normalizedDir.scale(d));
            BlockPos blockPos = BlockPos.containing(currentPos);

            if (excludeBlocks.contains(blockPos)) continue;

            BlockState blockState = level.getBlockState(blockPos);
            if (!blockState.isAir() && blockState.isSolid()) {
                Direction face = getHitFace(currentPos, blockPos);
                Vec3 hitPos = getHitPosition(currentPos, blockPos, face);
                return new BlockHitResult(hitPos, face, blockPos, false);
            }
        }
        return null;
    }

    private float getOptimizedReflectivity(BlockState blockState) {
        // Simplified reflectivity for performance
        if (blockState.is(Blocks.GLASS) || blockState.is(Blocks.WHITE_STAINED_GLASS)) return 0.95f;
        if (blockState.is(Blocks.ICE) || blockState.is(Blocks.PACKED_ICE)) return 0.90f;
        if (blockState.is(Blocks.IRON_BLOCK) || blockState.is(Blocks.DIAMOND_BLOCK)) return 0.85f;
        if (blockState.is(Blocks.WATER)) return 0.80f;
        if (blockState.is(Blocks.STONE) || blockState.is(Blocks.COBBLESTONE)) return 0.70f;
        if (blockState.is(Blocks.OBSIDIAN)) return 0.60f;
        return blockState.isSolid() ? 0.65f : 0.0f;
    }

    // ==== OPTIMIZED DAMAGE APPLICATION ====

    private void applyPulsarDamage(ServerLevel level, List<OptimizedSegment> segments, Entity shooter) {
        Set<Entity> hitEntities = new HashSet<>(); // Prevent multiple hits

        for (OptimizedSegment segment : segments) {
            if (hitEntities.size() > 50) break; // Limit entity hits for performance

            // Use larger bounding box but fewer checks
            AABB boundingBox = new AABB(segment.start, segment.end).inflate(0.8);
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

            for (Entity entity : entities) {
                if (entity == shooter || hitEntities.contains(entity)) continue;
                if (!(entity instanceof LivingEntity)) continue;

                if (fastEntityIntersection(segment, entity)) {
                    hitEntities.add(entity);

                    float damage = segment.energy;
                    DamageSource damageSource = shooter instanceof Player player ?
                            level.damageSources().playerAttack(player) : level.damageSources().magic();

                    if (entity.hurt(damageSource, damage)) {
                        // Apply knockback
                        Vec3 knockback = segment.end.subtract(segment.start).normalize()
                                .scale(Math.min(2.0, segment.energy / BASE_DAMAGE));
                        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
                    }
                }
            }
        }
    }

    private boolean fastEntityIntersection(OptimizedSegment segment, Entity entity) {
        // Simplified intersection check for performance
        AABB entityBounds = entity.getBoundingBox();
        Vec3 segmentCenter = segment.start.add(segment.end).scale(0.5);
        Vec3 entityCenter = entityBounds.getCenter();

        double distance = segmentCenter.distanceTo(entityCenter);
        double maxDistance = segment.start.distanceTo(segment.end) * 0.5 +
                Math.max(entityBounds.getXsize(), entityBounds.getZsize());

        return distance <= maxDistance;
    }

    // ==== UTILITY METHODS ====

    private Vec3 addSpread(Vec3 direction, double spread) {
        if (spread <= 0) return direction;
        double offsetX = (Math.random() - 0.5) * spread;
        double offsetY = (Math.random() - 0.5) * spread;
        double offsetZ = (Math.random() - 0.5) * spread;
        return direction.add(offsetX, offsetY, offsetZ).normalize();
    }

    private Vec3 getPerpendicular(Vec3 vector) {
        Vec3 candidate = new Vec3(0, 1, 0);
        if (Math.abs(vector.dot(candidate)) > 0.9) {
            candidate = new Vec3(1, 0, 0);
        }
        return vector.cross(candidate).normalize();
    }

    private Vec3 rotateVector(Vec3 vector, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vector.scale(cos).add(axis.cross(vector).scale(sin)).add(axis.scale(axis.dot(vector) * (1 - cos)));
    }

    private Direction getHitFace(Vec3 hitPos, BlockPos blockPos) {
        Vec3 center = Vec3.atCenterOf(blockPos);
        Vec3 diff = hitPos.subtract(center);
        double absX = Math.abs(diff.x), absY = Math.abs(diff.y), absZ = Math.abs(diff.z);

        if (absX > absY && absX > absZ) return diff.x > 0 ? Direction.EAST : Direction.WEST;
        if (absY > absZ) return diff.y > 0 ? Direction.UP : Direction.DOWN;
        return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private Vec3 getHitPosition(Vec3 rayPos, BlockPos blockPos, Direction face) {
        Vec3 min = Vec3.atLowerCornerOf(blockPos);
        Vec3 max = min.add(1, 1, 1);

        return switch (face) {
            case EAST -> new Vec3(max.x, rayPos.y, rayPos.z);
            case WEST -> new Vec3(min.x, rayPos.y, rayPos.z);
            case UP -> new Vec3(rayPos.x, max.y, rayPos.z);
            case DOWN -> new Vec3(rayPos.x, min.y, rayPos.z);
            case SOUTH -> new Vec3(rayPos.x, rayPos.y, max.z);
            case NORTH -> new Vec3(rayPos.x, rayPos.y, min.z);
        };
    }

    // ==== ITEM PROPERTIES ====

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float energyLevel = 1.0f - ((float) stack.getDamageValue() / stack.getMaxDamage());
        if (energyLevel > 0.8f) return 0xFFFFFF;
        if (energyLevel > 0.6f) return 0xAA00FF;
        if (energyLevel > 0.4f) return 0x6600CC;
        if (energyLevel > 0.2f) return 0x4400AA;
        return 0xFF0000;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float energyLevel = 1.0f - ((float) stack.getDamageValue() / stack.getMaxDamage());
        return Math.round(energyLevel * 13.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        float energyLevel = 1.0f - ((float) stack.getDamageValue() / stack.getMaxDamage());
        int energyPercent = Math.round(energyLevel * 100);

        tooltip.add(Component.literal("§d§lPULSAR CANNON"));
        tooltip.add(Component.literal("§bEnergy: §f" + energyPercent + "%"));
        tooltip.add(Component.literal("§7Damage: §c" + BASE_DAMAGE));
        tooltip.add(Component.literal("§7Max Range: §e" + (int)(MAX_RANGE/1000) + "km"));
        tooltip.add(Component.literal("§7Max Bounces: §a" + MAX_BOUNCES));
        tooltip.add(Component.literal("§7Damage Retention: §6" + (int)(DAMAGE_RETENTION * 100) + "%"));
        tooltip.add(Component.literal("§7Pierce: §d∞ UNLIMITED"));
        tooltip.add(Component.literal("§7Split Chance: §5" + (int)(BEAM_SPLIT_CHANCE * 100) + "%"));

        if (energyLevel < 0.1f) {
            tooltip.add(Component.literal("§4§l⚠ CRITICAL ENERGY"));
        } else if (energyLevel > 0.9f) {
            tooltip.add(Component.literal("§d§l✦ MAXIMUM POWER"));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8\"Optimized for enclosed spaces\""));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ==== DATA CLASSES ====

    private static class BeamState {
        public final Vec3 position;
        public final Vec3 direction;
        public final float energy;
        public final int bounces;
        public final Set<BlockPos> hitBlocks;
        public final boolean isSplit;
        public final int generation;

        public BeamState(Vec3 position, Vec3 direction, float energy, int bounces,
                         Set<BlockPos> hitBlocks, boolean isSplit, int generation) {
            this.position = position;
            this.direction = direction;
            this.energy = energy;
            this.bounces = bounces;
            this.hitBlocks = hitBlocks;
            this.isSplit = isSplit;
            this.generation = generation;
        }
    }

    public static class OptimizedSegment {
        public final Vec3 start;
        public final Vec3 end;
        public final float energy;
        public final int bounceCount;
        public final boolean isSplit;
        public final boolean hitBlock;
        public final int generation;

        public OptimizedSegment(Vec3 start, Vec3 end, float energy, int bounceCount,
                                boolean isSplit, boolean hitBlock, int generation) {
            this.start = start;
            this.end = end;
            this.energy = energy;
            this.bounceCount = bounceCount;
            this.isSplit = isSplit;
            this.hitBlock = hitBlock;
            this.generation = generation;
        }
    }

    private static class PulsarCalculationResult {
        public final List<OptimizedSegment> segments;
        public final int totalSegments;
        public final int generations;

        public PulsarCalculationResult(List<OptimizedSegment> segments, int totalSegments, int generations) {
            this.segments = segments;
            this.totalSegments = totalSegments;
            this.generations = generations;
        }
    }
}