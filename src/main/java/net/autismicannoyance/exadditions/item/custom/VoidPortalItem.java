package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.world.dimension.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VoidPortalItem extends Item {
    public VoidPortalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Check if player is in void dimension
            if (level.dimension() == ModDimensions.VOID_DIM_LEVEL) {
                // Teleport back to overworld at corresponding coordinates
                ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
                if (overworld != null) {
                    Vec3 currentPos = player.position();
                    BlockPos targetPos = findSafeOverworldPosition(overworld,
                            (int) currentPos.x, (int) currentPos.z);

                    serverPlayer.teleportTo(overworld,
                            targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("Returned to the Overworld at ("
                            + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ")!"));
                }
            } else {
                // Teleport to void dimension at corresponding coordinates
                ServerLevel voidDim = level.getServer().getLevel(ModDimensions.VOID_DIM_LEVEL);
                if (voidDim != null) {
                    Vec3 currentPos = player.position();
                    BlockPos targetPos = findSafeVoidPosition(voidDim,
                            (int) currentPos.x, (int) currentPos.z);

                    serverPlayer.teleportTo(voidDim,
                            targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("Entered the Void Dimension at ("
                            + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ")!"));
                }
            }

            // Damage the item (if it has durability)
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isDamageableItem()) {
                stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    /**
     * Finds a safe position in the overworld, prioritizing the same coordinates
     * but ensuring the player spawns on solid ground with air above
     */
    private BlockPos findSafeOverworldPosition(ServerLevel overworld, int targetX, int targetZ) {
        // Start searching from a reasonable height
        int startY = overworld.getHeight() - 1;

        // First, try to find the surface at the exact coordinates
        BlockPos exactPos = findSurfacePosition(overworld, targetX, targetZ, startY);
        if (exactPos != null) {
            return exactPos;
        }

        // If exact coordinates aren't safe, search in expanding rings
        for (int radius = 1; radius <= 32; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check positions on the edge of the current radius
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        BlockPos safePos = findSurfacePosition(overworld, targetX + dx, targetZ + dz, startY);
                        if (safePos != null) {
                            return safePos;
                        }
                    }
                }
            }
        }

        // Fallback to world spawn if nothing else works
        BlockPos worldSpawn = overworld.getSharedSpawnPos();
        return new BlockPos(worldSpawn.getX(), worldSpawn.getY() + 1, worldSpawn.getZ());
    }

    /**
     * Finds a safe position in the void dimension
     */
    private BlockPos findSafeVoidPosition(ServerLevel voidLevel, int targetX, int targetZ) {
        // First, try to find a safe spot at the exact coordinates
        BlockPos exactPos = findVoidSurfacePosition(voidLevel, targetX, targetZ);
        if (exactPos != null) {
            return exactPos;
        }

        // If exact coordinates aren't safe, search in expanding rings
        for (int radius = 1; radius <= 32; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check positions on the edge of the current radius
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        BlockPos safePos = findVoidSurfacePosition(voidLevel, targetX + dx, targetZ + dz);
                        if (safePos != null) {
                            return safePos;
                        }
                    }
                }
            }
        }

        // Fallback: find any solid surface between Y=100 and Y=200
        return findFallbackVoidPosition(voidLevel, targetX, targetZ);
    }

    /**
     * Finds a safe surface position at specific coordinates in the overworld
     */
    private BlockPos findSurfacePosition(ServerLevel level, int x, int z, int startY) {
        // Scan downward from the top to find solid ground
        for (int y = startY; y >= level.getMinBuildHeight(); y--) {
            BlockPos groundPos = new BlockPos(x, y, z);
            BlockPos airPos1 = new BlockPos(x, y + 1, z);
            BlockPos airPos2 = new BlockPos(x, y + 2, z);

            // Check if we have solid ground with 2 air blocks above
            if (isSafeGround(level, groundPos) &&
                    isPassable(level, airPos1) &&
                    isPassable(level, airPos2)) {
                return airPos1; // Return the position where the player's feet will be
            }
        }
        return null;
    }

    /**
     * Finds a safe surface position in the void dimension
     */
    private BlockPos findVoidSurfacePosition(ServerLevel voidLevel, int x, int z) {
        // In void dimension, scan from Y=1000 down to Y=100 for surface
        for (int y = 1000; y >= 100; y--) {
            BlockPos groundPos = new BlockPos(x, y, z);
            BlockPos airPos1 = new BlockPos(x, y + 1, z);
            BlockPos airPos2 = new BlockPos(x, y + 2, z);

            // Check if we have solid ground with air above (avoid lava)
            if (isSafeVoidGround(voidLevel, groundPos) &&
                    isPassable(voidLevel, airPos1) &&
                    isPassable(voidLevel, airPos2) &&
                    !isLava(voidLevel, airPos1) &&
                    !isLava(voidLevel, airPos2)) {
                return airPos1;
            }
        }
        return null;
    }

    /**
     * Fallback method to find any reasonably safe void position
     */
    private BlockPos findFallbackVoidPosition(ServerLevel voidLevel, int centerX, int centerZ) {
        // Search in a wider area for any safe spot
        for (int radius = 0; radius <= 64; radius += 8) {
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                int x = centerX + (int) (radius * Math.cos(radians));
                int z = centerZ + (int) (radius * Math.sin(radians));

                BlockPos safePos = findVoidSurfacePosition(voidLevel, x, z);
                if (safePos != null) {
                    return safePos;
                }
            }
        }

        // Ultimate fallback: create a safe platform at Y=150
        return createEmergencyPlatform(voidLevel, centerX, centerZ, 150);
    }

    /**
     * Creates an emergency 3x3 obsidian platform if no safe spot is found
     */
    private BlockPos createEmergencyPlatform(ServerLevel level, int centerX, int centerZ, int y) {
        // Create a 3x3 obsidian platform
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos platformPos = new BlockPos(centerX + dx, y, centerZ + dz);
                level.setBlock(platformPos, Blocks.OBSIDIAN.defaultBlockState(), 3);

                // Clear air above the platform
                for (int clearY = y + 1; clearY <= y + 3; clearY++) {
                    BlockPos clearPos = new BlockPos(centerX + dx, clearY, centerZ + dz);
                    if (level.getBlockState(clearPos).getBlock() != Blocks.AIR) {
                        level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        return new BlockPos(centerX, y + 1, centerZ);
    }

    /**
     * Checks if a block is safe to stand on in the overworld
     */
    private boolean isSafeGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() &&
                state.getBlock() != Blocks.LAVA &&
                state.getBlock() != Blocks.WATER &&
                state.getBlock() != Blocks.FIRE &&
                !state.canBeReplaced();
    }

    /**
     * Checks if a block is safe to stand on in the void dimension
     */
    private boolean isSafeVoidGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() &&
                state.getBlock() != Blocks.LAVA &&
                state.getBlock() != Blocks.FIRE &&
                !state.canBeReplaced();
    }

    /**
     * Checks if a position is passable (air or other non-solid blocks)
     */
    private boolean isPassable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() ||
                !state.canOcclude() ||
                state.canBeReplaced();
    }

    /**
     * Checks if a position contains lava
     */
    private boolean isLava(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() == Blocks.LAVA;
    }
}