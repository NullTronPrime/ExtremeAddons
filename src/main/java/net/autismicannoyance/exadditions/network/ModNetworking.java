package net.autismicannoyance.exadditions.network;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;

public class ModNetworking {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ExAdditions.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextId = 0;

    /** Call during FMLCommonSetupEvent (or mod init) before sending any packets */
    public static void register() {
        // Register CocoonEffectPacket
        CHANNEL.registerMessage(
                nextId++,
                CocoonEffectPacket.class,
                CocoonEffectPacket::encode,
                CocoonEffectPacket::decode,
                CocoonEffectPacket::handle
        );
    }
}
