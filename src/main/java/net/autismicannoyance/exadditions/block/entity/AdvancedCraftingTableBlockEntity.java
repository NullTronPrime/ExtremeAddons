package net.autismicannoyance.exadditions.block.entity;

import net.autismicannoyance.exadditions.recipe.AdvancedCraftingRecipe;
import net.autismicannoyance.exadditions.recipe.ModRecipeTypes;
import net.autismicannoyance.exadditions.screen.AdvancedCraftingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.level.block.entity.HopperBlockEntity;
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
    private boolean isUpdatingResult = false; // Flag to prevent recursive updates

    private final ItemStackHandler itemHandler = new ItemStackHandler(26) { // 25 crafting slots + 1 result slot
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // Update crafting result when input slots change, but NOT when result slot changes
            // Also prevent updates during result extraction
            if (slot < 25 && !isUpdatingResult) { // Only update for input slots, not result slot
                updateCraftingResult();
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot != 25; // Only result slot (25) is not valid for insertion
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == 25) {
                // When extracting from result slot, trigger crafting consumption
                ItemStack result = super.extractItem(slot, amount, simulate);
                if (!result.isEmpty() && !simulate) {
                    System.out.println("DEBUG: Result extracted from slot, amount: " + amount);

                    isUpdatingResult = true; // Prevent recursive updates

                    // Handle multiple crafts for shift-click
                    int craftsToPerform = Math.min(amount, result.getCount());
                    for (int i = 0; i < craftsToPerform; i++) {
                        System.out.println("DEBUG: Performing craft " + (i + 1) + "/" + craftsToPerform);
                        consumeIngredientsForBestRecipe();

                        // Check if we can still craft more
                        if (i < craftsToPerform - 1) {
                            ItemStack nextResult = calculateCraftingResult();
                            if (nextResult.isEmpty()) {
                                System.out.println("DEBUG: Cannot craft more, stopping at " + (i + 1));
                                break;
                            }
                        }
                    }

                    updateCraftingResult(); // Update for next craft
                    isUpdatingResult = false; // Re-enable updates
                }
                return result;
            }
            return super.extractItem(slot, amount, simulate);
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

        // Check if there's a hopper or container below before crafting
        BlockEntity blockEntityBelow = level.getBlockEntity(worldPosition.below());
        boolean hasOutputContainer = false;

        if (blockEntityBelow instanceof HopperBlockEntity) {
            hasOutputContainer = true;
        } else if (blockEntityBelow != null) {
            LazyOptional<IItemHandler> handlerBelow = blockEntityBelow.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
            hasOutputContainer = handlerBelow.isPresent();
        }

        // Only auto-craft if there's an output container
        if (!hasOutputContainer) {
            System.out.println("DEBUG: No output container found, skipping auto-craft");
            return;
        }

        // Check if result slot is empty or can accept more items
        ItemStack currentResult = itemHandler.getStackInSlot(25);
        ItemStack potentialResult = calculateCraftingResult();

        if (potentialResult.isEmpty()) return;

        // Check if we can add the result to the output slot
        if (currentResult.isEmpty()) {
            // Consume ingredients and set result
            consumeIngredientsForBestRecipe();
            ItemStack result = potentialResult.copy();

            // Try to push to container below first
            if (tryPushResultToContainer(result)) {
                // Successfully pushed, don't put in result slot
            } else {
                // Couldn't push, put in result slot
                itemHandler.setStackInSlot(25, result);
            }
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
        if (level == null || level.isClientSide() || isUpdatingResult) return;

        // DEBUG: Log what items we have (but less spammy)
        boolean hasItems = false;
        for (int i = 0; i < 25; i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }

        if (hasItems) {
            System.out.println("DEBUG: Updating crafting result with items in grid");
        }

        ItemStack result = calculateCraftingResult();
        System.out.println("DEBUG: Calculated result: " + result);
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
                    // DEBUG: Log what recipe we found
                    System.out.println("DEBUG: Found recipe: " + recipe.get().getId() + " (" + recipe.get().getClass().getSimpleName() + ")");

                    // Count non-empty items in this recipe
                    int itemCount = 0;
                    for (int i = 0; i < 9; i++) {
                        if (!craftingContainer.getItem(i).isEmpty()) {
                            itemCount++;
                        }
                    }

                    ItemStack result = recipe.get().assemble(wrappedContainer, level.registryAccess());
                    // FIXED: Pass the wrappedContainer here
                    validRecipes.add(new ValidRecipe(recipe.get(), result, itemCount, startRow, startCol, wrappedContainer));
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
            System.out.println("DEBUG: Using advanced crafting recipe");
            // Handle advanced crafting consumption (5x5 recipes don't support remaining items yet)
            for (int i = 0; i < 25; i++) {
                ItemStack currentStack = itemHandler.getStackInSlot(i);
                if (!currentStack.isEmpty()) {
                    currentStack.shrink(1);
                    itemHandler.setStackInSlot(i, currentStack);
                }
            }
            return;
        }

        // Handle regular 3x3 crafting consumption with proper remaining items support
        List<ValidRecipe> validRecipes = new ArrayList<>();

        // Find all valid recipes again (same logic as findBestRegularCraftingResult)
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

                // Check if this 3x3 area has any items
                boolean hasItems = false;
                for (int i = 0; i < 9; i++) {
                    if (!craftingContainer.getItem(i).isEmpty()) {
                        hasItems = true;
                        break;
                    }
                }

                if (!hasItems) continue;

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
                    validRecipes.add(new ValidRecipe(recipe.get(), result, itemCount, startRow, startCol, wrappedContainer));
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

            // DEBUG: Add extensive logging for sharpening recipe
            System.out.println("DEBUG: Recipe type: " + bestRecipe.recipe.getClass().getSimpleName());
            System.out.println("DEBUG: Recipe ID: " + bestRecipe.recipe.getId());

            // Check if this is specifically our sharpening recipe
            boolean isSharpeningRecipe = bestRecipe.recipe.getId().toString().contains("sharpened_diamond");
            System.out.println("DEBUG: Is sharpening recipe: " + isSharpeningRecipe);

            // Get remaining items from the recipe (this handles custom recipes properly)
            NonNullList<ItemStack> remainingItems = bestRecipe.recipe.getRemainingItems(bestRecipe.craftingContainer);

            // DEBUG: Print all input items
            System.out.println("DEBUG: Input items:");
            for (int i = 0; i < bestRecipe.craftingContainer.getContainerSize(); i++) {
                ItemStack input = bestRecipe.craftingContainer.getItem(i);
                if (!input.isEmpty()) {
                    System.out.println("  Slot " + i + ": " + input + " (damage: " + input.getDamageValue() + "/" + input.getMaxDamage() + ")");
                }
            }

            // DEBUG: Print remaining items
            System.out.println("DEBUG: Remaining items:");
            for (int i = 0; i < remainingItems.size(); i++) {
                ItemStack remaining = remainingItems.get(i);
                if (!remaining.isEmpty()) {
                    System.out.println("  Slot " + i + ": " + remaining + " (damage: " + remaining.getDamageValue() + "/" + remaining.getMaxDamage() + ")");
                }
            }

            // Apply the remaining items back to the grid
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int gridIndex = (bestRecipe.startRow + row) * 5 + (bestRecipe.startCol + col);
                    int craftingIndex = row * 3 + col;

                    System.out.println("DEBUG: Processing grid slot " + gridIndex + " (row=" + (bestRecipe.startRow + row) + ", col=" + (bestRecipe.startCol + col) + ") from crafting slot " + craftingIndex);

                    ItemStack currentStack = itemHandler.getStackInSlot(gridIndex);
                    if (!currentStack.isEmpty()) {
                        // Check if there's a remaining item for this slot
                        ItemStack remainingItem = remainingItems.get(craftingIndex);

                        if (!remainingItem.isEmpty()) {
                            // Set the remaining item (this handles damaged swords, etc.)
                            System.out.println("DEBUG: Setting remaining item at grid " + gridIndex + " (crafting " + craftingIndex + "): " + remainingItem + " (damage: " + remainingItem.getDamageValue() + ")");
                            itemHandler.setStackInSlot(gridIndex, remainingItem.copy());
                        } else {
                            // Standard consumption - reduce by 1
                            System.out.println("DEBUG: Standard consumption at grid " + gridIndex + " (crafting " + craftingIndex + ")");
                            if (currentStack.hasCraftingRemainingItem()) {
                                ItemStack containerItem = currentStack.getCraftingRemainingItem();
                                currentStack.shrink(1);
                                if (currentStack.isEmpty()) {
                                    itemHandler.setStackInSlot(gridIndex, containerItem);
                                }
                            } else {
                                currentStack.shrink(1);
                                itemHandler.setStackInSlot(gridIndex, currentStack);
                            }
                        }
                    }
                }
            }
        } else {
            System.out.println("DEBUG: No valid recipes found for consumption");
        }
    }

    // Helper method to try pushing result to container below
    private boolean tryPushResultToContainer(ItemStack result) {
        BlockEntity blockEntityBelow = level.getBlockEntity(worldPosition.below());

        if (blockEntityBelow instanceof HopperBlockEntity hopperBelow) {
            // Try to insert into the hopper
            for (int i = 0; i < 5; i++) { // Hopper has 5 slots
                ItemStack hopperStack = hopperBelow.getItem(i);
                if (hopperStack.isEmpty()) {
                    // Empty slot - put the result here
                    hopperBelow.setItem(i, result.copy());
                    return true;
                } else if (ItemStack.isSameItemSameTags(hopperStack, result) &&
                        hopperStack.getCount() + result.getCount() <= hopperStack.getMaxStackSize()) {
                    // Can stack with existing item
                    hopperStack.grow(result.getCount());
                    return true;
                }
            }
        }

        // If hopper didn't work, try with any IItemHandler below
        if (blockEntityBelow != null) {
            LazyOptional<IItemHandler> handlerBelow = blockEntityBelow.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);

            if (handlerBelow.isPresent()) {
                IItemHandler handler = handlerBelow.orElse(null);
                if (handler != null) {
                    // Try to insert into the item handler
                    ItemStack remaining = handler.insertItem(0, result.copy(), false);
                    return remaining.getCount() < result.getCount();
                }
            }
        }

        return false; // Couldn't push to any container
    }

    // Method called when the craft button is pressed in the GUI
    public void onCraftButtonPressed() {
        if (level == null || level.isClientSide()) return;

        System.out.println("DEBUG: Craft button pressed");

        // Get the current result
        ItemStack result = itemHandler.getStackInSlot(25);
        if (result.isEmpty()) {
            System.out.println("DEBUG: No result to craft");
            return;
        }

        System.out.println("DEBUG: Crafting result: " + result);

        // Always consume ingredients when button is pressed
        consumeIngredientsForBestRecipe();

        // Try to push to container below first
        if (tryPushResultToContainer(result)) {
            System.out.println("DEBUG: Pushed result to container below");
            // Successfully pushed, clear the result slot
            itemHandler.setStackInSlot(25, ItemStack.EMPTY);
        } else {
            System.out.println("DEBUG: No container below, keeping result in output slot");
            // No container below, keep result in output slot for manual collection
        }

        // Update the result for the next craft
        updateCraftingResult();
        setChanged();
    }

    // TEST METHOD - Call this from your GUI or command to test the button manually
    public void testCraftButton() {
        System.out.println("DEBUG: TEST - Manual craft button test");
        onCraftButtonPressed();
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

    // Updated ValidRecipe class with CraftingContainer
    private static class ValidRecipe {
        final CraftingRecipe recipe;
        final ItemStack result;
        final int itemCount;
        final int startRow;
        final int startCol;
        final CraftingContainer craftingContainer;

        ValidRecipe(CraftingRecipe recipe, ItemStack result, int itemCount, int startRow, int startCol, CraftingContainer craftingContainer) {
            this.recipe = recipe;
            this.result = result;
            this.itemCount = itemCount;
            this.startRow = startRow;
            this.startCol = startCol;
            this.craftingContainer = craftingContainer;
        }
    }
}