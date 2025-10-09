package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.autismicannoyance.exadditions.world.dimension.ArcanePouchDimensionManager;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class ArcanePouchItem extends Item {
    private static final String TAG_POUCH_UUID = "PouchUUID";
    private static final String TAG_MOBS = "Mobs";
    private static final String TAG_DEAD_MOBS = "DeadMobs";

    public ArcanePouchItem(Properties p) {
        super(p);
    }

    private static UUID getPouchUUID(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID(TAG_POUCH_UUID)) {
            UUID uuid = UUID.randomUUID();
            tag.putUUID(TAG_POUCH_UUID, uuid);
        }
        return tag.getUUID(TAG_POUCH_UUID);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (target instanceof Player) return InteractionResult.PASS;
        UUID pouchUUID = getPouchUUID(stack);
        ServerLevel pouchLevel = ArcanePouchDimensionManager.getOrCreatePouchDimension((ServerLevel) player.level(), pouchUUID);
        if (pouchLevel == null) return InteractionResult.FAIL;
        CompoundTag mobTag = new CompoundTag();
        target.save(mobTag);
        double angle = player.level().random.nextDouble() * Math.PI * 2;
        double radius = 6 + player.level().random.nextDouble() * 2;
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        target.changeDimension(pouchLevel, new net.minecraftforge.common.util.ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                entity = repositionEntity.apply(false);
                entity.moveTo(x, 65, z, yaw, entity.getXRot());
                return entity;
            }
        });
        ListTag mobs = stack.getOrCreateTag().getList(TAG_MOBS, 10);
        CompoundTag entry = new CompoundTag();
        entry.putUUID("UUID", target.getUUID());
        entry.put("Data", mobTag);
        mobs.add(entry);
        stack.getOrCreateTag().put(TAG_MOBS, mobs);
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (player.isShiftKeyDown()) {
            UUID pouchUUID = getPouchUUID(stack);
            ServerLevel pouchLevel = ArcanePouchDimensionManager.getOrCreatePouchDimension((ServerLevel) level, pouchUUID);
            if (pouchLevel == null) return InteractionResultHolder.fail(stack);
            player.changeDimension(pouchLevel, new net.minecraftforge.common.util.ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                    entity = repositionEntity.apply(false);
                    entity.moveTo(0, 65, 0, yaw, entity.getXRot());
                    return entity;
                }
            });
            player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.5F);
            return InteractionResultHolder.success(stack);
        }
        ListTag deadMobs = stack.getOrCreateTag().getList(TAG_DEAD_MOBS, 10);
        if (!deadMobs.isEmpty()) {
            CompoundTag deadMob = deadMobs.getCompound(0);
            deadMobs.remove(0);
            stack.getOrCreateTag().put(TAG_DEAD_MOBS, deadMobs);
            CompoundTag mobData = deadMob.getCompound("Data");
            String id = mobData.getString("id");
            net.minecraft.world.entity.EntityType.byString(id).ifPresent(type -> {
                Entity e = type.create((ServerLevel) level);
                if (e != null) {
                    CompoundTag loadTag = mobData.copy();
                    loadTag.remove("Pos");
                    loadTag.remove("UUID");
                    loadTag.remove("UUIDLeast");
                    loadTag.remove("UUIDMost");
                    try {
                        e.load(loadTag);
                        Vec3 spawnPos = player.position().add(player.getLookAngle().scale(2));
                        e.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, level.random.nextFloat() * 360F, 0F);
                        level.addFreshEntity(e);
                    } catch (Exception ex) {}
                }
            });
            player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    public static void handleMobDeath(ServerLevel level, LivingEntity entity, UUID pouchUUID) {
        ItemStack pouchStack = findPouchByUUID(level, pouchUUID);
        if (pouchStack == null) return;
        CompoundTag mobTag = new CompoundTag();
        entity.save(mobTag);
        ListTag deadMobs = pouchStack.getOrCreateTag().getList(TAG_DEAD_MOBS, 10);
        CompoundTag entry = new CompoundTag();
        entry.putUUID("UUID", entity.getUUID());
        entry.put("Data", mobTag);
        deadMobs.add(entry);
        pouchStack.getOrCreateTag().put(TAG_DEAD_MOBS, deadMobs);
        ListTag mobs = pouchStack.getOrCreateTag().getList(TAG_MOBS, 10);
        for (int i = 0; i < mobs.size(); i++) {
            if (mobs.getCompound(i).getUUID("UUID").equals(entity.getUUID())) {
                mobs.remove(i);
                break;
            }
        }
        pouchStack.getOrCreateTag().put(TAG_MOBS, mobs);
    }

    private static ItemStack findPouchByUUID(ServerLevel level, UUID pouchUUID) {
        for (Player player : level.players()) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof ArcanePouchItem && getPouchUUID(stack).equals(pouchUUID)) {
                    return stack;
                }
            }
        }
        return null;
    }

    public static record ArcanePouchTooltip(UUID pouchUUID, List<CompoundTag> mobTags) implements TooltipComponent {}

    @Override
    public java.util.Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        UUID uuid = getPouchUUID(stack);
        ListTag mobs = stack.getOrCreateTag().getList(TAG_MOBS, 10);
        List<CompoundTag> copies = new ArrayList<>();
        for (int i = 0; i < mobs.size(); i++) {
            copies.add(mobs.getCompound(i).getCompound("Data").copy());
        }
        return java.util.Optional.of(new ArcanePouchTooltip(uuid, copies));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID uuid = getPouchUUID(stack);
        ListTag mobs = stack.getOrCreateTag().getList(TAG_MOBS, 10);
        ListTag dead = stack.getOrCreateTag().getList(TAG_DEAD_MOBS, 10);
        tooltip.add(Component.literal("UUID: " + uuid.toString().substring(0, 8) + "...").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.literal("Living: " + mobs.size()).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("Dead: " + dead.size()).withStyle(ChatFormatting.RED));
    }
}