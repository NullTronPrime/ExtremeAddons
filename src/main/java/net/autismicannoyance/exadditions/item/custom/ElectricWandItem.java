package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.client.ElectricityRenderer;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Electric Wand - Creates storm clouds above player that strike nearby mobs with chained lightning
 * Updated to spawn clouds 3-5 blocks above player and detect mobs within range
 */
public class ElectricWandItem extends Item {

    private static final double CLOUD_HEIGHT_MIN = 3.0;
    private static final double CLOUD_HEIGHT_MAX = 5.0;
    private static final double MOB_DETECTION_RANGE = 6.0; // Range from cloud to detect mobs
    private static final double CHAIN_RANGE = 5.0; // Range for chaining between mobs
    private static final int MAX_TARGETS = 8;
    private static final int COOLDOWN_TICKS = 60; // Longer cooldown for cloud mode
    private static final int EFFECT_DURATION = 100; // Longer duration for cloud effects
    private static final int CLOUD_DURATION = 200; // How long clouds persist

    private static final float BASE_DAMAGE = 6.0f;
    private static final float CHAIN_DAMAGE_REDUCTION = 0.8f;
    private static final int STUN_DURATION = 20;

    // Track active storm clouds per player
    private static final Map<UUID, StormCloud> activeStormClouds = new HashMap<>();

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

            // Create or refresh storm cloud above player
            createStormCloud(serverLevel, player, stack, hand);

            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

