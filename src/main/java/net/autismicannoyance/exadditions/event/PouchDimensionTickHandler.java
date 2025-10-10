package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.world.dimension.ArcanePouchDimensionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles ticking of pouch dimensions.
 * Ensures dimensions tick when:
 * - A player is inside the dimension
 * - A player is viewing the tooltip (marked as active)
 */
@Mod.EventBusSubscriber(modid = "exadditions")
public class PouchDimensionTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Get overworld to pass to tick handler
        if (event.getServer() != null) {
            ServerLevel overworld = event.getServer().overworld();
            if (overworld != null) {
                ArcanePouchDimensionManager.tickActiveDimensions(overworld);
            }
        }
    }
}