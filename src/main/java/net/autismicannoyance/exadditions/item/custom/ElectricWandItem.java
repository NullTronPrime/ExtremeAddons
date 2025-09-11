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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Electric Wand - Creates chained lightning between nearby mobs
 */
public class ElectricWandItem extends Item {

    private static final double DETECTION_RADIUS = 12.0;
    private static final double CHAIN_RANGE = 6.0;
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
            List<LivingEntity> targets = findChainableTargets(serverLevel, player);

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

    private List<LivingEntity> findChainableTargets(ServerLevel level, Player player) {
        Vec3 playerPos = player.position();

        // Use the Vec3-to-Vec3 AABB constructor then inflate
        AABB searchBox = new AABB(playerPos, playerPos).inflate(DETECTION_RADIUS);

        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity != player && entity.isAlive());

        if (nearbyEntities.isEmpty()) return Collections.emptyList();

        List<LivingEntity> chainedTargets = new ArrayList<>();
        Set<LivingEntity> visited = new HashSet<>();

        LivingEntity startEntity = nearbyEntities.stream()
                .min(Comparator.comparingDouble(a -> a.distanceToSqr(player)))
                .orElse(null);

        if (startEntity != null) {
            Queue<LivingEntity> queue = new LinkedList<>();
            queue.offer(startEntity);
            visited.add(startEntity);
            chainedTargets.add(startEntity);

            while (!queue.isEmpty() && chainedTargets.size() < MAX_TARGETS) {
                LivingEntity current = queue.poll();

                for (LivingEntity candidate : nearbyEntities) {
                    if (!visited.contains(candidate)
                            && current.distanceTo(candidate) <= CHAIN_RANGE
                            && chainedTargets.size() < MAX_TARGETS) {

                        visited.add(candidate);
                        chainedTargets.add(candidate);
                        queue.offer(candidate);
                    }
                }
            }
        }

        return chainedTargets;
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
