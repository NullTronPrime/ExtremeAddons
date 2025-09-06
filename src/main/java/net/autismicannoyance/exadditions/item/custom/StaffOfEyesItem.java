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
 * Enhanced StaffOfEyes with piercing lasers, knockback, orbit visualization, and mob support
 */
public class StaffOfEyesItem extends Item {
    // Staff configuration - stored per ItemStack
    private static final String EYE_COUNT_TAG = "eye_count";
    private static final int DEFAULT_EYE_COUNT = 45; // Tripled from 15

    public StaffOfEyesItem(Properties props) {
        super(props);
    }

    /**
     * Get the eye count for this specific staff
     */
    public static int getEyeCount(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(EYE_COUNT_TAG)) {
            return stack.getTag().getInt(EYE_COUNT_TAG);
        }
        return DEFAULT_EYE_COUNT;
    }

    /**
     * Set the eye count for this specific staff
     */
    public static void setEyeCount(ItemStack stack, int count) {
        stack.getOrCreateTag().putInt(EYE_COUNT_TAG, Math.max(1, count));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide) return;
        if (!(entity instanceof LivingEntity livingEntity)) return;

        // Check if this entity is holding the staff in main hand
        boolean isHoldingStaff = false;
        if (entity instanceof Player p) {
            isHoldingStaff = selected && p.getMainHandItem() == stack;
        } else {
            // For mobs, check if they're holding it in main hand
            isHoldingStaff = livingEntity.getMainHandItem() == stack;
        }

        if (isHoldingStaff) {
            EyeController.ensure(livingEntity, stack);
        } else {
            EyeController.remove(livingEntity);
        }
    }

    @Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class EyeController {
        private static final Map<UUID, EyeController> INSTANCES = new HashMap<>();
        private static final Random RAND = new Random();

        // tuning
        private static final double LOCK_DISTANCE = 12.0;
        private static final double SEARCH_RADIUS = 16.0;
        private static final int PACKET_INTERVAL = 2;
        private static final float LASER_DAMAGE = 4.0f;
        private static final float LASER_KNOCKBACK_STRENGTH = 0.6f; // Halved from 1.2f
        private static final double LASER_MAX_RANGE = 32.0;
        private static final int AIM_THRESHOLD = 5;
        private static final int FIRE_COOLDOWN_MIN = 15;
        private static final int FIRE_COOLDOWN_MAX = 30;

        // orbital system constants - adjusted for more eyes
        private static final double[] ORBITAL_RADII = {2.0, 3.0, 4.0, 5.0, 6.0}; // More rings for more eyes
        private static final double[] ORBITAL_HEIGHTS = {0.5, 1.0, 1.5, 2.0, 2.5};
        private static final double[] ORBITAL_SPEEDS = {0.010, 0.008, 0.006, 0.004, 0.002};

        // Orbit ring visualization settings
        private static final int ORBIT_RING_SEGMENTS = 64; // Resolution of orbit rings
        private static final float ORBIT_RING_THICKNESS = 0.02f;
        private static final int ORBIT_RING_COLOR = 0x30FF0000; // Semi-transparent red

        private final LivingEntity owner;
        private final ItemStack staffStack;
        private final List<Eye> eyes = new ArrayList<>();
        private int tick = 0;

        private EyeController(LivingEntity owner, ItemStack staffStack) {
            this.owner = owner;
            this.staffStack = staffStack;

            int eyeCount = getEyeCount(staffStack);
            int ringsUsed = Math.min(ORBITAL_RADII.length, (eyeCount + 8) / 9); // Distribute eyes across rings

            for (int i = 0; i < eyeCount; i++) {
                Eye e = new Eye();

                e.orbitalRing = i % ringsUsed;
                double eyesInThisRing = Math.ceil((double)eyeCount / ringsUsed);
                double angleStep = (Math.PI * 2.0) / eyesInThisRing;
                double ringIndex = i / (double)ringsUsed;
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
            int ring = Math.min(eye.orbitalRing, ORBITAL_RADII.length - 1);
            double radius = ORBITAL_RADII[ring];
            double height = ORBITAL_HEIGHTS[ring] + Math.sin(tick * 0.01 + eye.hashCode()) * 0.15;

            double x = Math.cos(eye.orbitalAngle) * radius;
            double z = Math.sin(eye.orbitalAngle) * radius;

            eye.offset = new Vec3(x, height, z);
        }

        public static void ensure(LivingEntity entity, ItemStack staffStack) {
            UUID entityId = entity.getUUID();
            EyeController existing = INSTANCES.get(entityId);

            // If controller exists but staff changed, recreate it
            if (existing != null && !ItemStack.isSameItem(existing.staffStack, staffStack)) {
                remove(entity);
                existing = null;
            }

            if (existing == null) {
                INSTANCES.put(entityId, new EyeController(entity, staffStack));
            }
        }

        public static void remove(LivingEntity entity) {
            EyeController removed = INSTANCES.remove(entity.getUUID());
            if (removed != null) {
                List<EyeRenderPacket.EyeEntry> emptyList = Collections.emptyList();
                EyeRenderPacket clear = new EyeRenderPacket(entity.getId(), emptyList);

                // Send to all tracking players
                if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    ModNetworking.CHANNEL.send(
                            PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                            clear
                    );
                    // Also send to the entity itself if it's a player
                    if (entity instanceof ServerPlayer serverPlayer) {
                        ModNetworking.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> serverPlayer),
                                clear
                        );
                    }
                }
            }
        }

        private static void tickAll() {
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, EyeController> e : INSTANCES.entrySet()) {
                EyeController c = e.getValue();
                if (c.owner.isRemoved() || !isEntityHoldingStaff(c.owner, c.staffStack)) {
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

        private static boolean isEntityHoldingStaff(LivingEntity entity, ItemStack expectedStaff) {
            ItemStack mainHand = entity.getMainHandItem();
            return mainHand.getItem() instanceof StaffOfEyesItem &&
                    ItemStack.isSameItem(mainHand, expectedStaff);
        }

        private void tick() {
            if (owner.isRemoved()) return;
            tick++;

            LivingEntity target = findNearestTargetWithin(SEARCH_RADIUS);

            boolean locked = false;
            if (target != null && target != owner) { // Make sure we don't target ourselves
                double d2 = target.distanceToSqr(owner);
                if (d2 <= LOCK_DISTANCE * LOCK_DISTANCE) locked = true;
            }

            for (Eye e : eyes) {
                updateBlinking(e);

                if (!locked || target == null) {
                    // IDLE MODE: Orbital movement with owner-facing
                    int ring = Math.min(e.orbitalRing, ORBITAL_SPEEDS.length - 1);
                    e.orbitalAngle += ORBITAL_SPEEDS[ring];
                    if (e.orbitalAngle > Math.PI * 2.0) e.orbitalAngle -= Math.PI * 2.0;

                    updateEyeOrbitalPosition(e);

                    Vec3 eyeWorld = owner.position().add(e.offset);
                    Vec3 ownerCenter = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
                    Vec3 lookToOwner = ownerCenter.subtract(eyeWorld);
                    if (lookToOwner.length() > 1e-6) {
                        e.look = lookToOwner.normalize();
                    }

                    e.aimTicks = 0;
                    e.firing = false;
                    e.laserEnd = null;
                } else {
                    // LOCKED MODE: Fixed position, track target center
                    Vec3 eyeWorld = owner.position().add(e.offset);
                    Vec3 targetCenter = getEntityCenter(target);
                    Vec3 dir = targetCenter.subtract(eyeWorld);
                    if (dir.length() > 1e-6) e.look = dir.normalize();

                    if (e.fireCooldown > 0) e.fireCooldown--;
                    e.aimTicks++;

                    boolean canFire = e.aimTicks >= AIM_THRESHOLD && e.fireCooldown <= 0;

                    if (canFire) {
                        LaserResult laserResult = performPiercingLaser(eyeWorld, targetCenter);

                        e.firing = true;
                        e.laserEnd = laserResult.endPoint;
                        e.fireCooldown = FIRE_COOLDOWN_MIN + RAND.nextInt(FIRE_COOLDOWN_MAX - FIRE_COOLDOWN_MIN + 1);
                        e.aimTicks = 0;

                        for (LivingEntity hitEntity : laserResult.hitEntities) {
                            applyLaserEffects(hitEntity, eyeWorld, laserResult.endPoint);
                        }
                    } else {
                        e.firing = e.aimTicks > AIM_THRESHOLD / 2;
                        e.laserEnd = null;
                    }
                }
            }

            // Render orbit rings
            //renderOrbitRings();

            if (tick % PACKET_INTERVAL == 0) sendRenderPacket();
        }

        /**
         * Renders visible orbit rings using VectorRenderer
         */
//        private void renderOrbitRings() {
//            if (!(owner.level() instanceof net.minecraft.server.level.ServerLevel)) return;
//
//            // Determine which rings are actually used
//            Set<Integer> usedRings = new HashSet<>();
//            for (Eye eye : eyes) {
//                usedRings.add(Math.min(eye.orbitalRing, ORBITAL_RADII.length - 1));
//            }
//
//            Vec3 ownerPos = owner.position();
//
//            // Create ring wireframes for each used orbital ring
//            for (int ring : usedRings) {
//                double radius = ORBITAL_RADII[ring];
//                double height = ORBITAL_HEIGHTS[ring];
//                Vec3 ringCenter = ownerPos.add(0, height, 0);
//
//                // Create circle points
//                List<Vec3> ringPoints = new ArrayList<>();
//                for (int i = 0; i <= ORBIT_RING_SEGMENTS; i++) {
//                    double angle = (i / (double)ORBIT_RING_SEGMENTS) * Math.PI * 2.0;
//                    double x = Math.cos(angle) * radius;
//                    double z = Math.sin(angle) * radius;
//                    ringPoints.add(ringCenter.add(x, 0, z));
//                }
//
//                // Draw the ring as a polyline
//                net.autismicannoyance.exadditions.client.VectorRenderer.drawPolylineWorld(
//                        ringPoints,
//                        ORBIT_RING_COLOR,
//                        ORBIT_RING_THICKNESS,
//                        false, // thickness is not in pixels
//                        40, // lifetime ticks
//                        net.autismicannoyance.exadditions.client.VectorRenderer.Transform.IDENTITY
//                );
//            }
//        }

        private Vec3 getEntityCenter(LivingEntity entity) {
            return new Vec3(
                    entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5,
                    entity.getZ()
            );
        }

        private LaserResult performPiercingLaser(Vec3 start, Vec3 initialTarget) {
            Vec3 direction = initialTarget.subtract(start).normalize();
            Vec3 end = start.add(direction.scale(LASER_MAX_RANGE));

            ClipContext clipContext = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner);
            BlockHitResult blockHit = owner.level().clip(clipContext);

            Vec3 actualEnd = end;
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                actualEnd = blockHit.getLocation();
            }

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

            hitEntities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(start.x, start.y, start.z)));

            return new LaserResult(actualEnd, hitEntities);
        }

        private boolean isEntityInLaserPath(Vec3 laserStart, Vec3 laserEnd, LivingEntity entity) {
            AABB entityBox = entity.getBoundingBox();

            Vec3 direction = laserEnd.subtract(laserStart).normalize();
            double laserLength = laserStart.distanceTo(laserEnd);

            int checkPoints = Math.max(3, (int)(laserLength / 2.0));
            for (int i = 0; i <= checkPoints; i++) {
                double t = (double)i / checkPoints;
                Vec3 checkPoint = laserStart.add(direction.scale(laserLength * t));

                AABB laserPoint = new AABB(checkPoint, checkPoint).inflate(0.3);
                if (entityBox.intersects(laserPoint)) {
                    return true;
                }
            }

            return false;
        }

        private void applyLaserEffects(LivingEntity target, Vec3 laserStart, Vec3 laserEnd) {
            // Create damage source
            DamageSource damageSource;
            if (owner instanceof Player player) {
                damageSource = owner.damageSources().playerAttack(player);
            } else {
                damageSource = owner.damageSources().mobAttack(owner);
            }

            target.hurt(damageSource, LASER_DAMAGE);

            Vec3 targetCenter = getEntityCenter(target);
            Vec3 knockbackDirection = targetCenter.subtract(laserStart).normalize();

            double horizontalStrength = LASER_KNOCKBACK_STRENGTH;
            double verticalStrength = LASER_KNOCKBACK_STRENGTH * 0.4;

            Vec3 knockback = new Vec3(
                    knockbackDirection.x * horizontalStrength,
                    Math.max(0.1, knockbackDirection.y * verticalStrength + 0.1),
                    knockbackDirection.z * horizontalStrength
            );

            target.setDeltaMovement(target.getDeltaMovement().add(knockback));
            target.hurtMarked = true;

            target.setSecondsOnFire(1);
        }

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
                // Additional check for hostile behavior - mobs won't target their own kind typically
                if (owner instanceof Player || shouldTargetEntity(owner, le)) {
                    double d2 = le.distanceToSqr(owner);
                    if (d2 < best) {
                        best = d2;
                        bestE = le;
                    }
                }
            }
            return bestE;
        }

        /**
         * Determines if the owner should target the given entity
         */
        private boolean shouldTargetEntity(LivingEntity owner, LivingEntity target) {
            if (owner instanceof Player) {
                return true; // Players can target anything
            }

            // For mobs, use basic hostile logic
            if (target instanceof Player) {
                return true; // Mobs generally hostile to players
            }

            // Check if they're different types
            return !owner.getClass().equals(target.getClass());
        }

        private void sendRenderPacket() {
            List<EyeRenderPacket.EyeEntry> entries = new ArrayList<>(eyes.size());
            for (Eye e : eyes) {
                entries.add(new EyeRenderPacket.EyeEntry(e.offset, e.firing, e.laserEnd, e.look, -1, e.isBlinking, e.blinkPhase));
            }
            EyeRenderPacket pkt = new EyeRenderPacket(owner.getId(), entries);

            if (owner.level() instanceof net.minecraft.server.level.ServerLevel) {
                ModNetworking.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> owner),
                        pkt
                );
                // Also send to the owner if it's a player
                if (owner instanceof ServerPlayer serverPlayer) {
                    ModNetworking.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> serverPlayer),
                            pkt
                    );
                }
            }
        }

        private static final class Eye {
            Vec3 offset = Vec3.ZERO;
            Vec3 look = new Vec3(0,0,1);
            boolean firing = false;
            Vec3 laserEnd = null;
            float width = 0.8f, height = 0.4f;
            int fireCooldown = 0;
            int aimTicks = 0;

            LivingEntity currentTarget = null;

            int orbitalRing = 0;
            double orbitalAngle = 0.0;

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