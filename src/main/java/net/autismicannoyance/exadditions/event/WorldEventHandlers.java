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
 * FIXED: Handles world loading/unloading events for black hole persistence
 * The issue was trying to restore during world loading events - this causes hanging
 * because the world isn't fully ready yet. Now we only restore after server is completely started.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldEventHandlers {

    private static boolean restorationCompleted = false;
    private static int ticksAfterServerStart = 0;
    private static final int RESTORATION_DELAY = 60; // 3 seconds after server start

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        System.out.println("[BlackHole Persistence] Server started, will restore black holes after delay...");

        // Reset restoration flags
        restorationCompleted = false;
        ticksAfterServerStart = 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // ONLY handle restoration, nothing else in server tick
        if (event.phase != TickEvent.Phase.END) return;

        // Only attempt restoration if not already completed
        if (!restorationCompleted) {
            ticksAfterServerStart++;

            // Wait for the delay, then restore
            if (ticksAfterServerStart >= RESTORATION_DELAY) {
                System.out.println("[BlackHole Persistence] Attempting black hole restoration...");

                try {
                    ServerLevel overworld = event.getServer().getLevel(ServerLevel.OVERWORLD);
                    if (overworld != null) {
                        // This is the critical fix - run restoration asynchronously to avoid blocking
                        event.getServer().execute(() -> {
                            try {
                                BlackHoleWorldData worldData = BlackHoleWorldData.get(overworld);
                                worldData.restoreBlackHoles(overworld);
                                System.out.println("[BlackHole Persistence] Black hole restoration completed successfully!");
                            } catch (Exception e) {
                                System.err.println("[BlackHole Persistence] Error during restoration: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("[BlackHole Persistence] Error scheduling restoration: " + e.getMessage());
                    e.printStackTrace();
                }

                // Mark as completed regardless of success/failure to prevent retry loops
                restorationCompleted = true;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        System.out.println("[BlackHole Persistence] Server stopping, saving black holes...");

        try {
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

        // Reset flags for next server start
        restorationCompleted = false;
        ticksAfterServerStart = 0;
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        // DO NOT restore here - this is what causes the hanging!
        // Just log that the world loaded
        if (event.getLevel() instanceof ServerLevel serverLevel &&
                serverLevel.dimension().equals(ServerLevel.OVERWORLD)) {
            System.out.println("[BlackHole Persistence] Overworld loaded - restoration will happen via ServerStartedEvent");
        }
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