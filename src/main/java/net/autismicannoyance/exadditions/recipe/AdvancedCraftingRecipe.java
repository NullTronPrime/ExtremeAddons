package net.autismicannoyance.exadditions.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class AdvancedCraftingRecipe implements Recipe<SimpleContainer> {
    private final NonNullList<Ingredient> recipeItems;
    private final ItemStack output;
    private final ResourceLocation id;

    public AdvancedCraftingRecipe(NonNullList<Ingredient> recipeItems, ItemStack output, ResourceLocation id) {
        this.recipeItems = recipeItems;
        this.output = output;
        this.id = id;
    }

    @Override
    public boolean matches(SimpleContainer pContainer, Level pLevel) {
        if (pLevel.isClientSide()) {
            return false;
        }

        // Check if the 5x5 pattern matches (slots 0-24)
        for (int i = 0; i < 25; i++) {
            if (i < recipeItems.size()) {
                if (!recipeItems.get(i).test(pContainer.getItem(i))) {
                    return false;
                }
            } else {
                // If recipe has fewer than 25 ingredients, remaining slots should be empty
                if (!pContainer.getItem(i).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(SimpleContainer pContainer, RegistryAccess pRegistryAccess) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return pWidth >= 5 && pHeight >= 5;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess pRegistryAccess) {
        return output.copy();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ADVANCED_CRAFTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.ADVANCED_CRAFTING_TYPE.get();
    }

    public NonNullList<Ingredient> getRecipeItems() {
        return recipeItems;
    }
}