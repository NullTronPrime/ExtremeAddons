package net.autismicannoyance.exadditions.item.custom;

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
 * Creates massive amounts of meteors with varied sizes and destructive power
 */
public class MeteoriteWandItem extends Item {

    // Cooldown tracking per player
    private static final ConcurrentHashMap<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 20000; // 20 second cooldown

    // Active meteors for damage tracking
    private static final ConcurrentHashMap<Integer, ActiveMeteorite> ACTIVE_METEORS = new ConcurrentHashMap<>();
    private static int nextMeteorId = 0;

    // Enhanced meteorite sizes and properties
    private static final MeteoriteType TINY = new MeteoriteType(1.0f, 25f, 5f, 0x70FF4500, 0x50FF3000);
    private static final MeteoriteType SMALL = new MeteoriteType(1.5f, 40f, 8f, 0x80FF6B00, 0x60FF4500);
    private static final MeteoriteType MEDIUM = new MeteoriteType(2.5f, 80f, 15f, 0x90FF8C00, 0x70FF6500);
    private static final MeteoriteType LARGE = new MeteoriteType(4.0f, 150f, 25f, 0xA0FFAA00, 0x80FF8500);
    private static final MeteoriteType MASSIVE = new MeteoriteType(6.0f, 250f, 40f, 0xB0FFCC00, 0x90FFA500);

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

        // Increased number of meteors (20-50)
        int meteorCount = 20 + random.nextInt(31);

        // Enhanced distribution
        int massiveMeteors = Math.max(1, meteorCount / 20); // 5%
        int largeMeteors = Math.max(1, meteorCount / 15);   // ~7%
        int mediumMeteors = (meteorCount - massiveMeteors - largeMeteors) * 25 / 100; // 25% of remaining
        int smallMeteors = (meteorCount - massiveMeteors - largeMeteors - mediumMeteors) * 40 / 100; // 40% of remaining
        int tinyMeteors = meteorCount - massiveMeteors - largeMeteors - mediumMeteors - smallMeteors; // Rest are tiny

        caster.displayClientMessage(Component.literal("§6§lSummoning " + meteorCount + " meteors from the cosmic void!"), false);
        caster.displayClientMessage(Component.literal("§c§lMassive: " + massiveMeteors + " | Large: " + largeMeteors + " | Medium: " + mediumMeteors + " | Small: " + smallMeteors + " | Tiny: " + tinyMeteors), false);

        // Play dramatic sound sequence
        level.playSound(null, caster.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 2.0f, 0.3f);

