package net.autismicannoyance.exadditions.world.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
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

public class ArcanePouchChunkGenerator extends ChunkGenerator {
    public static final Codec<ArcanePouchChunkGenerator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    Codec.LONG.fieldOf("seed").forGetter(gen -> gen.seed)
            ).apply(instance, ArcanePouchChunkGenerator::new)
    );

    private final long seed;
    private static final int PLATFORM_Y = 64;
    private static final int RADIUS = 8;

    public ArcanePouchChunkGenerator(BiomeSource biomeSource, long seed) {
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
        // DO NOTHING - no carving
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        // FILL EVERYTHING WITH AIR FIRST
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                }
            }
        }

        // THEN place ONLY the platform at Y=64
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;
                int distSq = worldX * worldX + worldZ * worldZ;

                if (distSq <= RADIUS * RADIUS) {
                    BlockPos platformPos = new BlockPos(x, PLATFORM_Y, z);

                    if (worldX == 0 && worldZ == 0) {
                        chunk.setBlockState(platformPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), false);
                    } else {
                        chunk.setBlockState(platformPos, Blocks.OBSIDIAN.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // NO MOBS
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return PLATFORM_Y + 3;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        // CRITICAL: Return chunk immediately, don't fill with noise
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState randomState) {
        int distSq = x * x + z * z;
        // Only platform exists at Y=64, everything else is void
        return (distSq <= RADIUS * RADIUS) ? PLATFORM_Y : level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[level.getHeight()];
        int distSq = x * x + z * z;
        boolean isInCircle = distSq <= RADIUS * RADIUS;

        // Fill EVERYTHING with air
        for (int i = 0; i < states.length; i++) {
            states[i] = Blocks.AIR.defaultBlockState();
        }

        // ONLY place platform at Y=64
        if (isInCircle) {
            int platformIndex = PLATFORM_Y - level.getMinBuildHeight();
            if (platformIndex >= 0 && platformIndex < states.length) {
                states[platformIndex] = (x == 0 && z == 0) ?
                        Blocks.DIAMOND_BLOCK.defaultBlockState() :
                        Blocks.OBSIDIAN.defaultBlockState();
            }
        }

        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Arcane Pouch: Pure void + platform at Y=64");
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }
}