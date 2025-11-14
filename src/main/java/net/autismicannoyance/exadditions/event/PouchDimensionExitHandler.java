package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.client.PouchClientData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles pouch dimension client data cleanup.
 * Exit functionality is now handled directly by the ArcanePouchItem.
 */
public class PouchDimensionExitHandler {

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