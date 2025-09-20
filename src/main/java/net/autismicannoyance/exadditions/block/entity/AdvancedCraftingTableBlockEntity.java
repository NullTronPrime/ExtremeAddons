package net.autismicannoyance.exadditions.block.entity;

import net.autismicannoyance.exadditions.recipe.AdvancedCraftingRecipe;
import net.autismicannoyance.exadditions.recipe.ModRecipeTypes;
import net.autismicannoyance.exadditions.screen.AdvancedCraftingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdvancedCraftingTableBlockEntity extends BlockEntity implements MenuProvider {
    private final ItemStackHandler itemHandler = new ItemStackHandler(26) { // 25 crafting slots + 1 result slot
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // Update crafting result when input slots change
            if (slot < 25) { // Only update for input slots, not result slot
                updateCraftingResult();
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot != 25; // Only result slot (25) is not valid for insertion
        }
    };

    // Separate wrappers for automation
    private final LazyOptional<IItemHandler> inputHandler = LazyOptional.of(() -> new RangedWrapper(itemHandler, 0, 25));
    private final LazyOptional<IItemHandler> outputHandler = LazyOptional.of(() -> new RangedWrapper(itemHandler, 25, 26));
    private final LazyOptional<IItemHandler> combinedHandler = LazyOptional.of(() -> new CombinedInvWrapper(
            new RangedWrapper(itemHandler, 0, 25), // inputs
            new RangedWrapper(itemHandler, 25, 26) // output
    ));

    private boolean wasRedstoneSignal = false;
    private boolean currentRedstoneSignal = false;

    public AdvancedCraftingTableBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.ADVANCED_CRAFTING_TABLE_BE.get(), pPos, pBlockState);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return combinedHandler.cast(); // For GUI access
            }
            // Hopper automation
            if (side == Direction.DOWN) {
                return outputHandler.cast(); // Only output from bottom
            } else {
                return inputHandler.cast(); // Input from all other sides
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateCraftingResult();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inputHandler.invalidate();
        outputHandler.invalidate();
        combinedHandler.invalidate();
    }

    public void drops() {
        SimpleContainer inventory = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            inventory.setItem(i, itemHandler.getStackInSlot(i));
        }
        Containers.dropContents(this.level, this.worldPosition, inventory);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.exadditions.advanced_crafting_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new AdvancedCraftingMenu(pContainerId, pPlayerInventory, this, new SimpleContainerData(2));
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.put("inventory", itemHandler.serializeNBT());
        pTag.putBoolean("redstoneSignal", currentRedstoneSignal);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        itemHandler.deserializeNBT(pTag.getCompound("inventory"));
        currentRedstoneSignal = pTag.getBoolean("redstoneSignal");
        wasRedstoneSignal = currentRedstoneSignal;
    }

    public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
        if (pLevel.isClientSide()) {
            return;
        }

        // Check for redstone pulse (rising edge)
        if (currentRedstoneSignal && !wasRedstoneSignal) {
            performAutoCraft();
        }
        wasRedstoneSignal = currentRedstoneSignal;
    }

    public void setRedstoneSignal(boolean signal) {
        this.currentRedstoneSignal = signal;
    }

    // Auto-crafting when redstone pulse is received
    private void performAutoCraft() {
        if (level == null || level.isClientSide()) return;

        // Check if result slot is empty or can accept more items
        ItemStack currentResult = itemHandler.getStackInSlot(25);
        ItemStack potentialResult = calculateCraftingResult();

        if (potentialResult.isEmpty()) return;

        // Check if we can add the result to the output slot
        if (currentResult.isEmpty()) {
            // Consume ingredients and set result
            consumeIngredientsForBestRecipe();
            itemHandler.setStackInSlot(25, potentialResult.copy());
            setChanged();
        } else if (ItemStack.isSameItemSameTags(currentResult, potentialResult) &&
                currentResult.getCount() + potentialResult.getCount() <= currentResult.getMaxStackSize()) {
            // Stack with existing result
            consumeIngredientsForBestRecipe();
            currentResult.grow(potentialResult.getCount());
            setChanged();
        }
        // If we can't fit the result, don't craft
    }

    public void updateCraftingResult() {
        if (level == null || level.isClientSide()) return;

        ItemStack result = calculateCraftingResult();
        itemHandler.setStackInSlot(25, result);
        setChanged();
    }

    private ItemStack calculateCraftingResult() {
        // First try advanced crafting (5x5)
        SimpleContainer fullContainer = new SimpleContainer(25);
        for (int i = 0; i < 25; i++) {
            fullContainer.setItem(i, itemHandler.getStackInSlot(i));
        }

        Optional<AdvancedCraftingRecipe> advancedRecipe = level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.ADVANCED_CRAFTING_TYPE.get(), fullContainer, level);

        if (advancedRecipe.isPresent()) {
            return advancedRecipe.get().assemble(fullContainer, level.registryAccess());
        }

        // Try regular 3x3 crafting with priority system
        return findBestRegularCraftingResult();
    }

    private ItemStack findBestRegularCraftingResult() {
        List<ValidRecipe> validRecipes = new ArrayList<>();

        // Try all possible 3x3 positions within the 5x5 grid
        for (int startRow = 0; startRow <= 2; startRow++) {
            for (int startCol = 0; startCol <= 2; startCol++) {
                SimpleContainer craftingContainer = new SimpleContainer(9);

                // Fill the 3x3 container with items from the current position
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int gridIndex = (startRow + row) * 5 + (startCol + col);
                        ItemStack stackInSlot = itemHandler.getStackInSlot(gridIndex);
                        craftingContainer.setItem(row * 3 + col, stackInSlot);
                    }
                }

                // Check if this 3x3 area has any items
                boolean hasItems = false;
                for (int i = 0; i < 9; i++) {
                    if (!craftingContainer.getItem(i).isEmpty()) {
                        hasItems = true;
                        break;
                    }
                }

                if (!hasItems) continue;

                // Try to find a regular crafting recipe using CraftingContainer wrapper
                CraftingContainer wrappedContainer = new CraftingContainer() {
                    @Override
                    public int getWidth() { return 3; }

                    @Override
                    public int getHeight() { return 3; }

                    @Override
                    public List<ItemStack> getItems() {
                        List<ItemStack> items = new ArrayList<>();
                        for (int i = 0; i < 9; i++) {
                            items.add(craftingContainer.getItem(i));
                        }
                        return items;
                    }

                    @Override
                    public int getContainerSize() { return 9; }

                    @Override
                    public boolean isEmpty() { return craftingContainer.isEmpty(); }

                    @Override
                    public ItemStack getItem(int slot) { return craftingContainer.getItem(slot); }

                    @Override
                    public ItemStack removeItem(int slot, int amount) {
                        return craftingContainer.removeItem(slot, amount);
                    }

                    @Override
                    public ItemStack removeItemNoUpdate(int slot) {
                        return craftingContainer.removeItemNoUpdate(slot);
                    }

                    @Override
                    public void setItem(int slot, ItemStack stack) {
                        craftingContainer.setItem(slot, stack);
                    }

                    @Override
                    public void setChanged() { craftingContainer.setChanged(); }

                    @Override
                    public boolean stillValid(Player player) { return true; }

                    @Override
                    public void clearContent() { craftingContainer.clearContent(); }

                    @Override
                    public void fillStackedContents(StackedContents stackedContents) {
                        for (int i = 0; i < 9; i++) {
                            stackedContents.accountStack(craftingContainer.getItem(i));
                        }
                    }
                };

                Optional<CraftingRecipe> recipe = level.getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, wrappedContainer, level);

                if (recipe.isPresent()) {
                    // Count non-empty items in this recipe
                    int itemCount = 0;
                    for (int i = 0; i < 9; i++) {
                        if (!craftingContainer.getItem(i).isEmpty()) {
                            itemCount++;
                        }
                    }

                    ItemStack result = recipe.get().assemble(wrappedContainer, level.registryAccess());
                    validRecipes.add(new ValidRecipe(recipe.get(), result, itemCount, startRow, startCol));
                }
            }
        }

        // Find the recipe that uses the most items (prioritize more complex recipes)
        if (!validRecipes.isEmpty()) {
            ValidRecipe bestRecipe = validRecipes.get(0);
            for (ValidRecipe recipe : validRecipes) {
                if (recipe.itemCount > bestRecipe.itemCount) {
                    bestRecipe = recipe;
                }
            }
            return bestRecipe.result;
        }

        return ItemStack.EMPTY;
    }

    public void consumeIngredientsForBestRecipe() {
        // First check for advanced recipes
        SimpleContainer fullContainer = new SimpleContainer(25);
        for (int i = 0; i < 25; i++) {
            fullContainer.setItem(i, itemHandler.getStackInSlot(i));
        }

        Optional<AdvancedCraftingRecipe> advancedRecipe = level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.ADVANCED_CRAFTING_TYPE.get(), fullContainer, level);

        if (advancedRecipe.isPresent()) {
            // Handle advanced crafting consumption
            for (int i = 0; i < 25; i++) {
                ItemStack currentStack = itemHandler.getStackInSlot(i);
                if (!currentStack.isEmpty()) {
                    currentStack.shrink(1);
                    itemHandler.setStackInSlot(i, currentStack);
                }
            }
            return;
        }

        // Handle regular crafting consumption
        List<ValidRecipe> validRecipes = new ArrayList<>();

        // Find all valid recipes again
        for (int startRow = 0; startRow <= 2; startRow++) {
            for (int startCol = 0; startCol <= 2; startCol++) {
                SimpleContainer craftingContainer = new SimpleContainer(9);

                // Fill the 3x3 container
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int gridIndex = (startRow + row) * 5 + (startCol + col);
                        ItemStack stackInSlot = itemHandler.getStackInSlot(gridIndex);
                        craftingContainer.setItem(row * 3 + col, stackInSlot);
                    }
                }

                // Check if this matches a recipe using CraftingContainer wrapper
                CraftingContainer wrappedContainer = new CraftingContainer() {
                    @Override
                    public int getWidth() { return 3; }

                    @Override
                    public int getHeight() { return 3; }

                    @Override
                    public List<ItemStack> getItems() {
                        List<ItemStack> items = new ArrayList<>();
                        for (int i = 0; i < 9; i++) {
                            items.add(craftingContainer.getItem(i));
                        }
                        return items;
                    }

                    @Override
                    public int getContainerSize() { return 9; }

                    @Override
                    public boolean isEmpty() { return craftingContainer.isEmpty(); }

                    @Override
                    public ItemStack getItem(int slot) { return craftingContainer.getItem(slot); }

                    @Override
                    public ItemStack removeItem(int slot, int amount) {
                        return craftingContainer.removeItem(slot, amount);
                    }

                    @Override
                    public ItemStack removeItemNoUpdate(int slot) {
                        return craftingContainer.removeItemNoUpdate(slot);
                    }

                    @Override
                    public void setItem(int slot, ItemStack stack) {
                        craftingContainer.setItem(slot, stack);
                    }

                    @Override
                    public void setChanged() { craftingContainer.setChanged(); }

                    @Override
                    public boolean stillValid(Player player) { return true; }

                    @Override
                    public void clearContent() { craftingContainer.clearContent(); }

                    @Override
                    public void fillStackedContents(StackedContents stackedContents) {
                        for (int i = 0; i < 9; i++) {
                            stackedContents.accountStack(craftingContainer.getItem(i));
                        }
                    }
                };

                Optional<CraftingRecipe> recipe = level.getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, wrappedContainer, level);

                if (recipe.isPresent()) {
                    // Count non-empty items in this recipe
                    int itemCount = 0;
                    for (int i = 0; i < 9; i++) {
                        if (!craftingContainer.getItem(i).isEmpty()) {
                            itemCount++;
                        }
                    }

                    ItemStack result = recipe.get().assemble(wrappedContainer, level.registryAccess());
                    validRecipes.add(new ValidRecipe(recipe.get(), result, itemCount, startRow, startCol));
                }
            }
        }

        // Find the recipe that uses the most items (same priority logic)
        if (!validRecipes.isEmpty()) {
            ValidRecipe bestRecipe = validRecipes.get(0);
            for (ValidRecipe recipe : validRecipes) {
                if (recipe.itemCount > bestRecipe.itemCount) {
                    bestRecipe = recipe;
                }
            }

            // Consume ingredients from the best recipe's 3x3 area
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int gridIndex = (bestRecipe.startRow + row) * 5 + (bestRecipe.startCol + col);
                    ItemStack currentStack = itemHandler.getStackInSlot(gridIndex);
                    if (!currentStack.isEmpty()) {
                        // Handle container items (like buckets)
                        if (currentStack.hasCraftingRemainingItem()) {
                            ItemStack remainingItem = currentStack.getCraftingRemainingItem();
                            currentStack.shrink(1);
                            if (currentStack.isEmpty()) {
                                itemHandler.setStackInSlot(gridIndex, remainingItem);
                            }
                        } else {
                            currentStack.shrink(1);
                            itemHandler.setStackInSlot(gridIndex, currentStack);
                        }
                    }
                }
            }
        }
    }

    // Comparator support - calculate fill level based on input slots
    public int getComparatorLevel() {
        int filledSlots = 0;
        int totalInputSlots = 25; // Only count input slots for comparator

        for (int i = 0; i < totalInputSlots; i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                filledSlots++;
            }
        }

        if (filledSlots == 0) {
            return 0;
        }

        return 1 + (filledSlots * 14) / totalInputSlots; // Scale to 1-15 redstone levels
    }

    public ItemStackHandler getItemHandler() {
        return this.itemHandler;
    }

    // Helper class to store valid recipes with their item counts
    private static class ValidRecipe {
        final CraftingRecipe recipe;
        final ItemStack result;
        final int itemCount;
        final int startRow;
        final int startCol;

        ValidRecipe(CraftingRecipe recipe, ItemStack result, int itemCount, int startRow, int startCol) {
            this.recipe = recipe;
            this.result = result;
            this.itemCount = itemCount;
            this.startRow = startRow;
            this.startCol = startCol;
        }
    }
}