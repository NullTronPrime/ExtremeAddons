package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = "exadditions")
public class ReflectCharmEvents {
    private static final String NBT_REFLECTED_AT = "exadditions:reflected_at";
    private static final String NBT_REFLECTED_BY = "exadditions:reflected_by";

    private static final Map<UUID, List<RingData>> ACTIVE_RINGS = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_SHIFT_STATE = new HashMap<>();
    private static final Map<UUID, Long> RING_COOLDOWNS = new HashMap<>();

    // ======================================================
    // PROJECTILE REFLECTION (does not consume rings)
    // ======================================================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        HitResult hit = event.getRayTraceResult();

        if (!(hit instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        if (!hasReflectCharm(player)) return;
        if (!player.isShiftKeyDown()) return;
        if (proj.getOwner() == player) return;

        CompoundTag tag = proj.getPersistentData();
        long now = player.level().getGameTime();

        if (tag.getLong(NBT_REFLECTED_AT) == now) return;

        // cancel & reflect
        event.setCanceled(true);

        Vec3 oldVel = proj.getDeltaMovement();
        Vec3 newVel = oldVel.lengthSqr() < 1.0E-6
                ? player.getLookAngle().normalize().scale(-0.6)
                : oldVel.scale(-1);

        proj.setDeltaMovement(newVel);

        if (proj instanceof AbstractHurtingProjectile hurting) {
            hurting.xPower = -hurting.xPower;
            hurting.yPower = -hurting.yPower;
            hurting.zPower = -hurting.zPower;
        }

        proj.setPos(
                proj.getX() + newVel.x * 0.2,
                proj.getY() + newVel.y * 0.2,
                proj.getZ() + newVel.z * 0.2
        );

        float yRot = (float)(Mth.atan2(newVel.x, newVel.z) * (180F / Math.PI));
        float xRot = (float)(Mth.atan2(newVel.y, newVel.horizontalDistance()) * (180F / Math.PI));
        proj.setYRot(yRot);
        proj.setXRot(xRot);
        proj.yRotO = yRot;
        proj.xRotO = xRot;

        if (proj instanceof AbstractArrow arrow) {
            arrow.hasImpulse = true;
        } else {
            proj.hasImpulse = true;
        }

        proj.setOwner(player);
        tag.putLong(NBT_REFLECTED_AT, now);
        tag.putUUID(NBT_REFLECTED_BY, player.getUUID());
    }

    // ======================================================
    // MELEE REFLECTION (consumes rings)
    // ======================================================
    @SubscribeEvent
    public static void onMeleeHit(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!hasReflectCharm(player)) return;
        if (!player.isShiftKeyDown()) return;

        LivingEntity attacker = null;
        if (event.getSource().getEntity() instanceof LivingEntity living) {
            attacker = living;
        }
        if (attacker == null) return;

        UUID id = player.getUUID();
        List<RingData> rings = ACTIVE_RINGS.get(id);
        if (rings == null) return;

        // find first intact ring
        for (RingData ring : rings) {
            if (!ring.broken) {
                // cancel player damage
                event.setCanceled(true);

                // reflect exact damage to attacker
                attacker.hurt(event.getSource(), event.getAmount());

                // apply knockback (smaller than before)
                attacker.push(player.getLookAngle().x * 0.3, 0.2, player.getLookAngle().z * 0.3);

                // break ring
                breakRing((ServerLevel)player.level(), player, ring);
                return;
            }
        }
    }

    private static void breakRing(ServerLevel level, Player player, RingData ring) {
        ring.broken = true;
        ring.brokenAt = level.getGameTime();

        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 1f);

