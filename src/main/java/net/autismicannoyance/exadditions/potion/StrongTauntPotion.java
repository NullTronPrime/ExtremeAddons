package net.autismicannoyance.exadditions.potion;

import net.autismicannoyance.exadditions.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public class StrongTauntPotion extends Potion {

    public StrongTauntPotion() {
        super(new MobEffectInstance(ModEffects.TAUNT.get(), 1800, 1)); // 1.5 minutes, level 2
    }
}