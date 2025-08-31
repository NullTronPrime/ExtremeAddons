package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.enchantment.ModEnchantments;
import net.autismicannoyance.exadditions.enchantment.RingCapacityEnchantment;
import net.autismicannoyance.exadditions.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
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
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = "exadditions")
public class ReflectCharmEvents {
    private static final String NBT_REFLECTED_AT = "exadditions:reflected_at";
    private static final String NBT_REFLECTED_BY = "exadditions:reflected_by";
    private static final String NBT_COOLDOWN_END = "ReflectCharmCooldown";
    private static final String NBT_RING_DATA = "ReflectCharmRings";

    private static final Map<UUID, List<RingData>> ACTIVE_RINGS = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_SHIFT_STATE = new HashMap<>();

    // ======================================================
    // ENCHANTMENT HANDLING - Clear rings when enchantments change
    // ======================================================
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // Check if we're enchanting a Reflect Charm
        if (left.is(ModItems.REFLECT_CHARM.get()) ||
                (event.getOutput() != null && event.getOutput().is(ModItems.REFLECT_CHARM.get()))) {

            // Clear ring data when enchantments are modified
            if (event.getOutput() != null) {
                clearRingData(event.getOutput());
            }
        }
    }

    // ======================================================
    // PROJECTILE REFLECTION (consumes durability, respects cooldown)
    // ======================================================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        HitResult hit = event.getRayTraceResult();

        if (!(hit instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack charm = getReflectCharm(player);
        if (charm.isEmpty()) return;
        if (!player.isShiftKeyDown()) return;
        if (proj.getOwner() == player) return;

        // Check cooldown
        if (isOnCooldown(charm, player.level().getGameTime())) return;

        CompoundTag tag = proj.getPersistentData();
        long now = player.level().getGameTime();

        if (tag.getLong(NBT_REFLECTED_AT) == now) return;

        // Cancel & reflect
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

        // Consume 1 durability for projectile reflection
        if (!player.isCreative()) {
            charm.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
        }
    }

    // ======================================================
    // MELEE REFLECTION (consumes rings and durability)
    // ======================================================
    @SubscribeEvent
    public static void onMeleeHit(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack charm = getReflectCharm(player);
        if (charm.isEmpty()) return;
        if (!player.isShiftKeyDown()) return;

        // Check cooldown
        if (isOnCooldown(charm, player.level().getGameTime())) return;

        LivingEntity attacker = null;
        if (event.getSource().getEntity() instanceof LivingEntity living) {
            attacker = living;
        }
        if (attacker == null) return;

        UUID id = player.getUUID();
        List<RingData> rings = ACTIVE_RINGS.get(id);
        if (rings == null) return;

        // Find first intact ring
        for (RingData ring : rings) {
            if (!ring.broken) {
                // Cancel player damage
                event.setCanceled(true);

                // Reflect exact damage to attacker
                attacker.hurt(event.getSource(), event.getAmount());

                // Apply knockback
                attacker.push(player.getLookAngle().x * 0.3, 0.2, player.getLookAngle().z * 0.3);

                // Break ring and consume 2 durability
                breakRing((ServerLevel)player.level(), player, ring, charm);

                // Consume 2 durability for ring break
                if (!player.isCreative()) {
                    charm.hurtAndBreak(2, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
                }
                return;
            }
        }
    }

    private static void breakRing(ServerLevel level, Player player, RingData ring, ItemStack charm) {
        ring.broken = true;
        ring.brokenAt = level.getGameTime();

        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 1f);

        // Burst of fire + smoke
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

        // Save ring states to charm NBT
        saveRingDataToCharm(charm, ACTIVE_RINGS.get(player.getUUID()));

        // If all rings broken → explosion + cleanup + cooldown
        if (ACTIVE_RINGS.get(player.getUUID()).stream().allMatch(r -> r.broken)) {
            float power = 0f;
            for (RingData r : ACTIVE_RINGS.get(player.getUUID())) {
                power += (3 * 2) * r.radius; // 3 hearts (6 dmg) * radius
            }

            level.explode(player, player.getX(), player.getY(), player.getZ(), power, Level.ExplosionInteraction.NONE);

            // Remove rings completely
            ACTIVE_RINGS.remove(player.getUUID());

            // Set cooldown in item NBT (30 seconds)
            CompoundTag charmTag = charm.getOrCreateTag();
            charmTag.putLong(NBT_COOLDOWN_END, level.getGameTime() + 20 * 30);

            // Remove ring data from charm
            charmTag.remove(NBT_RING_DATA);

            // Consume 10 durability for explosion
            if (!player.isCreative()) {
                charm.hurtAndBreak(10, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
            }
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
        ItemStack charm = getReflectCharm(player);
        boolean active = player.isShiftKeyDown() && !charm.isEmpty();

        // Stop movement when sneaking and charm is present
        if (active && !isOnCooldown(charm, level.getGameTime())) {
            player.setDeltaMovement(0, Math.min(player.getDeltaMovement().y, 0), 0);
            player.hurtMarked = true;
        }

        boolean lastShift = LAST_SHIFT_STATE.getOrDefault(id, false);
        if (active && !lastShift) {
            // Only generate rings if not on cooldown
            if (!isOnCooldown(charm, level.getGameTime())) {
                List<RingData> rings = loadRingDataFromCharm(charm);
                // Always regenerate rings if they don't match the current enchantment level
                if (rings == null || !ringsMatchEnchantment(rings, charm)) {
                    System.out.println("DEBUG: Regenerating rings - old count: " + (rings != null ? rings.size() : "none"));
                    clearRingData(charm); // Force clear old data
                    rings = generateRings(player.getRandom(), charm);
                    saveRingDataToCharm(charm, rings);
                }
                ACTIVE_RINGS.put(id, rings);
            }
        } else if (!active && lastShift) {
            // When stopping sneak, keep broken rings persistent
            List<RingData> rings = ACTIVE_RINGS.get(id);
            if (rings != null) {
                saveRingDataToCharm(charm, rings);
            }
        }

        // Show ring visuals if active and not on cooldown
        List<RingData> rings = ACTIVE_RINGS.get(id);
        if (active && rings != null && !isOnCooldown(charm, level.getGameTime())) {
            ServerLevel server = (ServerLevel) level;
            long gameTime = level.getGameTime();

            for (RingData ring : rings) {
                showRingParticles(server, player, ring, gameTime);
            }
        } else if (!active) {
            // Clear rings from memory when not active
            ACTIVE_RINGS.remove(id);
        }

        LAST_SHIFT_STATE.put(id, active);
    }

    private static void showRingParticles(ServerLevel server, Player player, RingData ring, long gameTime) {
        double angleBase = gameTime * ring.angularSpeed + ring.offset;
        double circumference = 2 * Math.PI * ring.radius;
        int points = Math.max(24, (int)(circumference * 8));

        // Ring particles
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

        // Orbital particles - 2-3 balls moving much slower along each ring
        int orbitalCount = ring.broken ? 1 : 2; // Fewer orbitals for broken rings
        for (int orb = 0; orb < orbitalCount; orb++) {
            // Each orbital has its own phase offset and much slower speed
            double orbitalPhase = (2 * Math.PI * orb / orbitalCount) + ring.orbitalOffset;
            double orbitalSpeed = ring.angularSpeed * 0.1 * (0.8 + 0.4 * orb); // Much slower: 10% of ring speed
            double orbitalAngle = gameTime * orbitalSpeed + orbitalPhase;

            double cos = Math.cos(orbitalAngle), sin = Math.sin(orbitalAngle);
            double x = ring.axisX * cos * ring.radius + ring.perpX * sin * ring.radius;
            double y = ring.axisY * cos * ring.radius + ring.perpY * sin * ring.radius;
            double z = ring.axisZ * cos * ring.radius + ring.perpZ * sin * ring.radius;

            if (!ring.broken) {
                // Bright orbitals for intact rings - spawn multiple particles for visibility
                server.sendParticles(ParticleTypes.END_ROD,
                        player.getX()+x, player.getY()+1.0+y, player.getZ()+z, 3, 0.05, 0.05, 0.05, 0.01);
                // Add a glowing core
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX()+x, player.getY()+1.0+y, player.getZ()+z, 1, 0, 0, 0, 0);
            } else {
                // Dimmer orbitals for broken rings
                server.sendParticles(ParticleTypes.ASH,
                        player.getX()+x, player.getY()+1.0+y, player.getZ()+z, 2, 0.03, 0.03, 0.03, 0.005);
            }
        }
    }

    private static boolean ringsMatchEnchantment(List<RingData> rings, ItemStack charm) {
        if (rings == null) return false;

        int maxPossibleRings = RingCapacityEnchantment.getRingCount(charm);
        int currentRingCount = rings.size();

        System.out.println("DEBUG: Checking ring match - current: " + currentRingCount + ", max possible: " + maxPossibleRings);

        // If we have way fewer rings than we should (indicating old data), regenerate
        // Or if we have more rings than the current enchantment allows, regenerate
        boolean shouldRegenerate = currentRingCount < (maxPossibleRings - 3) || currentRingCount > maxPossibleRings;

        System.out.println("DEBUG: Should regenerate: " + shouldRegenerate);

        return !shouldRegenerate;
    }

    private static void clearRingData(ItemStack charm) {
        if (!charm.isEmpty()) {
            CompoundTag charmTag = charm.getOrCreateTag();
            charmTag.remove(NBT_RING_DATA);
        }
    }

    private static boolean isOnCooldown(ItemStack charm, long currentTime) {
        if (charm.isEmpty()) return false;
        CompoundTag tag = charm.getOrCreateTag();
        long cooldownEnd = tag.getLong(NBT_COOLDOWN_END);
        return currentTime < cooldownEnd;
    }

    private static void saveRingDataToCharm(ItemStack charm, List<RingData> rings) {
        if (rings == null || rings.isEmpty()) return;

        CompoundTag charmTag = charm.getOrCreateTag();
        CompoundTag ringData = new CompoundTag();

        for (int i = 0; i < rings.size(); i++) {
            RingData ring = rings.get(i);
            CompoundTag ringTag = new CompoundTag();
            ringTag.putDouble("radius", ring.radius);
            ringTag.putDouble("angularSpeed", ring.angularSpeed);
            ringTag.putDouble("offset", ring.offset);
            ringTag.putDouble("orbitalOffset", ring.orbitalOffset);
            ringTag.putDouble("axisX", ring.axisX);
            ringTag.putDouble("axisY", ring.axisY);
            ringTag.putDouble("axisZ", ring.axisZ);
            ringTag.putDouble("perpX", ring.perpX);
            ringTag.putDouble("perpY", ring.perpY);
            ringTag.putDouble("perpZ", ring.perpZ);
            ringTag.putBoolean("broken", ring.broken);
            ringTag.putLong("brokenAt", ring.brokenAt);

            ringData.put("ring_" + i, ringTag);
        }

        charmTag.put(NBT_RING_DATA, ringData);
    }

    private static List<RingData> loadRingDataFromCharm(ItemStack charm) {
        if (charm.isEmpty()) return null;

        CompoundTag charmTag = charm.getOrCreateTag();
        if (!charmTag.contains(NBT_RING_DATA)) return null;

        CompoundTag ringData = charmTag.getCompound(NBT_RING_DATA);
        List<RingData> rings = new ArrayList<>();

        for (String key : ringData.getAllKeys()) {
            if (key.startsWith("ring_")) {
                CompoundTag ringTag = ringData.getCompound(key);
                RingData ring = new RingData(
                        ringTag.getDouble("radius"),
                        ringTag.getDouble("angularSpeed"),
                        ringTag.getDouble("offset"),
                        ringTag.getDouble("orbitalOffset"),
                        new Vec3(ringTag.getDouble("axisX"), ringTag.getDouble("axisY"), ringTag.getDouble("axisZ")),
                        new Vec3(ringTag.getDouble("perpX"), ringTag.getDouble("perpY"), ringTag.getDouble("perpZ"))
                );
                ring.broken = ringTag.getBoolean("broken");
                ring.brokenAt = ringTag.getLong("brokenAt");
                rings.add(ring);
            }
        }

        return rings.isEmpty() ? null : rings;
    }

    private static ItemStack getReflectCharm(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (main.is(ModItems.REFLECT_CHARM.get())) return main;
        if (off.is(ModItems.REFLECT_CHARM.get())) return off;

        return ItemStack.EMPTY;
    }

    private static List<RingData> generateRings(RandomSource rand, ItemStack charm) {
        int maxRings = RingCapacityEnchantment.getRingCount(charm);

        // Debug logging - remove this later
        System.out.println("DEBUG: Generating rings for charm with enchantment level: " +
                charm.getEnchantmentLevel(ModEnchantments.RING_CAPACITY.get()));
        System.out.println("DEBUG: Max rings allowed: " + maxRings);

        // Generate closer to max rings (75% chance of getting max-2 to max rings)
        int minRings = Math.max(3, maxRings - 2);
        int count = minRings + rand.nextInt(3); // Will generate minRings to minRings+2
        count = Math.min(count, maxRings); // Don't exceed max

        System.out.println("DEBUG: Actually generating: " + count + " rings");

        List<RingData> rings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double radius = 1.0 + rand.nextDouble() * 3.0;
            double speed = Mth.lerp((float)((radius - 1) / 3.0), 1.0, 0.2);
            double orbitalOffset = rand.nextDouble() * Math.PI * 2;
            Vec3 axis = new Vec3(rand.nextDouble()*2-1, rand.nextDouble()*2-1, rand.nextDouble()*2-1).normalize();
            Vec3 temp = Math.abs(axis.x)<0.9 ? new Vec3(1,0,0) : new Vec3(0,1,0);
            Vec3 perp = axis.cross(temp).normalize();
            rings.add(new RingData(radius, speed*(2*Math.PI/20), rand.nextDouble()*Math.PI*2, orbitalOffset, axis, perp));
        }

        System.out.println("DEBUG: Successfully created " + rings.size() + " ring objects");
        return rings;
    }

    private static class RingData {
        final double radius;
        final double angularSpeed;
        final double offset;
        final double orbitalOffset; // Phase offset for orbital particles
        final double axisX, axisY, axisZ;
        final double perpX, perpY, perpZ;
        boolean broken = false;
        long brokenAt = -1;

        RingData(double r, double speed, double off, double orbOff, Vec3 axis, Vec3 perp) {
            this.radius = r;
            this.angularSpeed = speed;
            this.offset = off;
            this.orbitalOffset = orbOff;
            this.axisX = axis.x; this.axisY = axis.y; this.axisZ = axis.z;
            this.perpX = perp.x; this.perpY = perp.y; this.perpZ = perp.z;
        }
    }
}