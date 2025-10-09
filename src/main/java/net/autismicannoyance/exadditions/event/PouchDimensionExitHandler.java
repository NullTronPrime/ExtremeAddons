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
    @Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.DEDICATED_SERVER)
    public static class ServerHandler {
        @SubscribeEvent
        public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            ResourceKey<Level> dimKey = player.level().dimension();
            String dimPath = dimKey.location().getPath();

            if (dimPath.startsWith("pouch_")) {
                BlockPos pos = event.getPos();
                if (pos.getX() == 0 && pos.getY() == 64 && pos.getZ() == 0) {
                    ServerLevel overworld = serverPlayer.getServer().overworld();

                    serverPlayer.changeDimension(overworld, new ITeleporter() {
                        @Override
                        public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                            entity = repositionEntity.apply(false);
                            entity.moveTo(entity.getX(), entity.getY() + 5, entity.getZ(), yaw, entity.getXRot());
                            return entity;
                        }
                    });

                    event.setCanceled(true);
                }
            }
        }
    }

    /**
     * Client-side: Clears cached pouch data when disconnecting from a server.
     */
    @Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
    public static class ClientHandler {
        @SubscribeEvent
        public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            PouchClientData.clear();
        }
    }
}
