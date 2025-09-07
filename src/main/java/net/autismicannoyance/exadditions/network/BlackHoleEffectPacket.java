package net.autismicannoyance.exadditions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Enhanced packet for creating and updating black hole visual effects on the client
 */
public final class BlackHoleEffectPacket {
    private final int entityId;
    private final Vec3 position;
    private final float size;
    private final float rotationSpeed;
    private final int lifetime;
    private final boolean remove; // If true, removes the effect instead of creating it
    private final boolean sizeUpdate; // If true, this is just a size update

    // Create new black hole
    public BlackHoleEffectPacket(int entityId, Vec3 position, float size, float rotationSpeed, int lifetime) {
        this.entityId = entityId;
        this.position = position;
        this.size = size;
        this.rotationSpeed = rotationSpeed;
        this.lifetime = lifetime;
        this.remove = false;
        this.sizeUpdate = false;
    }

    // Remove black hole
    public BlackHoleEffectPacket(int entityId) {
        this.entityId = entityId;
        this.position = Vec3.ZERO;
        this.size = 0;
        this.rotationSpeed = 0;
        this.lifetime = 0;
        this.remove = true;
        this.sizeUpdate = false;
    }

    // Update black hole size
    public static BlackHoleEffectPacket createSizeUpdate(int entityId, float newSize) {
        return new BlackHoleEffectPacket(entityId, Vec3.ZERO, newSize, 0, 0, true);
    }

    // Private constructor for size updates
    private BlackHoleEffectPacket(int entityId, Vec3 position, float size, float rotationSpeed, int lifetime, boolean sizeUpdate) {
        this.entityId = entityId;
        this.position = position;
        this.size = size;
        this.rotationSpeed = rotationSpeed;
        this.lifetime = lifetime;
        this.remove = false;
        this.sizeUpdate = sizeUpdate;
    }

    public static BlackHoleEffectPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        boolean remove = buf.readBoolean();

        if (remove) {
            return new BlackHoleEffectPacket(entityId);
        }

        boolean sizeUpdate = buf.readBoolean();

        if (sizeUpdate) {
            float size = buf.readFloat();
            return new BlackHoleEffectPacket(entityId, Vec3.ZERO, size, 0, 0, true);
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
            buf.writeBoolean(msg.sizeUpdate);

            if (msg.sizeUpdate) {
                buf.writeFloat(msg.size);
            } else {
                buf.writeDouble(msg.position.x);
                buf.writeDouble(msg.position.y);
                buf.writeDouble(msg.position.z);
                buf.writeFloat(msg.size);
                buf.writeFloat(msg.rotationSpeed);
                buf.writeInt(msg.lifetime);
            }
        }
    }

    public static void handle(BlackHoleEffectPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (msg.remove) {
                    net.autismicannoyance.exadditions.client.BlackHoleRenderer.removeEffect(msg.entityId);
                } else if (msg.sizeUpdate) {
                    net.autismicannoyance.exadditions.client.BlackHoleRenderer.updateEffectSize(msg.entityId, msg.size);
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
    public boolean isSizeUpdate() { return sizeUpdate; }
}