package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.EnumSet;

public class AIHijackerItem extends Item {

    public AIHijackerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();

        // Don't hijack players
        if (target instanceof Player) {
            return InteractionResult.PASS;
        }

        // Don't hijack non-mob entities
        if (!(target instanceof Mob)) {
            return InteractionResult.PASS;
        }

        // safe cast now that we've checked the type
        Mob mob = (Mob) target;

        // Check if already hijacked by this player
        if (isHijackedByPlayer(mob, player.getUUID())) {
            // Show "already controlled" particles (red)
            if (level instanceof ServerLevel serverLevel) {
                showFailureParticles(serverLevel, target.position());
            }
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            // Success particles (green hearts and sparkles)
            if (level instanceof ServerLevel serverLevel) {
                showSuccessParticles(serverLevel, target.position());
            }

            // Play conversion sound
            level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.PLAYERS, 1.0F, 1.5F);

            // Hijack the mob's AI
            hijackMobAI(mob, player);

            // Mark the mob as hijacked by this player
            markAsHijacked(mob, player.getUUID());

            // Damage the item (consumes durability)
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));

            // Play success sound
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 1.0F, 1.8F);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player) {
            // Try to hijack the target
            InteractionResult result = interactLivingEntity(stack, player, target, player.getUsedItemHand());

            // If hijacking was successful, cancel the damage
            if (result == InteractionResult.SUCCESS) {
                return false; // Don't deal damage
            }
        }
        return true; // Allow normal damage if hijacking failed
    }

    private void hijackMobAI(Mob mob, Player owner) {
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);

        // Basic goals
        mob.goalSelector.addGoal(1, new FloatGoal(mob));
        mob.goalSelector.addGoal(2, new FollowOwnerGoal(mob, owner, 1.0D, 10.0F, 2.0F, false));

        // Combat goals - attack what owner attacks and defend owner
        mob.targetSelector.addGoal(1, new OwnerHurtTargetGoalImpl(mob, owner));
        mob.targetSelector.addGoal(2, new OwnerHurtByTargetGoalImpl(mob, owner));

        // Add attack behavior for passive mobs using TauntEffect pattern
        if (mob instanceof PathfinderMob pathfinder) {
            mob.goalSelector.addGoal(3, new HijackedAttackGoal(pathfinder, owner));
            mob.goalSelector.addGoal(8, new RandomStrollGoal(pathfinder, 1.0D));
        } else {
            mob.goalSelector.addGoal(8, new RandomLookAroundGoal(mob));
        }

        mob.goalSelector.addGoal(9, new LookAtPlayerGoal(mob, Player.class, 8.0F));
        mob.goalSelector.addGoal(10, new RandomLookAroundGoal(mob));

        // Use mapping-friendly glowing call
        mob.setGlowingTag(true);
    }

    private void markAsHijacked(Mob mob, UUID playerUUID) {
        CompoundTag persistentData = mob.getPersistentData();
        persistentData.putString("ExAdditions_HijackedBy", playerUUID.toString());
        persistentData.putLong("ExAdditions_HijackedTime", mob.level().getGameTime());
    }

    private boolean isHijackedByPlayer(Mob mob, UUID playerUUID) {
        CompoundTag persistentData = mob.getPersistentData();
        if (persistentData.contains("ExAdditions_HijackedBy")) {
            String hijackerUUID = persistentData.getString("ExAdditions_HijackedBy");
            return hijackerUUID.equals(playerUUID.toString());
        }
        return false;
    }

    // Check if a mob is hijacked by any player
    public static boolean isHijackedByAnyPlayer(Mob mob) {
        CompoundTag persistentData = mob.getPersistentData();
        return persistentData.contains("ExAdditions_HijackedBy");
    }

    // Get the owner of a hijacked mob
    public static UUID getHijackOwner(Mob mob) {
        CompoundTag persistentData = mob.getPersistentData();
        if (persistentData.contains("ExAdditions_HijackedBy")) {
            return UUID.fromString(persistentData.getString("ExAdditions_HijackedBy"));
        }
        return null;
    }

    private void showSuccessParticles(ServerLevel level, Vec3 pos) {
        var random = level.getRandom();

        for (int i = 0; i < 15; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 2.0;
            double offsetY = random.nextDouble() * 2.0;
            double offsetZ = (random.nextDouble() - 0.5) * 2.0;

            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0.0, 0.1, 0.0, 0.1);
        }

        for (int i = 0; i < 5; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 1.5;
            double offsetY = random.nextDouble() * 1.5 + 0.5;
            double offsetZ = (random.nextDouble() - 0.5) * 1.5;

            level.sendParticles(ParticleTypes.HEART,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0.0, 0.1, 0.0, 0.0);
        }
    }

    private void showFailureParticles(ServerLevel level, Vec3 pos) {
        var random = level.getRandom();

        for (int i = 0; i < 10; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 1.5;
            double offsetY = random.nextDouble() * 1.5;
            double offsetZ = (random.nextDouble() - 0.5) * 1.5;

            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0.0, 0.1, 0.0, 0.0);
        }
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level,
                                  net.minecraft.core.BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == net.autismicannoyance.exadditions.item.ModItems.CHAOS_CRYSTAL.get() ||
                repair.getItem() == net.autismicannoyance.exadditions.item.ModItems.SAPPHIRE.get();
    }

    // -------------------------
    // Inner goal classes
    // -------------------------

    private static class FollowOwnerGoal extends Goal {
        private final Mob mob;
        private final Player owner;
        private final double speedModifier;
        private final float stopDistance;
        private final float startDistance;
        private final boolean canFly;
        private int timeToRecalcPath;

        public FollowOwnerGoal(Mob mob, Player owner, double speedModifier, float startDistance, float stopDistance, boolean canFly) {
            this.mob = mob;
            this.owner = owner;
            this.speedModifier = speedModifier;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.canFly = canFly;
        }

        @Override
        public boolean canUse() {
            if (owner == null || owner.isSpectator() || mob.distanceToSqr(owner) < (double)(startDistance * startDistance)) {
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone() && mob.distanceToSqr(owner) > (double)(stopDistance * stopDistance);
        }

        @Override
        public void start() {
            timeToRecalcPath = 0;
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(owner, 10.0F, (float)mob.getMaxHeadXRot());
            if (--timeToRecalcPath <= 0) {
                timeToRecalcPath = 10;
                mob.getNavigation().moveTo(owner, speedModifier);
            }
        }
    }

    private static class OwnerHurtTargetGoalImpl extends TargetGoal {
        private final Mob mob;
        private final Player owner;
        private LivingEntity ownerLastHurt;
        private int timestamp;

        public OwnerHurtTargetGoalImpl(Mob mob, Player owner) {
            super(mob, false);
            this.mob = mob;
            this.owner = owner;
        }

        @Override
        public boolean canUse() {
            if (owner == null) return false;

            ownerLastHurt = owner.getLastHurtMob();
            int i = owner.getLastHurtMobTimestamp();
            if (ownerLastHurt == null) return false;

            // Don't attack other hijacked mobs from the same owner
            if (ownerLastHurt instanceof Mob targetMob && isHijackedByAnyPlayer(targetMob)) {
                UUID targetOwner = getHijackOwner(targetMob);
                if (targetOwner != null && targetOwner.equals(owner.getUUID())) {
                    return false;
                }
            }

            return i != timestamp && this.canAttack(ownerLastHurt, TargetingConditions.forCombat());
        }

        @Override
        public void start() {
            mob.setTarget(ownerLastHurt);
            timestamp = owner.getLastHurtMobTimestamp();
            super.start();
        }
    }

    private static class OwnerHurtByTargetGoalImpl extends TargetGoal {
        private final Mob mob;
        private final Player owner;
        private LivingEntity ownerLastHurtBy;
        private int timestamp;

        public OwnerHurtByTargetGoalImpl(Mob mob, Player owner) {
            super(mob, false);
            this.mob = mob;
            this.owner = owner;
        }

        @Override
        public boolean canUse() {
            if (owner == null) return false;

            ownerLastHurtBy = owner.getLastHurtByMob();
            int i = owner.getLastHurtByMobTimestamp();
            if (ownerLastHurtBy == null) return false;

            // Don't attack other hijacked mobs from the same owner
            if (ownerLastHurtBy instanceof Mob attackerMob && isHijackedByAnyPlayer(attackerMob)) {
                UUID attackerOwner = getHijackOwner(attackerMob);
                if (attackerOwner != null && attackerOwner.equals(owner.getUUID())) {
                    return false;
                }
            }

            return i != timestamp && this.canAttack(ownerLastHurtBy, TargetingConditions.forCombat());
        }

        @Override
        public void start() {
            mob.setTarget(ownerLastHurtBy);
            timestamp = owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    // Attack goal that works for passive mobs, based on your TauntEffect pattern
    private static class HijackedAttackGoal extends Goal {
        private final PathfinderMob mob;
        private final Player owner;
        private int cooldown;

        public HijackedAttackGoal(PathfinderMob mob, Player owner) {
            this.mob = mob;
            this.owner = owner;
            this.cooldown = 0;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() != null && mob.getTarget().isAlive() && mob.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) return;

            // Don't attack other hijacked mobs from the same owner
            if (target instanceof Mob targetMob && isHijackedByAnyPlayer(targetMob)) {
                UUID targetOwner = getHijackOwner(targetMob);
                if (targetOwner != null && targetOwner.equals(owner.getUUID())) {
                    mob.setTarget(null);
                    return;
                }
            }

            PathNavigation nav = mob.getNavigation();
            if (nav != null) nav.moveTo(target, 1.2);

            mob.getLookControl().setLookAt(target, 30, 30);

            if (cooldown > 0) cooldown--;

            double distSq = mob.distanceToSqr(target);
            double reachSq = (mob.getBbWidth() * 2.0 + target.getBbWidth()) * (mob.getBbWidth() * 2.0 + target.getBbWidth());

            if (distSq <= reachSq && cooldown <= 0) {
                cooldown = 20; // 1s cooldown

                float damage = safeGetAttackDamage(mob);
                target.hurt(mob.damageSources().mobAttack(mob), damage);
                mob.swing(InteractionHand.MAIN_HAND);

                if (mob.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.CRIT,
                            mob.getX(), mob.getY() + 1.0, mob.getZ(),
                            3, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }

        private float safeGetAttackDamage(Mob mob) {
            try {
                AttributeInstance inst = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                if (inst != null && inst.getValue() >= 0) {
                    return (float) inst.getValue();
                }
            } catch (Exception ignored) {}
            return 2.0f; // Default damage for passive mobs
        }
    }
}