package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.PulsarRenderer;
import net.autismicannoyance.exadditions.item.custom.PulsarCannonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Optimized network packet for Pulsar Cannon attacks
 * Pre-calculates segments server-side and sends optimized data to clients
 */
public class PulsarAttackPacket {
    private final Vec3 startPos;
    private final Vec3 direction;
    private final int shooterId;
    private final float damage;
    private final int maxBounces;
    private final double maxRange;
    private final List<PulsarCannonItem.OptimizedSegment> preCalculatedSegments;

    // Constructor with pre-calculated segments
    public PulsarAttackPacket(Vec3 startPos, Vec3 direction, int shooterId,
                              float damage, int maxBounces, double maxRange,
                              List<PulsarCannonItem.OptimizedSegment> preCalculatedSegments) {
        this.startPos = startPos;
        this.direction = direction;
        this.shooterId = shooterId;
        this.damage = damage;
        this.maxBounces = maxBounces;
        this.maxRange = maxRange;
        this.preCalculatedSegments = preCalculatedSegments != null ? preCalculatedSegments : new ArrayList<>();
    }

    // Fallback constructor without pre-calculated segments
    public PulsarAttackPacket(Vec3 startPos, Vec3 direction, int shooterId,
                              float damage, int maxBounces, double maxRange) {
        this(startPos, direction, shooterId, damage, maxBounces, maxRange, null);
    }

    public static void encode(PulsarAttackPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.startPos.x);
        buf.writeDouble(packet.startPos.y);
        buf.writeDouble(packet.startPos.z);
        buf.writeDouble(packet.direction.x);
        buf.writeDouble(packet.direction.y);
        buf.writeDouble(packet.direction.z);
        buf.writeInt(packet.shooterId);
        buf.writeFloat(packet.damage);
        buf.writeInt(packet.maxBounces);
        buf.writeDouble(packet.maxRange);

        // Encode pre-calculated segments
        buf.writeInt(packet.preCalculatedSegments.size());
        for (PulsarCannonItem.OptimizedSegment segment : packet.preCalculatedSegments) {
            // Start position
            buf.writeDouble(segment.start.x);
            buf.writeDouble(segment.start.y);
            buf.writeDouble(segment.start.z);

            // End position
            buf.writeDouble(segment.end.x);
            buf.writeDouble(segment.end.y);
            buf.writeDouble(segment.end.z);

            // Properties
            buf.writeFloat(segment.energy);
            buf.writeInt(segment.bounceCount);
            buf.writeBoolean(segment.isSplit);
            buf.writeBoolean(segment.hitBlock);
            buf.writeInt(segment.generation);
        }
    }

    public static PulsarAttackPacket decode(FriendlyByteBuf buf) {
        Vec3 startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 direction = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int shooterId = buf.readInt();
        float damage = buf.readFloat();
        int maxBounces = buf.readInt();
        double maxRange = buf.readDouble();

        // Decode pre-calculated segments
        int segmentCount = buf.readInt();
        List<PulsarCannonItem.OptimizedSegment> segments = new ArrayList<>();

        for (int i = 0; i < segmentCount; i++) {
            Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float energy = buf.readFloat();
            int bounceCount = buf.readInt();
            boolean isSplit = buf.readBoolean();
            boolean hitBlock = buf.readBoolean();
            int generation = buf.readInt();

            segments.add(new PulsarCannonItem.OptimizedSegment(
                    start, end, energy, bounceCount, isSplit, hitBlock, generation));
        }

        return new PulsarAttackPacket(startPos, direction, shooterId, damage, maxBounces, maxRange, segments);
    }

    public static void handle(PulsarAttackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // This runs on the client side with pre-calculated segments
            PulsarRenderer.handleOptimizedPulsarAttack(
                    packet.startPos,
                    packet.direction,
                    packet.shooterId,
                    packet.damage,
                    packet.maxBounces,
                    packet.maxRange,
                    packet.preCalculatedSegments
            );
        });
        ctx.get().setPacketHandled(true);
    }

    // Getters
    public Vec3 getStartPos() { return startPos; }
    public Vec3 getDirection() { return direction; }
    public int getShooterId() { return shooterId; }
    public float getDamage() { return damage; }
    public int getMaxBounces() { return maxBounces; }
    public double getMaxRange() { return maxRange; }
    public List<PulsarCannonItem.OptimizedSegment> getPreCalculatedSegments() { return preCalculatedSegments; }
}