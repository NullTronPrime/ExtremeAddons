package net.autismicannoyance.exadditions.entity;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModEntityDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> DATA_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, ExAdditions.MOD_ID);

    public static void register(IEventBus eventBus) {
        DATA_SERIALIZERS.register(eventBus);
    }
}