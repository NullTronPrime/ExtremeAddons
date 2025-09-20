package net.autismicannoyance.exadditions.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

public class SharpenedDiamondRecipeSerializer implements RecipeSerializer<SharpenedDiamondRecipe> {

    @Override
    public SharpenedDiamondRecipe fromJson(ResourceLocation pRecipeId, JsonObject pSerializedRecipe) {
        return new SharpenedDiamondRecipe(pRecipeId, CraftingBookCategory.MISC);
    }

    @Override
    public @Nullable SharpenedDiamondRecipe fromNetwork(ResourceLocation pRecipeId, FriendlyByteBuf pBuffer) {
        return new SharpenedDiamondRecipe(pRecipeId, CraftingBookCategory.MISC);
    }

    @Override
    public void toNetwork(FriendlyByteBuf pBuffer, SharpenedDiamondRecipe pRecipe) {
        // No additional data to serialize for this recipe
    }
}