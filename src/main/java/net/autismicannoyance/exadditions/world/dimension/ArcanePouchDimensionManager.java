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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArcanePouchDimensionManager {
    private static final Map<UUID, ResourceKey<Level>> POUCH_DIMENSIONS = new HashMap<>();
    private static final Map<UUID, ServerLevel> DIMENSION_CACHE = new HashMap<>();
    private static final Map<UUID, Integer> DIMENSION_LOAD_COUNTER = new HashMap<>();

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
        }

        return level;
    }

    public static ServerLevel getDimensionIfLoaded(UUID pouchUUID) {
        return DIMENSION_CACHE.get(pouchUUID);
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

            ServerLevelData overworldData = (ServerLevelData) overworld.getLevelData();

            ServerLevelData worldData = new net.minecraft.world.level.storage.PrimaryLevelData(
                    overworldData.worldGenOptions(),
                    overworldData.getLevelSettings(),
                    overworldData.worldGenOptions().isOldCustomizedWorld()
            );

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

            ServerLevel newLevel = new ServerLevel(
                    server,
                    server.executor,
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

        // Force load the spawn chunk
        level.setDefaultSpawnPos(new BlockPos(0, 65, 0), 0);

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

    public static void tickPouchDimension(UUID pouchUUID) {
        ServerLevel level = DIMENSION_CACHE.get(pouchUUID);
        if (level != null && !level.getServer().isStopped()) {
            try {
                // Don't call level.tick() here - server handles that
                // Just sync entity data
                if (level.getGameTime() % 10 == 0) {
                    syncPouchToClients(level, pouchUUID);
                }
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().error("Error ticking pouch dimension", e);
            }
        }
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

            // Send to all players in overworld
            ServerLevel overworld = pouchLevel.getServer().overworld();
            for (ServerPlayer player : overworld.players()) {
                net.autismicannoyance.exadditions.network.ModNetworking.INSTANCE.send(
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
    }
}