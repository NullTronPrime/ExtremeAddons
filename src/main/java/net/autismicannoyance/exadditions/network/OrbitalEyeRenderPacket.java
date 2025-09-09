package net.autismicannoyance.exadditions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Network packet for anatomically correct orbital eye system with full articulation
 * Supports attachment to both items and entities with realistic eye behavior
 */
public final class OrbitalEyeRenderPacket {
    public final int targetId; // Entity or item ID
    public final boolean isEntity; // true if attached to entity, false if item-based
    public final List<OrbitalEyeEntry> eyes;

    public OrbitalEyeRenderPacket(int targetId, boolean isEntity, List<OrbitalEyeEntry> eyes) {
        this.targetId = targetId;
        this.isEntity = isEntity;
        this.eyes = eyes != null ? new ArrayList<>(eyes) : new ArrayList<>();
    }

    // Decode packet from network buffer
    public static OrbitalEyeRenderPacket decode(FriendlyByteBuf buf) {
        try {
            int id = buf.readInt();
            boolean isEntity = buf.readBoolean();
            int eyeCount = buf.readVarInt(); // Use VarInt for better compression

            List<OrbitalEyeEntry> list = new ArrayList<>(eyeCount);

            for (int i = 0; i < eyeCount; i++) {
                // Position data
                double ox = buf.readDouble();
                double oy = buf.readDouble();
                double oz = buf.readDouble();
                Vec3 offset = new Vec3(ox, oy, oz);

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
                Vec3 laserEnd = null;
                if (buf.readBoolean()) { // hasLaserEnd
                    double lx = buf.readDouble();
                    double ly = buf.readDouble();
                    double lz = buf.readDouble();
                    laserEnd = new Vec3(lx, ly, lz);
                }

                // Look direction
                Vec3 lookDirection = null;
                if (buf.readBoolean()) { // hasLookDir
                    double dx = buf.readDouble();
                    double dy = buf.readDouble();
                    double dz = buf.readDouble();
                    lookDirection = new Vec3(dx, dy, dz);
                }

                // Pulse data
                boolean isPulsing = buf.readBoolean();
                float pulseIntensity = buf.readFloat();
                int laserColor = buf.readInt();

                // Anatomical data (new fields)
                float bloodshotIntensity = buf.readFloat();
                float fatigueLevel = buf.readFloat();
                float pupilSize = buf.readFloat();

                list.add(new OrbitalEyeEntry(
                        offset, radius, scleraColor, pupilColor, irisColor,
                        firing, isBlinking, blinkPhase, laserEnd, lookDirection,
                        isPulsing, pulseIntensity, laserColor,
                        bloodshotIntensity, fatigueLevel, pupilSize
                ));
            }

            return new OrbitalEyeRenderPacket(id, isEntity, list);
        } catch (Exception e) {
            // Log error and return empty packet to prevent crashes
            System.err.println("Failed to decode OrbitalEyeRenderPacket: " + e.getMessage());
            return new OrbitalEyeRenderPacket(0, true, Collections.emptyList());
        }
    }

