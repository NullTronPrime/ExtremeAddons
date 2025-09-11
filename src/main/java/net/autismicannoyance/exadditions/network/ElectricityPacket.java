package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.ElectricityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Network packet for synchronizing electricity effects between server and clients
 * Updated for Forge 1.20.1 with proper cloud lightning support and cloud position data
 */
public class ElectricityPacket {
    private final int sourceEntityId;
    private final List<Integer> targetEntityIds;
    private final int duration;
    private final Vec3 cloudPosition; // Store cloud position for accurate lightning source

    public ElectricityPacket(int sourceEntityId, List<Integer> targetEntityIds, int duration) {
        this(sourceEntityId, targetEntityIds, duration, null);
    }

    public ElectricityPacket(int sourceEntityId, List<Integer> targetEntityIds, int duration, Vec3 cloudPosition) {
        this.sourceEntityId = sourceEntityId;
        this.targetEntityIds = new ArrayList<>(targetEntityIds);
        this.duration = duration;
        this.cloudPosition = cloudPosition;
    }

    public static void encode(ElectricityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.sourceEntityId);
        buffer.writeInt(packet.duration);

        buffer.writeInt(packet.targetEntityIds.size());
        for (int targetId : packet.targetEntityIds) {
            buffer.writeInt(targetId);
        }

        buffer.writeBoolean(packet.cloudPosition != null);
        if (packet.cloudPosition != null) {
            buffer.writeDouble(packet.cloudPosition.x);
            buffer.writeDouble(packet.cloudPosition.y);
            buffer.writeDouble(packet.cloudPosition.z);
        }
    }

    public static ElectricityPacket decode(FriendlyByteBuf buffer) {
        int sourceEntityId = buffer.readInt();
        int duration = buffer.readInt();

        int targetCount = buffer.readInt();
        List<Integer> targetEntityIds = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            targetEntityIds.add(buffer.readInt());
        }

        Vec3 cloudPosition = null;
        if (buffer.readBoolean()) {
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();
            cloudPosition = new Vec3(x, y, z);
        }

        return new ElectricityPacket(sourceEntityId, targetEntityIds, duration, cloudPosition);
    }

    public static void handle(ElectricityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Only process on client side
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClientSide(packet));
        });
        context.setPacketHandled(true);
    }

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

                for (Player p : level.players()) {
                    if (p.getId() == playerId) {
                        player = p;
                        break;
                    }
                }

                if (player != null) {
                    System.out.println("ElectricityPacket: Found player " + player.getName().getString() + " for cloud lightning");

                    // Create a virtual entity for the cloud position if we have one
                    if (packet.cloudPosition != null) {
                        sourceEntity = new VirtualCloudEntity(player, packet.cloudPosition);
                    } else {
                        sourceEntity = player;
                    }
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
                int playerId = Math.abs(packet.sourceEntityId);
                for (Player p : level.players()) {
                    if (p.getId() == playerId) {
                        ElectricityRenderer.createStormCloud(level, p, packet.duration);
                        System.out.println("ElectricityPacket: Created storm cloud visual effect");
                        break;
                    }
                }
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

    /**
     * Virtual entity to represent cloud position for lightning effects
     */
    private static class VirtualCloudEntity extends Entity {
        private final Vec3 fixedPosition;

        public VirtualCloudEntity(Player player, Vec3 cloudPosition) {
            super(player.getType(), player.level());
            this.fixedPosition = cloudPosition;
            // Note: we avoid forcing a negative ID here. If you rely on an ID marker,
            // you can optionally call setId(...) if needed and safe for your logic.
        }

        @Override
        public Vec3 position() {
            return fixedPosition;
        }

        // DON'T override getX/getY/getZ() — those are final in upstream mappings.
        // Use position() and other non-final accessors instead.

        @Override
        protected void defineSynchedData() {
            // Empty implementation - not needed for virtual entity
        }

        @Override
        protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
            // Empty implementation - not needed for virtual entity
        }

        @Override
        protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
            // Empty implementation - not needed for virtual entity
        }

        @Override
        public net.minecraft.network.chat.Component getName() {
            return net.minecraft.network.chat.Component.literal("Storm Cloud");
        }
    }

    public int getSourceEntityId() {
        return sourceEntityId;
    }

    public List<Integer> getTargetEntityIds() {
        return new ArrayList<>(targetEntityIds);
    }

    public int getDuration() {
        return duration;
    }

    public Vec3 getCloudPosition() {
        return cloudPosition;
    }
}
