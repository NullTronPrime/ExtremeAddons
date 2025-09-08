package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.MeteoriteRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet for spawning meteorite visual effects on the client side.
 * Sends meteorite data from server to clients for rendering stunning visuals.
 */
public class MeteoriteEffectPacket {
    private final Vec3 startPos;
    private final Vec3 endPos;
    private final Vec3 velocity;
    private final float size;
    private final int lifetimeTicks;
    private final int meteoriteId;
    private final boolean hasTrail;
    private final int coreColor;
    private final int trailColor;
    private final float intensity;

    public MeteoriteEffectPacket(Vec3 startPos, Vec3 endPos, Vec3 velocity, float size,
                                 int lifetimeTicks, int meteoriteId, boolean hasTrail,
                                 int coreColor, int trailColor, float intensity) {
        this.startPos = startPos;
        this.endPos = endPos;
        this.velocity = velocity;
        this.size = size;
        this.lifetimeTicks = lifetimeTicks;
        this.meteoriteId = meteoriteId;
        this.hasTrail = hasTrail;
        this.coreColor = coreColor;
        this.trailColor = trailColor;
        this.intensity = intensity;
    }

    public static void encode(MeteoriteEffectPacket packet, FriendlyByteBuf buf) {
        // Position data
        buf.writeDouble(packet.startPos.x);
        buf.writeDouble(packet.startPos.y);
        buf.writeDouble(packet.startPos.z);
        buf.writeDouble(packet.endPos.x);
        buf.writeDouble(packet.endPos.y);
        buf.writeDouble(packet.endPos.z);

        // Velocity
        buf.writeDouble(packet.velocity.x);
        buf.writeDouble(packet.velocity.y);
        buf.writeDouble(packet.velocity.z);

        // Properties
        buf.writeFloat(packet.size);
        buf.writeInt(packet.lifetimeTicks);
        buf.writeInt(packet.meteoriteId);
        buf.writeBoolean(packet.hasTrail);
        buf.writeInt(packet.coreColor);
        buf.writeInt(packet.trailColor);
        buf.writeFloat(packet.intensity);
    }

    public static MeteoriteEffectPacket decode(FriendlyByteBuf buf) {
        Vec3 startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 endPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 velocity = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float size = buf.readFloat();
        int lifetimeTicks = buf.readInt();
        int meteoriteId = buf.readInt();
        boolean hasTrail = buf.readBoolean();
        int coreColor = buf.readInt();
        int trailColor = buf.readInt();
        float intensity = buf.readFloat();

        return new MeteoriteEffectPacket(startPos, endPos, velocity, size, lifetimeTicks,
                meteoriteId, hasTrail, coreColor, trailColor, intensity);
    }

    public static void handle(MeteoriteEffectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Only handle on client side
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    MeteoriteRenderer.spawnMeteorite(
                            packet.startPos,
                            packet.endPos,
                            packet.velocity,
                            packet.size,
                            packet.lifetimeTicks,
                            packet.meteoriteId,
                            packet.hasTrail,
                            packet.coreColor,
                            packet.trailColor,
                            packet.intensity
                    )
            );
        });
        context.setPacketHandled(true);
    }
}