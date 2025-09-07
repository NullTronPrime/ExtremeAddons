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
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Aggressive black hole physics that destroys everything within visual boundaries
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlackHoleEvents {

    private static final Map<Integer, BlackHole> ACTIVE_BLACK_HOLES = new ConcurrentHashMap<>();

    // Visual zone multipliers - EXACTLY matching renderer
    private static final float EVENT_HORIZON_MULTIPLIER = 1.0f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float ACCRETION_INNER_MULTIPLIER = 2.5f;
    private static final float ACCRETION_OUTER_MULTIPLIER = 12.0f;
    private static final float JET_LENGTH_MULTIPLIER = 20.0f;
    private static final float JET_WIDTH_MULTIPLIER = 0.8f;

    // Aggressive block destruction limits
    private static final int MAX_BLOCKS_PER_TICK_PER_HOLE = 15;  // More aggressive
    private static final int MAX_BLOCKS_QUEUED_PER_HOLE = 200;   // Larger queue
    private static final int BLOCK_SCAN_INTERVAL = 3;            // Scan more frequently
    private static final int ENTITY_PROCESS_INTERVAL = 1;        // Process every tick

    // Moderate growth rates
    private static final float BASE_GROWTH_RATE = 0.02f;         // Moderate growth
    private static final float GROWTH_SCALING = 0.8f;            // Scales with size
    private static final int BASE_LIFETIME_BONUS = 40;           // Good lifetime bonus
    private static final int DECAY_TIMER = 80;                   // Moderate decay delay
    private static final float DECAY_RATE = 0.01f;               // Moderate decay

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
            blackHole.size = Math.max(0.1f, Math.min(20.0f, newSize));
            blackHole.lifetime += additionalLifetime;
            blackHole.timeSinceLastFeed = 0;

            if (blackHole.level instanceof ServerLevel serverLevel) {
                BlackHoleEffectPacket packet = BlackHoleEffectPacket.createSizeUpdate(id, blackHole.size);
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

            if (blackHole.isExpired()) {
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

            processBlackHoleAggressive(blackHole, serverLevel);
        }
    }

    private static void processBlackHoleAggressive(BlackHole blackHole, ServerLevel level) {
        // Calculate exact visual zones
        float eventHorizon = blackHole.size * EVENT_HORIZON_MULTIPLIER;
        float photonSphere = blackHole.size * PHOTON_SPHERE_MULTIPLIER;
        float accretionInner = blackHole.size * ACCRETION_INNER_MULTIPLIER;
        float accretionOuter = blackHole.size * ACCRETION_OUTER_MULTIPLIER;
        float jetLength = blackHole.size * JET_LENGTH_MULTIPLIER;
        float jetWidth = blackHole.size * JET_WIDTH_MULTIPLIER;

        boolean fedThisTick = false;

        // Process entities every tick
        if (blackHole.age % ENTITY_PROCESS_INTERVAL == 0) {
            fedThisTick |= processEntities(blackHole, level, eventHorizon, photonSphere,
                    accretionInner, accretionOuter, jetLength, jetWidth);
        }

        // Aggressive block scanning
        if (blackHole.age % BLOCK_SCAN_INTERVAL == 0) {
            scanForBlocksAggressive(blackHole, level, eventHorizon, photonSphere, accretionInner, accretionOuter, jetLength, jetWidth);
        }

        // Process more blocks per tick
        fedThisTick |= processQueuedBlocksAggressive(blackHole, level);

        // Growth/decay handling
        handleGrowthDecay(blackHole, fedThisTick);
    }

    private static boolean processEntities(BlackHole blackHole, ServerLevel level,
                                           float eventHorizon, float photonSphere,
                                           float accretionInner, float accretionOuter,
                                           float jetLength, float jetWidth) {
        boolean fed = false;

        // Search the full visual area
        float searchRadius = Math.max(accretionOuter, jetLength);
        AABB searchArea = AABB.ofSize(blackHole.position,
                searchRadius * 2.2, jetLength * 2.2, searchRadius * 2.2);

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, searchArea);

        for (Entity entity : entities) {
            if (entity instanceof Player player && player.isCreative()) continue;

            Vec3 entityCenter = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            Vec3 delta = entityCenter.subtract(blackHole.position);
            float distance = (float) delta.length();

            // Polar jets - vertical cylinders extending up and down
            boolean inJet = Math.abs(delta.y) > eventHorizon && Math.abs(delta.y) <= jetLength &&
                    Math.sqrt(delta.x * delta.x + delta.z * delta.z) <= jetWidth;

            if (inJet) {
                float jetDamage = 50.0f * blackHole.size;
                entity.hurt(level.damageSources().genericKill(), jetDamage);
                continue;
            }

            // Event horizon - instant kill
            if (distance <= eventHorizon) {
                entity.remove(Entity.RemovalReason.KILLED);
                fed = true;
                continue;
            }

            // Photon sphere - extreme damage
            if (distance <= photonSphere) {
                float photonDamage = 25.0f * blackHole.size;
                entity.hurt(level.damageSources().genericKill(), photonDamage);
                applySpiral(entity, blackHole, distance, 2.0f);
                continue;
            }

            // Inner accretion disk
            if (distance <= accretionInner) {
                float innerDamage = 12.0f * blackHole.size;
                entity.hurt(level.damageSources().genericKill(), innerDamage);
                applySpiral(entity, blackHole, distance, 1.5f);
                continue;
            }

            // Outer accretion disk
            if (distance <= accretionOuter) {
                float outerDamage = 6.0f * blackHole.size;
                entity.hurt(level.damageSources().genericKill(), outerDamage);
                applySpiral(entity, blackHole, distance, 1.0f);
                continue;
            }

            // Gravitational influence beyond visual boundary
            if (distance <= accretionOuter * 1.3f) {
                Vec3 pull = blackHole.position.subtract(entityCenter).normalize();
                float pullStrength = (0.08f * blackHole.size) / Math.max(distance * distance, 1.0f);
                Vec3 newVel = entity.getDeltaMovement().add(pull.scale(pullStrength));
                entity.setDeltaMovement(newVel);
            }
        }

        return fed;
    }

    /**
     * Aggressively scan for blocks matching ALL visual zones
     */
    private static void scanForBlocksAggressive(BlackHole blackHole, ServerLevel level,
                                                float eventHorizon, float photonSphere,
                                                float accretionInner, float accretionOuter,
                                                float jetLength, float jetWidth) {

        // Don't overfill the queue
        if (blackHole.blockQueue.size() >= MAX_BLOCKS_QUEUED_PER_HOLE) {
            return;
        }

        BlockPos center = BlockPos.containing(blackHole.position);

        // Scan the full visual area - include jets
        int horizontalRadius = (int) Math.ceil(accretionOuter) + 2;
        int verticalRadius = (int) Math.ceil(jetLength) + 2;

        // More aggressive scanning
        int blocksScanned = 0;
        final int MAX_BLOCKS_TO_SCAN = 150; // Increased scan limit

        // Start from where we left off for distributed scanning
        for (int y = blackHole.lastScanY; y <= verticalRadius && blocksScanned < MAX_BLOCKS_TO_SCAN; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius && blocksScanned < MAX_BLOCKS_TO_SCAN; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius && blocksScanned < MAX_BLOCKS_TO_SCAN; z++) {

                    // Check both positive and negative Y
                    for (int ySign : new int[]{1, -1}) {
                        int actualY = y * ySign;
                        BlockPos pos = center.offset(x, actualY, z);
                        Vec3 blockCenter = Vec3.atCenterOf(pos);
                        Vec3 delta = blockCenter.subtract(blackHole.position);
                        float distance = (float) delta.length();

                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)) {
                            continue;
                        }

                        boolean shouldDestroy = false;

                        // Check if in polar jet first
                        boolean inJet = Math.abs(delta.y) > eventHorizon && Math.abs(delta.y) <= jetLength &&
                                Math.sqrt(delta.x * delta.x + delta.z * delta.z) <= jetWidth;

                        if (inJet) {
                            shouldDestroy = true; // Jets destroy everything they touch
                        }
                        // Event horizon - always destroy
                        else if (distance <= eventHorizon) {
                            shouldDestroy = true;
                        }
                        // Photon sphere - very high chance
                        else if (distance <= photonSphere) {
                            shouldDestroy = level.random.nextFloat() < 0.95f;
                        }
                        // Inner accretion - high chance
                        else if (distance <= accretionInner) {
                            shouldDestroy = level.random.nextFloat() < 0.85f;
                        }
                        // Outer accretion - moderate to high chance with distance falloff
                        else if (distance <= accretionOuter) {
                            float ratio = (accretionOuter - distance) / (accretionOuter - accretionInner);
                            float chance = 0.4f + (0.4f * ratio); // 40-80% chance based on distance
                            shouldDestroy = level.random.nextFloat() < chance;
                        }

                        if (shouldDestroy) {
                            blackHole.blockQueue.offer(new QueuedBlock(pos, state, distance));
                        }

                        blocksScanned++;
                        if (blocksScanned >= MAX_BLOCKS_TO_SCAN) break;
                    }
                    if (blocksScanned >= MAX_BLOCKS_TO_SCAN) break;
                }
                if (blocksScanned >= MAX_BLOCKS_TO_SCAN) break;
            }
            blackHole.lastScanY = y + 1;
            if (blocksScanned >= MAX_BLOCKS_TO_SCAN) break;
        }

        // Reset scan when complete
        if (blackHole.lastScanY > verticalRadius) {
            blackHole.lastScanY = -verticalRadius;
        }
    }

    /**
     * Process more queued blocks per tick for aggressive destruction
     */
    private static boolean processQueuedBlocksAggressive(BlackHole blackHole, ServerLevel level) {
        boolean fed = false;
        int processed = 0;

        while (!blackHole.blockQueue.isEmpty() && processed < MAX_BLOCKS_PER_TICK_PER_HOLE) {
            QueuedBlock queuedBlock = blackHole.blockQueue.poll();
            if (queuedBlock == null) break;

            // Verify block is still there
            BlockState currentState = level.getBlockState(queuedBlock.pos);
            if (!currentState.isAir() && !currentState.is(Blocks.BEDROCK) && !currentState.is(Blocks.BARRIER)) {
                level.setBlock(queuedBlock.pos, Blocks.AIR.defaultBlockState(), 3);
                consumeBlock(blackHole, queuedBlock.originalState);
                fed = true;
            }
            processed++;
        }

        return fed;
    }

    private static void consumeBlock(BlackHole blackHole, BlockState state) {
        float baseGrowth = BASE_GROWTH_RATE * (1.0f + blackHole.size * GROWTH_SCALING);

        // Material-based growth multipliers
        float materialMultiplier = 1.0f;
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.IRON_BLOCK)) {
            materialMultiplier = 1.4f;
        } else if (state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.NETHERITE_BLOCK)) {
            materialMultiplier = 2.5f;
        } else if (state.is(Blocks.DIRT) || state.is(Blocks.SAND)) {
            materialMultiplier = 0.9f;
        }

        float growth = baseGrowth * materialMultiplier;
        blackHole.size += growth;
        blackHole.lifetime += (int)(BASE_LIFETIME_BONUS * materialMultiplier);
        blackHole.timeSinceLastFeed = 0;
        blackHole.size = Math.min(blackHole.size, 20.0f);
    }

    private static void handleGrowthDecay(BlackHole blackHole, boolean fedThisTick) {
        if (fedThisTick) {
            // Send size updates every 15 ticks for better responsiveness
            if (blackHole.age % 15 == 0) {
                updateBlackHoleSize(blackHole.id, blackHole.size, 0);
            }
        } else {
            blackHole.timeSinceLastFeed++;
            if (blackHole.timeSinceLastFeed > DECAY_TIMER) {
                float decay = DECAY_RATE * Math.max(0.5f, blackHole.size);
                blackHole.size = Math.max(0.1f, blackHole.size - decay);
                blackHole.lifetime = Math.max(0, blackHole.lifetime - 10);

                if (blackHole.age % 30 == 0) {
                    updateBlackHoleSize(blackHole.id, blackHole.size, 0);
                }
            }
        }
    }

    private static void applySpiral(Entity entity, BlackHole blackHole, float distance, float intensity) {
        Vec3 toCenter = blackHole.position.subtract(entity.position());
        Vec3 tangent = new Vec3(-toCenter.z, 0, toCenter.x).normalize();

        // Scaled orbital motion
        float orbitalSpeed = (0.08f * blackHole.size * intensity) / Math.max(distance, 0.1f);
        Vec3 orbital = tangent.scale(orbitalSpeed);

        // Scaled inward pull
        float inwardPull = 0.04f * blackHole.size * intensity / Math.max(distance, 0.1f);
        Vec3 inward = toCenter.normalize().scale(inwardPull);

        Vec3 totalForce = orbital.add(inward);
        Vec3 newVel = entity.getDeltaMovement().add(totalForce.scale(0.12f));

        // Reasonable speed limits
        double maxSpeed = 1.2 + (blackHole.size * 0.08);
        if (newVel.length() > maxSpeed) {
            newVel = newVel.normalize().scale(maxSpeed);
        }

        entity.setDeltaMovement(newVel);
    }

    private static class QueuedBlock {
        final BlockPos pos;
        final BlockState originalState;
        final float distance;

        QueuedBlock(BlockPos pos, BlockState originalState, float distance) {
            this.pos = pos;
            this.originalState = originalState;
            this.distance = distance;
        }
    }

    private static class BlackHole {
        final int id;
        final Vec3 position;
        float size;
        final float rotationSpeed;
        int lifetime;
        int age = 0;
        int timeSinceLastFeed = 0;
        int lastScanY = Integer.MIN_VALUE; // Track Y-level scanning progress
        Level level;

        final Queue<QueuedBlock> blockQueue = new ConcurrentLinkedQueue<>();

        BlackHole(int id, Vec3 position, float size, float rotationSpeed, int lifetime) {
            this.id = id;
            this.position = position;
            this.size = size;
            this.rotationSpeed = rotationSpeed;
            this.lifetime = lifetime;
            this.lastScanY = -(int)Math.ceil(size * JET_LENGTH_MULTIPLIER) - 2; // Start from bottom
        }

        void tick() {
            if (lifetime >= 0) age++;
        }

        boolean isExpired() {
            return (lifetime >= 0 && age >= lifetime) || size <= 0.1f;
        }
    }
}