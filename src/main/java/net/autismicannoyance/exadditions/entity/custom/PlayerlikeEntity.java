package net.autismicannoyance.exadditions.entity.custom;

import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.network.AdaptationWheelPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class PlayerlikeEntity extends Monster {

    private static final EntityDataAccessor<Float> WHEEL_ROTATION = SynchedEntityData.defineId(PlayerlikeEntity.class, EntityDataSerializers.FLOAT);

    // Damage type tracking for adaptation
    private final Map<String, Integer> damageHitCounts = new HashMap<>();
    private final Map<String, Float> damageResistances = new HashMap<>();

    // Boss bar
    private ServerBossEvent bossEvent;

    public PlayerlikeEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);

        // Initialize boss bar on server side
        if (!level.isClientSide) {
            this.bossEvent = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            this.bossEvent.setDarkenScreen(false);
            this.bossEvent.setCreateWorldFog(false);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(WHEEL_ROTATION, 0.0F);
    }

    @Override
    protected void registerGoals() {
        // Combat goals
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // Targeting goals - makes it hostile
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                entity -> entity instanceof LivingEntity && !(entity instanceof PlayerlikeEntity)));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D) // Increased health for boss
                .add(Attributes.MOVEMENT_SPEED, 0.3D) // Slightly faster
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D) // Some armor toughness
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D) // Knockback on attacks
                .add(Attributes.ATTACK_DAMAGE, 14.0D) // 14 damage as requested
                .add(Attributes.FOLLOW_RANGE, 32.0D); // Larger detection range
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (!this.level().isClientSide) {
            String damageType = damageSource.getMsgId();

            // Track hit count
            int hitCount = damageHitCounts.getOrDefault(damageType, 0) + 1;
            damageHitCounts.put(damageType, hitCount);

            // Calculate resistance based on hit count (faster adaptation for boss)
            float resistance = 0.0F;
            if (hitCount >= 3) {
                resistance = 0.4F; // 40% resistance after 3 hits
            }
            if (hitCount >= 5) {
                resistance = 0.7F; // 70% resistance after 5 hits
            }
            if (hitCount >= 7) {
                resistance = 0.9F; // 90% resistance after 7 hits
            }
            if (hitCount >= 10) {
                resistance = 1.0F; // Full immunity after 10 hits
            }

            // Update resistance map
            damageResistances.put(damageType, resistance);

            // Apply resistance to damage
            float finalDamage = amount * (1.0F - resistance);

            // Rotate wheel and send packet to clients
            float currentRotation = this.entityData.get(WHEEL_ROTATION);
            float newRotation = (currentRotation + 45.0F) % 360.0F; // Rotate 45 degrees per hit
            this.entityData.set(WHEEL_ROTATION, newRotation);

            // Send wheel rotation packet to nearby players
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new AdaptationWheelPacket(this.getId(), newRotation, resistance));

            // Become more aggressive when adapted (speed boost)
            if (resistance > 0.5F) {
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(16.0D); // Even more damage when adapted
            }

            // Call parent with modified damage
            return super.hurt(damageSource, finalDamage);
        }

        return super.hurt(damageSource, amount);
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
    public void tick() {
        super.tick();

        // Update boss bar
        if (!this.level().isClientSide && this.bossEvent != null) {
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }

        // Aggressive behavior - target nearby entities more actively
        if (!this.level().isClientSide && this.getTarget() == null && this.tickCount % 20 == 0) {
            // Look for players nearby every second
            Player nearestPlayer = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 16.0D, false);
            if (nearestPlayer != null && !nearestPlayer.isCreative() && !nearestPlayer.isSpectator()) {
                this.setTarget(nearestPlayer);
            }
        }
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        // Save adaptation data
        CompoundTag adaptationTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : damageHitCounts.entrySet()) {
            adaptationTag.putInt(entry.getKey() + "_hits", entry.getValue());
        }
        for (Map.Entry<String, Float> entry : damageResistances.entrySet()) {
            adaptationTag.putFloat(entry.getKey() + "_resistance", entry.getValue());
        }
        tag.put("Adaptation", adaptationTag);

        tag.putFloat("WheelRotation", this.entityData.get(WHEEL_ROTATION));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        // Load adaptation data
        if (tag.contains("Adaptation")) {
            CompoundTag adaptationTag = tag.getCompound("Adaptation");
            for (String key : adaptationTag.getAllKeys()) {
                if (key.endsWith("_hits")) {
                    String damageType = key.substring(0, key.length() - 5);
                    damageHitCounts.put(damageType, adaptationTag.getInt(key));
                } else if (key.endsWith("_resistance")) {
                    String damageType = key.substring(0, key.length() - 11);
                    damageResistances.put(damageType, adaptationTag.getFloat(key));
                }
            }
        }

        if (tag.contains("WheelRotation")) {
            this.entityData.set(WHEEL_ROTATION, tag.getFloat("WheelRotation"));
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (this.bossEvent != null) {
            this.bossEvent.removeAllPlayers();
        }
    }

    // Override to make it always hostile
    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return true;
    }
}