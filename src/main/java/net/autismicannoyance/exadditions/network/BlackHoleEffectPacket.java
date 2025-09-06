package net.autismicannoyance.exadditions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet for creating black hole visual effects on the client
 */
public final class BlackHoleEffectPacket {
    private final int entityId;
    private final Vec3 position;
    private final float size;
    private final float rotationSpeed;
    private final int lifetime;
    private final boolean remove; // If true, removes the effect instead of creating it

    public BlackHoleEffectPacket(int entityId, Vec3 position, float size, float rotationSpeed, int lifetime) {
        this.entityId = entityId;
        this.position = position;
        this.size = size;
        this.rotationSpeed = rotationSpeed;
        this.lifetime = lifetime;
        this.remove = false;
    }

    // Constructor for removal
    public BlackHoleEffectPacket(int entityId) {
        this.entityId = entityId;
        this.position = Vec3.ZERO;
        this.size = 0;
        this.rotationSpeed = 0;
        this.lifetime = 0;
        this.remove = true;
    }

    public static BlackHoleEffectPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        boolean remove = buf.readBoolean();

        if (remove) {
            return new BlackHoleEffectPacket(entityId);
        }

        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        Vec3 position = new Vec3(x, y, z);
        float size = buf.readFloat();
        float rotationSpeed = buf.readFloat();
        int lifetime = buf.readInt();

        return new BlackHoleEffectPacket(entityId, position, size, rotationSpeed, lifetime);
    }

    public static void encode(BlackHoleEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.remove);

        if (!msg.remove) {
            buf.writeDouble(msg.position.x);
            buf.writeDouble(msg.position.y);
            buf.writeDouble(msg.position.z);
            buf.writeFloat(msg.size);
            buf.writeFloat(msg.rotationSpeed);
            buf.writeInt(msg.lifetime);
        }
    }

    public static void handle(BlackHoleEffectPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (msg.remove) {
                    net.autismicannoyance.exadditions.client.BlackHoleRenderer.removeEffect(msg.entityId);
                } else {
                    net.autismicannoyance.exadditions.client.BlackHoleRenderer.addEffect(
                            msg.entityId, msg.position, msg.size, msg.rotationSpeed, msg.lifetime
                    );
                }
            });
        });
        ctx.setPacketHandled(true);
    }

    // Getters for access
    public int getEntityId() { return entityId; }
    public Vec3 getPosition() { return position; }
    public float getSize() { return size; }
    public float getRotationSpeed() { return rotationSpeed; }
    public int getLifetime() { return lifetime; }
    public boolean isRemove() { return remove; }
}