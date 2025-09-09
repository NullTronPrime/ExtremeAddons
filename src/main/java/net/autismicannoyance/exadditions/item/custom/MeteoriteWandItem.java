package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.network.MeteoriteEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enhanced Meteorite Wand - Summons realistic meteorite bombardments from angled trajectories
 * Creates 10-20 meteors with weighted distribution and timed launches for dramatic effect
 */
public class MeteoriteWandItem extends Item {

    private static final ConcurrentHashMap<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 25000; // 25 second cooldown for the enhanced bombardment

    private static final ConcurrentHashMap<Integer, ActiveMeteorite> ACTIVE_METEORS = new ConcurrentHashMap<>();
    private static int nextMeteorId = 0;

    // Enhanced meteorite types with proper scaling
    private static final MeteoriteType SMALL = new MeteoriteType(1.2f, 35f, 6f, 0.5f, 0x80FF4500, 0x60FF3000);
    private static final MeteoriteType MEDIUM = new MeteoriteType(2.8f, 120f, 18f, 2.0f, 0x90FF6B00, 0x70FF4500);
    private static final MeteoriteType LARGE = new MeteoriteType(5.5f, 300f, 35f, 5.0f, 0xA0FFAA00, 0x80FF6500);

    public MeteoriteWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        // Check cooldown
        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastUsed = COOLDOWNS.get(playerId);

        if (lastUsed != null && currentTime - lastUsed < COOLDOWN_MS) {
            long remainingSeconds = (COOLDOWN_MS - (currentTime - lastUsed)) / 1000;
            player.displayClientMessage(Component.literal("§cMeteor Bombardment on cooldown: " + remainingSeconds + "s"), true);
            return InteractionResultHolder.fail(stack);
        }

        // Get target area (where player is looking or standing)
        Vec3 targetCenter = player.position();

        // Launch meteorite bombardment
        launchMeteoriteBombardment((ServerLevel) level, player, targetCenter);
        COOLDOWNS.put(playerId, currentTime);

