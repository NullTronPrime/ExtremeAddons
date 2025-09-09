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
 * Network packet for spherical orbital eye system with color customization
 * Supports attachment to both items and entities
 */
public final class OrbitalEyeRenderPacket {
    public final int targetId; // Entity or item ID
    public final boolean isEntity; // true if attached to entity, false if item-based
    public final List<OrbitalEyeEntry> eyes;

    public OrbitalEyeRenderPacket(int targetId, boolean isEntity, List<OrbitalEyeEntry> eyes) {
        this.targetId = targetId;
        this.isEntity = isEntity;
        this.eyes = new ArrayList<>(eyes);
    }

    public static OrbitalEyeRenderPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        boolean isEntity = buf.readBoolean();
        int n = buf.readInt();
        List<OrbitalEyeEntry> list = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            // Position data
            double ox = buf.readDouble();
            double oy = buf.readDouble();
            double oz = buf.readDouble();

            // Size and appearance
            float radius = buf.readFloat();
            int scleraColor = buf.readInt();
            int pupilColor = buf.readInt();
            int irisColor = buf.readInt();

            // Animation state
            boolean firing = buf.readBoolean();
            boolean isBlinking = buf.readBoolean();
            float blinkPhase = buf.readFloat();

            // Laser data
            boolean hasLaserEnd = buf.readBoolean();
            Vec3 laserEnd = null;
            if (hasLaserEnd) {
                double lx = buf.readDouble();
                double ly = buf.readDouble();
                double lz = buf.readDouble();
                laserEnd = new Vec3(lx, ly, lz);
            }

            // Look direction
            boolean hasLookDir = buf.readBoolean();
            Vec3 lookDirection = null;
            if (hasLookDir) {
                double dx = buf.readDouble();
                double dy = buf.readDouble();
                double dz = buf.readDouble();
                lookDirection = new Vec3(dx, dy, dz);
            }

            // Pulse data
            boolean isPulsing = buf.readBoolean();
            float pulseIntensity = buf.readFloat();
            int laserColor = buf.readInt();

            list.add(new OrbitalEyeEntry(
                    new Vec3(ox, oy, oz), radius, scleraColor, pupilColor, irisColor,
                    firing, isBlinking, blinkPhase, laserEnd, lookDirection,
                    isPulsing, pulseIntensity, laserColor
            ));
        }

        return new OrbitalEyeRenderPacket(id, isEntity, list);
    }

    public static void encode(OrbitalEyeRenderPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetId);
        buf.writeBoolean(msg.isEntity);
        buf.writeInt(msg.eyes.size());

        for (OrbitalEyeEntry e : msg.eyes) {
            // Position data
            buf.writeDouble(e.offset.x);
            buf.writeDouble(e.offset.y);
            buf.writeDouble(e.offset.z);

            // Size and appearance
            buf.writeFloat(e.radius);
            buf.writeInt(e.scleraColor);
            buf.writeInt(e.pupilColor);
            buf.writeInt(e.irisColor);

            // Animation state
            buf.writeBoolean(e.firing);
            buf.writeBoolean(e.isBlinking);
            buf.writeFloat(e.blinkPhase);

            // Laser data
            if (e.laserEnd != null) {
                buf.writeBoolean(true);
                buf.writeDouble(e.laserEnd.x);
                buf.writeDouble(e.laserEnd.y);
                buf.writeDouble(e.laserEnd.z);
            } else {
                buf.writeBoolean(false);
            }

            // Look direction
            if (e.lookDirection != null) {
                buf.writeBoolean(true);
                buf.writeDouble(e.lookDirection.x);
                buf.writeDouble(e.lookDirection.y);
                buf.writeDouble(e.lookDirection.z);
            } else {
                buf.writeBoolean(false);
            }

            // Pulse data
            buf.writeBoolean(e.isPulsing);
            buf.writeFloat(e.pulseIntensity);
            buf.writeInt(e.laserColor);
        }
    }

    public static void handle(OrbitalEyeRenderPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.autismicannoyance.exadditions.client.OrbitalEyeRenderer.handlePacket(msg);
            });
        });
        ctx.setPacketHandled(true);
    }

    public static final class OrbitalEyeEntry {
        public final Vec3 offset;
        public final float radius;
        public final int scleraColor; // ARGB color for eye white
        public final int pupilColor;  // ARGB color for pupil
        public final int irisColor;   // ARGB color for iris
        public final boolean firing;
        public final boolean isBlinking;
        public final float blinkPhase; // 0.0 to 1.0
        public final Vec3 laserEnd;    // nullable
        public final Vec3 lookDirection; // nullable
        public final boolean isPulsing; // for laser pulse animation
        public final float pulseIntensity; // 0.0 to 1.0
        public final int laserColor;   // ARGB color for laser beam

        public OrbitalEyeEntry(Vec3 offset, float radius, int scleraColor, int pupilColor, int irisColor,
                               boolean firing, boolean isBlinking, float blinkPhase, Vec3 laserEnd, Vec3 lookDirection,
                               boolean isPulsing, float pulseIntensity, int laserColor) {
            this.offset = offset == null ? Vec3.ZERO : offset;
            this.radius = Math.max(0.1f, radius);
            this.scleraColor = scleraColor;
            this.pupilColor = pupilColor;
            this.irisColor = irisColor;
            this.firing = firing;
            this.isBlinking = isBlinking;
            this.blinkPhase = Math.max(0.0f, Math.min(1.0f, blinkPhase));
            this.laserEnd = laserEnd;
            this.lookDirection = lookDirection;
            this.isPulsing = isPulsing;
            this.pulseIntensity = Math.max(0.0f, Math.min(1.0f, pulseIntensity));
            this.laserColor = laserColor;
        }

        // Backward compatibility constructor with default colors
        public OrbitalEyeEntry(Vec3 offset, float radius, boolean firing, boolean isBlinking, float blinkPhase,
                               Vec3 laserEnd, Vec3 lookDirection, boolean isPulsing, float pulseIntensity) {
            this(offset, radius, 0xFF222222, 0xFFFFFFFF, 0xFF000000,
                    firing, isBlinking, blinkPhase, laserEnd, lookDirection,
                    isPulsing, pulseIntensity, 0xFFFF3333);
        }
    }
}