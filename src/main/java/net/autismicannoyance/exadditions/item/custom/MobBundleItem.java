package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MobBundleItem (common-side)
 *
 * - sneak + right-click a mob to capture it (stores full entity NBT)
 * - right-click on a block to release a single mob on top of the clicked block
 * - shift + right-click in the air to release ALL stored mobs at once (spread around player)
 * - if the dropped bundle is DESTROYED (lava/cactus/explosion/fire/void or /kill), release all at that spot
 * - if the dropped bundle naturally DESPAWNS, release all at that spot
 * - provides a TooltipComponent (MobTooltip) for client renderer to display stored mobs
 */
public class MobBundleItem extends Item {
    private static final String TAG_MOBS = "Mobs";
    public static final int MAX_MOBS = 64; // public so client renderer can reference

    public MobBundleItem(Properties p) {
        super(p);
    }

    // --------------------------------------------------------------------------
    // Capture: sneak + right-click a living mob
    // --------------------------------------------------------------------------
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (target instanceof Player) return InteractionResult.PASS;   // don't capture players
        if (!canStoreMore(stack)) return InteractionResult.FAIL;

        boolean captured = captureMobIntoStack(stack, target);
        if (!captured) return InteractionResult.PASS;

        // sync updated stack to client
        player.setItemInHand(hand, stack);

        // remove the entity (server-side)
        target.discard();

        player.playSound(SoundEvents.BUNDLE_INSERT, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.CONSUME;
    }

    // --------------------------------------------------------------------------
    // Use:
    //  - right-click block => release one on top
    //  - shift + right-click in the air => release all
    //  - right-click air (no shift) => release one in front
    // --------------------------------------------------------------------------
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (isEmpty(stack)) return InteractionResultHolder.pass(stack);

        HitResult hit = player.pick(5.0D, 0.0F, false);

        // SHIFT + right-click in air releases all mobs around player
        if (player.isShiftKeyDown() && (hit.getType() == HitResult.Type.MISS
                || (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr
                && bhr.getBlockPos().equals(player.blockPosition())))) {

            releaseAllAround(stack, (ServerLevel) level, player.getX(), player.getY(), player.getZ(), 1.0);
            player.setItemInHand(hand, stack);

            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 1.0F, 1.0F);
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // Right-click a block: spawn one mob on the block surface
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            BlockState state = level.getBlockState(bhr.getBlockPos());
            VoxelShape shape = state.getShape(level, bhr.getBlockPos());
            double top = shape.max(Direction.Axis.Y); // 0..1 local coordinates
            double spawnX = bhr.getBlockPos().getX() + 0.5;
            double spawnZ = bhr.getBlockPos().getZ() + 0.5;
            double spawnY = (top <= 0.0001)
                    ? bhr.getBlockPos().getY() + 1.0 // fallback
                    : bhr.getBlockPos().getY() + top + 0.01; // epsilon above surface