        // burst of fire + smoke
        int points = Math.max(16, (int)(ring.radius * 32));
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i / points);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double x = ring.axisX * cos * ring.radius + ring.perpX * sin * ring.radius;
            double y = ring.axisY * cos * ring.radius + ring.perpY * sin * ring.radius;
            double z = ring.axisZ * cos * ring.radius + ring.perpZ * sin * ring.radius;

            level.sendParticles(ParticleTypes.SMOKE, player.getX() + x, player.getY() + 1.0 + y, player.getZ() + z, 1, 0,0,0,0);
            level.sendParticles(ParticleTypes.FLAME, player.getX() + x, player.getY() + 1.0 + y, player.getZ() + z, 1, 0,0,0,0);
        }

        // if all rings broken → explosion + cleanup + cooldown
        if (ACTIVE_RINGS.get(player.getUUID()).stream().allMatch(r -> r.broken)) {
            float power = 0f;
            for (RingData r : ACTIVE_RINGS.get(player.getUUID())) {
                power += (3 * 2) * r.radius; // 3 hearts (6 dmg) * radius
            }

            level.explode(player, player.getX(), player.getY(), player.getZ(), power, Level.ExplosionInteraction.NONE);

            // remove rings completely (no smoke visuals)
            ACTIVE_RINGS.remove(player.getUUID());

            // set cooldown
            RING_COOLDOWNS.put(player.getUUID(), level.getGameTime() + 20*30); // 30s cooldown
        }
    }

    // ======================================================
    // RINGS + MOVEMENT CONTROL
    // ======================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        Level level = player.level();
        if (level.isClientSide || event.phase != TickEvent.Phase.END) return;

        UUID id = player.getUUID();
        boolean active = player.isShiftKeyDown() && hasReflectCharm(player);

        // stop movement when sneaking
        if (active) {
            player.setDeltaMovement(0, Math.min(player.getDeltaMovement().y, 0), 0);
            player.hurtMarked = true;
        }

        boolean lastShift = LAST_SHIFT_STATE.getOrDefault(id, false);
        if (active && !lastShift) {
            // only regen if cooldown expired
            long now = level.getGameTime();
            if (!RING_COOLDOWNS.containsKey(id) || RING_COOLDOWNS.get(id) <= now) {
                ACTIVE_RINGS.put(id, generateRings(player.getRandom()));
            }
        }

        List<RingData> rings = ACTIVE_RINGS.get(id);
        if (active && rings != null) {
            ServerLevel server = (ServerLevel) level;
            long gameTime = level.getGameTime();

            for (RingData ring : rings) {
                double angleBase = gameTime * ring.angularSpeed + ring.offset;
                double circumference = 2 * Math.PI * ring.radius;
                int points = Math.max(24, (int)(circumference * 8)); // doubled density

                for (int i = 0; i < points; i++) {
                    double angle = angleBase + (2 * Math.PI * i / points);
                    double cos = Math.cos(angle), sin = Math.sin(angle);
                    double x = ring.axisX * cos * ring.radius + ring.perpX * sin * ring.radius;
                    double y = ring.axisY * cos * ring.radius + ring.perpY * sin * ring.radius;
                    double z = ring.axisZ * cos * ring.radius + ring.perpZ * sin * ring.radius;

                    if (!ring.broken) {
                        server.sendParticles(ParticleTypes.ENCHANT, player.getX()+x, player.getY()+1.0+y, player.getZ()+z, 1,0,0,0,0);
                    } else {
                        server.sendParticles(ParticleTypes.SMOKE, player.getX()+x, player.getY()+1.0+y, player.getZ()+z, 1,0,0,0,0);
                    }
                }
            }
        }

        LAST_SHIFT_STATE.put(id, active);
    }

    private static boolean hasReflectCharm(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.is(ModItems.REFLECT_CHARM.get()) || off.is(ModItems.REFLECT_CHARM.get());
    }

    private static List<RingData> generateRings(RandomSource rand) {
        int count = 3 + rand.nextInt(5); // 3–7 rings
        List<RingData> rings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double radius = 1.0 + rand.nextDouble() * 3.0;
            double speed = Mth.lerp((float)((radius - 1) / 3.0), 1.0, 0.2);
            Vec3 axis = new Vec3(rand.nextDouble()*2-1, rand.nextDouble()*2-1, rand.nextDouble()*2-1).normalize();
            Vec3 temp = Math.abs(axis.x)<0.9 ? new Vec3(1,0,0) : new Vec3(0,1,0);
            Vec3 perp = axis.cross(temp).normalize();
            rings.add(new RingData(radius, speed*(2*Math.PI/20), rand.nextDouble()*Math.PI*2, axis, perp));
        }
        return rings;
    }

    private static class RingData {
        final double radius;
        final double angularSpeed;
        final double offset;
        final double axisX, axisY, axisZ;
        final double perpX, perpY, perpZ;
        boolean broken = false;
        long brokenAt = -1;

        RingData(double r, double speed, double off, Vec3 axis, Vec3 perp) {
            this.radius = r;
            this.angularSpeed = speed;
            this.offset = off;
            this.axisX = axis.x; this.axisY = axis.y; this.axisZ = axis.z;
            this.perpX = perp.x; this.perpY = perp.y; this.perpZ = perp.z;
        }
    }
}
