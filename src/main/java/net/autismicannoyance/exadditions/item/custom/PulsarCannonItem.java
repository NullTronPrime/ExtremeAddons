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

import java.util.*;

public class PulsarCannonItem extends Item {
    // Enhanced aggressive light physics properties
    private static final int COOLDOWN_TICKS = 80;
    private static final int ENERGY_COST = 12;
    private static final float BASE_DAMAGE = 30.0f;
    private static final int MAX_BOUNCES = 2000;
    private static final double MAX_RANGE = 8000.0;
    private static final double ACCURACY = 0.998;
    private static final double DAMAGE_RETENTION = 0.985;
    private static final double MIN_REFLECTION_ENERGY = 0.02;
    private static final double BEAM_SPLIT_CHANCE = 0.45;
    private static final double PHOTON_SPLIT_CHANCE = 0.25;

    // Performance optimizations
    private static final int MAX_CALCULATION_TIME_MS = 80;
    private static final int MAX_SEGMENTS_PER_BEAM = 300;
    private static final int MAX_TOTAL_SEGMENTS = 1200;
    private static final double MIN_SEGMENT_LENGTH = 0.05;
    private static final int MAX_GENERATIONS = 12;

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
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0f, 0.3f);
            }
            return InteractionResultHolder.fail(itemstack);
        }

        if (!level.isClientSide) {
            Vec3 startPos = player.getEyePosition();
            Vec3 lookVec = addSpread(player.getLookAngle(), (1.0 - ACCURACY) * 0.03);

            // Enhanced calculation
            PhotonCalculationResult result = calculateAdvancedPhotonBeam((ServerLevel) level, startPos, lookVec, player);

            // Apply damage
            applyPhotonDamage((ServerLevel) level, result.segments, player);

            // Send packet to clients
            PulsarAttackPacket packet = new PulsarAttackPacket(
                    startPos, lookVec, player.getId(), BASE_DAMAGE, MAX_BOUNCES, MAX_RANGE,
                    result.segments
            );

            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);

            // Enhanced sound effects
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.5f, 0.4f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.8f, 1.8f);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        itemstack.hurtAndBreak(ENERGY_COST, player, (p) -> p.broadcastBreakEvent(hand));

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    private PhotonCalculationResult calculateAdvancedPhotonBeam(ServerLevel level, Vec3 startPos, Vec3 direction, Entity shooter) {
        long startTime = System.currentTimeMillis();
        List<OptimizedSegment> allSegments = new ArrayList<>();
        Queue<PhotonBeam> activeBeams = new ArrayDeque<>();

        // Initialize primary beam
        activeBeams.add(new PhotonBeam(startPos, direction.normalize(), BASE_DAMAGE, 0,
                new HashSet<>(), false, 0, 1.0, PhotonType.PRIMARY));

        int totalSegments = 0;
        int generation = 0;

        while (!activeBeams.isEmpty() && totalSegments < MAX_TOTAL_SEGMENTS &&
                generation < MAX_GENERATIONS && (System.currentTimeMillis() - startTime) < MAX_CALCULATION_TIME_MS) {

            Queue<PhotonBeam> nextGeneration = new ArrayDeque<>();
            int beamsThisGeneration = activeBeams.size();

            for (int i = 0; i < beamsThisGeneration && totalSegments < MAX_TOTAL_SEGMENTS; i++) {
                PhotonBeam beam = activeBeams.poll();
                if (beam == null) break;

                List<OptimizedSegment> beamSegments = calculatePhotonPath(level, beam);
                allSegments.addAll(beamSegments);
                totalSegments += beamSegments.size();

                // Advanced splitting logic
                if (!beamSegments.isEmpty() && beam.energy > MIN_REFLECTION_ENERGY * 2) {
                    OptimizedSegment lastSegment = beamSegments.get(beamSegments.size() - 1);

                    if (lastSegment.bounceCount < MAX_BOUNCES && lastSegment.hitBlock) {
                        List<PhotonBeam> newBeams = calculatePhotonSplitting(beam, lastSegment, generation);
                        nextGeneration.addAll(newBeams);
                    }
                }

                // Photon scattering
                if (beam.type == PhotonType.PRIMARY && Math.random() < PHOTON_SPLIT_CHANCE && generation < 3) {
                    List<PhotonBeam> scatteredBeams = createPhotonScattering(beam, generation);
                    nextGeneration.addAll(scatteredBeams);
                }
            }

            activeBeams.addAll(nextGeneration);
            generation++;
        }

        return new PhotonCalculationResult(allSegments, totalSegments, generation);
    }

    private List<OptimizedSegment> calculatePhotonPath(ServerLevel level, PhotonBeam beam) {
        List<OptimizedSegment> segments = new ArrayList<>();
        Vec3 currentPos = beam.position;
        Vec3 currentDir = beam.direction;
        double remainingRange = Math.min(MAX_RANGE * beam.intensity, 2000);
        float remainingEnergy = beam.energy;
        int bounces = beam.bounces;
        Set<BlockPos> hitBlocks = new HashSet<>(beam.hitBlocks);

        double baseStepSize = beam.type == PhotonType.PRIMARY ? 0.3 : 0.5;
        if (beam.intensity < 0.1) baseStepSize *= 2;

        while (bounces <= MAX_BOUNCES && remainingRange > 0.5 &&
                remainingEnergy > MIN_REFLECTION_ENERGY && segments.size() < MAX_SEGMENTS_PER_BEAM) {

            Vec3 endPos = currentPos.add(currentDir.scale(Math.min(remainingRange, 100)));
            BlockHitResult hitResult = optimizedRaycast(level, currentPos, endPos, hitBlocks, baseStepSize);

            Vec3 hitPos = hitResult != null ? hitResult.getLocation() : endPos;
            boolean hasBlockHit = hitResult != null;

            double segmentLength = currentPos.distanceTo(hitPos);
            if (segmentLength < MIN_SEGMENT_LENGTH) break;

            OptimizedSegment segment = new OptimizedSegment(
                    currentPos, hitPos, remainingEnergy, bounces, beam.isSplit,
                    hasBlockHit, beam.generation, beam.type, beam.intensity
            );
            segments.add(segment);

            remainingRange -= segmentLength;

            if (hasBlockHit && bounces < MAX_BOUNCES) {
                BlockPos hitBlockPos = hitResult.getBlockPos();
                BlockState hitState = level.getBlockState(hitBlockPos);

                float reflectivity = getEnhancedReflectivity(hitState);

                if (reflectivity > 0.05) {
                    Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());
                    Vec3 reflection = calculateReflection(currentDir, normal);

                    currentPos = hitPos.add(normal.scale(0.02 + Math.random() * 0.01));
                    currentDir = reflection.normalize();
                    remainingEnergy *= DAMAGE_RETENTION * reflectivity;
                    bounces++;

                    hitBlocks.add(hitBlockPos);

                    if (bounces % 50 == 0 && hitBlocks.size() > 20) {
                        hitBlocks.clear();
                    }

                    baseStepSize = Math.max(0.1, reflectivity * 0.5);
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return segments;
    }

    private List<PhotonBeam> calculatePhotonSplitting(PhotonBeam beam, OptimizedSegment lastSegment, int generation) {
        List<PhotonBeam> newBeams = new ArrayList<>();

        double splitChance = BEAM_SPLIT_CHANCE * beam.intensity;
        if (beam.type == PhotonType.SCATTERED) splitChance *= 0.7;

        if (Math.random() > splitChance) return newBeams;

        Vec3 incidentDir = lastSegment.end.subtract(lastSegment.start).normalize();

        int splitCount = generation < 4 ? (2 + (int)(Math.random() * 3)) : 2;

        for (int i = 0; i < splitCount; i++) {
            double baseAngle = Math.PI / 6 + (Math.random() - 0.5) * Math.PI / 4;
            double rotationAngle = (2.0 * Math.PI * i) / splitCount;

            Vec3 perpendicular = getPerpendicular(incidentDir);
            Vec3 splitAxis = rotateVectorAroundAxis(perpendicular, incidentDir, rotationAngle);
            Vec3 splitDirection = rotateVectorAroundAxis(incidentDir, splitAxis, baseAngle);

            float splitEnergy = beam.energy * (0.3f + (float)Math.random() * 0.4f) / splitCount;
            double splitIntensity = beam.intensity * (0.4 + Math.random() * 0.4);

            PhotonType newType = beam.type == PhotonType.PRIMARY ? PhotonType.SPLIT : PhotonType.SCATTERED;

            newBeams.add(new PhotonBeam(
                    lastSegment.end,
                    splitDirection.normalize(),
                    splitEnergy,
                    lastSegment.bounceCount + 1,
                    new HashSet<>(beam.hitBlocks),
                    true,
                    generation + 1,
                    splitIntensity,
                    newType
            ));
        }

        return newBeams;
    }

    private List<PhotonBeam> createPhotonScattering(PhotonBeam beam, int generation) {
        List<PhotonBeam> scatteredBeams = new ArrayList<>();

        int scatterCount = 2 + (int)(Math.random() * 2);

        for (int i = 0; i < scatterCount; i++) {
            double theta = Math.random() * Math.PI * 0.6;
            double phi = Math.random() * Math.PI * 2;

            Vec3 scatterDir = new Vec3(
                    Math.sin(theta) * Math.cos(phi),
                    Math.cos(theta),
                    Math.sin(theta) * Math.sin(phi)
            ).normalize();

            Vec3 alignedDir = beam.direction.add(scatterDir).normalize();

            scatteredBeams.add(new PhotonBeam(
                    beam.position.add(beam.direction.scale(0.1)),
                    alignedDir,
                    beam.energy * 0.1f,
                    beam.bounces,
                    new HashSet<>(),
                    false,
                    generation + 1,
                    beam.intensity * 0.2,
                    PhotonType.SCATTERED
            ));
        }

        return scatteredBeams;
    }

    private float getEnhancedReflectivity(BlockState blockState) {
        if (blockState.is(Blocks.GLASS) || blockState.is(Blocks.WHITE_STAINED_GLASS)) return 0.98f;
        if (blockState.is(Blocks.ICE) || blockState.is(Blocks.PACKED_ICE) || blockState.is(Blocks.BLUE_ICE)) return 0.95f;
        if (blockState.is(Blocks.IRON_BLOCK) || blockState.is(Blocks.GOLD_BLOCK)) return 0.92f;
        if (blockState.is(Blocks.DIAMOND_BLOCK) || blockState.is(Blocks.EMERALD_BLOCK)) return 0.96f;
        if (blockState.is(Blocks.WATER)) return 0.85f;
        if (blockState.is(Blocks.QUARTZ_BLOCK) || blockState.is(Blocks.WHITE_CONCRETE)) return 0.80f;
        if (blockState.is(Blocks.POLISHED_ANDESITE) || blockState.is(Blocks.POLISHED_GRANITE)) return 0.75f;
        if (blockState.is(Blocks.STONE) || blockState.is(Blocks.COBBLESTONE)) return 0.65f;
        if (blockState.is(Blocks.DEEPSLATE) || blockState.is(Blocks.BLACKSTONE)) return 0.70f;
        if (blockState.is(Blocks.OBSIDIAN)) return 0.88f;
        if (blockState.is(Blocks.NETHERITE_BLOCK)) return 0.94f;
        if (blockState.is(Blocks.SNOW_BLOCK) || blockState.is(Blocks.SNOW)) return 0.90f;

        return blockState.isSolid() ? 0.60f : 0.0f;
    }

    private BlockHitResult optimizedRaycast(ServerLevel level, Vec3 start, Vec3 end, Set<BlockPos> excludeBlocks, double stepSize) {
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (distance == 0) return null;

        Vec3 normalizedDir = direction.normalize();
        double adaptiveStep = Math.min(stepSize * 2, distance / 20);

        for (double d = 0; d <= distance; d += adaptiveStep) {
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

    private void applyPhotonDamage(ServerLevel level, List<OptimizedSegment> segments, Entity shooter) {
        Set<Entity> hitEntities = new HashSet<>();

        for (OptimizedSegment segment : segments) {
            if (hitEntities.size() > 100) break;

            AABB boundingBox = new AABB(segment.start, segment.end).inflate(0.6);
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

            for (Entity entity : entities) {
                if (entity == shooter || hitEntities.contains(entity)) continue;
                if (!(entity instanceof LivingEntity)) continue;

                if (preciseEntityIntersection(segment, entity)) {
                    hitEntities.add(entity);

                    float damage = segment.energy * (segment.type == PhotonType.PRIMARY ? 1.0f : 0.7f);
                    DamageSource damageSource = shooter instanceof Player player ?
                            level.damageSources().playerAttack(player) : level.damageSources().magic();

                    if (entity.hurt(damageSource, damage)) {
                        Vec3 knockback = segment.end.subtract(segment.start).normalize()
                                .scale(Math.min(3.0, segment.energy / BASE_DAMAGE * segment.intensity));
                        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
                    }
                }
            }
        }
    }

    private boolean preciseEntityIntersection(OptimizedSegment segment, Entity entity) {
        AABB entityBounds = entity.getBoundingBox();
        Vec3 segmentDir = segment.end.subtract(segment.start).normalize();
        double segmentLength = segment.start.distanceTo(segment.end);

        Vec3 toEntity = entityBounds.getCenter().subtract(segment.start);
        double projection = Math.max(0, Math.min(segmentLength, toEntity.dot(segmentDir)));

        Vec3 closestPoint = segment.start.add(segmentDir.scale(projection));
        double distance = closestPoint.distanceTo(entityBounds.getCenter());

        double hitRadius = Math.max(entityBounds.getXsize(), entityBounds.getZsize()) * 0.6;
        return distance <= hitRadius;
    }

    // Utility methods
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

    private Vec3 rotateVectorAroundAxis(Vec3 vector, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3 normalizedAxis = axis.normalize();

        return vector.scale(cos)
                .add(normalizedAxis.cross(vector).scale(sin))
                .add(normalizedAxis.scale(normalizedAxis.dot(vector) * (1 - cos)));
    }

    private Vec3 calculateReflection(Vec3 incident, Vec3 normal) {
        return incident.subtract(normal.scale(2 * incident.dot(normal)));
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

        return switch (face) {
            case EAST -> new Vec3(blockMax.x, rayPos.y, rayPos.z);
            case WEST -> new Vec3(blockMin.x, rayPos.y, rayPos.z);
            case UP -> new Vec3(rayPos.x, blockMax.y, rayPos.z);
            case DOWN -> new Vec3(rayPos.x, blockMin.y, rayPos.z);
            case SOUTH -> new Vec3(rayPos.x, rayPos.y, blockMax.z);
            case NORTH -> new Vec3(rayPos.x, rayPos.y, blockMin.z);
        };
    }

    // Item properties
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float energyLevel = 1.0f - ((float) stack.getDamageValue() / stack.getMaxDamage());
        if (energyLevel > 0.8f) return 0xFFFFFF;
        if (energyLevel > 0.6f) return 0xDD00FF;
        if (energyLevel > 0.4f) return 0x8800DD;
        if (energyLevel > 0.2f) return 0x5500AA;
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

        tooltip.add(Component.literal("§d§l⚡ PHOTON PULSAR CANNON ⚡"));
        tooltip.add(Component.literal("§bEnergy: §f" + energyPercent + "%"));
        tooltip.add(Component.literal("§7Damage: §c" + BASE_DAMAGE));
        tooltip.add(Component.literal("§7Max Range: §e" + (int)(MAX_RANGE/1000) + "km"));
        tooltip.add(Component.literal("§7Max Bounces: §a" + MAX_BOUNCES + " §7(AGGRESSIVE)"));
        tooltip.add(Component.literal("§7Energy Retention: §6" + (int)(DAMAGE_RETENTION * 1000) / 10.0f + "%"));
        tooltip.add(Component.literal("§7Pierce: §d∞ UNLIMITED"));
        tooltip.add(Component.literal("§7Split Chance: §5" + (int)(BEAM_SPLIT_CHANCE * 100) + "% §7(HIGH)"));
        tooltip.add(Component.literal("§7Photon Scatter: §3" + (int)(PHOTON_SPLIT_CHANCE * 100) + "%"));
        tooltip.add(Component.literal("§7Max Generations: §2" + MAX_GENERATIONS));

        if (energyLevel < 0.1f) {
            tooltip.add(Component.literal("§4§l⚠ CRITICAL ENERGY"));
        } else if (energyLevel > 0.9f) {
            tooltip.add(Component.literal("§d§l✦ PHOTONIC OVERDRIVE"));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8\"Light behaves as both wave and particle\""));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // Data classes and enums
    public enum PhotonType {
        PRIMARY,
        SPLIT,
        SCATTERED
    }

    private static class PhotonBeam {
        public final Vec3 position;
        public final Vec3 direction;
        public final float energy;
        public final int bounces;
        public final Set<BlockPos> hitBlocks;
        public final boolean isSplit;
        public final int generation;
        public final double intensity;
        public final PhotonType type;

        public PhotonBeam(Vec3 position, Vec3 direction, float energy, int bounces,
                          Set<BlockPos> hitBlocks, boolean isSplit, int generation,
                          double intensity, PhotonType type) {
            this.position = position;
            this.direction = direction;
            this.energy = energy;
            this.bounces = bounces;
            this.hitBlocks = hitBlocks;
            this.isSplit = isSplit;
            this.generation = generation;
            this.intensity = intensity;
            this.type = type;
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
        public final PhotonType type;
        public final double intensity;

        public OptimizedSegment(Vec3 start, Vec3 end, float energy, int bounceCount,
                                boolean isSplit, boolean hitBlock, int generation,
                                PhotonType type, double intensity) {
            this.start = start;
            this.end = end;
            this.energy = energy;
            this.bounceCount = bounceCount;
            this.isSplit = isSplit;
            this.hitBlock = hitBlock;
            this.generation = generation;
            this.type = type;
            this.intensity = intensity;
        }

        // Legacy constructor for compatibility
        public OptimizedSegment(Vec3 start, Vec3 end, float energy, int bounceCount,
                                boolean isSplit, boolean hitBlock, int generation) {
            this(start, end, energy, bounceCount, isSplit, hitBlock, generation,
                    PhotonType.PRIMARY, 1.0);
        }
    }

    private static class PhotonCalculationResult {
        public final List<OptimizedSegment> segments;
        public final int totalSegments;
        public final int generations;

        public PhotonCalculationResult(List<OptimizedSegment> segments, int totalSegments, int generations) {
            this.segments = segments;
            this.totalSegments = totalSegments;
            this.generations = generations;
        }
    }
}