            if (releaseOneFromStack(stack, (ServerLevel) level, spawnX, spawnY, spawnZ)) {
                player.setItemInHand(hand, stack);
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 1.0F, 1.0F);
                player.awardStat(Stats.ITEM_USED.get(this));
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
            return InteractionResultHolder.pass(stack);
        }

        // Otherwise (air, no shift): spawn one in front of the player
        double spawnX = player.getX() + player.getLookAngle().x;
        double spawnY = player.getY() + player.getLookAngle().y;
        double spawnZ = player.getZ() + player.getLookAngle().z;

        if (releaseOneFromStack(stack, (ServerLevel) level, spawnX, spawnY, spawnZ)) {
            player.setItemInHand(hand, stack);
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 1.0F, 1.0F);
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return InteractionResultHolder.pass(stack);
    }

    // --------------------------------------------------------------------------
    // ItemEntity destruction hook: called when the dropped item gets KILLED
    // (lava, cactus, explosion, fire, void, /kill, etc.)
    // This does NOT fire for natural despawn; that’s handled by ItemExpireEvent.
    // --------------------------------------------------------------------------
    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        Level level = itemEntity.level();
        if (level instanceof ServerLevel serverLevel) {
            ItemStack stack = itemEntity.getItem();
            // release at the exact death position
            releaseAllAround(stack, serverLevel, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), 0.6);
        }
        super.onDestroyed(itemEntity); // vanilla no-op, safe to call
    }

    // --------------------------------------------------------------------------
    // NBT helpers
    // --------------------------------------------------------------------------
    private static ListTag getMobListTag(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();
        if (!root.contains(TAG_MOBS)) root.put(TAG_MOBS, new ListTag());
        return root.getList(TAG_MOBS, 10);
    }

    private static void setMobListTag(ItemStack stack, ListTag list) {
        stack.getOrCreateTag().put(TAG_MOBS, list);
    }

    public static boolean isEmpty(ItemStack stack) {
        return getMobListTag(stack).isEmpty();
    }

    public static boolean canStoreMore(ItemStack stack) {
        return getMobListTag(stack).size() < MAX_MOBS;
    }

    public static int storedCount(ItemStack stack) {
        return getMobListTag(stack).size();
    }

    /**
     * Save entity full NBT into the bundle.
     */
    private static boolean captureMobIntoStack(ItemStack stack, Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;

        ListTag list = getMobListTag(stack);
        if (list.size() >= MAX_MOBS) return false;

        CompoundTag mobTag = new CompoundTag();
        mobTag.putString("id", EntityType.getKey(entity.getType()).toString());
        entity.save(mobTag);

        list.add(mobTag);
        setMobListTag(stack, list);
        return true;
    }

    /**
     * Release one mob (LIFO) at coords server-side.
     */
    public static boolean releaseOneFromStack(ItemStack stack, ServerLevel level, double x, double y, double z) {
        ListTag list = getMobListTag(stack);
        if (list.isEmpty()) return false;

        int last = list.size() - 1;
        CompoundTag mobTag = list.getCompound(last);
        list.remove(last);
        setMobListTag(stack, list);

        String id = mobTag.getString("id");
        Optional<EntityType<?>> maybeType = EntityType.byString(id);
        EntityType<?> type = maybeType.orElse(null);
        if (type == null) return false;

        Entity e = type.create(level);
        if (e == null) return false;

        CompoundTag loadTag = mobTag.copy();
        loadTag.remove("Pos");
        loadTag.remove("UUID");
        loadTag.remove("UUIDLeast");
        loadTag.remove("UUIDMost");

        try {
            e.load(loadTag);
        } catch (Exception ex) {
            list.add(mobTag);
            setMobListTag(stack, list);
            return false;
        }

        double spawnY = y + (e.getBbHeight() * 0.5);

        e.moveTo(x, spawnY, z, level.random.nextFloat() * 360F, 0F);
        level.addFreshEntity(e);
        return true;
    }

    /**
     * Release all mobs with spread.
     */
    public static void releaseAllAround(ItemStack stack, ServerLevel level, double x, double y, double z, double spread) {
        ListTag list = getMobListTag(stack);
        int count = list.size();
        if (count == 0) return;

        for (int i = count - 1; i >= 0; i--) {
            CompoundTag mobTag = list.getCompound(i);

            String id = mobTag.getString("id");
            Optional<EntityType<?>> maybeType = EntityType.byString(id);
            EntityType<?> type = maybeType.orElse(null);
            if (type == null) continue;

            Entity e = type.create(level);
            if (e == null) continue;

            CompoundTag loadTag = mobTag.copy();
            loadTag.remove("Pos");
            loadTag.remove("UUID");
            loadTag.remove("UUIDLeast");
            loadTag.remove("UUIDMost");

            try {
                e.load(loadTag);
            } catch (Exception ex) {
                continue;
            }

            double dx = x + (level.random.nextDouble() - 0.5) * spread * 2.0;
            double dz = z + (level.random.nextDouble() - 0.5) * spread * 2.0;
            double dy = y + 0.1 + level.random.nextDouble() * 0.5;
            dy += e.getBbHeight() * 0.5;

            e.moveTo(dx, dy, dz, level.random.nextFloat() * 360F, 0F);
            level.addFreshEntity(e);
        }

        stack.removeTagKey(TAG_MOBS);
    }

    // --------------------------------------------------------------------------
    // Tooltip component (common)
    // --------------------------------------------------------------------------
    public static record MobTooltip(List<CompoundTag> mobTags, int count) implements TooltipComponent {}

    @Override
    public java.util.Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        ListTag list = getMobListTag(stack);
        List<CompoundTag> copies = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag copy = list.getCompound(i).copy();
            copy.remove("Pos");
            copy.remove("UUID");
            copy.remove("UUIDLeast");
            copy.remove("UUIDMost");
            copies.add(copy);
        }
        return java.util.Optional.of(new MobTooltip(copies, storedCount(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int count = storedCount(stack);
        tooltip.add(Component.literal("Mobs: " + count + " / " + MAX_MOBS).withStyle(ChatFormatting.GRAY));

        ListTag list = getMobListTag(stack);
        int show = Math.min(3, list.size());
        for (int i = 0; i < show; i++) {
            CompoundTag mob = list.getCompound(i);
            String display = mob.contains("CustomName") ? mob.getString("CustomName") : mob.getString("id");
            tooltip.add(Component.literal("- " + display).withStyle(ChatFormatting.DARK_GREEN));
        }
        if (list.size() > show) {
            tooltip.add(Component.literal("... +" + (list.size() - show) + " more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // --------------------------------------------------------------------------
    // Events
    // --------------------------------------------------------------------------
    @Mod.EventBusSubscriber(modid = "exadditions")
    public static class Events {
        @SubscribeEvent
        public static void onItemExpire(ItemExpireEvent event) {
            Entity ent = event.getEntity();
            if (!(ent instanceof ItemEntity itemEntity)) return;
            ItemStack stack = itemEntity.getItem();
            if (!(stack.getItem() instanceof MobBundleItem)) return;
            if (!(itemEntity.level() instanceof ServerLevel serverLevel)) return;

            releaseAllAround(stack, serverLevel, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), 0.6);
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
            Player player = event.getEntity();
            if (player == null) return;

            ItemStack stack = player.getItemInHand(event.getHand());
            if (!(stack.getItem() instanceof MobBundleItem bundle)) return;

            if (player.level().isClientSide) return;
            if (!(event.getTarget() instanceof LivingEntity living)) return;
            if (living instanceof Player) return;
            if (!MobBundleItem.canStoreMore(stack)) return;

            boolean captured = captureMobIntoStack(stack, living);
            if (!captured) return;

            player.setItemInHand(event.getHand(), stack);
            living.discard();

            player.playSound(SoundEvents.BUNDLE_INSERT, 1.0F, 1.0F);
            player.awardStat(Stats.ITEM_USED.get(bundle));

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }
}
