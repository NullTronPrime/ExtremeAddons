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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Enhanced StaffOfEyes with 3-orbital idle system and improved eye tracking
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
        private static final int AIM_THRESHOLD = 5;
        private static final int FIRE_COOLDOWN_MIN = 15;
        private static final int FIRE_COOLDOWN_MAX = 30;

        // orbital system constants
        private static final double[] ORBITAL_RADII = {2.5, 3.8, 5.2}; // 3 orbital rings
        private static final double[] ORBITAL_HEIGHTS = {0.8, 1.5, 2.2}; // different heights for each ring
        private static final double[] ORBITAL_SPEEDS = {0.008, 0.006, 0.004}; // different speeds (rad/tick)

        private final ServerPlayer owner;
        private final List<Eye> eyes = new ArrayList<>();
        private int tick = 0;

        private EyeController(ServerPlayer owner) {
            this.owner = owner;
            int count = Math.min(MAX_EYES, 6 + RAND.nextInt(5)); // 6..10 eyes

            // Distribute eyes across the 3 orbitals
            for (int i = 0; i < count; i++) {
                Eye e = new Eye();

                // Assign orbital ring (distribute evenly)
                e.orbitalRing = i % 3;

                // Initial position on the orbital
                double eyesInThisRing = Math.ceil(count / 3.0);
                double angleStep = (Math.PI * 2.0) / eyesInThisRing;
                double ringIndex = i / 3;
                e.orbitalAngle = ringIndex * angleStep + RAND.nextDouble() * 0.5; // slight randomization

                // Set initial offset based on orbital position
                updateEyeOrbitalPosition(e);

                e.width = 0.6f + RAND.nextFloat() * 0.9f;
                e.height = e.width * 0.5f;
                e.fireCooldown = RAND.nextInt(FIRE_COOLDOWN_MAX);
                e.aimTicks = 0;

                // Enhanced blinking system
                e.blinkCooldown = 60 + RAND.nextInt(120); // 3-9 seconds between blinks
                e.isBlinking = false;
                e.blinkPhase = 0.0f;
                e.blinkSpeed = 0.15f + RAND.nextFloat() * 0.1f; // varied blink speeds

                eyes.add(e);
            }

            sendRenderPacket();
        }

        private void updateEyeOrbitalPosition(Eye eye) {
            int ring = eye.orbitalRing;
            double radius = ORBITAL_RADII[ring];
            double height = ORBITAL_HEIGHTS[ring] + Math.sin(tick * 0.01 + eye.hashCode()) * 0.15; // gentle bobbing

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
                // Update blinking animation
                updateBlinking(e);

                if (!locked || target == null) {
                    // IDLE MODE: Orbital movement with player-facing

                    // Update orbital angle (each ring moves at different speeds)
                    e.orbitalAngle += ORBITAL_SPEEDS[e.orbitalRing];
                    if (e.orbitalAngle > Math.PI * 2.0) e.orbitalAngle -= Math.PI * 2.0;

                    // Update position on orbital
                    updateEyeOrbitalPosition(e);

                    // Always look at player center when idle
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
                    // LOCKED MODE: Fixed position, track target
                    // Keep current orbital position but stop moving

                    Vec3 eyeWorld = owner.position().add(e.offset);
                    Vec3 targetPos = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
                    Vec3 dir = targetPos.subtract(eyeWorld);
                    if (dir.length() > 1e-6) e.look = dir.normalize();

                    if (e.fireCooldown > 0) e.fireCooldown--;
                    e.aimTicks++;

                    boolean canFire = e.aimTicks >= AIM_THRESHOLD && e.fireCooldown <= 0;

                    if (canFire) {
                        Vec3 hitPoint = targetPos;
                        boolean hitTarget = false;

                        Vec3 start = eyeWorld;
                        Vec3 end = targetPos;
                        AABB searchBox = owner.getBoundingBox().expandTowards(end.subtract(start)).inflate(2.0);
                        EntityHitResult entResult = ProjectileUtil.getEntityHitResult(owner.level(), owner, start, end, searchBox,
                                e2 -> (e2 instanceof LivingEntity) && !e2.isRemoved() && e2 != owner);

                        if (entResult != null && entResult.getEntity() == target) {
                            hitPoint = entResult.getLocation();
                            hitTarget = true;
                        } else {
                            ClipContext cc = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner);
                            BlockHitResult bhr = owner.level().clip(cc);
                            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                                hitPoint = bhr.getLocation();
                                hitTarget = false;
                            } else {
                                hitPoint = targetPos;
                                hitTarget = true;
                            }
                        }

                        if (hitTarget) {
                            target.hurt(owner.damageSources().playerAttack(owner), LASER_DAMAGE);
                        }

                        e.firing = true;
                        e.laserEnd = hitPoint;
                        e.fireCooldown = FIRE_COOLDOWN_MIN + RAND.nextInt(FIRE_COOLDOWN_MAX - FIRE_COOLDOWN_MIN + 1);
                        e.aimTicks = 0;
                    } else {
                        e.firing = e.aimTicks > AIM_THRESHOLD / 2;
                        e.laserEnd = null;
                    }
                }
            }

            if (tick % PACKET_INTERVAL == 0) sendRenderPacket();
        }

        private void updateBlinking(Eye eye) {
            if (eye.isBlinking) {
                eye.blinkPhase += eye.blinkSpeed;
                if (eye.blinkPhase >= 1.0f) {
                    eye.isBlinking = false;
                    eye.blinkPhase = 0.0f;
                    eye.blinkCooldown = 40 + RAND.nextInt(160); // 2-10 seconds until next blink
                }
            } else {
                if (eye.blinkCooldown > 0) {
                    eye.blinkCooldown--;
                } else {
                    // Start a new blink
                    eye.isBlinking = true;
                    eye.blinkPhase = 0.0f;
                    eye.blinkSpeed = 0.12f + RAND.nextFloat() * 0.16f; // 0.12 to 0.28 speed
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
                // Send blinking state and phase for synchronized animation
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

            // Orbital system
            int orbitalRing = 0; // 0, 1, or 2
            double orbitalAngle = 0.0; // current angle on the orbital

            // Enhanced blinking
            int blinkCooldown = 60;
            boolean isBlinking = false;
            float blinkPhase = 0.0f; // 0.0 to 1.0 blink animation
            float blinkSpeed = 0.15f;
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent evt) {
            if (evt.phase != TickEvent.Phase.END) return;
            tickAll();
        }
    }
}