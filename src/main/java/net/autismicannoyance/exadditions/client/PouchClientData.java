package net.autismicannoyance.exadditions.client;

import net.minecraft.nbt.ListTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side storage for pouch dimension entity data.
 * This data is synced from the server via PouchEntitySyncPacket.
 * Used by the tooltip renderer to display real-time entity positions.
 */
@OnlyIn(Dist.CLIENT)
public class PouchClientData {
    private static final Map<UUID, ListTag> POUCH_ENTITY_DATA = new HashMap<>();

    /**
     * Updates the cached entity data for a specific pouch.
     * Called when receiving PouchEntitySyncPacket from server.
     *
     * @param pouchUUID The UUID of the pouch dimension
     * @param entityData NBT list containing entity data with positions
     */
    public static void updatePouchData(UUID pouchUUID, ListTag entityData) {
        POUCH_ENTITY_DATA.put(pouchUUID, entityData);
    }

    /**
     * Gets the cached entity data for a specific pouch.
     * Returns empty list if no data is cached.
     *
     * @param pouchUUID The UUID of the pouch dimension
     * @return ListTag containing entity data, or empty list
     */
    public static ListTag getPouchData(UUID pouchUUID) {
        return POUCH_ENTITY_DATA.getOrDefault(pouchUUID, new ListTag());
    }

    /**
     * Clears all cached pouch data.
     * Should be called when disconnecting from server.
     */
    public static void clear() {
        POUCH_ENTITY_DATA.clear();
    }
}