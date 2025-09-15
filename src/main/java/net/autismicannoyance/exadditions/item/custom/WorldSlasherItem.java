package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * WorldSlasher - Creates a massive vertical curved arc slash that propagates outward
 * Features dramatic black and white visual effects with reality-tearing aesthetics
 */
public class WorldSlasherItem extends Item {
    private static final int USE_DURATION = 40; // 2 seconds charge time
    private static final int COOLDOWN_TICKS = 300; // 15 second cooldown
    private static final double SLASH_RANGE = 60.0; // How far the slash reaches
    private static final double SLASH_HEIGHT = 25.0; // Height of the arc
    private static final double SLASH_WIDTH = 12.0; // Width of the slash area
    private static final double SLASH_DAMAGE = 30.0; // Base damage
    private static final int SLASH_DURATION_TICKS = 120; // How long the visual effect lasts
    private static final double ARC_CURVATURE = 0.4; // How curved the arc is (higher = more curved)

    public WorldSlasherItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        // Send out flying slashes on normal attack
        if (player.level() instanceof ServerLevel serverLevel) {
            sendFlyingSlashes(serverLevel, player);

            // Small cooldown for flying slashes (1 second)
            player.getCooldowns().addCooldown(this, 20);

            // Light damage to the item
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(p.getUsedItemHand()));
        }

        return super.onLeftClickEntity(stack, player, target);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Also trigger flying slashes when hitting any living entity
        if (attacker instanceof Player player && attacker.level() instanceof ServerLevel serverLevel) {
            if (!player.getCooldowns().isOnCooldown(this)) {
                sendFlyingSlashes(serverLevel, player);
                player.getCooldowns().addCooldown(this, 20);
            }
        }

        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;

        int chargeTime = USE_DURATION - remainingUseDuration;
        float chargeProgress = (float) chargeTime / USE_DURATION;

        if (level instanceof ServerLevel serverLevel) {
            // Dark energy charging effects - monochrome particles
            double radius = 1.5 + (chargeTime * 0.08);
            int particleCount = Math.min(chargeTime / 3, 8);

            for (int i = 0; i < particleCount; i++) {
                double angle = (level.getGameTime() * 0.3 + i * (360.0 / particleCount)) * Math.PI / 180.0;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;
                double y = player.getY() + 0.5 + Math.sin(level.getGameTime() * 0.2 + i) * 0.8;

                // Alternating black and white particle effects
                if (i % 2 == 0) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            x, y, z, 1, 0, 0, 0, 0.02);
                } else {
                    serverLevel.sendParticles(ParticleTypes.END_ROD,
                            x, y, z, 1, 0, 0, 0, 0.02);
                }
            }

            // Void energy building up in front of player
            Vec3 lookDir = player.getLookAngle();
            Vec3 voidCenter = player.getEyePosition().add(lookDir.scale(2.0));

            for (int i = 0; i < chargeTime / 5; i++) {
                Vec3 sparkPos = voidCenter.add(
                        (level.getRandom().nextDouble() - 0.5) * chargeProgress * 2,
                        (level.getRandom().nextDouble() - 0.5) * chargeProgress * 2,
                        (level.getRandom().nextDouble() - 0.5) * chargeProgress * 2
                );

                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        sparkPos.x, sparkPos.y, sparkPos.z, 1,
                        0, 0, 0, 0.1);
            }

            // Charging sound with escalating pitch
            if (chargeTime % 8 == 0) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                        0.4f + chargeProgress * 0.6f, 0.5f + chargeProgress * 1.5f);
            }
        }

        // Slow player while charging
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, true, false));

        // Screen shake effect during late charge
        if (chargeProgress > 0.7f && level.isClientSide) {
            // Client-side screen shake would go here if implemented
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;

        // Execute the vertical arc slash
        executeVerticalArcSlash(level, player);

        // Apply cooldown and damage item
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(p.getUsedItemHand()));

        return stack;
    }

    private void executeVerticalArcSlash(Level level, Player player) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 lookDirection = player.getLookAngle();

        if (level instanceof ServerLevel serverLevel) {
            // Create the propagating arc slash effect
            createPropagatingArcSlash(serverLevel, playerPos, lookDirection, player);

            // Dramatic sound effects
            level.playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS,
                    1.5f, 0.3f);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                    2.0f, 0.1f);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                    1.0f, 2.0f);
        }
    }

    private void createPropagatingArcSlash(ServerLevel level, Vec3 startPos, Vec3 direction, Player player) {
        VectorRenderer.Transform slashTransform = VectorRenderer.Transform.IDENTITY;

        // Calculate arc parameters
        Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = direction.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = new Vec3(0, 1, 0);

        // Create the main vertical curved arc
        List<Vec3> arcPoints = generateVerticalArc(startPos, direction, right, up);

        // Animate the slash propagating outward in waves
        for (int wave = 0; wave < 8; wave++) {
            int delay = wave * 3; // Each wave starts 3 ticks after the previous
            double waveIntensity = 1.0 - (wave * 0.1); // Later waves are slightly weaker

            // Schedule each wave to appear with a delay
            scheduleSlashWave(level, arcPoints, wave, delay, waveIntensity, player);
        }
    }

    private List<Vec3> generateVerticalArc(Vec3 start, Vec3 direction, Vec3 right, Vec3 up) {
        List<Vec3> arcPoints = new ArrayList<>();

        int segments = 40;
        double arcAngle = Math.PI * 0.8; // 144 degrees vertical arc

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double distance = t * SLASH_RANGE;

            // Vertical arc calculation - starts high, curves down
            double verticalAngle = (arcAngle * 0.5) - (t * arcAngle); // Start at +72°, end at -72°
            double heightOffset = Math.sin(verticalAngle) * SLASH_HEIGHT;
            double forwardOffset = Math.cos(verticalAngle) * distance;

            // Width varies along the arc (wider in middle)
            double widthMultiplier = Math.sin(t * Math.PI) * 1.5; // Sine wave for width variation

            Vec3 arcCenter = start.add(direction.scale(forwardOffset)).add(up.scale(heightOffset));

            // Create multiple points across the width at this segment
            int widthSegments = 8;
            for (int j = -widthSegments; j <= widthSegments; j++) {
                double widthT = (double) j / widthSegments;
                double actualWidth = SLASH_WIDTH * widthMultiplier * Math.abs(widthT);

                Vec3 point = arcCenter.add(right.scale(widthT * actualWidth));
                arcPoints.add(point);
            }
        }

        return arcPoints;
    }

    private void scheduleSlashWave(ServerLevel level, List<Vec3> arcPoints, int waveIndex, int delay, double intensity, Player player) {
        // This creates the visual effect for each wave of the slash
        VectorRenderer.Transform waveTransform = VectorRenderer.Transform.IDENTITY;

        // Color scheme: Black and white with void effects
        int primaryColor, secondaryColor, accentColor;

        switch (waveIndex % 3) {
            case 0: // Pure black wave
                primaryColor = (int)(0x80 * intensity) << 24 | 0x000000; // Semi-transparent black
                secondaryColor = (int)(0xFF * intensity) << 24 | 0x111111; // Dark gray
                accentColor = (int)(0xFF * intensity) << 24 | 0x000000; // Pure black
                break;
            case 1: // Pure white wave
                primaryColor = (int)(0x80 * intensity) << 24 | 0xFFFFFF; // Semi-transparent white
                secondaryColor = (int)(0xFF * intensity) << 24 | 0xEEEEEE; // Light gray
                accentColor = (int)(0xFF * intensity) << 24 | 0xFFFFFF; // Pure white
                break;
            default: // Void/purple accent wave
                primaryColor = (int)(0x60 * intensity) << 24 | 0x2A0A2E; // Deep purple
                secondaryColor = (int)(0xFF * intensity) << 24 | 0x4A1A4E; // Purple
                accentColor = (int)(0xFF * intensity) << 24 | 0x8A3A8E; // Light purple
                break;
        }

        // Create the main arc slash plane
        for (int i = 0; i < arcPoints.size() - 34; i += 17) {
            if (i + 34 < arcPoints.size()) {
                Vec3 p1 = arcPoints.get(i);
                Vec3 p2 = arcPoints.get(i + 17);
                Vec3 p3 = arcPoints.get(i + 34);

                int[] colors = {primaryColor, secondaryColor, accentColor};
                VectorRenderer.drawPlaneWorld(p1, p2, p3, colors, true,
                        SLASH_DURATION_TICKS - waveIndex * 10, waveTransform);
            }
        }

        // Create edge trails for dramatic effect
        if (arcPoints.size() >= 34) {
            // Top edge of the arc
            for (int i = 0; i < arcPoints.size() - 17; i += 17) {
                Vec3 start = arcPoints.get(i);
                Vec3 end = arcPoints.get(i + 17);

                VectorRenderer.drawLineWorld(start, end, accentColor,
                        4.0f - waveIndex * 0.3f, true, SLASH_DURATION_TICKS - waveIndex * 8, waveTransform);
            }
        }

        // Reality tear effects - jagged lines that represent tears in space
        Random random = new Random(waveIndex * 12345);
        for (int i = 0; i < 15; i++) {
            if (arcPoints.size() > 10) {
                Vec3 tearStart = arcPoints.get(random.nextInt(arcPoints.size()));
                Vec3 tearEnd = tearStart.add(
                        (random.nextDouble() - 0.5) * 3.0,
                        (random.nextDouble() - 0.5) * 3.0,
                        (random.nextDouble() - 0.5) * 3.0
                );

                int tearColor = random.nextBoolean() ? 0xFF000000 : 0xFFFFFFFF; // Pure black or white
                VectorRenderer.drawLineWorld(tearStart, tearEnd, tearColor,
                        2.0f, true, SLASH_DURATION_TICKS / 2, waveTransform);
            }
        }

        // Void spheres along the arc path
        for (int i = 0; i < 8; i++) {
            if (arcPoints.size() > 0) {
                Vec3 spherePos = arcPoints.get((i * arcPoints.size()) / 8);
                float sphereRadius = 0.5f + (float)(intensity * 0.8);
                int sphereColor = (waveIndex % 2 == 0) ? 0xC0000000 : 0xC0FFFFFF;

                VectorRenderer.drawSphereWorld(spherePos, sphereRadius, sphereColor,
                        8, 6, false, SLASH_DURATION_TICKS - waveIndex * 12, waveTransform);
            }
        }

        // Particle effects for each wave
        createWaveParticleEffects(level, arcPoints, waveIndex, intensity);

        // Damage entities in this wave's path
        damageEntitiesInArcWave(level, arcPoints, waveIndex, intensity, player);

        // Environmental destruction
        createEnvironmentalDestruction(level, arcPoints, waveIndex, intensity);
    }
    private void createEnvironmentalDestruction(ServerLevel level, List<Vec3> arcPoints, int waveIndex, double intensity) {
        if (arcPoints == null || arcPoints.isEmpty()) return;

        // use a Random seeded from waveIndex for repeatability per wave
        Random rnd = new Random(waveIndex * 1337L + (long)(intensity * 1000));

        // chance multiplier decreases for later waves
        double baseChance = 0.08 * intensity * Math.max(0.1, 1.0 - waveIndex * 0.08);

        // Step through arc points sparsely so we don't check every block
        int step = Math.max(1, arcPoints.size() / 40);

        for (int i = 0; i < arcPoints.size(); i += step) {
            if (rnd.nextDouble() > baseChance) continue;

            Vec3 v = arcPoints.get(i);
            BlockPos pos = new BlockPos(Mth.floor(v.x), Mth.floor(v.y), Mth.floor(v.z));

            // Safety: ensure within world bounds
            if (pos.getY() < 1 || pos.getY() > level.getMaxBuildHeight() - 1) continue;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            // Filter: only allow relatively weak / non-critical blocks to be destroyed
            float destroySpeed = state.getDestroySpeed(level, pos);
            // skip bedrock-like or heavy blocks (negative or very high destroySpeed)
            if (destroySpeed < 0 || destroySpeed > 50f) continue;

            // Extra guard: skip important blocks like containers (chests) / tile-entities
            if (state.hasBlockEntity()) continue;

            // final randomness check and then destroy without drops (so it feels like "reality tear")
            if (rnd.nextDouble() < 0.6) {
                level.destroyBlock(pos, false); // don't drop items
                // small poof particles so player sees the tear
                level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.04);
            } else {
                // sometimes replace with air quietly to make it harsher
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.08);
            }
        }
    }
    private void createWaveParticleEffects(ServerLevel level, List<Vec3> arcPoints, int waveIndex, double intensity) {
        Random random = new Random();

        // Main slash particles
        int particleCount = (int)(50 * intensity);
        for (int i = 0; i < particleCount && i < arcPoints.size(); i++) {
            Vec3 particlePos = arcPoints.get(random.nextInt(arcPoints.size()));

            // Black and white particle effects
            if (waveIndex % 2 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        particlePos.x, particlePos.y, particlePos.z, 3,
                        0.8, 0.8, 0.8, 0.05);
            } else {
                level.sendParticles(ParticleTypes.END_ROD,
                        particlePos.x, particlePos.y, particlePos.z, 2,
                        0.5, 0.5, 0.5, 0.1);
            }

            // Void particles for tear effect
            level.sendParticles(ParticleTypes.PORTAL,
                    particlePos.x, particlePos.y, particlePos.z, 1,
                    0.3, 0.3, 0.3, 0.2);
        }

        // Dramatic explosion particles at key points
        if (waveIndex < 3 && arcPoints.size() > 10) {
            for (int i = 0; i < 5; i++) {
                Vec3 explosionPos = arcPoints.get(i * (arcPoints.size() / 5));
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        explosionPos.x, explosionPos.y, explosionPos.z, 1,
                        0, 0, 0, 0);
            }
        }
    }

    private void damageEntitiesInArcWave(ServerLevel level, List<Vec3> arcPoints, int waveIndex, double intensity, Player attacker) {
        if (arcPoints.isEmpty()) return;

        // Create bounding box for the arc
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;

        for (Vec3 point : arcPoints) {
            minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
            minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
        }

        AABB arcBox = new AABB(minX - 5, minY - 5, minZ - 5, maxX + 5, maxY + 5, maxZ + 5);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, arcBox);

        for (Entity entity : entities) {
            if (entity == attacker || !entity.isAlive()) continue;

            Vec3 entityPos = entity.getEyePosition();

            // Check if entity is within the arc slash area
            double closestDistance = Double.MAX_VALUE;
            for (Vec3 arcPoint : arcPoints) {
                double dist = arcPoint.distanceTo(entityPos);
                closestDistance = Math.min(closestDistance, dist);
            }

            if (closestDistance <= SLASH_WIDTH / 2) {
                double damageMultiplier = intensity * Math.max(0.2, 1.0 - (closestDistance / (SLASH_WIDTH / 2)));
                float finalDamage = (float) (SLASH_DAMAGE * damageMultiplier * (1.0 - waveIndex * 0.1));

                if (entity instanceof LivingEntity livingEntity && finalDamage > 1.0f) {
                    livingEntity.hurt(level.damageSources().playerAttack(attacker), finalDamage);

                    // Dramatic knockback with upward component
                    Vec3 knockback = entityPos.subtract(attacker.getEyePosition()).normalize()
                            .add(0, 0.8, 0).normalize().scale(3.0 * damageMultiplier);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                    // Void effects
                    livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                    livingEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                    livingEntity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 2));

                    // Damage indicator particles
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                            entityPos.x, entityPos.y + 1, entityPos.z, 8,
                            0.5, 0.5, 0.5, 0.3);
                }
            }
        }
    }

    private void sendFlyingSlashes(ServerLevel level, Player player) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 lookDirection = player.getLookAngle();
        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }

        // Create 3 flying slash projectiles in a spread pattern
        for (int i = -1; i <= 1; i++) {
            double angleOffset = i * 15.0; // 15 degree spread
            Vec3 slashDirection = rotateVectorAroundY(lookDirection, Math.toRadians(angleOffset));

            // Start position slightly offset
            Vec3 startPos = playerPos.add(lookDirection.scale(1.5)).add(right.scale(i * 0.8));

            // Create flying slash
            createFlyingSlash(level, startPos, slashDirection, player, i);
        }

        // Sound effect for flying slashes
        level.playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                1.2f, 1.5f);
        level.playSound(null, player.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                0.8f, 2.0f);
    }

    private void createFlyingSlash(ServerLevel level, Vec3 startPos, Vec3 direction, Player owner, int slashIndex) {
        VectorRenderer.Transform slashTransform = VectorRenderer.Transform.IDENTITY;

        // Flying slash parameters
        double slashLength = 4.0;
        double slashWidth = 1.5;
        double slashSpeed = 1.2; // blocks per tick
        double maxRange = 25.0; // how far the slash travels
        int slashLifetime = (int)(maxRange / slashSpeed); // ticks to live

        // Color scheme - alternating black and white
        int slashColor = (slashIndex % 2 == 0) ? 0xE0000000 : 0xE0FFFFFF; // Semi-transparent black or white
        int trailColor = (slashIndex % 2 == 0) ? 0xFF111111 : 0xFFEEEEEE; // Dark gray or light gray

        // Calculate slash orientation
        Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = direction.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = right.cross(direction).normalize();

        // Create the flying slash visual that moves over time
        for (int tick = 0; tick < slashLifetime; tick++) {
            Vec3 currentPos = startPos.add(direction.scale(tick * slashSpeed));

            // Create slash shape - diamond/rhombus shape
            Vec3 tip = currentPos.add(direction.scale(slashLength * 0.3));
            Vec3 leftWing = currentPos.add(right.scale(-slashWidth * 0.5));
            Vec3 rightWing = currentPos.add(right.scale(slashWidth * 0.5));
            Vec3 tail = currentPos.add(direction.scale(-slashLength * 0.7));

            // Add slight vertical movement for more dynamic look
            double verticalOffset = Math.sin(tick * 0.3) * 0.2;
            tip = tip.add(up.scale(verticalOffset));
            leftWing = leftWing.add(up.scale(verticalOffset));
            rightWing = rightWing.add(up.scale(verticalOffset));
            tail = tail.add(up.scale(verticalOffset));

            // Create the slash as triangular planes
            int[] colors = {slashColor, slashColor, slashColor};
            int startTick = tick * 2; // Each position appears 2 ticks after the previous
            int duration = 25 - tick; // Later positions fade faster

            // Top triangle
            VectorRenderer.drawPlaneWorld(tip, leftWing, tail, colors, true,
                    duration, slashTransform);
            // Bottom triangle
            VectorRenderer.drawPlaneWorld(tip, tail, rightWing, colors, true,
                    duration, slashTransform);

            // Edge trails for sharpness effect
            VectorRenderer.drawLineWorld(tip, tail, trailColor, 2.5f, true,
                    duration, slashTransform);
            VectorRenderer.drawLineWorld(leftWing, rightWing, trailColor, 1.5f, true,
                    duration, slashTransform);

            // Small energy sphere at the tip
            if (tick % 3 == 0) { // Every 3rd tick
                int sphereColor = (slashIndex % 2 == 0) ? 0x80FFFFFF : 0x80000000; // Opposite color for contrast
                VectorRenderer.drawSphereWorld(tip, 0.15f, sphereColor, 6, 4, false,
                        duration / 2, slashTransform);
            }
        }

        // Create particle trail
        createFlyingSlashParticles(level, startPos, direction, slashLifetime, slashSpeed, slashIndex);

        // Damage entities along the path
        damageFlyingSlashPath(level, startPos, direction, slashLifetime, slashSpeed, owner, slashLength, slashWidth);
    }

    private void createFlyingSlashParticles(ServerLevel level, Vec3 startPos, Vec3 direction,
                                            int lifetime, double speed, int slashIndex) {
        Random random = new Random();

        for (int tick = 0; tick < lifetime; tick += 2) { // Every other tick
            Vec3 currentPos = startPos.add(direction.scale(tick * speed));

            // Add some randomness to particle positions
            Vec3 particlePos = currentPos.add(
                    (random.nextDouble() - 0.5) * 0.8,
                    (random.nextDouble() - 0.5) * 0.8,
                    (random.nextDouble() - 0.5) * 0.8
            );

            if (slashIndex % 2 == 0) {
                // Black slash - dark particles
                level.sendParticles(ParticleTypes.SMOKE,
                        particlePos.x, particlePos.y, particlePos.z, 2,
                        0.3, 0.3, 0.3, 0.02);
            } else {
                // White slash - bright particles
                level.sendParticles(ParticleTypes.END_ROD,
                        particlePos.x, particlePos.y, particlePos.z, 1,
                        0.2, 0.2, 0.2, 0.05);
            }

            // Void particles for mystical effect
            if (tick % 4 == 0) {
                level.sendParticles(ParticleTypes.PORTAL,
                        particlePos.x, particlePos.y, particlePos.z, 1,
                        0.1, 0.1, 0.1, 0.1);
            }
        }
    }

    private void damageFlyingSlashPath(ServerLevel level, Vec3 startPos, Vec3 direction,
                                       int lifetime, double speed, Player owner,
                                       double slashLength, double slashWidth) {

        double totalRange = lifetime * speed;
        int checkPoints = (int)(totalRange / 2.0); // Check every 2 blocks

        for (int i = 0; i < checkPoints; i++) {
            double distance = i * 2.0;
            Vec3 checkPos = startPos.add(direction.scale(distance));

            // Create damage area around this point
            AABB damageArea = new AABB(
                    checkPos.x - slashWidth, checkPos.y - slashLength, checkPos.z - slashWidth,
                    checkPos.x + slashWidth, checkPos.y + slashLength, checkPos.z + slashWidth
            );

            List<Entity> nearbyEntities = level.getEntitiesOfClass(Entity.class, damageArea);

            for (Entity entity : nearbyEntities) {
                if (entity == owner || !entity.isAlive()) continue;

                double entityDistance = entity.getEyePosition().distanceTo(checkPos);
                if (entityDistance <= slashWidth * 1.2) { // Slightly larger hit area

                    if (entity instanceof LivingEntity livingEntity) {
                        float damage = 12.0f - (float)(entityDistance * 2); // 12 damage at center, less at edges
                        damage = Math.max(3.0f, damage); // Minimum 3 damage

                        livingEntity.hurt(level.damageSources().playerAttack(owner), damage);

                        // Knockback in slash direction
                        Vec3 knockback = direction.scale(1.5).add(0, 0.3, 0);
                        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                        // Brief weakness effect
                        livingEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));

                        // Hit particles
                        level.sendParticles(ParticleTypes.CRIT,
                                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 3,
                                0.3, 0.3, 0.3, 0.1);
                    }
                }
            }

            // Break weak blocks along the path
            if (i % 3 == 0) { // Every 6 blocks
                BlockPos blockPos = new BlockPos((int)checkPos.x, (int)checkPos.y, (int)checkPos.z);
                BlockState state = level.getBlockState(blockPos);

                if (!state.isAir()) {
                    float destroySpeed = state.getDestroySpeed(level, blockPos);
                    if (destroySpeed >= 0 && destroySpeed < 3.0f) { // Only very weak blocks
                        if (level.getRandom().nextFloat() < 0.3f) {
                            level.destroyBlock(blockPos, false); // Don't drop items

                            // Small explosion effect
                            level.sendParticles(ParticleTypes.POOF,
                                    blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 3,
                                    0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }
            }
        }
    }

    // Utility method to rotate a vector around the Y axis
    private Vec3 rotateVectorAroundY(Vec3 vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double newX = vector.x * cos - vector.z * sin;
        double newZ = vector.x * sin + vector.z * cos;

        return new Vec3(newX, vector.y, newZ);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // Always has enchanted glow
    }
}