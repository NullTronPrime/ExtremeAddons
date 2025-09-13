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
    private static final int SAMPLE_RATE = 2;
    private static final int LAKE_SAMPLE_RATE = 4; // Larger sampling for proper lakes

    // New height parameters
    private static final int SURFACE_BASE = 100;      // Base surface level
    private static final int MAX_MOUNTAIN_HEIGHT = 1000;  // Mountain peaks
    private static final int MIN_WORLD_HEIGHT = -1024;    // Deep caves
    private static final int MAX_WORLD_HEIGHT = 1024;     // Absolute max

    // Lake parameters
    private static final double LAKE_THRESHOLD = 0.3;
    private static final int LAKE_DEPTH_MIN = 10;
    private static final int LAKE_DEPTH_MAX = 30;

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
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        // Generate surface terrain heights
        double[][] surfaceHeights = generateJaggedTerrain(chunkPos);

        // Generate lake data - both mask and depths
        LakeData[][] lakeData = generateProperLakes(chunkPos);

        // Generate terrain for each column
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double surfaceHeight = bilinearInterpolate(surfaceHeights, x, z, SAMPLE_RATE);
                LakeData lake = bilinearInterpolateLakeData(lakeData, x, z, LAKE_SAMPLE_RATE);

                generateColumn(chunk, x, z, (int) surfaceHeight, lake, minY, maxY, chunkPos);
            }
        }
    }

    // Lake data structure
    private static class LakeData {
        final boolean isLake;
        final int depth;

        LakeData(boolean isLake, int depth) {
            this.isLake = isLake;
            this.depth = depth;
        }
    }

    /**
     * Generates jagged terrain with proper height range
     */
    private double[][] generateJaggedTerrain(ChunkPos chunkPos) {
        int samples = (16 / SAMPLE_RATE) + 1;
        double[][] heights = new double[samples][samples];

        for (int i = 0; i < samples; i++) {
            for (int j = 0; j < samples; j++) {
                int worldX = chunkPos.getMinBlockX() + (i * SAMPLE_RATE);
                int worldZ = chunkPos.getMinBlockZ() + (j * SAMPLE_RATE);

                double height = generateComplexTerrain(worldX, worldZ);
                // Clamp to proper range - surface from 100 to 1000
                heights[i][j] = Mth.clamp(height, SURFACE_BASE, MAX_MOUNTAIN_HEIGHT);
            }
        }
        return heights;
    }

    /**
     * Complex terrain generation for dramatic mountains
     */
    private double generateComplexTerrain(int x, int z) {
        // Base surface level
        double baseHeight = SURFACE_BASE;

        // Method 1: Large scale mountain placement
        double largeMountains = ridgedNoise(x * 0.002, z * 0.002, seed, 4) * 400;

        // Method 2: Medium scale ridges
        double mediumRidges = ridgedNoise(x * 0.006, z * 0.006, seed + 1000, 3) * 200;

        // Method 3: Sharp peaks with domain warping
        double warpX = x + simplexNoise(x * 0.008, z * 0.008, seed + 2000) * 100;
        double warpZ = z + simplexNoise(x * 0.008, z * 0.008, seed + 3000) * 100;
        double sharpPeaks = Math.pow(Math.abs(simplexNoise(warpX * 0.01, warpZ * 0.01, seed + 4000)), 2.5) * 300;

        // Method 4: Fractal detail
        double detail = fractalBrownianMotion(x, z, 5, 0.015, 0.5, seed + 5000) * 100;

        // Method 5: Cellular noise for jagged edges
        double jagged = cellularNoise(x, z, seed + 6000) * 80;

        // Combine all layers
        double mountainHeight = largeMountains + mediumRidges + detail + jagged;

        // Add sharp peaks in certain areas
        if (sharpPeaks > 100) {
            mountainHeight += sharpPeaks;
        }

        // Height multiplier for even more variation
        double heightMultiplier = 1.0 + Math.abs(simplexNoise(x * 0.001, z * 0.001, seed + 7000)) * 1.5;

        return baseHeight + (mountainHeight * heightMultiplier);
    }

    /**
     * Generate proper lake data with consistent depths
     */
    private LakeData[][] generateProperLakes(ChunkPos chunkPos) {
        int samples = (16 / LAKE_SAMPLE_RATE) + 1;
        LakeData[][] lakeData = new LakeData[samples][samples];

        for (int i = 0; i < samples; i++) {
            for (int j = 0; j < samples; j++) {
                int worldX = chunkPos.getMinBlockX() + (i * LAKE_SAMPLE_RATE);
                int worldZ = chunkPos.getMinBlockZ() + (j * LAKE_SAMPLE_RATE);

                // Large scale lake placement
                double lakeNoise1 = simplexNoise(worldX * 0.003, worldZ * 0.003, seed + 8000);
                double lakeNoise2 = simplexNoise(worldX * 0.006, worldZ * 0.006, seed + 9000) * 0.6;
                double lakeNoise3 = simplexNoise(worldX * 0.012, worldZ * 0.012, seed + 10000) * 0.3;

                double combinedLakeNoise = lakeNoise1 + lakeNoise2 + lakeNoise3;
                boolean isLake = combinedLakeNoise > LAKE_THRESHOLD;

                int lakeDepth = 0;
                if (isLake) {
                    // Variable lake depth based on noise
                    double depthNoise = simplexNoise(worldX * 0.005, worldZ * 0.005, seed + 11000);
                    lakeDepth = (int) (LAKE_DEPTH_MIN + (depthNoise + 1.0) * 0.5 * (LAKE_DEPTH_MAX - LAKE_DEPTH_MIN));
                }

                lakeData[i][j] = new LakeData(isLake, lakeDepth);
            }
        }
        return lakeData;
    }

    /**
     * Enhanced column generation with proper height distribution
     */
    private void generateColumn(ChunkAccess chunk, int x, int z, int surfaceHeight, LakeData lakeData,
                                int minY, int maxY, ChunkPos chunkPos) {
        int worldX = chunkPos.getMinBlockX() + x;
        int worldZ = chunkPos.getMinBlockZ() + z;
        RandomSource random = RandomSource.create(hash2D(worldX, worldZ, seed));

        // Determine actual surface level (accounting for lakes)
        int actualSurfaceHeight = surfaceHeight;
        if (lakeData.isLake) {
            actualSurfaceHeight = surfaceHeight - lakeData.depth;
        }

        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState blockToPlace = Blocks.AIR.defaultBlockState();

            if (y < 0) {
                // Underground/cave system (y < 0)
                boolean isCave = shouldGenerateDeepCave(worldX, y, worldZ);
                if (!isCave) {
                    // Deep underground is mostly blackstone
                    double materialNoise = simplexNoise(worldX * 0.05, y * 0.05, worldZ * 0.05, seed + 12000);
                    if (materialNoise > 0.4) {
                        blockToPlace = Blocks.BASALT.defaultBlockState();
                    } else {
                        blockToPlace = Blocks.BLACKSTONE.defaultBlockState();
                    }
                }
            } else if (y <= actualSurfaceHeight) {
                // Surface terrain (y >= 0)
                boolean isCave = shouldGenerateSurfaceCave(worldX, y, worldZ, actualSurfaceHeight);

                if (isCave && y < actualSurfaceHeight - 5) {
                    blockToPlace = Blocks.AIR.defaultBlockState();
                } else {
                    // Surface terrain materials
                    double materialNoise = simplexNoise(worldX * 0.08, y * 0.08, worldZ * 0.08, seed + 13000);

                    if (y > surfaceHeight * 0.7 && materialNoise > 0.3) {
                        blockToPlace = Blocks.BLACKSTONE.defaultBlockState();
                    } else if (materialNoise > 0.5) {
                        blockToPlace = Blocks.BASALT.defaultBlockState();
                    } else {
                        blockToPlace = Blocks.OBSIDIAN.defaultBlockState();
                    }
                }
            } else if (lakeData.isLake && y <= surfaceHeight && y > actualSurfaceHeight) {
                // Lake lava fill
                blockToPlace = Blocks.LAVA.defaultBlockState();
            }

            chunk.setBlockState(pos, blockToPlace, false);
        }

        // Surface features (much more conservative)
        if (!lakeData.isLake && surfaceHeight + 1 <= maxY && random.nextFloat() < 0.0005f) {
            chunk.setBlockState(new BlockPos(x, surfaceHeight + 1, z), Blocks.LAVA.defaultBlockState(), false);
        }
    }

    private boolean shouldGenerateDeepCave(int x, int y, int z) {
        // Deep cave system below y=0
        double caveNoise1 = simplexNoise(x * 0.02, y * 0.02, z * 0.02, seed + 14000);
        double caveNoise2 = simplexNoise(x * 0.04, y * 0.04, z * 0.04, seed + 15000) * 0.6;
        double caveNoise3 = simplexNoise(x * 0.08, y * 0.08, z * 0.08, seed + 16000) * 0.3;

        double combinedNoise = caveNoise1 + caveNoise2 + caveNoise3;
        return combinedNoise > 0.5;
    }

    private boolean shouldGenerateSurfaceCave(int x, int y, int z, int surfaceHeight) {
        if (y > surfaceHeight - 8) return false;

        double caveNoise1 = simplexNoise(x * 0.025, y * 0.025, z * 0.025, seed + 17000);
        double caveNoise2 = simplexNoise(x * 0.05, y * 0.05, z * 0.05, seed + 18000) * 0.5;

        double combinedNoise = caveNoise1 + caveNoise2;
        return combinedNoise > 0.4;
    }

    // Utility methods for noise generation
    private double ridgedNoise(double x, double z, long seed, int octaves) {
        double value = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            double noise = Math.abs(simplexNoise(x * frequency, z * frequency, seed + i * 1000));
            noise = 1.0 - noise;
            noise = noise * noise;

            value += noise * amplitude;
            maxValue += amplitude;

            amplitude *= 0.5;
            frequency *= 2.0;
        }

        return value / maxValue;
    }

    private double fractalBrownianMotion(int x, int z, int octaves, double frequency, double persistence, long seed) {
        double value = 0;
        double amplitude = 1;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            value += simplexNoise(x * frequency, z * frequency, seed + i * 1000) * amplitude;
            maxValue += amplitude;

            amplitude *= persistence;
            frequency *= 2.0;
        }

        return value / maxValue;
    }

    private double cellularNoise(int x, int z, long seed) {
        int cellSize = 32;
        int cellX = x / cellSize;
        int cellZ = z / cellSize;

        double minDist = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cellSeed = hash2D(cellX + dx, cellZ + dz, seed);
                RandomSource random = RandomSource.create(cellSeed);

                double pointX = (cellX + dx) * cellSize + random.nextDouble() * cellSize;
                double pointZ = (cellZ + dz) * cellSize + random.nextDouble() * cellSize;

                double dist = Math.sqrt((x - pointX) * (x - pointX) + (z - pointZ) * (z - pointZ));
                minDist = Math.min(minDist, dist);
            }
        }

        double normalizedDist = minDist / cellSize;
        return Math.cos(normalizedDist * Math.PI * 2) * 0.5 + 0.5;
    }

    // Interpolation methods
    private double bilinearInterpolate(double[][] samples, int x, int z, int sampleRate) {
        double fx = (double) x / sampleRate;
        double fz = (double) z / sampleRate;

        int x1 = (int) Math.floor(fx);
        int z1 = (int) Math.floor(fz);
        int x2 = Math.min(x1 + 1, samples.length - 1);
        int z2 = Math.min(z1 + 1, samples[0].length - 1);

        double dx = fx - x1;
        double dz = fz - z1;

        double c00 = samples[x1][z1];
        double c10 = samples[x2][z1];
        double c01 = samples[x1][z2];
        double c11 = samples[x2][z2];

        double c0 = c00 * (1 - dx) + c10 * dx;
        double c1 = c01 * (1 - dx) + c11 * dx;

        return c0 * (1 - dz) + c1 * dz;
    }

    private LakeData bilinearInterpolateLakeData(LakeData[][] samples, int x, int z, int sampleRate) {
        double fx = (double) x / sampleRate;
        double fz = (double) z / sampleRate;

        int x1 = (int) Math.floor(fx);
        int z1 = (int) Math.floor(fz);
        int x2 = Math.min(x1 + 1, samples.length - 1);
        int z2 = Math.min(z1 + 1, samples[0].length - 1);

        // For lake data, we'll use the closest sample
        LakeData closest = samples[x1][z1];

        // If any nearby sample is a lake, consider this position a lake too
        if (!closest.isLake) {
            if (samples[x2][z1].isLake) closest = samples[x2][z1];
            else if (samples[x1][z2].isLake) closest = samples[x1][z2];
            else if (samples[x2][z2].isLake) closest = samples[x2][z2];
        }

        return closest;
    }

    // Hash and noise functions
    private long hash2D(int x, int z, long seed) {
        long hash = seed;
        hash ^= x * 374761393L + z * 668265263L;
        hash = (hash ^ (hash >> 13)) * 1274126177L;
        return hash ^ (hash >> 16);
    }

    private double simplexNoise(double x, double z, long seed) {
        return simplexNoise(x, 0, z, seed);
    }

    private double simplexNoise(double x, double y, double z, long seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);

        double xf = x - xi;
        double yf = y - yi;
        double zf = z - zi;

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int a = hash3D(xi, yi, zi, seed);
        int b = hash3D(xi + 1, yi, zi, seed);
        int c = hash3D(xi, yi + 1, zi, seed);
        int d = hash3D(xi + 1, yi + 1, zi, seed);
        int e = hash3D(xi, yi, zi + 1, seed);
        int f = hash3D(xi + 1, yi, zi + 1, seed);
        int g = hash3D(xi, yi + 1, zi + 1, seed);
        int h = hash3D(xi + 1, yi + 1, zi + 1, seed);

        double i1 = lerp(u, grad(a, xf, yf, zf), grad(b, xf - 1, yf, zf));
        double i2 = lerp(u, grad(c, xf, yf - 1, zf), grad(d, xf - 1, yf - 1, zf));
        double i3 = lerp(u, grad(e, xf, yf, zf - 1), grad(f, xf - 1, yf, zf - 1));
        double i4 = lerp(u, grad(g, xf, yf - 1, zf - 1), grad(h, xf - 1, yf - 1, zf - 1));

        double j1 = lerp(v, i1, i2);
        double j2 = lerp(v, i3, i4);

        return lerp(w, j1, j2);
    }

    private int hash3D(int x, int y, int z, long seed) {
        long hash = seed;
        hash ^= x * 374761393L + y * 668265263L + z * 1610612741L;
        hash = (hash ^ (hash >> 13)) * 1274126177L;
        return (int) (hash ^ (hash >> 16)) & 255;
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getSeaLevel() {
        return 0; // Sea level at 0 for the new height system
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return SURFACE_BASE + 50; // Spawn above the base surface
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState randomState) {
        return (int) Mth.clamp(generateComplexTerrain(x, z), SURFACE_BASE, MAX_MOUNTAIN_HEIGHT);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int height = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        BlockState[] states = new BlockState[level.getHeight()];

        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y <= height && y >= 0) {
                states[i] = Blocks.OBSIDIAN.defaultBlockState();
            } else if (y < 0) {
                states[i] = Blocks.BLACKSTONE.defaultBlockState();
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }

        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("VoidDim: Extreme Heights (-1024 to 1024)");
        info.add("Surface: Y100-1000, Caves: Y0 to -1024");
    }

    @Override
    public int getMinY() {
        return MIN_WORLD_HEIGHT;
    }

    @Override
    public int getGenDepth() {
        return MAX_WORLD_HEIGHT - MIN_WORLD_HEIGHT;
    }
}