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
    // Enhanced aggressive light physics properties with infinite bouncing and piercing
    private static final int COOLDOWN_TICKS = 80;
    private static final int ENERGY_COST = 12;
    private static final float BASE_DAMAGE = 30.0f;
    private static final int MAX_BOUNCES = Integer.MAX_VALUE; // Infinite bounces
    private static final double MAX_RANGE = 15000.0; // Much longer range
    private static final double ACCURACY = 0.998;
    private static final double DAMAGE_RETENTION = 0.995; // Better retention for more bouncing
    private static final double SPLIT_DAMAGE_RETENTION = 0.992; // Better retention for splits
    private static final double MIN_REFLECTION_ENERGY = 0.005; // Much lower minimum for extreme bouncing
    private static final double BEAM_SPLIT_CHANCE = 0.30; // 30% split chance as requested
    private static final double PHOTON_SPLIT_CHANCE = 0.45; // Higher photon splitting
    private static final int BOUNCE_DELAY_TICKS = 1; // 1 tick delay after bouncing as requested
    private static final int MAX_SPLIT_GENERATIONS = 2; // Splits can only split twice more

    // Performance optimizations for extreme bouncing
    private static final int MAX_CALCULATION_TIME_MS = 150; // More time for extreme calculations
    private static final int MAX_SEGMENTS_PER_BEAM = 800; // Much more segments per beam
    private static final int MAX_TOTAL_SEGMENTS = 3000; // Much more total segments
    private static final double MIN_SEGMENT_LENGTH = 0.02; // Smaller minimum for precision
    private static final int MAX_GENERATIONS = 25; // More generations allowed

    // Delayed calculation system
    private static final Map<String, DelayedBeamCalculation> pendingCalculations = new HashMap<>();

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

            // Start enhanced calculation with delayed bouncing
            startDelayedPhotonBeamCalculation((ServerLevel) level, startPos, lookVec, player);

            // Enhanced sound effects
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.8f, 0.4f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.9f);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        itemstack.hurtAndBreak(ENERGY_COST, player, (p) -> p.broadcastBreakEvent(hand));

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    private void startDelayedPhotonBeamCalculation(ServerLevel level, Vec3 startPos, Vec3 direction, Player shooter) {
        String calculationId = UUID.randomUUID().toString();
        DelayedBeamCalculation calculation = new DelayedBeamCalculation(
                level, startPos, direction, shooter, calculationId, 0
        );

        pendingCalculations.put(calculationId, calculation);

        // Start the first segment with 1 tick delay as requested
        level.getServer().execute(() -> processDelayedCalculation(calculationId));
    }

    public static void processDelayedCalculation(String calculationId) {
        DelayedBeamCalculation calculation = pendingCalculations.get(calculationId);
        if (calculation == null) return;

        long startTime = System.currentTimeMillis();

        // Process one generation of beams
        List<PhotonBeam> newBeams = new ArrayList<>();
        boolean hasMoreWork = false;

        for (PhotonBeam beam : calculation.activeBeams) {
            if (calculation.totalSegments >= MAX_TOTAL_SEGMENTS) break;
            if ((System.currentTimeMillis() - startTime) > MAX_CALCULATION_TIME_MS / 4) break;

            List<OptimizedSegment> beamSegments = calculatePhotonPath(calculation.level, beam);
            calculation.allSegments.addAll(beamSegments);
            calculation.totalSegments += beamSegments.size();

            // Check if beam segments indicate more bouncing will happen
            if (!beamSegments.isEmpty()) {
                OptimizedSegment lastSegment = beamSegments.get(beamSegments.size() - 1);

                // If the last segment has remaining energy, there will be more bouncing
                if (lastSegment.energy > MIN_REFLECTION_ENERGY * 2) {
                    hasMoreWork = true; // There might be more bouncing
                }

                // Create splits as offshoots if split generations haven't been exhausted
                if (beam.energy > MIN_REFLECTION_ENERGY * 1.5 && lastSegment.hitBlock && beam.splitGenerationsRemaining > 0) {
                    List<PhotonBeam> splitBeams = calculateEnhancedPhotonSplitting(beam, lastSegment, calculation.generation);
                    newBeams.addAll(splitBeams); // These are offshoots
                }

                // Enhanced photon scattering as additional offshoots
                if (beam.type == PhotonType.PRIMARY && Math.random() < PHOTON_SPLIT_CHANCE && calculation.generation < 5) {
                    List<PhotonBeam> scatteredBeams = createEnhancedPhotonScattering(beam, calculation.generation);
                    newBeams.addAll(scatteredBeams);
                }
            }
        }

        // Apply damage for current segments (infinite piercing)
        applyEnhancedPhotonDamage(calculation.level, calculation.allSegments, calculation.shooter);

        if (hasMoreWork && calculation.generation < MAX_GENERATIONS && (newBeams.size() > 0 || !calculation.activeBeams.isEmpty())) {
            // Continue with new beams (offshoots only, main beams continue automatically via calculatePhotonPath)
            calculation.activeBeams.clear();
            calculation.activeBeams.addAll(newBeams); // Only the new split/scattered beams
            calculation.generation++;

            // Schedule with BOUNCE_DELAY_TICKS delay as requested
            scheduleWithDelay(calculation.level, () -> processDelayedCalculation(calculationId), BOUNCE_DELAY_TICKS);
        } else {
            // Calculation complete, send final result
            finishDelayedCalculation(calculation);
            pendingCalculations.remove(calculationId);
        }
    }

    private static void scheduleWithDelay(ServerLevel level, Runnable task, int delayTicks) {
        if (delayTicks <= 0) {
            level.getServer().execute(task);
        } else {
            // Schedule with the requested delay
            for (int i = 0; i < delayTicks; i++) {
                level.getServer().execute(() -> level.getServer().execute(task));
            }
        }
    }

    private static void finishDelayedCalculation(DelayedBeamCalculation calculation) {
        // Send packet to clients with all calculated segments
        PulsarAttackPacket packet = new PulsarAttackPacket(
                calculation.startPos, calculation.direction, calculation.shooter.getId(),
                BASE_DAMAGE, MAX_BOUNCES, MAX_RANGE, calculation.allSegments
        );

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> calculation.shooter), packet);
    }

    private static List<OptimizedSegment> calculatePhotonPath(ServerLevel level, PhotonBeam beam) {
        List<OptimizedSegment> segments = new ArrayList<>();
        Vec3 currentPos = beam.position;
        Vec3 currentDir = beam.direction;
        double remainingRange = Math.min(MAX_RANGE * beam.intensity, 5000);
        float remainingEnergy = beam.energy;
        int bounces = beam.bounces;
        Set<BlockPos> hitBlocks = new HashSet<>(beam.hitBlocks);

        // Dynamic step size based on beam type and energy
        double baseStepSize = switch (beam.type) {
            case PRIMARY -> 0.15; // Smaller for more precision
            case SPLIT -> 0.10; // Even smaller for splits
            case SCATTERED -> 0.25;
        };

        if (beam.intensity < 0.2) baseStepSize *= 1.2;

        // Continue bouncing infinitely until energy is depleted
        while (remainingRange > 1.0 && remainingEnergy > MIN_REFLECTION_ENERGY && segments.size() < MAX_SEGMENTS_PER_BEAM) {

            double segmentRange = Math.min(remainingRange, 200);
            Vec3 endPos = currentPos.add(currentDir.scale(segmentRange));
            BlockHitResult hitResult = enhancedRaycast(level, currentPos, endPos, hitBlocks, baseStepSize);

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

            // Infinite bouncing - continue until energy is depleted
            if (hasBlockHit && remainingRange > 1.0) {
                BlockPos hitBlockPos = hitResult.getBlockPos();
                BlockState hitState = level.getBlockState(hitBlockPos);
                float reflectivity = getEnhancedReflectivity(hitState);

                if (reflectivity > 0.05) {
                    // Calculate perfect reflection
                    Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());
                    Vec3 reflection = currentDir.subtract(normal.scale(2 * currentDir.dot(normal)));

                    // Update position and direction for continued bouncing
                    currentPos = hitPos.add(normal.scale(0.02 + Math.random() * 0.01));
                    currentDir = reflection.normalize();

                    // Apply energy reduction
                    double damageRetention = beam.type == PhotonType.SPLIT ? SPLIT_DAMAGE_RETENTION : DAMAGE_RETENTION;
                    remainingEnergy *= (damageRetention * reflectivity);
                    bounces++;

                    hitBlocks.add(hitBlockPos);

                    // Clear hit blocks periodically to prevent infinite exclusion
                    if (bounces % 100 == 0 && hitBlocks.size() > 50) {
                        hitBlocks.clear();
                    }
                } else {
                    // Laser absorbed - end this beam
                    break;
                }
            } else if (hasBlockHit) {
                // Hit but no more range - end beam
                break;
            }
        }

        return segments;
    }

    private static List<PhotonBeam> calculateEnhancedPhotonSplitting(PhotonBeam beam, OptimizedSegment lastSegment, int generation) {
        List<PhotonBeam> newBeams = new ArrayList<>();

        // Check if this beam can still split
        if (beam.splitGenerationsRemaining <= 0) {
            return newBeams;
        }

        double splitChance = BEAM_SPLIT_CHANCE * beam.intensity;
        if (beam.type == PhotonType.SPLIT) splitChance *= 0.9; // Splits can split again
        if (beam.type == PhotonType.SCATTERED) splitChance *= 0.6;

        if (Math.random() > splitChance) return newBeams;

        Vec3 incidentDir = lastSegment.end.subtract(lastSegment.start).normalize();
        Vec3 surfaceNormal = calculateSurfaceNormal(lastSegment.end, BlockPos.containing(lastSegment.end), incidentDir);

        // Create splits with laser-like behavior - offshoots get 50% of main beam's remaining bounces
        int remainingBounces = MAX_BOUNCES; // Since we have infinite bounces, use a reasonable number for splits
        int splitMaxBounces = Math.max(10, beam.bounces / 2); // 50% of current bounce count

        int splitCount = generation < 3 ? (3 + (int)(Math.random() * 4)) : (2 + (int)(Math.random() * 3));

        for (int i = 0; i < splitCount; i++) {
            // Create splits that behave like focused laser beams
            double baseAngle = Math.PI / 8 + (Math.random() - 0.5) * Math.PI / 6; // Tighter angle spread
            double rotationAngle = (2.0 * Math.PI * i) / splitCount;

            Vec3 perpendicular = getPerpendicular(incidentDir);
            Vec3 splitAxis = rotateVectorAroundAxis(perpendicular, incidentDir, rotationAngle);
            Vec3 splitDirection = rotateVectorAroundAxis(incidentDir, splitAxis, baseAngle);

            // Splits get independent energy but reduced generations for splitting
            float splitEnergy = beam.energy * (0.3f + (float)Math.random() * 0.2f);
            double splitIntensity = beam.intensity * (0.6 + Math.random() * 0.3);

            // All splits are laser-like and have reduced split generations
            PhotonType newType = PhotonType.SPLIT;

            // Create split beam starting from the impact point with reduced split generations
            PhotonBeam splitBeam = new PhotonBeam(
                    lastSegment.end.add(surfaceNormal.scale(0.01)),
                    splitDirection.normalize(),
                    splitEnergy,
                    0, // Splits start with 0 bounces, they are independent
                    new HashSet<>(), // Fresh hit block tracking for splits
                    true,
                    generation + 1,
                    splitIntensity,
                    newType,
                    splitMaxBounces,
                    beam.splitGenerationsRemaining - 1 // Reduce split generations remaining
            );
            newBeams.add(splitBeam);
        }

        return newBeams;
    }

    private static List<PhotonBeam> createEnhancedPhotonScattering(PhotonBeam beam, int generation) {
        List<PhotonBeam> scatteredBeams = new ArrayList<>();

        int scatterCount = 3 + (int)(Math.random() * 3);

        for (int i = 0; i < scatterCount; i++) {
            // More focused scattering
            double theta = Math.random() * Math.PI * 0.4; // Tighter scattering
            double phi = Math.random() * Math.PI * 2;

            Vec3 scatterDir = new Vec3(
                    Math.sin(theta) * Math.cos(phi),
                    Math.cos(theta) * 0.3 + 0.7, // Bias toward forward direction
                    Math.sin(theta) * Math.sin(phi)
            ).normalize();

            Vec3 alignedDir = beam.direction.add(scatterDir.scale(0.5)).normalize();

            // Scattered beams get 25% of remaining bounces
            int remainingBounces = beam.maxBounces - beam.bounces;
            int scatterMaxBounces = Math.max(5, remainingBounces / 4);

            // Scattered beams don't reduce main beam energy
            PhotonBeam scatteredBeam = new PhotonBeam(
                    beam.position.add(beam.direction.scale(0.05)),
                    alignedDir,
                    beam.energy * 0.12f, // Independent energy for scattered beams
                    0, // Start fresh bounce count
                    new HashSet<>(), // Fresh hit block tracking
                    false,
                    generation + 1,
                    beam.intensity * 0.3,
                    PhotonType.SCATTERED,
                    scatterMaxBounces,
                    0 // Scattered beams can't split
            );
            scatteredBeams.add(scatteredBeam);
        }

        return scatteredBeams;
    }

    private static float getEnhancedReflectivity(BlockState blockState) {
        // Enhanced reflectivity values for better bouncing
        if (blockState.is(Blocks.GLASS) || blockState.is(Blocks.WHITE_STAINED_GLASS)) return 0.99f;
        if (blockState.is(Blocks.ICE) || blockState.is(Blocks.PACKED_ICE) || blockState.is(Blocks.BLUE_ICE)) return 0.97f;
        if (blockState.is(Blocks.IRON_BLOCK) || blockState.is(Blocks.GOLD_BLOCK)) return 0.95f;
        if (blockState.is(Blocks.DIAMOND_BLOCK) || blockState.is(Blocks.EMERALD_BLOCK)) return 0.98f;
        if (blockState.is(Blocks.WATER)) return 0.88f;
        if (blockState.is(Blocks.QUARTZ_BLOCK) || blockState.is(Blocks.WHITE_CONCRETE)) return 0.85f;
        if (blockState.is(Blocks.POLISHED_ANDESITE) || blockState.is(Blocks.POLISHED_GRANITE)) return 0.80f;
        if (blockState.is(Blocks.STONE) || blockState.is(Blocks.COBBLESTONE)) return 0.72f;
        if (blockState.is(Blocks.DEEPSLATE) || blockState.is(Blocks.BLACKSTONE)) return 0.75f;
        if (blockState.is(Blocks.OBSIDIAN)) return 0.92f;
        if (blockState.is(Blocks.NETHERITE_BLOCK)) return 0.96f;
        if (blockState.is(Blocks.SNOW_BLOCK) || blockState.is(Blocks.SNOW)) return 0.93f;

        return blockState.isSolid() ? 0.68f : 0.0f; // Better base reflectivity
    }

    private static BlockHitResult enhancedRaycast(ServerLevel level, Vec3 start, Vec3 end, Set<BlockPos> excludeBlocks, double stepSize) {
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (distance == 0) return null;

        Vec3 normalizedDir = direction.normalize();
        double adaptiveStep = Math.min(stepSize, distance / 30); // Smaller steps for precision

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

    private static void applyEnhancedPhotonDamage(ServerLevel level, List<OptimizedSegment> segments, Entity shooter) {
        // Infinite piercing - allow all entities to be hit without restriction
        Set<Entity> hitEntities = new HashSet<>();

        for (OptimizedSegment segment : segments) {
            AABB boundingBox = new AABB(segment.start, segment.end).inflate(
                    segment.type == PhotonType.SPLIT ? 0.4 : 0.6 // Tighter hitbox for splits
            );
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

            for (Entity entity : entities) {
                if (entity == shooter) continue;
                if (!(entity instanceof LivingEntity)) continue;

                if (preciseEntityIntersection(segment, entity)) {
                    float damage = segment.energy * getDamageMultiplier(segment.type);

                    // Infinite piercing - every beam segment can hit every entity
                    DamageSource damageSource = shooter instanceof Player player ?
                            level.damageSources().playerAttack(player) : level.damageSources().magic();

                    if (entity.hurt(damageSource, damage)) {
                        Vec3 knockback = segment.end.subtract(segment.start).normalize()
                                .scale(Math.min(2.5, damage / BASE_DAMAGE * segment.intensity));
                        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.scale(0.3)));
                        hitEntities.add(entity);
                    }
                }
            }
        }
    }

    private static float getDamageMultiplier(PhotonType type) {
        return switch (type) {
            case PRIMARY -> 1.0f;
            case SPLIT -> 0.85f; // Splits do good damage
            case SCATTERED -> 0.6f;
        };
    }

    private static boolean preciseEntityIntersection(OptimizedSegment segment, Entity entity) {
        // Simple AABB intersection for entity detection
        AABB entityBounds = entity.getBoundingBox();
        Vec3 start = segment.start;
        Vec3 end = segment.end;

        // Check if the line segment intersects with the entity's bounding box
        return entityBounds.clip(start, end).isPresent();
    }

    // Enhanced utility methods
    private static Vec3 calculateSurfaceNormal(Vec3 hitPos, BlockPos blockPos, Vec3 rayDir) {
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        Vec3 toHit = hitPos.subtract(blockCenter);

        double absX = Math.abs(toHit.x);
        double absY = Math.abs(toHit.y);
        double absZ = Math.abs(toHit.z);

        if (absX > absY && absX > absZ) {
            return new Vec3(toHit.x > 0 ? 1 : -1, 0, 0);
        } else if (absY > absZ) {
            return new Vec3(0, toHit.y > 0 ? 1 : -1, 0);
        } else {
            return new Vec3(0, 0, toHit.z > 0 ? 1 : -1);
        }
    }

    private static Vec3 addSpread(Vec3 direction, double spread) {
        if (spread <= 0) return direction;
        double offsetX = (Math.random() - 0.5) * spread;
        double offsetY = (Math.random() - 0.5) * spread;
        double offsetZ = (Math.random() - 0.5) * spread;
        return direction.add(offsetX, offsetY, offsetZ).normalize();
    }

    private static Vec3 getPerpendicular(Vec3 vector) {
        Vec3 candidate = new Vec3(0, 1, 0);
        if (Math.abs(vector.dot(candidate)) > 0.9) {
            candidate = new Vec3(1, 0, 0);
        }
        return vector.cross(candidate).normalize();
    }

    private static Vec3 rotateVectorAroundAxis(Vec3 vector, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3 normalizedAxis = axis.normalize();

        return vector.scale(cos)
                .add(normalizedAxis.cross(vector).scale(sin))
                .add(normalizedAxis.scale(normalizedAxis.dot(vector) * (1 - cos)));
    }

    private static Direction calculateHitFace(Vec3 hitPos, BlockPos blockPos, Vec3 rayDir) {
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

        tooltip.add(Component.literal("§d§l⚡ INFINITE PIERCING PULSAR ⚡"));
        tooltip.add(Component.literal("§bEnergy: §f" + energyPercent + "%"));
        tooltip.add(Component.literal("§7Damage: §c" + BASE_DAMAGE + " §7(Infinite Pierce)"));
        tooltip.add(Component.literal("§7Max Range: §e" + (int)(MAX_RANGE/1000) + "km"));
        tooltip.add(Component.literal("§7Max Bounces: §a∞ INFINITE"));
        tooltip.add(Component.literal("§7Energy Retention: §6" + (int)(DAMAGE_RETENTION * 1000) / 10.0f + "%"));
        tooltip.add(Component.literal("§7Split Retention: §6" + (int)(SPLIT_DAMAGE_RETENTION * 1000) / 10.0f + "%"));
        tooltip.add(Component.literal("§7Pierce: §d∞ INFINITE §7(All Entities)"));
        tooltip.add(Component.literal("§7Split Chance: §5" + (int)(BEAM_SPLIT_CHANCE * 100) + "%"));
        tooltip.add(Component.literal("§7Photon Scatter: §3" + (int)(PHOTON_SPLIT_CHANCE * 100) + "%"));
        tooltip.add(Component.literal("§7Max Generations: §2" + MAX_GENERATIONS));
        tooltip.add(Component.literal("§7Bounce Delay: §e" + BOUNCE_DELAY_TICKS + " tick §7(Anti-Lag)"));
        tooltip.add(Component.literal("§7Split Generations: §c" + MAX_SPLIT_GENERATIONS + " §7(Limited)"));

        if (energyLevel < 0.1f) {
            tooltip.add(Component.literal("§4§l⚠ CRITICAL ENERGY"));
        } else if (energyLevel > 0.9f) {
            tooltip.add(Component.literal("§d§l✦ INFINITE PIERCING OVERDRIVE"));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8\"Infinite bouncing and piercing pulsar cannon\""));
        tooltip.add(Component.literal("§8\"Offshoots get 50% of main beam bounces\""));
        tooltip.add(Component.literal("§8\"Splits can only split " + MAX_SPLIT_GENERATIONS + " more times\""));
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
        public final int maxBounces;
        public final int splitGenerationsRemaining;

        public PhotonBeam(Vec3 position, Vec3 direction, float energy, int bounces,
                          Set<BlockPos> hitBlocks, boolean isSplit, int generation,
                          double intensity, PhotonType type) {
            this(position, direction, energy, bounces, hitBlocks, isSplit, generation, intensity, type, MAX_BOUNCES, MAX_SPLIT_GENERATIONS);
        }

        public PhotonBeam(Vec3 position, Vec3 direction, float energy, int bounces,
                          Set<BlockPos> hitBlocks, boolean isSplit, int generation,
                          double intensity, PhotonType type, int maxBounces) {
            this(position, direction, energy, bounces, hitBlocks, isSplit, generation, intensity, type, maxBounces, MAX_SPLIT_GENERATIONS);
        }

        public PhotonBeam(Vec3 position, Vec3 direction, float energy, int bounces,
                          Set<BlockPos> hitBlocks, boolean isSplit, int generation,
                          double intensity, PhotonType type, int maxBounces, int splitGenerationsRemaining) {
            this.position = position;
            this.direction = direction;
            this.energy = energy;
            this.bounces = bounces;
            this.hitBlocks = hitBlocks;
            this.isSplit = isSplit;
            this.generation = generation;
            this.intensity = intensity;
            this.type = type;
            this.maxBounces = maxBounces;
            this.splitGenerationsRemaining = splitGenerationsRemaining;
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

    private static class DelayedBeamCalculation {
        public final ServerLevel level;
        public final Vec3 startPos;
        public final Vec3 direction;
        public final Player shooter;
        public final String calculationId;
        public final List<OptimizedSegment> allSegments;
        public final List<PhotonBeam> activeBeams;
        public int totalSegments;
        public int generation;

        public DelayedBeamCalculation(ServerLevel level, Vec3 startPos, Vec3 direction,
                                      Player shooter, String calculationId, int generation) {
            this.level = level;
            this.startPos = startPos;
            this.direction = direction;
            this.shooter = shooter;
            this.calculationId = calculationId;
            this.allSegments = new ArrayList<>();
            this.activeBeams = new ArrayList<>();
            this.totalSegments = 0;
            this.generation = generation;

            // Initialize with primary beam
            this.activeBeams.add(new PhotonBeam(startPos, direction.normalize(), BASE_DAMAGE, 0,
                    new HashSet<>(), false, 0, 1.0, PhotonType.PRIMARY));
        }
    }

    // Cleanup method for pending calculations (should be called periodically)
    public static void cleanupPendingCalculations() {
        // Remove calculations older than 10 seconds to prevent memory leaks
        long currentTime = System.currentTimeMillis();
        pendingCalculations.entrySet().removeIf(entry -> {
            // This is a simple cleanup - in a real implementation you'd want to track creation time
            return Math.random() < 0.01; // Randomly cleanup 1% of entries each call
        });
    }
}