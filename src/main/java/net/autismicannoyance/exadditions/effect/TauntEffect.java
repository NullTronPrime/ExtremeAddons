package net.autismicannoyance.exadditions.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.*;

public class TauntEffect extends MobEffect {

    private static final Set<UUID> modifiedMobs = new HashSet<>();
    private static final Map<UUID, SimpleAttackGoal> addedGoals = new HashMap<>();
    private static final Map<UUID, TauntTarget> mobTauntTargets = new HashMap<>();

    public TauntEffect() {
        super(MobEffectCategory.HARMFUL, 0xff3300);
    }

    @Override
    public void applyEffectTick(LivingEntity taunted, int amplifier) {
        Level world = taunted.level();
        if (!(world instanceof ServerLevel serverLevel)) return;

        // Creative players are skipped entirely
        if (taunted instanceof Player p && p.isCreative()) return;

        double radius = 10.0 + amplifier * 5.0;
        AABB area = new AABB(
                taunted.getX() - radius, taunted.getY() - radius, taunted.getZ() - radius,
                taunted.getX() + radius, taunted.getY() + radius, taunted.getZ() + radius
        );

        for (LivingEntity near : serverLevel.getEntitiesOfClass(LivingEntity.class, area, e -> e != taunted && e.isAlive())) {
            if (!(near instanceof Mob mob)) continue;

            if (!modifiedMobs.contains(mob.getUUID()) && mob instanceof PathfinderMob pmob) {
                SimpleAttackGoal goal = new SimpleAttackGoal(pmob, taunted, amplifier);
                pmob.goalSelector.addGoal(0, goal);
                addedGoals.put(mob.getUUID(), goal);
                modifiedMobs.add(mob.getUUID());
            }

            considerTargeting(mob, taunted, amplifier, serverLevel, radius);
        }
    }

    private void considerTargeting(Mob mob, LivingEntity taunted, int amplifier, ServerLevel server, double tauntRadius) {
        TauntTarget current = mobTauntTargets.get(mob.getUUID());
        double newDistSq = mob.distanceToSqr(taunted);

        if (shouldReplaceTarget(current, amplifier, newDistSq)) {
            mob.setTarget(taunted);
            mobTauntTargets.put(mob.getUUID(), new TauntTarget(taunted, amplifier, tauntRadius));
        }

        double provokeChance = 0.1 + amplifier * 0.05; // stronger -> more visuals
        if (server.random.nextDouble() < provokeChance) {
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    mob.getX(), mob.getY() + 1.0, mob.getZ(),
                    1, 0.2, 0.2, 0.2, 0.0);
        }
    }

    private boolean shouldReplaceTarget(TauntTarget current, int newAmp, double newDistSq) {
        if (current == null || !current.target.isAlive()) return true;
        if (current.target instanceof Player p && p.isCreative()) return true;
        if (newAmp > current.amplifier) return true;
        if (newAmp < current.amplifier) return false;
        double currentDistSq = current.target.distanceToSqr(current.target);
        return newDistSq < currentDistSq;
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public void removeAttributeModifiers(LivingEntity taunted, net.minecraft.world.entity.ai.attributes.AttributeMap attributes, int amplifier) {
        super.removeAttributeModifiers(taunted, attributes, amplifier);

        Level world = taunted.level();
        if (!(world instanceof ServerLevel serverLevel)) return;

        double radius = 64.0;
        AABB area = new AABB(
                taunted.getX() - radius, taunted.getY() - radius, taunted.getZ() - radius,
                taunted.getX() + radius, taunted.getY() + radius, taunted.getZ() + radius
        );

        for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, area)) {
            if (mob.getTarget() == taunted) {
                mob.setTarget(null);
            }
            if (modifiedMobs.remove(mob.getUUID())) {
                SimpleAttackGoal goal = addedGoals.remove(mob.getUUID());
                if (goal != null) mob.goalSelector.removeGoal(goal);
            }
            mobTauntTargets.remove(mob.getUUID());
        }
    }

    private static class SimpleAttackGoal extends Goal {
        private final PathfinderMob mob;
        private final LivingEntity taunted;
        private final int amplifier;
        private int cooldown;

        public SimpleAttackGoal(PathfinderMob mob, LivingEntity taunted, int amplifier) {
            this.mob = mob;
            this.taunted = taunted;
            this.amplifier = amplifier;
            this.cooldown = 0;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return taunted.isAlive() && mob.isAlive();
        }

        @Override
        public void tick() {
            if (!taunted.isAlive()) return;

            PathNavigation nav = mob.getNavigation();
            if (nav != null) nav.moveTo(taunted, 1.2);

            mob.getLookControl().setLookAt(taunted, 30, 30);

            if (cooldown > 0) cooldown--;

            double distSq = mob.distanceToSqr(taunted);
            double reachSq = (mob.getBbWidth() * 2.0 + taunted.getBbWidth()) * (mob.getBbWidth() * 2.0 + taunted.getBbWidth());

            if (distSq <= reachSq && cooldown <= 0) {
                cooldown = 20; // 1s cooldown

                float damage = safeGetAttackDamage(mob);
                taunted.hurt(mob.damageSources().mobAttack(mob), damage);
                mob.swing(InteractionHand.MAIN_HAND);

                if (mob.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            mob.getX(), mob.getY() + 1.0, mob.getZ(),
                            2, 0.1, 0.1, 0.1, 0.0);
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
            return 2.0f;
        }
    }

    private record TauntTarget(LivingEntity target, int amplifier, double tauntRadius) {}
}
