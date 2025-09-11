package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.ElectricityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Network packet for synchronizing electricity effects between server and clients
 * Sent from server to clients when an electric wand is used
 */
public class ElectricityPacket {
    private final int sourceEntityId;
    private final List<Integer> targetEntityIds;
    private final int duration;

    /**
     * Create a new electricity effect packet
     * @param sourceEntityId The entity that triggered the electricity (usually a player)
     * @param targetEntityIds List of entity IDs that should be connected with electricity
     * @param duration How long the effect should last in ticks
     */
    public ElectricityPacket(int sourceEntityId, List<Integer> targetEntityIds, int duration) {
        this.sourceEntityId = sourceEntityId;
        this.targetEntityIds = new ArrayList<>(targetEntityIds);
        this.duration = duration;
    }

    /**
     * Encode the packet data to the network buffer
     */
    public static void encode(ElectricityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.sourceEntityId);
        buffer.writeInt(packet.duration);

        // Write the number of targets and their IDs
        buffer.writeInt(packet.targetEntityIds.size());
        for (int targetId : packet.targetEntityIds) {
            buffer.writeInt(targetId);
        }
    }

    /**
     * Decode the packet data from the network buffer
     */
    public static ElectricityPacket decode(FriendlyByteBuf buffer) {
        int sourceEntityId = buffer.readInt();
        int duration = buffer.readInt();

        // Read the target entity IDs
        int targetCount = buffer.readInt();
        List<Integer> targetEntityIds = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            targetEntityIds.add(buffer.readInt());
        }

        return new ElectricityPacket(sourceEntityId, targetEntityIds, duration);
    }

    /**
     * Handle the packet on the receiving side
     */
    public static void handle(ElectricityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Only process on client side
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClientSide(packet));
        });
        context.setPacketHandled(true);
    }

    /**
     * Client-side handling of the electricity packet
     */
    private static void handleClientSide(ElectricityPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;

        if (level == null) {
            return;
        }

        // Find the source entity
        Entity sourceEntity = level.getEntity(packet.sourceEntityId);
        if (sourceEntity == null) {
            return;
        }

        // Find all target entities
        List<Entity> targetEntities = new ArrayList<>();
        for (int targetId : packet.targetEntityIds) {
            Entity target = level.getEntity(targetId);
            if (target != null) {
                targetEntities.add(target);
            }
        }

        // Only proceed if we have at least one valid target
        if (!targetEntities.isEmpty()) {
            // Create the electricity chain effect
            ElectricityRenderer.createElectricityChain(
                    level,
                    sourceEntity,
                    targetEntities,
                    packet.duration
            );
        }
    }

    // Getters for accessing packet data (if needed)
    public int getSourceEntityId() {
        return sourceEntityId;
    }

    public List<Integer> getTargetEntityIds() {
        return new ArrayList<>(targetEntityIds);
    }

    public int getDuration() {
        return duration;
    }
}