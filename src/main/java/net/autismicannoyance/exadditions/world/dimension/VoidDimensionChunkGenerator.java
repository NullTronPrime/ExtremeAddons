package net.autismicannoyance.exadditions.world.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class VoidDimensionChunkGenerator extends ChunkGenerator {
    public static final Codec<VoidDimensionChunkGenerator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    Codec.LONG.fieldOf("seed").forGetter(generator -> generator.seed)
            ).apply(instance, VoidDimensionChunkGenerator::new)
    );

    private final long seed;

    // Terrain generation parameters
    private static final int BASE_HEIGHT = 80;
    private static final int MIN_HEIGHT = 40;
    private static final int MAX_HEIGHT = 240; // Proper world height for peaks

    // Peak layer thresholds
    private static final int BLACKSTONE_THRESHOLD = 140; // Above this height, use blackstone
    private static final int GLOWSTONE_THRESHOLD = 200;  // Above this height, cap with glowstone

    // Lava basin parameters
    private static final double BASIN_FREQUENCY = 0.007;
    private static final double BASIN_THRESHOLD = 0.3;
    private static final int BASIN_DEPTH = 12;
    private static final int LAVA_FILL_LEVEL = 8; // How high to fill basins with lava

    public VoidDimensionChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.seed = seed;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // No carvers needed
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        // Generate terrain for each column
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;

                // Calculate terrain features
                TerrainData terrain = calculateTerrainData(worldX, worldZ);

                // Generate the column
                generateColumn(chunk, x, z, terrain, minY, maxY);
            }
        }
    }

    /**
     * Data class to hold terrain information for a column
     */
    private static class TerrainData {
        final int height;
        final boolean isBasin;
        final int basinFloor;
        final boolean isPeak;

        TerrainData(int height, boolean isBasin, int basinFloor, boolean isPeak) {
            this.height = height;
            this.isBasin = isBasin;
            this.basinFloor = basinFloor;
            this.isPeak = isPeak;
        }
    }

    /**
     * Calculates terrain data for a specific world position using efficient noise
     */
    private TerrainData calculateTerrainData(int worldX, int worldZ) {
        // Base terrain using value noise (faster than Perlin)
        double baseNoise = valueNoise2D(worldX * 0.008, worldZ * 0.008, seed);

        // Ridge noise for creating sharp peaks
        double ridgeNoise = Math.abs(valueNoise2D(worldX * 0.015, worldZ * 0.015, seed + 1000));
        ridgeNoise = 1.0 - ridgeNoise; // Invert to create ridges
        ridgeNoise = ridgeNoise * ridgeNoise; // Square for sharper peaks

        // Detail noise for surface variation
        double detailNoise = valueNoise2D(worldX * 0.04, worldZ * 0.04, seed + 2000) * 0.3;

        // Calculate base height
        double heightFactor = baseNoise * 0.5 + ridgeNoise * 0.4 + detailNoise * 0.1;
        int height = (int)(BASE_HEIGHT + heightFactor * (MAX_HEIGHT - BASE_HEIGHT));

        // Check for lava basins
        double basinNoise = valueNoise2D(worldX * BASIN_FREQUENCY, worldZ * BASIN_FREQUENCY, seed + 3000);
        boolean isBasin = basinNoise > BASIN_THRESHOLD && height < 120; // Basins only in lower areas

        int basinFloor = height;
        if (isBasin) {
            // Carve out the basin
            double basinDepthFactor = (basinNoise - BASIN_THRESHOLD) / (1.0 - BASIN_THRESHOLD);
            int depthReduction = (int)(BASIN_DEPTH * basinDepthFactor);
            basinFloor = height - depthReduction;
            height = basinFloor; // Lower the surface to the basin floor
        }

        // Determine if this is a peak (for special layering)
        boolean isPeak = height > BLACKSTONE_THRESHOLD;

        // Clamp height to world bounds
        height = Mth.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        basinFloor = Mth.clamp(basinFloor, MIN_HEIGHT, height);

        return new TerrainData(height, isBasin, basinFloor, isPeak);
    }

    /**
     * Generates a single column of terrain with layered materials
     */
    private void generateColumn(ChunkAccess chunk, int x, int z, TerrainData terrain, int minY, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY && y <= terrain.height + LAVA_FILL_LEVEL; y++) {
            pos.set(x, y, z);
            BlockState blockToPlace = Blocks.AIR.defaultBlockState();

            if (terrain.isBasin && y > terrain.basinFloor && y <= terrain.basinFloor + LAVA_FILL_LEVEL) {
                // Fill basin with lava
                blockToPlace = Blocks.LAVA.defaultBlockState();
            } else if (y <= terrain.height) {
                // Determine block type based on height and peak status
                blockToPlace = getBlockForHeight(y, terrain.height, terrain.isPeak);
            }

            chunk.setBlockState(pos, blockToPlace, false);
        }
    }

    /**
     * Determines which block to use based on the height and whether it's a peak
     */
    private BlockState getBlockForHeight(int y, int surfaceHeight, boolean isPeak) {
        if (!isPeak) {
            // Non-peak areas are just obsidian
            return Blocks.OBSIDIAN.defaultBlockState();
        }

        // For peaks, create layered structure
        if (y >= GLOWSTONE_THRESHOLD) {
            // Cap with glowstone
            if (y >= surfaceHeight - 3) { // Only the very top
                return Blocks.GLOWSTONE.defaultBlockState();
            } else {
                return Blocks.BLACKSTONE.defaultBlockState();
            }
        } else if (y >= BLACKSTONE_THRESHOLD) {
            // Middle layer is blackstone
            return Blocks.BLACKSTONE.defaultBlockState();
        } else {
            // Base is obsidian
            return Blocks.OBSIDIAN.defaultBlockState();
        }
    }

    /**
     * Fast 2D value noise implementation
     */
    private double valueNoise2D(double x, double z, long seed) {
        int xi = (int)Math.floor(x);
        int zi = (int)Math.floor(z);

        double fx = x - xi;
        double fz = z - zi;

        // Smooth interpolation curves
        double u = smoothstep(fx);
        double w = smoothstep(fz);

        // Get corner values
        double v00 = hashToDouble(xi, zi, seed);
        double v10 = hashToDouble(xi + 1, zi, seed);
        double v01 = hashToDouble(xi, zi + 1, seed);
        double v11 = hashToDouble(xi + 1, zi + 1, seed);

        // Bilinear interpolation
        double v0 = lerp(u, v00, v10);
        double v1 = lerp(u, v01, v11);

        return lerp(w, v0, v1);
    }

    /**
     * Hash function to generate deterministic random values
     */
    private double hashToDouble(int x, int z, long seed) {
        long hash = seed;
        hash ^= x * 374761393L;
        hash ^= z * 668265263L;
        hash = (hash ^ (hash >> 13)) * 1274126177L;
        hash = hash ^ (hash >> 16);
        // Convert to 0-1 range
        return (hash & 0x7FFFFFFFL) / (double)0x7FFFFFFFL;
    }

    /**
     * Smoothstep interpolation for smoother noise
     */
    private double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Linear interpolation
     */
    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // No mob spawning in void dimension
    }

    @Override
    public int getSeaLevel() {
        return -63; // No sea level in void
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return BASE_HEIGHT + 30; // Spawn at safe height above floor
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState randomState) {
        TerrainData terrain = calculateTerrainData(x, z);
        return terrain.height;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        TerrainData terrain = calculateTerrainData(x, z);
        BlockState[] states = new BlockState[level.getHeight()];

        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y <= terrain.height) {
                states[i] = getBlockForHeight(y, terrain.height, terrain.isPeak);
            } else if (terrain.isBasin && y <= terrain.basinFloor + LAVA_FILL_LEVEL) {
                states[i] = Blocks.LAVA.defaultBlockState();
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }

        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        TerrainData terrain = calculateTerrainData(pos.getX(), pos.getZ());
        info.add("Void Dimension - Extreme Peaks & Lava Basins");
        info.add("Height: " + terrain.height + " / 512");
        info.add("Is Basin: " + terrain.isBasin);
        info.add("Is Peak: " + terrain.isPeak);
        if (terrain.height >= GLOWSTONE_THRESHOLD) {
            info.add("Zone: GLOWSTONE CAP");
        } else if (terrain.height >= BLACKSTONE_THRESHOLD) {
            info.add("Zone: BLACKSTONE");
        } else {
            info.add("Zone: OBSIDIAN");
        }
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getGenDepth() {
        return 512; // Full dimension height
    }
}