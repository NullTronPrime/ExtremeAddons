package net.autismicannoyance.exadditions.world.dimension;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import com.mojang.serialization.Codec;

public class ModChunkGenerators {
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, ExAdditions.MOD_ID);

    public static final RegistryObject<Codec<VoidDimensionChunkGenerator>> VOID_CHUNK_GENERATOR =
            CHUNK_GENERATORS.register("void_chunk_generator", () -> VoidDimensionChunkGenerator.CODEC);

    public static final RegistryObject<Codec<ArcanePouchChunkGenerator>> ARCANE_POUCH_CHUNK_GENERATOR =
            CHUNK_GENERATORS.register("arcane_pouch_chunk_generator", () -> ArcanePouchChunkGenerator.CODEC);

    public static void register(IEventBus eventBus) {
        CHUNK_GENERATORS.register(eventBus);
    }
}