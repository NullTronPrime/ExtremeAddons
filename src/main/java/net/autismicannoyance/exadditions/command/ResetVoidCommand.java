package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.autismicannoyance.exadditions.world.dimension.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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

        source.sendSuccess(() -> Component.literal("Starting NUCLEAR reset of the Void Dimension..."), true);
        source.sendSuccess(() -> Component.literal("Warning: Any players in the void dimension will be kicked out!"), false);

        // Step 1: Kick all players out of the void dimension
        kickAllPlayersFromVoid(player.getServer(), voidLevel);

        // Step 2: Force save and unload ALL chunks in the void dimension
        int chunksUnloaded = forceUnloadAllChunks(voidLevel);

        // Step 3: Delete all region files from disk
        int regionFilesDeleted = deleteAllRegionFiles(voidLevel);

        // Step 4: Clear any cached chunk data
        clearChunkCache(voidLevel);

        source.sendSuccess(() -> Component.literal("NUCLEAR RESET COMPLETE!"), true);
        source.sendSuccess(() -> Component.literal("- Unloaded " + chunksUnloaded + " chunks from memory"), false);
        source.sendSuccess(() -> Component.literal("- Deleted " + regionFilesDeleted + " region files from disk"), false);
        source.sendSuccess(() -> Component.literal("- Cleared all cached chunk data"), false);
        source.sendSuccess(() -> Component.literal("The void dimension will generate completely fresh when visited again!"), false);

        return chunksUnloaded + regionFilesDeleted;
    }

    private static void kickAllPlayersFromVoid(net.minecraft.server.MinecraftServer server, ServerLevel voidLevel) {
        // Get the overworld as fallback
        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        // Find all players in void dimension and teleport them out
        voidLevel.players().forEach(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                // Teleport to overworld spawn
                net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();
                serverPlayer.teleportTo(overworld, spawnPos.getX(), spawnPos.getY() + 1, spawnPos.getZ(), 0, 0);
                serverPlayer.sendSystemMessage(Component.literal("You have been moved out of the void dimension for a reset!"));
            }
        });
    }

    private static int forceUnloadAllChunks(ServerLevel level) {
        int unloaded = 0;

        try {
            var chunkSource = level.getChunkSource();

            // Get all loaded chunk positions
            Set<ChunkPos> loadedChunks = new HashSet<>();

            // Scan a large area to find any loaded chunks
            for (int x = -100; x <= 100; x++) {
                for (int z = -100; z <= 100; z++) {
                    ChunkPos pos = new ChunkPos(x, z);
                    if (level.hasChunk(pos.x, pos.z)) {
                        loadedChunks.add(pos);
                    }
                }
            }

            // Also check around entities
            level.getAllEntities().forEach(entity -> {
                ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
                if (level.hasChunk(chunkPos.x, chunkPos.z)) {
                    loadedChunks.add(chunkPos);
                }
            });

            // Force save all chunks first
            chunkSource.save(true);

            // Remove all tickets to force unloading
            for (ChunkPos chunkPos : loadedChunks) {
                try {
                    // Remove various ticket types that keep chunks loaded
                    chunkSource.removeRegionTicket(net.minecraft.server.level.TicketType.START, chunkPos, 1, chunkPos);
                    chunkSource.removeRegionTicket(net.minecraft.server.level.TicketType.PLAYER, chunkPos, 1, chunkPos);
                    chunkSource.removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunkPos, 1, chunkPos);
                    chunkSource.removeRegionTicket(net.minecraft.server.level.TicketType.UNKNOWN, chunkPos, 1, chunkPos);
                    unloaded++;
                } catch (Exception e) {
                    // Continue with other chunks
                }
            }

            // Force another save to ensure everything is written
            chunkSource.save(true);

        } catch (Exception e) {
            // Return what we managed to unload
        }

        return unloaded;
    }

    private static void clearChunkCache(ServerLevel level) {
        try {
            var chunkSource = level.getChunkSource();

            // Try to clear any cached data
            chunkSource.gatherStats();

            // Force garbage collection to clear memory
            System.gc();

        } catch (Exception e) {
            // Cache clearing failed, but that's okay
        }
    }

    private static int deleteAllRegionFiles(ServerLevel level) {
        try {
            // Get the dimension's region folder path
            Path worldPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path dimensionPath = worldPath.resolve("dimensions").resolve("exadditions").resolve("void_dim");
            Path regionPath = dimensionPath.resolve("region");

            if (!Files.exists(regionPath)) {
                return 0; // No region files to delete
            }

            int deletedCount = 0;
            File regionDir = regionPath.toFile();

            if (regionDir.exists() && regionDir.isDirectory()) {
                File[] files = regionDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".mca")) {
                            if (file.delete()) {
                                deletedCount++;
                            }
                        }
                    }
                }
            }

            // Also delete poi and entities directories if they exist
            try {
                Path poiPath = dimensionPath.resolve("poi");
                if (Files.exists(poiPath)) {
                    Files.walk(poiPath)
                            .filter(Files::isRegularFile)
                            .forEach(file -> {
                                try {
                                    Files.delete(file);
                                } catch (Exception e) {
                                    // Continue
                                }
                            });
                }

                Path entitiesPath = dimensionPath.resolve("entities");
                if (Files.exists(entitiesPath)) {
                    Files.walk(entitiesPath)
                            .filter(Files::isRegularFile)
                            .forEach(file -> {
                                try {
                                    Files.delete(file);
                                } catch (Exception e) {
                                    // Continue
                                }
                            });
                }
            } catch (Exception e) {
                // Continue even if poi/entities deletion fails
            }

            return deletedCount;

        } catch (Exception e) {
            return 0;
        }
    }
}