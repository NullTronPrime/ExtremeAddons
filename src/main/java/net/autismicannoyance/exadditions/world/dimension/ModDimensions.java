package net.autismicannoyance.exadditions.world.dimension;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModDimensions {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModDimensions.class);

    public static final ResourceKey<Level> VOID_DIM_LEVEL = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(ExAdditions.MOD_ID, "void_dim"));

    public static void register() {
        LOGGER.info("Registering ModDimensions for " + ExAdditions.MOD_ID);
    }
}