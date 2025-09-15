package net.autismicannoyance.exadditions.entity.custom;

import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.network.AdaptationWheelPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class PlayerlikeEntity extends Monster {

    private static final EntityDataAccessor<Float> WHEEL_ROTATION = SynchedEntityData.defineId(PlayerlikeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BOSS_PHASE = SynchedEntityData.defineId(PlayerlikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LAST_WEAPON_TYPE = SynchedEntityData.defineId(PlayerlikeEntity.class, EntityDataSerializers.STRING);

    // Enhanced adaptation tracking - now tracks specific sources
    private final Map<String, Integer> adaptationHitCounts = new HashMap<>(); // "damage_source:item_id" -> hit count
    private final Map<String, Float> adaptationResistances = new HashMap<>(); // "damage_source:item_id" -> resistance level

    // Weapon tracking with enchantments preserved
    private ItemStack lastWeaponUsed = ItemStack.EMPTY;
    private String lastDamageSource = "";

    // Combat behavior tracking
    private int rangedAttackCount = 0;
    private int maxRangedAttacks = 2; // Shoot 1-2 times before closing in
    private boolean shouldCloseIn = false;

    // Boss mechanics
    private int regenerationTimer = 0;
    private int weaponAttackCooldown = 0;

    // Boss bar with custom segments
    private ServerBossEvent bossEvent;

    // Phase thresholds
    private static final float PHASE_1_THRESHOLD = 0.25f;
    private static final float PHASE_2_THRESHOLD = 0.5f;
    private static final float PHASE_3_THRESHOLD = 0.75f;

    public PlayerlikeEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);

        if (!level.isClientSide) {
            this.bossEvent = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
            this.bossEvent.setDarkenScreen(false);
            this.bossEvent.setCreateWorldFog(false);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(WHEEL_ROTATION, 0.0F);
        this.entityData.define(BOSS_PHASE, 0);
        this.entityData.define(LAST_WEAPON_TYPE, "");
    }

    @Override
    protected void registerGoals() {
        // Combat goals - now attacks ALL living entities
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AdaptedMeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new AdaptedRangedAttackGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, LivingEntity.class, 32.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        // Targeting goals - ATTACKS EVERYTHING
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                entity -> entity instanceof LivingEntity && !(entity instanceof PlayerlikeEntity)));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (!this.level().isClientSide) {
            String adaptationKey = createAdaptationKey(damageSource);

            // Track the weapon or damage source
            if (damageSource.getEntity() instanceof Player player) {
                ItemStack heldItem = player.getMainHandItem();
                if (!heldItem.isEmpty()) {
                    this.lastWeaponUsed = heldItem.copy();
                    this.lastDamageSource = getWeaponType(heldItem);
                    this.entityData.set(LAST_WEAPON_TYPE, this.lastDamageSource);
                }
            }

            // Track hit count for this specific adaptation key
            int hitCount = adaptationHitCounts.getOrDefault(adaptationKey, 0) + 1;
            adaptationHitCounts.put(adaptationKey, hitCount);

            // Calculate resistance - requires 4 hits from same source to start adapting
            float resistance = 0.0F;
            if (hitCount >= 4) {
                // Progressive adaptation: 25% at 4 hits, scaling up
                resistance = Math.min(1.0F, (hitCount - 3) * 0.25F);
            }

            adaptationResistances.put(adaptationKey, resistance);

            // Apply resistance to damage
            float finalDamage = amount * (1.0F - resistance);

            // Rotate wheel and send packet to clients
            float currentRotation = this.entityData.get(WHEEL_ROTATION);
            float newRotation = (currentRotation + 30.0F) % 360.0F;
            this.entityData.set(WHEEL_ROTATION, newRotation);

            // Send wheel rotation packet
            float maxResistance = getMaxResistance();
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new AdaptationWheelPacket(this.getId(), newRotation, maxResistance));

            boolean result = super.hurt(damageSource, finalDamage);
            updateBossPhase();

            return result;
        }

        return super.hurt(damageSource, amount);
    }

    private String createAdaptationKey(DamageSource damageSource) {
        StringBuilder key = new StringBuilder();
        key.append(damageSource.getMsgId());

        // Add specific source information
        if (damageSource.getEntity() != null) {
            if (damageSource.getEntity() instanceof Player player) {
                ItemStack weapon = player.getMainHandItem();
                if (!weapon.isEmpty()) {
                    // Include item type and enchantments in key (enchants don't differentiate)
                    key.append(":").append(weapon.getItem().toString());
                }
            } else {
                // Different mobs have different adaptation keys
                key.append(":").append(damageSource.getEntity().getType().toString());
            }
        } else {
            // Environmental damage sources
            if (damageSource.getMsgId().contains("fall")) {
                key.append(":fall_damage");
            } else if (damageSource.getMsgId().contains("cactus")) {
                key.append(":cactus_damage");
            } else if (damageSource.getMsgId().contains("void")) {
                key.append(":void_damage");
            } else if (damageSource.getMsgId().contains("explosion")) {
                if (damageSource.getMsgId().contains("creeper")) {
                    key.append(":creeper_explosion");
                } else if (damageSource.getMsgId().contains("wither")) {
                    key.append(":wither_explosion");
                } else {
                    key.append(":generic_explosion");
                }
            }
        }

        return key.toString();
    }

    private void updateBossPhase() {
        float healthPercentage = this.getHealth() / this.getMaxHealth();
        float damageTaken = 1.0f - healthPercentage;

        int newPhase = 0;
        if (damageTaken >= PHASE_3_THRESHOLD) {
            newPhase = 3;
        } else if (damageTaken >= PHASE_2_THRESHOLD) {
            newPhase = 2;
        } else if (damageTaken >= PHASE_1_THRESHOLD) {
            newPhase = 1;
        }

        int currentPhase = this.entityData.get(BOSS_PHASE);
        if (newPhase != currentPhase) {
            this.entityData.set(BOSS_PHASE, newPhase);
            onPhaseChange(newPhase);
        }
    }

    private void onPhaseChange(int newPhase) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.0F, 1.0F);

        switch (newPhase) {
            case 1:
                // Reset ranged attack behavior
                this.rangedAttackCount = 0;
                this.shouldCloseIn = false;
                break;
            case 2:
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35D);
                break;
            case 3:
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.4D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20.0D);
                this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48.0D);
                break;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (this.bossEvent != null) {
                this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
            }

            // Handle regeneration in phase 2+
            int phase = this.entityData.get(BOSS_PHASE);
            if (phase >= 2) {
                regenerationTimer++;
                if (regenerationTimer >= 20) {
                    this.heal(3.0F);
                    regenerationTimer = 0;
                }
            }

            if (phase >= 1 && weaponAttackCooldown > 0) {
                weaponAttackCooldown--;
            }

            // Send continuous wheel rotation updates
            float rotation = this.entityData.get(WHEEL_ROTATION);
            rotation = (rotation + 1.0F) % 360.0F;
            this.entityData.set(WHEEL_ROTATION, rotation);

            if (this.tickCount % 10 == 0) {
                float maxResistance = getMaxResistance();
                ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
                        new AdaptationWheelPacket(this.getId(), rotation, maxResistance));
            }
        }
    }

    private float getMaxResistance() {
        return adaptationResistances.values().stream()
                .max(Float::compareTo)
                .orElse(0.0F);
    }

    private String getWeaponType(ItemStack item) {
        if (item.getItem() instanceof SwordItem) {
            return "sword";
        } else if (item.getItem() instanceof BowItem) {
            return "bow";
        } else if (item.getItem() instanceof CrossbowItem) {
            return "crossbow";
        } else if (item.getItem() instanceof TridentItem) {
            return "trident";
        } else if (item.getItem() instanceof AxeItem) {
            return "axe";
        }
        return "melee";
    }

    public void performAdaptedAttack(LivingEntity target) {
        int phase = this.entityData.get(BOSS_PHASE);
        if (phase < 1 || weaponAttackCooldown > 0) return;

        String weaponType = this.entityData.get(LAST_WEAPON_TYPE);
        double distance = this.distanceToSqr(target);

        // Decide attack type based on distance and weapon adaptation
        if (distance > 100.0D && (weaponType.equals("bow") || weaponType.equals("crossbow"))) { // 10+ blocks
            if (!shouldCloseIn && rangedAttackCount < maxRangedAttacks) {
                performRangedAttack(target, weaponType);
                rangedAttackCount++;
                if (rangedAttackCount >= maxRangedAttacks) {
                    shouldCloseIn = true; // Start closing in after max ranged attacks
                }
            } else {
                // Move closer to target
                this.getNavigation().moveTo(target, 1.5D);
            }
        } else if (distance > 64.0D && weaponType.equals("trident")) { // 8+ blocks for trident
            performTridentAttack(target);
        } else {
            // Close range - use melee or reset ranged behavior
            if (shouldCloseIn && distance < 25.0D) { // Reset when close
                rangedAttackCount = 0;
                shouldCloseIn = false;
            }
            performMeleeAttack(target, weaponType);
        }

        weaponAttackCooldown = 40; // 2 second cooldown
    }

    private void performRangedAttack(LivingEntity target, String weaponType) {
        this.swing(InteractionHand.MAIN_HAND);

        if (weaponType.equals("bow")) {
            Arrow arrow = new Arrow(this.level(), this);

            // Preserve enchantments from the original weapon
            if (!lastWeaponUsed.isEmpty() && lastWeaponUsed.getItem() instanceof BowItem) {
                // Apply bow enchantments to arrow
                arrow.setBaseDamage(8.0D + EnchantmentHelper.getDamageBonus(lastWeaponUsed, target.getMobType()));

                if (EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FLAMING_ARROWS, lastWeaponUsed) > 0) {
                    arrow.setSecondsOnFire(100);
                }

                int punchLevel = EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.PUNCH_ARROWS, lastWeaponUsed);
                if (punchLevel > 0) {
                    arrow.setKnockback(punchLevel);
                }
            } else {
                arrow.setBaseDamage(8.0D);
            }

            // Aim at target
            double dx = target.getX() - this.getX();
            double dy = target.getY(0.1D) - arrow.getY();
            double dz = target.getZ() - this.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            arrow.shoot(dx, dy + distance * 0.2D, dz, 1.6F, 8.0F);

            this.level().addFreshEntity(arrow);
            this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F);

        } else if (weaponType.equals("crossbow")) {
            Arrow bolt = new Arrow(this.level(), this);

            // Preserve crossbow enchantments
            if (!lastWeaponUsed.isEmpty() && lastWeaponUsed.getItem() instanceof CrossbowItem) {
                bolt.setBaseDamage(10.0D + EnchantmentHelper.getDamageBonus(lastWeaponUsed, target.getMobType()));

                if (EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FLAMING_ARROWS, lastWeaponUsed) > 0) {
                    bolt.setSecondsOnFire(100);
                }

                int piercingLevel = EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.PIERCING, lastWeaponUsed);
                if (piercingLevel > 0) {
                    bolt.setPierceLevel((byte) piercingLevel);
                }
            } else {
                bolt.setBaseDamage(10.0D);
            }

            Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 direction = targetPos.subtract(this.position().add(0, this.getBbHeight() * 0.5, 0)).normalize();

            bolt.shoot(direction.x, direction.y, direction.z, 2.0F, 1.0F);

            this.level().addFreshEntity(bolt);
            this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F);
        }
    }

    private void performTridentAttack(LivingEntity target) {
        this.swing(InteractionHand.MAIN_HAND);

        ItemStack tridentStack = new ItemStack(Items.TRIDENT);

        // Preserve trident enchantments
        if (!lastWeaponUsed.isEmpty() && lastWeaponUsed.getItem() instanceof TridentItem) {
            // Copy enchantments from original trident
            tridentStack.setTag(lastWeaponUsed.getTag() != null ? lastWeaponUsed.getTag().copy() : null);
        }

        ThrownTrident trident = new ThrownTrident(this.level(), this, tridentStack);

        double dx = target.getX() - this.getX();
        double dy = target.getY(0.1D) - trident.getY();
        double dz = target.getZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        trident.shoot(dx, dy + distance * 0.1D, dz, 1.6F, 1.0F);

        this.level().addFreshEntity(trident);
        this.playSound(SoundEvents.TRIDENT_THROW, 1.0F, 1.0F);
    }

    private void performMeleeAttack(LivingEntity target, String weaponType) {
        this.swing(InteractionHand.MAIN_HAND);

        if (this.distanceToSqr(target) < 9.0D) {
            float baseDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);

            // Add weapon-specific damage if we have the weapon
            if (!lastWeaponUsed.isEmpty()) {
                baseDamage += EnchantmentHelper.getDamageBonus(lastWeaponUsed, target.getMobType());

                // Apply weapon-specific effects
                if (lastWeaponUsed.getItem() instanceof SwordItem) {
                    // Sword sweep attack
                    this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.0F);
                } else if (lastWeaponUsed.getItem() instanceof AxeItem) {
                    // Axe critical hit
                    this.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
                    baseDamage *= 1.25F; // Axe bonus damage
                }
            }

            target.hurt(this.damageSources().mobAttack(this), baseDamage);

            // Apply knockback if weapon has it
            if (!lastWeaponUsed.isEmpty()) {
                int knockbackLevel = EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK, lastWeaponUsed);
                if (knockbackLevel > 0) {
                    target.knockback(knockbackLevel * 0.4F, this.getX() - target.getX(), this.getZ() - target.getZ());
                }
            }
        }
    }

    // Enhanced AI Goals
    private static class AdaptedMeleeAttackGoal extends MeleeAttackGoal {
        private final PlayerlikeEntity entity;

        public AdaptedMeleeAttackGoal(PlayerlikeEntity entity, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(entity, speedModifier, followingTargetEvenIfNotSeen);
            this.entity = entity;
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
            double attackReach = this.getAttackReachSqr(target);
            if (distToTargetSqr <= attackReach && this.isTimeToAttack()) {
                this.resetAttackCooldown();
                entity.performAdaptedAttack(target);
            }
        }
    }

    private static class AdaptedRangedAttackGoal extends Goal {
        private final PlayerlikeEntity entity;
        private int attackTimer = 0;

        public AdaptedRangedAttackGoal(PlayerlikeEntity entity) {
            this.entity = entity;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = entity.getTarget();
            return target != null && entity.entityData.get(BOSS_PHASE) >= 1;
        }

        @Override
        public void tick() {
            LivingEntity target = entity.getTarget();
            if (target != null) {
                entity.getLookControl().setLookAt(target);

                if (--attackTimer <= 0) {
                    entity.performAdaptedAttack(target);
                    attackTimer = 60; // 3 second cooldown for ranged attacks
                }
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.bossEvent != null) {
            this.bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (this.bossEvent != null) {
            this.bossEvent.removePlayer(player);
        }
    }

    public float getWheelRotation() {
        return this.entityData.get(WHEEL_ROTATION);
    }

    public int getBossPhase() {
        return this.entityData.get(BOSS_PHASE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        // Save enhanced adaptation data
        CompoundTag adaptationTag = new CompoundTag();
        ListTag hitCountsList = new ListTag();
        ListTag resistancesList = new ListTag();

        for (Map.Entry<String, Integer> entry : adaptationHitCounts.entrySet()) {
            CompoundTag hitEntry = new CompoundTag();
            hitEntry.putString("key", entry.getKey());
            hitEntry.putInt("hits", entry.getValue());
            hitCountsList.add(hitEntry);
        }

        for (Map.Entry<String, Float> entry : adaptationResistances.entrySet()) {
            CompoundTag resistanceEntry = new CompoundTag();
            resistanceEntry.putString("key", entry.getKey());
            resistanceEntry.putFloat("resistance", entry.getValue());
            resistancesList.add(resistanceEntry);
        }

        adaptationTag.put("HitCounts", hitCountsList);
        adaptationTag.put("Resistances", resistancesList);
        tag.put("EnhancedAdaptation", adaptationTag);

        tag.putFloat("WheelRotation", this.entityData.get(WHEEL_ROTATION));
        tag.putInt("BossPhase", this.entityData.get(BOSS_PHASE));
        tag.putString("LastWeaponType", this.entityData.get(LAST_WEAPON_TYPE));
        tag.putInt("RangedAttackCount", this.rangedAttackCount);
        tag.putBoolean("ShouldCloseIn", this.shouldCloseIn);

        if (!lastWeaponUsed.isEmpty()) {
            CompoundTag weaponTag = new CompoundTag();
            lastWeaponUsed.save(weaponTag);
            tag.put("LastWeapon", weaponTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        // Load enhanced adaptation data
        if (tag.contains("EnhancedAdaptation")) {
            CompoundTag adaptationTag = tag.getCompound("EnhancedAdaptation");

            if (adaptationTag.contains("HitCounts")) {
                ListTag hitCountsList = adaptationTag.getList("HitCounts", 10);
                for (int i = 0; i < hitCountsList.size(); i++) {
                    CompoundTag hitEntry = hitCountsList.getCompound(i);
                    adaptationHitCounts.put(hitEntry.getString("key"), hitEntry.getInt("hits"));
                }
            }

            if (adaptationTag.contains("Resistances")) {
                ListTag resistancesList = adaptationTag.getList("Resistances", 10);
                for (int i = 0; i < resistancesList.size(); i++) {
                    CompoundTag resistanceEntry = resistancesList.getCompound(i);
                    adaptationResistances.put(resistanceEntry.getString("key"), resistanceEntry.getFloat("resistance"));
                }
            }
        }

        if (tag.contains("WheelRotation")) {
            this.entityData.set(WHEEL_ROTATION, tag.getFloat("WheelRotation"));
        }
        if (tag.contains("BossPhase")) {
            this.entityData.set(BOSS_PHASE, tag.getInt("BossPhase"));
        }
        if (tag.contains("LastWeaponType")) {
            this.entityData.set(LAST_WEAPON_TYPE, tag.getString("LastWeaponType"));
        }
        if (tag.contains("RangedAttackCount")) {
            this.rangedAttackCount = tag.getInt("RangedAttackCount");
        }
        if (tag.contains("ShouldCloseIn")) {
            this.shouldCloseIn = tag.getBoolean("ShouldCloseIn");
        }
        if (tag.contains("LastWeapon")) {
            lastWeaponUsed = ItemStack.of(tag.getCompound("LastWeapon"));
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (this.bossEvent != null) {
            this.bossEvent.removeAllPlayers();
        }
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return true;
    }
}