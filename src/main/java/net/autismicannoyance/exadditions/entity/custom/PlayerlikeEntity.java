package net.autismicannoyance.exadditions.entity.custom;

import net.autismicannoyance.exadditions.network.ModNetworking;
import net.autismicannoyance.exadditions.network.AdaptationWheelPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class PlayerlikeEntity extends Mob {

    private static final EntityDataAccessor<Float> WHEEL_ROTATION = SynchedEntityData.defineId(PlayerlikeEntity.class, EntityDataSerializers.FLOAT);

    // Damage type tracking for adaptation
    private final Map<String, Integer> damageHitCounts = new HashMap<>();
    private final Map<String, Float> damageResistances = new HashMap<>();

    // Boss bar
    private ServerBossEvent bossEvent;

    public PlayerlikeEntity(EntityType<? extends Mob> entityType, Level level) {
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
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (!this.level().isClientSide) {
            String damageType = damageSource.getMsgId();

            // Track hit count
            int hitCount = damageHitCounts.getOrDefault(damageType, 0) + 1;
            damageHitCounts.put(damageType, hitCount);

            // Calculate resistance based on hit count
            float resistance = 0.0F;
            if (hitCount >= 4) {
                resistance = 0.5F; // 50% resistance
            }
            if (hitCount >= 6) {
                resistance = 0.75F; // 75% resistance
            }
            if (hitCount >= 7) {
                resistance = 1.0F; // Full immunity
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

            // Call parent with modified damage
            return super.hurt(damageSource, finalDamage);
        }

        return super.hurt(damageSource, amount);
    }

    @Override
    public void tick() {
        super.tick();

        // Update boss bar
        if (!this.level().isClientSide && this.bossEvent != null) {
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
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
}