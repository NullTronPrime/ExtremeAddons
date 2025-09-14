package net.autismicannoyance.exadditions.entity;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.entity.custom.PlayerlikeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExAdditions.MOD_ID);

    public static final RegistryObject<EntityType<PlayerlikeEntity>> PLAYERLIKE = ENTITY_TYPES.register("playerlike",
            () -> EntityType.Builder.of(PlayerlikeEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f) // Same size as player
                    .build("playerlike"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}