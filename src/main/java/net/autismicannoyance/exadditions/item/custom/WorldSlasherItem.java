package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;

        // Send out three distinct flying slashes on fully charged attack
        if (level instanceof ServerLevel serverLevel) {
            sendDistinctFlyingSlashes(serverLevel, player);
            // Execute the vertical arc slash
            executeVerticalArcSlash(level, player);
        }

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
        VectorRenderer.Transform waveTransform = VectorRenderer.Transform.IDENTITY;

        // Color scheme: Black and white with void effects
        int primaryColor, secondaryColor, accentColor;

        switch (waveIndex % 3) {
            case 0: // Pure black wave
                primaryColor = (int)(0x80 * intensity) << 24 | 0x000000;
                secondaryColor = (int)(0xFF * intensity) << 24 | 0x111111;
                accentColor = (int)(0xFF * intensity) << 24 | 0x000000;
                break;
            case 1: // Pure white wave
                primaryColor = (int)(0x80 * intensity) << 24 | 0xFFFFFF;
                secondaryColor = (int)(0xFF * intensity) << 24 | 0xEEEEEE;
                accentColor = (int)(0xFF * intensity) << 24 | 0xFFFFFF;
                break;
            default: // Void/purple accent wave
                primaryColor = (int)(0x60 * intensity) << 24 | 0x2A0A2E;
                secondaryColor = (int)(0xFF * intensity) << 24 | 0x4A1A4E;
                accentColor = (int)(0xFF * intensity) << 24 | 0x8A3A8E;
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

        // Particle effects and damage
        createWaveParticleEffects(level, arcPoints, waveIndex, intensity);
        damageEntitiesInArcWave(level, arcPoints, waveIndex, intensity, player);
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
                }
            }
        }
    }

    private void sendDistinctFlyingSlashes(ServerLevel level, Player player) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 lookDirection = player.getLookAngle();
        Vec3 right = lookDirection.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = lookDirection.cross(new Vec3(1, 0, 0)).normalize();
        }

        // Create 3 distinct flying curved slashes with gaps between them
        for (int i = 0; i < 3; i++) {
            int delay = i * 8; // 8 tick gap between each slash
            double spreadAngle = (i - 1) * 20.0; // -20, 0, +20 degree spread

            // Start position with slight vertical offset for each slash
            Vec3 startOffset = right.scale((i - 1) * 1.5).add(new Vec3(0, (i - 1) * 0.5, 0));
            Vec3 startPos = playerPos.add(lookDirection.scale(2.0)).add(startOffset);

            // Direction with spread
            Vec3 slashDirection = rotateVectorAroundY(lookDirection, Math.toRadians(spreadAngle));

            // Create the flying slash
            createDistinctFlyingSlash(level, startPos, slashDirection, player, i, delay);
        }

        // Sound effects
        level.playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 0.8f);
    }

    private void createDistinctFlyingSlash(ServerLevel level, Vec3 startPos, Vec3 direction,
                                           Player owner, int slashIndex, int delay) {
        VectorRenderer.Transform slashTransform = VectorRenderer.Transform.IDENTITY;

        double slashSpeed = 1.5;
        double maxRange = 35.0;
        int slashLifetime = (int)(maxRange / slashSpeed);

        // Colors per slash type
        int primaryColor, secondaryColor;
        switch (slashIndex) {
            case 0:
                primaryColor = 0xE0000000;
                secondaryColor = 0xFF111111;
                break;
            case 1:
                primaryColor = 0xE0FFFFFF;
                secondaryColor = 0xFFEEEEEE;
                break;
            default:
                primaryColor = 0xD02A0A2E;
                secondaryColor = 0xFF4A1A4E;
                break;
        }

        Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (right.length() < 0.1) {
            right = direction.cross(new Vec3(1, 0, 0)).normalize();
        }
        Vec3 up = right.cross(direction).normalize();

        // Create expanding curved slash
        for (int tick = 0; tick < slashLifetime; tick++) {
            Vec3 currentPos = startPos.add(direction.scale(tick * slashSpeed));
            double expansionFactor = 0.3 + (tick * 1.7 / slashLifetime);
            double baseWidth = 3.0 * expansionFactor;
            double baseHeight = 4.0 * expansionFactor;

            // Create curved arc points
            List<Vec3> arcPoints = createCurvedSlashArc(currentPos, direction, right, up,
                    baseWidth, baseHeight, 12, slashIndex);

            int visualDuration = Math.max(5, 35 - tick);

            // Create triangular segments for the slash
            if (arcPoints.size() >= 6) {
                for (int i = 0; i < arcPoints.size() - 3; i += 3) {
                    Vec3 p1 = arcPoints.get(i);
                    Vec3 p2 = arcPoints.get(i + 1);
                    Vec3 p3 = arcPoints.get(i + 2);

                    int[] colors = {primaryColor, secondaryColor, primaryColor};
                    VectorRenderer.drawPlaneWorld(p1, p2, p3, colors, true,
                            visualDuration, slashTransform);
                }
            }
        }

        // Create particles and damage
        createDistinctSlashParticles(level, startPos, direction, slashLifetime, slashSpeed, slashIndex);
        damageDistinctSlashPath(level, startPos, direction, slashLifetime, slashSpeed, owner, slashIndex);
    }

    private List<Vec3> createCurvedSlashArc(Vec3 center, Vec3 direction, Vec3 right, Vec3 up,
                                            double width, double height, int segments, int slashIndex) {
        List<Vec3> arcPoints = new ArrayList<>();

        double arcCurvature = 0.8 + slashIndex * 0.2;
        double arcSpread = Math.PI * (0.6 + slashIndex * 0.1);

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double angle = (t - 0.5) * arcSpread;

            double curveOffset = Math.sin(t * Math.PI) * arcCurvature;
            double radialOffset = Math.cos(angle) * width * 0.5;
            double heightOffset = Math.sin(angle) * height * 0.5;
            double forwardOffset = curveOffset * height * 0.3;

            Vec3 point = center
                    .add(right.scale(radialOffset))
                    .add(up.scale(heightOffset))
                    .add(direction.scale(forwardOffset));

            arcPoints.add(point);
        }

        return arcPoints;
    }

    private void createDistinctSlashParticles(ServerLevel level, Vec3 startPos, Vec3 direction,
                                              int lifetime, double speed, int slashIndex) {
        Random random = new Random(slashIndex * 12345L);

        for (int tick = 0; tick < lifetime; tick += 2) {
            Vec3 currentPos = startPos.add(direction.scale(tick * speed));
            double expansionFactor = 0.5 + (tick * 1.5 / lifetime);

            for (int p = 0; p < 3; p++) {
                Vec3 particlePos = currentPos.add(
                        (random.nextDouble() - 0.5) * expansionFactor * 2,
                        (random.nextDouble() - 0.5) * expansionFactor * 2,
                        (random.nextDouble() - 0.5) * expansionFactor * 2
                );

                switch (slashIndex) {
                    case 0:
                        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                                particlePos.x, particlePos.y, particlePos.z, 2,
                                0.4, 0.4, 0.4, 0.03);
                        break;
                    case 1:
                        level.sendParticles(ParticleTypes.END_ROD,
                                particlePos.x, particlePos.y, particlePos.z, 1,
                                0.3, 0.3, 0.3, 0.06);
                        break;
                    default:
                        level.sendParticles(ParticleTypes.PORTAL,
                                particlePos.x, particlePos.y, particlePos.z, 2,
                                0.2, 0.2, 0.2, 0.12);
                        break;
                }
            }
        }
    }

    private void damageDistinctSlashPath(ServerLevel level, Vec3 startPos, Vec3 direction,
                                         int lifetime, double speed, Player owner, int slashIndex) {
        double totalRange = lifetime * speed;
        int checkPoints = (int)(totalRange / 1.5);

        for (int i = 0; i < checkPoints; i++) {
            double distance = i * 1.5;
            Vec3 checkPos = startPos.add(direction.scale(distance));
            double expansionFactor = 0.5 + (distance / totalRange) * 2.0;

            double damageRadius = 2.0 * expansionFactor;
            double damageHeight = 3.0 * expansionFactor;

            AABB damageArea = new AABB(
                    checkPos.x - damageRadius, checkPos.y - damageHeight, checkPos.z - damageRadius,
                    checkPos.x + damageRadius, checkPos.y + damageHeight, checkPos.z + damageRadius
            );

            List<Entity> nearbyEntities = level.getEntitiesOfClass(Entity.class, damageArea);

            for (Entity entity : nearbyEntities) {
                if (entity == owner || !entity.isAlive()) continue;

                double entityDistance = entity.getEyePosition().distanceTo(checkPos);
                if (entityDistance <= damageRadius) {
                    if (entity instanceof LivingEntity livingEntity) {
                        float baseDamage = 15.0f + (slashIndex * 2.0f);
                        float distanceMultiplier = (float)(1.0 - (entityDistance / damageRadius));
                        float expansionMultiplier = (float)(0.5 + expansionFactor * 0.5);
                        float finalDamage = Math.max(4.0f, baseDamage * distanceMultiplier * expansionMultiplier);

                        livingEntity.hurt(level.damageSources().playerAttack(owner), finalDamage);

                        Vec3 knockback = direction.scale(2.0 * expansionFactor).add(0, 0.5, 0);
                        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                        // Different effects per slash
                        switch (slashIndex) {
                            case 0:
                                livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                                break;
                            case 1:
                                livingEntity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                                break;
                            default:
                                livingEntity.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                                break;
                        }
                    }
                }
            }
        }
    }

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
        return true;
    }
}