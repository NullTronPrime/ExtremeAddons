package net.autismicannoyance.exadditions.potion;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, ExAdditions.MOD_ID);

    public static final RegistryObject<Potion> ENDEROSIS_POTION =
            POTIONS.register("enderosis", EnderosisPotion::new);

    public static final RegistryObject<Potion> LONG_ENDEROSIS_POTION =
            POTIONS.register("long_enderosis", LongEnderosisPotion::new);

    public static final RegistryObject<Potion> STRONG_ENDEROSIS_POTION =
            POTIONS.register("strong_enderosis", StrongEnderosisPotion::new);

    public static void register(IEventBus eventBus) {
        POTIONS.register(eventBus);
    }
}