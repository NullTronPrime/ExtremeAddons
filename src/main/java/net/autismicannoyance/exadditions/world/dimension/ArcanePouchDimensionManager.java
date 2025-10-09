package net.autismicannoyance.exadditions.world.dimension;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.PrimaryLevelData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArcanePouchDimensionManager {
    private static final Map<UUID, ResourceKey<Level>> POUCH_DIMENSIONS = new HashMap<>();
    private static final Map<UUID, ServerLevel> DIMENSION_CACHE = new HashMap<>();

    public static ResourceKey<Level> getPouchDimensionKey(UUID pouchUUID) {
        return POUCH_DIMENSIONS.computeIfAbsent(pouchUUID, uuid ->
                ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.fromNamespaceAndPath(ExAdditions.MOD_ID, "pouch_" + uuid.toString().replace("-", "_"))));
    }

    public static ServerLevel getOrCreatePouchDimension(ServerLevel overworld, UUID pouchUUID) {
        if (DIMENSION_CACHE.containsKey(pouchUUID)) {
            return DIMENSION_CACHE.get(pouchUUID);
        }
        ResourceKey<Level> dimKey = getPouchDimensionKey(pouchUUID);
        ServerLevel level = overworld.getServer().getLevel(dimKey);
        if (level == null) {
            level = createPouchDimension(overworld, pouchUUID);
        }
        if (level != null) {
            DIMENSION_CACHE.put(pouchUUID, level);
            initializePouchTerrain(level);
        }
        return level;
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
            net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess storage = server.storageSource;

            // Get the overworld's level data
            PrimaryLevelData overworldData = (PrimaryLevelData) overworld.getLevelData();

            // Create new level data with proper methods
            PrimaryLevelData worldData = new PrimaryLevelData(
                    overworldData.worldGenOptions(),
                    overworldData.getLevelSettings(),
                    overworldData.getSpecialWorldProperty(),
                    overworldData.worldGenOptions().isOldCustomizedWorld()
            );

            ArcanePouchChunkGenerator generator = new ArcanePouchChunkGenerator(
                    new net.minecraft.world.level.biome.FixedBiomeSource(
                            registryAccess.registryOrThrow(Registries.BIOME).getHolderOrThrow(
                                    net.minecraft.world.level.biome.Biomes.THE_VOID)),
                    pouchUUID.getMostSignificantBits());

            ServerLevel newLevel = new ServerLevel(
                    server,
                    server.executor,
                    storage,
                    worldData,
                    dimKey,
                    new net.minecraft.world.level.dimension.LevelStem(
                            registryAccess.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(dimTypeKey),
                            generator),
                    (progressListener, createStructures) -> {},
                    false,
                    0L,
                    com.google.common.collect.ImmutableList.of(),
                    false,
                    null
            );
            return newLevel;
        } catch (Exception e) {
            return null;
        }
    }

    private static void initializePouchTerrain(ServerLevel level) {
        int radius = 8;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    level.setBlock(new BlockPos(x, 64, z), Blocks.OBSIDIAN.defaultBlockState(), 3);
                    for (int y = 65; y < 70; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        level.setBlock(new BlockPos(0, 64, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
    }

    public static void tickPouchDimension(UUID pouchUUID) {
        ServerLevel level = DIMENSION_CACHE.get(pouchUUID);
        if (level != null) {
            try {
                level.tick(() -> true);
            } catch (Exception ignored) {}
        }
    }
}