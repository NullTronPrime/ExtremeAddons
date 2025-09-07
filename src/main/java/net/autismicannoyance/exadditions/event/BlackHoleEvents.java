package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.client.BlackHoleRenderer;
import net.autismicannoyance.exadditions.network.BlackHoleEffectPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultra-fast black hole with perfect visual sync - stays alive during destruction
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlackHoleEvents {

    private static final Map<Integer, BlackHole> ACTIVE_BLACK_HOLES = new ConcurrentHashMap<>();

    // Visual zone multipliers
    private static final float EVENT_HORIZON_MULTIPLIER = 1.0f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float ACCRETION_INNER_MULTIPLIER = 2.5f;
    private static final float ACCRETION_OUTER_MULTIPLIER = 12.0f;
    private static final float JET_LENGTH_MULTIPLIER = 20.0f;
    private static final float JET_WIDTH_MULTIPLIER = 0.8f;

    // Ultra-aggressive settings for instant destruction
    private static final int BLOCKS_PER_TICK = 500; // Much higher for instant clearing
    private static final float BASE_GROWTH_RATE = 0.05f; // Faster growth
    private static final float GROWTH_SCALING = 0.5f;
    private static final int BASE_LIFETIME_BONUS = 60;

    public static void addBlackHole(int id, Vec3 position, float size, float rotationSpeed, int lifetime) {
        ACTIVE_BLACK_HOLES.put(id, new BlackHole(id, position, size, rotationSpeed, lifetime));
    }

    public static void setBlackHoleLevel(int id, Level level) {
        BlackHole blackHole = ACTIVE_BLACK_HOLES.get(id);
        if (blackHole != null) {
            blackHole.level = level;
        }
    }

    public static void removeBlackHole(int id) {
        ACTIVE_BLACK_HOLES.remove(id);
    }

    public static void updateBlackHoleSize(int id, float newSize, int additionalLifetime) {
        BlackHole blackHole = ACTIVE_BLACK_HOLES.get(id);
        if (blackHole != null) {
            float oldSize = blackHole.size;
            blackHole.targetSize = Math.max(0.1f, Math.min(20.0f, newSize));
            blackHole.lifetime += additionalLifetime;
            blackHole.timeSinceLastFeed = 0;

            // Don't grow until current destruction is complete
            blackHole.pendingGrowth = true;
            blackHole.needsRecalculation = true;

            if (blackHole.level instanceof ServerLevel serverLevel) {
                // Only send visual update, don't actually change size yet
                BlackHoleEffectPacket packet = BlackHoleEffectPacket.createSizeUpdate(id, blackHole.targetSize);
                ModNetworking.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                blackHole.position.x, blackHole.position.y, blackHole.position.z,
                                128.0, serverLevel.dimension()
                        )), packet
                );
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ACTIVE_BLACK_HOLES.isEmpty()) return;

        Iterator<Map.Entry<Integer, BlackHole>> iterator = ACTIVE_BLACK_HOLES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, BlackHole> entry = iterator.next();
            BlackHole blackHole = entry.getValue();

            if (blackHole.level == null) {
                blackHole.level = event.getServer().overworld();
            }

            if (!(blackHole.level instanceof ServerLevel serverLevel)) {
                iterator.remove();
                continue;
            }

            blackHole.tick();

            // Only allow expiration if no blocks are pending destruction
            if (blackHole.isExpired() && blackHole.isDestructionComplete()) {
                BlackHoleEffectPacket removePacket = new BlackHoleEffectPacket(blackHole.id);
                ModNetworking.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                blackHole.position.x, blackHole.position.y, blackHole.position.z,
                                128.0, serverLevel.dimension()
                        )), removePacket
                );
                iterator.remove();
                continue;
            }

            // Ultra-fast processing with visual sync
            processBlackHoleUltraFast(blackHole, serverLevel);
        }
    }

    /**
     * Ultra-fast processing that keeps black hole alive during destruction
     */
    private static void processBlackHoleUltraFast(BlackHole blackHole, ServerLevel level) {
        boolean fedThisTick = false;

        // Recalculate destruction zones if needed
        if (blackHole.needsRecalculation || blackHole.destructionZones.isEmpty()) {
            precalculateDestructionZones(blackHole);
            blackHole.needsRecalculation = false;
        }

        // Process blocks in large batches for instant destruction
        fedThisTick |= processDestructionZonesMassive(blackHole, level);

        // Apply pending growth only after destruction is complete
        if (blackHole.pendingGrowth && blackHole.isDestructionComplete()) {
            blackHole.size = blackHole.targetSize;
            blackHole.pendingGrowth = false;
            blackHole.needsRecalculation = true; // Recalculate for new size
        }

        // Process entities
        if (blackHole.age % 2 == 0) {
            fedThisTick |= processEntitiesFast(blackHole, level);
        }

        handleGrowthDecay(blackHole, fedThisTick);
    }

    /**
     * Pre-calculate destruction zones with extended lifetime consideration
     */
    private static void precalculateDestructionZones(BlackHole blackHole) {
        blackHole.destructionZones.clear();

        // Use target size for calculation to prepare for growth
        float workingSize = blackHole.pendingGrowth ? blackHole.targetSize : blackHole.size;

        float eventHorizon = workingSize * EVENT_HORIZON_MULTIPLIER;
        float photonSphere = workingSize * PHOTON_SPHERE_MULTIPLIER;
        float accretionInner = workingSize * ACCRETION_INNER_MULTIPLIER;
        float accretionOuter = workingSize * ACCRETION_OUTER_MULTIPLIER;
        float jetLength = workingSize * JET_LENGTH_MULTIPLIER;
        float jetWidth = workingSize * JET_WIDTH_MULTIPLIER;

        BlockPos center = BlockPos.containing(blackHole.position);
        Vec3 centerPos = blackHole.position;

        int maxRadius = (int) Math.ceil(Math.max(accretionOuter, jetLength)) + 1;
        maxRadius = Math.min(maxRadius, 50); // Increased limit

        // Pre-calculate all positions efficiently
        for (int x = -maxRadius; x <= maxRadius; x++) {
            for (int y = -maxRadius; y <= maxRadius; y++) {
                for (int z = -maxRadius; z <= maxRadius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    Vec3 blockCenter = Vec3.atCenterOf(pos);

                    DestructionZone zone = getBlockDestructionZone(blockCenter, centerPos,
                            eventHorizon, photonSphere,
                            accretionInner, accretionOuter,
                            jetLength, jetWidth);

                    if (zone != DestructionZone.NONE) {
                        blackHole.destructionZones.computeIfAbsent(zone, k -> new ArrayList<>()).add(pos);
                    }
                }
            }
        }

        // Extend lifetime to ensure visual doesn't disappear during destruction
        int totalBlocks = blackHole.destructionZones.values().stream()
                .mapToInt(List::size).sum();
        int ticksNeeded = (totalBlocks / BLOCKS_PER_TICK) + 10; // Buffer time
        blackHole.lifetime = Math.max(blackHole.lifetime, blackHole.age + ticksNeeded);
    }

    /**
     * Massive batch processing - clear huge amounts per tick
     */
    private static boolean processDestructionZonesMassive(BlackHole blackHole, ServerLevel level) {
        boolean fed = false;
        int blocksProcessed = 0;

        // Process zones in priority order with massive batches
        DestructionZone[] zones = {
                DestructionZone.EVENT_HORIZON,
                DestructionZone.POLAR_JET,
                DestructionZone.PHOTON_SPHERE,
                DestructionZone.ACCRETION_INNER,
                DestructionZone.ACCRETION_OUTER
        };

        for (DestructionZone zone : zones) {
            List<BlockPos> positions = blackHole.destructionZones.get(zone);
            if (positions == null || positions.isEmpty()) continue;

            // Process massive batches for instant clearing
            Iterator<BlockPos> iterator = positions.iterator();
            while (iterator.hasNext() && blocksProcessed < BLOCKS_PER_TICK) {
                BlockPos pos = iterator.next();

                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)) {
                    iterator.remove();
                    continue;
                }

                // Instant destruction
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                consumeBlock(blackHole, state);
                iterator.remove();
                fed = true;
                blocksProcessed++;
            }

            if (blocksProcessed >= BLOCKS_PER_TICK) break;
        }

        return fed;
    }

    /**
     * Check if destruction is complete
     */
    private static boolean isDestructionComplete(BlackHole blackHole) {
        return blackHole.destructionZones.values().stream()
                .allMatch(List::isEmpty);
    }

    private static DestructionZone getBlockDestructionZone(Vec3 blockCenter, Vec3 blackHoleCenter,
                                                           float eventHorizon, float photonSphere,
                                                           float accretionInner, float accretionOuter,
                                                           float jetLength, float jetWidth) {

        Vec3 delta = blockCenter.subtract(blackHoleCenter);
        float distance = (float) delta.length();
        float horizontalDistSq = (float)(delta.x * delta.x + delta.z * delta.z);
        float verticalDist = Math.abs((float) delta.y);

        // Event Horizon
        if (distance <= eventHorizon) {
            return DestructionZone.EVENT_HORIZON;
        }

        // Polar Jets
        if (verticalDist > eventHorizon && verticalDist <= jetLength && horizontalDistSq <= jetWidth * jetWidth) {
            return DestructionZone.POLAR_JET;
        }

        // Photon Sphere
        if (distance > eventHorizon && distance <= photonSphere) {
            return DestructionZone.PHOTON_SPHERE;
        }

        // Accretion Disk
        float diskThickness = accretionOuter * 0.1f;
        if (Math.abs(delta.y) <= diskThickness) {
            float horizontalDist = (float) Math.sqrt(horizontalDistSq);

            if (horizontalDist > eventHorizon && horizontalDist <= accretionInner) {
                return DestructionZone.ACCRETION_INNER;
            }

            if (horizontalDist > accretionInner && horizontalDist <= accretionOuter) {
                return DestructionZone.ACCRETION_OUTER;
            }
        }

        return DestructionZone.NONE;
    }

    private static boolean processEntitiesFast(BlackHole blackHole, ServerLevel level) {
        boolean fed = false;

        float eventHorizon = blackHole.size * EVENT_HORIZON_MULTIPLIER;
        float maxRange = Math.max(blackHole.size * ACCRETION_OUTER_MULTIPLIER,
                blackHole.size * JET_LENGTH_MULTIPLIER);

        AABB searchArea = AABB.ofSize(blackHole.position, maxRange * 2, maxRange * 2, maxRange * 2);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, searchArea);

        for (Entity entity : entities) {
            if (entity instanceof Player player && player.isCreative()) continue;

            Vec3 entityCenter = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            float distance = (float) entityCenter.distanceTo(blackHole.position);

            if (distance <= eventHorizon) {
                entity.remove(Entity.RemovalReason.KILLED);
                fed = true;
            } else if (distance <= maxRange) {
                float damage = (15.0f * blackHole.size) / Math.max(distance, 1.0f);
                if (damage > 1.0f) {
                    entity.hurt(level.damageSources().genericKill(), damage);

                    Vec3 pull = blackHole.position.subtract(entityCenter).normalize();
                    float pullStrength = (0.15f * blackHole.size) / Math.max(distance * distance, 1.0f);
                    Vec3 newVel = entity.getDeltaMovement().add(pull.scale(pullStrength));
                    entity.setDeltaMovement(newVel);
                }
            }
        }

        return fed;
    }

    private static void consumeBlock(BlackHole blackHole, BlockState state) {
        float baseGrowth = BASE_GROWTH_RATE * (1.0f + blackHole.size * GROWTH_SCALING);

        float materialMultiplier = 1.0f;
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.IRON_BLOCK)) {
            materialMultiplier = 1.4f;
        } else if (state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.NETHERITE_BLOCK)) {
            materialMultiplier = 2.5f;
        }

        float growth = baseGrowth * materialMultiplier;
        // Add to target size instead of current size
        blackHole.targetSize += growth;
        blackHole.lifetime += (int)(BASE_LIFETIME_BONUS * materialMultiplier);
        blackHole.timeSinceLastFeed = 0;
        blackHole.targetSize = Math.min(blackHole.targetSize, 20.0f);
        blackHole.pendingGrowth = true;
    }

    private static void handleGrowthDecay(BlackHole blackHole, boolean fedThisTick) {
        if (fedThisTick) {
            if (blackHole.age % 10 == 0) {
                updateBlackHoleSize(blackHole.id, blackHole.targetSize, 0);
            }
        } else {
            blackHole.timeSinceLastFeed++;
            if (blackHole.timeSinceLastFeed > 80) {
                blackHole.targetSize = Math.max(0.1f, blackHole.targetSize - 0.02f);
                blackHole.lifetime = Math.max(0, blackHole.lifetime - 3);

                if (blackHole.age % 25 == 0) {
                    updateBlackHoleSize(blackHole.id, blackHole.targetSize, 0);
                }
            }
        }
    }

    private enum DestructionZone {
        NONE,
        EVENT_HORIZON,
        POLAR_JET,
        PHOTON_SPHERE,
        ACCRETION_INNER,
        ACCRETION_OUTER
    }

    private static class BlackHole {
        final int id;
        final Vec3 position;
        float size;
        float targetSize; // Size to grow to after destruction completes
        final float rotationSpeed;
        int lifetime;
        int age = 0;
        int timeSinceLastFeed = 0;
        Level level;

        boolean pendingGrowth = false; // Don't grow until destruction is done
        final Map<DestructionZone, List<BlockPos>> destructionZones = new HashMap<>();
        boolean needsRecalculation = true;

        BlackHole(int id, Vec3 position, float size, float rotationSpeed, int lifetime) {
            this.id = id;
            this.position = position;
            this.size = size;
            this.targetSize = size;
            this.rotationSpeed = rotationSpeed;
            this.lifetime = lifetime;
        }

        void tick() {
            if (lifetime >= 0) age++;
        }

        boolean isExpired() {
            return (lifetime >= 0 && age >= lifetime) || size <= 0.1f;
        }

        boolean isDestructionComplete() {
            return destructionZones.values().stream().allMatch(List::isEmpty);
        }
    }
}