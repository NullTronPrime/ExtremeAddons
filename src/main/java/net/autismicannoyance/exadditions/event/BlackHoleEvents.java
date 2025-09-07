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
 * Black hole physics with gradual block destruction to prevent lag
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlackHoleEvents {

    private static final Map<Integer, BlackHole> ACTIVE_BLACK_HOLES = new ConcurrentHashMap<>();

    // Exact renderer constants
    private static final float EVENT_HORIZON_MULTIPLIER = 1.0f;
    private static final float PHOTON_SPHERE_MULTIPLIER = 1.5f;
    private static final float ACCRETION_INNER_MULTIPLIER = 2.5f;
    private static final float ACCRETION_OUTER_MULTIPLIER = 12.0f;
    private static final float JET_LENGTH_MULTIPLIER = 20.0f;
    private static final float JET_WIDTH_MULTIPLIER = 0.8f;

    // Performance limits to prevent lag
    private static final int MAX_BLOCKS_PER_TICK = 25; // Reduced for smooth performance
    private static final int BLOCK_PROCESS_INTERVAL = 1; // Process every tick
    private static final int ENTITY_PROCESS_INTERVAL = 1; // Process every tick

    // Growth constants
    private static final float BASE_GROWTH = 0.08f;
    private static final int BASE_LIFETIME = 100;
    private static final int DECAY_TIMER = 60;
    private static final float DECAY_RATE = 0.02f;

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
                // Send removal packet immediately when black hole expires
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

            // Process black hole physics while it's active
            processBlackHole(blackHole, serverLevel);
        }
    }

    private static void processBlackHole(BlackHole blackHole, ServerLevel level) {
        // Calculate visual zones
        float eventHorizon = blackHole.size * EVENT_HORIZON_MULTIPLIER;
        float photonSphere = blackHole.size * PHOTON_SPHERE_MULTIPLIER;
        float accretionInner = blackHole.size * ACCRETION_INNER_MULTIPLIER;
        float accretionOuter = blackHole.size * ACCRETION_OUTER_MULTIPLIER;
        float jetLength = blackHole.size * JET_LENGTH_MULTIPLIER;
        float jetWidth = blackHole.size * JET_WIDTH_MULTIPLIER;

        boolean fedThisTick = false;

        // Process entities every tick for smooth damage
        if (blackHole.age % ENTITY_PROCESS_INTERVAL == 0) {
            fedThisTick |= processEntities(blackHole, level, eventHorizon, photonSphere,
                    accretionInner, accretionOuter, jetLength, jetWidth);
        }

        // Process blocks gradually to prevent lag
        if (blackHole.age % BLOCK_PROCESS_INTERVAL == 0) {
            fedThisTick |= processBlocksGradually(blackHole, level, eventHorizon, photonSphere,
                    accretionInner, accretionOuter);
        }

        // Handle growth/decay
        handleGrowthDecay(blackHole, fedThisTick);
    }

    private static boolean processEntities(BlackHole blackHole, ServerLevel level, float eventHorizon,
                                           float photonSphere, float accretionInner, float accretionOuter,
                                           float jetLength, float jetWidth) {
        boolean fed = false;

        AABB searchArea = AABB.ofSize(blackHole.position,
                accretionOuter * 2.2, Math.max(jetLength * 2.2, accretionOuter * 2.2), accretionOuter * 2.2);

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, searchArea);

        for (Entity entity : entities) {
            if (entity instanceof Player player && player.isCreative()) continue;

            Vec3 entityCenter = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            Vec3 delta = entityCenter.subtract(blackHole.position);
            float distance = (float) delta.length();

            // Check jets - vertical cylinders
            boolean inJet = Math.abs(delta.y) > eventHorizon && Math.abs(delta.y) <= jetLength &&
                    Math.sqrt(delta.x * delta.x + delta.z * delta.z) <= jetWidth;

            if (inJet) {
                float jetDamage = 60.0f * blackHole.size;
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
                float photonDamage = 30.0f * blackHole.size;
                entity.hurt(level.damageSources().genericKill(), photonDamage);
                applySpiral(entity, blackHole, distance, 2.0f);
                continue;
            }

            // Inner accretion disk
            if (distance <= accretionInner) {
                float innerDamage = 15.0f * blackHole.size;
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

            // Gravitational influence
            if (distance <= accretionOuter * 1.3f) {
                Vec3 pull = blackHole.position.subtract(entityCenter).normalize();
                float pullStrength = (0.08f * blackHole.size) / Math.max(distance * distance, 1.0f);
                Vec3 newVel = entity.getDeltaMovement().add(pull.scale(pullStrength));
                entity.setDeltaMovement(newVel);
            }
        }

        return fed;
    }

    private static boolean processBlocksGradually(BlackHole blackHole, ServerLevel level, float eventHorizon,
                                                  float photonSphere, float accretionInner, float accretionOuter) {
        boolean fed = false;
        BlockPos center = BlockPos.containing(blackHole.position);

        // Create a list of blocks to process, sorted by priority
        List<BlockCandidate> candidates = new ArrayList<>();
        int searchRadius = (int) Math.ceil(accretionOuter) + 1;

        // Collect blocks in spiral pattern starting from center
        for (int r = 0; r <= searchRadius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        // Skip blocks not on the edge of current radius
                        if (r > 0 && Math.abs(x) < r && Math.abs(y) < r && Math.abs(z) < r) continue;

                        BlockPos pos = center.offset(x, y, z);
                        Vec3 blockCenter = Vec3.atCenterOf(pos);
                        float distance = (float) blackHole.position.distanceTo(blockCenter);

                        if (distance <= accretionOuter) {
                            BlockState state = level.getBlockState(pos);
                            if (!state.isAir() && !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER)) {
                                candidates.add(new BlockCandidate(pos, state, distance));
                            }
                        }
                    }
                }
            }
        }

        // Sort by distance (closest first) for more realistic consumption
        candidates.sort((a, b) -> Float.compare(a.distance, b.distance));

        // Process limited number of blocks per tick to prevent lag
        int processed = 0;
        for (BlockCandidate candidate : candidates) {
            if (processed >= MAX_BLOCKS_PER_TICK) break;

            float distance = candidate.distance;
            boolean shouldDestroy = false;

            // Event horizon - always destroy
            if (distance <= eventHorizon) {
                shouldDestroy = true;
            }
            // Photon sphere - high chance
            else if (distance <= photonSphere) {
                shouldDestroy = level.random.nextFloat() < 0.9f;
            }
            // Inner accretion - medium-high chance
            else if (distance <= accretionInner) {
                shouldDestroy = level.random.nextFloat() < 0.7f;
            }
            // Outer accretion - distance-based chance
            else if (distance <= accretionOuter) {
                float ratio = (accretionOuter - distance) / (accretionOuter - accretionInner);
                float chance = 0.5f * ratio * ratio; // Quadratic falloff
                shouldDestroy = level.random.nextFloat() < chance;
            }

            if (shouldDestroy) {
                level.setBlock(candidate.pos, Blocks.AIR.defaultBlockState(), 3);
                consumeBlock(blackHole, candidate.state);
                fed = true;
                processed++;
            } else {
                processed++; // Count checked blocks to maintain performance limit
            }
        }

        return fed;
    }

    private static void consumeBlock(BlackHole blackHole, BlockState state) {
        float growth = BASE_GROWTH * blackHole.size; // Scale with current size

        // Different blocks provide different growth
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.IRON_BLOCK)) {
            growth *= 1.5f;
        } else if (state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.NETHERITE_BLOCK)) {
            growth *= 3.0f;
        } else if (state.is(Blocks.DIRT) || state.is(Blocks.SAND)) {
            growth *= 0.8f; // Less growth from soft materials
        }

        blackHole.size += growth;
        blackHole.lifetime += (int)(BASE_LIFETIME * blackHole.size * 0.1f);
        blackHole.timeSinceLastFeed = 0;
        blackHole.size = Math.min(blackHole.size, 20.0f);
    }

    private static void handleGrowthDecay(BlackHole blackHole, boolean fedThisTick) {
        if (fedThisTick) {
            updateBlackHoleSize(blackHole.id, blackHole.size, 0);
        } else {
            blackHole.timeSinceLastFeed++;
            if (blackHole.timeSinceLastFeed > DECAY_TIMER) {
                float decay = DECAY_RATE * blackHole.size; // Proportional decay
                blackHole.size = Math.max(0.1f, blackHole.size - decay);
                blackHole.lifetime = Math.max(0, blackHole.lifetime - 20);
                updateBlackHoleSize(blackHole.id, blackHole.size, 0);
            }
        }
    }

    private static void applySpiral(Entity entity, BlackHole blackHole, float distance, float intensity) {
        Vec3 toCenter = blackHole.position.subtract(entity.position());
        Vec3 tangent = new Vec3(-toCenter.z, 0, toCenter.x).normalize();

        // Size-scaled orbital velocity
        float orbitalSpeed = (0.12f * blackHole.size * intensity) / Math.max(distance, 0.1f);
        Vec3 orbital = tangent.scale(orbitalSpeed);

        // Size-scaled inward pull
        float inwardPull = 0.06f * blackHole.size * intensity;
        Vec3 inward = toCenter.normalize().scale(inwardPull);

        Vec3 totalForce = orbital.add(inward);
        Vec3 newVel = entity.getDeltaMovement().add(totalForce.scale(0.15f));

        // Size-scaled speed limit
        double maxSpeed = 1.8 + (blackHole.size * 0.15);
        if (newVel.length() > maxSpeed) {
            newVel = newVel.normalize().scale(maxSpeed);
        }

        entity.setDeltaMovement(newVel);
    }

    private static class BlockCandidate {
        final BlockPos pos;
        final BlockState state;
        final float distance;

        BlockCandidate(BlockPos pos, BlockState state, float distance) {
            this.pos = pos;
            this.state = state;
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
        Level level;

        BlackHole(int id, Vec3 position, float size, float rotationSpeed, int lifetime) {
            this.id = id;
            this.position = position;
            this.size = size;
            this.rotationSpeed = rotationSpeed;
            this.lifetime = lifetime;
        }

        void tick() {
            if (lifetime >= 0) age++;
        }

        boolean isExpired() {
            return (lifetime >= 0 && age >= lifetime) || size <= 0.1f;
        }
    }
}