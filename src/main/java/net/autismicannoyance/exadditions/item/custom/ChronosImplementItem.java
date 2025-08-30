package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.Container;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class ChronosImplementItem extends Item {
    private static final int TIME_ACCELERATION_TICKS = 16800;

    public ChronosImplementItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }

        player.getCooldowns().addCooldown(this, 100);
        boolean success = false;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            player.sendSystemMessage(Component.literal("DEBUG: Attempting to accelerate block entity: " + blockEntity.getClass().getSimpleName()));
            success = accelerateBlockEntity(level, pos, blockEntity, TIME_ACCELERATION_TICKS);
            if (success) {
                System.out.println("DEBUG: Successfully accelerated block entity");
                player.sendSystemMessage(Component.literal("Accelerated block entity by 14 minutes"));
            } else {
                System.out.println("DEBUG: Failed to accelerate block entity");
            }
        }

        if (!success) {
            System.out.println("DEBUG: Attempting block-level acceleration at " + pos);
            success = accelerateBlock(level, pos, TIME_ACCELERATION_TICKS);
            if (success) {
                System.out.println("DEBUG: Successfully accelerated block");
                player.sendSystemMessage(Component.literal("Accelerated block by 14 minutes"));
            } else {
                System.out.println("DEBUG: Failed to accelerate block");
            }
        }

        if (success) {
            createTimeAccelerationEffects((ServerLevel) level, pos);
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
            return InteractionResult.SUCCESS;
        }

        player.sendSystemMessage(Component.literal("Nothing to accelerate here"));
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        player.getCooldowns().addCooldown(this, 100);

        AABB searchBox = new AABB(player.blockPosition()).inflate(12.0);
        List<Entity> entities = level.getEntities(player, searchBox,
                entity -> (entity instanceof AgeableMob || entity instanceof Villager)
                        && entity.distanceToSqr(player) <= 144);

        if (entities.isEmpty()) {
            System.out.println("DEBUG: No time-affected entities found nearby");
            player.sendSystemMessage(Component.literal("No time-affected entities nearby"));
            return InteractionResultHolder.pass(stack);
        }

        int accelerated = 0;
        System.out.println("DEBUG: Found " + entities.size() + " entities to accelerate");

        for (Entity entity : entities) {
            boolean entityChanged = false;

            if (entity instanceof AgeableMob ageable) {
                System.out.println("DEBUG: Accelerating ageable mob: " + entity.getClass().getSimpleName());
                entityChanged = accelerateAnimal(ageable, TIME_ACCELERATION_TICKS);
            }

            if (entity instanceof Villager villager) {
                System.out.println("DEBUG: Accelerating villager");
                entityChanged = accelerateVillager(villager, TIME_ACCELERATION_TICKS) || entityChanged;
            }

            if (entityChanged) {
                accelerated++;
                createTimeAccelerationEffects((ServerLevel) level, entity.blockPosition());
            }
        }

        if (accelerated > 0) {
            System.out.println("DEBUG: Successfully accelerated " + accelerated + " entities");
            player.sendSystemMessage(Component.literal("Time-accelerated " + accelerated + " entities by 14 minutes"));
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            return InteractionResultHolder.success(stack);
        }

        System.out.println("DEBUG: No entities were successfully accelerated");
        return InteractionResultHolder.pass(stack);
    }

    private boolean accelerateVillager(Villager villager, int ticks) {
        boolean changed = false;
        System.out.println("DEBUG: Starting villager acceleration");

        try {
            Level level = villager.level();
            Class<?> villagerClass = villager.getClass();
            int fieldsModified = 0;

            Class<?> currentClass = villagerClass;
            while (currentClass != null) {
                for (Field field : currentClass.getDeclaredFields()) {
                    field.setAccessible(true);
                    String fieldName = field.getName();

                    if (fieldName.equals("lastRestockGameTime") || fieldName.equals("f_35459_") ||
                            fieldName.equals("lastRestockCheckDayTime") || fieldName.equals("f_35460_") ||
                            fieldName.equals("numberOfRestocksToday") || fieldName.equals("f_35461_") ||
                            fieldName.contains("restock") || fieldName.contains("trade") ||
                            fieldName.contains("time") || fieldName.contains("tick") ||
                            fieldName.contains("cooldown") || fieldName.contains("delay")) {

                        try {
                            if (field.getType() == long.class) {
                                long current = field.getLong(villager);
                                field.setLong(villager, current - ticks);
                                System.out.println("DEBUG: Modified villager long field '" + fieldName + "' from " + current + " to " + (current - ticks));
                                fieldsModified++;
                                changed = true;
                            } else if (field.getType() == int.class) {
                                int current = field.getInt(villager);
                                if (fieldName.contains("restock") || fieldName.contains("count") ||
                                        fieldName.equals("numberOfRestocksToday") || fieldName.equals("f_35461_")) {
                                    field.setInt(villager, 0);
                                    System.out.println("DEBUG: Reset villager count field '" + fieldName + "' from " + current + " to 0");
                                } else if (fieldName.contains("cooldown") || fieldName.contains("delay")) {
                                    int newValue = Math.max(0, current - ticks);
                                    field.setInt(villager, newValue);
                                    System.out.println("DEBUG: Reduced villager cooldown field '" + fieldName + "' from " + current + " to " + newValue);
                                } else if (current > 0) {
                                    int newValue = Math.max(0, current - ticks);
                                    field.setInt(villager, newValue);
                                    System.out.println("DEBUG: Reduced villager int field '" + fieldName + "' from " + current + " to " + newValue);
                                }
                                fieldsModified++;
                                changed = true;
                            }
                        } catch (Exception e) {
                            System.out.println("DEBUG: Failed to modify villager field '" + fieldName + "': " + e.getMessage());
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }

            System.out.println("DEBUG: Modified " + fieldsModified + " villager fields using reflection");

            if (changed || true) {
                villager.restock();
                villager.refreshBrain((ServerLevel) level);
                changed = true;
                System.out.println("DEBUG: Forced villager restock and brain refresh");
            }

        } catch (Exception e) {
            System.out.println("DEBUG: Villager acceleration failed with exception: " + e.getMessage());
        }

        System.out.println("DEBUG: Villager acceleration result: " + changed);
        return changed;
    }

    private boolean accelerateBlockEntity(Level level, BlockPos pos, BlockEntity blockEntity, int ticks) {
        System.out.println("DEBUG: Starting block entity acceleration for: " + blockEntity.getClass().getSimpleName());

        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            System.out.println("DEBUG: Detected furnace, attempting direct acceleration");
            boolean result = accelerateFurnaceDirectly(furnace, ticks);
            if (!result) {
                System.out.println("DEBUG: Direct furnace acceleration failed, trying simulation");
                result = simulateFurnaceTicks(furnace, Math.min(ticks, 200));
                if (!result) {
                    System.out.println("DEBUG: Simulation failed, trying fallback item check");
                    ItemStack input = furnace.getItem(0);
                    ItemStack fuel = furnace.getItem(1);
                    if (!input.isEmpty() || !fuel.isEmpty()) {
                        furnace.setChanged();
                        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
                        System.out.println("DEBUG: Used fallback item check for furnace");
                        return true;
                    }
                    System.out.println("DEBUG: All furnace acceleration methods failed");
                }
            }
            return result;
        }

        if (blockEntity instanceof BrewingStandBlockEntity brewingStand) {
            System.out.println("DEBUG: Detected brewing stand");
            return accelerateBrewingStand(brewingStand, ticks);
        }

        if (blockEntity instanceof CampfireBlockEntity campfire) {
            System.out.println("DEBUG: Detected campfire");
            return accelerateCampfire(campfire, ticks);
        }

        if (blockEntity instanceof HopperBlockEntity hopper) {
            System.out.println("DEBUG: Detected hopper");
            return accelerateHopper(hopper, ticks);
        }

        if (blockEntity instanceof BeaconBlockEntity beacon) {
            System.out.println("DEBUG: Detected beacon");
            return accelerateBeacon(beacon, ticks);
        }

        if (blockEntity instanceof SculkSensorBlockEntity sculkSensor) {
            System.out.println("DEBUG: Detected sculk sensor");
            return accelerateSculkSensor(sculkSensor, ticks);
        }

        if (blockEntity instanceof SculkShriekerBlockEntity sculkShrieker) {
            System.out.println("DEBUG: Detected sculk shrieker");
            return accelerateSculkShrieker(sculkShrieker, ticks);
        }

        System.out.println("DEBUG: Unknown block entity type, using generic reflection");
        return accelerateWithReflection(blockEntity, level, pos, level.getBlockState(pos), ticks);
    }

    private boolean accelerateFurnaceDirectly(AbstractFurnaceBlockEntity furnace, int accelerationTicks) {
        Level level = furnace.getLevel();
        if (level == null || level.isClientSide) {
            System.out.println("DEBUG: Furnace acceleration failed - null level or client side");
            return false;
        }

        try {
            ItemStack inputItem = furnace.getItem(0);
            ItemStack fuelItem = furnace.getItem(1);
            System.out.println("DEBUG: Furnace items - Input: " + (!inputItem.isEmpty()) + ", Fuel: " + (!fuelItem.isEmpty()));

            if (inputItem.isEmpty() && fuelItem.isEmpty()) {
                System.out.println("DEBUG: Furnace is completely empty");
                return false;
            }

            Field litTimeField = findFieldByNames(furnace, "litTime", "f_58387_", "burnTime");
            Field litDurationField = findFieldByNames(furnace, "litDuration", "f_58388_", "totalBurnTime");
            Field cookingProgressField = findFieldByNames(furnace, "cookingProgress", "f_58389_", "cookTime");
            Field cookingTotalTimeField = findFieldByNames(furnace, "cookingTotalTime", "f_58390_", "totalCookTime");

            System.out.println("DEBUG: Found furnace fields - litTime: " + (litTimeField != null) +
                    ", cookingProgress: " + (cookingProgressField != null) +
                    ", litDuration: " + (litDurationField != null) +
                    ", cookingTotalTime: " + (cookingTotalTimeField != null));

            if (litTimeField == null || cookingProgressField == null) {
                System.out.println("DEBUG: Critical furnace fields not found");
                return false;
            }

            boolean hasChanges = false;
            int remainingTicks = accelerationTicks;
            int cyclesProcessed = 0;
            int totalFuelUsed = 0;

            System.out.println("DEBUG: Starting furnace processing with " + accelerationTicks + " ticks");

            while (remainingTicks >= 400 && cyclesProcessed < 100) {
                cyclesProcessed++;
                System.out.println("DEBUG: Processing cycle " + cyclesProcessed);

                inputItem = furnace.getItem(0);
                fuelItem = furnace.getItem(1);

                if (inputItem.isEmpty()) {
                    System.out.println("DEBUG: No more input items to process");
                    break;
                }

                if (!canSmeltItem(furnace, inputItem, level)) {
                    System.out.println("DEBUG: Current item cannot be smelted");
                    break;
                }

                int litTime = litTimeField.getInt(furnace);
                int cookingProgress = cookingProgressField.getInt(furnace);
                int cookingTotalTime = cookingTotalTimeField != null ? cookingTotalTimeField.getInt(furnace) : 200;

                System.out.println("DEBUG: Current furnace state - litTime: " + litTime + ", cookingProgress: " + cookingProgress + ", cookingTotalTime: " + cookingTotalTime);

                int recipeTime = getRecipeCookTime(furnace, inputItem, level);
                if (cookingTotalTimeField != null) {
                    cookingTotalTimeField.setInt(furnace, recipeTime);
                    cookingTotalTime = recipeTime;
                    System.out.println("DEBUG: Set recipe cooking time to " + recipeTime);
                }

                int ticksNeededToComplete = cookingTotalTime - cookingProgress;
                System.out.println("DEBUG: Ticks needed to complete current item: " + ticksNeededToComplete);

                if (litTime <= 0) {
                    System.out.println("DEBUG: Furnace not lit, checking fuel");
                    if (fuelItem.isEmpty()) {
                        System.out.println("DEBUG: No fuel available");
                        break;
                    }

                    int fuelBurnTime = getBurnTime(fuelItem);
                    if (fuelBurnTime <= 0) {
                        System.out.println("DEBUG: Invalid fuel item");
                        break;
                    }

                    System.out.println("DEBUG: Lighting furnace with fuel burn time: " + fuelBurnTime);
                    litTimeField.setInt(furnace, fuelBurnTime);
                    if (litDurationField != null) {
                        litDurationField.setInt(furnace, fuelBurnTime);
                    }

                    if (fuelItem.getCount() == 1) {
                        furnace.setItem(1, ItemStack.EMPTY);
                    } else {
                        fuelItem.shrink(1);
                    }
                    fuelItem = furnace.getItem(1);

                    litTime = fuelBurnTime;
                    totalFuelUsed++;
                    hasChanges = true;
                    System.out.println("DEBUG: Consumed fuel item, total fuel used: " + totalFuelUsed);
                }

                int availableFuelTicks = litTime;
                ItemStack currentFuel = fuelItem.copy();
                int additionalFuelTicks = 0;

                while (!currentFuel.isEmpty() && (availableFuelTicks + additionalFuelTicks) < ticksNeededToComplete) {
                    int fuelBurnTime = getBurnTime(currentFuel);
                    if (fuelBurnTime <= 0) break;

                    additionalFuelTicks += fuelBurnTime;
                    if (currentFuel.getCount() == 1) {
                        currentFuel = ItemStack.EMPTY;
                    } else {
                        currentFuel.shrink(1);
                    }
                }

                int totalAvailableFuel = availableFuelTicks + additionalFuelTicks;
                System.out.println("DEBUG: Total available fuel ticks: " + totalAvailableFuel + " (current: " + availableFuelTicks + ", additional: " + additionalFuelTicks + ")");

                int maxAdvance = Math.min(remainingTicks, Math.min(ticksNeededToComplete, totalAvailableFuel));
                System.out.println("DEBUG: Max advance for this cycle: " + maxAdvance);

                if (maxAdvance <= 0) {
                    System.out.println("DEBUG: Cannot advance further - insufficient fuel or time");
                    break;
                }

                cookingProgressField.setInt(furnace, cookingProgress + maxAdvance);
                System.out.println("DEBUG: Advanced cooking progress from " + cookingProgress + " to " + (cookingProgress + maxAdvance));

                int fuelToConsume = maxAdvance;

                if (fuelToConsume > 0 && litTime > 0) {
                    int consumeFromLit = Math.min(fuelToConsume, litTime);
                    litTimeField.setInt(furnace, litTime - consumeFromLit);
                    fuelToConsume -= consumeFromLit;
                    litTime -= consumeFromLit;
                    System.out.println("DEBUG: Consumed " + consumeFromLit + " fuel ticks from current lit time");
                }

                while (fuelToConsume > 0 && !fuelItem.isEmpty()) {
                    int fuelBurnTime = getBurnTime(fuelItem);
                    if (fuelBurnTime <= 0) break;

                    if (fuelItem.getCount() == 1) {
                        furnace.setItem(1, ItemStack.EMPTY);
                    } else {
                        fuelItem.shrink(1);
                    }
                    fuelItem = furnace.getItem(1);
                    totalFuelUsed++;

                    int fuelUsed = Math.min(fuelToConsume, fuelBurnTime);
                    fuelToConsume -= fuelUsed;

                    int leftoverFuel = fuelBurnTime - fuelUsed;
                    if (leftoverFuel > 0) {
                        litTimeField.setInt(furnace, leftoverFuel);
                        if (litDurationField != null) {
                            litDurationField.setInt(furnace, fuelBurnTime);
                        }
                    }
                    System.out.println("DEBUG: Consumed additional fuel item, leftover fuel: " + leftoverFuel);
                }

                cookingProgress += maxAdvance;
                remainingTicks -= maxAdvance;
                hasChanges = true;

                if (cookingProgress >= cookingTotalTime) {
                    System.out.println("DEBUG: Item finished cooking, attempting to complete smelting");
                    if (completeSmeltingProcess(furnace, inputItem, level)) {
                        cookingProgressField.setInt(furnace, 0);
                        if (cookingTotalTimeField != null) {
                            cookingTotalTimeField.setInt(furnace, 200);
                        }
                        System.out.println("DEBUG: Smelting completed, continuing to next item");
                        continue;
                    } else {
                        System.out.println("DEBUG: Cannot complete smelting - output full");
                        break;
                    }
                } else {
                    System.out.println("DEBUG: Item not finished yet");
                    break;
                }
            }

            if (hasChanges) {
                furnace.setChanged();
                level.sendBlockUpdated(furnace.getBlockPos(), level.getBlockState(furnace.getBlockPos()),
                        level.getBlockState(furnace.getBlockPos()), Block.UPDATE_ALL);
                System.out.println("DEBUG: Furnace direct acceleration successful - processed " + cyclesProcessed + " cycles, used " + totalFuelUsed + " fuel items");
                return true;
            }

        } catch (Exception e) {
            System.out.println("DEBUG: Furnace direct acceleration exception: " + e.getMessage());
            return false;
        }

        System.out.println("DEBUG: Furnace direct acceleration failed - no changes made");
        return false;
    }

    private boolean hasSufficientFuelForItems(AbstractFurnaceBlockEntity furnace, int ticksNeeded) {
        try {
            Field litTimeField = findFieldByNames(furnace, "litTime", "f_58387_", "burnTime");
            if (litTimeField == null) {
                System.out.println("DEBUG: Cannot find litTime field for fuel calculation");
                return false;
            }

            int currentLitTime = litTimeField.getInt(furnace);
            int totalFuelTicks = currentLitTime;
            System.out.println("DEBUG: Current lit time: " + currentLitTime);

            ItemStack fuelItem = furnace.getItem(1).copy();
            int fuelItemsChecked = 0;
            while (!fuelItem.isEmpty()) {
                int burnTime = getBurnTime(fuelItem);
                if (burnTime <= 0) break;

                totalFuelTicks += burnTime;
                fuelItemsChecked++;
                if (fuelItem.getCount() == 1) {
                    fuelItem = ItemStack.EMPTY;
                } else {
                    fuelItem.shrink(1);
                }

                if (totalFuelTicks >= ticksNeeded) {
                    System.out.println("DEBUG: Sufficient fuel found - total: " + totalFuelTicks + ", checked " + fuelItemsChecked + " fuel items");
                    return true;
                }
            }

            System.out.println("DEBUG: Insufficient fuel - total: " + totalFuelTicks + ", needed: " + ticksNeeded + ", checked " + fuelItemsChecked + " fuel items");
            return totalFuelTicks >= ticksNeeded;
        } catch (Exception e) {
            System.out.println("DEBUG: Fuel calculation failed: " + e.getMessage());
            return false;
        }
    }

    private int calculateMaxSmeltableItems(AbstractFurnaceBlockEntity furnace, Level level) {
        try {
            Field litTimeField = findFieldByNames(furnace, "litTime", "f_58387_", "burnTime");
            if (litTimeField == null) {
                System.out.println("DEBUG: Cannot calculate smeltable items - no litTime field");
                return 0;
            }

            int currentLitTime = litTimeField.getInt(furnace);
            int totalFuelTicks = currentLitTime;

            ItemStack fuelItem = furnace.getItem(1).copy();
            while (!fuelItem.isEmpty()) {
                int burnTime = getBurnTime(fuelItem);
                if (burnTime <= 0) break;

                totalFuelTicks += burnTime;
                if (fuelItem.getCount() == 1) {
                    fuelItem = ItemStack.EMPTY;
                } else {
                    fuelItem.shrink(1);
                }
            }

            ItemStack inputItem = furnace.getItem(0);
            if (inputItem.isEmpty()) {
                System.out.println("DEBUG: No input items to smelt");
                return 0;
            }

            int recipeTime = getRecipeCookTime(furnace, inputItem, level);
            if (recipeTime <= 0) {
                System.out.println("DEBUG: Invalid recipe time");
                return 0;
            }

            int maxItems = totalFuelTicks / recipeTime;
            System.out.println("DEBUG: Can smelt " + maxItems + " items with " + totalFuelTicks + " fuel ticks (recipe time: " + recipeTime + ")");
            return maxItems;

        } catch (Exception e) {
            System.out.println("DEBUG: Calculate smeltable items failed: " + e.getMessage());
            return 0;
        }
    }

    private boolean simulateFurnaceTicks(AbstractFurnaceBlockEntity furnace, int ticks) {
        System.out.println("DEBUG: Starting furnace tick simulation with " + ticks + " ticks");

        try {
            Level level = furnace.getLevel();
            BlockPos pos = furnace.getBlockPos();
            BlockState state = level.getBlockState(pos);

            Method serverTickMethod = findMethodByNames(furnace.getClass(),
                    "serverTick", "m_155014_", "tick");

            if (serverTickMethod != null) {
                serverTickMethod.setAccessible(true);
                System.out.println("DEBUG: Found serverTick method: " + serverTickMethod.getName() + " with " + serverTickMethod.getParameterCount() + " parameters");

                int safeTicks = Math.min(ticks, 100);
                System.out.println("DEBUG: Running " + safeTicks + " safe simulation ticks");

                for (int i = 0; i < safeTicks; i++) {
                    try {
                        if (serverTickMethod.getParameterCount() == 4) {
                            serverTickMethod.invoke(null, level, pos, state, furnace);
                        } else if (serverTickMethod.getParameterCount() == 0) {
                            serverTickMethod.invoke(furnace);
                        }
                    } catch (Exception e) {
                        System.out.println("DEBUG: Tick simulation failed at tick " + i + ": " + e.getMessage());
                        break;
                    }
                }

                furnace.setChanged();
                System.out.println("DEBUG: Furnace tick simulation completed successfully");
                return true;
            } else {
                System.out.println("DEBUG: No serverTick method found");
            }

            ItemStack input = furnace.getItem(0);
            ItemStack fuel = furnace.getItem(1);
            if (!input.isEmpty() || !fuel.isEmpty()) {
                furnace.setChanged();
                System.out.println("DEBUG: Used final fallback - marking furnace as changed");
                return true;
            }

        } catch (Exception e) {
            System.out.println("DEBUG: Furnace simulation exception: " + e.getMessage());
        }

        System.out.println("DEBUG: Furnace simulation failed completely");
        return false;
    }

    private int getRecipeCookTime(AbstractFurnaceBlockEntity furnace, ItemStack item, Level level) {
        try {
            RecipeManager recipeManager = level.getRecipeManager();
            Container container = furnace;

            String className = furnace.getClass().getSimpleName().toLowerCase();
            System.out.println("DEBUG: Getting recipe cook time for furnace type: " + className);

            if (className.contains("blastfurnace")) {
                Optional<BlastingRecipe> recipe = recipeManager.getRecipeFor(RecipeType.BLASTING, container, level);
                if (recipe.isPresent()) {
                    int cookTime = recipe.get().getCookingTime();
                    System.out.println("DEBUG: Found blasting recipe with cook time: " + cookTime);
                    return cookTime;
                } else {
                    System.out.println("DEBUG: No blasting recipe found, using default 100");
                    return 100;
                }
            } else if (className.contains("smoker")) {
                Optional<SmokingRecipe> recipe = recipeManager.getRecipeFor(RecipeType.SMOKING, container, level);
                if (recipe.isPresent()) {
                    int cookTime = recipe.get().getCookingTime();
                    System.out.println("DEBUG: Found smoking recipe with cook time: " + cookTime);
                    return cookTime;
                } else {
                    System.out.println("DEBUG: No smoking recipe found, using default 100");
                    return 100;
                }
            } else {
                Optional<SmeltingRecipe> recipe = recipeManager.getRecipeFor(RecipeType.SMELTING, container, level);
                if (recipe.isPresent()) {
                    int cookTime = recipe.get().getCookingTime();
                    System.out.println("DEBUG: Found smelting recipe with cook time: " + cookTime);
                    return cookTime;
                } else {
                    System.out.println("DEBUG: No smelting recipe found, using default 200");
                    return 200;
                }
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Recipe lookup failed: " + e.getMessage());
            String className = furnace.getClass().getSimpleName().toLowerCase();
            if (className.contains("blastfurnace") || className.contains("smoker")) {
                System.out.println("DEBUG: Using fallback cook time 100 for fast furnace");
                return 100;
            }
            System.out.println("DEBUG: Using fallback cook time 200 for regular furnace");
            return 200;
        }
    }

    private int getBurnTime(ItemStack fuel) {
        if (fuel.isEmpty()) {
            System.out.println("DEBUG: Empty fuel stack");
            return 0;
        }

        System.out.println("DEBUG: Getting burn time for fuel: " + fuel.getItem().toString());

        try {
            int burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(fuel, null);
            System.out.println("DEBUG: ForgeHooks.getBurnTime() result: " + burnTime);
            if (burnTime > 0) {
                System.out.println("DEBUG: Used ForgeHooks.getBurnTime(), result: " + burnTime);
                return burnTime;
            }
        } catch (Exception e) {
            System.out.println("DEBUG: ForgeHooks.getBurnTime() failed: " + e.getMessage());
        }

        String itemName = fuel.getItem().toString().toLowerCase();
        int fallbackValue = 0;

        if (itemName.contains("coal_block")) fallbackValue = 16000;
        else if (itemName.contains("coal")) fallbackValue = 1600;
        else if (itemName.contains("charcoal")) fallbackValue = 1600;
        else if (itemName.contains("lava_bucket")) fallbackValue = 20000;
        else if (itemName.contains("blaze_rod")) fallbackValue = 2400;
        else if (itemName.contains("dried_kelp_block")) fallbackValue = 4000;
        else if (itemName.contains("log") || itemName.contains("wood")) fallbackValue = 300;
        else if (itemName.contains("planks")) fallbackValue = 300;
        else if (itemName.contains("stick")) fallbackValue = 100;
        else if (itemName.contains("bamboo")) fallbackValue = 50;
        else if (itemName.contains("sapling")) fallbackValue = 100;
        else if (itemName.contains("wood") || itemName.contains("coal") || itemName.contains("fuel") ||
                itemName.contains("burn") || itemName.contains("fire")) {
            fallbackValue = 200;
        }

        if (fallbackValue > 0) {
            System.out.println("DEBUG: Used hardcoded fallback burn time for '" + itemName + "': " + fallbackValue);
        } else {
            System.out.println("DEBUG: No burn time found for '" + itemName + "'");
        }

        return fallbackValue;
    }

    private boolean canSmeltItem(AbstractFurnaceBlockEntity furnace, ItemStack item, Level level) {
        if (item.isEmpty()) {
            System.out.println("DEBUG: Cannot smelt empty item");
            return false;
        }

        try {
            RecipeManager recipeManager = level.getRecipeManager();
            Container container = furnace;
            String className = furnace.getClass().getSimpleName().toLowerCase();

            boolean hasRecipe = false;
            if (className.contains("blastfurnace")) {
                hasRecipe = recipeManager.getRecipeFor(RecipeType.BLASTING, container, level).isPresent();
                System.out.println("DEBUG: Blast furnace recipe check for " + item.getItem() + ": " + hasRecipe);
            } else if (className.contains("smoker")) {
                hasRecipe = recipeManager.getRecipeFor(RecipeType.SMOKING, container, level).isPresent();
                System.out.println("DEBUG: Smoker recipe check for " + item.getItem() + ": " + hasRecipe);
            } else {
                hasRecipe = recipeManager.getRecipeFor(RecipeType.SMELTING, container, level).isPresent();
                System.out.println("DEBUG: Smelting recipe check for " + item.getItem() + ": " + hasRecipe);
            }

            return hasRecipe;
        } catch (Exception e) {
            System.out.println("DEBUG: Recipe check failed, using fallback item analysis: " + e.getMessage());
            String itemName = item.getItem().toString().toLowerCase();
            boolean canSmelt = itemName.contains("ore") || itemName.contains("raw_") ||
                    itemName.contains("food") || itemName.contains("meat") ||
                    itemName.contains("fish") || itemName.contains("potato") ||
                    itemName.contains("log") || itemName.contains("sand");
            System.out.println("DEBUG: Fallback smelt check for '" + itemName + "': " + canSmelt);
            return canSmelt;
        }
    }

    private boolean completeSmeltingProcess(AbstractFurnaceBlockEntity furnace, ItemStack inputItem, Level level) {
        System.out.println("DEBUG: Starting smelting completion process");

        try {
            RecipeManager recipeManager = level.getRecipeManager();
            Container container = furnace;

            AbstractCookingRecipe recipe = null;
            ItemStack result = ItemStack.EMPTY;

            String className = furnace.getClass().getSimpleName().toLowerCase();
            if (className.contains("blastfurnace")) {
                Optional<BlastingRecipe> blastingRecipe = recipeManager.getRecipeFor(RecipeType.BLASTING, container, level);
                if (blastingRecipe.isPresent()) {
                    recipe = blastingRecipe.get();
                    result = recipe.getResultItem(level.registryAccess()).copy();
                    System.out.println("DEBUG: Found blasting recipe, result: " + result.getItem());
                }
            } else if (className.contains("smoker")) {
                Optional<SmokingRecipe> smokingRecipe = recipeManager.getRecipeFor(RecipeType.SMOKING, container, level);
                if (smokingRecipe.isPresent()) {
                    recipe = smokingRecipe.get();
                    result = recipe.getResultItem(level.registryAccess()).copy();
                    System.out.println("DEBUG: Found smoking recipe, result: " + result.getItem());
                }
            } else {
                Optional<SmeltingRecipe> smeltingRecipe = recipeManager.getRecipeFor(RecipeType.SMELTING, container, level);
                if (smeltingRecipe.isPresent()) {
                    recipe = smeltingRecipe.get();
                    result = recipe.getResultItem(level.registryAccess()).copy();
                    System.out.println("DEBUG: Found smelting recipe, result: " + result.getItem());
                }
            }

            if (result.isEmpty() || recipe == null) {
                System.out.println("DEBUG: No valid recipe found for smelting completion");
                return false;
            }

            ItemStack outputSlot = furnace.getItem(2);
            if (outputSlot.isEmpty()) {
                furnace.setItem(2, result);
                System.out.println("DEBUG: Placed result in empty output slot");
            } else if (ItemStack.isSameItemSameTags(outputSlot, result)) {
                int maxStack = Math.min(outputSlot.getMaxStackSize(), 64);
                if (outputSlot.getCount() + result.getCount() <= maxStack) {
                    outputSlot.grow(result.getCount());
                    System.out.println("DEBUG: Stacked result with existing output, new count: " + outputSlot.getCount());
                } else {
                    System.out.println("DEBUG: Output slot would overflow, cannot complete");
                    return false;
                }
            } else {
                System.out.println("DEBUG: Different items in output slot, cannot place result");
                return false;
            }

            float experience = recipe.getExperience();
            if (experience > 0) {
                System.out.println("DEBUG: Recipe gives " + experience + " experience");
                boolean usedActualExp = false;
                try {
                    Field recipesUsedField = findFieldByNames(furnace, "recipesUsed", "f_58391_");
                    if (recipesUsedField != null) {
                        recipesUsedField.setAccessible(true);
                        Object recipesUsedObj = recipesUsedField.get(furnace);
                        System.out.println("DEBUG: Found recipesUsed field");

                        if (recipesUsedObj != null) {
                            try {
                                Method addToMethod = recipesUsedObj.getClass().getMethod("addTo", Object.class, int.class);
                                addToMethod.invoke(recipesUsedObj, recipe.getId(), 1);
                                usedActualExp = true;
                                System.out.println("DEBUG: Used actual furnace XP storage (addTo method)");
                            } catch (Exception e) {
                                try {
                                    Method putMethod = recipesUsedObj.getClass().getMethod("put", Object.class, Object.class);
                                    Method getMethod = recipesUsedObj.getClass().getMethod("getOrDefault", Object.class, Object.class);
                                    Object currentCount = getMethod.invoke(recipesUsedObj, recipe.getId(), 0);
                                    if (currentCount instanceof Integer) {
                                        putMethod.invoke(recipesUsedObj, recipe.getId(), ((Integer) currentCount) + 1);
                                        usedActualExp = true;
                                        System.out.println("DEBUG: Used actual furnace XP storage (put method)");
                                    }
                                } catch (Exception e2) {
                                    System.out.println("DEBUG: Both XP storage methods failed, falling back to direct XP");
                                    awardDirectExperience(furnace, recipe, level);
                                }
                            }
                        } else {
                            System.out.println("DEBUG: recipesUsed object is null, using direct XP");
                            awardDirectExperience(furnace, recipe, level);
                        }
                    } else {
                        System.out.println("DEBUG: recipesUsed field not found, using direct XP");
                        awardDirectExperience(furnace, recipe, level);
                    }
                } catch (Exception e) {
                    System.out.println("DEBUG: Exception in XP handling: " + e.getMessage() + ", using direct XP");
                    awardDirectExperience(furnace, recipe, level);
                }

                if (!usedActualExp) {
                    System.out.println("DEBUG: Used direct XP orb spawning");
                }
            } else {
                System.out.println("DEBUG: Recipe gives no experience");
            }

            inputItem.shrink(1);
            if (inputItem.isEmpty()) {
                furnace.setItem(0, ItemStack.EMPTY);
            }
            System.out.println("DEBUG: Consumed input item, remaining: " + inputItem.getCount());

            return true;

        } catch (Exception e) {
            System.out.println("DEBUG: Smelting completion failed: " + e.getMessage());
            return false;
        }
    }

    private void awardDirectExperience(AbstractFurnaceBlockEntity furnace, AbstractCookingRecipe recipe, Level level) {
        try {
            float experience = recipe.getExperience();
            if (experience <= 0) {
                System.out.println("DEBUG: No experience to award");
                return;
            }

            BlockPos pos = furnace.getBlockPos();
            List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class,
                    new AABB(pos).inflate(8.0),
                    player -> player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 64);

            if (!nearbyPlayers.isEmpty()) {
                Player closestPlayer = nearbyPlayers.get(0);
                double closestDistance = closestPlayer.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());

                for (Player player : nearbyPlayers) {
                    double distance = player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                    if (distance < closestDistance) {
                        closestPlayer = player;
                        closestDistance = distance;
                    }
                }

                if (level instanceof ServerLevel serverLevel) {
                    int xpPoints = Math.round(experience * 10);
                    net.minecraft.world.entity.ExperienceOrb.award(serverLevel,
                            net.minecraft.world.phys.Vec3.atCenterOf(pos), xpPoints);
                    System.out.println("DEBUG: Awarded " + xpPoints + " XP points to closest player " + closestPlayer.getName().getString());
                }
            } else {
                System.out.println("DEBUG: No nearby players found for XP award");
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Direct XP award failed: " + e.getMessage());
        }
    }

    private boolean accelerateBrewingStand(BrewingStandBlockEntity brewingStand, int ticks) {
        Level level = brewingStand.getLevel();
        BlockPos pos = brewingStand.getBlockPos();
        System.out.println("DEBUG: Starting brewing stand acceleration");

        try {
            int remainingTicks = ticks;
            boolean anyBrewing = false;

            Field brewTimeField = findFieldByNames(brewingStand, "brewTime", "f_59123_");
            Field fuelField = findFieldByNames(brewingStand, "fuel", "f_59124_");

            System.out.println("DEBUG: Found brewing fields - brewTime: " + (brewTimeField != null) + ", fuel: " + (fuelField != null));

            if (brewTimeField != null && brewTimeField.getType() == int.class) {
                int cyclesCompleted = 0;
                while (remainingTicks >= 400 && isBrewingStandActive(brewingStand)) {
                    int currentBrewTime = brewTimeField.getInt(brewingStand);
                    System.out.println("DEBUG: Current brew time: " + currentBrewTime);

                    if (currentBrewTime > 0) {
                        int newTime = Math.max(0, currentBrewTime - 400);
                        brewTimeField.setInt(brewingStand, newTime);
                        System.out.println("DEBUG: Advanced brew time from " + currentBrewTime + " to " + newTime);

                        if (newTime == 0) {
                            System.out.println("DEBUG: Brewing cycle completed, attempting completion");
                            tryCompleteBrewingCycle(brewingStand);
                            cyclesCompleted++;
                        }

                        anyBrewing = true;
                        remainingTicks -= 400;
                    } else {
                        System.out.println("DEBUG: No active brewing to accelerate");
                        break;
                    }
                }
                System.out.println("DEBUG: Completed " + cyclesCompleted + " brewing cycles");
            } else {
                System.out.println("DEBUG: Cannot find or access brewTime field");
            }

            if (anyBrewing) {
                brewingStand.setChanged();
                if (level != null) level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
                System.out.println("DEBUG: Brewing stand acceleration successful using actual game values");
                return true;
            }

        } catch (Exception e) {
            System.out.println("DEBUG: Brewing stand acceleration failed, trying reflection fallback: " + e.getMessage());
            return accelerateWithReflection(brewingStand, level, pos, level.getBlockState(pos), ticks);
        }

        System.out.println("DEBUG: Brewing stand acceleration failed completely");
        return false;
    }

    private void tryCompleteBrewingCycle(BrewingStandBlockEntity brewingStand) {
        System.out.println("DEBUG: Attempting to complete brewing cycle");

        try {
            Method[] methods = brewingStand.getClass().getDeclaredMethods();
            boolean foundMethod = false;

            for (Method method : methods) {
                String name = method.getName();
                if (name.contains("brew") || name.contains("doBrew") || name.equals("m_59134_")) {
                    try {
                        method.setAccessible(true);
                        if (method.getParameterCount() == 0) {
                            method.invoke(brewingStand);
                            System.out.println("DEBUG: Successfully called brewing completion method: " + name);
                            foundMethod = true;
                            break;
                        }
                    } catch (Exception e) {
                        System.out.println("DEBUG: Failed to call brewing method '" + name + "': " + e.getMessage());
                        continue;
                    }
                }
            }

            if (!foundMethod) {
                System.out.println("DEBUG: No brewing completion method found");
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Brewing completion failed: " + e.getMessage());
        }
    }

    private boolean isBrewingStandActive(BrewingStandBlockEntity brewingStand) {
        try {
            Field brewTimeField = findFieldByNames(brewingStand, "brewTime", "f_59123_");
            if (brewTimeField != null && brewTimeField.getType() == int.class) {
                int brewTime = brewTimeField.getInt(brewingStand);
                System.out.println("DEBUG: Brewing stand brew time: " + brewTime);
                return brewTime > 0;
            }

            System.out.println("DEBUG: Cannot access brew time, checking items");
            boolean hasItems = !brewingStand.getItem(0).isEmpty() || !brewingStand.getItem(1).isEmpty() ||
                    !brewingStand.getItem(2).isEmpty() || !brewingStand.getItem(3).isEmpty();
            System.out.println("DEBUG: Brewing stand has items: " + hasItems);
            return hasItems;
        } catch (Exception e) {
            System.out.println("DEBUG: Brewing stand activity check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean accelerateCampfire(CampfireBlockEntity campfire, int ticks) {
        System.out.println("DEBUG: Starting campfire acceleration");

        try {
            Field cookingProgressField = findFieldByNames(campfire, "cookingProgress", "f_59042_");
            Field cookingTimeField = findFieldByNames(campfire, "cookingTime", "f_59043_");

            System.out.println("DEBUG: Found campfire fields - cookingProgress: " + (cookingProgressField != null) + ", cookingTime: " + (cookingTimeField != null));

            if (cookingProgressField != null && cookingTimeField != null) {
                Object progressArray = cookingProgressField.get(campfire);
                Object timeArray = cookingTimeField.get(campfire);

                if (progressArray instanceof int[] progressArr && timeArray instanceof int[] timesArr) {
                    System.out.println("DEBUG: Successfully accessed campfire arrays");
                    boolean anyChanges = false;
                    int slotsProcessed = 0;

                    for (int slot = 0; slot < 4; slot++) {
                        if (hasItemInSlot(campfire, slot) && timesArr[slot] > 0) {
                            int oldProgress = progressArr[slot];
                            progressArr[slot] = Math.min(600, progressArr[slot] + ticks);
                            System.out.println("DEBUG: Slot " + slot + " progress: " + oldProgress + " -> " + progressArr[slot]);

                            if (progressArr[slot] >= 600) {
                                timesArr[slot] = 0;
                                progressArr[slot] = 0;
                                System.out.println("DEBUG: Slot " + slot + " cooking completed");
                            }
                            anyChanges = true;
                            slotsProcessed++;
                        }
                    }

                    if (anyChanges) {
                        campfire.setChanged();
                        System.out.println("DEBUG: Campfire acceleration successful using actual game values - processed " + slotsProcessed + " slots");
                        return true;
                    } else {
                        System.out.println("DEBUG: No campfire slots had items to cook");
                    }
                } else {
                    System.out.println("DEBUG: Campfire arrays are wrong type");
                }
            } else {
                System.out.println("DEBUG: Cannot find campfire cooking fields");
            }

        } catch (Exception e) {
            System.out.println("DEBUG: Campfire acceleration failed, trying reflection fallback: " + e.getMessage());
            return accelerateWithReflection(campfire, campfire.getLevel(), campfire.getBlockPos(),
                    campfire.getLevel().getBlockState(campfire.getBlockPos()), ticks);
        }

        System.out.println("DEBUG: Campfire acceleration failed completely");
        return false;
    }

    private boolean hasItemInSlot(CampfireBlockEntity campfire, int slot) {
        try {
            Field itemsField = findFieldByNames(campfire, "items", "f_58857_");
            if (itemsField != null) {
                Object itemsObj = itemsField.get(campfire);
                if (itemsObj instanceof net.minecraft.core.NonNullList<?> items && slot < items.size()) {
                    Object item = items.get(slot);
                    if (item instanceof ItemStack stack) {
                        boolean hasItem = !stack.isEmpty();
                        System.out.println("DEBUG: Campfire slot " + slot + " has item: " + hasItem);
                        return hasItem;
                    }
                }
            }
            System.out.println("DEBUG: Cannot check campfire slot " + slot);
        } catch (Exception e) {
            System.out.println("DEBUG: Error checking campfire slot " + slot + ": " + e.getMessage());
        }
        return false;
    }

    private boolean accelerateHopper(HopperBlockEntity hopper, int ticks) {
        System.out.println("DEBUG: Starting hopper acceleration");

        try {
            Field cooldownField = findFieldByNames(hopper, "cooldownTime", "f_59309_");
            if (cooldownField != null && cooldownField.getType() == int.class) {
                int oldCooldown = cooldownField.getInt(hopper);
                cooldownField.setInt(hopper, 0);
                System.out.println("DEBUG: Reset hopper cooldown from " + oldCooldown + " to 0");
            } else {
                System.out.println("DEBUG: Cannot find hopper cooldown field");
            }

            int transfersToPerform = ticks / 8;
            boolean anyTransfers = false;
            System.out.println("DEBUG: Attempting " + transfersToPerform + " hopper transfers");

            for (int i = 0; i < Math.min(transfersToPerform, 500); i++) {
                Method[] methods = hopper.getClass().getDeclaredMethods();
                boolean foundTransferMethod = false;

                for (Method method : methods) {
                    String name = method.getName();
                    if (name.equals("tryMoveItems") || name.equals("m_59351_")) {
                        try {
                            method.setAccessible(true);
                            if (method.getParameterCount() <= 3) {
                                Object result = method.invoke(null, hopper.getLevel(), hopper.getBlockPos(),
                                        hopper.getLevel().getBlockState(hopper.getBlockPos()), hopper);
                                if (result instanceof Boolean && (Boolean) result) {
                                    anyTransfers = true;
                                    System.out.println("DEBUG: Successful hopper transfer " + (i + 1) + " using method: " + name);
                                }
                                foundTransferMethod = true;
                                break;
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }

                if (!foundTransferMethod && i == 0) {
                    System.out.println("DEBUG: No hopper transfer method found");
                    break;
                }
            }

            if (anyTransfers) {
                hopper.setChanged();
                System.out.println("DEBUG: Hopper acceleration successful using actual game transfer methods");
                return true;
            } else {
                System.out.println("DEBUG: No hopper transfers occurred");
            }

        } catch (Exception e) {
            System.out.println("DEBUG: Hopper acceleration failed, trying reflection fallback: " + e.getMessage());
            return accelerateWithReflection(hopper, hopper.getLevel(), hopper.getBlockPos(),
                    hopper.getLevel().getBlockState(hopper.getBlockPos()), ticks);
        }

        System.out.println("DEBUG: Hopper acceleration failed completely");
        return false;
    }

    private boolean accelerateBeacon(BeaconBlockEntity beacon, int ticks) {
        System.out.println("DEBUG: Starting beacon acceleration");

        try {
            Level level = beacon.getLevel();
            BlockPos pos = beacon.getBlockPos();

            int updates = ticks / 80;
            System.out.println("DEBUG: Scheduling " + updates + " beacon updates");

            for (int i = 0; i < Math.min(updates, 210); i++) {
                beacon.setChanged();
                level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 1);
            }

            System.out.println("DEBUG: Beacon acceleration successful using game tick scheduling");
            return true;
        } catch (Exception e) {
            System.out.println("DEBUG: Beacon acceleration failed: " + e.getMessage());
            return false;
        }
    }

    private boolean accelerateSculkSensor(SculkSensorBlockEntity sculkSensor, int ticks) {
        System.out.println("DEBUG: Starting sculk sensor acceleration");

        try {
            Field cooldownField = findFieldByNames(sculkSensor, "cooldownTicks", "f_222679_");
            if (cooldownField != null) {
                if (cooldownField.getType() == int.class) {
                    int current = cooldownField.getInt(sculkSensor);
                    if (current > 0) {
                        int newValue = Math.max(0, current - ticks);
                        cooldownField.setInt(sculkSensor, newValue);
                        sculkSensor.setChanged();
                        System.out.println("DEBUG: Sculk sensor acceleration successful using actual cooldown field - reduced from " + current + " to " + newValue);
                        return true;
                    } else {
                        System.out.println("DEBUG: Sculk sensor has no cooldown to reduce");
                    }
                } else {
                    System.out.println("DEBUG: Sculk sensor cooldown field is wrong type");
                }
            } else {
                System.out.println("DEBUG: Cannot find sculk sensor cooldown field");
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Sculk sensor acceleration failed, trying reflection fallback: " + e.getMessage());
            Level sensorLevel = sculkSensor.getLevel();
            BlockPos sensorPos = sculkSensor.getBlockPos();
            return accelerateWithReflection(sculkSensor, sensorLevel, sensorPos,
                    sensorLevel.getBlockState(sensorPos), ticks);
        }

        System.out.println("DEBUG: Sculk sensor acceleration failed completely");
        return false;
    }

    private boolean accelerateSculkShrieker(SculkShriekerBlockEntity sculkShrieker, int ticks) {
        System.out.println("DEBUG: Starting sculk shrieker acceleration");

        try {
            Field warningField = findFieldByNames(sculkShrieker, "warningLevel", "f_222858_");
            if (warningField != null) {
                if (warningField.getType() == int.class) {
                    int current = warningField.getInt(sculkShrieker);
                    if (current > 0) {
                        int newValue = Math.max(0, current - ticks);
                        warningField.setInt(sculkShrieker, newValue);
                        sculkShrieker.setChanged();
                        System.out.println("DEBUG: Sculk shrieker acceleration successful using actual warning field - reduced from " + current + " to " + newValue);
                        return true;
                    } else {
                        System.out.println("DEBUG: Sculk shrieker has no warning level to reduce");
                    }
                } else {
                    System.out.println("DEBUG: Sculk shrieker warning field is wrong type");
                }
            } else {
                System.out.println("DEBUG: Cannot find sculk shrieker warning field");
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Sculk shrieker acceleration failed, trying reflection fallback: " + e.getMessage());
            Level shriekerLevel = sculkShrieker.getLevel();
            BlockPos shriekerPos = sculkShrieker.getBlockPos();
            return accelerateWithReflection(sculkShrieker, shriekerLevel, shriekerPos,
                    shriekerLevel.getBlockState(shriekerPos), ticks);
        }

        System.out.println("DEBUG: Sculk shrieker acceleration failed completely");
        return false;
    }

    private boolean accelerateWithReflection(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int ticks) {
        System.out.println("DEBUG: Starting generic reflection acceleration for " + blockEntity.getClass().getSimpleName());

        try {
            boolean changed = false;
            int fieldsModified = 0;

            Class<?> currentClass = blockEntity.getClass();
            while (currentClass != null) {
                for (Field field : currentClass.getDeclaredFields()) {
                    field.setAccessible(true);
                    String fieldName = field.getName().toLowerCase();

                    if ((fieldName.contains("time") || fieldName.contains("tick") ||
                            fieldName.contains("cooldown") || fieldName.contains("progress") ||
                            fieldName.startsWith("f_") && field.getType() == int.class) &&
                            (field.getType() == int.class || field.getType() == long.class)) {

                        try {
                            if (field.getType() == int.class) {
                                int current = field.getInt(blockEntity);
                                if (current > 0) {
                                    if (fieldName.contains("progress") || fieldName.contains("cooking") ||
                                            fieldName.contains("brew") || fieldName.contains("burn")) {
                                        field.setInt(blockEntity, current + ticks);
                                        System.out.println("DEBUG: Advanced progress field '" + fieldName + "' from " + current + " to " + (current + ticks));
                                    } else {
                                        int newValue = Math.max(0, current - ticks);
                                        field.setInt(blockEntity, newValue);
                                        System.out.println("DEBUG: Reduced cooldown field '" + fieldName + "' from " + current + " to " + newValue);
                                    }
                                    changed = true;
                                    fieldsModified++;
                                }
                            } else {
                                long current = field.getLong(blockEntity);
                                if (current > 0) {
                                    long newValue = Math.max(0, current - ticks);
                                    field.setLong(blockEntity, newValue);
                                    System.out.println("DEBUG: Reduced long field '" + fieldName + "' from " + current + " to " + newValue);
                                    changed = true;
                                    fieldsModified++;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("DEBUG: Failed to modify field '" + fieldName + "': " + e.getMessage());
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }

            if (changed) {
                blockEntity.setChanged();
                System.out.println("DEBUG: Generic reflection acceleration successful - modified " + fieldsModified + " fields");
                return true;
            }

            System.out.println("DEBUG: No fields modified, trying tick methods");
            Method[] methods = blockEntity.getClass().getDeclaredMethods();
            boolean foundTickMethod = false;

            for (Method method : methods) {
                String name = method.getName();
                if ((name.equals("tick") || name.contains("Tick") || name.equals("m_6596_")) &&
                        method.getParameterCount() <= 4) {

                    method.setAccessible(true);
                    foundTickMethod = true;
                    System.out.println("DEBUG: Found tick method: " + name + " with " + method.getParameterCount() + " parameters");

                    int safeTicks = Math.min(ticks, 200);
                    System.out.println("DEBUG: Running " + safeTicks + " tick method calls");

                    for (int i = 0; i < safeTicks; i++) {
                        try {
                            if (method.getParameterCount() == 0) {
                                method.invoke(blockEntity);
                            } else if (method.getParameterCount() == 4) {
                                method.invoke(blockEntity, level, pos, state, blockEntity);
                            } else if (method.getParameterCount() == 3) {
                                method.invoke(blockEntity, level, pos, state);
                            }
                        } catch (Exception e) {
                            System.out.println("DEBUG: Tick method failed at iteration " + i + ": " + e.getMessage());
                            break;
                        }
                    }
                    System.out.println("DEBUG: Tick method simulation completed");
                    return true;
                }
            }

            if (!foundTickMethod) {
                System.out.println("DEBUG: No tick method found for " + blockEntity.getClass().getSimpleName());
            }

        } catch (Exception e) {
            System.out.println("DEBUG: Generic reflection acceleration failed: " + e.getMessage());
        }

        System.out.println("DEBUG: All reflection acceleration methods failed");
        return false;
    }

    private boolean accelerateBlock(Level level, BlockPos pos, int ticks) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        RandomSource random = level.getRandom();
        boolean changed = false;

        System.out.println("DEBUG: Starting block acceleration for " + block.getClass().getSimpleName() + " at " + pos);

        if (block instanceof BonemealableBlock bonemealable) {
            int bonemealApplications = Math.min(ticks / 100, 168);
            System.out.println("DEBUG: Attempting " + bonemealApplications + " bonemeal applications");

            int successfulApplications = 0;
            for (int i = 0; i < bonemealApplications; i++) {
                try {
                    if (bonemealable.isValidBonemealTarget(level, pos, state, false)) {
                        if (random.nextFloat() < 0.9f) {
                            if (bonemealable.isBonemealSuccess(level, random, pos, state)) {
                                bonemealable.performBonemeal((ServerLevel) level, random, pos, state);
                                state = level.getBlockState(pos);
                                block = state.getBlock();
                                changed = true;
                                successfulApplications++;

                                if (!(block instanceof BonemealableBlock)) {
                                    System.out.println("DEBUG: Block changed to non-bonemeala​ble type: " + block.getClass().getSimpleName());
                                    break;
                                }
                            }
                        }
                    } else {
                        System.out.println("DEBUG: Block no longer valid bonemeal target after " + successfulApplications + " applications");
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("DEBUG: Bonemeal application " + (i + 1) + " failed: " + e.getMessage());
                    break;
                }
            }
            System.out.println("DEBUG: Applied bonemeal " + successfulApplications + " times successfully");
        } else {
            System.out.println("DEBUG: Block is not bonemeala​ble");
        }

        if (block.isRandomlyTicking(state)) {
            int randomTicksToSimulate = Math.min(ticks / 600, 28);
            System.out.println("DEBUG: Block is randomly ticking, simulating " + randomTicksToSimulate + " random ticks");

            int successfulTicks = 0;
            for (int i = 0; i < randomTicksToSimulate; i++) {
                try {
                    block.randomTick(state, (ServerLevel) level, pos, random);
                    state = level.getBlockState(pos);
                    changed = true;
                    successfulTicks++;
                } catch (Exception e) {
                    System.out.println("DEBUG: Random tick failed at iteration " + i + ": " + e.getMessage());
                    break;
                }
            }
            System.out.println("DEBUG: Executed " + successfulTicks + " successful random ticks");
        } else {
            System.out.println("DEBUG: Block does not have random ticking");
        }

        if (!changed) {
            System.out.println("DEBUG: No standard acceleration worked, trying age property acceleration");
            changed = accelerateAgeProperty(level, pos, state, ticks, random);
        }

        System.out.println("DEBUG: Block acceleration result: " + changed);
        return changed;
    }

    private boolean accelerateAgeProperty(Level level, BlockPos pos, BlockState state, int ticks, RandomSource random) {
        System.out.println("DEBUG: Starting age property acceleration");

        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty intProp) {
                String propName = property.getName().toLowerCase();
                System.out.println("DEBUG: Checking property: " + propName);

                if (propName.contains("age") || propName.contains("stage") ||
                        propName.contains("growth") || propName.contains("level") ||
                        propName.contains("distance") || propName.contains("delay") ||
                        propName.contains("power") || propName.contains("charges")) {
                    try {
                        int currentValue = state.getValue(intProp);
                        int maxValue = intProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(currentValue);
                        int minValue = intProp.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(currentValue);

                        System.out.println("DEBUG: Property '" + propName + "' current: " + currentValue + ", min: " + minValue + ", max: " + maxValue);

                        if (currentValue < maxValue) {
                            int newValue = currentValue;
                            int growthTicks = 0;
                            for (int i = 0; i < ticks && newValue < maxValue; i++) {
                                if (random.nextFloat() < 0.3f) {
                                    newValue++;
                                    growthTicks++;
                                }
                            }

                            if (newValue != currentValue) {
                                BlockState newState = state.setValue(intProp, Math.min(newValue, maxValue));
                                level.setBlockAndUpdate(pos, newState);
                                System.out.println("DEBUG: Advanced growth property '" + propName + "' from " + currentValue + " to " + newValue + " (grew " + growthTicks + " times)");
                                return true;
                            }
                        }

                        if (propName.contains("delay") || propName.contains("cooldown") || propName.contains("distance")) {
                            if (currentValue > minValue) {
                                int newValue = Math.max(minValue, currentValue - (ticks / 20));
                                if (newValue != currentValue) {
                                    BlockState newState = state.setValue(intProp, newValue);
                                    level.setBlockAndUpdate(pos, newState);
                                    System.out.println("DEBUG: Reduced delay property '" + propName + "' from " + currentValue + " to " + newValue);
                                    return true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("DEBUG: Failed to modify property '" + propName + "': " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("DEBUG: No age properties could be accelerated");
        return false;
    }

    private boolean accelerateAnimal(AgeableMob animal, int ticks) {
        boolean changed = false;
        System.out.println("DEBUG: Accelerating animal: " + animal.getClass().getSimpleName());

        if (animal.isBaby()) {
            int currentAge = animal.getAge();
            int newAge = Math.min(0, currentAge + ticks);
            animal.setAge(newAge);
            changed = (currentAge != newAge);
            System.out.println("DEBUG: Baby animal age accelerated from " + currentAge + " to " + newAge + " (changed: " + changed + ")");
        } else if (animal instanceof Animal animalEntity) {
            int breedingAge = animalEntity.getAge();
            if (breedingAge > 0) {
                int newAge = Math.max(0, breedingAge - ticks);
                animalEntity.setAge(newAge);
                changed = true;
                System.out.println("DEBUG: Adult animal breeding cooldown reduced from " + breedingAge + " to " + newAge);
            } else {
                System.out.println("DEBUG: Adult animal has no breeding cooldown");
            }
        }

        System.out.println("DEBUG: Animal acceleration result: " + changed);
        return changed;
    }

    private Field findFieldByNames(Object obj, String... names) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                for (String name : names) {
                    try {
                        Field field = clazz.getDeclaredField(name);
                        field.setAccessible(true);
                        System.out.println("DEBUG: Found field '" + name + "' in class " + clazz.getSimpleName());
                        return field;
                    } catch (NoSuchFieldException e) {
                        continue;
                    }
                }
                clazz = clazz.getSuperclass();
            }
            System.out.println("DEBUG: Could not find any field with names: " + java.util.Arrays.toString(names));
        } catch (Exception e) {
            System.out.println("DEBUG: Field search failed: " + e.getMessage());
        }
        return null;
    }

    private Method findMethodByNames(Class<?> clazz, String... names) {
        while (clazz != null) {
            for (String name : names) {
                try {
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.getName().equals(name)) {
                            System.out.println("DEBUG: Found method '" + name + "' in class " + clazz.getSimpleName());
                            return method;
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            clazz = clazz.getSuperclass();
        }
        System.out.println("DEBUG: Could not find any method with names: " + java.util.Arrays.toString(names));
        return null;
    }

    private void createTimeAccelerationEffects(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        System.out.println("DEBUG: Creating time acceleration effects at " + pos);

        for (int i = 0; i < 150; i++) {
            double t = i / 150.0;
            double angle1 = t * 14 * Math.PI;
            double angle2 = angle1 + Math.PI;
            double height = t * 10.0;
            double radius = 2.5 + Math.sin(t * Math.PI) * 1.5;

            double x1 = pos.getX() + 0.5 + Math.cos(angle1) * radius;
            double y1 = pos.getY() + 0.5 + height;
            double z1 = pos.getZ() + 0.5 + Math.sin(angle1) * radius;

            double x2 = pos.getX() + 0.5 + Math.cos(angle2) * radius;
            double y2 = pos.getY() + 0.5 + height;
            double z2 = pos.getZ() + 0.5 + Math.sin(angle2) * radius;

            level.sendParticles(ParticleTypes.ENCHANT, x1, y1, z1, 1, 0, 0.1, 0, 0.1);
            level.sendParticles(ParticleTypes.PORTAL, x2, y2, z2, 1, 0, 0.1, 0, 0.1);

            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.WITCH, x1, y1, z1, 1, 0, 0.2, 0, 0.12);
                level.sendParticles(ParticleTypes.DRAGON_BREATH, x2, y2, z2, 1, 0, 0.2, 0, 0.12);
            }

            if (i % 8 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, x1, y1, z1, 1, 0, 0.15, 0, 0.08);
                level.sendParticles(ParticleTypes.SOUL, x2, y2, z2, 1, 0, 0.15, 0, 0.08);
            }
        }

        for (int ring = 0; ring < 7; ring++) {
            double ringRadius = (ring + 1) * 3.0;
            for (int i = 0; i < 24; i++) {
                double angle = (i / 24.0) * 2 * Math.PI;
                double x = pos.getX() + 0.5 + Math.cos(angle) * ringRadius;
                double y = pos.getY() + 0.5 + ring * 1.0;
                double z = pos.getZ() + 0.5 + Math.sin(angle) * ringRadius;

                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0.1, 0, 0.06);

                if (ring == 3) {
                    level.sendParticles(ParticleTypes.CRIT, x, y, z, 1, 0, 0.1, 0, 0.04);
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 1, 0, 0.1, 0, 0.04);
                }
            }
        }

        for (int i = 0; i < 100; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 8;
            double y = pos.getY() + 0.5 + random.nextDouble() * 8;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 8;

            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 1, 0, 0, 0, 0.03);

            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0, 0.1, 0, 0.02);
            }

            if (i % 5 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0, 0.1, 0, 0.04);
            }

            if (i % 7 == 0) {
                level.sendParticles(ParticleTypes.WAX_ON, x, y, z, 1, 0, 0.1, 0, 0.02);
            }
        }

        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.8f, 1.0f);
        level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1.2f, 1.8f);
        level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.5f);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6f, 1.6f);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 0.6f);
        level.playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.3f, 0.4f);

        System.out.println("DEBUG: Time acceleration effects created");
    }
}