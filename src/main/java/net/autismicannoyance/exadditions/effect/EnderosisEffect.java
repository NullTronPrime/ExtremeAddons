package net.autismicannoyance.exadditions.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

public class EnderosisEffect extends MobEffect {

    public EnderosisEffect() {
        super(MobEffectCategory.NEUTRAL, 0x1a1a2e); // Dark purple color like enderman eyes
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player && !player.level().isClientSide) {
            // Check if player is touching water
            if (isInWater(player)) {
                teleportAwayFromWater(player, amplifier);
                // Deal 1 heart (2 health points) of damage
                player.hurt(player.level().damageSources().magic(), 2.0f);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Check every tick for water contact
        return true;
    }

    /**
     * Check if the entity is in contact with water
     */
    private boolean isInWater(LivingEntity entity) {
        // Check if entity is in water fluid
        if (entity.isInWater()) {
            return true;
        }

        // Also check blocks around the entity for water
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    FluidState fluidState = level.getFluidState(checkPos);
                    if (fluidState.getType() == Fluids.WATER || fluidState.getType() == Fluids.FLOWING_WATER) {
                        return true;
                    }

                    // Also check for water blocks
                    BlockState blockState = level.getBlockState(checkPos);
                    if (blockState.is(Blocks.WATER)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Teleport the player away from water
     */
    private void teleportAwayFromWater(Player player, int amplifier) {
        Level level = player.level();
        RandomSource random = level.random;

        // Calculate teleport range: 8 + (2 * level)
        int range = 8 + (2 * amplifier);

        // Try to find a safe teleport location
        for (int attempts = 0; attempts < 16; attempts++) {
            double deltaX = (random.nextDouble() - 0.5) * 2.0 * range;
            double deltaY = (random.nextDouble() - 0.5) * 2.0 * 8; // Smaller Y range
            double deltaZ = (random.nextDouble() - 0.5) * 2.0 * range;

            Vec3 currentPos = player.position();
            Vec3 targetPos = currentPos.add(deltaX, deltaY, deltaZ);

            BlockPos targetBlockPos = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);

            // Check if the target location is safe (not in water, not in solid blocks)
            if (isSafeTeleportLocation(level, targetBlockPos)) {
                // Spawn particles at original location
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            currentPos.x, currentPos.y + 1.0, currentPos.z,
                            32, 0.5, 1.0, 0.5, 0.5);
                }

                // Teleport the player
                player.teleportTo(targetPos.x, targetPos.y, targetPos.z);

                // Play enderman teleport sound
                level.playSound(null, targetBlockPos, SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.PLAYERS, 1.0f, 1.0f);

                // Spawn particles at destination
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            targetPos.x, targetPos.y + 1.0, targetPos.z,
                            32, 0.5, 1.0, 0.5, 0.5);
                }

                return; // Successfully teleported
            }
        }

        // If no safe location found, teleport straight up
        Vec3 currentPos = player.position();
        BlockPos upPos = new BlockPos((int)currentPos.x, (int)currentPos.y + range, (int)currentPos.z);

        // Make sure the upward location is safe
        if (isSafeTeleportLocation(level, upPos)) {
            player.teleportTo(currentPos.x, currentPos.y + range, currentPos.z);
            level.playSound(null, upPos, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 1.0f, 1.0f);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        currentPos.x, currentPos.y + range + 1.0, currentPos.z,
                        32, 0.5, 1.0, 0.5, 0.5);
            }
        }
    }

    /**
     * Check if a location is safe for teleportation
     */
    private boolean isSafeTeleportLocation(Level level, BlockPos pos) {
        // Check if there's solid ground to stand on
        BlockState groundState = level.getBlockState(pos.below());
        if (!groundState.isSolid()) {
            return false;
        }

        // Check if the teleport location and the space above are not solid
        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(pos.above());

        if (feetState.isSolid() || headState.isSolid()) {
            return false;
        }

        // Check if not in water
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.getType() == Fluids.WATER || fluidState.getType() == Fluids.FLOWING_WATER) {
            return false;
        }

        // Check for water blocks
        if (feetState.is(Blocks.WATER) || headState.is(Blocks.WATER)) {
            return false;
        }

        return true;
    }

    /**
     * Teleport away from projectile impact
     * Called from the event handler when a projectile would hit
     */
    public static boolean teleportFromProjectile(Player player, int amplifier) {
        Level level = player.level();
        if (level.isClientSide) return false;

        RandomSource random = level.random;

        // Calculate teleport range: 8 + (2 * level)
        int range = 8 + (2 * amplifier);

        // Try to find a safe teleport location
        for (int attempts = 0; attempts < 16; attempts++) {
            double deltaX = (random.nextDouble() - 0.5) * 2.0 * range;
            double deltaY = (random.nextDouble() - 0.5) * 2.0 * 8; // Smaller Y range
            double deltaZ = (random.nextDouble() - 0.5) * 2.0 * range;

            Vec3 currentPos = player.position();
            Vec3 targetPos = currentPos.add(deltaX, deltaY, deltaZ);

            BlockPos targetBlockPos = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);

            // Check if the target location is safe
            if (isSafeTeleportLocationStatic(level, targetBlockPos)) {
                // Spawn particles at original location
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            currentPos.x, currentPos.y + 1.0, currentPos.z,
                            20, 0.3, 0.5, 0.3, 0.3);
                }

                // Teleport the player
                player.teleportTo(targetPos.x, targetPos.y, targetPos.z);

                // Play enderman teleport sound
                level.playSound(null, targetBlockPos, SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.PLAYERS, 1.0f, 1.0f);

                // Spawn particles at destination
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            targetPos.x, targetPos.y + 1.0, targetPos.z,
                            20, 0.3, 0.5, 0.3, 0.3);
                }

                return true; // Successfully teleported
            }
        }

        return false; // Failed to find safe location
    }

    /**
     * Static version of the safe teleport check for use in projectile teleportation
     */
    private static boolean isSafeTeleportLocationStatic(Level level, BlockPos pos) {
        // Check if there's solid ground to stand on
        BlockState groundState = level.getBlockState(pos.below());
        if (!groundState.isSolid()) {
            return false;
        }

        // Check if the teleport location and the space above are not solid
        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(pos.above());

        if (feetState.isSolid() || headState.isSolid()) {
            return false;
        }

        // Check if not in water
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.getType() == Fluids.WATER || fluidState.getType() == Fluids.FLOWING_WATER) {
            return false;
        }

        // Check for water blocks
        if (feetState.is(Blocks.WATER) || headState.is(Blocks.WATER)) {
            return false;
        }

        return true;
    }
}