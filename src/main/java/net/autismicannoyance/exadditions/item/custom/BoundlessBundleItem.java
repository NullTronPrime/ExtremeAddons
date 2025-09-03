package net.autismicannoyance.exadditions.item.custom;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BoundlessBundleItem extends Item {
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS; // client: show hand swing, server does work

        BlockEntity be = level.getBlockEntity(ctx.getClickedPos());
        if (be == null) return InteractionResult.PASS;

        // Try the face first, then a general lookup
        LazyOptional<IItemHandler> opt = be.getCapability(ForgeCapabilities.ITEM_HANDLER, ctx.getClickedFace());
        if (!opt.isPresent()) opt = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        if (!opt.isPresent()) return InteractionResult.PASS;

        IItemHandler handler = opt.orElse(null);
        if (handler == null) return InteractionResult.PASS;

        Player player = ctx.getPlayer();
        ItemStack bundle = ctx.getItemInHand();
        // only handle single bundle stacks (like your other overrides)
        if (bundle.getItem() != this || bundle.getCount() != 1) return InteractionResult.PASS;

        int beforeWeight = getContentWeight(bundle);
        int slots = handler.getSlots();

        // Go through each slot and try to move everything into the bundle
        for (int slot = 0; slot < slots; slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (inSlot.isEmpty()) continue;

            // First, simulate how many the bundle would accept
            ItemStack sim = inSlot.copy();
            ItemStack remainderAfterSim = add(bundle, sim); // this mutates sim; we only care about the delta
            int accepted = inSlot.getCount() - remainderAfterSim.getCount();

            if (accepted <= 0) continue; // nothing accepted from this slot

            // Actually extract exactly the accepted amount from the handler
            ItemStack extracted = handler.extractItem(slot, accepted, false);
            if (extracted.isEmpty()) continue;

            // Now add the actually-extracted stack to the bundle (authoritative add)
            ItemStack leftover = add(bundle, extracted);

            // If, for any reason, some couldn't fit (race with previous adds), try to put it back.
            if (!leftover.isEmpty()) {
                // Try to reinsert into any slot
                ItemStack toReturn = leftover;
                for (int s = 0; s < slots && !toReturn.isEmpty(); s++) {
                    toReturn = handler.insertItem(s, toReturn, false);
                }
                // If still leftover, drop near player to avoid losing items
                if (!toReturn.isEmpty() && player != null) {
                    player.drop(toReturn, false);
                }
            }

            // Early out if bundle is full
            if (getContentWeight(bundle) >= MAX_WEIGHT) break;
        }

        int afterWeight = getContentWeight(bundle);
        if (player != null && afterWeight > beforeWeight) {
            playInsertSound(player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        // If we moved anything, consume the interaction so the container GUI doesn't open
        return (afterWeight > beforeWeight) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    private static final String TAG_ITEMS = "Items";

    public static final int MAX_WEIGHT = 65_536; // total item capacity

    // Tooltip rendering defaults (same look as SuperBundle)
    private static final int DEFAULT_COLUMNS = 9;
    private static final int DEFAULT_CELL = 18;
    private static final int DEFAULT_ROW_HEIGHT = 20;
    private static final float SCREEN_RATIO = 0.80f;
    private static final int MAX_ICONS_RENDER = 9 * 64; // safety cap for rendering icons

    public BoundlessBundleItem(Properties properties) {
        super(properties);
    }

    // ---------------- full/tooltip/bar ----------------

    public static float getFullnessDisplay(ItemStack stack) {
        return (float) getContentWeight(stack) / (float) MAX_WEIGHT;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        // Build tooltip data with aggregated item stacks + integer counts
        List<Entry> entries = getEntries(stack);
        NonNullList<ItemStack> items = NonNullList.create();
        List<Integer> counts = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            items.add(e.stack.copy()); // copy to preserve NBT
            counts.add(e.count);
        }
        return Optional.of(new BoundlessTooltip(items, counts, getContentWeight(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Boundless: " + getContentWeight(stack) + " / " + MAX_WEIGHT).withStyle(ChatFormatting.GRAY));
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
        float f = getFullnessDisplay(stack);
        return getRainbowColor(f);
    }

    // ---------------- storage helpers: aggregated entries ----------------

    // Single entry: an ItemStack "prototype" + an integer count (can exceed maxStackSize)
    private static record Entry(ItemStack stack, int count) {}

    /**
     * Read entries from the bundle NBT.
     * Format (per-entry):
     *  - "Item" : CompoundTag (the saved ItemStack tag)
     *  - "Count": int (our aggregated count)
     *
     * Backwards compatibility: if entry looks like a vanilla ItemStack NBT (contains "id"), we
     * convert it into one Entry with the ItemStack and its Count.
     */
    private static List<Entry> getEntries(ItemStack bundle) {
        List<Entry> result = new ArrayList<>();
        CompoundTag root = bundle.getOrCreateTag();
        if (!root.contains(TAG_ITEMS)) return result;

        ListTag list = root.getList(TAG_ITEMS, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);

            // Our modern format: contains "Item" compound and "Count" int
            if (c.contains("Item") && c.contains("Count")) {
                CompoundTag itemTag = c.getCompound("Item");
                ItemStack proto = ItemStack.of(itemTag);
                int cnt = c.getInt("Count");
                if (!proto.isEmpty() && cnt > 0) result.add(new Entry(proto, cnt));
                continue;
            }

            // Backwards compat: old vanilla ItemStack NBT saved directly
            ItemStack maybe = ItemStack.of(c);
            if (!maybe.isEmpty()) {
                result.add(new Entry(maybe.copy(), maybe.getCount()));
            }
        }
        return result;
    }

    /**
     * Save aggregated entries into NBT using our modern format (Item + Count int)
     */
    private static void saveEntries(ItemStack bundle, List<Entry> entries) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag comp = new CompoundTag();
            CompoundTag itemTag = new CompoundTag();
            e.stack.save(itemTag);                // saves vanilla ItemStack data (id, tag, etc.)
            comp.put("Item", itemTag);
            comp.putInt("Count", e.count);        // our integer count (can exceed 127)
            list.add(comp);
        }
        bundle.getOrCreateTag().put(TAG_ITEMS, list);
    }

    // ---------------- capacity logic ----------------

    private static boolean isContainerBlocked(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof ShulkerBoxBlock && stack.hasTag()) return true;
        }
        if ((stack.is(Items.CHEST) || stack.is(Items.TRAPPED_CHEST)) && stack.hasTag()) return true;
        return false;
    }

    // Weight per single item is 1 (every item increments the total by 1)
    private static int getWeightForOne(ItemStack stack) {
        if (isContainerBlocked(stack)) return Integer.MAX_VALUE;
        int maxStack = Math.max(1, stack.getMaxStackSize());
        return 4096 / (64 * maxStack); // ensures per-type max always equals 4096 weight
    }

    // total items inside
    private static int getContentWeight(ItemStack bundle) {
        return getEntries(bundle).stream()
                .mapToInt(e -> e.count * getWeightForOne(e.stack))
                .sum();
    }

    // per-type cap: 64 * maxStackSize
    private static int maxPerType(ItemStack stack) {
        return 64 * Math.max(1, stack.getMaxStackSize());
    }

    // ---------------- add / remove logic (works with aggregated counts) ----------------

    /**
     * Try to add items from `toInsert` into `bundle`. Mutates `toInsert` (shrinks),
     * returns the remainder ItemStack (or ItemStack.EMPTY).
     */
    public static ItemStack add(ItemStack bundle, ItemStack toInsert) {
        if (toInsert.isEmpty()) return ItemStack.EMPTY;
        if (!toInsert.getItem().canFitInsideContainerItems()) return toInsert;
        if (isContainerBlocked(toInsert)) return toInsert;

        List<Entry> entries = getEntries(bundle);
        int total = getContentWeight(bundle);
        int available = MAX_WEIGHT - total;
        if (available <= 0) return toInsert;

        int perTypeLimit = maxPerType(toInsert);

        // Find existing entry index for same item+tags
        int existingIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (ItemStack.isSameItemSameTags(entries.get(i).stack, toInsert)) {
                existingIndex = i;
                break;
            }
        }

        // Merge into existing entry if present
        if (existingIndex >= 0) {
            Entry ex = entries.get(existingIndex);
            int currentOfType = ex.count;
            int canAddByType = Math.max(0, perTypeLimit - currentOfType);
            int canAddBySpace = Math.max(0, available);
            int toAdd = Math.min(Math.min(canAddByType, canAddBySpace), toInsert.getCount());
            if (toAdd > 0) {
                entries.set(existingIndex, new Entry(ex.stack, ex.count + toAdd));
                toInsert.shrink(toAdd);
                available -= toAdd;
                saveEntries(bundle, entries);
                if (toInsert.isEmpty()) return ItemStack.EMPTY;
            }
        }

        // Create new entry if still have items and space
        while (!toInsert.isEmpty() && available > 0) {
            // compute current count-of-type if any
            int currentOfType = 0;
            for (Entry e : entries) if (ItemStack.isSameItemSameTags(e.stack, toInsert)) currentOfType += e.count;
            int canAddByType = Math.max(0, perTypeLimit - currentOfType);
            if (canAddByType <= 0) break;
            int toAdd = Math.min(Math.min(canAddByType, available), toInsert.getCount());
            if (toAdd <= 0) break;

            // Create a new prototype stack with tags (count value in prototype is irrelevant)
            ItemStack proto = toInsert.copy();
            proto.setCount(1);
            entries.add(new Entry(proto, toAdd));
            toInsert.shrink(toAdd);
            available -= toAdd;
        }

        saveEntries(bundle, entries);
        return toInsert.isEmpty() ? ItemStack.EMPTY : toInsert;
    }

    /**
     * Remove a single item (LIFO) from the bundle and return it.
     */
    private static ItemStack removeOne(ItemStack bundle) {
        List<Entry> entries = getEntries(bundle);
        if (entries.isEmpty()) return ItemStack.EMPTY;

        int last = entries.size() - 1;
        Entry e = entries.get(last);
        ItemStack out = e.stack.copy();
        out.setCount(1);

        if (e.count > 1) {
            entries.set(last, new Entry(e.stack, e.count - 1));
        } else {
            entries.remove(last);
        }

        saveEntries(bundle, entries);
        return out;
    }

    // ---------------- interaction overrides (bundle stays on cursor) ----------------

    @Override
    public boolean overrideStackedOnOther(ItemStack bundle, Slot slot, ClickAction action, Player player) {
        if (bundle.getCount() != 1 || action != ClickAction.SECONDARY) return false;

        ItemStack slotItem = slot.getItem();
        if (slotItem.isEmpty()) {
            ItemStack removed = removeOne(bundle);
            if (!removed.isEmpty()) {
                playRemoveOneSound(player);
                slot.set(removed);
                return true;
            }
            return false;
        }

        if (!slotItem.getItem().canFitInsideContainerItems()) return false;
        if (isContainerBlocked(slotItem)) return false;

        ItemStack before = slotItem.copy();
        ItemStack remainder = add(bundle, slotItem);
        if (remainder.getCount() != before.getCount()) {
            playInsertSound(player);
            slot.set(remainder);
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bundle, ItemStack other, Slot slot,
                                            ClickAction action, Player player, SlotAccess access) {
        if (bundle.getCount() != 1 || action != ClickAction.SECONDARY) return false;

        if (other.isEmpty()) {
            ItemStack removed = removeOne(bundle);
            if (!removed.isEmpty()) {
                playRemoveOneSound(player);
                access.set(removed);
                return true;
            }
            return false;
        }

        if (!other.getItem().canFitInsideContainerItems()) return false;
        if (isContainerBlocked(other)) return false;

        ItemStack before = other.copy();
        ItemStack remainder = add(bundle, other);
        if (remainder.getCount() != before.getCount()) {
            playInsertSound(player);
            access.set(remainder);
            return true;
        }
        return false;
    }

    // keep use() to drop all contents (same behavior as SuperBundle)
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

    private static boolean dropContents(ItemStack stack, Player player) {
        CompoundTag compoundTag = stack.getOrCreateTag();
        if (!compoundTag.contains(TAG_ITEMS)) return false;

        if (player instanceof ServerPlayer serverPlayer) {
            ListTag list = compoundTag.getList(TAG_ITEMS, 10);
            for (int i = 0; i < list.size(); ++i) {
                CompoundTag entry = list.getCompound(i);
                // support both formats: our modern ("Item"+"Count") and legacy vanilla ItemStack NBT
                if (entry.contains("Item") && entry.contains("Count")) {
                    ItemStack proto = ItemStack.of(entry.getCompound("Item"));
                    int cnt = entry.getInt("Count");
                    if (!proto.isEmpty() && cnt > 0) {
                        while (cnt > 0) {
                            int dropCount = Math.min(cnt, proto.getMaxStackSize());
                            ItemStack toDrop = proto.copy();
                            toDrop.setCount(dropCount);
                            serverPlayer.drop(toDrop, true);
                            cnt -= dropCount;
                        }
                    }
                } else {
                    ItemStack s = ItemStack.of(entry);
                    if (!s.isEmpty()) serverPlayer.drop(s, true);
                }
            }
        }

        stack.removeTagKey(TAG_ITEMS);
        return true;
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        ItemStack bundle = itemEntity.getItem();
        List<Entry> entries = getEntries(bundle);

        Level level = itemEntity.level();
        double x = itemEntity.getX();
        double y = itemEntity.getY();
        double z = itemEntity.getZ();

        for (Entry e : entries) {
            ItemStack proto = e.stack;
            int remaining = e.count;
            while (remaining > 0) {
                int drop = Math.min(remaining, proto.getMaxStackSize());
                ItemStack out = proto.copy();
                out.setCount(drop);
                ItemEntity ent = new ItemEntity(level, x, y, z, out);
                ent.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.2,
                        level.random.nextDouble() * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.2);
                level.addFreshEntity(ent);
                remaining -= drop;
            }
        }
    }

    // ---------------- sounds ----------------

    private void playRemoveOneSound(Entity e) {
        e.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + e.level().getRandom().nextFloat() * 0.4F);
    }

    private void playInsertSound(Entity e) {
        e.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + e.level().getRandom().nextFloat() * 0.4F);
    }

    private void playDropContentsSound(Entity e) {
        e.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F, 0.8F + e.level().getRandom().nextFloat() * 0.4F);
    }

    // ---------------- tooltip data + renderer ----------------

    public static record BoundlessTooltip(NonNullList<ItemStack> items, List<Integer> counts, int weight) implements TooltipComponent {}

    public static class BoundlessTooltipRenderer implements ClientTooltipComponent {
        private final BoundlessTooltip tooltip;

        public BoundlessTooltipRenderer(BoundlessTooltip tooltip) {
            this.tooltip = tooltip;
        }

        @Override
        public int getHeight() {
            int total = tooltip.items().size();
            if (total == 0) return 0;
            int cols = Math.min(DEFAULT_COLUMNS, Math.max(1, total));
            int renderCount = Math.min(total, MAX_ICONS_RENDER);
            int rows = (int) Math.ceil((double) renderCount / cols);
            int cell = computeCellSize(cols, rows);
            return rows * (cell + 2) + 6;
        }

        @Override
        public int getWidth(Font font) {
            int total = tooltip.items().size();
            if (total == 0) return 0;
            int cols = Math.min(DEFAULT_COLUMNS, Math.max(1, total));
            int cell = computeCellSize(cols, (int) Math.ceil((double) Math.min(total, MAX_ICONS_RENDER) / cols));
            return cols * cell;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            int total = tooltip.items().size();
            if (total == 0) return;

            int cols = Math.min(DEFAULT_COLUMNS, Math.max(1, total));
            int renderCount = Math.min(total, MAX_ICONS_RENDER);
            int rows = (int) Math.ceil((double) renderCount / cols);
            int cell = computeCellSize(cols, rows);
            int rowHeight = cell + 2;

            for (int i = 0; i < renderCount; i++) {
                ItemStack s = tooltip.items().get(i);
                int cnt = tooltip.counts().get(i);
                if (s.isEmpty()) continue;

                int row = i / cols;
                int col = i % cols;

                int drawX = x + col * cell;
                int drawY = y + row * rowHeight;

                graphics.pose().pushPose();
                float scale = (float) cell / 16.0f;
                graphics.pose().translate(drawX, drawY, 0);
                graphics.pose().scale(scale, scale, 1.0f);

                graphics.renderItem(s, 0, 0);
                // Render decorations with the aggregated count string (so >64 shows)
                String countText = cnt > 1 ? String.valueOf(cnt) : "";
                graphics.renderItemDecorations(font, s, 0, 0, countText);

                graphics.pose().popPose();
            }

            int remaining = total - renderCount;
            if (remaining > 0) {
                String txt = "+" + remaining + " more";
                int badgeX = x + Math.max(0, cols * cell - font.width(txt) - 6);
                int badgeY = y + rows * rowHeight - 10;
                graphics.fill(badgeX - 3, badgeY - 2, badgeX + font.width(txt) + 3, badgeY + font.lineHeight + 2, 0x88000000);
                graphics.drawString(font, txt, badgeX, badgeY, 0xFFFFFF, false);
            }

            // progress bar (below)
            int gridWidth = cols * cell;
            int barX = x + 1;
            int barY = y + rows * rowHeight + 2;
            int barInnerWidth = Math.max(2, gridWidth - 2);
            int weight = tooltip.weight();
            int filled = (int) ((double) weight / (double) MAX_WEIGHT * barInnerWidth);
            filled = Mth.clamp(filled, 0, barInnerWidth);

            // background
            graphics.fill(barX, barY, barX + barInnerWidth, barY + 4, 0xFF1F1F1F);
            // gradient fill (rainbow)
            for (int i = 0; i < filled; i++) {
                float t = (float) i / Math.max(1, barInnerWidth - 1);
                int color = getRainbowColor(t);
                graphics.fill(barX + i, barY, barX + i + 1, barY + 4, color);
            }
        }

        private int computeCellSize(int cols, int rows) {
            Minecraft mc = Minecraft.getInstance();
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();

            int preferredW = cols * DEFAULT_CELL;
            int preferredH = rows * DEFAULT_ROW_HEIGHT;

            int maxW = (int) (screenW * SCREEN_RATIO);
            int maxH = (int) (screenH * SCREEN_RATIO);

            if (preferredW <= maxW && preferredH <= maxH) return DEFAULT_CELL;

            double scaleW = (double) maxW / Math.max(1, preferredW);
            double scaleH = (double) maxH / Math.max(1, preferredH);
            double scale = Math.min(scaleW, scaleH);
            int cell = Math.max(4, (int) Math.floor(DEFAULT_CELL * scale));
            return cell;
        }
    }

    // ---------------- rainbow color ----------------

    private static int getRainbowColor(float progress) {
        float hue = Mth.clamp(progress, 0f, 1f) * 0.8f; // 0..0.8 map red->purple
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }
}
