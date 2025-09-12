package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.autismicannoyance.exadditions.world.dimension.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public class ResetVoidCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("resetvoid")
                .requires(source -> source.hasPermission(2)) // Requires OP level 2
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10))
                        .executes(ResetVoidCommand::executeWithRadius))
                .executes(ResetVoidCommand::executeDefault));
    }

    private static int executeDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return execute(context, 3); // Default radius of 3 chunks
    }

    private static int executeWithRadius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        return execute(context, radius);
    }

    private static int execute(CommandContext<CommandSourceStack> context, int radius) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        // Check if command is run by a player
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        ServerLevel level = player.serverLevel();

        // Check if player is in void dimension
        if (level.dimension() != ModDimensions.VOID_DIM_LEVEL) {
            source.sendFailure(Component.literal("You must be in the Void Dimension to use this command"));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();
        ChunkPos centerChunk = new ChunkPos(playerPos);

        int chunksReset = 0;

        // Clear chunks in radius around player
        for (int chunkX = -radius; chunkX <= radius; chunkX++) {
            for (int chunkZ = -radius; chunkZ <= radius; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(centerChunk.x + chunkX, centerChunk.z + chunkZ);

                if (clearChunk(level, chunkPos)) {
                    chunksReset++;
                }
            }
        }

        // Make chunksReset effectively final for lambda
        final int finalChunksReset = chunksReset;
        source.sendSuccess(() -> Component.literal("Cleared " + finalChunksReset + " chunks in a " + radius + " chunk radius. Move away and come back to see regenerated terrain."), true);

        return chunksReset;
    }

    private static boolean clearChunk(ServerLevel level, ChunkPos chunkPos) {
        try {
            // Get the chunk bounds
            int minX = chunkPos.getMinBlockX();
            int maxX = chunkPos.getMaxBlockX();
            int minZ = chunkPos.getMinBlockZ();
            int maxZ = chunkPos.getMaxBlockZ();

            // Clear the entire chunk area
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16); // Update + no block update
                    }
                }
            }

            // Force chunk to be marked as needing regeneration
            if (level.hasChunk(chunkPos.x, chunkPos.z)) {
                var chunk = level.getChunk(chunkPos.x, chunkPos.z);
                chunk.setUnsaved(true);

                // Mark all chunk sections as empty/needing regeneration
                for (var section : chunk.getSections()) {
                    section.recalcBlockCounts();
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}