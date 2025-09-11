package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.ElectricityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Network packet for synchronizing electricity effects between server and clients
 * Updated for Forge 1.20.1 with proper cloud lightning support
 */
public class ElectricityPacket {
    private final int sourceEntityId;
    private final List<Integer> targetEntityIds;
    private final int duration;

    /**
     * Create a new electricity effect packet
     * @param sourceEntityId The entity that triggered the electricity (positive = player, negative = cloud)
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
     * Updated with proper cloud lightning support
     */
    private static void handleClientSide(ElectricityPacket packet) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Level level = minecraft.level;

            if (level == null) {
                System.out.println("ElectricityPacket: Level is null, cannot process");
                return;
            }

            boolean isCloudLightning = packet.sourceEntityId < 0;
            Entity sourceEntity = null;

            if (!isCloudLightning) {
                // Normal player-to-mob lightning
                sourceEntity = level.getEntity(packet.sourceEntityId);
                if (sourceEntity == null) {
                    System.out.println("ElectricityPacket: Source entity not found (ID: " + packet.sourceEntityId + ")");
                    return;
                }
            } else {
                // Cloud lightning - find the player from the negative ID
                int playerId = Math.abs(packet.sourceEntityId);
                Player player = null;

                // Search for the player
                for (Player p : level.players()) {
                    if (p.getId() == playerId) {
                        player = p;
                        break;
                    }
                }

                if (player != null) {
                    System.out.println("ElectricityPacket: Found player " + player.getName().getString() + " for cloud lightning");
                    sourceEntity = player; // Use the actual player instead of virtual entity
                } else {
                    System.out.println("ElectricityPacket: Player not found for cloud lightning (ID: " + playerId + ")");
                    return;
                }
            }

            // Find all target entities - make sure they're LivingEntity
            List<Entity> targetEntities = new ArrayList<>();
            int foundTargets = 0;
            int missingTargets = 0;

            for (int targetId : packet.targetEntityIds) {
                Entity target = level.getEntity(targetId);
                if (target instanceof LivingEntity && target.isAlive() && !target.isRemoved()) {
                    targetEntities.add(target);
                    foundTargets++;
                } else {
                    missingTargets++;
                    System.out.println("ElectricityPacket: Target entity not found or invalid (ID: " + targetId + ")");
                }
            }

            System.out.println("ElectricityPacket: Processing " + (isCloudLightning ? "cloud" : "player") +
                    " chain with " + foundTargets + " valid targets, " + missingTargets + " missing");

            // Handle cloud creation vs lightning strike
            if (targetEntities.isEmpty() && isCloudLightning) {
                // This is a cloud creation packet (no targets)
                ElectricityRenderer.createStormCloud(level, sourceEntity, packet.duration);
                System.out.println("ElectricityPacket: Created storm cloud visual effect");
            } else if (!targetEntities.isEmpty()) {
                // This is a lightning strike packet
                ElectricityRenderer.createElectricityChain(
                        level,
                        sourceEntity,
                        targetEntities,
                        packet.duration
                );

                String sourceName = sourceEntity != null ? sourceEntity.getName().getString() : "unknown";
                System.out.println("ElectricityPacket: Successfully created electricity chain from " +
                        sourceName + " to " + targetEntities.size() + " targets");
            } else {
                System.out.println("ElectricityPacket: No valid targets found, skipping effect");
            }

        } catch (Exception e) {
            System.err.println("ElectricityPacket: Error handling packet: " + e.getMessage());
            e.printStackTrace();
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