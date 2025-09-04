package net.autismicannoyance.exadditions.network;

import com.mojang.blaze3d.platform.InputConstants;
import net.autismicannoyance.exadditions.client.CocoonRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CocoonEffectPacket {
    private final int entityId;
    private final int lifetime;

    public CocoonEffectPacket(int entityId, int lifetime) {
        this.entityId = entityId;
        this.lifetime = lifetime;
    }

    // Decoder used by the networking system
    public static CocoonEffectPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int lifetime = buf.readInt();
        return new CocoonEffectPacket(entityId, lifetime);
    }

    // Encoder used by the networking system
    public static void encode(CocoonEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.lifetime);
    }

    // Handler called when the packet is received (network thread)
    public static void handle(CocoonEffectPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // Run on main thread
        ctx.enqueueWork(() -> {
            // Only run client-side code — ensure this runs only on clients
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                // Add the effect to the client renderer
                // CocoonRenderer is client-only; it must exist in your client package
                CocoonRenderer.addEffect(msg.entityId, msg.lifetime);
            });
        });
        ctx.setPacketHandled(true);
    }
}
