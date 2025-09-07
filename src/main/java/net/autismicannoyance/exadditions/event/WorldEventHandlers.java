package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.world.BlackHoleWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles world loading/unloading events for black hole persistence
 * FIXED: Delays restoration until world is fully loaded
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldEventHandlers {

    private static boolean needsRestoration = false;
    private static ServerLevel pendingLevel = null;
    private static int restorationDelay = 0;
    private static final int REQUIRED_DELAY = 100; // 5 seconds at 20 TPS

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        System.out.println("[BlackHole Persistence] Server started, scheduling delayed black hole restoration...");

        // DON'T restore immediately - schedule for later
        ServerLevel overworld = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            needsRestoration = true;
            pendingLevel = overworld;
            restorationDelay = 0;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Handle delayed restoration
        if (needsRestoration && pendingLevel != null) {
            restorationDelay++;

            if (restorationDelay >= REQUIRED_DELAY) {
                System.out.println("[BlackHole Persistence] World fully loaded, now restoring black holes...");

                try {
                    BlackHoleWorldData worldData = BlackHoleWorldData.get(pendingLevel);
                    worldData.restoreBlackHoles(pendingLevel);

                    System.out.println("[BlackHole Persistence] Black hole restoration completed successfully!");
                } catch (Exception e) {
                    System.err.println("[BlackHole Persistence] Error during restoration: " + e.getMessage());
                    e.printStackTrace();
                }

                // Reset flags
                needsRestoration = false;
                pendingLevel = null;
                restorationDelay = 0;
            } else if (restorationDelay % 20 == 0) {
                // Progress indicator every second
                int secondsRemaining = (REQUIRED_DELAY - restorationDelay) / 20;
                System.out.println("[BlackHole Persistence] Waiting " + secondsRemaining + " more seconds before restoration...");
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        System.out.println("[BlackHole Persistence] Server stopping, saving black holes...");

        try {
            // Save black holes from the overworld
            ServerLevel overworld = event.getServer().getLevel(ServerLevel.OVERWORLD);
            if (overworld != null) {
                BlackHoleWorldData worldData = BlackHoleWorldData.get(overworld);
                worldData.saveBlackHoles(overworld);
                System.out.println("[BlackHole Persistence] Black holes saved successfully!");
            }
        } catch (Exception e) {
            System.err.println("[BlackHole Persistence] Error saving black holes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(ServerLevel.OVERWORLD)) return;

        System.out.println("[BlackHole Persistence] World load event detected - will restore via server tick delay");

        // DON'T restore here - this causes the hanging issue
        // The ServerStartedEvent will handle it with proper delay
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(ServerLevel.OVERWORLD)) return;

        System.out.println("[BlackHole Persistence] World unloading, saving black holes...");

        try {
            // Save black holes when world unloads
            BlackHoleWorldData worldData = BlackHoleWorldData.get(serverLevel);
            worldData.saveBlackHoles(serverLevel);
            System.out.println("[BlackHole Persistence] Black holes saved on world unload!");
        } catch (Exception e) {
            System.err.println("[BlackHole Persistence] Error saving on world unload: " + e.getMessage());
            e.printStackTrace();
        }
    }
}