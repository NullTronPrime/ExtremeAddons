package net.autismicannoyance.exadditions.item.custom;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.network.EyeEffectPacket;
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
 * StaffOfEyes item with merged server-side EyeController.
 * - max 10 eyes
 * - fixed offsets when locked (<= 8 blocks), eyes rotate in place to face the target
 * - server-side raytrace; damage credited to player
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
        private static final int MAX_EYES = 10;            // cap at 10
        private static final double LOCK_DISTANCE = 8.0;   // <= 8 blocks -> lock and aim
        private static final double SEARCH_RADIUS = 12.0;  // search radius for targets
        private static final int PACKET_INTERVAL = 4;      // ticks between updates
        private static final float LASER_DAMAGE = 6.0f;    // damage per hit
        private static final int AIM_THRESHOLD = 10;       // 10 ticks aim before firing
        private static final int FIRE_COOLDOWN_MIN = 20;   // per-eye cooldown min
        private static final int FIRE_COOLDOWN_MAX = 80;   // per-eye cooldown max
        private static final int VIS_LIFETIME = 40;        // client lifetime if server stops sending

        private final ServerPlayer owner;
        private final List<Eye> eyes = new ArrayList<>();
        private int tick = 0;

        private EyeController(ServerPlayer owner) {
            this.owner = owner;
            int count = Math.min(MAX_EYES, 5 + RAND.nextInt(6)); // 5..10
            for (int i = 0; i < count; i++) {
                Eye e = new Eye();
                e.offset = pickInitialOffset(i, count);
                e.width = 0.6f + RAND.nextFloat() * 0.9f;
                e.height = e.width * 0.5f;
                e.fireCooldown = FIRE_COOLDOWN_MIN + RAND.nextInt(FIRE_COOLDOWN_MAX - FIRE_COOLDOWN_MIN + 1); // different cooldown per-eye
                e.aimTicks = 0;
                eyes.add(e);
            }
            // inform client to spawn visual eyes (keeps visuals identical)
            EyeEffectPacket spawn = new EyeEffectPacket(owner.getId(), eyes.size(), -1);
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> owner), spawn);
        }

        public static void ensure(ServerPlayer p) {
            INSTANCES.computeIfAbsent(p.getUUID(), u -> new EyeController(p));
        }

        public static void remove(ServerPlayer p) {
            EyeController removed = INSTANCES.remove(p.getUUID());
            if (removed != null) {
                EyeEffectPacket stop = new EyeEffectPacket(p.getId(), 0, 0);
                ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), stop);
                EyeRenderPacket clear = new EyeRenderPacket(p.getId(), Collections.emptyList());
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
            for (UUID u : toRemove) INSTANCES.remove(u);
        }

        private static boolean isPlayerHoldingStaff(ServerPlayer p) {
            ItemStack main = p.getMainHandItem();
            return main.getItem() instanceof StaffOfEyesItem;
        }

        private void tick() {
            if (owner.isRemoved()) return;
            tick++;

            // find nearest target within SEARCH_RADIUS
            LivingEntity target = findNearestTargetWithin(SEARCH_RADIUS);

            boolean locked = false;
            if (target != null) {
                double d2 = target.distanceToSqr(owner);
                if (d2 <= LOCK_DISTANCE * LOCK_DISTANCE) locked = true;
            }

            for (Eye e : eyes) {
                if (!locked) {
                    // idle: slow orbit around player
                    double ang = Math.atan2(e.offset.z, e.offset.x) + 0.01 * (0.5 + RAND.nextDouble());
                    double r = Math.sqrt(e.offset.x * e.offset.x + e.offset.z * e.offset.z);
                    e.offset = new Vec3(Math.cos(ang) * r, e.offset.y, Math.sin(ang) * r);
                    e.aimTicks = 0;
                    e.firing = false;
                    e.laserEnd = null;
                } else {
                    // locked: maintain fixed offset relative to player, compute look vector to target center
                    Vec3 eyeWorld = owner.position().add(e.offset);
                    Vec3 targetPos = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
                    Vec3 dir = targetPos.subtract(eyeWorld);
                    if (dir.length() > 1e-6) e.look = dir.normalize();

                    // charge/aim then fire if cooldown allows
                    if (e.fireCooldown > 0) e.fireCooldown--;
                    e.aimTicks++;
                    if (e.aimTicks >= AIM_THRESHOLD && e.fireCooldown <= 0) {
                        // perform raytrace from eyeWorld towards targetPos; prefer entity hit, then block
                        Vec3 hitPoint = targetPos;
                        int hitEntityId = -1;

                        // 1) try entity hit (ProjectileUtil helper)
                        Vec3 start = eyeWorld;
                        Vec3 end = targetPos;
                        // expand bbox slightly along path to catch thin entities
                        AABB searchBox = owner.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
                        EntityHitResult entResult = ProjectileUtil.getEntityHitResult(owner.level(), owner, start, end, searchBox,
                                e2 -> (e2 instanceof LivingEntity) && !e2.isRemoved() && e2 != owner);
                        if (entResult != null) {
                            hitPoint = entResult.getLocation();
                            hitEntityId = entResult.getEntity().getId();
                        } else {
                            // 2) block hit
                            ClipContext cc = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner);
                            BlockHitResult bhr = owner.level().clip(cc);
                            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                                hitPoint = bhr.getLocation();
                                hitEntityId = -1;
                            } else {
                                // fallback: target center
                                hitPoint = targetPos;
                                hitEntityId = target.getId();
                            }
                        }

                        // apply damage to hit entity if there is one
                        if (hitEntityId != -1) {
                            Entity hit = owner.level().getEntity(hitEntityId);
                            if (hit instanceof LivingEntity living) {
                                living.hurt(owner.damageSources().playerAttack(owner), LASER_DAMAGE);
                            }
                        }

                        // set firing state and laserEnd point
                        e.firing = true;
                        e.laserEnd = hitPoint;

                        // reset cooldown and aimTicks
                        e.fireCooldown = FIRE_COOLDOWN_MIN + RAND.nextInt(FIRE_COOLDOWN_MAX - FIRE_COOLDOWN_MIN + 1);
                        e.aimTicks = 0;
                    } else {
                        e.firing = false;
                        e.laserEnd = null;
                    }
                }
            }

            if (tick % PACKET_INTERVAL == 0) sendRenderPacket();
        }

        private LivingEntity findNearestTargetWithin(double radius) {
            double best = radius * radius;
            LivingEntity bestE = null;
            List<LivingEntity> list = owner.level().getEntitiesOfClass(LivingEntity.class,
                    owner.getBoundingBox().inflate(radius),
                    e -> e != owner && !e.isRemoved() && e.getHealth() > 0.0F);
            for (LivingEntity le : list) {
                double d2 = le.distanceToSqr(owner);
                if (d2 < best) { best = d2; bestE = le; }
            }
            return bestE;
        }

        private void sendRenderPacket() {
            List<EyeRenderPacket.EyeEntry> entries = new ArrayList<>(eyes.size());
            for (Eye e : eyes) {
                entries.add(new EyeRenderPacket.EyeEntry(e.offset, e.firing, e.laserEnd, e.firing ? ((e.laserEnd == null) ? -1 : owner.level().getEntitiesOfClass(Entity.class, owner.getBoundingBox().inflate(0)).stream().findFirst().map(Entity::getId).orElse(-1)) : -1));
                // NOTE: hitEntityId is mostly optional on client; laserEnd + firing are primary.
                // We send -1 here for simplicity; laserEnd contains the real hit point from server above.
            }
            EyeRenderPacket pkt = new EyeRenderPacket(owner.getId(), entries);
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> owner), pkt);
        }

        private Vec3 pickInitialOffset(int index, int count) {
            double ang = (index / (double) count) * Math.PI * 2.0 + RAND.nextDouble() * 0.2;
            double radius = 3.0 + RAND.nextDouble() * 3.0;
            double y = 0.5 + RAND.nextDouble() * 2.0;
            return new Vec3(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
        }

        private static final class Eye {
            Vec3 offset = Vec3.ZERO;     // fixed offset relative to owner
            Vec3 look = new Vec3(0,0,1);
            boolean firing = false;
            Vec3 laserEnd = null;
            float width = 0.8f, height = 0.4f;
            int fireCooldown = 0;
            int aimTicks = 0;
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent evt) {
            if (evt.phase != TickEvent.Phase.END) return;
            tickAll();
        }
    }
}
