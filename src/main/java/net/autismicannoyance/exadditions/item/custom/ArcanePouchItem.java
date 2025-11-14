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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.autismicannoyance.exadditions.world.dimension.ArcanePouchDimensionManager;
import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class ArcanePouchItem extends Item {
    private static final String TAG_POUCH_UUID = "PouchUUID";
    private static final String TAG_MOBS = "Mobs";
    private static final String TAG_DEAD_MOBS = "DeadMobs";
    private static final String TAG_RETURN_POS = "ReturnPos";
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

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (target instanceof Player) return InteractionResult.PASS;

        try {
            UUID pouchUUID = getPouchUUID(stack);
            ServerLevel currentLevel = (ServerLevel) player.level();

            ServerLevel pouchLevel = ArcanePouchDimensionManager.getOrCreatePouchDimension(currentLevel, pouchUUID);
            if (pouchLevel == null) {
                player.displayClientMessage(Component.literal("Failed to create dimension!").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }

            // Save mob data
            CompoundTag mobTag = new CompoundTag();
            target.save(mobTag);

            // CRITICAL FIX: Use portals FALSE to prevent relative positioning
            Entity teleported = target.changeDimension(pouchLevel, new ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                    // CRITICAL: Call with FALSE to prevent relative positioning
                    Entity e = repositionEntity.apply(false);

                    // Random position on platform (not center, spread mobs out)
                    double angle = destWorld.random.nextDouble() * Math.PI * 2;
                    double radius = 2 + destWorld.random.nextDouble() * 4; // 2-6 blocks from center
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    // ABSOLUTE positioning - no relation to overworld coords
                    e.moveTo(x, PLATFORM_Y + 1.5, z, yaw, 0);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.setOnGround(false);
                    e.fallDistance = 0;

                    return e;
                }

                @Override
                public boolean playTeleportSound(net.minecraft.server.level.ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
                    return false;
                }
            });

            if (teleported != null) {
                ListTag mobs = stack.getOrCreateTag().getList(TAG_MOBS, 10);
                CompoundTag entry = new CompoundTag();
                entry.putUUID("UUID", target.getUUID());
                entry.put("Data", mobTag);
                mobs.add(entry);
                stack.getOrCreateTag().put(TAG_MOBS, mobs);

                player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
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

    private boolean isInPouchDimension(Player player) {
        ResourceKey<Level> dimKey = player.level().dimension();
        String dimPath = dimKey.location().getPath();
        return dimPath.startsWith("pouch_");
    }

    private boolean exitPouchDimension(Player player, ItemStack stack) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }

        ServerLevel overworld = serverPlayer.getServer().overworld();

        BlockPos exitPos;
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(TAG_RETURN_POS)) {
            CompoundTag posTag = tag.getCompound(TAG_RETURN_POS);
            exitPos = new BlockPos(
                    posTag.getInt("X"),
                    posTag.getInt("Y"),
                    posTag.getInt("Z")
            );
            tag.remove(TAG_RETURN_POS);
        } else {
            exitPos = overworld.getSharedSpawnPos();
        }

        BlockPos safePos = findSafeExitPosition(overworld, exitPos);

        // CRITICAL FIX: Use portals FALSE
        serverPlayer.changeDimension(overworld, new ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                // CRITICAL: FALSE to prevent relative positioning
                Entity e = repositionEntity.apply(false);

                // ABSOLUTE positioning to saved location
                e.moveTo(safePos.getX() + 0.5, safePos.getY() + 1, safePos.getZ() + 0.5, e.getYRot(), e.getXRot());
                e.setDeltaMovement(Vec3.ZERO);
                e.fallDistance = 0;

                return e;
            }

            @Override
            public boolean playTeleportSound(net.minecraft.server.level.ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
                return false;
            }
        });

        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.5F);
        return true;
    }

    private static BlockPos findSafeExitPosition(ServerLevel level, BlockPos targetPos) {
        if (isSafePosition(level, targetPos)) {
            return targetPos;
        }

        for (int radius = 1; radius <= 10; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;

                    BlockPos testPos = targetPos.offset(x, 0, z);
                    BlockPos groundPos = findGroundBelow(level, testPos);

                    if (isSafePosition(level, groundPos)) {
                        return groundPos;
                    }
                }
            }
        }

        return targetPos.above(10);
    }

    private static boolean isSafePosition(ServerLevel level, BlockPos pos) {
        try {
            return level.getBlockState(pos).isSolid() &&
                    level.getBlockState(pos.above()).isAir() &&
                    level.getBlockState(pos.above(2)).isAir() &&
                    !level.getBlockState(pos).is(Blocks.LAVA) &&
                    !level.getBlockState(pos).is(Blocks.FIRE);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        ServerLevel serverLevel = (ServerLevel) level;

        if (isInPouchDimension(player)) {
            if (exitPouchDimension(player, stack)) {
                return InteractionResultHolder.success(stack);
            } else {
                player.displayClientMessage(Component.literal("Failed to exit dimension!").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
        }

        if (player.isShiftKeyDown()) {
            try {
                UUID pouchUUID = getPouchUUID(stack);

                ServerLevel pouchLevel = ArcanePouchDimensionManager.getOrCreatePouchDimension(serverLevel, pouchUUID);
                if (pouchLevel == null) {
                    player.displayClientMessage(Component.literal("Failed to create dimension!").withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }

                // Save return position
                BlockPos currentPos = player.blockPosition();
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", currentPos.getX());
                posTag.putInt("Y", currentPos.getY());
                posTag.putInt("Z", currentPos.getZ());
                stack.getOrCreateTag().put(TAG_RETURN_POS, posTag);

                // CRITICAL FIX: Use portals FALSE to prevent relative positioning
                Entity teleported = player.changeDimension(pouchLevel, new ITeleporter() {
                    @Override
                    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                        // CRITICAL: FALSE to prevent relative positioning
                        Entity e = repositionEntity.apply(false);

                        // ABSOLUTE position at center of platform, above diamond block
                        e.moveTo(0.5, PLATFORM_Y + 3, 0.5, e.getYRot(), e.getXRot());
                        e.setDeltaMovement(Vec3.ZERO);
                        e.setOnGround(false);
                        e.fallDistance = 0;

                        return e;
                    }

                    @Override
                    public boolean playTeleportSound(net.minecraft.server.level.ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
                        return false;
                    }
                });

                if (teleported != null) {
                    player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.5F);
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

        // Release dead mob
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

                        Vec3 lookVec = player.getLookAngle();
                        Vec3 spawnPos = player.position().add(lookVec.scale(2));

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

    private static BlockPos findGroundBelow(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos pos = start.mutable();

        for (int i = 0; i < 10; i++) {
            if (level.getBlockState(pos).isSolid() && level.getBlockState(pos.above()).isAir()) {
                return pos.immutable();
            }
            pos.move(0, -1, 0);
        }

        pos.set(start);
        for (int i = 0; i < 10; i++) {
            if (level.getBlockState(pos).isSolid() && level.getBlockState(pos.above()).isAir()) {
                return pos.immutable();
            }
            pos.move(0, 1, 0);
        }

        return start;
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
        tooltip.add(Component.literal("Right-click inside to exit").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click mob to capture").withStyle(ChatFormatting.GRAY));
    }
}