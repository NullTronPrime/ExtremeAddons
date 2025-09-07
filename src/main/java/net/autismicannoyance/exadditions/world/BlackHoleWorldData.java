package net.autismicannoyance.exadditions.world;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.event.BlackHoleEvents;
import net.autismicannoyance.exadditions.network.BlackHoleEffectPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * COMPLETELY REWRITTEN: Safe black hole persistence that won't hang world loading
 * Uses proper SavedData lifecycle and non-blocking operations
 */
public class BlackHoleWorldData extends SavedData {

    private static final String DATA_NAME = ExAdditions.MOD_ID + "_blackholes";

    // NBT keys
    private static final String BLACKHOLES_KEY = "BlackHoles";
    private static final String ID_KEY = "id";
    private static final String POS_X_KEY = "posX";
    private static final String POS_Y_KEY = "posY";
    private static final String POS_Z_KEY = "posZ";
    private static final String SIZE_KEY = "size";
    private static final String ROTATION_SPEED_KEY = "rotationSpeed";
    private static final String AGE_KEY = "age";
    private static final String CURRENT_ROTATION_KEY = "currentRotation";
    private static final String TIME_SINCE_FEED_KEY = "timeSinceLastFeed";
    private static final String PENDING_GROWTH_KEY = "pendingGrowth";

    // Store parsed data in memory
    private final List<SavedBlackHoleData> savedBlackHoles = new ArrayList<>();

    public BlackHoleWorldData() {
        super();
    }

