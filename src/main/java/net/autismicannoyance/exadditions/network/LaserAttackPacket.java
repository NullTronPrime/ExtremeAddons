package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.LaserRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class LaserAttackPacket {
    private final Vec3 startPos;
    private final Vec3 direction;
    private final int shooterId;
    private final float damage;
    private final int maxBounces;
    private final double maxRange;

    public LaserAttackPacket(Vec3 startPos, Vec3 direction, int shooterId,
                             float damage, int maxBounces, double maxRange) {
        this.startPos = startPos;
        this.direction = direction;
        this.shooterId = shooterId;
        this.damage = damage;
        this.maxBounces = maxBounces;
        this.maxRange = maxRange;
    }

    public static void encode(LaserAttackPacket packet, FriendlyByteBuf buf) {
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
    }

    public static LaserAttackPacket decode(FriendlyByteBuf buf) {
        Vec3 startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 direction = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int shooterId = buf.readInt();
        float damage = buf.readFloat();
        int maxBounces = buf.readInt();
        double maxRange = buf.readDouble();

        return new LaserAttackPacket(startPos, direction, shooterId, damage, maxBounces, maxRange);
    }

    public static void handle(LaserAttackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // This runs on the client side
            LaserRenderer.handleLaserAttack(
                    packet.startPos,
                    packet.direction,
                    packet.shooterId,
                    packet.damage,
                    packet.maxBounces,
                    packet.maxRange
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
}