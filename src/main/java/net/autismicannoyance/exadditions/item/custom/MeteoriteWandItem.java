package net.autismicannoyance.exadditions.item;

import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.network.MeteoriteEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
 * Meteorite Wand - Summons devastating meteorite storms from the heavens
 * Creates 5-15 meteors with varied sizes and massive destructive power
 */
public class MeteoriteWand extends Item {

    // Cooldown tracking per player
    private static final ConcurrentHashMap<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 30000; // 30 second cooldown

    // Active meteors for damage tracking
    private static final ConcurrentHashMap<Integer, ActiveMeteorite> ACTIVE_METEORS = new ConcurrentHashMap<>();
    private static int nextMeteorId = 0;

    // Meteorite sizes and properties
    private static final MeteoriteType SMALL = new MeteoriteType(1.5f, 40f, 8f, 0x80FF6B00, 0x60FF4500);
    private static final MeteoriteType MEDIUM = new MeteoriteType(2.5f, 80f, 15f, 0x90FF8C00, 0x70FF6500);
    private static final MeteoriteType LARGE = new MeteoriteType(4.0f, 150f, 25f, 0xA0FFAA00, 0x80FF8500);

    public MeteoriteWand(Properties properties) {
        super(properties);
    }

    // Helper method to schedule delayed tasks
    private void scheduleDelayedTask(ServerLevel level, Runnable task, int delayTicks) {
        level.getServer().execute(() -> {
            // Create a simple delayed execution using server tick counter
            final int targetTick = level.getServer().getTickCount() + delayTicks;

            // Use a repeating check until we reach the target tick
            Runnable delayedExecution = new Runnable() {
                @Override
                public void run() {
                    if (level.getServer().getTickCount() >= targetTick) {
                        task.run();
                    } else {
                        // Schedule another check next tick
                        level.getServer().execute(this);
                    }
                }
            };

            level.getServer().execute(delayedExecution);
        });
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
            player.displayClientMessage(Component.literal("§cMeteorite Storm on cooldown: " + remainingSeconds + "s"), true);
            return InteractionResultHolder.fail(stack);
        }

        // Cast meteorite storm
        summonMeteoriteStorm((ServerLevel) level, player);
        COOLDOWNS.put(playerId, currentTime);