    // Encode packet to network buffer
    public static void encode(OrbitalEyeRenderPacket msg, FriendlyByteBuf buf) {
        try {
            buf.writeInt(msg.targetId);
            buf.writeBoolean(msg.isEntity);
            buf.writeVarInt(msg.eyes.size()); // Use VarInt for better compression

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

                // Anatomical data (new fields)
                buf.writeFloat(e.bloodshotIntensity);
                buf.writeFloat(e.fatigueLevel);
                buf.writeFloat(e.pupilSize);
            }
        } catch (Exception ex) {
            System.err.println("Failed to encode OrbitalEyeRenderPacket: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Handle packet on client side
    public static void handle(OrbitalEyeRenderPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Ensure we're on the client side before processing
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                try {
                    net.autismicannoyance.exadditions.client.OrbitalEyeRenderer.handlePacket(msg);
                } catch (Exception e) {
                    System.err.println("Failed to handle OrbitalEyeRenderPacket on client: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Enhanced orbital eye entry with full anatomical data
     */
    public static final class OrbitalEyeEntry {
        // Basic properties
        public final Vec3 offset;
        public final float radius;
        public final int scleraColor; // ARGB color for eye white
        public final int pupilColor;  // ARGB color for pupil
        public final int irisColor;   // ARGB color for iris
        public final int laserColor;  // ARGB color for laser beam

        // State flags
        public final boolean firing;
        public final boolean isBlinking;
        public final boolean isPulsing;

        // Animation values
        public final float blinkPhase; // 0.0 to 1.0
        public final float pulseIntensity; // 0.0 to 1.0
        public final float pupilSize; // Pupil size relative to iris

        // Direction vectors
        public final Vec3 laserEnd;    // nullable - where laser beam ends
        public final Vec3 lookDirection; // nullable - where eye is looking

        // Anatomical effects
        public final float bloodshotIntensity; // 0.0 to 1.0
        public final float fatigueLevel; // 0.0 to 1.0

        // Full constructor with all anatomical features
        public OrbitalEyeEntry(Vec3 offset, float radius, int scleraColor, int pupilColor, int irisColor,
                               boolean firing, boolean isBlinking, float blinkPhase, Vec3 laserEnd, Vec3 lookDirection,
                               boolean isPulsing, float pulseIntensity, int laserColor,
                               float bloodshotIntensity, float fatigueLevel, float pupilSize) {
            this.offset = offset != null ? offset : Vec3.ZERO;
            this.radius = Math.max(0.1f, radius);
            this.scleraColor = scleraColor;
            this.pupilColor = pupilColor;
            this.irisColor = irisColor;
            this.laserColor = laserColor;
            this.firing = firing;
            this.isBlinking = isBlinking;
            this.isPulsing = isPulsing;
            this.blinkPhase = Math.max(0.0f, Math.min(1.0f, blinkPhase));
            this.pulseIntensity = Math.max(0.0f, Math.min(1.0f, pulseIntensity));
            this.laserEnd = laserEnd;
            this.lookDirection = lookDirection;
            this.bloodshotIntensity = Math.max(0.0f, Math.min(1.0f, bloodshotIntensity));
            this.fatigueLevel = Math.max(0.0f, Math.min(1.0f, fatigueLevel));
            this.pupilSize = Math.max(0.1f, Math.min(1.0f, pupilSize));
        }

        // Backward compatibility constructor with default anatomical values
        public OrbitalEyeEntry(Vec3 offset, float radius, int scleraColor, int pupilColor, int irisColor,
                               boolean firing, boolean isBlinking, float blinkPhase, Vec3 laserEnd, Vec3 lookDirection,
                               boolean isPulsing, float pulseIntensity, int laserColor) {
            this(offset, radius, scleraColor, pupilColor, irisColor,
                    firing, isBlinking, blinkPhase, laserEnd, lookDirection,
                    isPulsing, pulseIntensity, laserColor,
                    0.0f, 0.0f, 0.25f); // Default anatomical values
        }

        // Simple constructor with default colors and minimal parameters
        public OrbitalEyeEntry(Vec3 offset, float radius, boolean firing, boolean isBlinking, float blinkPhase,
                               Vec3 laserEnd, Vec3 lookDirection, boolean isPulsing, float pulseIntensity) {
            this(offset, radius, 0xFFEEEEEE, 0xFF000000, 0xFF4A90E2,
                    firing, isBlinking, blinkPhase, laserEnd, lookDirection,
                    isPulsing, pulseIntensity, 0xFFFF3333);
        }

        // Factory methods for common eye types

        /**
         * Creates a basic eye entry with standard human colors
         */
        public static OrbitalEyeEntry createBasicEye(Vec3 offset, float radius) {
            return new OrbitalEyeEntry(
                    offset, radius,
                    0xFFFFFFFF, // White sclera
                    0xFF000000, // Black pupil
                    0xFF4A90E2, // Blue iris
                    false, false, 0.0f, null, null,
                    false, 0.0f, 0xFFFF3333
            );
        }

        /**
         * Creates a menacing red eye
         */
        public static OrbitalEyeEntry createRedEye(Vec3 offset, float radius, boolean firing, Vec3 laserEnd) {
            return new OrbitalEyeEntry(
                    offset, radius,
                    0xFFFFDDDD, // Slightly pink sclera
                    0xFF330000, // Dark red pupil
                    0xFFFF0000, // Red iris
                    firing, false, 0.0f, laserEnd, null,
                    firing, firing ? 0.8f : 0.0f, 0xFFFF0000,
                    firing ? 0.6f : 0.0f, firing ? 0.4f : 0.0f, firing ? 0.4f : 0.2f
            );
        }

        /**
         * Creates a mystical glowing eye
         */
        public static OrbitalEyeEntry createGlowingEye(Vec3 offset, float radius, int glowColor, boolean pulsing) {
            return new OrbitalEyeEntry(
                    offset, radius,
                    0xFFEEEEEE, // Light sclera
                    glowColor,  // Glowing pupil
                    glowColor,  // Matching iris
                    false, false, 0.0f, null, null,
                    pulsing, pulsing ? 0.7f : 0.0f, glowColor,
                    0.0f, 0.0f, 0.3f
            );
        }

        /**
         * Creates a tired/bloodshot eye
         */
        public static OrbitalEyeEntry createTiredEye(Vec3 offset, float radius, float fatigueLevel) {
            return new OrbitalEyeEntry(
                    offset, radius,
                    0xFFFFDDDD, // Pinkish sclera
                    0xFF000000, // Black pupil
                    0xFF666666, // Gray iris
                    false, true, 0.3f, null, null,
                    false, 0.0f, 0xFFFF3333,
                    fatigueLevel, fatigueLevel, 0.2f + fatigueLevel * 0.1f
            );
        }
    }
}