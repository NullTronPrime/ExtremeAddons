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
    private static final Map<UUID, Boolean> TERRAIN_INITIALIZED = new HashMap<>();
    private static final Set<UUID> ACTIVE_DIMENSIONS = new HashSet<>();
    private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();

    private static final long ACTIVITY_TIMEOUT = 600;

    public static ResourceKey<Level> getPouchDimensionKey(UUID pouchUUID) {
        return POUCH_DIMENSIONS.computeIfAbsent(pouchUUID, uuid ->
                ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.fromNamespaceAndPath(ExAdditions.MOD_ID, "pouch_" + uuid.toString().replace("-", "_"))));
    }

    public static ServerLevel getOrCreatePouchDimension(ServerLevel overworld, UUID pouchUUID) {
        if (DIMENSION_CACHE.containsKey(pouchUUID)) {
            ServerLevel cached = DIMENSION_CACHE.get(pouchUUID);
            if (cached != null && !cached.getServer().isStopped()) {
                cached.setDefaultSpawnPos(new BlockPos(0, 67, 0), 0);
                return cached;
            }
            DIMENSION_CACHE.remove(pouchUUID);
        }

        ResourceKey<Level> dimKey = getPouchDimensionKey(pouchUUID);
        ServerLevel level = overworld.getServer().getLevel(dimKey);

        if (level == null) {
            level = createPouchDimension(overworld, pouchUUID);
        }

        if (level != null) {
            DIMENSION_CACHE.put(pouchUUID, level);

            // Initialize terrain EVERY time if not marked as initialized
            if (!TERRAIN_INITIALIZED.getOrDefault(pouchUUID, false)) {
                initializePouchTerrain(level);
                TERRAIN_INITIALIZED.put(pouchUUID, true);
            }

            level.setDefaultSpawnPos(new BlockPos(0, 67, 0), 0);
        }

        return level;
    }

    public static ServerLevel getDimensionIfLoaded(UUID pouchUUID) {
        return DIMENSION_CACHE.get(pouchUUID);
    }

    public static void markDimensionActive(UUID pouchUUID) {
        boolean wasInactive = !ACTIVE_DIMENSIONS.contains(pouchUUID);
        ACTIVE_DIMENSIONS.add(pouchUUID);
        LAST_ACTIVITY.put(pouchUUID, System.currentTimeMillis());

        if (wasInactive) {
            com.mojang.logging.LogUtils.getLogger().info("Pouch dimension {} marked as active", pouchUUID);
        }
    }

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

            LevelStorageSource.LevelStorageAccess storageAccess;
            try {
                java.lang.reflect.Field storageSourceField = net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");
                storageSourceField.setAccessible(true);
                storageAccess = (LevelStorageSource.LevelStorageAccess) storageSourceField.get(server);
            } catch (Exception e) {
                throw new RuntimeException("Failed to access storage source", e);
            }

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
        int clearRadius = 15; // Clear a larger area to be sure

        // Force load chunks
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
                level.setChunkForced(chunkPos.x, chunkPos.z, true);
            }
        }

        level.setDefaultSpawnPos(new BlockPos(0, 67, 0), 0);

        // AGGRESSIVELY CLEAR EVERYTHING in a large area
        System.out.println("[Pouch] Clearing stone/cobblestone from dimension...");
        int blocksCleared = 0;

        for (int x = -clearRadius; x <= clearRadius; x++) {
            for (int z = -clearRadius; z <= clearRadius; z++) {
                // Clear from bedrock to sky - EVERYTHING
                for (int y = -64; y <= 320; y++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    // Skip the platform at Y=64 within the circle
                    int distSq = x * x + z * z;
                    if (y == 64 && distSq <= radius * radius) {
                        // This is the platform - place it correctly
                        if (x == 0 && z == 0) {
                            level.setBlock(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                        } else {
                            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                        }
                    } else {
                        // EVERYTHING else becomes AIR
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            blocksCleared++;
                        }
                    }
                }
            }
        }

        System.out.println("[Pouch] Cleared " + blocksCleared + " non-air blocks");
    }

    public static void tickActiveDimensions(ServerLevel overworld) {
        long currentTime = System.currentTimeMillis();
        Set<UUID> toRemove = new HashSet<>();

        for (UUID pouchUUID : new HashSet<>(ACTIVE_DIMENSIONS)) {
            ServerLevel level = DIMENSION_CACHE.get(pouchUUID);
            if (level == null) {
                toRemove.add(pouchUUID);
                continue;
            }

            boolean hasPlayers = !level.players().isEmpty();
            Long lastActivity = LAST_ACTIVITY.get(pouchUUID);
            long timeSinceActivity = lastActivity != null ?
                    (currentTime - lastActivity) / 50 :
                    Long.MAX_VALUE;

            if (hasPlayers) {
                LAST_ACTIVITY.put(pouchUUID, currentTime);
            } else if (timeSinceActivity > ACTIVITY_TIMEOUT) {
                toRemove.add(pouchUUID);
                continue;
            }

            try {
                level.tick(() -> true);

                if (level.getGameTime() % 10 == 0) {
                    syncPouchToClients(level, pouchUUID);
                }
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("Error ticking pouch dimension: " + e.getMessage());
            }
        }

        ACTIVE_DIMENSIONS.removeAll(toRemove);
        toRemove.forEach(LAST_ACTIVITY::remove);
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

            ServerLevel overworld = pouchLevel.getServer().overworld();
            for (ServerPlayer player : overworld.players()) {
                net.autismicannoyance.exadditions.network.ModNetworking.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new net.autismicannoyance.exadditions.network.PouchEntitySyncPacket(pouchUUID, entityData)
                );
            }

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
        TERRAIN_INITIALIZED.clear();
        ACTIVE_DIMENSIONS.clear();
        LAST_ACTIVITY.clear();
    }
}