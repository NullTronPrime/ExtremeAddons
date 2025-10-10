package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.autismicannoyance.exadditions.world.dimension.ArcanePouchDimensionManager;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class ArcanePouchItem extends Item {
    private static final String TAG_POUCH_UUID = "PouchUUID";
    private static final String TAG_MOBS = "Mobs";
    private static final String TAG_DEAD_MOBS = "DeadMobs";
    private static final int PLATFORM_RADIUS = 7; // Safe spawn radius (8 block radius platform, spawn within 7)
    private static final int PLATFORM_Y = 64;

    public ArcanePouchItem(Properties p) {
        super(p);
    }

    public static UUID getPouchUUID(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID(TAG_POUCH_UUID)) {
            UUID uuid = UUID.randomUUID();
            tag.putUUID(TAG_POUCH_UUID, uuid);
        }
        return tag.getUUID(TAG_POUCH_UUID);
    }

    /**
     * Finds a safe spawn position on the platform within the radius
     */
    private static Vec3 findSafeSpawnPosition(ServerLevel level, net.minecraft.util.RandomSource random) {
        // Try to find a spot on the platform
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            // Spawn within safe radius (leave 1 block margin from edge, avoid center diamond block)
            double radius = 2 + random.nextDouble() * (PLATFORM_RADIUS - 3);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            BlockPos checkPos = new BlockPos((int)Math.floor(x), PLATFORM_Y, (int)Math.floor(z));
            BlockPos abovePos = checkPos.above();

            // Check if position is safe (platform below, air above)
            if (level.getBlockState(checkPos).isSolid() &&
                    level.getBlockState(abovePos).isAir() &&
                    level.getBlockState(abovePos.above()).isAir()) {
                // Return position centered on block, 1 block above platform
                return new Vec3(x, PLATFORM_Y + 1.0, z);
            }
        }

        // Fallback to a safe spot near center (not ON the diamond block)
        return new Vec3(2.5, PLATFORM_Y + 1.0, 2.5);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (target instanceof Player) return InteractionResult.PASS;

        try {
            UUID pouchUUID = getPouchUUID(stack);
            ServerLevel currentLevel = (ServerLevel) player.level();

            // Get or create the pouch dimension
            ServerLevel pouchLevel = ArcanePouchDimensionManager.getOrCreatePouchDimension(currentLevel, pouchUUID);
            if (pouchLevel == null) {
                player.displayClientMessage(Component.literal("Failed to create dimension!").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }

            // Save mob data before teleporting
            CompoundTag mobTag = new CompoundTag();
            target.save(mobTag);

            // Find safe spawn position on platform
            Vec3 spawnPos = findSafeSpawnPosition(pouchLevel, player.level().random);

            // Teleport the entity
            Entity teleported = target.changeDimension(pouchLevel, new net.minecraftforge.common.util.ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                    entity = repositionEntity.apply(false);
                    entity.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, yaw, entity.getXRot());
                    return entity;
                }
            });

            if (teleported != null) {
                // Update pouch NBT data
                ListTag mobs = stack.getOrCreateTag().getList(TAG_MOBS, 10);
                CompoundTag entry = new CompoundTag();
                entry.putUUID("UUID", target.getUUID());
                entry.put("Data", mobTag);
                mobs.add(entry);
                stack.getOrCreateTag().put(TAG_MOBS, mobs);

                player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);

                // Mark dimension as active for ticking
                ArcanePouchDimensionManager.markDimensionActive(pouchUUID);

                return InteractionResult.CONSUME;
            } else {
                player.displayClientMessage(Component.literal("Failed to capture entity!").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
        } catch (Exception e) {
            player.displayClientMessage(Component.literal("Error: " + e.getMessage()).withStyle(ChatFormatting.RED), true);
            e.printStackTrace();
            return InteractionResult.FAIL;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        ServerLevel serverLevel = (ServerLevel) level;

        // Shift-click: Enter the pouch dimension
        if (player.isShiftKeyDown()) {
            try {
                UUID pouchUUID = getPouchUUID(stack);

                ServerLevel pouchLevel = ArcanePouchDimensionManager.getOrCreatePouchDimension(serverLevel, pouchUUID);
                if (pouchLevel == null) {
                    player.displayClientMessage(Component.literal("Failed to create dimension!").withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }

                Entity teleported = player.changeDimension(pouchLevel, new net.minecraftforge.common.util.ITeleporter() {
                    @Override
                    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                        entity = repositionEntity.apply(false);
                        // Spawn player at center of platform (diamond block at 0,64,0, spawn at 0,65,0)
                        entity.moveTo(0.5, 65.0, 0.5, yaw, entity.getXRot());
                        entity.setYHeadRot(yaw);
                        return entity;
                    }
                });

                if (teleported != null) {
                    player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.5F);

                    // Mark dimension as active for ticking
                    ArcanePouchDimensionManager.markDimensionActive(pouchUUID);

                    return InteractionResultHolder.success(stack);
                } else {
                    player.displayClientMessage(Component.literal("Failed to enter dimension!").withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }
            } catch (Exception e) {
                player.displayClientMessage(Component.literal("Error: " + e.getMessage()).withStyle(ChatFormatting.RED), true);
                e.printStackTrace();
                return InteractionResultHolder.fail(stack);
            }
        }

        // Normal click: Release a dead mob
        ListTag deadMobs = stack.getOrCreateTag().getList(TAG_DEAD_MOBS, 10);
        if (!deadMobs.isEmpty()) {
            CompoundTag deadMob = deadMobs.getCompound(0);
            deadMobs.remove(0);
            stack.getOrCreateTag().put(TAG_DEAD_MOBS, deadMobs);

            CompoundTag mobData = deadMob.getCompound("Data");
            String id = mobData.getString("id");

            net.minecraft.world.entity.EntityType.byString(id).ifPresent(type -> {
                Entity e = type.create(serverLevel);
                if (e != null) {
                    CompoundTag loadTag = mobData.copy();
                    loadTag.remove("Pos");
                    loadTag.remove("UUID");
                    loadTag.remove("UUIDLeast");
                    loadTag.remove("UUIDMost");

                    try {
                        e.load(loadTag);

                        // Find safe spawn position in front of player
                        Vec3 lookVec = player.getLookAngle();
                        Vec3 spawnPos = player.position().add(lookVec.scale(2));

                        // Make sure mob spawns on ground
                        BlockPos groundPos = findGroundBelow(serverLevel, new BlockPos((int)spawnPos.x, (int)spawnPos.y, (int)spawnPos.z));

                        e.moveTo(groundPos.getX() + 0.5, groundPos.getY() + 1, groundPos.getZ() + 0.5,
                                level.random.nextFloat() * 360F, 0F);
                        level.addFreshEntity(e);
                    } catch (Exception ignored) {}
                }
            });

            player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * Finds the ground level below a position
     */
    private static BlockPos findGroundBelow(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos pos = start.mutable();

        // Search down up to 10 blocks
        for (int i = 0; i < 10; i++) {
            if (level.getBlockState(pos).isSolid() && level.getBlockState(pos.above()).isAir()) {
                return pos.immutable();
            }
            pos.move(0, -1, 0);
        }

        // Search up if no ground found below
        pos.set(start);
        for (int i = 0; i < 10; i++) {
            if (level.getBlockState(pos).isSolid() && level.getBlockState(pos.above()).isAir()) {
                return pos.immutable();
            }
            pos.move(0, 1, 0);
        }

        return start; // Fallback to original position
    }

    public static void handleMobDeath(ServerLevel level, LivingEntity entity, UUID pouchUUID) {
        ItemStack pouchStack = findPouchByUUID(level, pouchUUID);
        if (pouchStack.isEmpty()) return;

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
        for (ServerLevel world : level.getServer().getAllLevels()) {
            for (Player player : world.players()) {
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() instanceof ArcanePouchItem && getPouchUUID(stack).equals(pouchUUID)) {
                        return stack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static record ArcanePouchTooltip(UUID pouchUUID) implements TooltipComponent {}

    @Override
    public java.util.Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        UUID uuid = getPouchUUID(stack);

        // Mark dimension as active when tooltip is being viewed
        if (!stack.getOrCreateTag().getList(TAG_MOBS, 10).isEmpty()) {
            ArcanePouchDimensionManager.markDimensionActive(uuid);
        }

        return java.util.Optional.of(new ArcanePouchTooltip(uuid));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID uuid = getPouchUUID(stack);
        ListTag mobs = stack.getOrCreateTag().getList(TAG_MOBS, 10);
        ListTag dead = stack.getOrCreateTag().getList(TAG_DEAD_MOBS, 10);

        tooltip.add(Component.literal("UUID: " + uuid.toString().substring(0, 8) + "...").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.literal("Living: " + mobs.size()).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("Dead: " + dead.size()).withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("Shift-Right Click to enter").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click mob to capture").withStyle(ChatFormatting.GRAY));
    }
}