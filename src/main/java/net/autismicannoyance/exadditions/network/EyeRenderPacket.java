package net.autismicannoyance.exadditions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Enhanced server -> client packet with per-eye data including blinking state
 */
public final class EyeRenderPacket {
    public final int entityId;
    public final List<EyeEntry> eyes;

    public EyeRenderPacket(int entityId, List<EyeEntry> eyes) {
        this.entityId = entityId;
        this.eyes = new ArrayList<>(eyes);
    }

    public static EyeRenderPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        int n = buf.readInt();
        List<EyeEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double ox = buf.readDouble();
            double oy = buf.readDouble();
            double oz = buf.readDouble();
            boolean firing = buf.readBoolean();

            boolean hasEnd = buf.readBoolean();
            Vec3 end = null;
            if (hasEnd) {
                double ex = buf.readDouble();
                double ey = buf.readDouble();
                double ez = buf.readDouble();
                end = new Vec3(ex, ey, ez);
            }

            boolean hasLook = buf.readBoolean();
            Vec3 lookDir = null;
            if (hasLook) {
                double lx = buf.readDouble();
                double ly = buf.readDouble();
                double lz = buf.readDouble();
                lookDir = new Vec3(lx, ly, lz);
            }

            int hitEntityId = buf.readInt(); // -1 = none

            // Enhanced blinking data
            boolean isBlinking = buf.readBoolean();
            float blinkPhase = buf.readFloat(); // 0.0 to 1.0

            list.add(new EyeEntry(new Vec3(ox, oy, oz), firing, end, lookDir, hitEntityId, isBlinking, blinkPhase));
        }
        return new EyeRenderPacket(id, list);
    }

    public static void encode(EyeRenderPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.eyes.size());
        for (EyeEntry e : msg.eyes) {
            buf.writeDouble(e.offset.x);
            buf.writeDouble(e.offset.y);
            buf.writeDouble(e.offset.z);
            buf.writeBoolean(e.firing);

            if (e.laserEnd != null) {
                buf.writeBoolean(true);
                buf.writeDouble(e.laserEnd.x);
                buf.writeDouble(e.laserEnd.y);
                buf.writeDouble(e.laserEnd.z);
            } else {
                buf.writeBoolean(false);
            }

            if (e.lookDirection != null) {
                buf.writeBoolean(true);
                buf.writeDouble(e.lookDirection.x);
                buf.writeDouble(e.lookDirection.y);
                buf.writeDouble(e.lookDirection.z);
            } else {
                buf.writeBoolean(false);
            }

            buf.writeInt(e.hitEntityId);

            // Enhanced blinking data
            buf.writeBoolean(e.isBlinking);
            buf.writeFloat(e.blinkPhase);
        }
    }

    public static void handle(EyeRenderPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.autismicannoyance.exadditions.client.EyeStaffRenderer.handlePacket(msg);
            });
        });
        ctx.setPacketHandled(true);
    }

    public static final class EyeEntry {
        public final Vec3 offset;
        public final boolean firing;
        public final Vec3 laserEnd; // nullable
        public final Vec3 lookDirection; // nullable - direction the eye should face
        public final int hitEntityId; // -1 if none
        public final boolean isBlinking; // synchronized blinking state
        public final float blinkPhase; // 0.0 to 1.0 blink animation progress

        public EyeEntry(Vec3 offset, boolean firing, Vec3 laserEnd, Vec3 lookDirection, int hitEntityId, boolean isBlinking, float blinkPhase) {
            this.offset = offset == null ? Vec3.ZERO : offset;
            this.firing = firing;
            this.laserEnd = laserEnd;
            this.lookDirection = lookDirection;
            this.hitEntityId = hitEntityId;
            this.isBlinking = isBlinking;
            this.blinkPhase = blinkPhase;
        }

        // Backward compatibility constructor
        public EyeEntry(Vec3 offset, boolean firing, Vec3 laserEnd, Vec3 lookDirection, int hitEntityId) {
            this(offset, firing, laserEnd, lookDirection, hitEntityId, false, 0.0f);
        }
    }
}