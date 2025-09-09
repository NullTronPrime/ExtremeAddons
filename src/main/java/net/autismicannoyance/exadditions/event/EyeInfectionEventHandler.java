package net.autismicannoyance.exadditions.event;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.item.ModItems;
import net.autismicannoyance.exadditions.item.custom.StaffOfEyesItem;
import net.autismicannoyance.exadditions.network.EyeRenderPacket;
import net.autismicannoyance.exadditions.network.ModNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event handler for managing eye infection status for mobs
 * Provides enhanced stats and eye protection through Forge event system
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EyeInfectionEventHandler {

    public static final Capability<IEyeInfection> EYE_INFECTION_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    // Active infected entities and their eye controllers
    private static final Map<UUID, InfectedEyeController> INFECTED_ENTITIES = new ConcurrentHashMap<>();

    // Attribute modifier UUIDs for consistent application/removal
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final UUID ARMOR_MODIFIER_ID = UUID.fromString("87654321-4321-8765-cba9-987654321cba");

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof LivingEntity && !(event.getObject() instanceof net.minecraft.world.entity.player.Player)) {
            event.addCapability(new ResourceLocation(ExAdditions.MOD_ID, "eye_infection"),
                    new EyeInfectionProvider());
        }
    }

    /**
     * Infect a mob with eye symbiosis
     */
    public static boolean infectMob(LivingEntity mob) {
        if (mob instanceof net.minecraft.world.entity.player.Player) return false; // No infecting players

        return mob.getCapability(EYE_INFECTION_CAPABILITY).map(infection -> {
            if (infection.isInfected()) return false; // Already infected

            infection.setInfected(true);
            applyInfectionEffects(mob, infection);

            return true;
        }).orElse(false);
    }

    /**
     * Remove infection from a mob
     */
    public static void cureInfection(LivingEntity mob) {
        mob.getCapability(EYE_INFECTION_CAPABILITY).ifPresent(infection -> {
            if (infection.isInfected()) {
                infection.setInfected(false);
                removeInfectionEffects(mob);

                // Remove eye controller
                INFECTED_ENTITIES.remove(mob.getUUID());
                sendClearEyePacket(mob);
            }
        });
    }

    private static void applyInfectionEffects(LivingEntity mob, IEyeInfection infection) {
        // Double health (stored as max health boost)
        double currentMaxHealth = mob.getMaxHealth();
        double healthBoost = currentMaxHealth; // 100% boost = 2x total

        mob.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(
                new AttributeModifier(HEALTH_MODIFIER_ID, "Eye Infection Health Boost",
                        healthBoost, AttributeModifier.Operation.ADDITION));

        // Heal to full with new max health
        mob.setHealth(mob.getMaxHealth());

        // Add 10 base armor then double total armor
        double currentArmor = mob.getAttributeValue(Attributes.ARMOR);
        double armorBoost = 10 + currentArmor; // +10 then double total

        mob.getAttribute(Attributes.ARMOR).addPermanentModifier(
                new AttributeModifier(ARMOR_MODIFIER_ID, "Eye Infection Armor Boost",
                        armorBoost, AttributeModifier.Operation.ADDITION));

        // Store original values for potential restoration
        infection.setOriginalMaxHealth(currentMaxHealth);
        infection.setOriginalArmor(currentArmor);

        // Create eye controller for this infected mob
        InfectedEyeController controller = new InfectedEyeController(mob);
        INFECTED_ENTITIES.put(mob.getUUID(), controller);

        // Visual effect - spawn particle burst or similar
        spawnInfectionVisualEffect(mob);
    }

    private static void removeInfectionEffects(LivingEntity mob) {
        // Remove attribute modifiers
        mob.getAttribute(Attributes.MAX_HEALTH).removeModifier(HEALTH_MODIFIER_ID);
        mob.getAttribute(Attributes.ARMOR).removeModifier(ARMOR_MODIFIER_ID);

        // Adjust current health if it exceeds new max
        if (mob.getHealth() > mob.getMaxHealth()) {
            mob.setHealth(mob.getMaxHealth());
        }
    }

    private static void spawnInfectionVisualEffect(LivingEntity mob) {
        // Spawn some particles or visual effect to indicate infection
        if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Vec3 pos = mob.position().add(0, mob.getBbHeight() * 0.5, 0);

            // Spawn red particles in a burst pattern
            for (int i = 0; i < 20; i++) {
                double angle = (i / 20.0) * Math.PI * 2;
                double radius = 1.5;
                Vec3 particlePos = pos.add(
                        Math.cos(angle) * radius,
                        mob.getRandom().nextGaussian() * 0.5,
                        Math.sin(angle) * radius
                );

                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,
                        particlePos.x, particlePos.y, particlePos.z,
                        1, 0, 0, 0, 0.1
                );
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity entity = event.getEntity();
        InfectedEyeController controller = INFECTED_ENTITIES.get(entity.getUUID());

        if (controller != null) {
            if (entity.isRemoved() || !isEntityInfected(entity)) {
                INFECTED_ENTITIES.remove(entity.getUUID());
                sendClearEyePacket(entity);
            } else {
                controller.tick();
            }
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (isEntityInfected(entity)) {
            // Drop Staff of Eyes when infected mob dies
            ItemStack staff = new ItemStack(ModItems.EYE_STAFF.get());

            // Set eye count based on mob type (bigger mobs = more eyes)
            int eyeCount = calculateEyeCountForMob(entity);
            StaffOfEyesItem.setEyeCount(staff, eyeCount);

            // Drop the staff at mob location
            if (!entity.level().isClientSide()) {
                ItemEntity itemEntity = new ItemEntity(entity.level(),
                        entity.getX(), entity.getY(), entity.getZ(), staff);
                itemEntity.setPickUpDelay(10);
                entity.level().addFreshEntity(itemEntity);
            }

            // Remove infection and eye controller
            cureInfection(entity);
        }
    }

    private static int calculateEyeCountForMob(LivingEntity mob) {
        // Base eye count based on mob health and size
        float health = mob.getMaxHealth();
        double size = mob.getBbWidth() * mob.getBbHeight();

        int baseCount = (int)(health / 10.0) + (int)(size * 10);
        return Math.max(15, Math.min(60, baseCount)); // Between 15-60 eyes
    }

    public static boolean isEntityInfected(LivingEntity entity) {
        return entity.getCapability(EYE_INFECTION_CAPABILITY)
                .map(IEyeInfection::isInfected)
                .orElse(false);
    }

    private static void sendClearEyePacket(LivingEntity entity) {
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel) {
            EyeRenderPacket clearPacket = new EyeRenderPacket(entity.getId(), Collections.emptyList());
            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                    clearPacket
            );
        }
    }

    /**
     * Controller for infected mobs - similar to StaffOfEyesItem controller but with different behavior
     */
    private static class InfectedEyeController {
        private final LivingEntity owner;
        private final List<InfectedEye> eyes = new ArrayList<>();
        private final Random rand = new Random();
        private int tick = 0;

        // Infection-specific constants (much lower cooldowns, no knockback)
        private static final double LOCK_DISTANCE = 16.0; // Slightly longer range
        private static final double SEARCH_RADIUS = 20.0;
        private static final int PACKET_INTERVAL = 3; // Slightly slower updates
        private static final float LASER_DAMAGE = 6.0f; // Higher damage
        private static final int FIRE_COOLDOWN_MIN = 2; // 10x faster (was 15)
        private static final int FIRE_COOLDOWN_MAX = 5; // 10x faster (was 30)
        private static final int AIM_THRESHOLD = 3; // Faster aiming

        // Orbital parameters for infected eyes
        private static final double[] ORBITAL_RADII = {2.5, 3.5, 4.5, 5.5};
        private static final double[] ORBITAL_HEIGHTS = {1.0, 1.5, 2.0, 2.5};
        private static final double[] ORBITAL_SPEEDS = {0.015, 0.012, 0.009, 0.006};

        public InfectedEyeController(LivingEntity owner) {
            this.owner = owner;

            // Create 20-30 eyes for infected mobs
            int eyeCount = 20 + rand.nextInt(11);
            int ringsUsed = Math.min(ORBITAL_RADII.length, (eyeCount + 6) / 7);

            for (int i = 0; i < eyeCount; i++) {
                InfectedEye eye = new InfectedEye();

                eye.orbitalRing = i % ringsUsed;
                double eyesInThisRing = Math.ceil((double)eyeCount / ringsUsed);
                double angleStep = (Math.PI * 2.0) / eyesInThisRing;
                double ringIndex = i / (double)ringsUsed;
                eye.orbitalAngle = ringIndex * angleStep + rand.nextDouble() * 0.5;

                updateEyeOrbitalPosition(eye);

                eye.width = 0.7f + rand.nextFloat() * 0.6f;
                eye.height = eye.width * 0.45f;
                eye.fireCooldown = rand.nextInt(FIRE_COOLDOWN_MAX);
                eye.aimTicks = 0;

                // Infected eyes blink less frequently
                eye.blinkCooldown = 80 + rand.nextInt(200);
                eye.isBlinking = false;
                eye.blinkPhase = 0.0f;
                eye.blinkSpeed = 0.1f + rand.nextFloat() * 0.08f;

                eyes.add(eye);
            }
        }

        private void updateEyeOrbitalPosition(InfectedEye eye) {
            int ring = Math.min(eye.orbitalRing, ORBITAL_RADII.length - 1);
            double radius = ORBITAL_RADII[ring];
            double height = ORBITAL_HEIGHTS[ring] + Math.sin(tick * 0.008 + eye.hashCode()) * 0.2;

            double x = Math.cos(eye.orbitalAngle) * radius;
            double z = Math.sin(eye.orbitalAngle) * radius;

            eye.offset = new Vec3(x, height, z);
        }

        public void tick() {
            if (owner.isRemoved()) return;
            tick++;

            // Find hostile targets (things that are attacking the infected mob)
            LivingEntity target = findHostileTarget();

            boolean locked = false;
            if (target != null && target != owner) {
                double d2 = target.distanceToSqr(owner);
                if (d2 <= LOCK_DISTANCE * LOCK_DISTANCE) locked = true;
            }

            for (InfectedEye eye : eyes) {
                updateBlinking(eye);

                if (!locked || target == null) {
                    // Idle orbital movement
                    int ring = Math.min(eye.orbitalRing, ORBITAL_SPEEDS.length - 1);
                    eye.orbitalAngle += ORBITAL_SPEEDS[ring];
                    if (eye.orbitalAngle > Math.PI * 2.0) eye.orbitalAngle -= Math.PI * 2.0;

                    updateEyeOrbitalPosition(eye);

                    Vec3 eyeWorld = owner.position().add(eye.offset);
                    Vec3 ownerCenter = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
                    Vec3 lookToOwner = ownerCenter.subtract(eyeWorld);
                    if (lookToOwner.length() > 1e-6) {
                        eye.look = lookToOwner.normalize();
                    }

                    eye.aimTicks = 0;
                    eye.firing = false;
                    eye.laserEnd = null;
                } else {
                    // Combat mode - much more aggressive
                    Vec3 eyeWorld = owner.position().add(eye.offset);
                    Vec3 targetCenter = getEntityCenter(target);
                    Vec3 dir = targetCenter.subtract(eyeWorld);
                    if (dir.length() > 1e-6) eye.look = dir.normalize();

                    if (eye.fireCooldown > 0) eye.fireCooldown--;
                    eye.aimTicks++;

                    boolean canFire = eye.aimTicks >= AIM_THRESHOLD && eye.fireCooldown <= 0;

                    if (canFire) {
                        // Perform laser attack without knockback
                        performInfectedLaser(eyeWorld, targetCenter, target);

                        eye.firing = true;
                        eye.laserEnd = targetCenter;
                        eye.fireCooldown = FIRE_COOLDOWN_MIN + rand.nextInt(FIRE_COOLDOWN_MAX - FIRE_COOLDOWN_MIN + 1);
                        eye.aimTicks = 0;
                    } else {
                        eye.firing = eye.aimTicks > AIM_THRESHOLD / 2;
                        eye.laserEnd = null;
                    }
                }
            }

            if (tick % PACKET_INTERVAL == 0) sendRenderPacket();
        }

        private Vec3 getEntityCenter(LivingEntity entity) {
            return new Vec3(
                    entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5,
                    entity.getZ()
            );
        }

        private void performInfectedLaser(Vec3 start, Vec3 target, LivingEntity targetEntity) {
            // Direct laser without knockback
            net.minecraft.world.damagesource.DamageSource damageSource =
                    owner.damageSources().mobAttack(owner);

            targetEntity.hurt(damageSource, LASER_DAMAGE);

            // No knockback for infected mob lasers - they're more precise and deadly
            // Set on fire briefly
            targetEntity.setSecondsOnFire(2);
        }

        private LivingEntity findHostileTarget() {
            double best = SEARCH_RADIUS * SEARCH_RADIUS;
            LivingEntity bestTarget = null;

            List<LivingEntity> potentials = owner.level().getEntitiesOfClass(LivingEntity.class,
                    owner.getBoundingBox().inflate(SEARCH_RADIUS),
                    e -> e != owner && !e.isRemoved() && e.getHealth() > 0.0F && !e.isInvulnerable());

            for (LivingEntity candidate : potentials) {
                // Prioritize entities that are targeting the infected mob
                if (isHostileToOwner(candidate)) {
                    double d2 = candidate.distanceToSqr(owner);
                    if (d2 < best) {
                        best = d2;
                        bestTarget = candidate;
                    }
                }
            }

            return bestTarget;
        }

        private boolean isHostileToOwner(LivingEntity candidate) {
            // Check if candidate is attacking or targeting our infected owner
            if (candidate.getLastHurtMob() == owner) return true;
            if (candidate instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == owner) return true;
            if (candidate instanceof net.minecraft.world.entity.player.Player) return true; // Always defend against players

            // Check if different mob types
            return !candidate.getClass().equals(owner.getClass());
        }

        private void updateBlinking(InfectedEye eye) {
            if (eye.isBlinking) {
                eye.blinkPhase += eye.blinkSpeed;
                if (eye.blinkPhase >= 1.0f) {
                    eye.isBlinking = false;
                    eye.blinkPhase = 0.0f;
                    eye.blinkCooldown = 80 + rand.nextInt(200);
                }
            } else {
                if (eye.blinkCooldown > 0) {
                    eye.blinkCooldown--;
                } else {
                    eye.isBlinking = true;
                    eye.blinkPhase = 0.0f;
                    eye.blinkSpeed = 0.1f + rand.nextFloat() * 0.08f;
                }
            }
        }

        private void sendRenderPacket() {
            List<EyeRenderPacket.EyeEntry> entries = new ArrayList<>(eyes.size());
            for (InfectedEye eye : eyes) {
                entries.add(new EyeRenderPacket.EyeEntry(
                        eye.offset, eye.firing, eye.laserEnd, eye.look, -1,
                        eye.isBlinking, eye.blinkPhase));
            }
            EyeRenderPacket packet = new EyeRenderPacket(owner.getId(), entries);

            if (owner.level() instanceof net.minecraft.server.level.ServerLevel) {
                ModNetworking.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> owner),
                        packet
                );
            }
        }

        private static class InfectedEye {
            Vec3 offset = Vec3.ZERO;
            Vec3 look = new Vec3(0,0,1);
            boolean firing = false;
            Vec3 laserEnd = null;
            float width = 0.8f, height = 0.4f;
            int fireCooldown = 0;
            int aimTicks = 0;

            int orbitalRing = 0;
            double orbitalAngle = 0.0;

            int blinkCooldown = 80;
            boolean isBlinking = false;
            float blinkPhase = 0.0f;
            float blinkSpeed = 0.1f;
        }
    }

    // Capability interface and implementation
    public interface IEyeInfection {
        boolean isInfected();
        void setInfected(boolean infected);
        double getOriginalMaxHealth();
        void setOriginalMaxHealth(double health);
        double getOriginalArmor();
        void setOriginalArmor(double armor);
        CompoundTag serializeNBT();
        void deserializeNBT(CompoundTag nbt);
    }

    public static class EyeInfection implements IEyeInfection {
        private boolean infected = false;
        private double originalMaxHealth = 20.0;
        private double originalArmor = 0.0;

        @Override
        public boolean isInfected() { return infected; }

        @Override
        public void setInfected(boolean infected) { this.infected = infected; }

        @Override
        public double getOriginalMaxHealth() { return originalMaxHealth; }

        @Override
        public void setOriginalMaxHealth(double health) { this.originalMaxHealth = health; }

        @Override
        public double getOriginalArmor() { return originalArmor; }

        @Override
        public void setOriginalArmor(double armor) { this.originalArmor = armor; }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("infected", infected);
            tag.putDouble("originalMaxHealth", originalMaxHealth);
            tag.putDouble("originalArmor", originalArmor);
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            infected = nbt.getBoolean("infected");
            originalMaxHealth = nbt.getDouble("originalMaxHealth");
            originalArmor = nbt.getDouble("originalArmor");
        }
    }

    public static class EyeInfectionProvider implements ICapabilityProvider {
        private final LazyOptional<IEyeInfection> instance = LazyOptional.of(EyeInfection::new);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
            return cap == EYE_INFECTION_CAPABILITY ? instance.cast() : LazyOptional.empty();
        }
    }
}