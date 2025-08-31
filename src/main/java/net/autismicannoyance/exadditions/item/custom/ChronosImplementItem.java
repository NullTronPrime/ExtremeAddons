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
    private static final int RADIUS = 5;
    private static final int MIN_TIME_TICKS = 2400;
    private static final int MAX_TIME_TICKS = 36000;

    public ChronosImplementItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return performAOEAcceleration(context.getLevel(), context.getClickedPos(), context.getPlayer(), context.getItemInHand(), context.getHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        InteractionResult result = performAOEAcceleration(level, player.blockPosition(), player, stack, hand);
        return result == InteractionResult.SUCCESS ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
    }

    private InteractionResult performAOEAcceleration(Level level, BlockPos centerPos, Player player, ItemStack stack, InteractionHand hand) {
        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }
        RandomSource random = level.getRandom();
        int accelerationTicks = MIN_TIME_TICKS + random.nextInt(MAX_TIME_TICKS - MIN_TIME_TICKS + 1);
        int affectedCount = 0;
        int totalTicksAccelerated = 0;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    BlockPos pos = centerPos.offset(x, y, z);
                    if (centerPos.distSqr(pos) <= RADIUS * RADIUS) {
                        boolean affected = false;
                        BlockEntity blockEntity = level.getBlockEntity(pos);
                        if (blockEntity != null) {
                            affected = accelerateBlockEntity(level, pos, blockEntity, accelerationTicks);
                        }
                        if (!affected) {
                            affected = accelerateBlock(level, pos, accelerationTicks);
                        }
                        if (affected) {
                            affectedCount++;
                            totalTicksAccelerated += accelerationTicks;
                            createHelixEffects((ServerLevel) level, pos);
                        }
                    }
                }
            }
        }
        AABB searchBox = new AABB(centerPos).inflate(RADIUS);
        List<Entity> entities = level.getEntities(player, searchBox, entity -> !(entity instanceof Player) && entity.distanceToSqr(centerPos.getX(), centerPos.getY(), centerPos.getZ()) <= RADIUS * RADIUS);
        for (Entity entity : entities) {
            boolean entityChanged = false;
            if (entity instanceof AgeableMob ageable) {
                entityChanged = accelerateAnimal(ageable, accelerationTicks);
            }
            if (entity instanceof Villager villager) {
                entityChanged = accelerateVillager(villager, accelerationTicks) || entityChanged;
            }
            if (entityChanged) {
                affectedCount++;
                totalTicksAccelerated += accelerationTicks;
                createHelixEffects((ServerLevel) level, entity.blockPosition());
            }
        }
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, searchBox, p -> p != player && p.distanceToSqr(centerPos.getX(), centerPos.getY(), centerPos.getZ()) <= RADIUS * RADIUS);
        for (Player nearbyPlayer : nearbyPlayers) {
            boolean affectedPlayer = acceleratePlayerCooldowns(nearbyPlayer, accelerationTicks);
            if (affectedPlayer) {
                affectedCount++;
                totalTicksAccelerated += accelerationTicks;
                createHelixEffects((ServerLevel) level, nearbyPlayer.blockPosition());
            }
        }
        if (affectedCount > 0) {
            int cooldownTicks = (int) (5 * Math.log(totalTicksAccelerated) * 20);
            player.getCooldowns().addCooldown(this, cooldownTicks);
            int minutes = accelerationTicks / 1200;
            player.sendSystemMessage(Component.literal("Time-accelerated " + affectedCount + " targets by " + minutes + " minutes in " + RADIUS + " block radius"));
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            return InteractionResult.SUCCESS;
        }
        player.getCooldowns().addCooldown(this, 200);
        player.sendSystemMessage(Component.literal("Nothing to accelerate in area"));
        return InteractionResult.PASS;
    }

    private boolean acceleratePlayerCooldowns(Player player, int ticks) {
        try {
            Field cooldownsField = findFieldByNames(player, "cooldowns", "f_36175_");
            if (cooldownsField != null) {
                Object cooldowns = cooldownsField.get(player);
                if (cooldowns != null) {
                    Field cooldownMapField = findFieldByNames(cooldowns, "cooldowns", "f_36357_");
                    if (cooldownMapField != null) {
                        Object cooldownMap = cooldownMapField.get(cooldowns);
                        if (cooldownMap instanceof java.util.Map<?, ?> map) {
                            boolean modified = false;
                            for (Object entry : map.entrySet()) {
                                if (entry instanceof java.util.Map.Entry<?, ?> mapEntry) {
                                    Object value = mapEntry.getValue();
                                    if (value != null) {
                                        Field endTimeField = findFieldByNames(value, "endTime", "f_36365_");
                                        if (endTimeField != null && endTimeField.getType() == int.class) {
                                            int currentEndTime = endTimeField.getInt(value);
                                            if (currentEndTime > 0) {
                                                int newEndTime = Math.max(0, currentEndTime - ticks);
                                                endTimeField.setInt(value, newEndTime);
                                                modified = true;
                                            }
                                        }
                                    }
                                }
                            }
                            return modified;
                        }
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private boolean accelerateVillager(Villager villager, int ticks) {
        boolean changed = false;
        try {
            Level level = villager.level();
            Class<?> villagerClass = villager.getClass();
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
                                changed = true;
                            } else if (field.getType() == int.class) {
                                int current = field.getInt(villager);
                                if (fieldName.contains("restock") || fieldName.contains("count") ||
                                        fieldName.equals("numberOfRestocksToday") || fieldName.equals("f_35461_")) {
                                    field.setInt(villager, 0);
                                } else if (fieldName.contains("cooldown") || fieldName.contains("delay")) {
                                    int newValue = Math.max(0, current - ticks);
                                    field.setInt(villager, newValue);
                                } else if (current > 0) {
                                    int newValue = Math.max(0, current - ticks);
                                    field.setInt(villager, newValue);
                                }
                                changed = true;
                            }
                        } catch (Exception e) {
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            if (changed || true) {
                villager.restock();
                villager.refreshBrain((ServerLevel) level);
                changed = true;
            }
        } catch (Exception e) {
        }
        return changed;
    }

    private boolean accelerateBlockEntity(Level level, BlockPos pos, BlockEntity blockEntity, int ticks) {
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            boolean result = accelerateFurnaceDirectly(furnace, ticks);
            if (!result) {
                result = simulateFurnaceTicks(furnace, Math.min(ticks, 200));
                if (!result) {
                    ItemStack input = furnace.getItem(0);
                    ItemStack fuel = furnace.getItem(1);
                    if (!input.isEmpty() || !fuel.isEmpty()) {
                        furnace.setChanged();
                        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
                        return true;
                    }
                }
            }
            return result;
        }
        if (blockEntity instanceof BrewingStandBlockEntity brewingStand) {
            return accelerateBrewingStand(brewingStand, ticks);
        }
        if (blockEntity instanceof CampfireBlockEntity campfire) {
            return accelerateCampfire(campfire, ticks);
        }
        if (blockEntity instanceof HopperBlockEntity hopper) {
            return accelerateHopper(hopper, ticks);
        }
        if (blockEntity instanceof BeaconBlockEntity beacon) {
            return accelerateBeacon(beacon, ticks);
        }
        if (blockEntity instanceof SculkSensorBlockEntity sculkSensor) {
            return accelerateSculkSensor(sculkSensor, ticks);
        }
        if (blockEntity instanceof SculkShriekerBlockEntity sculkShrieker) {
            return accelerateSculkShrieker(sculkShrieker, ticks);
        }
        return accelerateWithReflection(blockEntity, level, pos, level.getBlockState(pos), ticks);
    }

    private boolean accelerateFurnaceDirectly(AbstractFurnaceBlockEntity furnace, int accelerationTicks) {
        Level level = furnace.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        try {
            ItemStack inputItem = furnace.getItem(0);
            ItemStack fuelItem = furnace.getItem(1);
            if (inputItem.isEmpty() && fuelItem.isEmpty()) {
                return false;
            }
            Field litTimeField = findFieldByNames(furnace, "litTime", "f_58387_", "burnTime");
            Field litDurationField = findFieldByNames(furnace, "litDuration", "f_58388_", "totalBurnTime");
            Field cookingProgressField = findFieldByNames(furnace, "cookingProgress", "f_58389_", "cookTime");
            Field cookingTotalTimeField = findFieldByNames(furnace, "cookingTotalTime", "f_58390_", "totalCookTime");
            if (litTimeField == null || cookingProgressField == null) {
                return false;
            }
            boolean hasChanges = false;
            int remainingTicks = accelerationTicks;
            int cyclesProcessed = 0;
            int totalFuelUsed = 0;
            while (remainingTicks >= 400) {
                cyclesProcessed++;
                inputItem = furnace.getItem(0);
                fuelItem = furnace.getItem(1);
                if (inputItem.isEmpty()) {
                    break;
                }
                if (!canSmeltItem(furnace, inputItem, level)) {
                    break;
                }
                int litTime = litTimeField.getInt(furnace);
                int cookingProgress = cookingProgressField.getInt(furnace);
                int cookingTotalTime = cookingTotalTimeField != null ? cookingTotalTimeField.getInt(furnace) : 200;
                int recipeTime = getRecipeCookTime(furnace, inputItem, level);
                if (cookingTotalTimeField != null) {
                    cookingTotalTimeField.setInt(furnace, recipeTime);
                    cookingTotalTime = recipeTime;
                }
                int ticksNeededToComplete = cookingTotalTime - cookingProgress;
                if (litTime <= 0) {
                    if (fuelItem.isEmpty()) {
                        break;
                    }
                    int fuelBurnTime = getBurnTime(fuelItem);
                    if (fuelBurnTime <= 0) {
                        break;
                    }
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
                int maxAdvance = Math.min(remainingTicks, Math.min(ticksNeededToComplete, totalAvailableFuel));
                if (maxAdvance <= 0) {
                    break;
                }
                cookingProgressField.setInt(furnace, cookingProgress + maxAdvance);
                int fuelToConsume = maxAdvance;
                if (fuelToConsume > 0 && litTime > 0) {
                    int consumeFromLit = Math.min(fuelToConsume, litTime);
                    litTimeField.setInt(furnace, litTime - consumeFromLit);
                    fuelToConsume -= consumeFromLit;
                    litTime -= consumeFromLit;
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
                }
                cookingProgress += maxAdvance;
                remainingTicks -= maxAdvance;
                hasChanges = true;
                if (cookingProgress >= cookingTotalTime) {
                    if (completeSmeltingProcess(furnace, inputItem, level)) {
                        cookingProgressField.setInt(furnace, 0);
                        if (cookingTotalTimeField != null) {
                            cookingTotalTimeField.setInt(furnace, 200);
                        }
                        continue;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            if (hasChanges) {
                furnace.setChanged();
                level.sendBlockUpdated(furnace.getBlockPos(), level.getBlockState(furnace.getBlockPos()),
                        level.getBlockState(furnace.getBlockPos()), Block.UPDATE_ALL);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private boolean hasSufficientFuelForItems(AbstractFurnaceBlockEntity furnace, int ticksNeeded) {
        try {
            Field litTimeField = findFieldByNames(furnace, "litTime", "f_58387_", "burnTime");
            if (litTimeField == null) {
                return false;
            }
            int currentLitTime = litTimeField.getInt(furnace);
            int totalFuelTicks = currentLitTime;
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
                    return true;
                }
            }
            return totalFuelTicks >= ticksNeeded;
        } catch (Exception e) {
            return false;
        }
    }

    private int calculateMaxSmeltableItems(AbstractFurnaceBlockEntity furnace, Level level) {
        try {
            Field litTimeField = findFieldByNames(furnace, "litTime", "f_58387_", "burnTime");
            if (litTimeField == null) {
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
                return 0;
            }
            int recipeTime = getRecipeCookTime(furnace, inputItem, level);
            if (recipeTime <= 0) {
                return 0;
            }
            int maxItems = totalFuelTicks / recipeTime;
            return maxItems;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean simulateFurnaceTicks(AbstractFurnaceBlockEntity furnace, int ticks) {
        try {
            Level level = furnace.getLevel();
            BlockPos pos = furnace.getBlockPos();
            BlockState state = level.getBlockState(pos);
            Method serverTickMethod = findMethodByNames(furnace.getClass(),
                    "serverTick", "m_155014_", "tick");
            if (serverTickMethod != null) {
                serverTickMethod.setAccessible(true);
                int safeTicks = Math.min(ticks, 100);
                for (int i = 0; i < safeTicks; i++) {
                    try {
                        if (serverTickMethod.getParameterCount() == 4) {
                            serverTickMethod.invoke(null, level, pos, state, furnace);
                        } else if (serverTickMethod.getParameterCount() == 0) {
                            serverTickMethod.invoke(furnace);
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
                furnace.setChanged();
                return true;
            }
            ItemStack input = furnace.getItem(0);
            ItemStack fuel = furnace.getItem(1);
            if (!input.isEmpty() || !fuel.isEmpty()) {
                furnace.setChanged();
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private int getRecipeCookTime(AbstractFurnaceBlockEntity furnace, ItemStack item, Level level) {
        try {
            RecipeManager recipeManager = level.getRecipeManager();
            Container container = furnace;
            String className = furnace.getClass().getSimpleName().toLowerCase();
            if (className.contains("blastfurnace")) {
                Optional<BlastingRecipe> recipe = recipeManager.getRecipeFor(RecipeType.BLASTING, container, level);
                if (recipe.isPresent()) {
                    int cookTime = recipe.get().getCookingTime();
                    return cookTime;
                } else {
                    return 100;
                }
            } else if (className.contains("smoker")) {
                Optional<SmokingRecipe> recipe = recipeManager.getRecipeFor(RecipeType.SMOKING, container, level);
                if (recipe.isPresent()) {
                    int cookTime = recipe.get().getCookingTime();
                    return cookTime;
                } else {
                    return 100;
                }
            } else {
                Optional<SmeltingRecipe> recipe = recipeManager.getRecipeFor(RecipeType.SMELTING, container, level);
                if (recipe.isPresent()) {
                    int cookTime = recipe.get().getCookingTime();
                    return cookTime;
                } else {
                    return 200;
                }
            }
        } catch (Exception e) {
            String className = furnace.getClass().getSimpleName().toLowerCase();
            if (className.contains("blastfurnace") || className.contains("smoker")) {
                return 100;
            }
            return 200;
        }
    }

    private int getBurnTime(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        }
        try {
            int burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(fuel, null);
            if (burnTime > 0) {
                return burnTime;
            }
        } catch (Exception e) {
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
        return fallbackValue;
    }

    private boolean canSmeltItem(AbstractFurnaceBlockEntity furnace, ItemStack item, Level level) {
        if (item.isEmpty()) {
            return false;
        }
        try {
            RecipeManager recipeManager = level.getRecipeManager();
            Container container = furnace;
            String className = furnace.getClass().getSimpleName().toLowerCase();
            boolean hasRecipe = false;
            if (className.contains("blastfurnace")) {
                hasRecipe = recipeManager.getRecipeFor(RecipeType.BLASTING, container, level).isPresent();
            } else if (className.contains("smoker")) {
                hasRecipe = recipeManager.getRecipeFor(RecipeType.SMOKING, container, level).isPresent();
            } else {
                hasRecipe = recipeManager.getRecipeFor(RecipeType.SMELTING, container, level).isPresent();
            }
            return hasRecipe;
        } catch (Exception e) {
            String itemName = item.getItem().toString().toLowerCase();
            boolean canSmelt = itemName.contains("ore") || itemName.contains("raw_") ||
                    itemName.contains("food") || itemName.contains("meat") ||
                    itemName.contains("fish") || itemName.contains("potato") ||
                    itemName.contains("log") || itemName.contains("sand");
            return canSmelt;
        }
    }

    private boolean completeSmeltingProcess(AbstractFurnaceBlockEntity furnace, ItemStack inputItem, Level level) {
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
                }
            } else if (className.contains("smoker")) {
                Optional<SmokingRecipe> smokingRecipe = recipeManager.getRecipeFor(RecipeType.SMOKING, container, level);
                if (smokingRecipe.isPresent()) {
                    recipe = smokingRecipe.get();
                    result = recipe.getResultItem(level.registryAccess()).copy();
                }
            } else {
                Optional<SmeltingRecipe> smeltingRecipe = recipeManager.getRecipeFor(RecipeType.SMELTING, container, level);
                if (smeltingRecipe.isPresent()) {
                    recipe = smeltingRecipe.get();
                    result = recipe.getResultItem(level.registryAccess()).copy();
                }
            }
            if (result.isEmpty() || recipe == null) {
                return false;
            }
            ItemStack outputSlot = furnace.getItem(2);
            if (outputSlot.isEmpty()) {
                furnace.setItem(2, result);
            } else if (ItemStack.isSameItemSameTags(outputSlot, result)) {
                int maxStack = Math.min(outputSlot.getMaxStackSize(), 64);
                if (outputSlot.getCount() + result.getCount() <= maxStack) {
                    outputSlot.grow(result.getCount());
                } else {
                    return false;
                }
            } else {
                return false;
            }
            float experience = recipe.getExperience();
            if (experience > 0) {
                boolean usedActualExp = false;
                try {
                    Field recipesUsedField = findFieldByNames(furnace, "recipesUsed", "f_58391_");
                    if (recipesUsedField != null) {
                        recipesUsedField.setAccessible(true);
                        Object recipesUsedObj = recipesUsedField.get(furnace);
                        if (recipesUsedObj != null) {
                            try {
                                Method addToMethod = recipesUsedObj.getClass().getMethod("addTo", Object.class, int.class);
                                addToMethod.invoke(recipesUsedObj, recipe.getId(), 1);
                                usedActualExp = true;
                            } catch (Exception e) {
                                try {
                                    Method putMethod = recipesUsedObj.getClass().getMethod("put", Object.class, Object.class);
                                    Method getMethod = recipesUsedObj.getClass().getMethod("getOrDefault", Object.class, Object.class);
                                    Object currentCount = getMethod.invoke(recipesUsedObj, recipe.getId(), 0);
                                    if (currentCount instanceof Integer) {
                                        putMethod.invoke(recipesUsedObj, recipe.getId(), ((Integer) currentCount) + 1);
                                        usedActualExp = true;
                                    }
                                } catch (Exception e2) {
                                    awardDirectExperience(furnace, recipe, level);
                                }
                            }
                        } else {
                            awardDirectExperience(furnace, recipe, level);
                        }
                    } else {
                        awardDirectExperience(furnace, recipe, level);
                    }
                } catch (Exception e) {
                    awardDirectExperience(furnace, recipe, level);
                }
                if (!usedActualExp) {
                }
            }
            inputItem.shrink(1);
            if (inputItem.isEmpty()) {
                furnace.setItem(0, ItemStack.EMPTY);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void awardDirectExperience(AbstractFurnaceBlockEntity furnace, AbstractCookingRecipe recipe, Level level) {
        try {
            float experience = recipe.getExperience();
            if (experience <= 0) {
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
                }
            }
        } catch (Exception e) {
        }
    }

    private boolean accelerateBrewingStand(BrewingStandBlockEntity brewingStand, int ticks) {
        Level level = brewingStand.getLevel();
        BlockPos pos = brewingStand.getBlockPos();
        try {
            int remainingTicks = ticks;
            boolean anyBrewing = false;
            Field brewTimeField = findFieldByNames(brewingStand, "brewTime", "f_59123_");
            Field fuelField = findFieldByNames(brewingStand, "fuel", "f_59124_");
            if (brewTimeField != null && brewTimeField.getType() == int.class) {
                int cyclesCompleted = 0;
                while (remainingTicks >= 400 && isBrewingStandActive(brewingStand)) {
                    int currentBrewTime = brewTimeField.getInt(brewingStand);
                    if (currentBrewTime > 0) {
                        int newTime = Math.max(0, currentBrewTime - 400);
                        brewTimeField.setInt(brewingStand, newTime);
                        if (newTime == 0) {
                            tryCompleteBrewingCycle(brewingStand);
                            cyclesCompleted++;
                        }
                        anyBrewing = true;
                        remainingTicks -= 400;
                    } else {
                        break;
                    }
                }
            }
            if (anyBrewing) {
                brewingStand.setChanged();
                if (level != null) level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
                return true;
            }
        } catch (Exception e) {
            return accelerateWithReflection(brewingStand, level, pos, level.getBlockState(pos), ticks);
        }
        return false;
    }

    private void tryCompleteBrewingCycle(BrewingStandBlockEntity brewingStand) {
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
                            foundMethod = true;
                            break;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private boolean isBrewingStandActive(BrewingStandBlockEntity brewingStand) {
        try {
            Field brewTimeField = findFieldByNames(brewingStand, "brewTime", "f_59123_");
            if (brewTimeField != null && brewTimeField.getType() == int.class) {
                int brewTime = brewTimeField.getInt(brewingStand);
                return brewTime > 0;
            }
            boolean hasItems = !brewingStand.getItem(0).isEmpty() || !brewingStand.getItem(1).isEmpty() ||
                    !brewingStand.getItem(2).isEmpty() || !brewingStand.getItem(3).isEmpty();
            return hasItems;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean accelerateCampfire(CampfireBlockEntity campfire, int ticks) {
        try {
            Field cookingProgressField = findFieldByNames(campfire, "cookingProgress", "f_59042_");
            Field cookingTimeField = findFieldByNames(campfire, "cookingTime", "f_59043_");
            if (cookingProgressField != null && cookingTimeField != null) {
                Object progressArray = cookingProgressField.get(campfire);
                Object timeArray = cookingTimeField.get(campfire);
                if (progressArray instanceof int[] progressArr && timeArray instanceof int[] timesArr) {
                    boolean anyChanges = false;
                    int slotsProcessed = 0;
                    for (int slot = 0; slot < 4; slot++) {
                        if (hasItemInSlot(campfire, slot) && timesArr[slot] > 0) {
                            int oldProgress = progressArr[slot];
                            progressArr[slot] = Math.min(600, progressArr[slot] + ticks);
                            if (progressArr[slot] >= 600) {
                                timesArr[slot] = 0;
                                progressArr[slot] = 0;
                            }
                            anyChanges = true;
                            slotsProcessed++;
                        }
                    }
                    if (anyChanges) {
                        campfire.setChanged();
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            return accelerateWithReflection(campfire, campfire.getLevel(), campfire.getBlockPos(),
                    campfire.getLevel().getBlockState(campfire.getBlockPos()), ticks);
        }
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
                        return hasItem;
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean accelerateHopper(HopperBlockEntity hopper, int ticks) {
        try {
            Field cooldownField = findFieldByNames(hopper, "cooldownTime", "f_59309_");
            if (cooldownField != null && cooldownField.getType() == int.class) {
                int oldCooldown = cooldownField.getInt(hopper);
                cooldownField.setInt(hopper, 0);
            }
            int transfersToPerform = ticks / 8;
            boolean anyTransfers = false;
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
                    break;
                }
            }
            if (anyTransfers) {
                hopper.setChanged();
                return true;
            }
        } catch (Exception e) {
            return accelerateWithReflection(hopper, hopper.getLevel(), hopper.getBlockPos(),
                    hopper.getLevel().getBlockState(hopper.getBlockPos()), ticks);
        }
        return false;
    }

    private boolean accelerateBeacon(BeaconBlockEntity beacon, int ticks) {
        try {
            Level level = beacon.getLevel();
            BlockPos pos = beacon.getBlockPos();
            int updates = ticks / 80;
            for (int i = 0; i < Math.min(updates, 210); i++) {
                beacon.setChanged();
                level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 1);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean accelerateSculkSensor(SculkSensorBlockEntity sculkSensor, int ticks) {
        try {
            Field cooldownField = findFieldByNames(sculkSensor, "cooldownTicks", "f_222679_");
            if (cooldownField != null) {
                if (cooldownField.getType() == int.class) {
                    int current = cooldownField.getInt(sculkSensor);
                    if (current > 0) {
                        int newValue = Math.max(0, current - ticks);
                        cooldownField.setInt(sculkSensor, newValue);
                        sculkSensor.setChanged();
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Level sensorLevel = sculkSensor.getLevel();
            BlockPos sensorPos = sculkSensor.getBlockPos();
            return accelerateWithReflection(sculkSensor, sensorLevel, sensorPos,
                    sensorLevel.getBlockState(sensorPos), ticks);
        }
        return false;
    }

    private boolean accelerateSculkShrieker(SculkShriekerBlockEntity sculkShrieker, int ticks) {
        try {
            Field warningField = findFieldByNames(sculkShrieker, "warningLevel", "f_222858_");
            if (warningField != null) {
                if (warningField.getType() == int.class) {
                    int current = warningField.getInt(sculkShrieker);
                    if (current > 0) {
                        int newValue = Math.max(0, current - ticks);
                        warningField.setInt(sculkShrieker, newValue);
                        sculkShrieker.setChanged();
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Level shriekerLevel = sculkShrieker.getLevel();
            BlockPos shriekerPos = sculkShrieker.getBlockPos();
            return accelerateWithReflection(sculkShrieker, shriekerLevel, shriekerPos,
                    shriekerLevel.getBlockState(shriekerPos), ticks);
        }
        return false;
    }

    private boolean accelerateWithReflection(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int ticks) {
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
                                    } else {
                                        int newValue = Math.max(0, current - ticks);
                                        field.setInt(blockEntity, newValue);
                                    }
                                    changed = true;
                                    fieldsModified++;
                                }
                            } else {
                                long current = field.getLong(blockEntity);
                                if (current > 0) {
                                    long newValue = Math.max(0, current - ticks);
                                    field.setLong(blockEntity, newValue);
                                    changed = true;
                                    fieldsModified++;
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            if (changed) {
                blockEntity.setChanged();
                return true;
            }
            Method[] methods = blockEntity.getClass().getDeclaredMethods();
            boolean foundTickMethod = false;
            for (Method method : methods) {
                String name = method.getName();
                if ((name.equals("tick") || name.contains("Tick") || name.equals("m_6596_")) &&
                        method.getParameterCount() <= 4) {
                    method.setAccessible(true);
                    foundTickMethod = true;
                    int safeTicks = Math.min(ticks, 200);
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
                            break;
                        }
                    }
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean accelerateBlock(Level level, BlockPos pos, int ticks) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        RandomSource random = level.getRandom();
        boolean changed = false;
        if (block instanceof BonemealableBlock bonemealable) {
            int bonemealApplications = Math.min(ticks / 100, 168);
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
                                    break;
                                }
                            }
                        }
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
        if (block.isRandomlyTicking(state)) {
            int randomTicksToSimulate = Math.min(ticks / 600, 28);
            int successfulTicks = 0;
            for (int i = 0; i < randomTicksToSimulate; i++) {
                try {
                    block.randomTick(state, (ServerLevel) level, pos, random);
                    state = level.getBlockState(pos);
                    changed = true;
                    successfulTicks++;
                } catch (Exception e) {
                    break;
                }
            }
        }
        if (!changed) {
            changed = accelerateAgeProperty(level, pos, state, ticks, random);
        }
        return changed;
    }

    private boolean accelerateAgeProperty(Level level, BlockPos pos, BlockState state, int ticks, RandomSource random) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty intProp) {
                String propName = property.getName().toLowerCase();
                if (propName.contains("age") || propName.contains("stage") ||
                        propName.contains("growth") || propName.contains("level") ||
                        propName.contains("distance") || propName.contains("delay") ||
                        propName.contains("power") || propName.contains("charges")) {
                    try {
                        int currentValue = state.getValue(intProp);
                        int maxValue = intProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(currentValue);
                        int minValue = intProp.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(currentValue);
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
                                return true;
                            }
                        }
                        if (propName.contains("delay") || propName.contains("cooldown") || propName.contains("distance")) {
                            if (currentValue > minValue) {
                                int newValue = Math.max(minValue, currentValue - (ticks / 20));
                                if (newValue != currentValue) {
                                    BlockState newState = state.setValue(intProp, newValue);
                                    level.setBlockAndUpdate(pos, newState);
                                    return true;
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }
        return false;
    }

    private boolean accelerateAnimal(AgeableMob animal, int ticks) {
        boolean changed = false;
        if (animal.isBaby()) {
            int currentAge = animal.getAge();
            int newAge = Math.min(0, currentAge + ticks);
            animal.setAge(newAge);
            changed = (currentAge != newAge);
        } else if (animal instanceof Animal animalEntity) {
            int breedingAge = animalEntity.getAge();
            if (breedingAge > 0) {
                int newAge = Math.max(0, breedingAge - ticks);
                animalEntity.setAge(newAge);
                changed = true;
            }
        }
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
                        return field;
                    } catch (NoSuchFieldException e) {
                        continue;
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
        }
        return null;
    }

    private Method findMethodByNames(Class<?> clazz, String... names) {
        while (clazz != null) {
            for (String name : names) {
                try {
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.getName().equals(name)) {
                            return method;
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private void createHelixEffects(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 40; i++) {
            double t = i / 40.0;
            double angle1 = t * 8 * Math.PI;
            double angle2 = angle1 + Math.PI;
            double height = t * 3.0;
            double radius = 1.0;
            double x1 = pos.getX() + 0.5 + Math.cos(angle1) * radius;
            double y1 = pos.getY() + 0.5 + height;
            double z1 = pos.getZ() + 0.5 + Math.sin(angle1) * radius;
            double x2 = pos.getX() + 0.5 + Math.cos(angle2) * radius;
            double y2 = pos.getY() + 0.5 + height;
            double z2 = pos.getZ() + 0.5 + Math.sin(angle2) * radius;
            level.sendParticles(ParticleTypes.ENCHANT, x1, y1, z1, 1, 0, 0.05, 0, 0.02);
            level.sendParticles(ParticleTypes.PORTAL, x2, y2, z2, 1, 0, 0.05, 0, 0.02);
        }
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.3f, 1.5f);
    }
}