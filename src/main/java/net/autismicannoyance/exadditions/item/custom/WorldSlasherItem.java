package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.network.WorldSlashPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WorldSlasherItem extends Item {
    private static final int CURVED_SLASH_COOLDOWN = 20;
    private static final int FLYING_SLASH_COOLDOWN = 100;
    private static final double CURVED_SLASH_RADIUS = 2.5;
    private static final double CURVED_SLASH_DAMAGE = 12.0;
    private static final double FLYING_SLASH_DAMAGE = 20.0;
    private static final double FLYING_SLASH_RANGE = 50.0;

    // Executor for scheduled damage tasks
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

    public WorldSlasherItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        if (player.level() instanceof ServerLevel serverLevel && !player.getCooldowns().isOnCooldown(this)) {
            createCurvedSlash(serverLevel, player);
            player.getCooldowns().addCooldown(this, CURVED_SLASH_COOLDOWN);
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(p.getUsedItemHand()));
        }
        return super.onLeftClickEntity(stack, player, target);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (entity instanceof Player player && player.level() instanceof ServerLevel serverLevel && !player.getCooldowns().isOnCooldown(this)) {
            createCurvedSlash(serverLevel, player);
            player.getCooldowns().addCooldown(this, CURVED_SLASH_COOLDOWN);
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(p.getUsedItemHand()));
        }
        return super.onEntitySwing(stack, entity);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && attacker.level() instanceof ServerLevel serverLevel) {
            if (!player.getCooldowns().isOnCooldown(this)) {
                createCurvedSlash(serverLevel, player);
                player.getCooldowns().addCooldown(this, CURVED_SLASH_COOLDOWN);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (level instanceof ServerLevel serverLevel) {
            createFlyingSlash(serverLevel, player);
            player.getCooldowns().addCooldown(this, FLYING_SLASH_COOLDOWN);
            stack.hurtAndBreak(2, player, (p) -> p.broadcastBreakEvent(p.getUsedItemHand()));
        }

        return InteractionResultHolder.success(stack);
    }

    private void createCurvedSlash(ServerLevel level, Player player) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 lookDirection = player.getLookAngle();

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new WorldSlashPacket(
                        WorldSlashPacket.SlashType.CURVED,
                        playerPos.x, playerPos.y, playerPos.z,
                        lookDirection.x, lookDirection.y, lookDirection.z,
                        CURVED_SLASH_RADIUS, 0, 0
                )
        );

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6f, 1.8f);

        createCurvedSlashParticles(level, playerPos, lookDirection);
        damageCurvedSlashEntities(level, playerPos, lookDirection, player);
    }

    private void createFlyingSlash(ServerLevel level, Player player) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 lookDirection = player.getLookAngle();
        Vec3 startPos = playerPos.add(lookDirection.scale(2.0));

        // Send visual effect to clients
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new WorldSlashPacket(
                        WorldSlashPacket.SlashType.FLYING,
                        startPos.x, startPos.y, startPos.z,
                        lookDirection.x, lookDirection.y, lookDirection.z,
                        8.0, 6.0, FLYING_SLASH_RANGE
                )
        );

        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.5f);

        // Schedule damage over time instead of instant damage
        scheduleFlyingSlashDamage(level, startPos, lookDirection, player);
    }

    // New method to handle damage over time for the flying slash
    private void scheduleFlyingSlashDamage(ServerLevel level, Vec3 startPos, Vec3 direction, Player attacker) {
        final double slashSpeed = 0.8; // blocks per tick
        final int tickInterval = 2; // Apply damage every 2 ticks (100ms)
        final int maxTicks = (int)(FLYING_SLASH_RANGE / slashSpeed);

        for (int tick = 0; tick < maxTicks; tick += tickInterval) {
            final int currentTick = tick;

            executor.schedule(() -> {
                // Make sure we're still on the server thread
                level.getServer().execute(() -> {
                    if (level.isClientSide) return;

                    double currentDistance = currentTick * slashSpeed;
                    Vec3 currentPos = startPos.add(direction.scale(currentDistance));

                    double progress = (double)currentTick / maxTicks;
                    double sizeMultiplier = 0.6 + (progress * 0.8);
                    double damageWidth = 8.0 * sizeMultiplier;
                    double damageHeight = 6.0 * sizeMultiplier;

                    AABB damageArea = new AABB(
                            currentPos.x - damageWidth/2, currentPos.y - damageHeight/2, currentPos.z - damageWidth/2,
                            currentPos.x + damageWidth/2, currentPos.y + damageHeight/2, currentPos.z + damageWidth/2
                    );

                    List<Entity> entities = level.getEntitiesOfClass(Entity.class, damageArea);

                    for (Entity entity : entities) {
                        if (entity == attacker || !entity.isAlive()) continue;

                        double entityDistance = entity.getEyePosition().distanceTo(currentPos);
                        if (entityDistance <= damageWidth/2 && entity instanceof LivingEntity livingEntity) {
                            float baseDamage = (float)FLYING_SLASH_DAMAGE;
                            float distanceMultiplier = (float)(1.0 - (entityDistance / (damageWidth/2)));
                            float finalDamage = Math.max(5.0f, baseDamage * distanceMultiplier);

                            livingEntity.hurt(level.damageSources().playerAttack(attacker), finalDamage);

                            Vec3 knockback = direction.scale(2.5).add(0, 0.5, 0);
                            entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                            livingEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                            livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));

                            level.sendParticles(ParticleTypes.CRIT, entity.getX(),
                                    entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                                    5, 0.5, 0.5, 0.5, 0.15);
                        }
                    }

                    // Add particles at current position
                    if (currentTick % 6 == 0) {
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, currentPos.x, currentPos.y, currentPos.z,
                                3, sizeMultiplier, sizeMultiplier, sizeMultiplier, 0.05);
                        level.sendParticles(ParticleTypes.END_ROD, currentPos.x, currentPos.y, currentPos.z,
                                2, sizeMultiplier * 0.5, sizeMultiplier * 0.5, sizeMultiplier * 0.5, 0.08);
                    }
                });
            }, currentTick * 50L, TimeUnit.MILLISECONDS); // Convert ticks to milliseconds
        }
    }

    private void createCurvedSlashParticles(ServerLevel level, Vec3 playerPos, Vec3 lookDirection) {
        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }

        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * (i / 20.0 - 0.5) * 0.9;
            double x = Math.sin(angle) * CURVED_SLASH_RADIUS;
            double z = Math.cos(angle) * CURVED_SLASH_RADIUS;

            Vec3 particlePos = playerPos.add(right.scale(x)).add(lookDirection.scale(z));

            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, particlePos.x, particlePos.y, particlePos.z, 2, 0.2, 0.2, 0.2, 0.02);
            } else {
                level.sendParticles(ParticleTypes.END_ROD, particlePos.x, particlePos.y, particlePos.z, 1, 0.15, 0.15, 0.15, 0.05);
            }
        }
    }

    private void damageCurvedSlashEntities(ServerLevel level, Vec3 playerPos, Vec3 lookDirection, Player attacker) {
        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }

        AABB damageArea = new AABB(
                playerPos.x - CURVED_SLASH_RADIUS - 1, playerPos.y - 3, playerPos.z - CURVED_SLASH_RADIUS - 1,
                playerPos.x + CURVED_SLASH_RADIUS + 1, playerPos.y + 3, playerPos.z + CURVED_SLASH_RADIUS + 1
        );

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, damageArea);

        for (Entity entity : entities) {
            if (entity == attacker || !entity.isAlive()) continue;

            Vec3 entityPos = entity.getEyePosition();
            double distanceFromPlayer = entityPos.distanceTo(playerPos);

            if (distanceFromPlayer <= CURVED_SLASH_RADIUS + 1 && entity instanceof LivingEntity livingEntity) {
                Vec3 toEntity = entityPos.subtract(playerPos).normalize();
                Vec3 entityRight = toEntity.cross(new Vec3(0, 1, 0));
                double dotProduct = Math.abs(entityRight.dot(right));

                if (dotProduct > 0.3) {
                    float damage = (float)(CURVED_SLASH_DAMAGE * (1.0 - distanceFromPlayer / CURVED_SLASH_RADIUS));
                    damage = Math.max(3.0f, damage);

                    livingEntity.hurt(level.damageSources().playerAttack(attacker), damage);

                    Vec3 knockback = toEntity.scale(1.2).add(0, 0.2, 0);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                    livingEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                }
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}