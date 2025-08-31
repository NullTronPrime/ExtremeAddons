package net.autismicannoyance.exadditions.potion;

import net.autismicannoyance.exadditions.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public class EnderosisPotion extends Potion {

    public EnderosisPotion() {
        super(new MobEffectInstance(ModEffects.ENDEROSIS.get(), 3600, 0)); // 3 minutes, level 1
    }
}