        // Secondary sound for dramatic effect
        level.getServer().execute(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            level.playSound(null, caster.blockPosition(), SoundEvents.WITHER_SPAWN,
                    SoundSource.PLAYERS, 1.5f, 0.8f);
        });

        // Enhanced warning particles around caster
        for (int i = 0; i < 100; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 5 + random.nextDouble() * 10;
            double x = casterPos.x + Math.cos(angle) * radius;
            double z = casterPos.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, casterPos.y + 1 + random.nextDouble() * 3, z,
                    1, 0, 0.8, 0, 0.15);
        }

        // Schedule meteors with staggered timing for maximum chaos
        scheduleMeteorites(level, caster, massiveMeteors, MASSIVE, 0);
        scheduleMeteorites(level, caster, largeMeteors, LARGE, 10);
        scheduleMeteorites(level, caster, mediumMeteors, MEDIUM, 20);
        scheduleMeteorites(level, caster, smallMeteors, SMALL, 30);
        scheduleMeteorites(level, caster, tinyMeteors, TINY, 40);
    }

    private void scheduleMeteorites(ServerLevel level, Player caster, int count, MeteoriteType type, int baseDelay) {
        RandomSource random = level.getRandom();

        for (int i = 0; i < count; i++) {
            int delay = baseDelay + i * 3 + random.nextInt(10); // Faster spawn rate

            final int finalI = i;
            level.getServer().execute(() -> {
                level.getServer().execute(() -> {
                    if (level.getServer().getTickCount() >= delay) {
                        spawnSingleMeteorite(level, caster, type);
                    } else {
                        // Reschedule
                        level.getServer().execute(() -> {
                            try { Thread.sleep(delay * 50L); } catch (InterruptedException e) {}
                            spawnSingleMeteorite(level, caster, type);
                        });
                    }
                });
            });
        }
    }

    private void spawnSingleMeteorite(ServerLevel level, Player caster, MeteoriteType type) {
        RandomSource random = level.getRandom();
        Vec3 casterPos = caster.position();

        // Extended range for more chaos (60-120 blocks away)
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 60 + random.nextDouble() * 60;
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

        // Start position much higher in the sky
        Vec3 startPos = new Vec3(targetX + random.nextGaussian() * 10,
                targetPos.getY() + 80 + random.nextDouble() * 60,
                targetZ + random.nextGaussian() * 10);
        Vec3 endPos = new Vec3(targetPos.getX(), targetPos.getY(), targetPos.getZ());

        // Calculate velocity
        Vec3 trajectory = endPos.subtract(startPos);
        double travelTime = 2.0 + random.nextDouble() * 3.0; // 2-5 seconds
        Vec3 velocity = trajectory.scale(1.0 / (travelTime * 20));

        // Add more velocity variation
        velocity = velocity.add(
                (random.nextDouble() - 0.5) * 0.2,
                (random.nextDouble() - 0.5) * 0.1,
                (random.nextDouble() - 0.5) * 0.2
        );

        int meteorId = nextMeteorId++;
        int lifetimeTicks = (int)(travelTime * 20) + 30;

        // Create active meteorite for damage tracking
        ActiveMeteorite activeMeteor = new ActiveMeteorite(meteorId, startPos, endPos, velocity,
                type, caster.getUUID(), lifetimeTicks);
        ACTIVE_METEORS.put(meteorId, activeMeteor);

        // Start damage tracking
        startMeteoriteDamageTracking(level, activeMeteor);

        // Send packet to clients
        MeteoriteEffectPacket packet = new MeteoriteEffectPacket(
                startPos, endPos, velocity, type.size, lifetimeTicks, meteorId,
                true, type.coreColor, type.trailColor, 1.0f
        );

        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        startPos.x, startPos.y, startPos.z, 300, level.dimension()
                )), packet
        );

        // Enhanced sound effects
        level.playSound(null, new BlockPos((int)startPos.x, (int)startPos.y, (int)startPos.z),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE,
                1.0f + (type.size / 4.0f), 0.2f + random.nextFloat() * 0.6f);
    }

    private void startMeteoriteDamageTracking(ServerLevel level, ActiveMeteorite meteor) {
        // More frequent damage checks for better collision detection
        for (int tick = 0; tick < meteor.lifetimeTicks; tick += 1) {
            final int currentTick = tick;

            level.getServer().execute(() -> {
                // Simple delay simulation
                try { Thread.sleep(currentTick * 50L); } catch (InterruptedException e) {}

                if (!ACTIVE_METEORS.containsKey(meteor.id)) return;

                float progress = Math.min(1.0f, (float) currentTick / meteor.lifetimeTicks);
                Vec3 currentPos = meteor.startPos.lerp(meteor.endPos, progress);

                // Enhanced collision detection
                double flightRadius = meteor.type.size * 0.4;
                AABB searchBox = new AABB(currentPos.subtract(flightRadius, flightRadius, flightRadius),
                        currentPos.add(flightRadius, flightRadius, flightRadius));

                List<Entity> entitiesInPath = level.getEntitiesOfClass(Entity.class, searchBox);
                for (Entity entity : entitiesInPath) {
                    if (entity instanceof LivingEntity living && !meteor.hitEntities.contains(entity.getUUID())) {
                        if (!entity.getUUID().equals(meteor.casterId)) {
                            meteor.hitEntities.add(entity.getUUID());

                            // Enhanced flight damage
                            float flightDamage = meteor.type.damage * 0.4f;
                            DamageSource damageSource = level.damageSources().magic();
                            living.hurt(damageSource, flightDamage);

                            // Stronger knockback
                            Vec3 knockback = meteor.velocity.normalize().scale(0.8);
                            living.setDeltaMovement(living.getDeltaMovement().add(knockback));

                            // Enhanced fire effects
                            if (meteor.type.size > 1.5f) {
                                living.setSecondsOnFire((int)(meteor.type.size * 2));
                            }
                        }
                    }
                }

                // Impact detection
                if (progress > 0.9f || currentTick >= meteor.lifetimeTicks - 5) {
                    performMeteoriteImpact(level, meteor);
                    ACTIVE_METEORS.remove(meteor.id);
                }
            });
        }

        // Cleanup
        level.getServer().execute(() -> {
            try { Thread.sleep((meteor.lifetimeTicks + 40) * 50L); } catch (InterruptedException e) {}
            ACTIVE_METEORS.remove(meteor.id);
        });
    }

    private void performMeteoriteImpact(ServerLevel level, ActiveMeteorite meteor) {
        Vec3 impactPos = meteor.endPos;
        BlockPos impactBlock = new BlockPos((int)impactPos.x, (int)impactPos.y, (int)impactPos.z);

        // Enhanced impact sound
        level.playSound(null, impactBlock, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                4.0f * meteor.type.size / 3.0f, 0.05f + meteor.type.size * 0.05f);

        // Additional impact sounds for larger meteors
        if (meteor.type.size > 3.0f) {
            level.playSound(null, impactBlock, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE,
                    3.0f, 0.3f);
        }

        // Enhanced entity damage
        AABB explosionBox = new AABB(impactPos.subtract(meteor.type.explosionRadius, meteor.type.explosionRadius, meteor.type.explosionRadius),
                impactPos.add(meteor.type.explosionRadius, meteor.type.explosionRadius, meteor.type.explosionRadius));

        List<Entity> entitiesInExplosion = level.getEntitiesOfClass(Entity.class, explosionBox);
        for (Entity entity : entitiesInExplosion) {
            if (entity instanceof LivingEntity living && !entity.getUUID().equals(meteor.casterId)) {
                double distance = entity.position().distanceTo(impactPos);
                if (distance <= meteor.type.explosionRadius) {
                    double damageFactor = Math.max(0.2, 1.0 - (distance / meteor.type.explosionRadius));
                    float explosionDamage = meteor.type.damage * (float)damageFactor;

                    DamageSource damageSource = level.damageSources().explosion(null, null);
                    living.hurt(damageSource, explosionDamage);

                    // Enhanced knockback
                    Vec3 knockbackDir = entity.position().subtract(impactPos).normalize();
                    double knockbackStrength = (meteor.type.size / 1.5) * damageFactor;
                    living.setDeltaMovement(living.getDeltaMovement().add(knockbackDir.scale(knockbackStrength)));

                    // Extended fire effects
                    int fireTime = Math.max(5, (int)(meteor.type.size * 3));
                    living.setSecondsOnFire(fireTime);
                }
            }
        }

        // Enhanced environmental destruction
        createMeteorCrater(level, impactBlock, meteor.type);
        spawnImpactEffects(level, impactPos, meteor.type);
    }

    private void createMeteorCrater(ServerLevel level, BlockPos center, MeteoriteType type) {
        int radius = Math.max(3, (int)(type.size * 2.0)); // Larger craters

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius/2; y <= radius/3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x*x + y*y*1.5 + z*z);

                    if (distance <= radius) {
                        BlockPos pos = center.offset(x, y, z);
                        BlockState currentState = level.getBlockState(pos);

                        if (!currentState.isAir() && currentState.getDestroySpeed(level, pos) >= 0) {
                            double destroyChance = Math.max(0.3, 1.2 - (distance / radius));

                            if (level.random.nextDouble() < destroyChance) {
                                // More frequent item drops
                                if (level.random.nextDouble() < 0.4 && destroyChance > 0.4) {
                                    currentState.getBlock().popResource(level, pos, new ItemStack(currentState.getBlock()));
                                }

                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }

        // Enhanced fire placement
        if (type.size > 1.0f) {
            int fireCount = (int)(radius * type.size);
            for (int i = 0; i < fireCount; i++) {
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
        int particleCount = (int)(type.size * 50); // More particles

        // Enhanced explosion particles
        for (int i = 0; i < particleCount; i++) {
            double offsetX = random.nextGaussian() * type.explosionRadius * 0.4;
            double offsetY = random.nextDouble() * type.explosionRadius * 0.6;
            double offsetZ = random.nextGaussian() * type.explosionRadius * 0.4;

            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0, 0, 0, 0);
        }

        // Enhanced fire and smoke effects
        for (int i = 0; i < particleCount * 3; i++) {
            double offsetX = random.nextGaussian() * type.explosionRadius;
            double offsetY = random.nextDouble() * type.explosionRadius;
            double offsetZ = random.nextGaussian() * type.explosionRadius;

            if (random.nextBoolean()) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                        1, random.nextGaussian() * 0.2, random.nextDouble() * 0.3, random.nextGaussian() * 0.2, 0.1);
            } else {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                        1, random.nextGaussian() * 0.1, random.nextDouble() * 0.2, random.nextGaussian() * 0.1, 0.05);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§6§lSummons an apocalyptic meteorite storm"));
        tooltip.add(Component.literal("§7§l20-50 meteors rain from the cosmic void"));
        tooltip.add(Component.literal("§c§lDeals catastrophic AOE damage"));
        tooltip.add(Component.literal("§4§lMassive environmental destruction"));
        tooltip.add(Component.literal("§9§l20 second cooldown"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8\"When the stars fall, worlds end.\""));
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