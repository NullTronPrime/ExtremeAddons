package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.ChaosCrystalRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChaosCrystalPacket {
    private final int entityId;
    private final int lifetime;

    public ChaosCrystalPacket(int entityId, int lifetime) {
        this.entityId = entityId;
        this.lifetime = lifetime;
    }

    public static ChaosCrystalPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int lifetime = buf.readInt();
        return new ChaosCrystalPacket(entityId, lifetime);
    }

    public static void encode(ChaosCrystalPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.lifetime);
    }

    public static void handle(ChaosCrystalPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // run only on client side - safe guard
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                ChaosCrystalRenderer.addEffect(msg.entityId, msg.lifetime);
            });
        });
        ctx.setPacketHandled(true);
    }
}
