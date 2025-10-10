package net.autismicannoyance.exadditions.world.dimension;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

public class ArcanePouchDimensionManager {
    private static final Map<UUID, ResourceKey<Level>> POUCH_DIMENSIONS = new HashMap<>();
    private static final Map<UUID, ServerLevel> DIMENSION_CACHE = new HashMap<>();
    private static final Map<UUID, Integer> DIMENSION_LOAD_COUNTER = new HashMap<>();
    private static final Set<UUID> ACTIVE_DIMENSIONS = new HashSet<>();
    private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();

    // Dimensions stay active for 30 seconds after last activity
    private static final long ACTIVITY_TIMEOUT = 600; // 30 seconds in ticks

    public static ResourceKey<Level> getPouchDimensionKey(UUID pouchUUID) {
        return POUCH_DIMENSIONS.computeIfAbsent(pouchUUID, uuid ->
                ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.fromNamespaceAndPath(ExAdditions.MOD_ID, "pouch_" + uuid.toString().replace("-", "_"))));
    }

    public static ServerLevel getOrCreatePouchDimension(ServerLevel overworld, UUID pouchUUID) {
        // Check cache first
        if (DIMENSION_CACHE.containsKey(pouchUUID)) {
            ServerLevel cached = DIMENSION_CACHE.get(pouchUUID);
            if (cached != null && !cached.getServer().isStopped()) {
                // Ensure chunks are loaded
                ensureChunksLoaded(cached);
                return cached;
            }
            DIMENSION_CACHE.remove(pouchUUID);
        }

        ResourceKey<Level> dimKey = getPouchDimensionKey(pouchUUID);

        // Try to get existing dimension from server
        ServerLevel level = overworld.getServer().getLevel(dimKey);

        if (level == null) {
            // Create new dimension
            level = createPouchDimension(overworld, pouchUUID);
        }

        if (level != null) {
            DIMENSION_CACHE.put(pouchUUID, level);

            // Initialize terrain if needed (only once)
            if (!DIMENSION_LOAD_COUNTER.containsKey(pouchUUID)) {
                DIMENSION_LOAD_COUNTER.put(pouchUUID, 1);
                initializePouchTerrain(level);
            }

            // Always ensure chunks are loaded when accessing
            ensureChunksLoaded(level);
        }

        return level;
    }

    /**
     * Ensures the spawn chunks are loaded for the dimension
     */
    private static void ensureChunksLoaded(ServerLevel level) {
        // Force load 3x3 chunk area around spawn
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
                level.setChunkForced(chunkPos.x, chunkPos.z, true);
            }
        }
    }

    public static ServerLevel getDimensionIfLoaded(UUID pouchUUID) {
        return DIMENSION_CACHE.get(pouchUUID);
    }

    /**
     * Mark a dimension as active (being viewed or player inside)
     * This enables ticking for the dimension
     */
    public static void markDimensionActive(UUID pouchUUID) {
        boolean wasInactive = !ACTIVE_DIMENSIONS.contains(pouchUUID);
        ACTIVE_DIMENSIONS.add(pouchUUID);
        LAST_ACTIVITY.put(pouchUUID, System.currentTimeMillis());

        if (wasInactive) {
            com.mojang.logging.LogUtils.getLogger().debug("Pouch dimension {} marked as active", pouchUUID);
        }
    }

    /**
     * Check if a player is currently in any pouch dimension
     */
    public static boolean hasPlayersInDimension(ServerLevel level, UUID pouchUUID) {
        if (level == null) return false;
        ResourceKey<Level> dimKey = getPouchDimensionKey(pouchUUID);
        ServerLevel pouchLevel = level.getServer().getLevel(dimKey);
        return pouchLevel != null && !pouchLevel.players().isEmpty();
    }

    private static ServerLevel createPouchDimension(ServerLevel overworld, UUID pouchUUID) {
        ResourceKey<Level> dimKey = getPouchDimensionKey(pouchUUID);
        ResourceKey<DimensionType> dimTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE,
                ResourceLocation.fromNamespaceAndPath(ExAdditions.MOD_ID, "pouch_dim_type"));

        try {
            net.minecraft.server.MinecraftServer server = overworld.getServer();
            net.minecraft.core.RegistryAccess registryAccess = server.registryAccess();
            net.minecraft.core.Registry<DimensionType> dimTypeRegistry = registryAccess.registryOrThrow(Registries.DIMENSION_TYPE);
            DimensionType dimType = dimTypeRegistry.get(dimTypeKey);

            if (dimType == null) {
                dimType = overworld.dimensionType();
            }

            // Get level storage access via reflection
            LevelStorageSource.LevelStorageAccess storageAccess;
            try {
                java.lang.reflect.Field storageSourceField = net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");
                storageSourceField.setAccessible(true);
                storageAccess = (LevelStorageSource.LevelStorageAccess) storageSourceField.get(server);
            } catch (Exception e) {
                throw new RuntimeException("Failed to access storage source", e);
            }

            // Use overworld level data
            ServerLevelData worldData = (ServerLevelData) overworld.getLevelData();

            ArcanePouchChunkGenerator generator = new ArcanePouchChunkGenerator(
                    new net.minecraft.world.level.biome.FixedBiomeSource(
                            registryAccess.registryOrThrow(Registries.BIOME).getHolderOrThrow(
                                    net.minecraft.world.level.biome.Biomes.THE_VOID)),
                    pouchUUID.getMostSignificantBits());

            LevelStem levelStem = new LevelStem(
                    registryAccess.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(dimTypeKey),
                    generator
            );

            ChunkProgressListener progressListener = new ChunkProgressListener() {
                @Override
                public void updateSpawnPos(net.minecraft.world.level.ChunkPos chunkPos) {}

                @Override
                public void onStatusChange(net.minecraft.world.level.ChunkPos chunkPos, net.minecraft.world.level.chunk.ChunkStatus status) {}

                @Override
                public void start() {}

                @Override
                public void stop() {}
            };

            // Use synchronous executor to prevent deadlocks
            Executor syncExecutor = Runnable::run;

            ServerLevel newLevel = new ServerLevel(
                    server,
                    syncExecutor,
                    storageAccess,
                    worldData,
                    dimKey,
                    levelStem,
                    progressListener,
                    false,
                    0L,
                    com.google.common.collect.ImmutableList.of(),
                    false,
                    null
            );

            // Register dimension with server
            Map<ResourceKey<Level>, ServerLevel> levels = server.forgeGetWorldMap();
            levels.put(dimKey, newLevel);

            return newLevel;
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Failed to create pouch dimension", e);
            return null;
        }
    }

    private static void initializePouchTerrain(ServerLevel level) {
        int radius = 8;

        // Set spawn position first
        level.setDefaultSpawnPos(new BlockPos(0, 65, 0), 0);

        // Force load spawn chunks around the platform
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
                level.setChunkForced(chunkPos.x, chunkPos.z, true);
            }
        }

        // Build platform
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    BlockPos platformPos = new BlockPos(x, 64, z);

                    // Set platform block
                    if (x == 0 && z == 0) {
                        level.setBlock(platformPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                    } else {
                        level.setBlock(platformPos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                    }

                    // Clear space above
                    for (int y = 65; y < 70; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /**
     * Called every server tick to update active pouch dimensions
     */
    public static void tickActiveDimensions(ServerLevel overworld) {
        long currentTime = System.currentTimeMillis();
        Set<UUID> toDeactivate = new HashSet<>();

        // Check all active dimensions
        for (UUID pouchUUID : new HashSet<>(ACTIVE_DIMENSIONS)) {
            ServerLevel level = DIMENSION_CACHE.get(pouchUUID);
            if (level == null) {
                toDeactivate.add(pouchUUID);
                continue;
            }

            // Check if dimension still has activity
            boolean hasPlayers = !level.players().isEmpty();
            Long lastActivity = LAST_ACTIVITY.get(pouchUUID);
            long timeSinceActivity = lastActivity != null ?
                    (currentTime - lastActivity) / 50 : // Convert to ticks (50ms per tick)
                    Long.MAX_VALUE;

            if (hasPlayers) {
                // Keep active if players inside
                LAST_ACTIVITY.put(pouchUUID, currentTime);
            } else if (timeSinceActivity > ACTIVITY_TIMEOUT) {
                // Deactivate if no activity for too long
                toDeactivate.add(pouchUUID);
                continue;
            }

            // ✅ FIX: Manually tick entities since the dimension isn't in the main tick loop
            try {
                // Tick all entities in the dimension
                for (Entity entity : level.getAllEntities()) {
                    if (!entity.isRemoved() && entity.isAlive()) {
                        entity.tick();
                    }
                }

                // Sync entities to clients every 0.5 seconds
                if (level.getGameTime() % 10 == 0) {
                    syncPouchToClients(level, pouchUUID);
                }
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("Error ticking pouch dimension entities: " + e.getMessage());
            }
        }

        // Remove inactive dimensions
        ACTIVE_DIMENSIONS.removeAll(toDeactivate);
        toDeactivate.forEach(uuid -> {
            LAST_ACTIVITY.remove(uuid);
            com.mojang.logging.LogUtils.getLogger().debug("Pouch dimension {} deactivated", uuid);
        });
    }

    private static void syncPouchToClients(ServerLevel pouchLevel, UUID pouchUUID) {
        try {
            ListTag entityData = new ListTag();
            AABB searchBox = new AABB(-10, 60, -10, 10, 75, 10);
            List<Entity> entities = pouchLevel.getEntities((Entity) null, searchBox, e -> e instanceof LivingEntity);

            for (Entity entity : entities) {
                CompoundTag tag = new CompoundTag();
                entity.save(tag);
                entityData.add(tag);
            }

            // Send to all players in overworld who might be viewing the tooltip
            ServerLevel overworld = pouchLevel.getServer().overworld();
            for (ServerPlayer player : overworld.players()) {
                net.autismicannoyance.exadditions.network.ModNetworking.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new net.autismicannoyance.exadditions.network.PouchEntitySyncPacket(pouchUUID, entityData)
                );
            }

            // Also send to players inside the dimension
            for (ServerPlayer player : pouchLevel.players()) {
                net.autismicannoyance.exadditions.network.ModNetworking.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new net.autismicannoyance.exadditions.network.PouchEntitySyncPacket(pouchUUID, entityData)
                );
            }
        } catch (Exception e) {
            // Ignore sync errors
        }
    }

    public static void cleanup() {
        DIMENSION_CACHE.clear();
        POUCH_DIMENSIONS.clear();
        DIMENSION_LOAD_COUNTER.clear();
        ACTIVE_DIMENSIONS.clear();
        LAST_ACTIVITY.clear();
    }
}