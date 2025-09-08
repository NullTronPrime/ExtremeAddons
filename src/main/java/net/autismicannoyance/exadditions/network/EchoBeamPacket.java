package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.EchoBeamRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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

    public static void encode(EchoBeamPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.start.x);
        buf.writeDouble(packet.start.y);
        buf.writeDouble(packet.start.z);
        buf.writeDouble(packet.end.x);
        buf.writeDouble(packet.end.y);
        buf.writeDouble(packet.end.z);
        buf.writeDouble(packet.beamWidth);
        buf.writeInt(packet.hitCount);
    }

    public static EchoBeamPacket decode(FriendlyByteBuf buf) {
        Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        double beamWidth = buf.readDouble();
        int hitCount = buf.readInt();
        return new EchoBeamPacket(start, end, beamWidth, hitCount);
    }

    public static void handle(EchoBeamPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                // This runs on the client side only
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    EchoBeamRenderer.renderEchoBeam(
                            packet.start,
                            packet.end,
                            packet.beamWidth,
                            packet.hitCount
                    );
                });
            });
        }
        context.setPacketHandled(true);
    }

    // Getters for packet data
    public Vec3 getStart() { return start; }
    public Vec3 getEnd() { return end; }
    public double getBeamWidth() { return beamWidth; }
    public int getHitCount() { return hitCount; }
}