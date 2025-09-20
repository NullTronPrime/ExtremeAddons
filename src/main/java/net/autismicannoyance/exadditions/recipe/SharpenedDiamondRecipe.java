package net.autismicannoyance.exadditions.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.autismicannoyance.exadditions.item.ModItems;

public class SharpenedDiamondRecipe extends CustomRecipe {

    public SharpenedDiamondRecipe(ResourceLocation pId, CraftingBookCategory pCategory) {
        super(pId, pCategory);
    }

    @Override
    public boolean matches(CraftingContainer pContainer, Level pLevel) {
        ItemStack sword = ItemStack.EMPTY;
        ItemStack diamond = ItemStack.EMPTY;
        int itemCount = 0;

        // Check all slots in the crafting container
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
            if (!stack.isEmpty()) {
                itemCount++;

                if (stack.getItem() instanceof SwordItem) {
                    if (!sword.isEmpty()) {
                        return false; // Multiple swords not allowed
                    }
                    sword = stack;
                } else if (stack.is(Items.DIAMOND)) {
                    if (!diamond.isEmpty()) {
                        return false; // Multiple diamonds not allowed
                    }
                    diamond = stack;
                } else {
                    return false; // Invalid item
                }
            }
        }

        // Must have exactly one sword, one diamond, and nothing else
        return itemCount == 2 && !sword.isEmpty() && !diamond.isEmpty() &&
                sword.getDamageValue() < sword.getMaxDamage(); // Sword must not be broken
    }

    @Override
    public ItemStack assemble(CraftingContainer pContainer, RegistryAccess pRegistryAccess) {
        // Return the sharpened diamond
        return new ItemStack(ModItems.SHARPENED_DIAMOND.get());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer pContainer) {
        NonNullList<ItemStack> remainingItems = NonNullList.withSize(pContainer.getContainerSize(), ItemStack.EMPTY);

        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);

            if (stack.getItem() instanceof SwordItem) {
                // Create a copy of the sword with 1 less durability
                ItemStack damagedSword = stack.copy();
                damagedSword.setDamageValue(damagedSword.getDamageValue() + 1);

                // If the sword would break, return empty (consume it)
                if (damagedSword.getDamageValue() >= damagedSword.getMaxDamage()) {
                    remainingItems.set(i, ItemStack.EMPTY);
                } else {
                    remainingItems.set(i, damagedSword);
                }
            }
            // Diamond gets consumed (default empty behavior)
        }

        return remainingItems;
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return pWidth >= 2 && pHeight >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.SHARPENED_DIAMOND_SERIALIZER.get();
    }
}