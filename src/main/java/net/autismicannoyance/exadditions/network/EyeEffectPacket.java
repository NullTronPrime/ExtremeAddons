package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.EyeWatcherRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class EyeEffectPacket {
    private final int entityId;
    private final int eyeCount;
    private final int lifetime;

    public EyeEffectPacket(int entityId, int eyeCount, int lifetime) {
        this.entityId = entityId;
        this.eyeCount = eyeCount;
        this.lifetime = lifetime;
    }

    public static EyeEffectPacket decode(FriendlyByteBuf buf) {
        int eid = buf.readInt();
        int count = buf.readInt();
        int life = buf.readInt();
        return new EyeEffectPacket(eid, count, life);
    }

    public static void encode(EyeEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.eyeCount);
        buf.writeInt(msg.lifetime);
    }

    public static void handle(EyeEffectPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                EyeWatcherRenderer.addEffect(msg.entityId, msg.eyeCount, msg.lifetime);
            });
        });
        ctx.setPacketHandled(true);
    }
}
