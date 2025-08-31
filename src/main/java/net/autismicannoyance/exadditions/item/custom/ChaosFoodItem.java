package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ArrayList;

public class ChaosFoodItem extends Item {
    private static final int PLAYER_TELEPORT_COUNT = 81;
    private static final int TELEPORT_DELAY = 1; // ticks between teleports
    private static final int COOLDOWN_TICKS = 100; // 5 seconds (20 ticks per second)
    private static final double TELEPORT_RADIUS = 32.0; // Double the normal chorus fruit radius

    // Entity teleport counts by distance
    private static final int TELEPORTS_3_BLOCKS = 40;
    private static final int TELEPORTS_5_BLOCKS = 20;
    private static final int TELEPORTS_10_BLOCKS = 5;

    // Static list to track active teleport sequences
    private static final List<TeleportSequence> activeTeleportSequences = new ArrayList<>();

    public ChaosFoodItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(ChatFormatting.DARK_PURPLE + "Chaos Fruit").withStyle(ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal(ChatFormatting.GOLD + "Effects when consumed:"));
        tooltip.add(Component.literal(ChatFormatting.YELLOW + "• You: 81 teleports (32 block radius)"));
        tooltip.add(Component.literal(ChatFormatting.RED + "• 3 blocks: 40 teleports"));
        tooltip.add(Component.literal(ChatFormatting.GOLD + "• 5 blocks: 20 teleports"));
        tooltip.add(Component.literal(ChatFormatting.LIGHT_PURPLE + "• 10 blocks: 5 teleports"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal(ChatFormatting.GRAY + "5 second cooldown after use"));
        tooltip.add(Component.literal(ChatFormatting.DARK_GRAY + "Handle with extreme caution..."));

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!level.isClientSide && livingEntity instanceof Player player) {
            // Check if player is on cooldown
            if (player.getCooldowns().isOnCooldown(this)) {
                player.sendSystemMessage(Component.literal("Chaos Fruit is on cooldown!"));
                return stack;
            }

            // Apply cooldown
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

            // Show effect rings before chaos begins
            showEffectRings((ServerLevel) level, player.position());

            // Start teleporting nearby entities first
            teleportNearbyEntities(player, (ServerLevel) level);

            // Add player teleport sequence (starts after 0.5 second delay)
            activeTeleportSequences.add(new TeleportSequence(player, PLAYER_TELEPORT_COUNT, true, 10, TELEPORT_DELAY));

            // Send warning message
            player.sendSystemMessage(Component.literal(ChatFormatting.DARK_RED + "CHAOS UNLEASHED!"));

            // Register the tick handler if not already registered
            if (!isTickHandlerRegistered) {
                MinecraftForge.EVENT_BUS.register(ChaosTickHandler.class);
                isTickHandlerRegistered = true;
            }
        }

