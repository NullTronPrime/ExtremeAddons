package net.autismicannoyance.exadditions.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import java.util.List;
import java.util.Random;

public class EyeOfSauronItem extends Item {

    private final Random random = new Random();

    public EyeOfSauronItem(Properties props) {
        super(props.durability(500));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseTicks) {
        if (!(living instanceof Player player)) return;

        int usedTicks = getUseDuration(stack) - remainingUseTicks;
        if (usedTicks < 10) return;

        float progress = Math.min(1.0f, usedTicks / 60f);
        double maxLen = 4 + (28 * progress);

        Vec3 eyePos = player.getEyePosition().add(0, -0.5, 0); // beam starts lower
        Vec3 look = player.getLookAngle().normalize();
        Vec3 endVec = eyePos.add(look.scale(maxLen));

        // Block collision
        ClipContext ctx = new ClipContext(eyePos, endVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = player.level().clip(ctx);

        double length = maxLen;
        boolean soulFirePhase = false;

        if (hit.getType() != HitResult.Type.MISS) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = player.level().getBlockState(pos);

            if (!(state.getBlock() instanceof GlassBlock)) {
                length = eyePos.distanceTo(hit.getLocation());
                endVec = hit.getLocation();
                soulFirePhase = true;
            }
        } else if (length >= 31.5) {
            soulFirePhase = true;
        }

        // Client: particles & screenshake
        if (level.isClientSide) {
            spawnExpandingHelixParticles(level, player, eyePos, look, length, soulFirePhase, usedTicks);

            if (progress >= 1.0f) {
                applyScreenShake(player);
            }
            return;
        }

        // Durability drain
        if (!player.isCreative()) {
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
        }

        // Damage ramp
        float baseDamage = Mth.lerp((float)(length / 32.0), 1.0F, 8.0F);
        if (soulFirePhase) baseDamage *= 2.0F;

        double maxRadius = getRadius(length);
        AABB damageBox = new AABB(eyePos, endVec).inflate(maxRadius);

        List<Entity> entities = player.level().getEntities(player, damageBox,
                e -> e instanceof LivingEntity && e != player);

        for (Entity e : entities) {
            Vec3 toEntity = e.position().subtract(eyePos);
            double along = toEntity.dot(look);
            if (along < 0 || along > length) continue;

            double radiusAtPoint = getRadius(along);
            Vec3 closestPoint = eyePos.add(look.scale(along));
            if (e.position().distanceTo(closestPoint) <= radiusAtPoint + e.getBbWidth() / 2) {
                e.hurt(level.damageSources().inFire(), baseDamage);

                Vec3 knock = look.scale(0.5);
                e.push(knock.x, knock.y, knock.z);

                if (e instanceof LivingEntity le) {
                    le.setSecondsOnFire(3);
                }
            }
        }
    }

    // Non-linear radius growth: slow → fast
    private double getRadius(double dist) {
        double maxDist = 32.0;
        double t = dist / maxDist;
        return 0.1 + (12.0 - 0.1) * Math.pow(t, 2.0); // quadratic growth
    }

    private void spawnExpandingHelixParticles(Level level, Player player, Vec3 start, Vec3 look,
                                              double length, boolean soulFirePhase, int usedTicks) {
        ParticleOptions particle = soulFirePhase ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;

        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(look.dot(up)) > 0.9) {
            up = new Vec3(1, 0, 0);
        }
        Vec3 right = look.cross(up).normalize();
        up = right.cross(look).normalize();

        int steps = (int)(length * 4);
        double helixPitch = 0.5;

        for (int i = 0; i < steps; i++) {
            double dist = i * (length / steps);
            Vec3 base = start.add(look.scale(dist));

            double radius = getRadius(dist);
            double angle = (i * helixPitch) + (usedTicks * 0.2);

            // helix 1
            Vec3 offset1 = right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            level.addParticle(particle, base.x + offset1.x, base.y + offset1.y, base.z + offset1.z, 0, 0, 0);

            // helix 2
            Vec3 offset2 = right.scale(Math.cos(angle + Math.PI) * radius).add(up.scale(Math.sin(angle + Math.PI) * radius));
            level.addParticle(particle, base.x + offset2.x, base.y + offset2.y, base.z + offset2.z, 0, 0, 0);
        }
    }

    // Small randomized screen shake for max charge
    private void applyScreenShake(Player player) {
        float yawShake = (random.nextFloat() - 0.5f) * 2.0f;   // ±1°
        float pitchShake = (random.nextFloat() - 0.5f) * 2.0f;

        player.setYRot(player.getYRot() + yawShake);
        player.setXRot(Mth.clamp(player.getXRot() + pitchShake, -90, 90));
    }
}
