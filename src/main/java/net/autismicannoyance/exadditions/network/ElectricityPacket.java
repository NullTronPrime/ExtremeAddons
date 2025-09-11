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
                if (isCloudLightning && packet.cloudPosition != null) {
                    // Cloud lightning with position data
                    System.out.println("ElectricityPacket: Creating cloud lightning from position: " + packet.cloudPosition);
                    ElectricityRenderer.createCloudLightningChain(
                            level,
                            packet.cloudPosition,
                            targetEntities,
                            packet.duration
                    );
                } else if (!isCloudLightning) {
                    // Normal player-to-mob lightning
                    Entity sourceEntity = level.getEntity(packet.sourceEntityId);
                    if (sourceEntity != null) {
                        ElectricityRenderer.createElectricityChain(
                                level,
                                sourceEntity,
                                targetEntities,
                                packet.duration
                        );
                    } else {
                        System.out.println("ElectricityPacket: Source entity not found (ID: " + packet.sourceEntityId + ")");
                        return;
                    }
                } else {
                    // Cloud lightning without position data - fallback to player position
                    int playerId = Math.abs(packet.sourceEntityId);
                    for (Player p : level.players()) {
                        if (p.getId() == playerId) {
                            Vec3 fallbackCloudPos = p.position().add(0, 4.0, 0);
                            System.out.println("ElectricityPacket: Creating cloud lightning with fallback position: " + fallbackCloudPos);
                            ElectricityRenderer.createCloudLightningChain(
                                    level,
                                    fallbackCloudPos,
                                    targetEntities,
                                    packet.duration
                            );
                            break;
                        }
                    }
                }

                String sourceDesc = isCloudLightning ? "cloud at " + packet.cloudPosition : "entity " + packet.sourceEntityId;
                System.out.println("ElectricityPacket: Successfully created electricity chain from " +
                        sourceDesc + " to " + targetEntities.size() + " targets");
            } else {
                System.out.println("ElectricityPacket: No valid targets found, skipping effect");
            }

        } catch (Exception e) {
            System.err.println("ElectricityPacket: Error handling packet: " + e.getMessage());
            e.printStackTrace();
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