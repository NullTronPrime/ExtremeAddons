package net.autismicannoyance.exadditions.effect;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ExAdditions.MOD_ID);

    public static final RegistryObject<MobEffect> ENDEROSIS =
            MOB_EFFECTS.register("enderosis", EnderosisEffect::new);

    public static final RegistryObject<MobEffect> TAUNT =
            MOB_EFFECTS.register("taunt", TauntEffect::new);

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