        // Consume durability (if not creative)
        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
        }

        return InteractionResultHolder.success(stack);
    }

    private void summonMeteoriteStorm(ServerLevel level, Player caster) {
        RandomSource random = level.getRandom();
        Vec3 casterPos = caster.position();

        // Determine number of meteors (5-15)
        int meteorCount = 5 + random.nextInt(11);

        // Calculate distribution
        int largeMeteors = Math.max(1, meteorCount / 10); // At least 1, about 10%
        int mediumMeteors = (meteorCount - largeMeteors) * 40 / 100; // 40% of remaining
        int smallMeteors = meteorCount - largeMeteors - mediumMeteors; // Rest are small

        caster.displayClientMessage(Component.literal("§6Summoning " + meteorCount + " meteors from the heavens!"), false);

        // Play dramatic sound
        level.playSound(null, caster.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 1.5f, 0.5f);

        // Spawn warning particles around caster
        for (int i = 0; i < 50; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 3 + random.nextDouble() * 5;
            double x = casterPos.x + Math.cos(angle) * radius;
            double z = casterPos.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.FLAME, x, casterPos.y + 1, z, 1, 0, 0.5, 0, 0.1);
        }

        // Schedule meteors with delays for dramatic effect
        scheduleMeteorites(level, caster, largeMeteors, LARGE, 0);
        scheduleMeteorites(level, caster, mediumMeteors, MEDIUM, 20); // 1 second delay
        scheduleMeteorites(level, caster, smallMeteors, SMALL, 40); // 2 second delay
    }

    private void scheduleMeteorites(ServerLevel level, Player caster, int count, MeteoriteType type, int baseDelay) {
        RandomSource random = level.getRandom();

        for (int i = 0; i < count; i++) {
            int delay = baseDelay + i * 5 + random.nextInt(15); // Spread them out

            level.getServer().execute(() -> {
                // Schedule with delay
                level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + delay, () -> {
                    spawnSingleMeteorite(level, caster, type);
                }));
            });
        }
    }

    private void spawnSingleMeteorite(ServerLevel level, Player caster, MeteoriteType type) {
        RandomSource random = level.getRandom();
        Vec3 casterPos = caster.position();

        // Random position around caster (40-80 blocks away)
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 40 + random.nextDouble() * 40;
        double targetX = casterPos.x + Math.cos(angle) * distance;
        double targetZ = casterPos.z + Math.sin(angle) * distance;

        // Find ground level or use caster Y as fallback
        BlockPos targetPos = new BlockPos((int)targetX, (int)casterPos.y, (int)targetZ);
        for (int y = level.getMaxBuildHeight(); y > level.getMinBuildHeight(); y--) {
            BlockPos checkPos = new BlockPos((int)targetX, y, (int)targetZ);
            if (!level.getBlockState(checkPos).isAir()) {
                targetPos = checkPos.above();
                break;
            }
        }

        // Start position high in the sky
        Vec3 startPos = new Vec3(targetX + random.nextGaussian() * 5, targetPos.getY() + 60 + random.nextDouble() * 40,
                targetZ + random.nextGaussian() * 5);
        Vec3 endPos = new Vec3(targetPos.getX(), targetPos.getY(), targetPos.getZ());

        // Calculate velocity based on distance and desired travel time
        Vec3 trajectory = endPos.subtract(startPos);
        double travelTime = 3.0 + random.nextDouble() * 2.0; // 3-5 seconds
        Vec3 velocity = trajectory.scale(1.0 / (travelTime * 20)); // Convert to per-tick velocity

        // Add some random variation to velocity
        velocity = velocity.add(
                (random.nextDouble() - 0.5) * 0.1,
                (random.nextDouble() - 0.5) * 0.05,
                (random.nextDouble() - 0.5) * 0.1
        );

        int meteorId = nextMeteorId++;
        int lifetimeTicks = (int)(travelTime * 20) + 20; // Add buffer

        // Create active meteorite for damage tracking
        ActiveMeteorite activeMeteor = new ActiveMeteorite(meteorId, startPos, endPos, velocity,
                type, caster.getUUID(), lifetimeTicks);
        ACTIVE_METEORS.put(meteorId, activeMeteor);

        // Start damage tick task
        startMeteoriteDamageTracking(level, activeMeteor);

        // Send packet to all nearby clients
        MeteoriteEffectPacket packet = new MeteoriteEffectPacket(
                startPos, endPos, velocity, type.size, lifetimeTicks, meteorId,
                true, type.coreColor, type.trailColor, 1.0f
        );

        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        startPos.x, startPos.y, startPos.z, 200, level.dimension()
                )), packet
        );

        // Play whoosh sound
        level.playSound(null, new BlockPos((int)startPos.x, (int)startPos.y, (int)startPos.z),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 2.0f, 0.3f + random.nextFloat() * 0.4f);
    }

    private void startMeteoriteDamageTracking(ServerLevel level, ActiveMeteorite meteor) {
        // Schedule damage checks throughout the meteorite's flight
        for (int tick = 0; tick < meteor.lifetimeTicks; tick += 2) { // Check every 2 ticks
            final int currentTick = tick;

            level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + tick, () -> {
                if (!ACTIVE_METEORS.containsKey(meteor.id)) return;

                float progress = (float) currentTick / meteor.lifetimeTicks;
                Vec3 currentPos = meteor.startPos.lerp(meteor.endPos, progress);

                // Check for entities in meteorite path (smaller radius during flight)
                double flightRadius = meteor.type.size * 0.3;
                AABB searchBox = new AABB(currentPos.subtract(flightRadius, flightRadius, flightRadius),
                        currentPos.add(flightRadius, flightRadius, flightRadius));

                List<Entity> entitiesInPath = level.getEntitiesOfClass(Entity.class, searchBox);
                for (Entity entity : entitiesInPath) {
                    if (entity instanceof LivingEntity living && !meteor.hitEntities.contains(entity.getUUID())) {
                        // Don't damage the caster
                        if (!entity.getUUID().equals(meteor.casterId)) {
                            meteor.hitEntities.add(entity.getUUID());

                            // Flight damage (reduced)
                            float flightDamage = meteor.type.damage * 0.3f;
                            DamageSource damageSource = level.damageSources().magic();
                            living.hurt(damageSource, flightDamage);

                            // Knockback effect
                            Vec3 knockback = meteor.velocity.normalize().scale(0.5);
                            living.setDeltaMovement(living.getDeltaMovement().add(knockback));

                            // Fire effect for larger meteors
                            if (meteor.type.size > 2.0f) {
                                living.setSecondsOnFire(3);
                            }
                        }
                    }
                }

                // Impact detection (close to ground)
                if (progress > 0.95f) {
                    performMeteoriteImpact(level, meteor);
                    ACTIVE_METEORS.remove(meteor.id);
                }
            }));
        }

        // Cleanup after lifetime expires
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + meteor.lifetimeTicks + 20, () -> {
            ACTIVE_METEORS.remove(meteor.id);
        }));
    }

    private void performMeteoriteImpact(ServerLevel level, ActiveMeteorite meteor) {
        Vec3 impactPos = meteor.endPos;
        BlockPos impactBlock = new BlockPos((int)impactPos.x, (int)impactPos.y, (int)impactPos.z);

        // Impact sound
        level.playSound(null, impactBlock, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                3.0f * meteor.type.size / 4.0f, 0.1f);

        // Screen shake effect for nearby players
        double shakeRadius = meteor.type.explosionRadius * 2;
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(impactPos) <= shakeRadius) {
                // Send particles to create screen shake effect
                for (int i = 0; i < 20; i++) {
                    level.sendParticles(ParticleTypes.EXPLOSION,
                            impactPos.x + (level.random.nextGaussian() * 3),
                            impactPos.y + level.random.nextDouble() * 3,
                            impactPos.z + (level.random.nextGaussian() * 3),
                            1, 0, 0, 0, 0);
                }
            }
        }

        // Entity damage in explosion radius
        AABB explosionBox = new AABB(impactPos.subtract(meteor.type.explosionRadius, meteor.type.explosionRadius, meteor.type.explosionRadius),
                impactPos.add(meteor.type.explosionRadius, meteor.type.explosionRadius, meteor.type.explosionRadius));

        List<Entity> entitiesInExplosion = level.getEntitiesOfClass(Entity.class, explosionBox);
        for (Entity entity : entitiesInExplosion) {
            if (entity instanceof LivingEntity living && !entity.getUUID().equals(meteor.casterId)) {
                double distance = entity.position().distanceTo(impactPos);
                if (distance <= meteor.type.explosionRadius) {
                    // Calculate damage falloff
                    double damageFactor = 1.0 - (distance / meteor.type.explosionRadius);
                    damageFactor = Math.max(0.1, damageFactor); // Minimum 10% damage

                    float explosionDamage = meteor.type.damage * (float)damageFactor;

                    DamageSource damageSource = level.damageSources().explosion(null, null);
                    living.hurt(damageSource, explosionDamage);

                    // Strong knockback
                    Vec3 knockbackDir = entity.position().subtract(impactPos).normalize();
                    double knockbackStrength = (meteor.type.size / 2.0) * damageFactor;
                    living.setDeltaMovement(living.getDeltaMovement().add(knockbackDir.scale(knockbackStrength)));

                    // Fire effect
                    int fireTime = (int)(meteor.type.size * 2);
                    living.setSecondsOnFire(fireTime);
                }
            }
        }

        // Environmental destruction
        createMeteorCrater(level, impactBlock, meteor.type);

        // Spawn impact particles
        spawnImpactEffects(level, impactPos, meteor.type);
    }

    private void createMeteorCrater(ServerLevel level, BlockPos center, MeteoriteType type) {
        int radius = (int)(type.size * 1.5); // Crater radius based on meteorite size

        // Create spherical crater
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius/2; y <= radius/4; y++) { // Shallower crater
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x*x + y*y*2 + z*z); // Elliptical shape

                    if (distance <= radius) {
                        BlockPos pos = center.offset(x, y, z);
                        BlockState currentState = level.getBlockState(pos);

                        // Don't break bedrock or other indestructible blocks
                        if (!currentState.isAir() && currentState.getDestroySpeed(level, pos) >= 0) {
                            // Chance to destroy block based on distance from center
                            double destroyChance = 1.0 - (distance / radius);

                            if (level.random.nextDouble() < destroyChance) {
                                // Drop items sometimes
                                if (level.random.nextDouble() < 0.3 && destroyChance > 0.5) {
                                    currentState.getBlock().popResource(level, pos, new ItemStack(currentState.getBlock()));
                                }

                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }

        // Add some fire blocks for larger meteors
        if (type.size > 2.0f) {
            for (int i = 0; i < radius * 2; i++) {
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
    }

    private void spawnImpactEffects(ServerLevel level, Vec3 pos, MeteoriteType type) {
        RandomSource random = level.random;
        int particleCount = (int)(type.size * 30);

        // Explosion particles
        for (int i = 0; i < particleCount; i++) {
            double offsetX = random.nextGaussian() * type.explosionRadius * 0.3;
            double offsetY = random.nextDouble() * type.explosionRadius * 0.5;
            double offsetZ = random.nextGaussian() * type.explosionRadius * 0.3;

            level.sendParticles(ParticleTypes.EXPLOSION,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0, 0, 0, 0);
        }

        // Fire and smoke particles
        for (int i = 0; i < particleCount * 2; i++) {
            double offsetX = random.nextGaussian() * type.explosionRadius;
            double offsetY = random.nextDouble() * type.explosionRadius;
            double offsetZ = random.nextGaussian() * type.explosionRadius;

            if (random.nextBoolean()) {
                level.sendParticles(ParticleTypes.FLAME,
                        pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                        1, random.nextGaussian() * 0.1, random.nextDouble() * 0.2, random.nextGaussian() * 0.1, 0.05);
            } else {
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                        1, random.nextGaussian() * 0.05, random.nextDouble() * 0.1, random.nextGaussian() * 0.05, 0.02);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§6Summons a devastating meteorite storm"));
        tooltip.add(Component.literal("§75-15 meteors rain from the heavens"));
        tooltip.add(Component.literal("§cDeals massive AOE damage and destruction"));
        tooltip.add(Component.literal("§930 second cooldown"));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Always enchanted glow
    }

    // Data classes
    private static class MeteoriteType {
        final float size;
        final float damage;
        final float explosionRadius;
        final int coreColor;
        final int trailColor;

        MeteoriteType(float size, float damage, float explosionRadius, int coreColor, int trailColor) {
            this.size = size;
            this.damage = damage;
            this.explosionRadius = explosionRadius;
            this.coreColor = coreColor;
            this.trailColor = trailColor;
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