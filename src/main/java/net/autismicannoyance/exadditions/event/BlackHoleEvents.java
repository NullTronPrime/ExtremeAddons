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

    // MEMORY-SAFE destruction settings - reduced to prevent OOM
    private static final int BLOCKS_PER_TICK_NORMAL = 2000;  // Reduced from 10k
    private static final int BLOCKS_PER_TICK_LARGE = 5000;   // Reduced from 25k
    private static final int BLOCKS_PER_TICK_MASSIVE = 8000; // Reduced from 50k
    private static final int MAX_SEARCH_RADIUS = 60;         // Reduced from 120
    private static final int MAX_ZONE_BLOCKS = 10000;        // Max blocks per zone to prevent OOM

    // Much harsher logarithmic growth settings
    private static final float GROWTH_BASE = 2.5f;
    private static final float MIN_GROWTH = 0.0005f;
    private static final float MAX_GROWTH = 0.25f;
    private static final int BASE_LIFETIME_BONUS = 150;

    // New size thresholds for different growth rates
    private static final float SMALL_BLACK_HOLE_THRESHOLD = 1.5f;
    private static final float MEDIUM_BLACK_HOLE_THRESHOLD = 4.0f;
    private static final float LARGE_BLACK_HOLE_THRESHOLD = 8.0f;

    // Damage zone settings
    private static final float EVENT_HORIZON_DAMAGE = 1000.0f;
    private static final float PHOTON_SPHERE_DAMAGE = 50.0f;
    private static final float ACCRETION_DISK_DAMAGE = 25.0f;
    private static final float POLAR_JET_DAMAGE = 75.0f;
    private static final float GRAVITATIONAL_DAMAGE = 10.0f;

    public static void addBlackHole(int id, Vec3 position, float size, float rotationSpeed, int lifetime) {
        float adjustedSize = Math.max(0.5f, Math.min(size, 1.0f));
        BlackHole blackHole = new BlackHole(id, position, adjustedSize, rotationSpeed, -1); // INFINITE LIFETIME ON SERVER
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
                BlackHoleEffectPacket packet = BlackHoleEffectPacket.createSizeUpdate(id, newSize);
                ModNetworking.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                blackHole.position.x, blackHole.position.y, blackHole.position.z,
                                200.0, serverLevel.dimension()
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

            if (blackHole.shouldRemove()) {
                BlackHoleEffectPacket removePacket = new BlackHoleEffectPacket(blackHole.id);
                ModNetworking.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                blackHole.position.x, blackHole.position.y, blackHole.position.z,
                                200.0, serverLevel.dimension()
                        )), removePacket
                );

                blackHole.markForRemoval();
                continue;
            }

            if (blackHole.isMarkedForRemoval()) {
                iterator.remove();
                continue;
            }

            // ALWAYS keep visual effect alive while black hole exists
            if (blackHole.age % 60 == 0) {
                refreshBlackHoleVisual(blackHole, serverLevel);
            }

            // Process destruction and physics - MEMORY-SAFE VERSION
            processBlackHoleMemorySafe(blackHole, serverLevel);

            if (!blackHole.isMarkedForRemoval()) {
                processEntitiesAccurate(blackHole, serverLevel);
            }
        }
    }

    private static void refreshBlackHoleVisual(BlackHole blackHole, ServerLevel serverLevel) {
        BlackHoleEffectPacket refreshPacket = new BlackHoleEffectPacket(
                blackHole.id,
                blackHole.position,
                blackHole.getCurrentSize(),
                blackHole.rotationSpeed,
                999999
        );

        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        blackHole.position.x, blackHole.position.y, blackHole.position.z,
                        200.0, serverLevel.dimension()
                )), refreshPacket
        );
    }

    /**
     * MEMORY-SAFE destruction processing - processes blocks in small chunks without pre-loading
     */
    private static void processBlackHoleMemorySafe(BlackHole blackHole, ServerLevel level) {
        boolean fedThisTick = false;
        float currentSize = blackHole.getCurrentSize();

        // Reduced destruction rates to prevent memory issues
        int maxBlocksThisTick;
        if (currentSize > LARGE_BLACK_HOLE_THRESHOLD) {
            maxBlocksThisTick = BLOCKS_PER_TICK_MASSIVE;
        } else if (currentSize > MEDIUM_BLACK_HOLE_THRESHOLD) {
            maxBlocksThisTick = BLOCKS_PER_TICK_LARGE;
        } else {
            maxBlocksThisTick = BLOCKS_PER_TICK_NORMAL;
        }

        int blocksDestroyed = 0;

        // Calculate current destruction zones
        float eventHorizon = currentSize * EVENT_HORIZON_MULTIPLIER;
        float photonSphere = currentSize * PHOTON_SPHERE_MULTIPLIER;
        float accretionInner = currentSize * ACCRETION_INNER_MULTIPLIER;
        float accretionOuter = currentSize * ACCRETION_OUTER_MULTIPLIER;
        float jetLength = currentSize * JET_LENGTH_MULTIPLIER;
        float jetWidth = currentSize * JET_WIDTH_MULTIPLIER;

        Vec3 center = blackHole.position;
        BlockPos centerPos = BlockPos.containing(center);
        int searchRadius = Math.min(MAX_SEARCH_RADIUS, (int)Math.ceil(Math.max(accretionOuter, jetLength)) + 2);

        // Process zones using STREAMING approach - no pre-loading of blocks
        DestructionZone[] priorityOrder = {
                DestructionZone.EVENT_HORIZON,
                DestructionZone.POLAR_JET,
                DestructionZone.PHOTON_SPHERE,
                DestructionZone.ACCRETION_INNER,
                DestructionZone.ACCRETION_OUTER
        };

        for (DestructionZone zone : priorityOrder) {
            if (blocksDestroyed >= maxBlocksThisTick) break;

            // STREAMING PROCESSING - process blocks as we find them, don't store in lists
            int zoneBlocksDestroyed = processZoneStreaming(blackHole, level, centerPos, center,
                    searchRadius, zone, eventHorizon, photonSphere, accretionInner,
                    accretionOuter, jetLength, jetWidth, maxBlocksThisTick - blocksDestroyed);

            if (zoneBlocksDestroyed > 0) {
                blocksDestroyed += zoneBlocksDestroyed;
                fedThisTick = true;
            }
        }

        blackHole.setActivelyFeeding(fedThisTick);

        if (!fedThisTick && blackHole.hasPendingGrowth()) {
            blackHole.applyPendingGrowth();
        }

        handleLifetimeAndDecay(blackHole, fedThisTick, blocksDestroyed);
    }

    /**
     * STREAMING zone processing - destroys blocks as we find them, no memory allocation
     */
    private static int processZoneStreaming(BlackHole blackHole, ServerLevel level, BlockPos center, Vec3 centerVec,
                                            int radius, DestructionZone zone, float eventHorizon, float photonSphere,
                                            float accretionInner, float accretionOuter, float jetLength, float jetWidth,
                                            int maxBlocks) {

        int blocksDestroyed = 0;

        // Pre-calculate squared distances for performance
        float eventHorizonSq = eventHorizon * eventHorizon;
        float photonSphereSq = photonSphere * photonSphere;

        // Process each zone with optimized loops and early termination
        switch (zone) {
            case EVENT_HORIZON -> {
                // Most critical zone - sphere search with immediate processing
                int eventRadius = Math.min(radius, (int)Math.ceil(eventHorizon) + 1);
                for (int x = -eventRadius; x <= eventRadius && blocksDestroyed < maxBlocks; x++) {
                    for (int y = -eventRadius; y <= eventRadius && blocksDestroyed < maxBlocks; y++) {
                        for (int z = -eventRadius; z <= eventRadius && blocksDestroyed < maxBlocks; z++) {
                            float distSq = x*x + y*y + z*z;
                            if (distSq <= eventHorizonSq) {
                                BlockPos pos = center.offset(x, y, z);
                                if (destroyBlockIfValid(blackHole, level, pos)) {
                                    blocksDestroyed++;
                                }
                            }
                        }
                    }
                }
            }

            case PHOTON_SPHERE -> {
                // Spherical shell processing
                int photonRadius = Math.min(radius, (int)Math.ceil(photonSphere) + 1);
                for (int x = -photonRadius; x <= photonRadius && blocksDestroyed < maxBlocks; x++) {
                    for (int y = -photonRadius; y <= photonRadius && blocksDestroyed < maxBlocks; y++) {
                        for (int z = -photonRadius; z <= photonRadius && blocksDestroyed < maxBlocks; z++) {
                            float distSq = x*x + y*y + z*z;
                            if (distSq > eventHorizonSq && distSq <= photonSphereSq) {
                                BlockPos pos = center.offset(x, y, z);
                                if (destroyBlockIfValid(blackHole, level, pos)) {
                                    blocksDestroyed++;
                                }
                            }
                        }
                    }
                }
            }

            case POLAR_JET -> {
                // Cylindrical regions - more conservative sizing
                int jetRadiusInt = Math.min(radius / 4, (int)Math.ceil(jetWidth * 2.0f)); // Reduced expansion
                int jetHeightInt = Math.min(radius, (int)Math.ceil(jetLength * 0.8f)); // Reduced height
                int jetRadiusSq = jetRadiusInt * jetRadiusInt;

                for (int x = -jetRadiusInt; x <= jetRadiusInt && blocksDestroyed < maxBlocks; x++) {
                    for (int z = -jetRadiusInt; z <= jetRadiusInt && blocksDestroyed < maxBlocks; z++) {
                        if (x*x + z*z <= jetRadiusSq) {
                            // Above jet
                            for (int y = (int)eventHorizon + 1; y <= jetHeightInt && blocksDestroyed < maxBlocks; y++) {
                                BlockPos pos = center.offset(x, y, z);
                                if (destroyBlockIfValid(blackHole, level, pos)) {
                                    blocksDestroyed++;
                                }
                            }
                            // Below jet
                            for (int y = -(int)eventHorizon - 1; y >= -jetHeightInt && blocksDestroyed < maxBlocks; y--) {
                                BlockPos pos = center.offset(x, y, z);
                                if (destroyBlockIfValid(blackHole, level, pos)) {
                                    blocksDestroyed++;
                                }
                            }
                        }
                    }
                }
            }

            case ACCRETION_INNER, ACCRETION_OUTER -> {
                // Disk regions with conservative sizing
                float innerRadius = zone == DestructionZone.ACCRETION_INNER ? eventHorizon : accretionInner;
                float outerRadius = zone == DestructionZone.ACCRETION_INNER ? accretionInner : accretionOuter;
                int diskThickness = Math.max(1, Math.min(5, (int)(outerRadius * 0.15f))); // Limited thickness

                int outerRadiusInt = Math.min(radius, (int)Math.ceil(outerRadius));
                for (int x = -outerRadiusInt; x <= outerRadiusInt && blocksDestroyed < maxBlocks; x++) {
                    for (int z = -outerRadiusInt; z <= outerRadiusInt && blocksDestroyed < maxBlocks; z++) {
                        float dist = (float)Math.sqrt(x*x + z*z);
                        if (dist >= innerRadius && dist <= outerRadius) {
                            for (int y = -diskThickness; y <= diskThickness && blocksDestroyed < maxBlocks; y++) {
                                BlockPos pos = center.offset(x, y, z);
                                if (destroyBlockIfValid(blackHole, level, pos)) {
                                    blocksDestroyed++;
                                }
                            }
                        }
                    }
                }
            }
        }

        return blocksDestroyed;
    }

    /**
     * Destroys a block if it's valid, returns true if destroyed
     */
    private static boolean destroyBlockIfValid(BlackHole blackHole, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)) {
            return false;
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        consumeBlockHarshLogarithmic(blackHole, state);
        return true;
    }

    /**
     * Accurate entity processing with proper damage zones and hitboxes
     */
    private static void processEntitiesAccurate(BlackHole blackHole, ServerLevel level) {
        if (blackHole.isMarkedForRemoval()) {
            return;
        }

        float currentSize = blackHole.getCurrentSize();

        float eventHorizon = currentSize * EVENT_HORIZON_MULTIPLIER;
        float photonSphere = currentSize * PHOTON_SPHERE_MULTIPLIER;
        float accretionInner = currentSize * ACCRETION_INNER_MULTIPLIER;
        float accretionOuter = currentSize * ACCRETION_OUTER_MULTIPLIER;
        float jetLength = currentSize * JET_LENGTH_MULTIPLIER;
        float jetWidth = currentSize * JET_WIDTH_MULTIPLIER;

        Vec3 blackHoleCenter = blackHole.position;

        float maxRange = Math.max(accretionOuter, jetLength) * 1.2f; // Reduced range
        AABB searchArea = AABB.ofSize(blackHoleCenter, maxRange * 2, maxRange * 2, maxRange * 2);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, searchArea);

        for (Entity entity : entities) {
            if (entity instanceof Player player && player.isCreative()) continue;

            Vec3 entityPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            Vec3 relativePos = entityPos.subtract(blackHoleCenter);

            float horizontalDist = (float)Math.sqrt(relativePos.x * relativePos.x + relativePos.z * relativePos.z);
            float totalDistance = (float) entityPos.distanceTo(blackHoleCenter);
            float verticalOffset = (float)relativePos.y;

            DamageZone zone = calculateDamageZone(totalDistance, horizontalDist, verticalOffset,
                    eventHorizon, photonSphere, accretionInner, accretionOuter, jetLength, jetWidth);

            // Apply zone-specific effects
            switch (zone) {
                case EVENT_HORIZON -> {
                    entity.remove(Entity.RemovalReason.KILLED);
                    continue;
                }

                case PHOTON_SPHERE -> {
                    float damage = EVENT_HORIZON_DAMAGE * (photonSphere - totalDistance) / (photonSphere - eventHorizon);
                    entity.hurt(level.damageSources().genericKill(), Math.min(damage, 100.0f));

                    Vec3 pullDirection = blackHoleCenter.subtract(entityPos).normalize();
                    float pullStrength = (2.0f * currentSize) / Math.max(totalDistance * totalDistance, 0.1f);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(pullDirection.scale(pullStrength)));
                }

                case POLAR_JET -> {
                    float jetDamage = POLAR_JET_DAMAGE * (currentSize / Math.max(totalDistance, 1.0f));
                    jetDamage *= (jetWidth - horizontalDist) / jetWidth;

                    entity.hurt(level.damageSources().genericKill(), Math.min(jetDamage, 75.0f));

                    Vec3 jetDirection = verticalOffset > 0 ? new Vec3(0, 1, 0) : new Vec3(0, -1, 0);
                    float pushStrength = jetDamage / 50.0f;
                    entity.setDeltaMovement(entity.getDeltaMovement().add(jetDirection.scale(pushStrength)));

                    entity.setSecondsOnFire((int)(jetDamage / 10.0f));
                }

                case ACCRETION_DISK_INNER -> {
                    float accretionDamage = ACCRETION_DISK_DAMAGE * (currentSize / Math.max(horizontalDist, 0.5f));
                    accretionDamage *= 1.5f;

                    entity.hurt(level.damageSources().genericKill(), Math.min(accretionDamage, 40.0f));

                    Vec3 orbitalDirection = new Vec3(-relativePos.z, 0, relativePos.x).normalize();
                    Vec3 pullDirection = blackHoleCenter.subtract(entityPos).normalize();

                    float orbitalSpeed = 0.3f * currentSize / Math.max(horizontalDist, 1.0f);
                    float pullStrength = 0.8f * currentSize / Math.max(totalDistance * totalDistance, 0.25f);

                    Vec3 combinedForce = orbitalDirection.scale(orbitalSpeed).add(pullDirection.scale(pullStrength));
                    entity.setDeltaMovement(entity.getDeltaMovement().add(combinedForce));

                    entity.setSecondsOnFire((int)(accretionDamage / 15.0f));
                }

                case ACCRETION_DISK_OUTER -> {
                    float accretionDamage = ACCRETION_DISK_DAMAGE * (currentSize / Math.max(horizontalDist, 1.0f)) * 0.7f;

                    entity.hurt(level.damageSources().genericKill(), Math.min(accretionDamage, 25.0f));

                    Vec3 orbitalDirection = new Vec3(-relativePos.z, 0, relativePos.x).normalize();
                    Vec3 pullDirection = blackHoleCenter.subtract(entityPos).normalize();

                    float orbitalSpeed = 0.15f * currentSize / Math.max(horizontalDist, 1.0f);
                    float pullStrength = 0.4f * currentSize / Math.max(totalDistance * totalDistance, 0.5f);

                    Vec3 combinedForce = orbitalDirection.scale(orbitalSpeed).add(pullDirection.scale(pullStrength));
                    entity.setDeltaMovement(entity.getDeltaMovement().add(combinedForce));
                }

                case GRAVITATIONAL_INFLUENCE -> {
                    float gravDamage = GRAVITATIONAL_DAMAGE * (currentSize / Math.max(totalDistance, 2.0f));

                    if (gravDamage > 0.5f) {
                        entity.hurt(level.damageSources().genericKill(), Math.min(gravDamage, 15.0f));
                    }

                    Vec3 pullDirection = blackHoleCenter.subtract(entityPos).normalize();
                    float pullStrength = 0.1f * currentSize / Math.max(totalDistance * totalDistance, 1.0f);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(pullDirection.scale(pullStrength)));
                }
            }
        }
    }

    private static DamageZone calculateDamageZone(float totalDistance, float horizontalDistance, float verticalOffset,
                                                  float eventHorizon, float photonSphere,
                                                  float accretionInner, float accretionOuter,
                                                  float jetLength, float jetWidth) {

        if (totalDistance <= eventHorizon) {
            return DamageZone.EVENT_HORIZON;
        }

        if (totalDistance <= photonSphere) {
            return DamageZone.PHOTON_SPHERE;
        }

        float absVerticalOffset = Math.abs(verticalOffset);
        boolean inJetHeight = absVerticalOffset > eventHorizon && absVerticalOffset <= jetLength;
        boolean inJetRadius = horizontalDistance <= jetWidth * 2.0f;

        if (inJetHeight && inJetRadius) {
            return DamageZone.POLAR_JET;
        }

        float diskThickness = Math.max(2.0f, accretionOuter * 0.15f);
        boolean inDiskHeight = absVerticalOffset <= diskThickness;

        if (inDiskHeight) {
            if (horizontalDistance <= accretionInner) {
                return DamageZone.ACCRETION_DISK_INNER;
            } else if (horizontalDistance <= accretionOuter) {
                return DamageZone.ACCRETION_DISK_OUTER;
            }
        }

        float maxInfluenceRange = Math.max(accretionOuter, jetLength) * 1.2f;
        if (totalDistance <= maxInfluenceRange) {
            return DamageZone.GRAVITATIONAL_INFLUENCE;
        }

        return DamageZone.NONE;
    }

    private static void consumeBlockHarshLogarithmic(BlackHole blackHole, BlockState state) {
        float currentSize = blackHole.getCurrentSize();

        float growthFactor;

        if (currentSize < SMALL_BLACK_HOLE_THRESHOLD) {
            growthFactor = MAX_GROWTH * (1.5f - (currentSize / SMALL_BLACK_HOLE_THRESHOLD) * 0.8f);
        } else if (currentSize < MEDIUM_BLACK_HOLE_THRESHOLD) {
            float sizeRatio = (currentSize - SMALL_BLACK_HOLE_THRESHOLD) / (MEDIUM_BLACK_HOLE_THRESHOLD - SMALL_BLACK_HOLE_THRESHOLD);
            growthFactor = MAX_GROWTH * 0.7f * (1.0f - sizeRatio * 0.6f);
        } else if (currentSize < LARGE_BLACK_HOLE_THRESHOLD) {
            float sizeRatio = (currentSize - MEDIUM_BLACK_HOLE_THRESHOLD) / (LARGE_BLACK_HOLE_THRESHOLD - MEDIUM_BLACK_HOLE_THRESHOLD);
            growthFactor = MAX_GROWTH * 0.3f * (1.0f - sizeRatio * 0.7f);
        } else {
            float excessSize = currentSize - LARGE_BLACK_HOLE_THRESHOLD;
            growthFactor = MIN_GROWTH / (1.0f + (float)Math.pow(GROWTH_BASE, excessSize * 0.5f));
        }

        growthFactor = Math.max(MIN_GROWTH, growthFactor);

        float materialBonus = 1.0f;
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) {
            materialBonus = 1.2f;
        } else if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.OBSIDIAN)) {
            materialBonus = 1.8f;
        } else if (state.is(Blocks.DIAMOND_BLOCK)) {
            materialBonus = 3.0f;
        } else if (state.is(Blocks.NETHERITE_BLOCK)) {
            materialBonus = 5.0f;
        } else if (state.is(Blocks.BEDROCK)) {
            materialBonus = 15.0f;
        } else if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)) {
            materialBonus = 0.8f;
        }

        float growth = growthFactor * materialBonus;
        int lifetimeBonus = (int)(BASE_LIFETIME_BONUS * Math.min(materialBonus, 2.0f));

        blackHole.addPendingGrowth(growth, lifetimeBonus);
    }

    private static void handleLifetimeAndDecay(BlackHole blackHole, boolean fedThisTick, int blocksDestroyed) {
        if (fedThisTick) {
            blackHole.timeSinceLastFeed = 0;
        } else {
            blackHole.timeSinceLastFeed++;

            if (blackHole.timeSinceLastFeed > 200) {
                float decayRate = MIN_GROWTH * 5.0f * (1.0f + blackHole.timeSinceLastFeed / 200.0f);
                blackHole.applyDecay(decayRate);
            }
        }
    }

    private enum DamageZone {
        NONE,
        GRAVITATIONAL_INFLUENCE,
        ACCRETION_DISK_OUTER,
        ACCRETION_DISK_INNER,
        POLAR_JET,
        PHOTON_SPHERE,
        EVENT_HORIZON
    }

    private enum DestructionZone {
        EVENT_HORIZON,
        POLAR_JET,
        PHOTON_SPHERE,
        ACCRETION_INNER,
        ACCRETION_OUTER
    }

    /**
     * Enhanced BlackHole class with proper removal state management
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
        private boolean activelyFeeding = false;
        private boolean markedForRemoval = false;
        private int removalTimer = 0;
        Level level;

        BlackHole(int id, Vec3 position, float size, float rotationSpeed, int lifetime) {
            this.id = id;
            this.position = position;
            this.size = Math.max(0.5f, Math.min(size, 1.0f));
            this.pendingSize = this.size;
            this.rotationSpeed = rotationSpeed;
            this.lifetime = lifetime; // -1 = infinite lifetime on server
        }

        void tick() {
            age++;

            if (markedForRemoval) {
                removalTimer++;
            }
        }

        /**
         * Much more conservative removal - only when truly starving AND tiny
         */
        boolean shouldRemove() {
            boolean tooSmall = size <= 0.1f;
            boolean longStarved = timeSinceLastFeed > 1200; // 60 seconds without food
            boolean notActive = !activelyFeeding && !hasPendingGrowth();

            return !markedForRemoval && tooSmall && longStarved && notActive;
        }

        void markForRemoval() {
            this.markedForRemoval = true;
            this.removalTimer = 0;
        }

        boolean isMarkedForRemoval() {
            return markedForRemoval && removalTimer > 5;
        }

        float getCurrentSize() {
            return size;
        }

        float getPendingSize() {
            return pendingSize;
        }

        boolean hasPendingGrowth() {
            return pendingGrowth > 0.0005f || Math.abs(pendingSize - size) > 0.0005f;
        }

        boolean isActivelyFeeding() {
            return activelyFeeding;
        }

        void setActivelyFeeding(boolean feeding) {
            this.activelyFeeding = feeding;
        }

        void queueGrowth(float newSize, int additionalLifetime) {
            this.pendingSize = Math.max(0.1f, Math.min(40.0f, newSize));
            this.timeSinceLastFeed = 0;
            this.activelyFeeding = true;

            if (this.markedForRemoval) {
                this.markedForRemoval = false;
                this.removalTimer = 0;
            }
        }

        void addPendingGrowth(float growth, int lifetimeBonus) {
            this.pendingGrowth += growth;
            this.timeSinceLastFeed = 0;
            this.activelyFeeding = true;

            if (this.markedForRemoval) {
                this.markedForRemoval = false;
                this.removalTimer = 0;
            }
        }

        void applyPendingGrowth() {
            if (pendingGrowth > 0.0005f) {
                size = Math.min(40.0f, size + pendingGrowth);
                pendingSize = size;
                pendingGrowth = 0.0f;
            } else if (Math.abs(pendingSize - size) > 0.0005f) {
                size = pendingSize;
            }
        }

        void applyDecay(float decay) {
            size = Math.max(0.1f, size - decay);
            pendingSize = size;
            this.activelyFeeding = false;
        }

        void extendLifetime(int ticks) {
            if (lifetime > 0) {
                lifetime = Math.max(lifetime, age + ticks);
            }
        }
    }
}