package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "exadditions")
public class ProjectileEnchantmentEvents {

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Arrow arrow)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(arrow.getOwner() instanceof Player player)) return;

        CompoundTag arrowData = arrow.getPersistentData();

        // FROST ENCHANTMENT
        int frostLevel = arrowData.getInt("FrostLevel");
        if (frostLevel > 0) {
            // Apply slowness and freeze effects
            int duration = frostLevel * 40; // 2 seconds per level in ticks
            float slowAmount = frostLevel * 0.05f; // 5% per level

            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, frostLevel - 1));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, frostLevel - 1));

            // Freezing effect (reduce attack speed)
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), duration));

            // Visual effect
            if (!target.level().isClientSide) {
                ((ServerLevel) target.level()).sendParticles(ParticleTypes.SNOWFLAKE,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        10 * frostLevel, 0.3, 0.5, 0.3, 0.1);
            }
        }

        // CHANCE ENCHANTMENT (looting effect)
        int chanceLevel = arrowData.getInt("ChanceLevel");
        if (chanceLevel > 0) {
            // Store looting level on the target for when it dies
            CompoundTag targetData = target.getPersistentData();
            targetData.putInt("ChanceEnchantLevel", chanceLevel);
            targetData.putUUID("ChanceEnchantPlayer", player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag entityData = entity.getPersistentData();

        // CHANCE ENCHANTMENT - Apply looting effect for bow/crossbow kills
        if (entityData.contains("ChanceEnchantLevel")) {
            int chanceLevel = entityData.getInt("ChanceEnchantLevel");
            UUID playerUUID = entityData.getUUID("ChanceEnchantPlayer");

            // Verify the killer matches
            if (event.getSource().getEntity() instanceof Player player &&
                    player.getUUID().equals(playerUUID)) {

                // Apply looting effect to drops
                for (ItemEntity itemEntity : event.getDrops()) {
                    ItemStack stack = itemEntity.getItem();

                    // Increase stack size based on looting level (simplified)
                    if (entity.level().random.nextFloat() < (chanceLevel * 0.33f)) {
                        stack.setCount(stack.getCount() + 1);
                        itemEntity.setItem(stack);
                    }
                }
            }
        }

        // EXTRACT ENCHANTMENT - Bonus experience
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack weapon = player.getMainHandItem();
            int extractLevel = weapon.getEnchantmentLevel(ModEnchantments.EXTRACT.get());

            if (extractLevel > 0) {
                // Increase experience drops by 5% per level
                int bonusExp = Math.round(event.getDroppedExperience() * (extractLevel * 0.05f));
                event.setDroppedExperience(event.getDroppedExperience() + bonusExp);
            }
        }
    }

    // HOMING ENCHANTMENT - Update arrow trajectory
    @SubscribeEvent
    public static void onArrowTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Player player = event.player;

        // Find all arrows in the world that belong to this player and have homing
        List<Arrow> arrows = player.level().getEntitiesOfClass(Arrow.class,
                player.getBoundingBox().inflate(50.0),
                arrow -> arrow.getOwner() == player && arrow.getPersistentData().getBoolean("IsHoming"));

        for (Arrow arrow : arrows) {
            CompoundTag arrowData = arrow.getPersistentData();
            UUID targetUUID = arrowData.getUUID("HomingTarget");

            if (targetUUID != null) {
                // Find the target entity
                Entity targetEntity = ((ServerLevel) arrow.level()).getEntity(targetUUID);
                if (targetEntity instanceof LivingEntity target && target.isAlive()) {
                    // Calculate homing trajectory
                    Vec3 arrowPos = arrow.position();
                    Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
                    Vec3 direction = targetPos.subtract(arrowPos).normalize();

                    // Gradually adjust arrow velocity toward target
                    Vec3 currentVelocity = arrow.getDeltaMovement();
                    Vec3 newVelocity = currentVelocity.scale(0.9).add(direction.scale(0.1));

                    arrow.setDeltaMovement(newVelocity);

                    // Update arrow rotation to match new direction
                    arrow.setYRot((float)(Mth.atan2(newVelocity.x, newVelocity.z) * (double)(180F / (float)Math.PI)));
                    arrow.setXRot((float)(Mth.atan2(newVelocity.y, newVelocity.horizontalDistance()) * (double)(180F / (float)Math.PI)));

                    // Particle effect
                    if (arrow.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                arrowPos.x, arrowPos.y, arrowPos.z,
                                2, 0.1, 0.1, 0.1, 0.02);
                    }
                }
            }
        }
    }
}