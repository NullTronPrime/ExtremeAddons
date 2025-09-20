package net.autismicannoyance.exadditions.screen;

import net.autismicannoyance.exadditions.block.entity.AdvancedCraftingTableBlockEntity;
import net.autismicannoyance.exadditions.recipe.AdvancedCraftingRecipe;
import net.autismicannoyance.exadditions.recipe.ModRecipeTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.SlotItemHandler;

import java.util.Optional;

public class AdvancedCraftingMenu extends AbstractContainerMenu {
    public final AdvancedCraftingTableBlockEntity blockEntity;
    private final Level level;
    private final ContainerData data;

    public AdvancedCraftingMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(2));
    }

    public AdvancedCraftingMenu(int pContainerId, Inventory inv, net.minecraft.world.level.block.entity.BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.ADVANCED_CRAFTING_MENU.get(), pContainerId);
        checkContainerSize(inv, 26);
        blockEntity = ((AdvancedCraftingTableBlockEntity) entity);
        this.level = inv.player.level();
        this.data = data;

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        this.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(iItemHandler -> {
            // Add 5x5 crafting grid (slots 0-24)
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 5; col++) {
                    this.addSlot(new SlotItemHandler(iItemHandler, row * 5 + col, 44 + col * 18, 17 + row * 18) {
                        @Override
                        public void setChanged() {
                            super.setChanged();
                            updateCraftingResult();
                        }
                    });
                }
            }

            // Add result slot (slot 25)
            this.addSlot(new AdvancedCraftingResultSlot(iItemHandler, 25, 152, 53));
        });

        addDataSlots(data);
    }

    private void updateCraftingResult() {
        if (!level.isClientSide()) {
            // First try advanced crafting (5x5)
            SimpleContainer fullContainer = new SimpleContainer(25);
            for (int i = 0; i < 25; i++) {
                fullContainer.setItem(i, blockEntity.getItemHandler().getStackInSlot(i));
            }

            Optional<AdvancedCraftingRecipe> advancedRecipe = level.getRecipeManager()
                    .getRecipeFor(ModRecipeTypes.ADVANCED_CRAFTING_TYPE.get(), fullContainer, level);

            if (advancedRecipe.isPresent()) {
                ItemStack result = advancedRecipe.get().assemble(fullContainer, level.registryAccess());
                blockEntity.getItemHandler().setStackInSlot(25, result);
                blockEntity.setChanged();
                return;
            }

            // Try regular 3x3 crafting in any position within the 5x5 grid
            ItemStack regularResult = tryRegularCrafting();
            blockEntity.getItemHandler().setStackInSlot(25, regularResult);
            blockEntity.setChanged();
        }
    }

    private ItemStack tryRegularCrafting() {
        // Try all possible 3x3 positions within the 5x5 grid
        for (int startRow = 0; startRow <= 2; startRow++) {
            for (int startCol = 0; startCol <= 2; startCol++) {
                CraftingContainer craftingContainer = new TransientCraftingContainer(this, 3, 3);

                // Fill the 3x3 container with items from the current position
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int gridIndex = (startRow + row) * 5 + (startCol + col);
                        ItemStack stackInSlot = blockEntity.getItemHandler().getStackInSlot(gridIndex);
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

                // Try to find a regular crafting recipe
                Optional<CraftingRecipe> recipe = level.getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, craftingContainer, level);

                if (recipe.isPresent()) {
                    return recipe.get().assemble(craftingContainer, level.registryAccess());
                }
            }
        }

        return ItemStack.EMPTY;
    }

    // Custom result slot that handles both advanced and regular crafting
    private class AdvancedCraftingResultSlot extends SlotItemHandler {
        public AdvancedCraftingResultSlot(net.minecraftforge.items.IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false; // Can't place items in result slot
        }

        @Override
        public void onTake(Player pPlayer, ItemStack pStack) {
            if (!level.isClientSide()) {
                // First check for advanced recipes
                SimpleContainer fullContainer = new SimpleContainer(25);
                for (int i = 0; i < 25; i++) {
                    fullContainer.setItem(i, blockEntity.getItemHandler().getStackInSlot(i));
                }

                Optional<AdvancedCraftingRecipe> advancedRecipe = level.getRecipeManager()
                        .getRecipeFor(ModRecipeTypes.ADVANCED_CRAFTING_TYPE.get(), fullContainer, level);

                if (advancedRecipe.isPresent()) {
                    // Handle advanced crafting consumption
                    for (int i = 0; i < 25; i++) {
                        ItemStack currentStack = blockEntity.getItemHandler().getStackInSlot(i);
                        if (!currentStack.isEmpty()) {
                            currentStack.shrink(1);
                            blockEntity.getItemHandler().setStackInSlot(i, currentStack);
                        }
                    }
                } else {
                    // Handle regular crafting consumption
                    consumeRegularCraftingIngredients();
                }

                updateCraftingResult();
            }

            super.onTake(pPlayer, pStack);
        }

        private void consumeRegularCraftingIngredients() {
            // Find which 3x3 area has the active recipe and consume those items
            for (int startRow = 0; startRow <= 2; startRow++) {
                for (int startCol = 0; startCol <= 2; startCol++) {
                    CraftingContainer craftingContainer = new TransientCraftingContainer(AdvancedCraftingMenu.this, 3, 3);

                    // Fill the 3x3 container
                    for (int row = 0; row < 3; row++) {
                        for (int col = 0; col < 3; col++) {
                            int gridIndex = (startRow + row) * 5 + (startCol + col);
                            ItemStack stackInSlot = blockEntity.getItemHandler().getStackInSlot(gridIndex);
                            craftingContainer.setItem(row * 3 + col, stackInSlot);
                        }
                    }

                    // Check if this matches a recipe
                    Optional<CraftingRecipe> recipe = level.getRecipeManager()
                            .getRecipeFor(RecipeType.CRAFTING, craftingContainer, level);

                    if (recipe.isPresent()) {
                        // Consume ingredients from this 3x3 area
                        for (int row = 0; row < 3; row++) {
                            for (int col = 0; col < 3; col++) {
                                int gridIndex = (startRow + row) * 5 + (startCol + col);
                                ItemStack currentStack = blockEntity.getItemHandler().getStackInSlot(gridIndex);
                                if (!currentStack.isEmpty()) {
                                    // Handle container items (like buckets)
                                    if (currentStack.hasCraftingRemainingItem()) {
                                        ItemStack remainingItem = currentStack.getCraftingRemainingItem();
                                        currentStack.shrink(1);
                                        if (currentStack.isEmpty()) {
                                            blockEntity.getItemHandler().setStackInSlot(gridIndex, remainingItem);
                                        } else {
                                            // Try to add remaining item to inventory or drop it
                                            // For simplicity, we'll just shrink for now
                                        }
                                    } else {
                                        currentStack.shrink(1);
                                        blockEntity.getItemHandler().setStackInSlot(gridIndex, currentStack);
                                    }
                                }
                            }
                        }
                        return; // Found and consumed the recipe, exit
                    }
                }
            }
        }
    }

    // Rest of the existing code remains the same...
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int VANILLA_FIRST_SLOT_INDEX = 0;
    private static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;
    private static final int TE_INVENTORY_SLOT_COUNT = 26;

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        if (index < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX
                    + TE_INVENTORY_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            System.out.println("Invalid slotIndex:" + index);
            return ItemStack.EMPTY;
        }

        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
                pPlayer, blockEntity.getBlockState().getBlock());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 118 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 176));
        }
    }
}