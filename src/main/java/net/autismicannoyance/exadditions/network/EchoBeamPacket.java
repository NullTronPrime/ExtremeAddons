package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.EchoBeamRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Simple network packet for echo beam effects.
 */
public class EchoBeamPacket {
    private final Vec3 start;
    private final Vec3 end;
    private final double beamWidth;
    private final int hitCount;

    public EchoBeamPacket(Vec3 start, Vec3 end, double beamWidth, int hitCount) {
        this.start = start;
        this.end = end;
        this.beamWidth = beamWidth;
        this.hitCount = hitCount;
    }

    public static void encode(EchoBeamPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.start.x);
        buffer.writeDouble(packet.start.y);
        buffer.writeDouble(packet.start.z);
        buffer.writeDouble(packet.end.x);
        buffer.writeDouble(packet.end.y);
        buffer.writeDouble(packet.end.z);
        buffer.writeDouble(packet.beamWidth);
        buffer.writeInt(packet.hitCount);
    }

    public static EchoBeamPacket decode(FriendlyByteBuf buffer) {
        Vec3 start = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        Vec3 end = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        double beamWidth = buffer.readDouble();
        int hitCount = buffer.readInt();
        return new EchoBeamPacket(start, end, beamWidth, hitCount);
    }

    public static void handle(EchoBeamPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    EchoBeamManager.addBeam(packet.start, packet.end, packet.beamWidth, packet.hitCount));
        });
        context.setPacketHandled(true);
    }
}