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

    // Enhanced wave parameters to match visual
    private static final double WAVE_SPAN = Math.PI * 1.4;
    private static final int DAMAGE_SEGMENTS = 30; // More precise hitbox segments

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

    // Enhanced method to handle damage matching the flowing wave visual
    private void scheduleFlyingSlashDamage(ServerLevel level, Vec3 startPos, Vec3 direction, Player attacker) {
        final double slashSpeed = 1.2; // Faster to match visual
        final int tickInterval = 2; // Apply damage every 2 ticks (100ms)
        final int maxTicks = (int)(FLYING_SLASH_RANGE / slashSpeed);

        for (int tick = 0; tick < maxTicks; tick += tickInterval) {
            final int currentTick = tick;

            executor.schedule(() -> {
                level.getServer().execute(() -> {
                    if (level.isClientSide) return;

                    double currentDistance = currentTick * slashSpeed;
                    Vec3 currentPos = startPos.add(direction.scale(currentDistance));

                    double progress = (double)currentTick / maxTicks;
                    double sizeMultiplier = 1.0 + (progress * 0.8);

                    // Calculate precise wave hitbox matching the visual
                    damageFlowingWaveArea(level, currentPos, direction, sizeMultiplier, progress, attacker);
                });
            }, currentTick * 50L, TimeUnit.MILLISECONDS);
        }
    }

    private void damageFlowingWaveArea(ServerLevel level, Vec3 center, Vec3 direction, double sizeMultiplier, double progress, Player attacker) {
        Vec3 forward = safeNormalize(direction);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 origin = center.add(forwardFlat.scale(4.0 * sizeMultiplier * 0.4)).add(worldUp.scale(-0.15));

        // Create damage area that matches the flowing wave shape
        double baseWidth = 8.0 * sizeMultiplier;
        double waveSpan = Math.PI * 0.8;
        double startAngle = -waveSpan / 2.0;

        // Expanded damage area to catch all entities in the wave
        AABB searchArea = new AABB(
                center.x - baseWidth, center.y - 3.0, center.z - baseWidth,
                center.x + baseWidth, center.y + 3.0, center.z + baseWidth
        );

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, searchArea);

        for (Entity entity : entities) {
            if (entity == attacker || !entity.isAlive() || !(entity instanceof LivingEntity livingEntity)) continue;

            Vec3 entityPos = entity.getEyePosition();
            Vec3 toEntity = entityPos.subtract(origin);

            // Check if entity is within the flowing wave shape
            boolean hitByWave = false;
            double closestDistance = Double.MAX_VALUE;
            double damageMultiplier = 0;

            // Sample points along the wave to check for hits
            for (int i = 0; i <= DAMAGE_SEGMENTS; i++) {
                double t = (double) i / DAMAGE_SEGMENTS;
                double angle = startAngle + (waveSpan * t);

                double waveProfile = Math.sin(t * Math.PI);
                double thickness = waveProfile * 0.3 + 0.1;
                double waveRadius = baseWidth * 0.5;

                double innerRadius = waveRadius * (0.4 - thickness * 0.2);
                double outerRadius = waveRadius * (0.7 + thickness * 0.3);

                Vec3 waveDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
                double verticalFlow = Math.sin(t * Math.PI) * baseWidth * 0.05;

                Vec3 wavePoint = origin.add(waveDir.scale((innerRadius + outerRadius) / 2))
                        .add(worldUp.scale(verticalFlow));

                double distanceToWave = entityPos.distanceTo(wavePoint);
                double maxHitDistance = (outerRadius - innerRadius) / 2 + 1.5; // Wave thickness + entity size buffer

                if (distanceToWave <= maxHitDistance) {
                    hitByWave = true;
                    if (distanceToWave < closestDistance) {
                        closestDistance = distanceToWave;
                        damageMultiplier = Math.max(0.3, 1.0 - (distanceToWave / maxHitDistance));
                    }
                }
            }

            if (hitByWave) {
                float baseDamage = (float)FLYING_SLASH_DAMAGE;
                float progressMultiplier = (float)(1.0 - progress * 0.3); // Less damage over distance
                float finalDamage = Math.max(8.0f, baseDamage * (float)damageMultiplier * progressMultiplier);

                livingEntity.hurt(level.damageSources().playerAttack(attacker), finalDamage);

                // Wave-style knockback
                Vec3 knockbackDir = entityPos.subtract(center).normalize();
                Vec3 knockback = knockbackDir.scale(2.0).add(direction.scale(1.5)).add(0, 0.6, 0);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                livingEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));

                // Hit particles
                level.sendParticles(ParticleTypes.CRIT, entityPos.x, entityPos.y, entityPos.z,
                        8, 0.8, 0.8, 0.8, 0.2);
                level.sendParticles(ParticleTypes.END_ROD, entityPos.x, entityPos.y, entityPos.z,
                        4, 0.5, 0.5, 0.5, 0.15);
            }
        }

        // Add flowing particles at current wave position
        if (progress < 0.8) { // Don't spawn particles near end
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z,
                    4, sizeMultiplier, sizeMultiplier, sizeMultiplier, 0.08);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                    3, sizeMultiplier * 0.7, sizeMultiplier * 0.7, sizeMultiplier * 0.7, 0.12);
        }
    }

    private void createCurvedSlashParticles(ServerLevel level, Vec3 playerPos, Vec3 lookDirection) {
        Vec3 forward = safeNormalize(lookDirection);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 origin = playerPos.add(forwardFlat.scale(CURVED_SLASH_RADIUS * 0.3)).add(worldUp.scale(-0.1));

        // Generate particles matching the flowing wave pattern
        int particleCount = 35;
        double startAngle = -WAVE_SPAN / 2;

        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / (particleCount - 1);
            double angle = startAngle + (WAVE_SPAN * t);

            double waveProfile = Math.sin(t * Math.PI);
            double flowProfile = Math.sin(t * Math.PI * 2.0) * 0.3;
            double combinedProfile = waveProfile + flowProfile;

            double particleRadius = CURVED_SLASH_RADIUS * (0.5 + 0.4 * Math.max(0.1, combinedProfile));

            Vec3 waveDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
            double verticalFlow = Math.sin(t * Math.PI * 1.5) * CURVED_SLASH_RADIUS * 0.08;
            double forwardFlow = Math.sin(t * Math.PI) * CURVED_SLASH_RADIUS * 0.15;

            Vec3 particlePos = origin
                    .add(waveDir.scale(particleRadius))
                    .add(worldUp.scale(verticalFlow))
                    .add(forwardFlat.scale(forwardFlow));

            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        particlePos.x, particlePos.y, particlePos.z,
                        2, 0.3, 0.3, 0.3, 0.03);
            } else if (i % 3 == 1) {
                level.sendParticles(ParticleTypes.END_ROD,
                        particlePos.x, particlePos.y, particlePos.z,
                        1, 0.2, 0.2, 0.2, 0.08);
            } else {
                level.sendParticles(ParticleTypes.ENCHANT,
                        particlePos.x, particlePos.y, particlePos.z,
                        1, 0.25, 0.25, 0.25, 0.1);
            }
        }
    }

    private void damageCurvedSlashEntities(ServerLevel level, Vec3 playerPos, Vec3 lookDirection, Player attacker) {
        Vec3 forward = safeNormalize(lookDirection);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 forwardFlat = new Vec3(forward.x, 0.0, forward.z);
        if (forwardFlat.length() < 1e-6) forwardFlat = safeNormalize(forward);
        else forwardFlat = safeNormalize(forwardFlat);

        Vec3 right = safeNormalize(forwardFlat.cross(worldUp));
        Vec3 origin = playerPos.add(forwardFlat.scale(CURVED_SLASH_RADIUS * 0.3)).add(worldUp.scale(-0.1));

        // Expanded damage area for the flowing wave
        AABB damageArea = new AABB(
                playerPos.x - CURVED_SLASH_RADIUS * 2, playerPos.y - 3.0, playerPos.z - CURVED_SLASH_RADIUS * 2,
                playerPos.x + CURVED_SLASH_RADIUS * 2, playerPos.y + 3.0, playerPos.z + CURVED_SLASH_RADIUS * 2
        );

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, damageArea);

        for (Entity entity : entities) {
            if (entity == attacker || !entity.isAlive() || !(entity instanceof LivingEntity livingEntity)) continue;

            Vec3 entityPos = entity.getEyePosition();

            // Check if entity is within the flowing wave pattern
            boolean hitByWave = false;
            double bestDamageMultiplier = 0;

            // Sample multiple points along the wave path for precise hit detection
            double startAngle = -WAVE_SPAN / 2;
            for (int i = 0; i <= DAMAGE_SEGMENTS; i++) {
                double t = (double) i / DAMAGE_SEGMENTS;
                double angle = startAngle + (WAVE_SPAN * t);

                double waveProfile = Math.sin(t * Math.PI);
                double flowProfile = Math.sin(t * Math.PI * 2.0) * 0.3;
                double combinedProfile = Math.max(0.1, waveProfile + flowProfile);

                double thickness = combinedProfile * 0.4 + 0.1;
                double baseRadius = CURVED_SLASH_RADIUS;

                double innerRadius = baseRadius * (0.5 - thickness * 0.3);
                double outerRadius = baseRadius * (0.8 + thickness * 0.5);

                Vec3 waveDir = forwardFlat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
                double verticalFlow = Math.sin(t * Math.PI * 1.5) * CURVED_SLASH_RADIUS * 0.08;
                double forwardFlow = Math.sin(t * Math.PI) * CURVED_SLASH_RADIUS * 0.15;

                Vec3 waveCenter = origin.add(waveDir.scale((innerRadius + outerRadius) / 2))
                        .add(worldUp.scale(verticalFlow))
                        .add(forwardFlat.scale(forwardFlow));

                double distanceToWave = entityPos.distanceTo(waveCenter);
                double waveThickness = (outerRadius - innerRadius) / 2 + 1.2; // Wave thickness + entity buffer

                if (distanceToWave <= waveThickness) {
                    hitByWave = true;
                    double damageMultiplier = Math.max(0.4, 1.0 - (distanceToWave / waveThickness));
                    damageMultiplier *= combinedProfile; // Stronger damage at wave peaks
                    bestDamageMultiplier = Math.max(bestDamageMultiplier, damageMultiplier);
                }
            }

            if (hitByWave) {
                float damage = (float)(CURVED_SLASH_DAMAGE * bestDamageMultiplier);
                damage = Math.max(5.0f, damage);

                livingEntity.hurt(level.damageSources().playerAttack(attacker), damage);

                // Flowing wave knockback
                Vec3 toEntity = entityPos.subtract(origin);
                Vec3 knockbackBase = toEntity.normalize();
                Vec3 knockback = knockbackBase.scale(1.8)
                        .add(forward.scale(1.2))
                        .add(worldUp.scale(0.4));
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                livingEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                livingEntity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0)); // Mark hit enemies

                // Enhanced hit particles
                level.sendParticles(ParticleTypes.CRIT, entityPos.x, entityPos.y, entityPos.z,
                        5, 0.5, 0.5, 0.5, 0.15);
                level.sendParticles(ParticleTypes.END_ROD, entityPos.x, entityPos.y, entityPos.z,
                        3, 0.4, 0.4, 0.4, 0.12);
            }
        }
    }

    private Vec3 safeNormalize(Vec3 v) {
        if (v == null) return new Vec3(0, 0, 1);
        double len = v.length();
        if (len < 1e-6) return new Vec3(0, 0, 1);
        return v.scale(1.0 / len);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}