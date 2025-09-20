package net.autismicannoyance.exadditions.recipe;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ExAdditions.MOD_ID);

    public static final RegistryObject<RecipeSerializer<AdvancedCraftingRecipe>> ADVANCED_CRAFTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("advanced_crafting", AdvancedCraftingRecipeSerializer::new);

    // ADD THIS LINE:
    public static final RegistryObject<RecipeSerializer<SharpenedDiamondRecipe>> SHARPENED_DIAMOND_SERIALIZER =
            RECIPE_SERIALIZERS.register("sharpened_diamond", SharpenedDiamondRecipeSerializer::new);

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}