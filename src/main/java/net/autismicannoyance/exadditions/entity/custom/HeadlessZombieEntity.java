package net.autismicannoyance.exadditions.entity.custom;

import net.autismicannoyance.exadditions.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeadlessZombieEntity extends Monster {

    private static final EntityDataAccessor<Integer> DEATH_COUNT = SynchedEntityData.defineId(HeadlessZombieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LAST_DEATH_SOURCE = SynchedEntityData.defineId(HeadlessZombieEntity.class, EntityDataSerializers.STRING);

    // Damage resistance tracking - maps damage source to resistance level (0.0 to 0.9)
    private final Map<String, Float> damageResistances = new HashMap<>();

    // Target tracking
    private UUID targetPlayerUUID;
    private Vec3 lastKnownPlayerPos;
    private int ticksSinceLastPlayerSeen = 0;
    private int chunkLoadRadius = 3; // How many chunks around the zombie to keep loaded

    // Base stats that increase with each death
    private static final double BASE_HEALTH = 20.0;
    private static final double BASE_DAMAGE = 3.0;
    private static final double BASE_SPEED = 0.23;

    public HeadlessZombieEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.setCanPickUpLoot(false);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DEATH_COUNT, 0);
        this.entityData.define(LAST_DEATH_SOURCE, "");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HeadlessZombieMeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(2, new HeadlessZombieTrackingGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 64.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, BASE_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 128.0D) // Large follow range for infinite tracking
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D); // Cannot be knocked back
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            // Keep chunks loaded around the zombie
            keepChunksLoaded(serverLevel);

            // Track nearest player if we don't have a target
            if (this.getTarget() == null || !this.getTarget().isAlive()) {
                findAndSetNearestPlayer(serverLevel);
            }

            // Update player tracking
            updatePlayerTracking();
        }
    }

    private void keepChunksLoaded(ServerLevel serverLevel) {
        BlockPos zombiePos = this.blockPosition();
        int chunkX = zombiePos.getX() >> 4;
        int chunkZ = zombiePos.getZ() >> 4;

        // Force load chunks in a radius around the zombie
        for (int dx = -chunkLoadRadius; dx <= chunkLoadRadius; dx++) {
            for (int dz = -chunkLoadRadius; dz <= chunkLoadRadius; dz++) {
                int targetChunkX = chunkX + dx;
                int targetChunkZ = chunkZ + dz;

                // Force the chunk to be loaded
                ChunkAccess chunk = serverLevel.getChunk(targetChunkX, targetChunkZ, ChunkStatus.FULL, true);
                if (chunk != null) {
                    serverLevel.setChunkForced(targetChunkX, targetChunkZ, true);
                }
            }
        }
    }

    private void findAndSetNearestPlayer(ServerLevel serverLevel) {
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        // Find the nearest player in the entire world
        for (Player player : serverLevel.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;

            double distance = this.distanceToSqr(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            this.setTarget(nearestPlayer);
            this.targetPlayerUUID = nearestPlayer.getUUID();
            this.lastKnownPlayerPos = nearestPlayer.position();
            this.ticksSinceLastPlayerSeen = 0;
        }
    }

    private void updatePlayerTracking() {
        if (this.targetPlayerUUID != null) {
            Player targetPlayer = null;

            // Find the target player
            for (Player player : this.level().players()) {
                if (player.getUUID().equals(this.targetPlayerUUID)) {
                    targetPlayer = player;
                    break;
                }
            }

            if (targetPlayer != null && targetPlayer.isAlive()) {
                this.lastKnownPlayerPos = targetPlayer.position();
                this.ticksSinceLastPlayerSeen = 0;

                // Always move towards the target player, even if very far away
                if (this.distanceToSqr(targetPlayer) > 2.0) {
                    this.getNavigation().moveTo(targetPlayer, 1.2);
                }
            } else {
                // Player is not found, move towards last known position
                this.ticksSinceLastPlayerSeen++;
                if (this.lastKnownPlayerPos != null && this.ticksSinceLastPlayerSeen < 6000) { // 5 minutes
                    this.getNavigation().moveTo(this.lastKnownPlayerPos.x, this.lastKnownPlayerPos.y, this.lastKnownPlayerPos.z, 1.2);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // Apply damage resistance based on death history
        String damageType = getDamageSourceKey(damageSource);
        float resistance = damageResistances.getOrDefault(damageType, 0.0f);
        float finalDamage = amount * (1.0f - resistance);

        return super.hurt(damageSource, finalDamage);
    }

    @Override
    protected void actuallyHurt(DamageSource damageSource, float damageAmount) {
        super.actuallyHurt(damageSource, damageAmount);

        // If this damage kills the zombie, prepare for respawn
        if (this.getHealth() <= 0) {
            this.onDeath(damageSource);
        }
    }

    private void onDeath(DamageSource damageSource) {
        if (this.level().isClientSide) return;

        // Increment death count
        int deathCount = this.entityData.get(DEATH_COUNT) + 1;
        this.entityData.set(DEATH_COUNT, deathCount);

        // Record what killed us and increase resistance
        String damageType = getDamageSourceKey(damageSource);
        this.entityData.set(LAST_DEATH_SOURCE, damageType);

        // Calculate new resistance (exponential: 90% reduction after first death from this source)
        float currentResistance = damageResistances.getOrDefault(damageType, 0.0f);
        float newResistance = 1.0f - (1.0f - currentResistance) * 0.1f; // 90% of remaining vulnerability removed
        newResistance = Math.min(0.99f, newResistance); // Cap at 99% resistance
        damageResistances.put(damageType, newResistance);

        // Schedule respawn
        scheduleRespawn();
    }

    private void scheduleRespawn() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Save current state
        CompoundTag zombieData = new CompoundTag();
        this.addAdditionalSaveData(zombieData);

        // Remove this instance
        this.discard();

        // Respawn after 3 seconds with improved stats
        serverLevel.getServer().execute(() -> {
            // Wait 60 ticks (3 seconds)
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Create new headless zombie
            HeadlessZombieEntity newZombie = ModEntities.HEADLESS_ZOMBIE.get().create(serverLevel);
            if (newZombie != null) {
                // Restore saved data
                newZombie.readAdditionalSaveData(zombieData);

                // Apply stat improvements based on death count
                int deathCount = newZombie.entityData.get(DEATH_COUNT);
                applyStatUpgrades(newZombie, deathCount);

                // Spawn at a random location near the world spawn or last death location
                BlockPos spawnPos = findSafeSpawnLocation(serverLevel);
                newZombie.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);

                serverLevel.addFreshEntity(newZombie);
            }
        });
    }

    private void applyStatUpgrades(HeadlessZombieEntity zombie, int deathCount) {
        // +1 heart (2 health) per death
        double newMaxHealth = BASE_HEALTH + (deathCount * 2.0);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);
        zombie.setHealth((float) newMaxHealth);

        // +1 damage per death
        double newDamage = BASE_DAMAGE + deathCount;
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(newDamage);

        // +10% speed per death (linear)
        double speedMultiplier = 1.0 + (deathCount * 0.1);
        double newSpeed = BASE_SPEED * speedMultiplier;
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(newSpeed);
    }

    private BlockPos findSafeSpawnLocation(ServerLevel serverLevel) {
        BlockPos worldSpawn = serverLevel.getSharedSpawnPos();

        // Try to spawn within 1000 blocks of world spawn
        for (int attempts = 0; attempts < 50; attempts++) {
            int x = worldSpawn.getX() + (this.random.nextInt(2000) - 1000);
            int z = worldSpawn.getZ() + (this.random.nextInt(2000) - 1000);
            int y = serverLevel.getHeight();

            // Find ground level
            for (int checkY = y; checkY > serverLevel.getMinBuildHeight(); checkY--) {
                BlockPos checkPos = new BlockPos(x, checkY, z);
                if (!serverLevel.getBlockState(checkPos).isAir() &&
                        serverLevel.getBlockState(checkPos.above()).isAir() &&
                        serverLevel.getBlockState(checkPos.above(2)).isAir()) {
                    return checkPos.above();
                }
            }
        }

        return worldSpawn; // Fallback
    }

    private String getDamageSourceKey(DamageSource source) {
        StringBuilder key = new StringBuilder();
        key.append(source.getMsgId());

        if (source.getEntity() != null) {
            key.append("_").append(source.getEntity().getType().toString());
        }

        if (source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity()) {
            key.append("_").append(source.getDirectEntity().getType().toString());
        }

        return key.toString();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putInt("DeathCount", this.entityData.get(DEATH_COUNT));
        tag.putString("LastDeathSource", this.entityData.get(LAST_DEATH_SOURCE));

        if (this.targetPlayerUUID != null) {
            tag.putUUID("TargetPlayerUUID", this.targetPlayerUUID);
        }

        if (this.lastKnownPlayerPos != null) {
            tag.putDouble("LastPlayerX", this.lastKnownPlayerPos.x);
            tag.putDouble("LastPlayerY", this.lastKnownPlayerPos.y);
            tag.putDouble("LastPlayerZ", this.lastKnownPlayerPos.z);
        }

        // Save damage resistances
        CompoundTag resistances = new CompoundTag();
        for (Map.Entry<String, Float> entry : damageResistances.entrySet()) {
            resistances.putFloat(entry.getKey(), entry.getValue());
        }
        tag.put("DamageResistances", resistances);

        tag.putInt("TicksSinceLastPlayerSeen", this.ticksSinceLastPlayerSeen);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("DeathCount")) {
            this.entityData.set(DEATH_COUNT, tag.getInt("DeathCount"));
        }

        if (tag.contains("LastDeathSource")) {
            this.entityData.set(LAST_DEATH_SOURCE, tag.getString("LastDeathSource"));
        }

        if (tag.contains("TargetPlayerUUID")) {
            this.targetPlayerUUID = tag.getUUID("TargetPlayerUUID");
        }

        if (tag.contains("LastPlayerX")) {
            this.lastKnownPlayerPos = new Vec3(
                    tag.getDouble("LastPlayerX"),
                    tag.getDouble("LastPlayerY"),
                    tag.getDouble("LastPlayerZ")
            );
        }

        // Load damage resistances
        if (tag.contains("DamageResistances")) {
            CompoundTag resistances = tag.getCompound("DamageResistances");
            damageResistances.clear();

            for (String key : resistances.getAllKeys()) {
                damageResistances.put(key, resistances.getFloat(key));
            }
        }

        if (tag.contains("TicksSinceLastPlayerSeen")) {
            this.ticksSinceLastPlayerSeen = tag.getInt("TicksSinceLastPlayerSeen");
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

    public int getDeathCount() {
        return this.entityData.get(DEATH_COUNT);
    }

    public String getLastDeathSource() {
        return this.entityData.get(LAST_DEATH_SOURCE);
    }

    // Custom AI Goals
    private static class HeadlessZombieMeleeAttackGoal extends MeleeAttackGoal {
        private final HeadlessZombieEntity headlessZombie;

        public HeadlessZombieMeleeAttackGoal(HeadlessZombieEntity zombie, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(zombie, speedModifier, followingTargetEvenIfNotSeen);
            this.headlessZombie = zombie;
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
            double attackReach = this.getAttackReachSqr(target);
            if (distToTargetSqr <= attackReach && this.isTimeToAttack()) {
                this.resetAttackCooldown();
                this.mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.mob.doHurtTarget(target);
            }
        }
    }

    private static class HeadlessZombieTrackingGoal extends Goal {
        private final HeadlessZombieEntity zombie;

        public HeadlessZombieTrackingGoal(HeadlessZombieEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
            return zombie.targetPlayerUUID != null || zombie.getTarget() == null;
        }

        @Override
        public void tick() {
            // This goal ensures the zombie always tries to path towards a player
            if (zombie.getTarget() != null) {
                double distance = zombie.distanceToSqr(zombie.getTarget());
                if (distance > 4.0) { // If more than 2 blocks away
                    zombie.getNavigation().moveTo(zombie.getTarget(), 1.2);
                }
            } else {
                // Find nearest player if we don't have a target
                if (zombie.level() instanceof ServerLevel serverLevel) {
                    zombie.findAndSetNearestPlayer(serverLevel);
                }
            }
        }
    }
}