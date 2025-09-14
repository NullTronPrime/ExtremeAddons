package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.AdaptationWheelRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AdaptationWheelPacket {
    private final int entityId;
    private final float rotation;
    private final float resistanceLevel;

    public AdaptationWheelPacket(int entityId, float rotation, float resistanceLevel) {
        this.entityId = entityId;
        this.rotation = rotation;
        this.resistanceLevel = resistanceLevel;
    }

    public static void encode(AdaptationWheelPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.entityId);
        buffer.writeFloat(packet.rotation);
        buffer.writeFloat(packet.resistanceLevel);
    }

    public static AdaptationWheelPacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readInt();
        float rotation = buffer.readFloat();
        float resistanceLevel = buffer.readFloat();
        return new AdaptationWheelPacket(entityId, rotation, resistanceLevel);
    }

    public static void handle(AdaptationWheelPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Handle on client side
            AdaptationWheelRenderer.onWheelRotation(packet.entityId, packet.rotation, packet.resistanceLevel);
        });
        context.setPacketHandled(true);
    }
}