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

@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlackHoleEvents {

    private static final Map<Integer, BlackHole> ACTIVE_BLACK_HOLES = new ConcurrentHashMap<>();

    // Visual zone multipliers - MUST match renderer exactly
    private static final float EVENT_HORIZON_MULTIPLIER = 1.0f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float ACCRETION_INNER_MULTIPLIER = 2.5f;
    private static final float ACCRETION_OUTER_MULTIPLIER = 12.0f;
    private static final float JET_LENGTH_MULTIPLIER = 20.0f;
    private static final float JET_WIDTH_MULTIPLIER = 0.8f;

    // Ultra-fast destruction settings
    private static final int BLOCKS_PER_TICK_NORMAL = 1000; // Much higher for instant clearing
    private static final int BLOCKS_PER_TICK_LARGE = 2500;  // Even more for large black holes
    private static final int MAX_SEARCH_RADIUS = 80;        // Reasonable limit to prevent lag

    // Logarithmic growth settings
    private static final float GROWTH_BASE = 1.15f;
    private static final float MIN_GROWTH = 0.002f;
    private static final float MAX_GROWTH = 0.15f;
    private static final int BASE_LIFETIME_BONUS = 200; // Generous lifetime

    public static void addBlackHole(int id, Vec3 position, float size, float rotationSpeed, int lifetime) {
        BlackHole blackHole = new BlackHole(id, position, size, rotationSpeed, Math.max(lifetime, 1200)); // Minimum 1 minute
        ACTIVE_BLACK_HOLES.put(id, blackHole);
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
            blackHole.queueGrowth(newSize, additionalLifetime);

            if (blackHole.level instanceof ServerLevel serverLevel) {
                // Always send visual update immediately
                BlackHoleEffectPacket packet = BlackHoleEffectPacket.createSizeUpdate(id, newSize);
                ModNetworking.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                blackHole.position.x, blackHole.position.y, blackHole.position.z,
                                150.0, serverLevel.dimension()
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

            // Only remove if truly finished and starving
            if (blackHole.shouldRemove()) {
                BlackHoleEffectPacket removePacket = new BlackHoleEffectPacket(blackHole.id);
                ModNetworking.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                blackHole.position.x, blackHole.position.y, blackHole.position.z,
                                150.0, serverLevel.dimension()
                        )), removePacket
                );
                iterator.remove();
                continue;
            }

            // Ultra-fast destruction processing
            processBlackHoleUltraFast(blackHole, serverLevel);
        }
    }

    /**
     * Ultra-optimized destruction that clears massive areas instantly while maintaining visual accuracy
     */
    private static void processBlackHoleUltraFast(BlackHole blackHole, ServerLevel level) {
        boolean fedThisTick = false;

        // Determine destruction rate based on black hole size
        int maxBlocksThisTick = blackHole.size > 5.0f ? BLOCKS_PER_TICK_LARGE : BLOCKS_PER_TICK_NORMAL;
        int blocksDestroyed = 0;

        // Calculate current destruction zones
        float currentSize = blackHole.getCurrentSize();
        float eventHorizon = currentSize * EVENT_HORIZON_MULTIPLIER;
        float photonSphere = currentSize * PHOTON_SPHERE_MULTIPLIER;
        float accretionInner = currentSize * ACCRETION_INNER_MULTIPLIER;
        float accretionOuter = currentSize * ACCRETION_OUTER_MULTIPLIER;
        float jetLength = currentSize * JET_LENGTH_MULTIPLIER;
        float jetWidth = currentSize * JET_WIDTH_MULTIPLIER;

        Vec3 center = blackHole.position;
        BlockPos centerPos = BlockPos.containing(center);
        int searchRadius = Math.min(MAX_SEARCH_RADIUS, (int)Math.ceil(Math.max(accretionOuter, jetLength)) + 1);

        // Process destruction in priority zones with massive batching
        DestructionZone[] priorityOrder = {
                DestructionZone.EVENT_HORIZON,
                DestructionZone.POLAR_JET,
                DestructionZone.PHOTON_SPHERE,
                DestructionZone.ACCRETION_INNER,
                DestructionZone.ACCRETION_OUTER
        };

        for (DestructionZone zone : priorityOrder) {
            if (blocksDestroyed >= maxBlocksThisTick) break;

            // Process this zone in large chunks
            List<BlockPos> zoneBlocks = findBlocksInZone(centerPos, center, searchRadius, zone,
                    eventHorizon, photonSphere, accretionInner, accretionOuter, jetLength, jetWidth);

            // Destroy blocks in massive batches
            Iterator<BlockPos> blockIterator = zoneBlocks.iterator();
            while (blockIterator.hasNext() && blocksDestroyed < maxBlocksThisTick) {
                BlockPos pos = blockIterator.next();

                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)) {
                    continue;
                }

                // Instant destruction
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); // Use flag 2 for better performance
                consumeBlockLogarithmic(blackHole, state);

                fedThisTick = true;
                blocksDestroyed++;
            }
        }

        // Apply growth immediately if no more blocks to destroy
        if (!fedThisTick && blackHole.hasPendingGrowth()) {
            blackHole.applyPendingGrowth();
        }

        // Process entities less frequently for performance
        if (blackHole.age % 3 == 0) {
            processEntitiesFast(blackHole, level, eventHorizon, accretionOuter, jetLength);
        }

        // Handle lifetime and decay
        handleLifetimeAndDecay(blackHole, fedThisTick, blocksDestroyed);
    }

    /**
     * Efficiently find all blocks in a specific destruction zone
     */
    private static List<BlockPos> findBlocksInZone(BlockPos center, Vec3 centerVec, int radius,
                                                   DestructionZone zone, float eventHorizon, float photonSphere,
                                                   float accretionInner, float accretionOuter, float jetLength, float jetWidth) {

        List<BlockPos> blocks = new ArrayList<>();

        // Optimized zone-specific searches
        switch (zone) {
            case EVENT_HORIZON -> {
                // Sphere search - most critical zone
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            if (x*x + y*y + z*z <= eventHorizon * eventHorizon) {
                                blocks.add(center.offset(x, y, z));
                            }
                        }
                    }
                }
            }
            case PHOTON_SPHERE -> {
                // Spherical shell
                float innerRadiusSq = eventHorizon * eventHorizon;
                float outerRadiusSq = photonSphere * photonSphere;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            float distSq = x*x + y*y + z*z;
                            if (distSq > innerRadiusSq && distSq <= outerRadiusSq) {
                                blocks.add(center.offset(x, y, z));
                            }
                        }
                    }
                }
            }
            case POLAR_JET -> {
                // Cylindrical regions above and below
                int jetRadiusInt = (int)Math.ceil(jetWidth * 3.0f); // Expanded jets
                int jetHeightInt = (int)Math.ceil(jetLength);
                for (int x = -jetRadiusInt; x <= jetRadiusInt; x++) {
                    for (int z = -jetRadiusInt; z <= jetRadiusInt; z++) {
                        if (x*x + z*z <= jetRadiusInt * jetRadiusInt) {
                            // Above jet
                            for (int y = (int)eventHorizon + 1; y <= jetHeightInt; y++) {
                                blocks.add(center.offset(x, y, z));
                            }
                            // Below jet
                            for (int y = -(int)eventHorizon - 1; y >= -jetHeightInt; y--) {
                                blocks.add(center.offset(x, y, z));
                            }
                        }
                    }
                }
            }
            case ACCRETION_INNER, ACCRETION_OUTER -> {
                // Disk regions
                float innerRadius = zone == DestructionZone.ACCRETION_INNER ? eventHorizon : accretionInner;
                float outerRadius = zone == DestructionZone.ACCRETION_INNER ? accretionInner : accretionOuter;
                int diskThickness = Math.max(1, (int)(outerRadius * 0.15f));

                int outerRadiusInt = (int)Math.ceil(outerRadius);
                for (int x = -outerRadiusInt; x <= outerRadiusInt; x++) {
                    for (int z = -outerRadiusInt; z <= outerRadiusInt; z++) {
                        float distSq = x*x + z*z;
                        float dist = (float)Math.sqrt(distSq);
                        if (dist >= innerRadius && dist <= outerRadius) {
                            for (int y = -diskThickness; y <= diskThickness; y++) {
                                blocks.add(center.offset(x, y, z));
                            }
                        }
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Fast entity processing with larger batches
     */
    private static void processEntitiesFast(BlackHole blackHole, ServerLevel level,
                                            float eventHorizon, float accretionOuter, float jetLength) {

        float maxRange = Math.max(accretionOuter, jetLength);
        AABB searchArea = AABB.ofSize(blackHole.position, maxRange * 2, maxRange * 2, maxRange * 2);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, searchArea);

        for (Entity entity : entities) {
            if (entity instanceof Player player && player.isCreative()) continue;

            Vec3 entityPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            float distance = (float) entityPos.distanceTo(blackHole.position);

            if (distance <= eventHorizon) {
                entity.remove(Entity.RemovalReason.KILLED);
            } else if (distance <= maxRange) {
                // Gravitational effects
                float damage = Math.min(25.0f, (25.0f * blackHole.size) / Math.max(distance, 1.0f));
                if (damage > 2.0f) {
                    entity.hurt(level.damageSources().genericKill(), damage);
                }

                // Strong gravitational pull
                Vec3 pullDirection = blackHole.position.subtract(entityPos).normalize();
                float pullStrength = (0.3f * blackHole.size) / Math.max(distance * distance, 1.0f);
                Vec3 newVelocity = entity.getDeltaMovement().add(pullDirection.scale(pullStrength));
                entity.setDeltaMovement(newVelocity);
            }
        }
    }

    /**
     * Logarithmic growth with proper material bonuses
     */
    private static void consumeBlockLogarithmic(BlackHole blackHole, BlockState state) {
        float currentSize = blackHole.getCurrentSize();

        // True logarithmic scaling - larger holes grow much slower
        float growthFactor = MAX_GROWTH / (float)(1.0f + Math.log(currentSize + 1.0f) * Math.log(GROWTH_BASE));
        growthFactor = Math.max(MIN_GROWTH, growthFactor);

        // Material multipliers
        float materialBonus = 1.0f;
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) {
            materialBonus = 1.1f;
        } else if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.OBSIDIAN)) {
            materialBonus = 1.4f;
        } else if (state.is(Blocks.DIAMOND_BLOCK)) {
            materialBonus = 2.0f;
        } else if (state.is(Blocks.NETHERITE_BLOCK)) {
            materialBonus = 3.0f;
        } else if (state.is(Blocks.BEDROCK)) {
            materialBonus = 10.0f; // Massive bonus for impossible blocks
        }

        float growth = growthFactor * materialBonus;
        int lifetimeBonus = (int)(BASE_LIFETIME_BONUS * materialBonus);

        blackHole.addPendingGrowth(growth, lifetimeBonus);
    }

    /**
     * Simplified lifetime management
     */
    private static void handleLifetimeAndDecay(BlackHole blackHole, boolean fedThisTick, int blocksDestroyed) {
        if (fedThisTick) {
            blackHole.timeSinceLastFeed = 0;
            blackHole.extendLifetime(50); // Extend life while feeding

            // Send periodic updates
            if (blackHole.age % 30 == 0) {
                updateBlackHoleSize(blackHole.id, blackHole.getPendingSize(), 200);
            }
        } else {
            blackHole.timeSinceLastFeed++;

            // Only decay after long starvation
            if (blackHole.timeSinceLastFeed > 300) { // 15 seconds
                float decayRate = MIN_GROWTH * 3.0f;
                blackHole.applyDecay(decayRate);

                if (blackHole.age % 60 == 0) {
                    updateBlackHoleSize(blackHole.id, blackHole.getCurrentSize(), 0);
                }
            }
        }
    }

    private enum DestructionZone {
        EVENT_HORIZON,
        POLAR_JET,
        PHOTON_SPHERE,
        ACCRETION_INNER,
        ACCRETION_OUTER
    }

    /**
     * Simplified BlackHole class focused on performance
     */
    private static class BlackHole {
        final int id;
        final Vec3 position;
        float size;
        private float pendingSize;
        private float pendingGrowth = 0.0f;
        final float rotationSpeed;
        int lifetime;
        int age = 0;
        int timeSinceLastFeed = 0;
        Level level;

        BlackHole(int id, Vec3 position, float size, float rotationSpeed, int lifetime) {
            this.id = id;
            this.position = position;
            this.size = size;
            this.pendingSize = size;
            this.rotationSpeed = rotationSpeed;
            this.lifetime = lifetime;
        }

        void tick() {
            age++;
        }

        boolean shouldRemove() {
            return size <= 0.05f || (lifetime > 0 && age > lifetime && timeSinceLastFeed > 200);
        }

        float getCurrentSize() {
            return size;
        }

        float getPendingSize() {
            return pendingSize;
        }

        boolean hasPendingGrowth() {
            return pendingGrowth > 0.001f || Math.abs(pendingSize - size) > 0.001f;
        }

        void queueGrowth(float newSize, int additionalLifetime) {
            this.pendingSize = Math.max(0.1f, Math.min(30.0f, newSize));
            this.lifetime += additionalLifetime;
            this.timeSinceLastFeed = 0;
        }

        void addPendingGrowth(float growth, int lifetimeBonus) {
            this.pendingGrowth += growth;
            this.lifetime += lifetimeBonus;
            this.timeSinceLastFeed = 0;
        }

        void applyPendingGrowth() {
            if (pendingGrowth > 0.001f) {
                size = Math.min(30.0f, size + pendingGrowth);
                pendingSize = size;
                pendingGrowth = 0.0f;
            } else if (Math.abs(pendingSize - size) > 0.001f) {
                size = pendingSize;
            }
        }

        void applyDecay(float decay) {
            size = Math.max(0.05f, size - decay);
            pendingSize = size;
        }

        void extendLifetime(int ticks) {
            if (lifetime > 0) {
                lifetime = Math.max(lifetime, age + ticks);
            }
        }
    }
}