package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.network.EyeRenderPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Enhanced StaffOfEyes with piercing lasers, knockback, and center targeting
 */
public class StaffOfEyesItem extends Item {
    public StaffOfEyesItem(Properties props) { super(props); }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide) return;
        if (!(entity instanceof Player p)) return;

        if (selected && p.getMainHandItem() == stack) {
            if (p instanceof ServerPlayer sp) EyeController.ensure(sp);
        } else {
            if (p instanceof ServerPlayer sp) EyeController.remove(sp);
        }
    }

    @Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class EyeController {
        private static final Map<UUID, EyeController> INSTANCES = new HashMap<>();
        private static final Random RAND = new Random();

        // tuning
        private static final int MAX_EYES = 15;
        private static final double LOCK_DISTANCE = 12.0;
        private static final double SEARCH_RADIUS = 16.0;
        private static final int PACKET_INTERVAL = 2;
        private static final float LASER_DAMAGE = 4.0f;
        private static final float LASER_KNOCKBACK_STRENGTH = 1.2f; // Knockback strength
        private static final double LASER_MAX_RANGE = 32.0; // Maximum laser range
        private static final int AIM_THRESHOLD = 5;
        private static final int FIRE_COOLDOWN_MIN = 15;
        private static final int FIRE_COOLDOWN_MAX = 30;

        // orbital system constants
        private static final double[] ORBITAL_RADII = {2.5, 3.8, 5.2};
        private static final double[] ORBITAL_HEIGHTS = {0.8, 1.5, 2.2};
        private static final double[] ORBITAL_SPEEDS = {0.008, 0.006, 0.004};

        private final ServerPlayer owner;
        private final List<Eye> eyes = new ArrayList<>();
        private int tick = 0;

        private EyeController(ServerPlayer owner) {
            this.owner = owner;
            int count = Math.min(MAX_EYES, 6 + RAND.nextInt(5));

            for (int i = 0; i < count; i++) {
                Eye e = new Eye();

                e.orbitalRing = i % 3;
                double eyesInThisRing = Math.ceil(count / 3.0);
                double angleStep = (Math.PI * 2.0) / eyesInThisRing;
                double ringIndex = i / 3;
                e.orbitalAngle = ringIndex * angleStep + RAND.nextDouble() * 0.5;

                updateEyeOrbitalPosition(e);

                e.width = 0.6f + RAND.nextFloat() * 0.9f;
                e.height = e.width * 0.5f;
                e.fireCooldown = RAND.nextInt(FIRE_COOLDOWN_MAX);
                e.aimTicks = 0;

                e.blinkCooldown = 60 + RAND.nextInt(120);
                e.isBlinking = false;
                e.blinkPhase = 0.0f;
                e.blinkSpeed = 0.15f + RAND.nextFloat() * 0.1f;

                eyes.add(e);
            }

            sendRenderPacket();
        }

        private void updateEyeOrbitalPosition(Eye eye) {
            int ring = eye.orbitalRing;
            double radius = ORBITAL_RADII[ring];
            double height = ORBITAL_HEIGHTS[ring] + Math.sin(tick * 0.01 + eye.hashCode()) * 0.15;

            double x = Math.cos(eye.orbitalAngle) * radius;
            double z = Math.sin(eye.orbitalAngle) * radius;

            eye.offset = new Vec3(x, height, z);
        }

        public static void ensure(ServerPlayer p) {
            INSTANCES.computeIfAbsent(p.getUUID(), u -> new EyeController(p));
        }

        public static void remove(ServerPlayer p) {
            EyeController removed = INSTANCES.remove(p.getUUID());
            if (removed != null) {
                List<EyeRenderPacket.EyeEntry> emptyList = Collections.emptyList();
                EyeRenderPacket clear = new EyeRenderPacket(p.getId(), emptyList);
                ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), clear);
            }
        }

        private static void tickAll() {
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, EyeController> e : INSTANCES.entrySet()) {
                EyeController c = e.getValue();
                if (c.owner.isRemoved() || !isPlayerHoldingStaff(c.owner)) {
                    toRemove.add(e.getKey());
                    continue;
                }
                c.tick();
            }
            for (UUID u : toRemove) {
                EyeController removed = INSTANCES.get(u);
                if (removed != null) {
                    remove(removed.owner);
                }
            }
        }

        private static boolean isPlayerHoldingStaff(ServerPlayer p) {
            ItemStack main = p.getMainHandItem();
            return main.getItem() instanceof StaffOfEyesItem;
        }

        private void tick() {
            if (owner.isRemoved()) return;
            tick++;

            LivingEntity target = findNearestTargetWithin(SEARCH_RADIUS);

            boolean locked = false;
            if (target != null) {
                double d2 = target.distanceToSqr(owner);
                if (d2 <= LOCK_DISTANCE * LOCK_DISTANCE) locked = true;
            }

            for (Eye e : eyes) {
                updateBlinking(e);

                if (!locked || target == null) {
                    // IDLE MODE: Orbital movement with player-facing
                    e.orbitalAngle += ORBITAL_SPEEDS[e.orbitalRing];
                    if (e.orbitalAngle > Math.PI * 2.0) e.orbitalAngle -= Math.PI * 2.0;

                    updateEyeOrbitalPosition(e);

                    Vec3 eyeWorld = owner.position().add(e.offset);
                    Vec3 playerCenter = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
                    Vec3 lookToPlayer = playerCenter.subtract(eyeWorld);
                    if (lookToPlayer.length() > 1e-6) {
                        e.look = lookToPlayer.normalize();
                    }

                    e.aimTicks = 0;
                    e.firing = false;
                    e.laserEnd = null;
                } else {
                    // LOCKED MODE: Fixed position, track target center
                    Vec3 eyeWorld = owner.position().add(e.offset);
                    Vec3 targetCenter = getEntityCenter(target); // Target center instead of bottom
                    Vec3 dir = targetCenter.subtract(eyeWorld);
                    if (dir.length() > 1e-6) e.look = dir.normalize();

                    if (e.fireCooldown > 0) e.fireCooldown--;
                    e.aimTicks++;

                    boolean canFire = e.aimTicks >= AIM_THRESHOLD && e.fireCooldown <= 0;

                    if (canFire) {
                        // Perform piercing laser trace
                        LaserResult laserResult = performPiercingLaser(eyeWorld, targetCenter);

                        e.firing = true;
                        e.laserEnd = laserResult.endPoint;
                        e.fireCooldown = FIRE_COOLDOWN_MIN + RAND.nextInt(FIRE_COOLDOWN_MAX - FIRE_COOLDOWN_MIN + 1);
                        e.aimTicks = 0;

                        // Apply damage and knockback to all hit entities
                        for (LivingEntity hitEntity : laserResult.hitEntities) {
                            applyLaserEffects(hitEntity, eyeWorld, laserResult.endPoint);
                        }
                    } else {
                        e.firing = e.aimTicks > AIM_THRESHOLD / 2;
                        e.laserEnd = null;
                    }
                }
            }

            if (tick % PACKET_INTERVAL == 0) sendRenderPacket();
        }

        /**
         * Gets the center point of an entity (middle of bounding box)
         */
        private Vec3 getEntityCenter(LivingEntity entity) {
            return new Vec3(
                    entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5,
                    entity.getZ()
            );
        }

        /**
         * Performs a piercing laser trace that can hit multiple entities
         */
        private LaserResult performPiercingLaser(Vec3 start, Vec3 initialTarget) {
            Vec3 direction = initialTarget.subtract(start).normalize();
            Vec3 end = start.add(direction.scale(LASER_MAX_RANGE));

            // Find the first wall hit
            ClipContext clipContext = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner);
            BlockHitResult blockHit = owner.level().clip(clipContext);

            Vec3 actualEnd = end;
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                actualEnd = blockHit.getLocation();
            }

            // Find all entities along the laser path
            List<LivingEntity> hitEntities = new ArrayList<>();
            AABB searchBox = new AABB(start, actualEnd).inflate(1.0);

            List<LivingEntity> potentialTargets = owner.level().getEntitiesOfClass(
                    LivingEntity.class,
                    searchBox,
                    entity -> entity != owner && !entity.isRemoved() && entity.getHealth() > 0.0F && !entity.isInvulnerable()
            );

            for (LivingEntity entity : potentialTargets) {
                if (isEntityInLaserPath(start, actualEnd, entity)) {
                    hitEntities.add(entity);
                }
            }

            // Sort by distance from laser start
            hitEntities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(start.x, start.y, start.z)));

            return new LaserResult(actualEnd, hitEntities);
        }

        /**
         * Checks if an entity intersects with the laser path
         */
        private boolean isEntityInLaserPath(Vec3 laserStart, Vec3 laserEnd, LivingEntity entity) {
            AABB entityBox = entity.getBoundingBox();

            // Use a slightly thicker ray for the laser
            Vec3 direction = laserEnd.subtract(laserStart).normalize();
            double laserLength = laserStart.distanceTo(laserEnd);

            // Check multiple points along the laser path
            int checkPoints = Math.max(3, (int)(laserLength / 2.0)); // Check every 2 blocks
            for (int i = 0; i <= checkPoints; i++) {
                double t = (double)i / checkPoints;
                Vec3 checkPoint = laserStart.add(direction.scale(laserLength * t));

                // Add small radius around the laser
                AABB laserPoint = new AABB(checkPoint, checkPoint).inflate(0.3);
                if (entityBox.intersects(laserPoint)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Overloaded version for any Entity (including projectiles)
         */
        private boolean isEntityInLaserPath(Vec3 laserStart, Vec3 laserEnd, Entity entity) {
            AABB entityBox = entity.getBoundingBox();

            Vec3 direction = laserEnd.subtract(laserStart).normalize();
            double laserLength = laserStart.distanceTo(laserEnd);

            // For projectiles, use a thicker detection radius
            double detectionRadius = entity instanceof net.minecraft.world.entity.projectile.Projectile ? 0.5 : 0.3;

            int checkPoints = Math.max(3, (int)(laserLength / 2.0));
            for (int i = 0; i <= checkPoints; i++) {
                double t = (double)i / checkPoints;
                Vec3 checkPoint = laserStart.add(direction.scale(laserLength * t));

                AABB laserPoint = new AABB(checkPoint, checkPoint).inflate(detectionRadius);
                if (entityBox.intersects(laserPoint)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Checks if an entity is a projectile that can be deflected
         */
        private boolean isProjectile(Entity entity) {
            return entity instanceof net.minecraft.world.entity.projectile.Projectile ||
                    entity instanceof net.minecraft.world.entity.projectile.Arrow ||
                    entity instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity ||
                    entity instanceof net.minecraft.world.entity.projectile.ThrownTrident ||
                    entity instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile ||
                    entity.getType().toString().toLowerCase().contains("projectile") ||
                    entity.getType().toString().toLowerCase().contains("arrow") ||
                    entity.getType().toString().toLowerCase().contains("bolt");
        }

        /**
         * Deflects a projectile when hit by laser
         */
        private void deflectProjectile(Entity projectile, Vec3 laserStart, Vec3 laserEnd) {
            Vec3 laserDirection = laserEnd.subtract(laserStart).normalize();
            Vec3 projectileVelocity = projectile.getDeltaMovement();

            // Calculate deflection - mix of reflection and laser direction
            Vec3 reflected = projectileVelocity.subtract(laserDirection.scale(2 * projectileVelocity.dot(laserDirection)));
            Vec3 deflected = reflected.scale(0.7).add(laserDirection.scale(0.3));

            // Add some randomness for chaotic deflection
            Vec3 randomOffset = new Vec3(
                    (RAND.nextDouble() - 0.5) * 0.4,
                    (RAND.nextDouble() - 0.5) * 0.4,
                    (RAND.nextDouble() - 0.5) * 0.4
            );

            deflected = deflected.add(randomOffset).normalize().scale(deflected.length() * 1.2); // Slightly faster

            // Apply the deflection
            projectile.setDeltaMovement(deflected);
            projectile.hurtMarked = true;

            // Change projectile ownership if possible (make it friendly)
            if (projectile instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                try {
                    proj.setOwner(owner); // Make deflected projectiles friendly
                } catch (Exception e) {
                    // Some projectiles might not allow owner changes
                }
            }

            // Visual effect for deflection
            projectile.setSecondsOnFire(2);

            // Spawn some particles at deflection point
            if (projectile.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                Vec3 deflectPos = projectile.position();
                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        deflectPos.x, deflectPos.y, deflectPos.z,
                        5, // count
                        0.2, 0.2, 0.2, // spread
                        0.1 // speed
                );
            }
        }

        /**
         * Applies damage and knockback to an entity hit by the laser
         */
        private void applyLaserEffects(LivingEntity target, Vec3 laserStart, Vec3 laserEnd) {
            // Create damage source
            DamageSource damageSource = owner.damageSources().playerAttack(owner);

            // Apply damage
            target.hurt(damageSource, LASER_DAMAGE);

            // Calculate and apply knockback
            Vec3 targetCenter = getEntityCenter(target);
            Vec3 knockbackDirection = targetCenter.subtract(laserStart).normalize();

            // Apply horizontal knockback (similar to how vanilla knockback works)
            double horizontalStrength = LASER_KNOCKBACK_STRENGTH;
            double verticalStrength = LASER_KNOCKBACK_STRENGTH * 0.4; // Reduced vertical component

            Vec3 knockback = new Vec3(
                    knockbackDirection.x * horizontalStrength,
                    Math.max(0.1, knockbackDirection.y * verticalStrength + 0.1), // Always lift slightly
                    knockbackDirection.z * horizontalStrength
            );

            // Apply the knockback
            target.setDeltaMovement(target.getDeltaMovement().add(knockback));
            target.hurtMarked = true; // Mark for velocity sync

            // Add some visual flair - make the target glow briefly
            target.setSecondsOnFire(1); // Brief fire effect for visual feedback
        }

        /**
         * Result of a piercing laser trace
         */
        private static class LaserResult {
            final Vec3 endPoint;
            final List<LivingEntity> hitEntities;

            LaserResult(Vec3 endPoint, List<LivingEntity> hitEntities) {
                this.endPoint = endPoint;
                this.hitEntities = hitEntities;
            }
        }

        private void updateBlinking(Eye eye) {
            if (eye.isBlinking) {
                eye.blinkPhase += eye.blinkSpeed;
                if (eye.blinkPhase >= 1.0f) {
                    eye.isBlinking = false;
                    eye.blinkPhase = 0.0f;
                    eye.blinkCooldown = 40 + RAND.nextInt(160);
                }
            } else {
                if (eye.blinkCooldown > 0) {
                    eye.blinkCooldown--;
                } else {
                    eye.isBlinking = true;
                    eye.blinkPhase = 0.0f;
                    eye.blinkSpeed = 0.12f + RAND.nextFloat() * 0.16f;
                }
            }
        }

        private LivingEntity findNearestTargetWithin(double radius) {
            double best = radius * radius;
            LivingEntity bestE = null;
            List<LivingEntity> list = owner.level().getEntitiesOfClass(LivingEntity.class,
                    owner.getBoundingBox().inflate(radius),
                    e -> e != owner && !e.isRemoved() && e.getHealth() > 0.0F && !e.isInvulnerable());
            for (LivingEntity le : list) {
                double d2 = le.distanceToSqr(owner);
                if (d2 < best) { best = d2; bestE = le; }
            }
            return bestE;
        }

        private void sendRenderPacket() {
            List<EyeRenderPacket.EyeEntry> entries = new ArrayList<>(eyes.size());
            for (Eye e : eyes) {
                entries.add(new EyeRenderPacket.EyeEntry(e.offset, e.firing, e.laserEnd, e.look, -1, e.isBlinking, e.blinkPhase));
            }
            EyeRenderPacket pkt = new EyeRenderPacket(owner.getId(), entries);
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> owner), pkt);
        }

        private static final class Eye {
            Vec3 offset = Vec3.ZERO;
            Vec3 look = new Vec3(0,0,1);
            boolean firing = false;
            Vec3 laserEnd = null;
            float width = 0.8f, height = 0.4f;
            int fireCooldown = 0;
            int aimTicks = 0;

            // Individual targeting
            LivingEntity currentTarget = null;

            // Orbital system
            int orbitalRing = 0;
            double orbitalAngle = 0.0;

            // Enhanced blinking
            int blinkCooldown = 60;
            boolean isBlinking = false;
            float blinkPhase = 0.0f;
            float blinkSpeed = 0.15f;
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent evt) {
            if (evt.phase != TickEvent.Phase.END) return;
            tickAll();
        }
    }
}