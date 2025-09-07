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
 * Handles persistent storage of black holes across world saves/loads
 */
public class BlackHoleWorldData extends SavedData {
    private static final String DATA_NAME = ExAdditions.MOD_ID + "_blackholes";

    private final List<BlackHoleData> blackHoles = new ArrayList<>();

    public BlackHoleWorldData() {
        super();
    }

    public BlackHoleWorldData(CompoundTag tag) {
        this();
        load(tag);
    }

    public static BlackHoleWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BlackHoleWorldData::new,
                BlackHoleWorldData::new,
                DATA_NAME
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag blackHoleList = new ListTag();

        for (BlackHoleData bhData : blackHoles) {
            CompoundTag bhTag = new CompoundTag();
            bhTag.putInt("id", bhData.id);
            bhTag.putDouble("x", bhData.position.x);
            bhTag.putDouble("y", bhData.position.y);
            bhTag.putDouble("z", bhData.position.z);
            bhTag.putFloat("size", bhData.size);
            bhTag.putFloat("rotationSpeed", bhData.rotationSpeed);
            bhTag.putInt("age", bhData.age);
            bhTag.putFloat("currentRotation", bhData.currentRotation);
            bhTag.putInt("timeSinceLastFeed", bhData.timeSinceLastFeed);
            bhTag.putFloat("pendingGrowth", bhData.pendingGrowth);
            blackHoleList.add(bhTag);
        }

        tag.put("blackHoles", blackHoleList);
        return tag;
    }

    private void load(CompoundTag tag) {
        blackHoles.clear();

        if (tag.contains("blackHoles", Tag.TAG_LIST)) {
            ListTag blackHoleList = tag.getList("blackHoles", Tag.TAG_COMPOUND);

            for (Tag bhTag : blackHoleList) {
                if (bhTag instanceof CompoundTag compound) {
                    BlackHoleData bhData = new BlackHoleData();
                    bhData.id = compound.getInt("id");
                    bhData.position = new Vec3(
                            compound.getDouble("x"),
                            compound.getDouble("y"),
                            compound.getDouble("z")
                    );
                    bhData.size = compound.getFloat("size");
                    bhData.rotationSpeed = compound.getFloat("rotationSpeed");
                    bhData.age = compound.getInt("age");
                    bhData.currentRotation = compound.getFloat("currentRotation");
                    bhData.timeSinceLastFeed = compound.getInt("timeSinceLastFeed");
                    bhData.pendingGrowth = compound.getFloat("pendingGrowth");

                    blackHoles.add(bhData);
                }
            }
        }
    }

    /**
     * Called when the world loads to restore all black holes
     */
    public void restoreBlackHoles(ServerLevel level) {
        for (BlackHoleData bhData : blackHoles) {
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

            // Send visual effect to clients
            BlackHoleEffectPacket packet = new BlackHoleEffectPacket(
                    bhData.id,
                    bhData.position,
                    bhData.size,
                    bhData.rotationSpeed,
                    999999 // Long lifetime for restored black holes
            );

            ModNetworking.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            bhData.position.x, bhData.position.y, bhData.position.z,
                            200.0, level.dimension()
                    )),
                    packet
            );
        }

        // Clear the temporary storage since they're now in the active system
        blackHoles.clear();
        setDirty();
    }

    /**
     * Save current black hole state before world unload
     */
    public void saveBlackHoles(ServerLevel level) {
        blackHoles.clear();

        // Get all active black holes and their data
        List<BlackHoleEvents.BlackHoleData> activeBlackHoles = BlackHoleEvents.getAllBlackHoleData();

        for (BlackHoleEvents.BlackHoleData bhData : activeBlackHoles) {
            BlackHoleData saveData = new BlackHoleData();
            saveData.id = bhData.id;
            saveData.position = bhData.position;
            saveData.size = bhData.size;
            saveData.rotationSpeed = bhData.rotationSpeed;
            saveData.age = bhData.age;
            saveData.currentRotation = bhData.currentRotation;
            saveData.timeSinceLastFeed = bhData.timeSinceLastFeed;
            saveData.pendingGrowth = bhData.pendingGrowth;

            blackHoles.add(saveData);
        }

        setDirty();
    }

    /**
     * Clear all saved black holes from persistent storage
     */
    public void clearAllSavedBlackHoles() {
        blackHoles.clear();
        setDirty();
    }

    /**
     * Data structure for saving/loading black hole information
     */
    private static class BlackHoleData {
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