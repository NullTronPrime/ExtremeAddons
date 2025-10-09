package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.client.PouchClientData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet for syncing pouch dimension entity positions from server to client.
 * Sent periodically (every 0.5s) to update the tooltip view with real-time entity positions.
 */
public class PouchEntitySyncPacket {
    private final UUID pouchUUID;
    private final ListTag entityData;

    public PouchEntitySyncPacket(UUID pouchUUID, ListTag entityData) {
        this.pouchUUID = pouchUUID;
        this.entityData = entityData;
    }

    public static PouchEntitySyncPacket decode(FriendlyByteBuf buf) {
        UUID pouchUUID = buf.readUUID();
        CompoundTag wrapper = buf.readNbt();
        ListTag entityData = wrapper != null ? wrapper.getList("Entities", 10) : new ListTag();
        return new PouchEntitySyncPacket(pouchUUID, entityData);
    }

    public static void encode(PouchEntitySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.pouchUUID);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("Entities", msg.entityData);
        buf.writeNbt(wrapper);
    }

    public static void handle(PouchEntitySyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Run only on client side
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                PouchClientData.updatePouchData(msg.pouchUUID, msg.entityData);
            });
        });
        ctx.setPacketHandled(true);
    }
}