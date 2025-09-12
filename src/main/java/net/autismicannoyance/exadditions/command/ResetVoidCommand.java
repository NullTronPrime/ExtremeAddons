package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.autismicannoyance.exadditions.world.dimension.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResetVoidCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("resetvoid")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2
                .executes(ResetVoidCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Check if command is run by a player
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        // Check if player is NOT in void dimension
        if (player.serverLevel().dimension() == ModDimensions.VOID_DIM_LEVEL) {
            source.sendFailure(Component.literal("You cannot use this command while in the Void Dimension. Please go to another dimension first."));
            return 0;
        }

        // Get the void dimension
        ServerLevel voidLevel = player.getServer().getLevel(ModDimensions.VOID_DIM_LEVEL);
        if (voidLevel == null) {
            source.sendFailure(Component.literal("Void Dimension not found or not loaded"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Starting complete reset of ALL chunks in the Void Dimension..."), true);

        // Get all chunks (both loaded and saved to disk)
        Set<ChunkPos> allChunks = new HashSet<>();

        // First, get all currently loaded chunks by scanning a large area
        try {
            // Scan a large area around spawn to find loaded chunks
            ChunkPos spawnChunk = new ChunkPos(voidLevel.getSharedSpawnPos());
            for (int x = -50; x <= 50; x++) {
                for (int z = -50; z <= 50; z++) {
                    ChunkPos checkPos = new ChunkPos(spawnChunk.x + x, spawnChunk.z + z);
                    if (voidLevel.hasChunk(checkPos.x, checkPos.z)) {
                        allChunks.add(checkPos);
                    }
                }
            }

            // Also add chunks that contain entities
            voidLevel.getAllEntities().forEach(entity -> {
                ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
                if (voidLevel.hasChunk(chunkPos.x, chunkPos.z)) {
                    allChunks.add(chunkPos);
                }
            });

        } catch (Exception e) {
            source.sendFailure(Component.literal("Error finding loaded chunks: " + e.getMessage()));
            return 0;
        }

        // Get all saved chunks from region files
        try {
            Set<ChunkPos> savedChunks = getAllSavedChunks(voidLevel);
            allChunks.addAll(savedChunks);

            if (!savedChunks.isEmpty()) {
                int savedCount = savedChunks.size();
                source.sendSuccess(() -> Component.literal("Found " + savedCount + " saved chunks on disk"), false);
            }

        } catch (Exception e) {
            source.sendSuccess(() -> Component.literal("Warning: Could not scan region files - will only reset loaded chunks"), false);
        }

        if (allChunks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No chunks found in the Void Dimension to reset"), true);
            return 0;
        }

        int totalCount = allChunks.size();
        source.sendSuccess(() -> Component.literal("Found " + totalCount + " total chunks to reset. Processing..."), false);

        // Process chunks - separate the counting from the lambda usage
        ResetResults results = processChunks(voidLevel, allChunks);

        // Now use the final results in lambdas
        source.sendSuccess(() -> Component.literal("Reset complete! Cleared " + results.chunksCleared() + " loaded chunks and deleted " + results.regionFilesDeleted() + " region files. All areas will regenerate with fresh terrain when visited."), true);

        return results.totalProcessed();
    }

    private static ResetResults processChunks(ServerLevel voidLevel, Set<ChunkPos> allChunks) {
        // Clear all loaded chunks
        List<ChunkPos> loadedChunks = new ArrayList<>();
        for (ChunkPos chunkPos : allChunks) {
            if (voidLevel.hasChunk(chunkPos.x, chunkPos.z)) {
                loadedChunks.add(chunkPos);
            }
        }

        int chunksCleared = 0;
        for (ChunkPos chunkPos : loadedChunks) {
            if (clearLoadedChunk(voidLevel, chunkPos)) {
                chunksCleared++;
            }
        }

        // Delete region files for complete reset
        int regionFilesDeleted = 0;
        try {
            regionFilesDeleted = deleteAllRegionFiles(voidLevel);
        } catch (Exception e) {
            // Handle silently for now
        }

        return new ResetResults(chunksCleared, regionFilesDeleted, chunksCleared + regionFilesDeleted);
    }

    // Simple record to hold results
    private record ResetResults(int chunksCleared, int regionFilesDeleted, int totalProcessed) {}

    private static Set<ChunkPos> getAllSavedChunks(ServerLevel level) throws Exception {
        Set<ChunkPos> savedChunks = new HashSet<>();

        // Get the dimension's region folder path
        Path worldPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path dimensionPath = worldPath.resolve("dimensions").resolve("exadditions").resolve("void_dim");
        Path regionPath = dimensionPath.resolve("region");

        if (!Files.exists(regionPath)) {
            return savedChunks; // No region files exist yet
        }

        // Pattern to match region file names (r.x.z.mca)
        Pattern regionPattern = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

        // Scan all region files
        Files.list(regionPath).forEach(file -> {
            String fileName = file.getFileName().toString();
            Matcher matcher = regionPattern.matcher(fileName);

            if (matcher.matches()) {
                int regionX = Integer.parseInt(matcher.group(1));
                int regionZ = Integer.parseInt(matcher.group(2));

                // Each region file contains 32x32 chunks
                for (int chunkX = regionX * 32; chunkX < (regionX + 1) * 32; chunkX++) {
                    for (int chunkZ = regionZ * 32; chunkZ < (regionZ + 1) * 32; chunkZ++) {
                        savedChunks.add(new ChunkPos(chunkX, chunkZ));
                    }
                }
            }
        });

        return savedChunks;
    }

    private static boolean clearLoadedChunk(ServerLevel level, ChunkPos chunkPos) {
        try {
            if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                return false;
            }

            // Get the chunk bounds
            int minX = chunkPos.getMinBlockX();
            int maxX = chunkPos.getMaxBlockX();
            int minZ = chunkPos.getMinBlockZ();
            int maxZ = chunkPos.getMaxBlockZ();

            // Clear absolutely everything in the chunk
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }

            // Get the chunk and mark it for regeneration
            var chunk = level.getChunk(chunkPos.x, chunkPos.z);
            chunk.setUnsaved(true);

            // Reset all sections in the chunk
            for (var section : chunk.getSections()) {
                section.recalcBlockCounts();
            }

            // Clear any entities in the chunk (but not players)
            try {
                AABB chunkBounds = new AABB(minX, level.getMinBuildHeight(), minZ,
                        maxX + 1, level.getMaxBuildHeight(), maxZ + 1);

                level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, chunkBounds)
                        .forEach(entity -> {
                            if (!(entity instanceof ServerPlayer)) {
                                entity.discard();
                            }
                        });
            } catch (Exception e) {
                // Entity clearing failed, but block clearing succeeded
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private static int deleteAllRegionFiles(ServerLevel level) throws Exception {
        // Get the dimension's region folder path
        Path worldPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path dimensionPath = worldPath.resolve("dimensions").resolve("exadditions").resolve("void_dim");
        Path regionPath = dimensionPath.resolve("region");

        if (!Files.exists(regionPath)) {
            return 0; // No region files to delete
        }

        int deletedCount = 0;

        // Delete all .mca files in the region directory
        try {
            Files.list(regionPath)
                    .filter(file -> file.getFileName().toString().endsWith(".mca"))
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (Exception e) {
                            // Continue deleting other files even if one fails
                        }
                    });

            // Count how many were deleted by checking what's left
            long remainingFiles = Files.list(regionPath)
                    .filter(file -> file.getFileName().toString().endsWith(".mca"))
                    .count();

            // If we had files before and none now, we deleted them all
            deletedCount = (int) (Files.list(regionPath.getParent().resolve("region_backup_temp")).count() - remainingFiles);

        } catch (Exception e) {
            // Simpler approach - just try to delete common region files
            Pattern regionPattern = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

            File regionDir = regionPath.toFile();
            if (regionDir.exists() && regionDir.isDirectory()) {
                File[] files = regionDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (regionPattern.matcher(file.getName()).matches()) {
                            if (file.delete()) {
                                deletedCount++;
                            }
                        }
                    }
                }
            }
        }

        return deletedCount;
    }
}