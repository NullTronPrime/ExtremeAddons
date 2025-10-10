package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.client.PouchClientData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Function;

/**
 * Handles pouch dimension exit (server-side)
 * and pouch client data cleanup (client-side)
 */
public class PouchDimensionExitHandler {

    /**
     * Server-side: Handles teleporting players out of pouch dimensions
     * when right-clicking the central block at (0, 64, 0).
     */
    @Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID)
    public static class ServerHandler {
        @SubscribeEvent
        public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            ResourceKey<Level> dimKey = player.level().dimension();
            String dimPath = dimKey.location().getPath();

            // Check if player is in a pouch dimension
            if (dimPath.startsWith("pouch_")) {
                BlockPos pos = event.getPos();

                // Check if clicking the center diamond block
                if (pos.getX() == 0 && pos.getY() == 64 && pos.getZ() == 0) {
                    ServerLevel overworld = serverPlayer.getServer().overworld();

                    // Find safe exit position
                    BlockPos exitPos = findSafeExitPosition(overworld, serverPlayer.blockPosition());

                    serverPlayer.changeDimension(overworld, new ITeleporter() {
                        @Override
                        public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                            entity = repositionEntity.apply(false);
                            // Teleport to safe position (center of block, 1 block above ground)
                            entity.moveTo(exitPos.getX() + 0.5, exitPos.getY() + 1, exitPos.getZ() + 0.5, yaw, entity.getXRot());
                            return entity;
                        }
                    });

                    event.setCanceled(true);
                }
            }
        }

        /**
         * Finds a safe exit position in the overworld.
         * Prioritizes: world spawn, then searches nearby for safe ground.
         */
        private static BlockPos findSafeExitPosition(ServerLevel level, BlockPos originalPos) {
            // Start from world spawn
            BlockPos spawnPos = level.getSharedSpawnPos();

            // Try spawn position first
            if (isSafePosition(level, spawnPos)) {
                return spawnPos;
            }

            // Search for safe ground near spawn in expanding circles
            for (int radius = 1; radius <= 10; radius++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        // Only check perimeter of current radius
                        if (Math.abs(x) != radius && Math.abs(z) != radius) continue;

                        BlockPos testPos = spawnPos.offset(x, 0, z);
                        BlockPos groundPos = findGroundBelow(level, testPos);

                        if (isSafePosition(level, groundPos)) {
                            return groundPos;
                        }
                    }
                }
            }

            // Last resort: spawn position + 10 blocks up (will fall to ground)
            return spawnPos.above(10);
        }

        /**
         * Checks if a position is safe for player teleportation.
         * Requirements: solid block below, 2 air blocks above
         */
        private static boolean isSafePosition(ServerLevel level, BlockPos pos) {
            try {
                return level.getBlockState(pos).isSolid() &&
                        level.getBlockState(pos.above()).isAir() &&
                        level.getBlockState(pos.above(2)).isAir() &&
                        !level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LAVA) &&
                        !level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FIRE);
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Finds the ground level below a position.
         * Searches down first, then up if nothing found.
         */
        private static BlockPos findGroundBelow(ServerLevel level, BlockPos start) {
            BlockPos.MutableBlockPos pos = start.mutable();

            // Search down up to 50 blocks
            for (int i = 0; i < 50; i++) {
                if (level.getBlockState(pos).isSolid() && level.getBlockState(pos.above()).isAir()) {
                    return pos.immutable();
                }
                pos.move(0, -1, 0);
            }

            // Search up if no ground found below
            pos.set(start);
            for (int i = 0; i < 50; i++) {
                if (level.getBlockState(pos).isSolid() && level.getBlockState(pos.above()).isAir()) {
                    return pos.immutable();
                }
                pos.move(0, 1, 0);
            }

            return start; // Fallback to original position
        }
    }

    /**
     * Client-side: Clears cached pouch data when disconnecting from a server.
     * This prevents stale data from persisting across different servers/worlds.
     */
    @Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientHandler {
        @SubscribeEvent
        public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            // Clear all cached pouch entity data
            PouchClientData.clear();
        }
    }
}