package net.autismicannoyance.exadditions.util;

import net.autismicannoyance.exadditions.item.ModItems;
import net.autismicannoyance.exadditions.potion.ModPotions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public class ModBrewingRecipes {

    public static void registerBrewingRecipes(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Normal potions
            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD)),
                    Ingredient.of(ModItems.SAPPHIRE.get()),
                    PotionUtils.setPotion(new ItemStack(Items.POTION), ModPotions.ENDEROSIS_POTION.get())
            ));

            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.POTION), ModPotions.ENDEROSIS_POTION.get())),
                    Ingredient.of(Items.REDSTONE),
                    PotionUtils.setPotion(new ItemStack(Items.POTION), ModPotions.LONG_ENDEROSIS_POTION.get())
            ));

            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.POTION), ModPotions.ENDEROSIS_POTION.get())),
                    Ingredient.of(Items.GLOWSTONE_DUST),
                    PotionUtils.setPotion(new ItemStack(Items.POTION), ModPotions.STRONG_ENDEROSIS_POTION.get())
            ));

            // Splash potions
            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.AWKWARD)),
                    Ingredient.of(ModItems.SAPPHIRE.get()),
                    PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), ModPotions.ENDEROSIS_POTION.get())
            ));

            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), ModPotions.ENDEROSIS_POTION.get())),
                    Ingredient.of(Items.REDSTONE),
                    PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), ModPotions.LONG_ENDEROSIS_POTION.get())
            ));

            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), ModPotions.ENDEROSIS_POTION.get())),
                    Ingredient.of(Items.GLOWSTONE_DUST),
                    PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), ModPotions.STRONG_ENDEROSIS_POTION.get())
            ));

            // Lingering potions
            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.AWKWARD)),
                    Ingredient.of(ModItems.SAPPHIRE.get()),
                    PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), ModPotions.ENDEROSIS_POTION.get())
            ));

            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), ModPotions.ENDEROSIS_POTION.get())),
                    Ingredient.of(Items.REDSTONE),
                    PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), ModPotions.LONG_ENDEROSIS_POTION.get())
            ));

            BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                    Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), ModPotions.ENDEROSIS_POTION.get())),
                    Ingredient.of(Items.GLOWSTONE_DUST),
                    PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), ModPotions.STRONG_ENDEROSIS_POTION.get())
            ));
        });
    }
}
