package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.EchoBeamPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Echo Rifle - A powerful weapon inspired by the Deep Dark and sculk mechanics.
 * Shoots wide echo beams that deal 20 damage + 5% of target's max health and apply darkness.
 */
public class EchoRifleItem extends Item {
    private static final int COOLDOWN_TICKS = 40; // 2 seconds
    private static final double BEAM_RANGE = 32.0;
    private static final double BEAM_WIDTH = 3.0;
    private static final float BASE_DAMAGE = 20.0f;
    private static final float HEALTH_PERCENTAGE = 0.05f; // 5% of max health
    private static final int DARKNESS_DURATION = 400; // 20 seconds (20 * 20 ticks)

    public EchoRifleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemStack);
        }

        if (!level.isClientSide) {
            fireEchoBeam(level, player, itemStack);

            // Apply cooldown
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

            // Damage the item
            itemStack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    private void fireEchoBeam(Level level, Player player, ItemStack itemStack) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(BEAM_RANGE));

        // Play sculk-themed sound
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 1.0f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.6f, 1.2f);

        // Find all entities within the beam's path and width
        List<LivingEntity> hitEntities = findEntitiesInBeam(level, start, end, BEAM_WIDTH, player);

        // Damage all hit entities
        for (LivingEntity entity : hitEntities) {
            damageEntity(entity);
        }

        // Send packet to clients for visual effect
        EchoBeamPacket packet = new EchoBeamPacket(
                start,
                end,
                BEAM_WIDTH,
                hitEntities.size()
        );

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                packet
        );

        // Create sculk-like particle effects around the shooter
        spawnShooterEffects(level, player);
    }

    private List<LivingEntity> findEntitiesInBeam(Level level, Vec3 start, Vec3 end, double width, Entity shooter) {
        // Create expanded bounding box for the beam
        AABB beamBounds = new AABB(start, end).inflate(width);

        return level.getEntitiesOfClass(LivingEntity.class, beamBounds, entity -> {
            if (entity == shooter) return false;
            if (!entity.isAlive()) return false;

            // Check if entity is actually within the beam cylinder
            return isEntityInBeamCylinder(start, end, width, entity.getBoundingBox().getCenter());
        });
    }

    private boolean isEntityInBeamCylinder(Vec3 start, Vec3 end, double width, Vec3 point) {
        Vec3 beamDirection = end.subtract(start).normalize();
        Vec3 toPoint = point.subtract(start);

        // Project point onto beam line
        double projectionLength = toPoint.dot(beamDirection);

        // Check if projection is within beam length
        if (projectionLength < 0 || projectionLength > start.distanceTo(end)) {
            return false;
        }

        // Find closest point on beam line
        Vec3 closestPoint = start.add(beamDirection.scale(projectionLength));

        // Check distance from point to beam line
        double distanceToBeam = point.distanceTo(closestPoint);
        return distanceToBeam <= width / 2.0;
    }

    private void damageEntity(LivingEntity entity) {
        // Calculate damage: base damage + percentage of max health
        float maxHealth = entity.getMaxHealth();
        float percentageDamage = maxHealth * HEALTH_PERCENTAGE;
        float totalDamage = BASE_DAMAGE + percentageDamage;

        // Apply damage
        entity.hurt(entity.damageSources().magic(), totalDamage);

        // Apply darkness effect (20 seconds)
        MobEffectInstance darknessEffect = new MobEffectInstance(
                MobEffects.DARKNESS,
                DARKNESS_DURATION,
                0, // amplifier
                false, // ambient
                true,  // visible
                true   // show icon
        );
        entity.addEffect(darknessEffect);

        // Also apply brief blindness for immediate disorientation
        MobEffectInstance blindnessEffect = new MobEffectInstance(
                MobEffects.BLINDNESS,
                60, // 3 seconds
                0,
                false,
                true,
                true
        );
        entity.addEffect(blindnessEffect);
    }

    private void spawnShooterEffects(Level level, Player player) {
        // Create sculk-like vibrations around the shooter
        BlockPos playerPos = player.blockPosition();

        for (int i = 0; i < 5; i++) {
            BlockPos randomPos = playerPos.offset(
                    level.random.nextInt(3) - 1,
                    level.random.nextInt(2),
                    level.random.nextInt(3) - 1
            );

            // Spawn sculk-themed particles if available
            // This creates ambient effects around the shooter
            if (level.random.nextFloat() < 0.7f) {
                level.playLocalSound(
                        randomPos.getX(), randomPos.getY(), randomPos.getZ(),
                        SoundEvents.SCULK_CATALYST_BLOOM,
                        SoundSource.BLOCKS,
                        0.3f, 1.5f, false
                );
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Give it an enchanted glow to make it look more mystical
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 0; // Instant use
    }
}