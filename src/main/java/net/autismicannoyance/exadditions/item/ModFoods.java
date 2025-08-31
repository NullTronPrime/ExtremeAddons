package net.autismicannoyance.exadditions.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

public class ModFoods {
    public static final FoodProperties STRAWBERRY = new FoodProperties.Builder().nutrition(2).fast()
            .saturationMod(0.2f).effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200), 0.1f).build();

    // Chaos fruit - high nutrition to justify the powerful effect
    public static final FoodProperties CHAOS_FRUIT = new FoodProperties.Builder()
            .nutrition(8)
            .saturationMod(0.8f)
            .alwaysEat() // Can be eaten even when full
            .build();
}