package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = "exadditions")
public class EnchantmentEvents {

    // Helper method to check if a block is an ore
    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.COPPER_ORES) ||
                state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES) ||
                state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES) ||
                state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES) ||
                state.getBlock() == Blocks.NETHER_QUARTZ_ORE || state.getBlock() == Blocks.NETHER_GOLD_ORE ||
                state.getBlock() == Blocks.ANCIENT_DEBRIS;
    }

    // VEIN MINE ENCHANTMENT
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getMainHandItem();
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        int veinMineLevel = tool.getEnchantmentLevel(ModEnchantments.VEIN_MINE.get());
        if (veinMineLevel > 0 && isOre(state)) {
            veinMine(level, pos, state.getBlock(), player, tool, new HashSet<>());
        }

        // SMELTING ENCHANTMENT
        int smeltingLevel = tool.getEnchantmentLevel(ModEnchantments.SMELTING.get());
        if (smeltingLevel > 0 && tool.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE) == 0 &&
                tool.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH) == 0) {

            Optional<SmeltingRecipe> recipe = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new net.minecraft.world.item.crafting.SimpleContainer(new ItemStack(state.getBlock())), level);

            if (recipe.isPresent()) {
                ItemStack result = recipe.get().getResultItem(level.registryAccess());
                if (!result.isEmpty()) {
                    // Cancel normal drops and spawn smelted item
                    event.setCanceled(true);
                    level.destroyBlock(pos, false);
                    ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, result.copy());
                    level.addFreshEntity(itemEntity);
                }
            }
        }

        // SIPHON ENCHANTMENT
        int siphonLevel = tool.getEnchantmentLevel(ModEnchantments.SIPHON.get());
        if (siphonLevel > 0) {
            event.setCanceled(true);
            level.destroyBlock(pos, false);

            // Get drops and add directly to inventory
            List<ItemStack> drops = Block.getDrops(state, (ServerLevel) level, pos, null, player, tool);
            for (ItemStack drop : drops) {
                if (!player.getInventory().add(drop)) {
                    // If inventory full, spawn as item
                    ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                    level.addFreshEntity(itemEntity);
                }
            }
        }

        // MASTERY ENCHANTMENT - Track blocks mined
        int masteryLevel = tool.getEnchantmentLevel(ModEnchantments.MASTERY.get());
        if (masteryLevel > 0) {
            CompoundTag tag = tool.getOrCreateTag();
            int blocksMined = tag.getInt("MasteryBlocksMined") + 1;
            tag.putInt("MasteryBlocksMined", blocksMined);

            // Update tooltip display
            updateMasteryTooltip(tool, blocksMined);
        }

        // FARMER ENCHANTMENT
        int farmerLevel = tool.getEnchantmentLevel(ModEnchantments.FARMER.get());
        if (farmerLevel > 0 && state.getBlock() instanceof CropBlock cropBlock) {
            if (!player.isCrouching()) {
                // Check if crop is mature
                if (cropBlock.isMaxAge(state)) {
                    // Find seed item in inventory and replant if available
                    ItemStack seeds = findSeedForCrop(player, cropBlock);
                    if (!seeds.isEmpty()) {
                        level.setBlock(pos, cropBlock.defaultBlockState(), 3);
                        seeds.shrink(1);
                    }
                } else {
                    // Prevent breaking baby plants
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    // Helper method for vein mining
    private static void veinMine(Level level, BlockPos startPos, Block oreType, Player player, ItemStack tool, Set<BlockPos> visited) {
        if (visited.size() > 64) return; // Limit to prevent lag
        if (visited.contains(startPos)) return;

        visited.add(startPos);
        BlockState state = level.getBlockState(startPos);

        if (state.getBlock() != oreType) return;

        // Mine the block
        List<ItemStack> drops = Block.getDrops(state, (ServerLevel) level, startPos, null, player, tool);
        level.destroyBlock(startPos, false);

        for (ItemStack drop : drops) {
            ItemEntity itemEntity = new ItemEntity(level, startPos.getX() + 0.5, startPos.getY() + 0.5, startPos.getZ() + 0.5, drop);
            level.addFreshEntity(itemEntity);
        }

        // Damage tool based on actual mining
        tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));

        // Check all 6 adjacent blocks
        for (BlockPos adjacent : List.of(
                startPos.north(), startPos.south(), startPos.east(),
                startPos.west(), startPos.above(), startPos.below())) {
            if (level.getBlockState(adjacent).getBlock() == oreType) {
                veinMine(level, adjacent, oreType, player, tool, visited);
            }
        }
    }

    private static ItemStack findSeedForCrop(Player player, CropBlock crop) {
        // This is a simplified version - you'd need to map crops to their seeds
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == Items.WHEAT_SEEDS && crop == Blocks.WHEAT) return stack;
            if (stack.getItem() == Items.CARROT && crop == Blocks.CARROTS) return stack;
            if (stack.getItem() == Items.POTATO && crop == Blocks.POTATOES) return stack;
            // Add more crop-seed mappings as needed
        }
        return ItemStack.EMPTY;
    }

    private static void updateMasteryTooltip(ItemStack tool, int blocksMined) {
        CompoundTag display = tool.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag lore = display.getList("Lore", 8);

        // Remove old mastery line
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.getString(i);
            if (line.contains("Mastery")) {
                lore.remove(i);
                break;
            }
        }

        // Add new mastery line
        double bonus = Math.log(blocksMined) / Math.log(10) * 100; // log10(n) * 100%
        String masteryLine = "§6Mastery: " + blocksMined + " mined (+" + String.format("%.1f", bonus) + "% speed)";
        lore.add(net.minecraft.nbt.StringTag.valueOf("{\"text\":\"" + masteryLine + "\"}"));

        display.put("Lore", lore);
    }

    // RENOUNCE ENCHANTMENT - Handled in combat events
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack weapon = player.getMainHandItem();
        Entity target = event.getTarget();

        // RISKY ENCHANTMENT
        int riskyLevel = weapon.getEnchantmentLevel(ModEnchantments.RISKY.get());
        if (riskyLevel > 0 && target instanceof LivingEntity) {
            // This will be handled in LivingDamageEvent for proper damage modification
        }

        // COMBO ENCHANTMENT
        int comboLevel = weapon.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (comboLevel > 0) {
            CompoundTag tag = weapon.getOrCreateTag();
            long currentTime = level.getGameTime();
            long lastKillTime = tag.getLong("ComboLastKill");
            int comboCount = tag.getInt("ComboCount");
            long comboTimeout = 5 * 20 + (comboLevel * 20); // 5 + level seconds in ticks

            if (currentTime - lastKillTime > comboTimeout) {
                comboCount = 0; // Reset combo
            }

            tag.putLong("ComboLastKill", currentTime);
            tag.putInt("ComboCount", comboCount);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();

        if (!(attacker instanceof Player player)) return;
        ItemStack weapon = player.getMainHandItem();

        // RISKY ENCHANTMENT
        int riskyLevel = weapon.getEnchantmentLevel(ModEnchantments.RISKY.get());
        if (riskyLevel > 0) {
            boolean isCritical = player.getAttackStrengthScale(0.5f) > 0.9f &&
                    player.fallDistance > 0.0f && !player.onGround() &&
                    !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS);

            float damage = event.getAmount();
            if (isCritical) {
                damage *= (1.0f + (riskyLevel * 0.1f)); // +10% per level for crits
            } else {
                damage *= (1.0f - (riskyLevel * 0.05f)); // -5% per level for normal
            }
            event.setAmount(damage);
        }

        // HEALTHY ENCHANTMENT
        int healthyLevel = weapon.getEnchantmentLevel(ModEnchantments.HEALTHY.get());
        if (healthyLevel > 0) {
            float currentHealth = player.getHealth();
            float maxHealth = player.getMaxHealth();

            // 1% bonus per HP + 0.5% bonus per HP per level
            float healthBonus = (currentHealth / maxHealth) * (1.0f + (healthyLevel * 0.5f));
            float damage = event.getAmount() * (1.0f + healthBonus);
            event.setAmount(damage);
        }

        // ADRENALINE ENCHANTMENT
        int adrenalineLevel = weapon.getEnchantmentLevel(ModEnchantments.ADRENALINE.get());
        if (adrenalineLevel > 0) {
            float currentHealth = player.getHealth();
            float maxHealth = player.getMaxHealth();
            float lostHealth = maxHealth - currentHealth;

            // 1% bonus per lost HP + 0.5% bonus per lost HP per level
            float adrenalineBonus = (lostHealth / maxHealth) * (1.0f + (adrenalineLevel * 0.5f));
            float damage = event.getAmount() * (1.0f + adrenalineBonus);
            event.setAmount(damage);
        }

        // COMBO ENCHANTMENT DAMAGE
        int comboLevel = weapon.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (comboLevel > 0) {
            CompoundTag tag = weapon.getOrCreateTag();
            int comboCount = tag.getInt("ComboCount");
            if (comboCount > 0) {
                float comboDamage = comboCount * (1.0f + comboLevel * 0.5f); // Damage scales with level
                event.setAmount(event.getAmount() + comboDamage);
            }
        }

        // BREAKING ENCHANTMENT
        int breakingLevel = weapon.getEnchantmentLevel(ModEnchantments.BREAKING.get());
        if (breakingLevel > 0) {
            float baseDamage = event.getAmount() * (1.0f - (breakingLevel * 0.02f)); // -2% per level
            event.setAmount(baseDamage);

            // Apply extra damage to armor
            if (victim instanceof LivingEntity livingVictim) {
                float armorDamage = breakingLevel * 2.0f; // 2x damage per level
                for (ItemStack armorPiece : livingVictim.getArmorSlots()) {
                    if (!armorPiece.isEmpty()) {
                        armorPiece.hurtAndBreak((int)armorDamage, livingVictim,
                                (entity) -> entity.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.CHEST));
                    }
                }
            }
        }

        // LIFESTEAL ENCHANTMENT (only works on players)
        int lifestealLevel = weapon.getEnchantmentLevel(ModEnchantments.LIFESTEAL.get());
        if (lifestealLevel > 0 && victim instanceof Player) {
            float healAmount = event.getAmount() * (0.1f + (lifestealLevel - 1) * 0.05f); // 10% + 5% per level
            player.heal(healAmount);
        }

        // FROST ENCHANTMENT (applied when arrow hits, handled in projectile events)
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();

        if (!(attacker instanceof Player player)) return;
        if (victim instanceof Player) return; // Don't count player kills

        ItemStack weapon = player.getMainHandItem();

        // EXTRACT ENCHANTMENT
        int extractLevel = weapon.getEnchantmentLevel(ModEnchantments.EXTRACT.get());
        if (extractLevel > 0) {
            // Give 5% more exp per level
            float expMultiplier = 1.0f + (extractLevel * 0.05f);
            // This would need to be handled in the experience drop event
        }

        // COMBO ENCHANTMENT
        int comboLevel = weapon.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (comboLevel > 0) {
            CompoundTag tag = weapon.getOrCreateTag();
            int comboCount = tag.getInt("ComboCount") + 1;
            tag.putInt("ComboCount", comboCount);
            tag.putLong("ComboLastKill", victim.level().getGameTime());

            // Visual effect for combo
            if (!victim.level().isClientSide) {
                ((ServerLevel) victim.level()).sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + 1.0, victim.getZ(),
                        comboCount * 2, 0.3, 0.3, 0.3, 0.1);
            }
        }

        // CHANCE ENCHANTMENT (handled in arrow impact events)
    }

    // LAVA WALKER ENCHANTMENT
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        Player player = event.player;
        ItemStack boots = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);

        int lavaWalkerLevel = boots.getEnchantmentLevel(ModEnchantments.LAVA_WALKER.get());
        if (lavaWalkerLevel > 0) {
            int radius = lavaWalkerLevel;
            BlockPos playerPos = player.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(
                    playerPos.offset(-radius, -1, -radius),
                    playerPos.offset(radius, -1, radius))) {

                BlockState state = player.level().getBlockState(pos);
                if (state.getBlock() == Blocks.LAVA) {
                    player.level().setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);

                    // Schedule conversion back to lava
                    player.level().scheduleTick(pos, Blocks.MAGMA_BLOCK, 100 + player.level().random.nextInt(100));
                }
            }

            // Magma damage immunity
            if (player.level().getBlockState(playerPos).getBlock() == Blocks.MAGMA_BLOCK ||
                    player.level().getBlockState(playerPos.below()).getBlock() == Blocks.MAGMA_BLOCK) {
                // Cancel magma damage (would need to be in damage event)
            }
        }

        // MAGNETISM ENCHANTMENT
        ItemStack helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        int magnetismLevel = helmet.getEnchantmentLevel(ModEnchantments.MAGNETISM.get());
        if (magnetismLevel > 0) {
            double pullRadius = 3.0 + magnetismLevel * 2.0; // 5, 7, 9 block radius
            double pullStrength = 0.1 * magnetismLevel; // Stronger pull per level

            List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class,
                    new AABB(player.getX() - pullRadius, player.getY() - pullRadius, player.getZ() - pullRadius,
                            player.getX() + pullRadius, player.getY() + pullRadius, player.getZ() + pullRadius));

            for (ItemEntity item : items) {
                if (item.getAge() < 10) continue; // Don't pull very new items

                Vec3 direction = player.position().subtract(item.position()).normalize();
                Vec3 velocity = direction.scale(pullStrength);
                item.setDeltaMovement(item.getDeltaMovement().add(velocity));
            }
        }

        // SPRINT ENCHANTMENT
        ItemStack sprintBoots = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        int sprintLevel = sprintBoots.getEnchantmentLevel(ModEnchantments.SPRINT.get());
        if (sprintLevel > 0 && player.isSprinting()) {
            // Apply speed boost (this would need attribute modification)
            float speedBonus = sprintLevel * 0.05f; // 5% per level
            // Implementation would require attribute modifiers
        }

        // MARATHON ENCHANTMENT
        ItemStack legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);

        int marathonLegs = legs.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        int marathonFeet = feet.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        int marathonLevel = Math.max(marathonLegs, marathonFeet);

        if (marathonLevel > 0 && (player.isSprinting() || player.isMoving())) {
            // Reduce hunger drain by 10% per level
            // This would need to be implemented in the hunger system
        }
    }

    // REJUVENATE ENCHANTMENT
    @SubscribeEvent
    public static void onItemDurabilityChange(net.minecraftforge.event.entity.player.PlayerEvent.ItemDurabilityChangeEvent event) {
        ItemStack item = event.getItem();
        int rejuvenateLevel = item.getEnchantmentLevel(ModEnchantments.REJUVENATE.get());

        if (rejuvenateLevel > 0) {
            Random random = new Random();
            int chance = 1000 / rejuvenateLevel; // 1/(1000/level) chance

            if (random.nextInt(chance) == 0) {
                // Increase max durability
                CompoundTag tag = item.getOrCreateTag();
                int bonusDurability = tag.getInt("RejuvenateBonusDurability") + rejuvenateLevel;
                tag.putInt("RejuvenateBonusDurability", bonusDurability);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Check all armor pieces for rejuvenate
        for (ItemStack armor : player.getArmorSlots()) {
            int rejuvenateLevel = armor.getEnchantmentLevel(ModEnchantments.REJUVENATE.get());
            if (rejuvenateLevel > 0) {
                Random random = new Random();
                int chance = 2000 / rejuvenateLevel; // 1/(2000/level) chance per HP regenerated

                if (random.nextInt(chance) == 0) {
                    // Repair armor
                    armor.setDamageValue(Math.max(0, armor.getDamageValue() - rejuvenateLevel));
                }
            }
        }
    }

    // HOMING ENCHANTMENT (for arrows)
    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player)) return;

        ItemStack bow = player.getMainHandItem();
        int homingLevel = bow.getEnchantmentLevel(ModEnchantments.HOMING.get());

        if (homingLevel > 0) {
            // Find closest enemy to player's crosshair
            LivingEntity target = findClosestEntityInCrosshair(player, 32.0);
            if (target != null) {
                // Store target info in arrow NBT
                CompoundTag arrowTag = arrow.getPersistentData();
                arrowTag.putUUID("HomingTarget", target.getUUID());
                arrowTag.putBoolean("IsHoming", true);
            }
        }

        // CHANCE ENCHANTMENT (looting for bows)
        int chanceLevel = bow.getEnchantmentLevel(ModEnchantments.CHANCE.get());
        if (chanceLevel > 0) {
            CompoundTag arrowTag = arrow.getPersistentData();
            arrowTag.putInt("ChanceLevel", chanceLevel);
        }

        // FROST ENCHANTMENT
        int frostLevel = bow.getEnchantmentLevel(ModEnchantments.FROST.get());
        if (frostLevel > 0) {
            CompoundTag arrowTag = arrow.getPersistentData();
            arrowTag.putInt("FrostLevel", frostLevel);
        }
    }

    // Helper method to find closest entity in crosshair direction
    private static LivingEntity findClosestEntityInCrosshair(Player player, double maxDistance) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endVec = eyePos.add(lookVec.scale(maxDistance));

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(eyePos, endVec).inflate(2.0),
                entity -> entity != player && !entity.isAlliedTo(player));

        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        double closestAngle = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            Vec3 toEntity = entity.position().subtract(eyePos).normalize();
            double angle = Math.acos(lookVec.dot(toEntity));
            double distance = eyePos.distanceTo(entity.position());

            // Prefer entities closer to crosshair (smaller angle)
            if (angle < Math.PI / 4 && angle < closestAngle) { // Within 45 degrees
                closest = entity;
                closestAngle = angle;
                closestDistance = distance;
            }
        }

        return closest;
    }

    // DRAW ENCHANTMENT (faster bow drawing)
    @SubscribeEvent
    public static void onPlayerTick2(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        Player player = event.player;
        if (!player.isUsingItem()) return;

        ItemStack item = player.getUseItem();
        int drawLevel = item.getEnchantmentLevel(ModEnchantments.DRAW.get());

        if (drawLevel > 0 && item.getItem() instanceof net.minecraft.world.item.BowItem) {
            // Simulate faster drawing by reducing use time needed
            // This is a simplified approach - full implementation would need mixin or reflection
            CompoundTag playerData = player.getPersistentData();
            int useTime = player.getTicksUsingItem();

            // Reduce effective use time by 7% per level
            float speedMultiplier = 1.0f + (drawLevel * 0.07f);
            int effectiveUseTime = (int)(useTime * speedMultiplier);

            // Store the modified time for bow mechanics
            playerData.putInt("DrawEnchantEffectiveTime", effectiveUseTime);
        }
    }

    // FARMER ENCHANTMENT additional logic for crop yield bonus would go here
    // This requires hooking into the loot table generation which is more complex

    // Helper method to handle mastery speed calculation
    public static float getMasterySpeedBonus(ItemStack tool) {
        int masteryLevel = tool.getEnchantmentLevel(ModEnchantments.MASTERY.get());
        if (masteryLevel == 0) return 1.0f;

        CompoundTag tag = tool.getTag();
        if (tag == null) return 1.0f;

        int blocksMined = tag.getInt("MasteryBlocksMined");
        if (blocksMined < 10) return 1.0f;

        // log_10(n) where n is blocks mined, 10 blocks = 100% boost, 100 blocks = 200% boost
        double bonus = Math.log10(blocksMined);
        return 1.0f + (float)bonus;
    }

    // ALL IN ENCHANTMENT damage multiplier
    public static float getAllInDamageMultiplier(ItemStack weapon) {
        int allInLevel = weapon.getEnchantmentLevel(ModEnchantments.ALL_IN.get());
        return allInLevel > 0 ? 3.0f : 1.0f;
    }

    // RENOUNCE ENCHANTMENT damage and speed modifications
    public static float getRenounceSpeedModifier(ItemStack axe, BlockState state) {
        int renounceLevel = axe.getEnchantmentLevel(ModEnchantments.RENOUNCE.get());
        if (renounceLevel > 0 && state.is(BlockTags.LOGS)) {
            return 1.0f; // Remove speed bonus against logs (normal speed)
        }
        return 1.0f; // No change for non-logs
    }

    public static float getRenounceDamageBonus(ItemStack axe) {
        int renounceLevel = axe.getEnchantmentLevel(ModEnchantments.RENOUNCE.get());
        return renounceLevel > 0 ? 5.0f : 0.0f; // Flat +5 damage
    }
}