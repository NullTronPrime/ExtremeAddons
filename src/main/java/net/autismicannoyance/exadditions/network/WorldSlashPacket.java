package net.autismicannoyance.exadditions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WorldSlashPacket {
    public enum SlashType {
        CURVED,
        FLYING
    }

    private final SlashType slashType;
    private final double startX, startY, startZ;
    private final double dirX, dirY, dirZ;
    private final double param1, param2, param3;

    public WorldSlashPacket(SlashType slashType, double startX, double startY, double startZ,
                            double dirX, double dirY, double dirZ, double param1, double param2, double param3) {
        this.slashType = slashType;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.param1 = param1;
        this.param2 = param2;
        this.param3 = param3;
    }

    public static void encode(WorldSlashPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.slashType);
        buffer.writeDouble(packet.startX);
        buffer.writeDouble(packet.startY);
        buffer.writeDouble(packet.startZ);
        buffer.writeDouble(packet.dirX);
        buffer.writeDouble(packet.dirY);
        buffer.writeDouble(packet.dirZ);
        buffer.writeDouble(packet.param1);
        buffer.writeDouble(packet.param2);
        buffer.writeDouble(packet.param3);
    }

    public static WorldSlashPacket decode(FriendlyByteBuf buffer) {
        SlashType slashType = buffer.readEnum(SlashType.class);
        double startX = buffer.readDouble();
        double startY = buffer.readDouble();
        double startZ = buffer.readDouble();
        double dirX = buffer.readDouble();
        double dirY = buffer.readDouble();
        double dirZ = buffer.readDouble();
        double param1 = buffer.readDouble();
        double param2 = buffer.readDouble();
        double param3 = buffer.readDouble();

        return new WorldSlashPacket(slashType, startX, startY, startZ, dirX, dirY, dirZ, param1, param2, param3);
    }

    public static void handle(WorldSlashPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.autismicannoyance.exadditions.client.WorldSlashRenderer.handleSlashPacket(packet);
            });
        });
        context.setPacketHandled(true);
    }

    public SlashType getSlashType() { return slashType; }
    public Vec3 getStartPos() { return new Vec3(startX, startY, startZ); }
    public Vec3 getDirection() { return new Vec3(dirX, dirY, dirZ); }
    public double getParam1() { return param1; }
    public double getParam2() { return param2; }
    public double getParam3() { return param3; }
}