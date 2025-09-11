package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.network.ElectricityPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Electric Wand - Creates chained lightning between nearby mobs
 * Updated to use raycast targeting and sequential mob-to-mob chaining
 */
public class ElectricWandItem extends Item {

    private static final double RAYCAST_DISTANCE = 20.0;
    private static final double CHAIN_RANGE = 5.0; // Changed to 5 blocks as requested
    private static final int MAX_TARGETS = 8;
    private static final int COOLDOWN_TICKS = 40;
    private static final int EFFECT_DURATION = 60;

    private static final float BASE_DAMAGE = 6.0f;
    private static final float CHAIN_DAMAGE_REDUCTION = 0.8f;
    private static final int STUN_DURATION = 20;

    public ElectricWandItem() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .durability(100)
        );
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level;
            List<LivingEntity> targets = findChainableTargetsWithRaycast(serverLevel, player);

            if (!targets.isEmpty()) {
                player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
                applyElectricEffects(serverLevel, player, targets);
                sendElectricityPacket(serverLevel, player, targets);

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                        0.8f, 1.2f + level.random.nextFloat() * 0.4f);

                return InteractionResultHolder.success(stack);
            }
        }

        return InteractionResultHolder.fail(stack);
    }

    /**
     * New method that uses raycast to find the initial target, then chains from mob to mob
     */
    private List<LivingEntity> findChainableTargetsWithRaycast(ServerLevel level, Player player) {
        // First, perform raycast to find the initial target
        LivingEntity initialTarget = findInitialTargetWithRaycast(level, player);

        if (initialTarget == null) {
            return Collections.emptyList();
        }

        // Now chain from the initial target to other nearby mobs
        List<LivingEntity> chainedTargets = new ArrayList<>();
        Set<LivingEntity> visited = new HashSet<>();

        // Add the initial target
        chainedTargets.add(initialTarget);
        visited.add(initialTarget);

        // Find all potential chain targets in the area
        Vec3 searchCenter = initialTarget.position();
        AABB searchBox = new AABB(searchCenter, searchCenter).inflate(CHAIN_RANGE * 2); // Search in larger area

        List<LivingEntity> potentialTargets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity != player && entity != initialTarget && entity.isAlive());

        // Chain from mob to mob
        LivingEntity currentTarget = initialTarget;

        while (chainedTargets.size() < MAX_TARGETS && potentialTargets.size() > 0) {
            LivingEntity nextTarget = findNearestUnvisitedTarget(currentTarget, potentialTargets, visited);

            if (nextTarget == null || currentTarget.distanceTo(nextTarget) > CHAIN_RANGE) {
                break; // No more valid targets within range
            }

            chainedTargets.add(nextTarget);
            visited.add(nextTarget);
            potentialTargets.remove(nextTarget); // Remove from potential targets
            currentTarget = nextTarget; // Move to the next target in the chain
        }

        return chainedTargets;
    }

    /**
     * Performs a raycast from the player's look direction to find the initial target
     */
    private LivingEntity findInitialTargetWithRaycast(ServerLevel level, Player player) {
        // Get player's look direction
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(RAYCAST_DISTANCE));

        // First check for block collision to limit our search
        ClipContext clipContext = new ClipContext(
                eyePos,
                endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        );

        BlockHitResult blockHit = level.clip(clipContext);
        if (blockHit.getType() != HitResult.Type.MISS) {
            endPos = blockHit.getLocation(); // Limit raycast to where we hit a block
        }

        // Create a bounding box along the raycast path to find entities
        AABB raycastBox = new AABB(eyePos, endPos).inflate(1.0); // 1 block tolerance

        List<LivingEntity> entitiesInPath = level.getEntitiesOfClass(LivingEntity.class, raycastBox,
                entity -> entity != player && entity.isAlive());

        // Find the closest entity to the raycast line
        LivingEntity closestEntity = null;
        double closestDistance = Double.MAX_VALUE;

        for (LivingEntity entity : entitiesInPath) {
            // Calculate distance from entity to the raycast line
            Vec3 entityPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0); // Entity center
            double distanceToRay = distancePointToLine(entityPos, eyePos, endPos);
            double distanceToPlayer = player.distanceTo(entity);

            // Prioritize entities closer to the raycast line and closer to the player
            double score = distanceToRay + (distanceToPlayer * 0.1); // Weight distance to ray more heavily

            if (score < closestDistance && distanceToRay <= 2.0) { // Must be within 2 blocks of raycast line
                closestDistance = score;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }

    /**
     * Finds the nearest unvisited target to the current target
     */
    private LivingEntity findNearestUnvisitedTarget(LivingEntity currentTarget, List<LivingEntity> potentialTargets, Set<LivingEntity> visited) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (LivingEntity target : potentialTargets) {
            if (!visited.contains(target)) {
                double distance = currentTarget.distanceTo(target);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = target;
                }
            }
        }

        return nearest;
    }

    /**
     * Calculates the distance from a point to a line segment
     */
    private double distancePointToLine(Vec3 point, Vec3 lineStart, Vec3 lineEnd) {
        Vec3 lineVec = lineEnd.subtract(lineStart);
        Vec3 pointVec = point.subtract(lineStart);

        double lineLength = lineVec.length();
        if (lineLength == 0) {
            return point.distanceTo(lineStart);
        }

        // Project point onto line
        double t = Math.max(0, Math.min(1, pointVec.dot(lineVec) / (lineLength * lineLength)));
        Vec3 projection = lineStart.add(lineVec.scale(t));

        return point.distanceTo(projection);
    }

    private void applyElectricEffects(ServerLevel level, Player player, List<LivingEntity> targets) {
        // Choose which DamageSource you want:
        // - Attributed to player: mobAttack(player) or playerAttack(player)
        // - Vanilla lightning style (not attributed): lightningBolt()
        DamageSource electricDamage = level.damageSources().mobAttack(player);

        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            float damage = BASE_DAMAGE * (float) Math.pow(CHAIN_DAMAGE_REDUCTION, i);
            target.hurt(electricDamage, damage);

            if (target.isAlive()) {
                Vec3 knockback = target.position().subtract(player.position()).normalize().scale(0.3);
                target.setDeltaMovement(target.getDeltaMovement().add(knockback.x, 0.1, knockback.z));
                // mark as hurt so client-side damage effects show
                target.hurtMarked = true;
            }
        }
    }

    private void sendElectricityPacket(ServerLevel level, Player player, List<LivingEntity> targets) {
        List<Integer> targetIds = new ArrayList<>(targets.size());
        for (LivingEntity t : targets) targetIds.add(t.getId());

        ElectricityPacket packet = new ElectricityPacket(player.getId(), targetIds, EFFECT_DURATION);

        PacketDistributor.TargetPoint targetPoint = new PacketDistributor.TargetPoint(
                player.getX(), player.getY(), player.getZ(), 64.0, level.dimension()
        );

        ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> targetPoint), packet);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == net.minecraft.world.item.Items.DIAMOND ||
                repair.getItem() == net.minecraft.world.item.Items.NETHERITE_INGOT;
    }
}