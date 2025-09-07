package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Central networking registration for ExAdditions */
public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ExAdditions.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;
    private static int id() { return nextId++; }

    /** Call this during your mod common setup (FMLCommonSetupEvent). */
    public static void register() {
        CHANNEL.registerMessage(id(), CocoonEffectPacket.class,
                CocoonEffectPacket::encode, CocoonEffectPacket::decode, CocoonEffectPacket::handle);

        CHANNEL.registerMessage(id(), ChaosCrystalPacket.class,
                ChaosCrystalPacket::encode, ChaosCrystalPacket::decode, ChaosCrystalPacket::handle);

        CHANNEL.registerMessage(id(), EyeRenderPacket.class,
                EyeRenderPacket::encode, EyeRenderPacket::decode, EyeRenderPacket::handle);

        // Eye effect packet - send from server to clients to spawn the visual eyes
        CHANNEL.registerMessage(id(), EyeEffectPacket.class,
                EyeEffectPacket::encode, EyeEffectPacket::decode, EyeEffectPacket::handle);

        // Black hole effect packet - send from server to clients to spawn black hole visuals
        CHANNEL.registerMessage(id(), BlackHoleEffectPacket.class,
                BlackHoleEffectPacket::encode, BlackHoleEffectPacket::decode, BlackHoleEffectPacket::handle);

        // Laser attack packet - send from server to clients for bouncing laser effects
        CHANNEL.registerMessage(id(), LaserAttackPacket.class,
                LaserAttackPacket::encode, LaserAttackPacket::decode, LaserAttackPacket::handle);
    }
}