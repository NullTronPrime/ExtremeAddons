package net.autismicannoyance.exadditions.item.custom;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class SuperBundleItem extends Item {
    private static final String TAG_ITEMS = "Items";
    public static final int MAX_WEIGHT = 256;
    private static final int ITEM_BAR_COLOR = Mth.color(0.4F, 0.4F, 1.0F);

    public SuperBundleItem(Properties properties) {
        super(properties);
    }

    // -------- Capacity / fullness --------
    public static float getFullnessDisplay(ItemStack stack) {
        return (float) getContentWeight(stack) / (float) MAX_WEIGHT;
    }

    // -------- Right-click to dump contents --------
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bundle = player.getItemInHand(hand);
        if (dropContents(bundle, player)) {
            playDropContentsSound(player);
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(bundle, level.isClientSide());
        }
        return InteractionResultHolder.fail(bundle);
    }

    // -------- Stacking behavior --------
    @Override
    public boolean overrideStackedOnOther(ItemStack bundle, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || bundle.getCount() != 1) {
            return false;
        }

        ItemStack slotItem = slot.getItem();

        if (slotItem.isEmpty()) {
            // Take one item *out* of the bundle
            ItemStack removed = removeOne(bundle);
            if (!removed.isEmpty()) {
                playRemoveOneSound(player);
                slot.set(removed); // put the removed item into the slot
                return true;
            }
        } else if (slotItem.getItem().canFitInsideContainerItems()) {
            // Try to add one or more of the clicked stack *into the bundle*
            ItemStack original = slotItem.copy();
            ItemStack remainder = add(bundle, slotItem);

            if (remainder.getCount() != original.getCount()) {
                playInsertSound(player);
                slot.set(remainder); // shrink the stack in the slot
                return true;
            }
        }

        return false;
    }



    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bundle, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || bundle.getCount() != 1) {
            return false;
        }

        if (other.isEmpty()) {
            // Take one item out of the bundle and move it to cursor
            ItemStack removed = removeOne(bundle);
            if (!removed.isEmpty()) {
                playRemoveOneSound(player);
                access.set(removed); // set the cursor to the removed item
                return true;
            }
        } else if (other.getItem().canFitInsideContainerItems()) {
            // Put cursor stack into the bundle
            ItemStack original = other.copy();
            ItemStack remainder = add(bundle, other);

            if (remainder.getCount() != original.getCount()) {
                playInsertSound(player);
                access.set(remainder); // cursor now has leftover items
                return true;
            }
        }

        return false;
    }
    // -------- Tooltip UI (icons + progress bar) --------
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new SuperBundleTooltip(getContents(stack), getContentWeight(stack), MAX_WEIGHT));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "item.minecraft.bundle.fullness",
                getContentWeight(stack),
                MAX_WEIGHT
        ).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getContentWeight(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(1 + 12 * getContentWeight(stack) / MAX_WEIGHT, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ITEM_BAR_COLOR;
    }

    // -------- Helpers --------
    private static boolean dropContents(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_ITEMS)) return false;

        if (player instanceof ServerPlayer serverPlayer) {
            ListTag listTag = tag.getList(TAG_ITEMS, 10);
            for (int i = 0; i < listTag.size(); ++i) {
                CompoundTag itemTag = listTag.getCompound(i);
                ItemStack itemStack = ItemStack.of(itemTag);
                serverPlayer.drop(itemStack, true);
            }
        }

        stack.removeTagKey(TAG_ITEMS);
        return true;
    }

    private static ItemStack removeOne(ItemStack bundle) {
        CompoundTag tag = bundle.getOrCreateTag();
        if (!tag.contains(TAG_ITEMS)) return ItemStack.EMPTY;

        ListTag listTag = tag.getList(TAG_ITEMS, 10);
        if (listTag.isEmpty()) return ItemStack.EMPTY;

        int last = listTag.size() - 1;
        CompoundTag itemTag = listTag.getCompound(last);
        listTag.remove(last);

        if (listTag.isEmpty()) bundle.removeTagKey(TAG_ITEMS);

        return ItemStack.of(itemTag);
    }

    private static ItemStack add(ItemStack bundle, ItemStack stack) {
        if (stack.isEmpty() || !stack.getItem().canFitInsideContainerItems()) return stack;

        CompoundTag tag = bundle.getOrCreateTag();
        if (!tag.contains(TAG_ITEMS)) {
            tag.put(TAG_ITEMS, new ListTag());
        }

        int currentWeight = getContentWeight(bundle);
        int itemWeight = getWeight(stack);
        int availableWeight = MAX_WEIGHT - currentWeight;

        if (availableWeight < itemWeight) return stack;

        NonNullList<ItemStack> contents = getContents(bundle);
        for (ItemStack existing : contents) {
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                int canAdd = Math.min(64 - existing.getCount(), stack.getCount());
                canAdd = Math.min(canAdd, availableWeight / itemWeight);
                if (canAdd > 0) {
                    existing.grow(canAdd);
                    stack.shrink(canAdd);
                }
                saveContents(bundle, contents);
                return stack;
            }
        }

        int toAdd = Math.min(stack.getCount(), Math.min(64, availableWeight / itemWeight));
        if (toAdd > 0) {
            ItemStack newStack = stack.copy();
            newStack.setCount(toAdd);
            contents.add(newStack);
            stack.shrink(toAdd);
        }

        saveContents(bundle, contents);
        return stack;
    }

    private static NonNullList<ItemStack> getContents(ItemStack stack) {
        NonNullList<ItemStack> contents = NonNullList.create();
        CompoundTag tag = stack.getOrCreateTag();

        if (tag.contains(TAG_ITEMS)) {
            ListTag listTag = tag.getList(TAG_ITEMS, 10);
            for (int i = 0; i < listTag.size(); ++i) {
                CompoundTag itemTag = listTag.getCompound(i);
                ItemStack itemStack = ItemStack.of(itemTag);
                if (!itemStack.isEmpty()) contents.add(itemStack);
            }
        }
        return contents;
    }

    private static void saveContents(ItemStack bundle, NonNullList<ItemStack> contents) {
        CompoundTag tag = bundle.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (ItemStack itemStack : contents) {
            CompoundTag itemTag = new CompoundTag();
            itemStack.save(itemTag);
            listTag.add(itemTag);
        }
        tag.put(TAG_ITEMS, listTag);
    }

    private static int getWeight(ItemStack stack) {
        // Prevent nesting shulker boxes or chests that have NBT (contents, custom names, etc.)
        if (stack.is(net.minecraft.world.item.Items.SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.WHITE_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.ORANGE_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.MAGENTA_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.LIGHT_BLUE_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.YELLOW_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.LIME_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.PINK_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.GRAY_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.LIGHT_GRAY_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.CYAN_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.PURPLE_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.BLUE_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.BROWN_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.GREEN_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.RED_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.BLACK_SHULKER_BOX)
                || stack.is(net.minecraft.world.item.Items.CHEST)
                || stack.is(net.minecraft.world.item.Items.TRAPPED_CHEST)) {

            // If the item has NBT (contents, BlockEntityTag, etc.), disallow it
            if (stack.hasTag()) {
                return Integer.MAX_VALUE; // will never fit
            }
        }

        // Bundles inside bundles get special treatment
        if (stack.is(net.minecraft.world.item.Items.BUNDLE)) {
            return 4 + getContentWeight(stack);
        }

        // Special handling for beehives with bees
        if ((stack.is(net.minecraft.world.item.Items.BEEHIVE) || stack.is(net.minecraft.world.item.Items.BEE_NEST)) && stack.hasTag()) {
            CompoundTag compoundTag = stack.getTag();
            if (compoundTag.contains("BlockEntityTag")) {
                CompoundTag blockEntityTag = compoundTag.getCompound("BlockEntityTag");
                if (blockEntityTag.contains("Bees")) {
                    return 64; // heavy if bees are inside
                }
            }
        }

        // Default weight formula
        return 64 / stack.getMaxStackSize();
    }


    private static int getContentWeight(ItemStack stack) {
        return getContents(stack).stream()
                .mapToInt(s -> getWeight(s) * s.getCount())
                .sum();
    }

    // -------- Drop contents when destroyed --------
    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        ItemStack bundle = itemEntity.getItem();
        NonNullList<ItemStack> contents = getContents(bundle);

        Level level = itemEntity.level();
        double x = itemEntity.getX();
        double y = itemEntity.getY();
        double z = itemEntity.getZ();

        for (ItemStack stack : contents) {
            ItemEntity newEntity = new ItemEntity(level, x, y, z, stack);
            newEntity.setDeltaMovement(
                    (level.random.nextDouble() - 0.5) * 0.2,
                    level.random.nextDouble() * 0.2,
                    (level.random.nextDouble() - 0.5) * 0.2
            );
            level.addFreshEntity(newEntity);
        }
    }

    // -------- Sounds --------
    private void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playDropContentsSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    // ======================================================================
    // Inner tooltip classes
    // ======================================================================

    public static class SuperBundleTooltip implements TooltipComponent {
        private final List<ItemStack> items;
        private final int weight;
        private final int maxWeight;

        public SuperBundleTooltip(List<ItemStack> items, int weight, int maxWeight) {
            this.items = items;
            this.weight = weight;
            this.maxWeight = maxWeight;
        }

        public List<ItemStack> getItems() {
            return items;
        }

        public int getWeight() {
            return weight;
        }

        public int getMaxWeight() {
            return maxWeight;
        }
    }

    public static class SuperBundleTooltipRenderer implements ClientTooltipComponent {
        private final List<ItemStack> items;
        private final int weight;
        private final int maxWeight;

        public SuperBundleTooltipRenderer(SuperBundleTooltip tooltip) {
            this.items = tooltip.getItems();
            this.weight = tooltip.getWeight();
            this.maxWeight = tooltip.getMaxWeight();
        }

        @Override
        public int getHeight() {
            return getRows() * 20 + 6;
        }

        @Override
        public int getWidth(Font font) {
            return getColumns() * 18;
        }

        private int getColumns() {
            return Math.min(items.size(), 9);
        }

        private int getRows() {
            return (int) Math.ceil(items.size() / 9.0);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            PoseStack pose = graphics.pose();

            int columns = getColumns();
            int rows = getRows();

            // Draw items in grid
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    int index = row * 9 + col;
                    if (index >= items.size()) break;
                    ItemStack stack = items.get(index);
                    int itemX = x + col * 18;
                    int itemY = y + row * 20;
                    graphics.renderItem(stack, itemX, itemY);
                    graphics.renderItemDecorations(font, stack, itemX, itemY);
                }
            }

            // Draw progress bar
            int barWidth = (int) ((float) weight / (float) maxWeight * (columns * 18 - 2));
            int barX = x + 1;
            int barY = y + rows * 20 + 2;

            graphics.fill(barX, barY, barX + barWidth, barY + 3, 0xFF6666FF);
        }
    }
}