            // Play thunder sound for cloud creation
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                    0.5f, 0.8f + level.random.nextFloat() * 0.4f);

            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.fail(stack);
    }

    /**
     * Creates a storm cloud above the player
     */
    private void createStormCloud(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
        UUID playerId = player.getUUID();

        // Remove existing storm cloud if present
        activeStormClouds.remove(playerId);

        // Calculate cloud position (3-5 blocks above player with some randomness)
        double cloudHeight = CLOUD_HEIGHT_MIN + level.random.nextDouble() * (CLOUD_HEIGHT_MAX - CLOUD_HEIGHT_MIN);
        Vec3 cloudPosition = player.position().add(0, cloudHeight, 0);

        // Create new storm cloud
        StormCloud stormCloud = new StormCloud(level, player, cloudPosition, CLOUD_DURATION, stack, hand);
        activeStormClouds.put(playerId, stormCloud);

        // Send packet to clients for visual cloud rendering
        sendStormCloudPacket(level, player, cloudPosition, CLOUD_DURATION);
    }

    /**
     * Tick method to be called from your mod's tick handler
     */
    public static void tickStormClouds() {
        Iterator<Map.Entry<UUID, StormCloud>> iterator = activeStormClouds.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, StormCloud> entry = iterator.next();
            StormCloud cloud = entry.getValue();

            if (cloud.tick()) {
                iterator.remove(); // Remove expired clouds
            }
        }
    }

    /**
     * Clear all storm clouds (useful for cleanup)
     */
    public static void clearAllStormClouds() {
        activeStormClouds.clear();
    }

    private void sendStormCloudPacket(ServerLevel level, Player player, Vec3 cloudPosition, int duration) {
        // For now, we'll use empty targets list - the cloud will be rendered separately
        ElectricityPacket packet = new ElectricityPacket(player.getId(), Collections.emptyList(), duration);

        PacketDistributor.TargetPoint targetPoint = new PacketDistributor.TargetPoint(
                player.getX(), player.getY(), player.getZ(), 64.0, level.dimension()
        );

        ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> targetPoint), packet);
    }

    /**
     * Storm cloud that hovers above the player and strikes nearby mobs
     */
    private static class StormCloud {
        private final ServerLevel level;
        private final Player player;
        private Vec3 position;
        private final int maxAge;
        private int age = 0;
        private int nextLightningCheck = 0;
        private final ItemStack wandStack;
        private final InteractionHand hand;

        private static final int LIGHTNING_CHECK_INTERVAL = 20; // Check for targets every second
        private static final int MIN_LIGHTNING_COOLDOWN = 40; // Minimum 2 seconds between strikes

        public StormCloud(ServerLevel level, Player player, Vec3 position, int maxAge, ItemStack wandStack, InteractionHand hand) {
            this.level = level;
            this.player = player;
            this.position = position;
            this.maxAge = maxAge;
            this.wandStack = wandStack;
            this.hand = hand;
        }

        public boolean tick() {
            age++;

            // Update cloud position to follow player
            if (player.isAlive() && !player.isRemoved()) {
                double cloudHeight = CLOUD_HEIGHT_MIN + level.random.nextDouble() * (CLOUD_HEIGHT_MAX - CLOUD_HEIGHT_MIN);
                position = player.position().add(
                        level.random.nextGaussian() * 0.5, // Small horizontal drift
                        cloudHeight,
                        level.random.nextGaussian() * 0.5
                );
            }

            // Check for lightning strikes
            if (age >= nextLightningCheck && player.isAlive()) {
                checkForLightningStrike();
                nextLightningCheck = age + LIGHTNING_CHECK_INTERVAL;
            }

            return age >= maxAge || !player.isAlive() || player.isRemoved();
        }

        private void checkForLightningStrike() {
            // Find mobs within range of the cloud
            AABB detectionBox = new AABB(position, position).inflate(MOB_DETECTION_RANGE);

            List<LivingEntity> nearbyMobs = level.getEntitiesOfClass(LivingEntity.class, detectionBox,
                    entity -> entity != player && entity.isAlive() && !entity.isRemoved());

            if (!nearbyMobs.isEmpty()) {
                // Find the closest mob as the initial target
                LivingEntity closestMob = findClosestMob(nearbyMobs);

                if (closestMob != null) {
                    // Create chained lightning from cloud to mobs
                    List<LivingEntity> chainTargets = findChainTargets(closestMob, nearbyMobs);

                    if (!chainTargets.isEmpty()) {
                        triggerLightningStrike(chainTargets);
                        nextLightningCheck = age + MIN_LIGHTNING_COOLDOWN + level.random.nextInt(20);
                    }
                }
            }
        }

        private LivingEntity findClosestMob(List<LivingEntity> mobs) {
            LivingEntity closest = null;
            double closestDistance = Double.MAX_VALUE;

            for (LivingEntity mob : mobs) {
                double distance = position.distanceTo(mob.position().add(0, mob.getBbHeight() * 0.5, 0));
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = mob;
                }
            }

            return closest;
        }

        private List<LivingEntity> findChainTargets(LivingEntity initialTarget, List<LivingEntity> allMobs) {
            List<LivingEntity> chainTargets = new ArrayList<>();
            Set<LivingEntity> visited = new HashSet<>();

            chainTargets.add(initialTarget);
            visited.add(initialTarget);

            // Chain from the initial target to other nearby mobs
            LivingEntity currentTarget = initialTarget;

            while (chainTargets.size() < MAX_TARGETS) {
                LivingEntity nextTarget = findNearestUnvisitedMob(currentTarget, allMobs, visited);

                if (nextTarget == null || currentTarget.distanceTo(nextTarget) > CHAIN_RANGE) {
                    break; // No more valid targets within range
                }

                chainTargets.add(nextTarget);
                visited.add(nextTarget);
                currentTarget = nextTarget;
            }

            return chainTargets;
        }

        private LivingEntity findNearestUnvisitedMob(LivingEntity currentTarget, List<LivingEntity> allMobs, Set<LivingEntity> visited) {
            LivingEntity nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (LivingEntity mob : allMobs) {
                if (!visited.contains(mob)) {
                    double distance = currentTarget.distanceTo(mob);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = mob;
                    }
                }
            }

            return nearest;
        }

        private void triggerLightningStrike(List<LivingEntity> targets) {
            // Apply damage and effects
            applyElectricEffects(targets);

            // Damage wand
            wandStack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));

            // Send visual effect packet (we'll use the cloud position as the "source")
            sendLightningEffectPacket(targets);

            // Play lightning sound
            level.playSound(null, position.x, position.y, position.z,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                    1.0f, 1.2f + level.random.nextFloat() * 0.6f);
        }

        private void applyElectricEffects(List<LivingEntity> targets) {
            DamageSource electricDamage = level.damageSources().mobAttack(player);

            for (int i = 0; i < targets.size(); i++) {
                LivingEntity target = targets.get(i);
                float damage = BASE_DAMAGE * (float) Math.pow(CHAIN_DAMAGE_REDUCTION, i);
                target.hurt(electricDamage, damage);

                if (target.isAlive()) {
                    // Knockback effect
                    Vec3 knockback = target.position().subtract(player.position()).normalize().scale(0.3);
                    target.setDeltaMovement(target.getDeltaMovement().add(knockback.x, 0.2, knockback.z));
                    target.hurtMarked = true;
                }
            }
        }

        private void sendLightningEffectPacket(List<LivingEntity> targets) {
            List<Integer> targetIds = new ArrayList<>();
            for (LivingEntity target : targets) {
                targetIds.add(target.getId());
            }

            // Create a temporary "cloud entity" ID for the source (using negative player ID)
            ElectricityPacket packet = new ElectricityPacket(-player.getId(), targetIds, 60);

            PacketDistributor.TargetPoint targetPoint = new PacketDistributor.TargetPoint(
                    position.x, position.y, position.z, 64.0, level.dimension()
            );

            ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> targetPoint), packet);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.getItem() == net.minecraft.world.item.Items.DIAMOND ||
                repair.getItem() == net.minecraft.world.item.Items.NETHERITE_INGOT;
    }

    // ============== INTEGRATED EVENT HANDLING ==============

    /**
     * Server tick event handler - ticks all active storm clouds
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickStormClouds();
        }
    }

    /**
     * Client tick event handler - ticks electricity renderer
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ElectricityRenderer.tick();
            });
        }
    }
}