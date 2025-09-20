package net.autismicannoyance.exadditions.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

public class AdvancedCraftingRecipeSerializer implements RecipeSerializer<AdvancedCraftingRecipe> {

    @Override
    public AdvancedCraftingRecipe fromJson(ResourceLocation pRecipeId, JsonObject pSerializedRecipe) {
        ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "output"));

        JsonArray ingredients = GsonHelper.getAsJsonArray(pSerializedRecipe, "ingredients");
        NonNullList<Ingredient> inputs = NonNullList.withSize(25, Ingredient.EMPTY);

        for (int i = 0; i < ingredients.size() && i < 25; i++) {
            inputs.set(i, Ingredient.fromJson(ingredients.get(i)));
        }

        return new AdvancedCraftingRecipe(inputs, output, pRecipeId);
    }

    @Override
    public @Nullable AdvancedCraftingRecipe fromNetwork(ResourceLocation pRecipeId, FriendlyByteBuf pBuffer) {
        NonNullList<Ingredient> inputs = NonNullList.withSize(pBuffer.readInt(), Ingredient.EMPTY);

        for (int i = 0; i < inputs.size(); i++) {
            inputs.set(i, Ingredient.fromNetwork(pBuffer));
        }

        ItemStack output = pBuffer.readItem();
        return new AdvancedCraftingRecipe(inputs, output, pRecipeId);
    }

    @Override
    public void toNetwork(FriendlyByteBuf pBuffer, AdvancedCraftingRecipe pRecipe) {
        pBuffer.writeInt(pRecipe.getRecipeItems().size());

        for (Ingredient ingredient : pRecipe.getRecipeItems()) {
            ingredient.toNetwork(pBuffer);
        }

        pBuffer.writeItemStack(pRecipe.getResultItem(null), false);
    }
}