package net.autismicannoyance.exadditions.recipe;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ExAdditions.MOD_ID);

    public static final RegistryObject<RecipeType<AdvancedCraftingRecipe>> ADVANCED_CRAFTING_TYPE =
            RECIPE_TYPES.register("advanced_crafting", () -> RecipeType.simple(new ResourceLocation(ExAdditions.MOD_ID, "advanced_crafting")));

    public static void register(IEventBus eventBus) {
        RECIPE_TYPES.register(eventBus);
    }
}