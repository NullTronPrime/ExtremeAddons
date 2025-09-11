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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Electric Wand - Creates storm clouds above player that strike nearby mobs with chained lightning
 * Updated for Forge 1.20.1 with proper cloud position passing
 */
public class ElectricWandItem extends Item {

    private static final double CLOUD_HEIGHT_MIN = 3.0;
    private static final double CLOUD_HEIGHT_MAX = 5.0;
    private static final double MOB_DETECTION_RANGE = 6.0;
    private static final double CHAIN_RANGE = 5.0;
    private static final int MAX_TARGETS = 8;
    private static final int COOLDOWN_TICKS = 60;
    private static final int EFFECT_DURATION = 100;
    private static final int CLOUD_DURATION = 200;

    private static final float BASE_DAMAGE = 6.0f;
    private static final float CHAIN_DAMAGE_REDUCTION = 0.8f;
    private static final int STUN_DURATION = 20;

    // Track active storm clouds per player - store both server and client data
    private static final Map<UUID, StormCloud> activeStormClouds = new HashMap<>();

    // Track if events are registered
    private static boolean eventsRegistered = false;

    public ElectricWandItem() {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .durability(100)
        );

        // Register events only once
        if (!eventsRegistered) {
            MinecraftForge.EVENT_BUS.register(ElectricWandItem.class);
            eventsRegistered = true;
            System.out.println("ElectricWandItem: Event handlers registered");
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level;
            System.out.println("Electric wand used by: " + player.getName().getString());

            // Create or refresh storm cloud above player
            createStormCloud(serverLevel, player, stack, hand);

            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

            // Play thunder sound for cloud creation
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                    0.5f, 0.8f + level.random.nextFloat() * 0.4f);

            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.success(stack);
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

        // Send packet to clients for initial visual cloud rendering with position
        sendStormCloudCreatePacket(level, player, cloudPosition, CLOUD_DURATION);

        System.out.println("Storm cloud created at: " + cloudPosition + " for player: " + player.getName().getString());
    }

    /**
     * Send packet to create visual storm cloud with position data
     */
    private void sendStormCloudCreatePacket(ServerLevel level, Player player, Vec3 cloudPosition, int duration) {
        // Use negative player ID to indicate storm cloud creation, pass cloud position
        ElectricityPacket packet = new ElectricityPacket(-Math.abs(player.getId()), Collections.emptyList(), duration, cloudPosition);

        PacketDistributor.TargetPoint targetPoint = new PacketDistributor.TargetPoint(
                player.getX(), player.getY(), player.getZ(), 64.0, level.dimension()
        );

        ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> targetPoint), packet);
        System.out.println("Storm cloud creation packet sent to clients with position: " + cloudPosition);
    }

    /**
     * Get current cloud position for a player (used by the renderer)
     */
    public static Vec3 getCloudPosition(UUID playerId) {
        StormCloud cloud = activeStormClouds.get(playerId);
        return cloud != null ? cloud.getCurrentPosition() : null;
    }

    /**
     * Tick method to be called from event handler
     */
    public static void tickStormClouds() {
        if (activeStormClouds.isEmpty()) return;

        Iterator<Map.Entry<UUID, StormCloud>> iterator = activeStormClouds.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, StormCloud> entry = iterator.next();
            StormCloud cloud = entry.getValue();

            if (cloud.tick()) {
                iterator.remove();
            }
        }
    }

    /**
     * Clear all storm clouds (useful for cleanup)
     */
    public static void clearAllStormClouds() {
        activeStormClouds.clear();
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

        private static final int LIGHTNING_CHECK_INTERVAL = 20;
        private static final int MIN_LIGHTNING_COOLDOWN = 40;

        public StormCloud(ServerLevel level, Player player, Vec3 position, int maxAge, ItemStack wandStack, InteractionHand hand) {
            this.level = level;
            this.player = player;
            this.position = position;
            this.maxAge = maxAge;
            this.wandStack = wandStack;
            this.hand = hand;
        }

        public Vec3 getCurrentPosition() {
            return position;
        }

        public boolean tick() {
            age++;

            // Update cloud position to follow player with gentle floating motion
            if (player.isAlive() && !player.isRemoved()) {
                double time = age * 0.02; // Slower movement
                double cloudHeight = CLOUD_HEIGHT_MIN + level.random.nextDouble() * (CLOUD_HEIGHT_MAX - CLOUD_HEIGHT_MIN);

                // More subtle floating motion
                Vec3 drift = new Vec3(
                        Math.sin(time) * 0.2,
                        Math.sin(time * 1.3) * 0.1,
                        Math.cos(time * 0.8) * 0.2
                );

                position = player.position().add(0, cloudHeight, 0).add(drift);
            }

            // Check for lightning strikes
            if (age >= nextLightningCheck && player.isAlive()) {
                checkForLightningStrike();
                nextLightningCheck = age + LIGHTNING_CHECK_INTERVAL;
            }

            return age >= maxAge || !player.isAlive() || player.isRemoved();
        }

        private void checkForLightningStrike() {
            // Find mobs within range of the player (not the cloud)
            AABB detectionBox = new AABB(player.position(), player.position()).inflate(MOB_DETECTION_RANGE);

            List<LivingEntity> nearbyMobs = level.getEntitiesOfClass(LivingEntity.class, detectionBox,
                    entity -> entity != player && entity.isAlive() && !entity.isRemoved());

            System.out.println("Found " + nearbyMobs.size() + " nearby mobs");

            if (!nearbyMobs.isEmpty()) {
                LivingEntity closestMob = findClosestMob(nearbyMobs);

                if (closestMob != null) {
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
                double distance = player.distanceTo(mob);
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

            LivingEntity currentTarget = initialTarget;

            while (chainTargets.size() < MAX_TARGETS) {
                LivingEntity nextTarget = findNearestUnvisitedMob(currentTarget, allMobs, visited);

                if (nextTarget == null || currentTarget.distanceTo(nextTarget) > CHAIN_RANGE) {
                    break;
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
            System.out.println("Triggering lightning strike on " + targets.size() + " targets");

            // Apply damage and effects
            applyElectricEffects(targets);

            // Damage wand
            wandStack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));

            // Send visual effect packet with current cloud position
            sendLightningEffectPacket(targets);

            // Play lightning sound
            level.playSound(null, position.x, position.y, position.z,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                    1.0f, 1.2f + level.random.nextFloat() * 0.6f);

            System.out.println("Lightning strike triggered on " + targets.size() + " targets");
        }

        private void applyElectricEffects(List<LivingEntity> targets) {
            DamageSource electricDamage = level.damageSources().playerAttack(player);

            for (int i = 0; i < targets.size(); i++) {
                LivingEntity target = targets.get(i);
                float damage = BASE_DAMAGE * (float) Math.pow(CHAIN_DAMAGE_REDUCTION, i);
                target.hurt(electricDamage, damage);
                System.out.println("Applied " + damage + " damage to " + target.getName().getString());

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

            // Use negative player ID to indicate cloud source and pass current cloud position
            ElectricityPacket packet = new ElectricityPacket(-Math.abs(player.getId()), targetIds, 60, position);

            PacketDistributor.TargetPoint targetPoint = new PacketDistributor.TargetPoint(
                    position.x, position.y, position.z, 64.0, level.dimension()
            );

            ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> targetPoint), packet);
            System.out.println("Lightning effect packet sent to clients with " + targetIds.size() + " targets and cloud position: " + position);
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

    // ============== EVENT HANDLERS ==============

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