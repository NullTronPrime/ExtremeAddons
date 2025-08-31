package net.autismicannoyance.exadditions.potion;

import net.autismicannoyance.exadditions.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public class LongEnderosisPotion extends Potion {

    public LongEnderosisPotion() {
        super(new MobEffectInstance(ModEffects.ENDEROSIS.get(), 9600, 0)); // 8 minutes, level 1
    }
}