        // Consume durability
        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
        }

        return InteractionResultHolder.success(stack);
    }

    private void launchMeteoriteBombardment(ServerLevel level, Player caster, Vec3 targetCenter) {
        RandomSource random = level.getRandom();

        // Calculate meteorite distribution (10-20 total meteorites)
        int totalMeteoritePoints = 10 + random.nextInt(11); // 10-20 points
        MeteoriteDistribution distribution = calculateMeteoriteDistribution(totalMeteoritePoints, random);

        caster.displayClientMessage(Component.literal("§6§lINITIATING COSMIC BOMBARDMENT!"), false);
        caster.displayClientMessage(Component.literal(String.format("§c§lLarge: %d | §e§lMedium: %d | §7§lSmall: %d",
                distribution.largeMeteors, distribution.mediumMeteors, distribution.smallMeteors)), false);

        // Play dramatic warning sequence
        level.playSound(null, caster.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 3.0f, 0.2f);

        // Enhanced warning effects
        createBombardmentWarning(level, targetCenter);

        // Schedule meteorites with bombardment timing
        scheduleBombardment(level, caster, targetCenter, distribution);
    }

    private MeteoriteDistribution calculateMeteoriteDistribution(int totalPoints, RandomSource random) {
        // Ensure at least 1 large meteorite
        int largeMeteors = 1;
        int remainingPoints = totalPoints - 5; // Subtract points for guaranteed large meteor

        // Calculate additional meteorites based on weighted distribution
        // Target: 10% large (5 points), 30% medium (2 points), 60% small (0.5 points)

        int additionalLarge = 0;
        int mediumMeteors = 0;
        int smallMeteors = 0;

        while (remainingPoints > 0) {
            float roll = random.nextFloat();

            if (roll < 0.10f && remainingPoints >= 5) {
                // 10% chance for additional large meteorite
                additionalLarge++;
                remainingPoints -= 5;
            } else if (roll < 0.40f && remainingPoints >= 2) {
                // 30% chance for medium meteorite
                mediumMeteors++;
                remainingPoints -= 2;
            } else if (remainingPoints >= 1) {
                // 60% chance for small meteorite (but need at least 1 point for 0.5 cost)
                smallMeteors += 2; // Add 2 small meteors for 1 point
                remainingPoints -= 1;
            } else {
                break; // Not enough points for any meteorite
            }
        }

        largeMeteors += additionalLarge;

        return new MeteoriteDistribution(largeMeteors, mediumMeteors, smallMeteors);
    }

    private void scheduleBombardment(ServerLevel level, Player caster, Vec3 targetCenter, MeteoriteDistribution distribution) {
        RandomSource random = level.getRandom();

        // Create bombardment schedule with realistic timing
        int totalMeteors = distribution.largeMeteors + distribution.mediumMeteors + distribution.smallMeteors;
        int currentDelay = 0;

        // Schedule large meteorites first (more dramatic impact)
        for (int i = 0; i < distribution.largeMeteors; i++) {
            currentDelay += 15 + random.nextInt(20); // 15-35 tick intervals
            scheduleSingleMeteor(level, caster, targetCenter, LARGE, currentDelay);
        }

        // Schedule medium meteorites
        for (int i = 0; i < distribution.mediumMeteors; i++) {
            currentDelay += 8 + random.nextInt(12); // 8-20 tick intervals
            scheduleSingleMeteor(level, caster, targetCenter, MEDIUM, currentDelay);
        }

        // Schedule small meteorites (rapid fire)
        for (int i = 0; i < distribution.smallMeteors; i++) {
            currentDelay += 3 + random.nextInt(8); // 3-11 tick intervals
            scheduleSingleMeteor(level, caster, targetCenter, SMALL, currentDelay);
        }
    }

    private void scheduleSingleMeteor(ServerLevel level, Player caster, Vec3 targetCenter, MeteoriteType type, int delay) {
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + delay, () -> {
            launchSingleMeteor(level, caster, targetCenter, type);
        }));
    }

    private void launchSingleMeteor(ServerLevel level, Player caster, Vec3 targetCenter, MeteoriteType type) {
        RandomSource random = level.getRandom();

        // Calculate impact position within bombardment area
        double bombardmentRadius = 25.0; // 25 block radius bombardment zone
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = Math.sqrt(random.nextDouble()) * bombardmentRadius; // Square root for even distribution
        double targetX = targetCenter.x + Math.cos(angle) * distance;
        double targetZ = targetCenter.z + Math.sin(angle) * distance;

        // Find ground level at target
        BlockPos groundPos = findGroundLevel(level, new BlockPos((int)targetX, (int)targetCenter.y, (int)targetZ));
        Vec3 impactPos = new Vec3(groundPos.getX() + 0.5, groundPos.getY(), groundPos.getZ() + 0.5);

        // Calculate angled entry trajectory
        Vec3 startPos = calculateAngledStartPosition(impactPos, type, random);
        Vec3 velocity = calculateTrajectoryVelocity(startPos, impactPos);

        int meteorId = nextMeteorId++;
        int lifetimeTicks = 80; // Fixed 4 second flight time

        // Create active meteorite for damage tracking
        ActiveMeteorite activeMeteor = new ActiveMeteorite(meteorId, startPos, impactPos, velocity,
                type, caster.getUUID(), lifetimeTicks);
        ACTIVE_METEORS.put(meteorId, activeMeteor);

        // Start enhanced damage tracking
        startEnhancedDamageTracking(level, activeMeteor);

        // Send visual effect packet
        MeteoriteEffectPacket packet = new MeteoriteEffectPacket(
                startPos, impactPos, velocity, type.size, lifetimeTicks, meteorId,
                true, type.coreColor, type.trailColor, type.intensity
        );

        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        startPos.x, startPos.y, startPos.z, 400, level.dimension()
                )), packet
        );

        // Enhanced launch sound based on meteorite size
        float pitch = 0.5f + (1.0f / type.size);
        float volume = Math.min(3.0f, type.size * 0.8f);
        level.playSound(null, new BlockPos((int)startPos.x, (int)startPos.y, (int)startPos.z),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, volume, pitch);
    }

    private Vec3 calculateAngledStartPosition(Vec3 impactPos, MeteoriteType type, RandomSource random) {
        // Calculate angled trajectory (not straight down)
        double height = 120 + (type.size * 20); // Higher start for larger meteors

        // Random angle between 25-65 degrees from vertical
        double angleFromVertical = Math.toRadians(25 + random.nextDouble() * 40);
        double horizontalDistance = height * Math.tan(angleFromVertical);

        // Random direction for the angle
        double direction = random.nextDouble() * Math.PI * 2;
        double offsetX = Math.cos(direction) * horizontalDistance;
        double offsetZ = Math.sin(direction) * horizontalDistance;

        return new Vec3(
                impactPos.x + offsetX,
                impactPos.y + height,
                impactPos.z + offsetZ
        );
    }

    private Vec3 calculateTrajectoryVelocity(Vec3 startPos, Vec3 impactPos) {
        Vec3 trajectory = impactPos.subtract(startPos);
        double flightTime = 4.0; // 4 seconds flight time
        return trajectory.scale(1.0 / (flightTime * 20)); // 20 ticks per second
    }

    private BlockPos findGroundLevel(ServerLevel level, BlockPos startPos) {
        for (int y = Math.min(level.getMaxBuildHeight() - 1, startPos.getY() + 10); y > level.getMinBuildHeight(); y--) {
            BlockPos checkPos = new BlockPos(startPos.getX(), y, startPos.getZ());
            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && state.isSolid()) {
                return checkPos.above();
            }
        }
        return startPos;
    }

    private void createBombardmentWarning(ServerLevel level, Vec3 targetCenter) {
        // Enhanced warning particle effects
        for (int i = 0; i < 100; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double radius = 5 + level.random.nextDouble() * 20;
            double x = targetCenter.x + Math.cos(angle) * radius;
            double z = targetCenter.z + Math.sin(angle) * radius;
            double y = targetCenter.y + 1 + level.random.nextDouble() * 4;

            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0, 1.2, 0, 0.2);

            if (level.random.nextFloat() < 0.3f) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 2, z, 1, 0.2, 0.5, 0.2, 0.1);
            }
        }
    }

    private void startEnhancedDamageTracking(ServerLevel level, ActiveMeteorite meteor) {
        // More frequent damage checks for enhanced realism
        for (int tick = 0; tick < meteor.lifetimeTicks; tick += 2) {
            final int currentTick = tick;

            level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + currentTick, () -> {
                if (!ACTIVE_METEORS.containsKey(meteor.id)) return;

                float progress = Math.min(1.0f, (float) currentTick / meteor.lifetimeTicks);
                Vec3 currentPos = meteor.startPos.lerp(meteor.endPos, progress);

                // Enhanced collision detection
                double collisionRadius = meteor.type.size * 0.6;
                AABB collisionBox = new AABB(
                        currentPos.subtract(collisionRadius, collisionRadius, collisionRadius),
                        currentPos.add(collisionRadius, collisionRadius, collisionRadius)
                );

                List<Entity> entities = level.getEntitiesOfClass(Entity.class, collisionBox);
                for (Entity entity : entities) {
                    if (entity instanceof LivingEntity living && !meteor.hitEntities.contains(entity.getUUID())) {
                        if (!entity.getUUID().equals(meteor.casterId)) {
                            meteor.hitEntities.add(entity.getUUID());

                            // Enhanced flight damage based on meteorite type
                            float flightDamage = meteor.type.damage * 0.3f;
                            DamageSource damageSource = level.damageSources().magic();
                            living.hurt(damageSource, flightDamage);

                            // Scaled knockback
                            Vec3 knockback = meteor.velocity.normalize().scale(meteor.type.weight * 0.4);
                            living.setDeltaMovement(living.getDeltaMovement().add(knockback));

                            // Fire effects
                            living.setSecondsOnFire((int)(meteor.type.size * 2));
                        }
                    }
                }

                // Impact detection
                if (progress >= 0.98f || currentTick >= meteor.lifetimeTicks - 5) {
                    performEnhancedImpact(level, meteor);
                    ACTIVE_METEORS.remove(meteor.id);
                }
            }));
        }

        // Cleanup
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + meteor.lifetimeTicks + 40, () -> {
            ACTIVE_METEORS.remove(meteor.id);
        }));
    }

    private void performEnhancedImpact(ServerLevel level, ActiveMeteorite meteor) {
        Vec3 impactPos = meteor.endPos;
        BlockPos impactBlock = new BlockPos((int)impactPos.x, (int)impactPos.y, (int)impactPos.z);

        // Enhanced impact sounds based on meteorite size
        float baseVolume = Math.min(5.0f, meteor.type.size * 1.2f);
        float basePitch = Math.max(0.1f, 1.0f - (meteor.type.size * 0.15f));

        level.playSound(null, impactBlock, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, baseVolume, basePitch);

        if (meteor.type.size >= 3.0f) {
            level.playSound(null, impactBlock, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE,
                    baseVolume * 0.8f, basePitch * 0.7f);
        }

        // Enhanced entity damage with proper scaling
        double damageRadius = meteor.type.explosionRadius;
        AABB damageBox = new AABB(
                impactPos.subtract(damageRadius, damageRadius, damageRadius),
                impactPos.add(damageRadius, damageRadius, damageRadius)
        );

        List<Entity> damagedEntities = level.getEntitiesOfClass(Entity.class, damageBox);
        for (Entity entity : damagedEntities) {
            if (entity instanceof LivingEntity living && !entity.getUUID().equals(meteor.casterId)) {
                double distance = entity.position().distanceTo(impactPos);
                if (distance <= damageRadius) {
                    double damageFactor = Math.max(0.15, 1.0 - (distance / damageRadius));
                    float explosionDamage = meteor.type.damage * (float)damageFactor;

                    DamageSource damageSource = level.damageSources().explosion(null, null);
                    living.hurt(damageSource, explosionDamage);

                    // Enhanced knockback based on meteorite weight
                    Vec3 knockbackDir = entity.position().subtract(impactPos).normalize();
                    double knockbackStrength = meteor.type.weight * damageFactor * 0.6;
                    living.setDeltaMovement(living.getDeltaMovement().add(knockbackDir.scale(knockbackStrength)));

                    // Extended fire based on meteorite type
                    int fireTime = Math.max(3, (int)(meteor.type.size * 4));
                    living.setSecondsOnFire(fireTime);
                }
            }
        }

        // Enhanced crater creation
        createScaledCrater(level, impactBlock, meteor.type);
        spawnScaledImpactEffects(level, impactPos, meteor.type);
    }

    private void createScaledCrater(ServerLevel level, BlockPos center, MeteoriteType type) {
        int radius = Math.max(2, (int)(type.size * 1.8)); // Scaled crater size
        int depth = Math.max(1, (int)(type.size * 0.6));

        for (int x = -radius; x <= radius; x++) {
            for (int y = -depth; y <= depth/2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x*x + y*y*1.8 + z*z);

                    if (distance <= radius) {
                        BlockPos pos = center.offset(x, y, z);
                        BlockState currentState = level.getBlockState(pos);

                        if (!currentState.isAir() && currentState.getDestroySpeed(level, pos) >= 0) {
                            double destroyChance = Math.max(0.4, 1.4 - (distance / radius));

                            if (level.random.nextDouble() < destroyChance) {
                                // Enhanced item drops for larger meteorites
                                if (level.random.nextDouble() < (0.3 + type.size * 0.1) && destroyChance > 0.5) {
                                    currentState.getBlock().popResource(level, pos, new ItemStack(currentState.getBlock()));
                                }
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }

        // Enhanced fire spread based on meteorite size
        int fireSpread = (int)(radius * type.size * 0.8);
        for (int i = 0; i < fireSpread; i++) {
            int x = level.random.nextInt(radius * 2) - radius;
            int z = level.random.nextInt(radius * 2) - radius;
            if (x*x + z*z <= radius*radius) {
                BlockPos firePos = center.offset(x, 1, z);
                if (level.getBlockState(firePos).isAir() && level.getBlockState(firePos.below()).isSolid()) {
                    level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
                }
            }
        }
    }

    private void spawnScaledImpactEffects(ServerLevel level, Vec3 pos, MeteoriteType type) {
        RandomSource random = level.random;
        int particleCount = (int)(type.size * 80); // More particles for larger meteors

        // Enhanced particle effects (no explosion particles as requested)
        for (int i = 0; i < particleCount; i++) {
            double offsetX = random.nextGaussian() * type.explosionRadius * 0.5;
            double offsetY = random.nextDouble() * type.explosionRadius * 0.8;
            double offsetZ = random.nextGaussian() * type.explosionRadius * 0.5;

            // Use flame and smoke instead of explosion particles
            if (random.nextFloat() < 0.6f) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                        1, random.nextGaussian() * 0.3, random.nextDouble() * 0.4, random.nextGaussian() * 0.3, 0.15);
            } else {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                        1, random.nextGaussian() * 0.2, random.nextDouble() * 0.3, random.nextGaussian() * 0.2, 0.08);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§6§lSummons a devastating meteor bombardment"));
        tooltip.add(Component.literal("§7§l10-20 meteors with angled trajectories"));
        tooltip.add(Component.literal("§c§lDistribution: 10% Large, 30% Medium, 60% Small"));
        tooltip.add(Component.literal("§e§lSpeed: Small (Fast) → Medium → Large (Slow)"));
        tooltip.add(Component.literal("§d§lRandom targeting within 40 block radius"));
        tooltip.add(Component.literal("§4§lMassive scaled damage and destruction"));
        tooltip.add(Component.literal("§9§l25 second cooldown (No cooldown in Creative)"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8\"The heavens rain destruction upon the earth.\""));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // Enhanced data classes
    private static class MeteoriteType {
        final float size;
        final float damage;
        final float explosionRadius;
        final float weight; // For knockback calculations
        final int coreColor;
        final int trailColor;
        final float intensity;

        MeteoriteType(float size, float damage, float explosionRadius, float weight, int coreColor, int trailColor) {
            this.size = size;
            this.damage = damage;
            this.explosionRadius = explosionRadius;
            this.weight = weight;
            this.coreColor = coreColor;
            this.trailColor = trailColor;
            this.intensity = 1.0f;
        }
    }

    private static class MeteoriteDistribution {
        final int largeMeteors;
        final int mediumMeteors;
        final int smallMeteors;

        MeteoriteDistribution(int largeMeteors, int mediumMeteors, int smallMeteors) {
            this.largeMeteors = largeMeteors;
            this.mediumMeteors = mediumMeteors;
            this.smallMeteors = smallMeteors;
        }
    }

    private static class ActiveMeteorite {
        final int id;
        final Vec3 startPos;
        final Vec3 endPos;
        final Vec3 velocity;
        final MeteoriteType type;
        final UUID casterId;
        final int lifetimeTicks;
        final List<UUID> hitEntities;

        ActiveMeteorite(int id, Vec3 startPos, Vec3 endPos, Vec3 velocity,
                        MeteoriteType type, UUID casterId, int lifetimeTicks) {
            this.id = id;
            this.startPos = startPos;
            this.endPos = endPos;
            this.velocity = velocity;
            this.type = type;
            this.casterId = casterId;
            this.lifetimeTicks = lifetimeTicks;
            this.hitEntities = new CopyOnWriteArrayList<>();
        }
    }
}