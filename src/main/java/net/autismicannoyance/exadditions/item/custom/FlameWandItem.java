package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.FlameJetPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class FlameWandItem extends Item {

    private static final int MAX_USE_DURATION = 100;
    private static final double FLAME_RANGE = 12.0;
    private static final double FLAME_WIDTH = 1.5;
    private static final float DAMAGE_PER_TICK = 1.5f;
    private static final int FLAME_CORE_COLOR = 0xFFFF4500;
    private static final int FLAME_OUTER_COLOR = 0xFFFFD700;

    public FlameWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;

        int useTicks = MAX_USE_DURATION - remainingUseDuration;

        if (useTicks % 2 == 0) {
            Vec3 eyePos = player.getEyePosition();
            Vec3 lookDir = player.getLookAngle();
            Vec3 startPos = eyePos.add(lookDir.scale(0.5));
            Vec3 endPos = eyePos.add(lookDir.scale(FLAME_RANGE));

            if (!level.isClientSide) {
                ServerLevel serverLevel = (ServerLevel) level;

                FlameJetPacket packet = new FlameJetPacket(
                        startPos, endPos, FLAME_WIDTH,
                        FLAME_CORE_COLOR, FLAME_OUTER_COLOR,
                        20 + useTicks / 5
                );

                ModNetworking.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                        packet
                );

                damageEntitiesInFlameJet(serverLevel, player, startPos, endPos);

                if (useTicks % 8 == 0) {
                    level.playSound(null, player.blockPosition(),
                            SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                            0.8f, 0.8f + level.random.nextFloat() * 0.4f);
                }

                spawnServerParticles(serverLevel, startPos, endPos);
            }
        }
    }

    private void damageEntitiesInFlameJet(ServerLevel level, Player caster, Vec3 start, Vec3 end) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        Vec3 normalizedDir = direction.normalize();

        int segments = (int)(length / 0.5) + 1;

        for (int i = 0; i < segments; i++) {
            double t = (double) i / Math.max(1, segments - 1);
            Vec3 segmentCenter = start.add(normalizedDir.scale(length * t));

            double segmentRadius = FLAME_WIDTH * 0.5 * (1.0 - t * 0.3);

            AABB searchBox = new AABB(segmentCenter.subtract(segmentRadius, segmentRadius, segmentRadius),
                    segmentCenter.add(segmentRadius, segmentRadius, segmentRadius));

            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> entity != caster && entity.distanceToSqr(segmentCenter) <= segmentRadius * segmentRadius);

            for (LivingEntity target : entities) {
                if (target.hurt(level.damageSources().playerAttack(caster), DAMAGE_PER_TICK)) {
                    target.setSecondsOnFire(3);

                    Vec3 knockback = target.position().subtract(segmentCenter).normalize().scale(0.2);
                    target.setDeltaMovement(target.getDeltaMovement().add(knockback));
                }
            }
        }
    }

    private void spawnServerParticles(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        Vec3 normalizedDir = direction.normalize();

        int particleCount = (int)(length * 3);

        for (int i = 0; i < particleCount; i++) {
            double t = level.random.nextDouble();
            Vec3 particlePos = start.add(normalizedDir.scale(length * t));

            double radius = FLAME_WIDTH * 0.5 * (1.0 - t * 0.2);
            double angle = level.random.nextDouble() * 2 * Math.PI;
            double distance = level.random.nextDouble() * radius;

            particlePos = particlePos.add(
                    Math.cos(angle) * distance,
                    (level.random.nextDouble() - 0.5) * radius * 0.5,
                    Math.sin(angle) * distance
            );

            level.sendParticles(ParticleTypes.FLAME,
                    particlePos.x, particlePos.y, particlePos.z,
                    1, 0.0, 0.1, 0.0, 0.02);

            if (level.random.nextFloat() < 0.3f) {
                level.sendParticles(ParticleTypes.SMOKE,
                        particlePos.x, particlePos.y, particlePos.z,
                        1, 0.0, 0.1, 0.0, 0.01);
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide && entity instanceof Player player) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                    0.5f, 1.2f);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return MAX_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}