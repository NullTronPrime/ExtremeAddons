package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.FlameJetRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FlameJetPacket {
    private final Vec3 start;
    private final Vec3 end;
    private final double width;
    private final int coreColor;
    private final int outerColor;
    private final int duration;

    public FlameJetPacket(Vec3 start, Vec3 end, double width, int coreColor, int outerColor, int duration) {
        this.start = start;
        this.end = end;
        this.width = width;
        this.coreColor = coreColor;
        this.outerColor = outerColor;
        this.duration = duration;
    }

    public static void encode(FlameJetPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.start.x);
        buf.writeDouble(packet.start.y);
        buf.writeDouble(packet.start.z);
        buf.writeDouble(packet.end.x);
        buf.writeDouble(packet.end.y);
        buf.writeDouble(packet.end.z);
        buf.writeDouble(packet.width);
        buf.writeInt(packet.coreColor);
        buf.writeInt(packet.outerColor);
        buf.writeInt(packet.duration);
    }

    public static FlameJetPacket decode(FriendlyByteBuf buf) {
        Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        double width = buf.readDouble();
        int coreColor = buf.readInt();
        int outerColor = buf.readInt();
        int duration = buf.readInt();
        return new FlameJetPacket(start, end, width, coreColor, outerColor, duration);
    }

    public static void handle(FlameJetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    FlameJetRenderer.renderFlameJet(
                            packet.start,
                            packet.end,
                            packet.width,
                            packet.coreColor,
                            packet.outerColor,
                            packet.duration
                    );
                });
            });
        }
        context.setPacketHandled(true);
    }

    public Vec3 getStart() { return start; }
    public Vec3 getEnd() { return end; }
    public double getWidth() { return width; }
    public int getCoreColor() { return coreColor; }
    public int getOuterColor() { return outerColor; }
    public int getDuration() { return duration; }
}