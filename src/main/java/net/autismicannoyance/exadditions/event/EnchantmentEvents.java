package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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

    // LIFESTEAL: integrated from LifestealEnchantmentEvents
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) return;

        int lifestealLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.LIFESTEAL.get(), weapon);
        if (lifestealLevel <= 0) return;

        if (player.level().isClientSide) return;

        float damageDealt = event.getAmount();
        float healingAmount = damageDealt * (lifestealLevel * 0.1f); // 10% healing per level

        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(healingAmount);
            // optional particle/sound can be added here
        }
    }

    // VEIN MINE & SMELTING & SIPHON & FARMER & MASTERY & REJUVENATE, etc.
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
                    .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(state.getBlock())), level);

            if (recipe.isPresent()) {
                ItemStack result = recipe.get().getResultItem(level.registryAccess());
                if (!result.isEmpty()) {
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

            List<ItemStack> drops = Block.getDrops(state, (ServerLevel) level, pos, null, player, tool);
            for (ItemStack drop : drops) {
                if (!player.getInventory().add(drop)) {
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
            updateMasteryTooltip(tool, blocksMined);
        }

        // FARMER ENCHANTMENT
        int farmerLevel = tool.getEnchantmentLevel(ModEnchantments.FARMER.get());
        if (farmerLevel > 0 && state.getBlock() instanceof CropBlock) {
            CropBlock cropBlock = (CropBlock) state.getBlock();
            if (!player.isCrouching()) {
                if (cropBlock.isMaxAge(state)) {
                    ItemStack seeds = findSeedForCrop(player, cropBlock);
                    if (!seeds.isEmpty()) {
                        level.setBlock(pos, cropBlock.defaultBlockState(), 3);
                        seeds.shrink(1);
                    }
                } else {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    // Helper method for vein mining
    private static void veinMine(Level level, BlockPos startPos, Block oreType, Player player, ItemStack tool, Set<BlockPos> visited) {
        if (visited.size() > 64) return;
        if (visited.contains(startPos)) return;

        visited.add(startPos);
        BlockState state = level.getBlockState(startPos);

        if (state.getBlock() != oreType) return;

        List<ItemStack> drops = Block.getDrops(state, (ServerLevel) level, startPos, null, player, tool);
        level.destroyBlock(startPos, false);

        for (ItemStack drop : drops) {
            ItemEntity itemEntity = new ItemEntity(level, startPos.getX() + 0.5, startPos.getY() + 0.5, startPos.getZ() + 0.5, drop);
            level.addFreshEntity(itemEntity);
        }

        tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));

        for (BlockPos adjacent : List.of(
                startPos.north(), startPos.south(), startPos.east(),
                startPos.west(), startPos.above(), startPos.below())) {
            if (level.getBlockState(adjacent).getBlock() == oreType) {
                veinMine(level, adjacent, oreType, player, tool, visited);
            }
        }
    }

    private static ItemStack findSeedForCrop(Player player, CropBlock crop) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == Items.WHEAT_SEEDS && crop == Blocks.WHEAT) return stack;
            if (stack.getItem() == Items.CARROT && crop == Blocks.CARROTS) return stack;
            if (stack.getItem() == Items.POTATO && crop == Blocks.POTATOES) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void updateMasteryTooltip(ItemStack tool, int blocksMined) {
        CompoundTag display = tool.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag lore = display.getList("Lore", 8);

        for (int i = 0; i < lore.size(); i++) {
            String line = lore.getString(i);
            if (line.contains("Mastery")) {
                lore.remove(i);
                break;
            }
        }

        double bonus = Math.log(blocksMined) / Math.log(10) * 100; // log10(n) * 100%
        String masteryLine = "§6Mastery: " + blocksMined + " mined (+" + String.format("%.1f", bonus) + "% speed)";
        lore.add(StringTag.valueOf("{\"text\":\"" + masteryLine + "\"}"));

        display.put("Lore", lore);
    }

    // REJUVENATE ENCHANTMENT - repaired on break speed and heal
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack tool = player.getMainHandItem();
        int rejuvenateLevel = tool.getEnchantmentLevel(ModEnchantments.REJUVENATE.get());

        if (rejuvenateLevel > 0 && tool.isDamaged()) {
            Random random = new Random();
            int chance = 1000 / rejuvenateLevel;
            if (random.nextInt(chance) == 0) {
                tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        for (ItemStack armor : player.getArmorSlots()) {
            int rejuvenateLevel = armor.getEnchantmentLevel(ModEnchantments.REJUVENATE.get());
            if (rejuvenateLevel > 0) {
                Random random = new Random();
                int chance = 2000 / rejuvenateLevel;
                if (random.nextInt(chance) == 0) {
                    armor.setDamageValue(Math.max(0, armor.getDamageValue() - rejuvenateLevel));
                }
            }
        }
    }

    // Combat event handlers
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack weapon = player.getMainHandItem();
        Entity target = event.getTarget();

        // COMBO ENCHANTMENT - Update combo tracking
        int comboLevel = weapon.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (comboLevel > 0) {
            CompoundTag tag = weapon.getOrCreateTag();
            long currentTime = player.level().getGameTime();
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

        if (!(attacker instanceof Player)) return;
        Player player = (Player) attacker;
        ItemStack weapon = player.getMainHandItem();

        // RISKY ENCHANTMENT
        int riskyLevel = weapon.getEnchantmentLevel(ModEnchantments.RISKY.get());
        if (riskyLevel > 0) {
            boolean isCritical = player.getAttackStrengthScale(0.5f) > 0.9f &&
                    player.fallDistance > 0.0f && !player.onGround() &&
                    !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS);

            if (!isCritical) {
                float damage = event.getAmount();
                damage *= (1.0f - (riskyLevel * 0.05f));
                event.setAmount(damage);
            }
        }

        // HEALTHY ENCHANTMENT
        int healthyLevel = weapon.getEnchantmentLevel(ModEnchantments.HEALTHY.get());
        if (healthyLevel > 0) {
            float currentHealth = player.getHealth();
            float maxHealth = player.getMaxHealth();
            float healthRatio = maxHealth > 0 ? (currentHealth / maxHealth) : 0f;

            float healthBonus = healthRatio * (1.0f + (healthyLevel * 0.5f));
            float damage = event.getAmount() * (1.0f + healthBonus);
            event.setAmount(damage);
        }

        // ADRENALINE ENCHANTMENT
        int adrenalineLevel = weapon.getEnchantmentLevel(ModEnchantments.ADRENALINE.get());
        if (adrenalineLevel > 0) {
            float currentHealth = player.getHealth();
            float maxHealth = player.getMaxHealth();
            float lostHealth = Math.max(0f, maxHealth - currentHealth);
            float lostHealthRatio = maxHealth > 0 ? (lostHealth / maxHealth) : 0f;

            float adrenalineBonus = lostHealthRatio * (1.0f + (adrenalineLevel * 0.5f));
            float damage = event.getAmount() * (1.0f + adrenalineBonus);
            event.setAmount(damage);
        }

        // COMBO ENCHANTMENT DAMAGE
        int comboLevel = weapon.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (comboLevel > 0) {
            CompoundTag tag = weapon.getOrCreateTag();
            int comboCount = tag.getInt("ComboCount");
            if (comboCount > 0) {
                float comboDamage = comboCount * (1.0f + comboLevel * 0.5f);
                event.setAmount(event.getAmount() + comboDamage);
            }
        }

        // BREAKING ENCHANTMENT
        int breakingLevel = weapon.getEnchantmentLevel(ModEnchantments.BREAKING.get());
        if (breakingLevel > 0) {
            float baseDamage = event.getAmount() * (1.0f - (breakingLevel * 0.02f));
            event.setAmount(baseDamage);

            if (victim instanceof LivingEntity) {
                LivingEntity livingVictim = (LivingEntity) victim;
                float armorDamage = breakingLevel * 2.0f;
                for (ItemStack armorPiece : livingVictim.getArmorSlots()) {
                    if (!armorPiece.isEmpty()) {
                        armorPiece.hurtAndBreak((int) armorDamage, livingVictim,
                                (entity) -> entity.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.CHEST));
                    }
                }
            }
        }

        // Note: RENOUNCE and ALL_IN handled by attribute system elsewhere
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();

        if (!(attacker instanceof Player)) return;
        Player player = (Player) attacker;
        if (victim instanceof Player) return;

        ItemStack weapon = player.getMainHandItem();

        // EXTRACT ENCHANTMENT
        int extractLevel = weapon.getEnchantmentLevel(ModEnchantments.EXTRACT.get());
        if (extractLevel > 0) {
            if (!victim.level().isClientSide) {
                int bonusExp = 3 * extractLevel;
                ExperienceOrb.award((ServerLevel) victim.level(), victim.position(), bonusExp);
            }
        }

        // COMBO ENCHANTMENT
        int comboLevel = weapon.getEnchantmentLevel(ModEnchantments.COMBO.get());
        if (comboLevel > 0) {
            CompoundTag tag = weapon.getOrCreateTag();
            int comboCount = tag.getInt("ComboCount") + 1;
            tag.putInt("ComboCount", comboCount);
            tag.putLong("ComboLastKill", victim.level().getGameTime());

            if (!victim.level().isClientSide) {
                ((ServerLevel) victim.level()).sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + 1.0, victim.getZ(),
                        comboCount * 2, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    // LivingDrops - chance enchantment handling
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag entityData = entity.getPersistentData();
        if (entityData.contains("ChanceEnchantLevel")) {
            int chanceLevel = entityData.getInt("ChanceEnchantLevel");
            UUID playerUUID = entityData.getUUID("ChanceEnchantPlayer");

            if (event.getSource().getEntity() instanceof Player) {
                Player player = (Player) event.getSource().getEntity();
                if (player.getUUID().equals(playerUUID)) {
                    for (ItemEntity itemEntity : event.getDrops()) {
                        ItemStack stack = itemEntity.getItem();
                        if (entity.level().random.nextFloat() < (chanceLevel * 0.33f)) {
                            stack.setCount(stack.getCount() + 1);
                            itemEntity.setItem(stack);
                        }
                    }
                }
            }
        }
    }

    // LAVA WALKER, MAGNETISM, MARATHON - per-player tick
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
                    player.level().scheduleTick(pos, Blocks.MAGMA_BLOCK, 100 + player.level().random.nextInt(100));
                }
            }
        }

        // MAGNETISM
        ItemStack helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        int magnetismLevel = helmet.getEnchantmentLevel(ModEnchantments.MAGNETISM.get());
        if (magnetismLevel > 0) {
            double pullRadius = 3.0 + magnetismLevel * 2.0;
            double pullStrength = 0.1 * magnetismLevel;

            List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class,
                    new AABB(player.getX() - pullRadius, player.getY() - pullRadius, player.getZ() - pullRadius,
                            player.getX() + pullRadius, player.getY() + pullRadius, player.getZ() + pullRadius));

            for (ItemEntity item : items) {
                if (item.getAge() < 10) continue;
                Vec3 direction = player.position().subtract(item.position()).normalize();
                Vec3 velocity = direction.scale(pullStrength);
                item.setDeltaMovement(item.getDeltaMovement().add(velocity));
            }
        }

        // MARATHON
        ItemStack legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);

        int marathonLegs = legs.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        int marathonFeet = feet.getEnchantmentLevel(ModEnchantments.MARATHON.get());
        int marathonLevel = Math.max(marathonLegs, marathonFeet);

        if (marathonLevel > 0 && (player.isSprinting() || isPlayerMoving(player))) {
            if (player.tickCount % (100 - (marathonLevel * 10)) == 0) {
                player.getFoodData().setSaturation(
                        Math.min(player.getFoodData().getSaturationLevel() + 0.1f,
                                player.getFoodData().getFoodLevel())
                );
            }
        }
    }

    // DRAW enchant: faster bow drawing (stores data in player persistent)
    @SubscribeEvent
    public static void onPlayerTickDraw(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        Player player = event.player;
        if (!player.isUsingItem()) return;

        ItemStack item = player.getUseItem();
        int drawLevel = item.getEnchantmentLevel(ModEnchantments.DRAW.get());

        if (drawLevel > 0 && item.getItem() instanceof net.minecraft.world.item.BowItem) {
            CompoundTag playerData = player.getPersistentData();
            int useTime = player.getTicksUsingItem();

            float speedMultiplier = 1.0f + (drawLevel * 0.07f);
            int effectiveUseTime = (int) (useTime * speedMultiplier);
            playerData.putInt("DrawEnchantEffectiveTime", effectiveUseTime);
        }
    }

    // HOMING: set arrow NBT when arrow spawns
    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();
        if (!(arrow.getOwner() instanceof Player)) return;
        Player player = (Player) arrow.getOwner();

        ItemStack bow = player.getMainHandItem();
        int homingLevel = bow.getEnchantmentLevel(ModEnchantments.HOMING.get());

        if (homingLevel > 0) {
            LivingEntity target = findClosestEntityInCrosshair(player, 32.0);
            if (target != null) {
                CompoundTag arrowTag = arrow.getPersistentData();
                arrowTag.putUUID("HomingTarget", target.getUUID());
                arrowTag.putBoolean("IsHoming", true);
            }
        }

        int chanceLevel = bow.getEnchantmentLevel(ModEnchantments.CHANCE.get());
        if (chanceLevel > 0) {
            CompoundTag arrowTag = arrow.getPersistentData();
            arrowTag.putInt("ChanceLevel", chanceLevel);
        }

        int frostLevel = bow.getEnchantmentLevel(ModEnchantments.FROST.get());
        if (frostLevel > 0) {
            CompoundTag arrowTag = arrow.getPersistentData();
            arrowTag.putInt("FrostLevel", frostLevel);
        }
    }

    // HOMING: update arrow trajectories (run per-player tick)
    @SubscribeEvent
    public static void onArrowTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Player player = event.player;

        if (player.level().isClientSide) return;

        List<Arrow> arrows = player.level().getEntitiesOfClass(Arrow.class,
                player.getBoundingBox().inflate(50.0),
                arrow -> arrow.getOwner() == player && arrow.getPersistentData().getBoolean("IsHoming"));

        for (Arrow arrow : arrows) {
            CompoundTag arrowData = arrow.getPersistentData();
            UUID targetUUID = arrowData.getUUID("HomingTarget");

            if (targetUUID != null) {
                ServerLevel serverLevel = (ServerLevel) arrow.level();
                Entity targetEntity = serverLevel.getEntity(targetUUID);

                if (targetEntity instanceof LivingEntity target && target.isAlive()) {
                    Vec3 arrowPos = arrow.position();
                    Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
                    Vec3 direction = targetPos.subtract(arrowPos).normalize();

                    Vec3 currentVelocity = arrow.getDeltaMovement();
                    Vec3 newVelocity = currentVelocity.scale(0.9).add(direction.scale(0.1));

                    arrow.setDeltaMovement(newVelocity);

                    arrow.setYRot((float) (Mth.atan2(newVelocity.x, newVelocity.z) * (180F / (float) Math.PI)));
                    arrow.setXRot((float) (Mth.atan2(newVelocity.y, newVelocity.horizontalDistance()) * (180F / (float) Math.PI)));

                    serverLevel.sendParticles(ParticleTypes.ENCHANT,
                            arrowPos.x, arrowPos.y, arrowPos.z,
                            2, 0.1, 0.1, 0.1, 0.02);
                }
            }
        }
    }

    // Helper: find closest entity near crosshair
    private static LivingEntity findClosestEntityInCrosshair(Player player, double maxDistance) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endVec = eyePos.add(lookVec.scale(maxDistance));

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(eyePos, endVec).inflate(2.0),
                entity -> entity != player && !entity.isAlliedTo(player));

        LivingEntity closest = null;
        double closestAngle = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            Vec3 toEntity = entity.position().subtract(eyePos).normalize();
            double angle = Math.acos(lookVec.dot(toEntity));
            if (angle < Math.PI / 4 && angle < closestAngle) {
                closest = entity;
                closestAngle = angle;
            }
        }

        return closest;
    }

    // Mastery speed bonus helper
    public static float getMasterySpeedBonus(ItemStack tool) {
        int masteryLevel = tool.getEnchantmentLevel(ModEnchantments.MASTERY.get());
        if (masteryLevel == 0) return 1.0f;

        CompoundTag tag = tool.getTag();
        if (tag == null) return 1.0f;

        int blocksMined = tag.getInt("MasteryBlocksMined");
        if (blocksMined < 10) return 1.0f;

        double bonus = Math.log10(blocksMined);
        return 1.0f + (float) bonus;
    }

    // Helpers for all-in/renounce (kept for compatibility)
    public static float getAllInDamageMultiplier(ItemStack weapon) {
        int allInLevel = weapon.getEnchantmentLevel(ModEnchantments.ALL_IN.get());
        return allInLevel > 0 ? 3.0f : 1.0f;
    }

    public static float getRenounceSpeedModifier(ItemStack axe, BlockState state) {
        int renounceLevel = axe.getEnchantmentLevel(ModEnchantments.RENOUNCE.get());
        if (renounceLevel > 0 && state.is(BlockTags.LOGS)) {
            return 1.0f;
        }
        return 1.0f;
    }

    public static float getRenounceDamageBonus(ItemStack axe) {
        int renounceLevel = axe.getEnchantmentLevel(ModEnchantments.RENOUNCE.get());
        return renounceLevel > 0 ? 5.0f : 0.0f;
    }

    // Movement helper
    private static boolean isPlayerMoving(Player player) {
        Vec3 movement = player.getDeltaMovement();
        return movement.horizontalDistanceSqr() > 0.001;
    }
}
