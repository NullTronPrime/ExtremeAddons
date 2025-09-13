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
    private static final int SAMPLE_RATE = 4;
    private static final int BASE_HEIGHT = 80;
    private static final int LAKE_SAMPLE_RATE = 2;

    // Lake generation parameters
    private static final double LAKE_THRESHOLD = 0.2;
    private static final int LAKE_DEPTH = 6; // Make lakes deeper and more visible

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

        // Generate base terrain height samples
        double[][] baseHeightSamples = generateBaseHeightSamples(chunkPos);

        // Generate lake mask - which areas should be lakes
        boolean[][] lakeMask = generateLakeMask(chunkPos);

        // Generate final height incorporating lakes
        double[][] finalHeights = generateFinalHeights(baseHeightSamples, lakeMask, chunkPos);

        // Generate terrain for each column
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double terrainHeight = bilinearInterpolate(finalHeights, x, z, SAMPLE_RATE);
                boolean isLake = bilinearInterpolateLakeMask(lakeMask, x, z, LAKE_SAMPLE_RATE);

                generateColumn(chunk, x, z, (int) terrainHeight, isLake, minY, maxY, chunkPos);
            }
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    /**
     * Generates the base terrain without lakes
     */
    private double[][] generateBaseHeightSamples(ChunkPos chunkPos) {
        int samples = (16 / SAMPLE_RATE) + 1;
        double[][] heights = new double[samples][samples];

        for (int i = 0; i < samples; i++) {
            for (int j = 0; j < samples; j++) {
                int worldX = chunkPos.getMinBlockX() + (i * SAMPLE_RATE);
                int worldZ = chunkPos.getMinBlockZ() + (j * SAMPLE_RATE);

                double baseHeight = BASE_HEIGHT;
                double largeScale = simplexNoise(worldX * 0.005, worldZ * 0.005, seed) * 40;
                double mediumScale = simplexNoise(worldX * 0.02, worldZ * 0.02, seed + 1000) * 25;
                double sharpPeaks = Math.abs(simplexNoise(worldX * 0.08, worldZ * 0.08, seed + 2000)) * 30;

                heights[i][j] = baseHeight + largeScale + mediumScale + sharpPeaks;
                heights[i][j] = Mth.clamp(heights[i][j], 20, 200);
            }
        }
        return heights;
    }

    /**
     * Generates a mask indicating where lakes should be
     */
    private boolean[][] generateLakeMask(ChunkPos chunkPos) {
        int samples = (16 / LAKE_SAMPLE_RATE) + 1;
        boolean[][] lakeMask = new boolean[samples][samples];

        for (int i = 0; i < samples; i++) {
            for (int j = 0; j < samples; j++) {
                int worldX = chunkPos.getMinBlockX() + (i * LAKE_SAMPLE_RATE);
                int worldZ = chunkPos.getMinBlockZ() + (j * LAKE_SAMPLE_RATE);

                // Large scale lake placement - bigger lakes
                double lakeNoise = simplexNoise(worldX * 0.006, worldZ * 0.006, seed + 5000);

                // Shape variation for more organic lakes
                double shapeNoise = simplexNoise(worldX * 0.012, worldZ * 0.012, seed + 6000);

                // Combine for final lake determination
                double combinedNoise = (lakeNoise + shapeNoise * 0.3) / 1.3;

                lakeMask[i][j] = combinedNoise > LAKE_THRESHOLD;
            }
        }
        return lakeMask;
    }

    /**
     * Generates final terrain heights, carving out lake basins where needed
     */
    private double[][] generateFinalHeights(double[][] baseHeights, boolean[][] lakeMask, ChunkPos chunkPos) {
        int samples = baseHeights.length;
        double[][] finalHeights = new double[samples][samples];

        for (int i = 0; i < samples; i++) {
            for (int j = 0; j < samples; j++) {
                double baseHeight = baseHeights[i][j];

                // Check if this point should be a lake
                boolean shouldBeLake = false;
                if (i < lakeMask.length && j < lakeMask[0].length) {
                    shouldBeLake = lakeMask[i][j];
                }

                if (shouldBeLake) {
                    // Carve out a lake basin - lower the terrain
                    finalHeights[i][j] = baseHeight - LAKE_DEPTH;
                } else {
                    // Normal terrain height
                    finalHeights[i][j] = baseHeight;
                }
            }
        }

        return finalHeights;
    }

    /**
     * Performs bilinear interpolation on height samples
     */
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

    /**
     * Performs bilinear interpolation on lake mask
     */
    private boolean bilinearInterpolateLakeMask(boolean[][] samples, int x, int z, int sampleRate) {
        double fx = (double) x / sampleRate;
        double fz = (double) z / sampleRate;

        int x1 = (int) Math.floor(fx);
        int z1 = (int) Math.floor(fz);
        int x2 = Math.min(x1 + 1, samples.length - 1);
        int z2 = Math.min(z1 + 1, samples[0].length - 1);

        // If any of the 4 corner samples are lake, consider this a lake area
        boolean c00 = samples[x1][z1];
        boolean c10 = samples[x2][z1];
        boolean c01 = samples[x1][z2];
        boolean c11 = samples[x2][z2];

        // Lake if any corner is lake (this creates slightly larger, more connected lakes)
        return c00 || c10 || c01 || c11;
    }

    /**
     * Generates a single column of terrain
     */
    private void generateColumn(ChunkAccess chunk, int x, int z, int terrainHeight, boolean isLake,
                                int minY, int maxY, ChunkPos chunkPos) {
        int worldX = chunkPos.getMinBlockX() + x;
        int worldZ = chunkPos.getMinBlockZ() + z;
        RandomSource random = RandomSource.create(hash2D(worldX, worldZ, seed));

        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState blockToPlace = Blocks.AIR.defaultBlockState();

            if (y <= terrainHeight) {
                if (isLake) {
                    // In a lake basin - fill with lava above the lake floor
                    int lakeFloor = terrainHeight;
                    int lakeTop = lakeFloor + LAKE_DEPTH; // Fill the carved basin with lava

                    if (y <= lakeFloor) {
                        // Lake floor - solid obsidian
                        blockToPlace = Blocks.OBSIDIAN.defaultBlockState();
                    } else if (y <= lakeTop) {
                        // Lake water level - fill with lava
                        blockToPlace = Blocks.LAVA.defaultBlockState();
                    }
                } else {
                    // Regular terrain generation
                    boolean isCave = shouldGenerateCave(worldX, y, worldZ, terrainHeight);

                    if (isCave && y < terrainHeight - 2) {
                        blockToPlace = Blocks.AIR.defaultBlockState();
                    } else {
                        blockToPlace = Blocks.OBSIDIAN.defaultBlockState();
                    }
                }
            }

            chunk.setBlockState(pos, blockToPlace, false);
        }

        // Occasional surface lava drops (only on non-lake terrain)
        if (!isLake && random.nextFloat() < 0.003f && terrainHeight + 1 <= maxY) {
            chunk.setBlockState(new BlockPos(x, terrainHeight + 1, z), Blocks.LAVA.defaultBlockState(), false);
        }
    }

    private boolean shouldGenerateCave(int x, int y, int z, int terrainHeight) {
        if (y > terrainHeight - 5) return false;

        double caveNoise1 = simplexNoise(x * 0.03, y * 0.03, z * 0.03, seed + 3000);
        double caveNoise2 = simplexNoise(x * 0.05, y * 0.05, z * 0.05, seed + 4000);

        return caveNoise1 > 0.4 && caveNoise2 > 0.3;
    }

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
    public int getSpawnHeight(LevelHeightAccessor level) {
        return BASE_HEIGHT + 20;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState randomState) {
        double largeScale = simplexNoise(x * 0.005, z * 0.005, seed) * 40;
        double mediumScale = simplexNoise(x * 0.02, z * 0.02, seed + 1000) * 25;
        double sharpPeaks = Math.abs(simplexNoise(x * 0.08, z * 0.08, seed + 2000)) * 30;

        return (int) Mth.clamp(BASE_HEIGHT + largeScale + mediumScale + sharpPeaks, 20, 200);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int height = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        BlockState[] states = new BlockState[level.getHeight()];

        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y <= height) {
                states[i] = Blocks.OBSIDIAN.defaultBlockState();
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }

        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("VoidDim Generator with Proper Lava Lake Basins");
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getGenDepth() {
        return 256; // Standard generation depth
    }
}