        return super.finishUsingItem(stack, level, livingEntity);
    }

    private void showEffectRings(ServerLevel level, Vec3 center) {
        // Ring 1: 3 block radius (intense red particles)
        createParticleRing(level, center, 3.0, ParticleTypes.FLAME, 80, 2.0);

        // Ring 2: 5 block radius (orange/lava particles)
        createParticleRing(level, center, 5.0, ParticleTypes.LAVA, 100, 1.5);

        // Ring 3: 10 block radius (purple/witch particles)
        createParticleRing(level, center, 10.0, ParticleTypes.WITCH, 140, 1.0);

        // Additional dramatic center explosion
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);

        // Play dramatic sounds
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7F, 2.0F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.5F, 0.8F);
    }

    private void createParticleRing(ServerLevel level, Vec3 center, double radius,
                                    net.minecraft.core.particles.ParticleOptions particle,
                                    int particleCount, double heightMultiplier) {
        for (int i = 0; i < particleCount; i++) {
            double angle = (2 * Math.PI * i) / particleCount;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = center.y + 0.5;

            // Create particles at different heights for a more dramatic effect
            int heightLayers = (int) (3 * heightMultiplier);
            for (int h = 0; h < heightLayers; h++) {
                double particleY = y + h * 0.7 * heightMultiplier;
                level.sendParticles(particle, x, particleY, z, 2, 0.15, 0.15, 0.15, 0.05);
            }

            // Add some scattered particles for extra effect
            if (i % 5 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, x, y + 1, z, 5, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    private void teleportNearbyEntities(Player player, ServerLevel level) {
        Vec3 playerPos = player.position();

        // Get entities in 10 block radius
        AABB searchArea = new AABB(playerPos.subtract(10, 10, 10), playerPos.add(10, 10, 10));
        List<Entity> nearbyEntities = level.getEntities(player, searchArea, entity ->
                entity instanceof LivingEntity && entity != player);

        int entitiesAffected = 0;
        for (Entity entity : nearbyEntities) {
            double distance = entity.distanceTo(player);
            int teleportCount = 0;

            // Determine teleport count based on distance
            if (distance <= 3.0) {
                teleportCount = TELEPORTS_3_BLOCKS;
            } else if (distance <= 5.0) {
                teleportCount = TELEPORTS_5_BLOCKS;
            } else if (distance <= 10.0) {
                teleportCount = TELEPORTS_10_BLOCKS;
            }

            if (teleportCount > 0) {
                // Calculate timing for proper durations:
                // 40 teleports in 2 seconds = every 1 tick (40 ticks total)
                // 20 teleports in 1 second = every 1 tick (20 ticks total)
                // 5 teleports in 0.25 seconds = every 1 tick (5 ticks total)
                activeTeleportSequences.add(new TeleportSequence(entity, teleportCount, false, 0, 1));
                entitiesAffected++;
            }
        }

        if (entitiesAffected > 0) {
            player.sendSystemMessage(Component.literal(ChatFormatting.YELLOW + "Chaos affects " + entitiesAffected + " nearby entities!"));
        }
    }

    private static void performChorusTeleportForPlayer(Player player, ServerLevel level) {
        RandomSource random = level.random;
        double originalX = player.getX();
        double originalY = player.getY();
        double originalZ = player.getZ();

        // Try to find a valid teleport location with double radius
        for (int attempt = 0; attempt < 20; ++attempt) {
            double targetX = player.getX() + (random.nextDouble() - 0.5) * TELEPORT_RADIUS;
            double targetY = Mth.clamp(
                    player.getY() + (random.nextInt(32) - 16),
                    level.getMinBuildHeight(),
                    level.getMinBuildHeight() + level.getLogicalHeight() - 1
            );
            double targetZ = player.getZ() + (random.nextDouble() - 0.5) * TELEPORT_RADIUS;

            // Try to teleport to this position
            if (teleportToPosition(player, level, targetX, targetY, targetZ)) {
                // Success! Play effects
                playTeleportEffects(level, originalX, originalY, originalZ, player.getX(), player.getY(), player.getZ());
                return;
            }
        }

        // If all attempts failed, just play the sound at original position
        level.playSound(null, originalX, originalY, originalZ, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void performChorusTeleportForEntity(Entity entity, ServerLevel level) {
        // For entities, always try to get them as far from players as possible
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class,
                new AABB(entity.position().subtract(50, 50, 50), entity.position().add(50, 50, 50)));

        if (nearbyPlayers.isEmpty()) {
            // No players nearby, use random teleportation
            performRandomTeleport(entity, level, 16.0);
            return;
        }

        // Find the position that maximizes distance from all players
        BlockPos bestPosition = null;
        double bestDistance = 0;

        // Search in a larger area around the entity
        BlockPos entityPos = entity.blockPosition();
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                for (int y = -8; y <= 8; y++) {
                    BlockPos candidate = entityPos.offset(x, y, z);

                    if (isSafePosition(level, candidate)) {
                        // Calculate minimum distance to any player
                        double minDistanceToPlayers = Double.MAX_VALUE;
                        for (Player player : nearbyPlayers) {
                            double distanceToPlayer = candidate.distSqr(player.blockPosition());
                            minDistanceToPlayers = Math.min(minDistanceToPlayers, distanceToPlayer);
                        }

                        // If this position is farther from players than our current best
                        if (minDistanceToPlayers > bestDistance) {
                            bestDistance = minDistanceToPlayers;
                            bestPosition = candidate;
                        }
                    }
                }
            }
        }

        if (bestPosition != null) {
            double originalX = entity.getX();
            double originalY = entity.getY();
            double originalZ = entity.getZ();

            entity.teleportTo(bestPosition.getX() + 0.5, bestPosition.getY(), bestPosition.getZ() + 0.5);
            if (entity instanceof LivingEntity living) {
                living.resetFallDistance();
            }

            playTeleportEffects(level, originalX, originalY, originalZ, entity.getX(), entity.getY(), entity.getZ());
        } else {
            // Fallback to random if no safe position found
            performRandomTeleport(entity, level, 16.0);
        }
    }

    private static void performRandomTeleport(Entity entity, ServerLevel level, double radius) {
        RandomSource random = level.random;
        double originalX = entity.getX();
        double originalY = entity.getY();
        double originalZ = entity.getZ();

        // Try to find a valid teleport location
        for (int attempt = 0; attempt < 16; ++attempt) {
            double targetX = entity.getX() + (random.nextDouble() - 0.5) * radius;
            double targetY = Mth.clamp(
                    entity.getY() + (random.nextInt(16) - 8),
                    level.getMinBuildHeight(),
                    level.getMinBuildHeight() + level.getLogicalHeight() - 1
            );
            double targetZ = entity.getZ() + (random.nextDouble() - 0.5) * radius;

            if (teleportEntityToPosition(entity, level, targetX, targetY, targetZ)) {
                playTeleportEffects(level, originalX, originalY, originalZ, entity.getX(), entity.getY(), entity.getZ());
                return;
            }
        }

        // If all attempts failed, just play the sound at original position
        level.playSound(null, originalX, originalY, originalZ, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.NEUTRAL, 0.5F, 1.0F);
    }

    private static boolean teleportToPosition(Player player, ServerLevel level, double x, double y, double z) {
        BlockPos targetPos = BlockPos.containing(x, y, z);

        if (level.hasChunkAt(targetPos)) {
            boolean canTeleport = false;

            for (int yOffset = 0; yOffset < 8; ++yOffset) {
                BlockPos checkPos = targetPos.offset(0, yOffset, 0);

                if (isSafePosition(level, checkPos)) {
                    y = checkPos.getY();
                    canTeleport = true;
                    break;
                }

                if (yOffset > 0) {
                    checkPos = targetPos.offset(0, -yOffset, 0);
                    if (isSafePosition(level, checkPos)) {
                        y = checkPos.getY();
                        canTeleport = true;
                        break;
                    }
                }
            }

            if (canTeleport) {
                player.teleportTo(x, y, z);
                player.resetFallDistance();
                return true;
            }
        }

        return false;
    }

    private static boolean teleportEntityToPosition(Entity entity, ServerLevel level, double x, double y, double z) {
        BlockPos targetPos = BlockPos.containing(x, y, z);

        if (level.hasChunkAt(targetPos)) {
            boolean canTeleport = false;

            for (int yOffset = 0; yOffset < 8; ++yOffset) {
                BlockPos checkPos = targetPos.offset(0, yOffset, 0);

                if (isSafePosition(level, checkPos)) {
                    y = checkPos.getY();
                    canTeleport = true;
                    break;
                }

                if (yOffset > 0) {
                    checkPos = targetPos.offset(0, -yOffset, 0);
                    if (isSafePosition(level, checkPos)) {
                        y = checkPos.getY();
                        canTeleport = true;
                        break;
                    }
                }
            }

            if (canTeleport) {
                entity.teleportTo(x, y, z);
                if (entity instanceof LivingEntity living) {
                    living.resetFallDistance();
                }
                return true;
            }
        }

        return false;
    }

    private static boolean isSafePosition(ServerLevel level, BlockPos pos) {
        BlockState blockBelow = level.getBlockState(pos.below());
        BlockState blockAt = level.getBlockState(pos);
        BlockState blockAbove = level.getBlockState(pos.above());

        return !blockBelow.isAir() &&
                blockBelow.isSolidRender(level, pos.below()) &&
                !blockAt.isSolidRender(level, pos) &&
                !blockAbove.isSolidRender(level, pos.above());
    }

    private static void playTeleportEffects(ServerLevel level, double fromX, double fromY, double fromZ,
                                            double toX, double toY, double toZ) {
        level.sendParticles(ParticleTypes.PORTAL, fromX, fromY + 1.0, fromZ, 32, 0.0, 0.0, 0.0, 1.0);
        level.sendParticles(ParticleTypes.PORTAL, toX, toY + 1.0, toZ, 32, 0.0, 0.0, 0.0, 1.0);
        level.playSound(null, toX, toY, toZ, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // Static tick handler class
    private static boolean isTickHandlerRegistered = false;

    public static class ChaosTickHandler {
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                List<TeleportSequence> toRemove = new ArrayList<>();

                for (TeleportSequence sequence : activeTeleportSequences) {
                    sequence.ticksWaited++;

                    if (sequence.ticksWaited >= sequence.delayTicks) {
                        sequence.ticksWaited = 0;

                        if (sequence.entity.isAlive() && !sequence.entity.isRemoved()) {
                            // Perform teleportation
                            if (sequence.entity instanceof Player player && sequence.isPlayer) {
                                performChorusTeleportForPlayer(player, (ServerLevel) sequence.entity.level());
                            } else {
                                performChorusTeleportForEntity(sequence.entity, (ServerLevel) sequence.entity.level());
                            }

                            sequence.currentTeleport++;

                            // Check if sequence is complete
                            if (sequence.currentTeleport >= sequence.totalTeleports) {
                                toRemove.add(sequence);

                                // Play completion effects for player
                                if (sequence.isPlayer && sequence.entity instanceof Player player) {
                                    ServerLevel level = (ServerLevel) player.level();
                                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(),
                                            50, 1.0, 1.0, 1.0, 0.1);
                                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 2.0F);
                                    player.sendSystemMessage(Component.literal(ChatFormatting.GOLD + "Chaos sequence complete!"));
                                }
                            } else {
                                // Reset delay for next teleport
                                sequence.delayTicks = sequence.ticksBetweenTeleports;
                            }
                        } else {
                            toRemove.add(sequence);
                        }
                    }
                }

                activeTeleportSequences.removeAll(toRemove);

                // Unregister if no active sequences
                if (activeTeleportSequences.isEmpty() && isTickHandlerRegistered) {
                    MinecraftForge.EVENT_BUS.unregister(ChaosTickHandler.class);
                    isTickHandlerRegistered = false;
                }
            }
        }
    }

    // Inner class to track teleport sequences
    private static class TeleportSequence {
        Entity entity;
        int totalTeleports;
        int currentTeleport;
        boolean isPlayer;
        int ticksWaited;
        int delayTicks;
        int ticksBetweenTeleports;

        TeleportSequence(Entity entity, int totalTeleports, boolean isPlayer, int initialDelayTicks, int ticksBetweenTeleports) {
            this.entity = entity;
            this.totalTeleports = totalTeleports;
            this.currentTeleport = 0;
            this.isPlayer = isPlayer;
            this.ticksWaited = 0;
            this.delayTicks = initialDelayTicks;
            this.ticksBetweenTeleports = ticksBetweenTeleports;
        }
    }
}