    /**
     * Get or create the world data instance
     */
    public static BlackHoleWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BlackHoleWorldData::load,
                BlackHoleWorldData::create,
                DATA_NAME
        );
    }

    /**
     * Create new empty instance
     */
    public static BlackHoleWorldData create() {
        return new BlackHoleWorldData();
    }

    /**
     * Load from NBT - this is called automatically by Minecraft
     */
    public static BlackHoleWorldData load(CompoundTag nbt) {
        BlackHoleWorldData data = new BlackHoleWorldData();

        if (nbt.contains(BLACKHOLES_KEY, Tag.TAG_LIST)) {
            ListTag blackHoleList = nbt.getList(BLACKHOLES_KEY, Tag.TAG_COMPOUND);

            for (int i = 0; i < blackHoleList.size(); i++) {
                CompoundTag bhTag = blackHoleList.getCompound(i);

                try {
                    SavedBlackHoleData bhData = new SavedBlackHoleData();
                    bhData.id = bhTag.getInt(ID_KEY);
                    bhData.position = new Vec3(
                            bhTag.getDouble(POS_X_KEY),
                            bhTag.getDouble(POS_Y_KEY),
                            bhTag.getDouble(POS_Z_KEY)
                    );
                    bhData.size = bhTag.getFloat(SIZE_KEY);
                    bhData.rotationSpeed = bhTag.getFloat(ROTATION_SPEED_KEY);
                    bhData.age = bhTag.getInt(AGE_KEY);
                    bhData.currentRotation = bhTag.getFloat(CURRENT_ROTATION_KEY);
                    bhData.timeSinceLastFeed = bhTag.getInt(TIME_SINCE_FEED_KEY);
                    bhData.pendingGrowth = bhTag.getFloat(PENDING_GROWTH_KEY);

                    data.savedBlackHoles.add(bhData);
                } catch (Exception e) {
                    System.err.println("[BlackHole Persistence] Error loading black hole " + i + ": " + e.getMessage());
                }
            }

            System.out.println("[BlackHole Persistence] Loaded " + data.savedBlackHoles.size() + " black holes from save data.");
        }

        return data;
    }

    /**
     * Save to NBT - called automatically by Minecraft
     */
    @Override
    public CompoundTag save(CompoundTag nbt) {
        try {
            // Get current black hole state from the active system
            List<BlackHoleEvents.BlackHoleData> activeBlackHoles = BlackHoleEvents.getAllBlackHoleData();

            ListTag blackHoleList = new ListTag();

            for (BlackHoleEvents.BlackHoleData bhData : activeBlackHoles) {
                try {
                    CompoundTag bhTag = new CompoundTag();
                    bhTag.putInt(ID_KEY, bhData.id);
                    bhTag.putDouble(POS_X_KEY, bhData.position.x);
                    bhTag.putDouble(POS_Y_KEY, bhData.position.y);
                    bhTag.putDouble(POS_Z_KEY, bhData.position.z);
                    bhTag.putFloat(SIZE_KEY, bhData.size);
                    bhTag.putFloat(ROTATION_SPEED_KEY, bhData.rotationSpeed);
                    bhTag.putInt(AGE_KEY, bhData.age);
                    bhTag.putFloat(CURRENT_ROTATION_KEY, bhData.currentRotation);
                    bhTag.putInt(TIME_SINCE_FEED_KEY, bhData.timeSinceLastFeed);
                    bhTag.putFloat(PENDING_GROWTH_KEY, bhData.pendingGrowth);

                    blackHoleList.add(bhTag);
                } catch (Exception e) {
                    System.err.println("[BlackHole Persistence] Error saving black hole " + bhData.id + ": " + e.getMessage());
                }
            }

            nbt.put(BLACKHOLES_KEY, blackHoleList);

            System.out.println("[BlackHole Persistence] Saved " + blackHoleList.size() + " black holes to NBT.");

        } catch (Exception e) {
            System.err.println("[BlackHole Persistence] Error during save: " + e.getMessage());
            e.printStackTrace();
        }

        return nbt;
    }

    /**
     * SAFE restoration method - called after server is fully started
     */
    public void restoreBlackHoles(ServerLevel level) {
        if (savedBlackHoles.isEmpty()) {
            System.out.println("[BlackHole Persistence] No black holes to restore.");
            return;
        }

        System.out.println("[BlackHole Persistence] Starting restoration of " + savedBlackHoles.size() + " black holes...");

        int restored = 0;

        for (SavedBlackHoleData bhData : savedBlackHoles) {
            try {
                // Restore the black hole to the active system
                BlackHoleEvents.restoreBlackHole(
                        bhData.id,
                        bhData.position,
                        bhData.size,
                        bhData.rotationSpeed,
                        bhData.age,
                        bhData.currentRotation,
                        bhData.timeSinceLastFeed,
                        bhData.pendingGrowth,
                        level
                );

                // Schedule visual effect creation (delayed to ensure clients are ready)
                final SavedBlackHoleData finalBhData = bhData;
                level.getServer().execute(() -> {
                    try {
                        BlackHoleEffectPacket packet = new BlackHoleEffectPacket(
                                finalBhData.id,
                                finalBhData.position,
                                finalBhData.size,
                                finalBhData.rotationSpeed,
                                999999 // Long lifetime
                        );

                        ModNetworking.CHANNEL.send(
                                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                        finalBhData.position.x,
                                        finalBhData.position.y,
                                        finalBhData.position.z,
                                        200.0,
                                        level.dimension()
                                )),
                                packet
                        );
                    } catch (Exception e) {
                        System.err.println("[BlackHole Persistence] Error sending visual packet for black hole " +
                                finalBhData.id + ": " + e.getMessage());
                    }
                });

                restored++;

            } catch (Exception e) {
                System.err.println("[BlackHole Persistence] Error restoring black hole " + bhData.id + ": " + e.getMessage());
            }
        }

        System.out.println("[BlackHole Persistence] Successfully restored " + restored + "/" + savedBlackHoles.size() + " black holes!");

        // Clear the saved data since it's now active
        savedBlackHoles.clear();
    }

    /**
     * Force save the world data
     */
    public void saveBlackHoles(ServerLevel level) {
        this.setDirty(); // Mark for saving

        // Force immediate save
        try {
            level.getDataStorage().save();
            System.out.println("[BlackHole Persistence] Forced save completed.");
        } catch (Exception e) {
            System.err.println("[BlackHole Persistence] Error during forced save: " + e.getMessage());
        }
    }

    /**
     * Clear all saved black hole data (used by commands)
     */
    public void clearAllSavedBlackHoles() {
        savedBlackHoles.clear();
        this.setDirty(); // Mark for saving to persist the cleared state
        System.out.println("[BlackHole Persistence] Cleared all saved black hole data.");
    }

    /**
     * Get the number of saved black holes
     */
    public int getSavedBlackHoleCount() {
        return savedBlackHoles.size();
    }

    /**
     * Data structure for holding saved black hole information
     */
    private static class SavedBlackHoleData {
        int id;
        Vec3 position;
        float size;
        float rotationSpeed;
        int age;
        float currentRotation;
        int timeSinceLastFeed;
        float pendingGrowth;
    }
}