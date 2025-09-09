package net.autismicannoyance.exadditions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.autismicannoyance.exadditions.ExAdditions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced meteorite renderer that creates realistic falling meteors with proper trails
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class MeteoriteRenderer {

    private static final Map<Integer, MeteoriteInstance> activeMeteorites = new ConcurrentHashMap<>();
    private static final RandomSource random = RandomSource.create();

    public static void spawnMeteorite(Vec3 startPos, Vec3 endPos, Vec3 velocity, float size,
                                      int lifetimeTicks, int meteoriteId, boolean hasTrail,
                                      int coreColor, int trailColor, float intensity) {

        MeteoriteInstance meteorite = new MeteoriteInstance(
                startPos, endPos, velocity, size, lifetimeTicks,
                hasTrail, coreColor, trailColor, intensity, System.currentTimeMillis()
        );

        activeMeteorites.put(meteoriteId, meteorite);

        // Debug message
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§aSpawned meteor " + meteoriteId + " at " + (int)startPos.x + ", " + (int)startPos.y + ", " + (int)startPos.z), false);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, MeteoriteInstance>> iterator = activeMeteorites.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, MeteoriteInstance> entry = iterator.next();
            MeteoriteInstance meteorite = entry.getValue();

            long elapsed = currentTime - meteorite.spawnTime;
            float progress = Math.min(1.0f, elapsed / (meteorite.lifetimeTicks * 50.0f));

            if (progress >= 1.0f) {
                // Meteorite has reached the ground
                createImpactEffects(level, meteorite.endPos, meteorite.size);
                iterator.remove();
                continue;
            }

            // Calculate current position
            Vec3 currentPos = meteorite.startPos.lerp(meteorite.endPos, progress);

            // Render the meteorite and its trail
            renderMeteoriteWithTrail(level, meteorite, currentPos, progress);
        }
    }

    private static void renderMeteoriteWithTrail(ClientLevel level, MeteoriteInstance meteorite, Vec3 currentPos, float progress) {
        // Determine trail color based on meteorite size
        boolean isLarge = meteorite.size > 3.0f;
        boolean isMedium = meteorite.size > 2.0f;

        // Create trail particles behind the meteorite
        if (meteorite.hasTrail) {
            createTrailParticles(level, meteorite, currentPos, isLarge, isMedium);
        }

        // Create the main meteorite body particles
        createMeteoriteCore(level, currentPos, meteorite.size, isLarge, isMedium);

        // Add debris particles around the meteorite
        createDebrisParticles(level, currentPos, meteorite.size);
    }

    private static void createTrailParticles(ClientLevel level, MeteoriteInstance meteorite, Vec3 currentPos, boolean isLarge, boolean isMedium) {
        Vec3 direction = meteorite.velocity.normalize();
        int trailLength = Math.max(8, (int)(meteorite.size * 4));

        for (int i = 0; i < trailLength; i++) {
            float trailProgress = (float) i / trailLength;
            Vec3 trailPos = currentPos.subtract(direction.scale(trailProgress * meteorite.size * 2));

            // Add some random spread to the trail
            Vec3 offset = new Vec3(
                    (random.nextDouble() - 0.5) * meteorite.size * 0.3,
                    (random.nextDouble() - 0.5) * meteorite.size * 0.3,
                    (random.nextDouble() - 0.5) * meteorite.size * 0.3
            );
            trailPos = trailPos.add(offset);

            // Choose trail color based on size
            if (isLarge) {
                // Large meteors have blue/white hot trails
                if (random.nextFloat() < 0.7f) {
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                            trailPos.x, trailPos.y, trailPos.z, 0, 0, 0);
                } else {
                    level.addParticle(ParticleTypes.FLAME,
                            trailPos.x, trailPos.y, trailPos.z, 0, 0.1, 0);
                }
            } else if (isMedium) {
                // Medium meteors have red/orange trails
                level.addParticle(ParticleTypes.FLAME,
                        trailPos.x, trailPos.y, trailPos.z,
                        (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
            } else {
                // Small meteors have orange/yellow trails
                if (random.nextFloat() < 0.6f) {
                    level.addParticle(ParticleTypes.FLAME,
                            trailPos.x, trailPos.y, trailPos.z, 0, 0.02, 0);
                } else {
                    level.addParticle(ParticleTypes.LAVA,
                            trailPos.x, trailPos.y, trailPos.z, 0, 0, 0);
                }
            }

            // Add smoke trail for larger meteors
            if (meteorite.size > 1.5f && random.nextFloat() < 0.3f) {
                level.addParticle(ParticleTypes.LARGE_SMOKE,
                        trailPos.x, trailPos.y, trailPos.z,
                        (random.nextDouble() - 0.5) * 0.05, 0.01, (random.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    private static void createMeteoriteCore(ClientLevel level, Vec3 pos, float size, boolean isLarge, boolean isMedium) {
        // Create the rocky core using multiple particle layers
        int coreParticles = Math.max(10, (int)(size * 8)); // More particles for visibility

        for (int i = 0; i < coreParticles; i++) {
            // Inner core - bright and hot
            Vec3 coreOffset = new Vec3(
                    (random.nextDouble() - 0.5) * size * 0.5,
                    (random.nextDouble() - 0.5) * size * 0.5,
                    (random.nextDouble() - 0.5) * size * 0.5
            );
            Vec3 corePos = pos.add(coreOffset);

            if (isLarge) {
                // Large meteors: Blue-white hot core with soul fire
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        corePos.x, corePos.y, corePos.z, 0, 0, 0);
                if (random.nextFloat() < 0.3f) {
                    level.addParticle(ParticleTypes.WHITE_ASH,
                            corePos.x, corePos.y, corePos.z, 0, 0, 0);
                }
            } else if (isMedium) {
                // Medium meteors: Red-orange hot core
                level.addParticle(ParticleTypes.FLAME,
                        corePos.x, corePos.y, corePos.z, 0, 0, 0);
                if (random.nextFloat() < 0.2f) {
                    level.addParticle(ParticleTypes.LAVA,
                            corePos.x, corePos.y, corePos.z, 0, 0, 0);
                }
            } else {
                // Small meteors: Orange-yellow core
                if (random.nextFloat() < 0.8f) {
                    level.addParticle(ParticleTypes.FLAME,
                            corePos.x, corePos.y, corePos.z, 0, 0, 0);
                } else {
                    level.addParticle(ParticleTypes.LAVA,
                            corePos.x, corePos.y, corePos.z, 0, 0, 0);
                }
            }
        }

        // Outer shell - darker, rocky appearance
        int shellParticles = Math.max(5, (int)(size * 4));
        for (int i = 0; i < shellParticles; i++) {
            Vec3 shellOffset = new Vec3(
                    (random.nextDouble() - 0.5) * size * 0.8,
                    (random.nextDouble() - 0.5) * size * 0.8,
                    (random.nextDouble() - 0.5) * size * 0.8
            );
            Vec3 shellPos = pos.add(shellOffset);

            // Use darker particles for the rocky shell
            if (random.nextFloat() < 0.6f) {
                level.addParticle(ParticleTypes.LARGE_SMOKE,
                        shellPos.x, shellPos.y, shellPos.z,
                        (random.nextDouble() - 0.5) * 0.02, 0, (random.nextDouble() - 0.5) * 0.02);
            } else {
                level.addParticle(ParticleTypes.ASH,
                        shellPos.x, shellPos.y, shellPos.z, 0, 0, 0);
            }
        }

        // Add extra bright core for visibility
        level.addParticle(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 0, 0, 0);
    }

    private static void createDebrisParticles(ClientLevel level, Vec3 pos, float size) {
        // Create debris particles flying off the meteorite
        int debrisCount = Math.max(3, (int)(size * 2));

        for (int i = 0; i < debrisCount; i++) {
            Vec3 debrisVelocity = new Vec3(
                    (random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.3
            );

            Vec3 debrisPos = pos.add(
                    (random.nextDouble() - 0.5) * size * 1.2,
                    (random.nextDouble() - 0.5) * size * 1.2,
                    (random.nextDouble() - 0.5) * size * 1.2
            );

            if (random.nextFloat() < 0.4f) {
                level.addParticle(ParticleTypes.LAVA,
                        debrisPos.x, debrisPos.y, debrisPos.z,
                        debrisVelocity.x, debrisVelocity.y, debrisVelocity.z);
            } else {
                level.addParticle(ParticleTypes.FLAME,
                        debrisPos.x, debrisPos.y, debrisPos.z,
                        debrisVelocity.x, debrisVelocity.y, debrisVelocity.z);
            }
        }
    }

    private static void createImpactEffects(ClientLevel level, Vec3 impactPos, float size) {
        // Create dramatic impact effects when meteorite hits the ground
        int explosionParticles = Math.max(30, (int)(size * 50)); // More particles

        for (int i = 0; i < explosionParticles; i++) {
            Vec3 explosionVel = new Vec3(
                    (random.nextDouble() - 0.5) * 2.0,
                    random.nextDouble() * 1.5,
                    (random.nextDouble() - 0.5) * 2.0
            ).normalize().scale(random.nextDouble() * size * 0.5);

            Vec3 explosionPos = impactPos.add(
                    (random.nextDouble() - 0.5) * size,
                    random.nextDouble() * size * 0.5,
                    (random.nextDouble() - 0.5) * size
            );

            // Mix of explosion effects based on meteorite size
            float effectChoice = random.nextFloat();
            if (size > 3.0f) {
                // Large meteors - dramatic blue/white explosions
                if (effectChoice < 0.4f) {
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                            explosionPos.x, explosionPos.y, explosionPos.z,
                            explosionVel.x, explosionVel.y, explosionVel.z);
                } else if (effectChoice < 0.7f) {
                    level.addParticle(ParticleTypes.EXPLOSION,
                            explosionPos.x, explosionPos.y, explosionPos.z, 0, 0, 0);
                } else {
                    level.addParticle(ParticleTypes.WHITE_ASH,
                            explosionPos.x, explosionPos.y, explosionPos.z,
                            explosionVel.x, explosionVel.y, explosionVel.z);
                }
            } else if (size > 2.0f) {
                // Medium meteors - red/orange explosions
                if (effectChoice < 0.5f) {
                    level.addParticle(ParticleTypes.FLAME,
                            explosionPos.x, explosionPos.y, explosionPos.z,
                            explosionVel.x, explosionVel.y, explosionVel.z);
                } else if (effectChoice < 0.8f) {
                    level.addParticle(ParticleTypes.LAVA,
                            explosionPos.x, explosionPos.y, explosionPos.z,
                            explosionVel.x * 0.5, explosionVel.y * 0.5, explosionVel.z * 0.5);
                } else {
                    level.addParticle(ParticleTypes.EXPLOSION,
                            explosionPos.x, explosionPos.y, explosionPos.z, 0, 0, 0);
                }
            } else {
                // Small meteors - simple fire explosions
                if (effectChoice < 0.7f) {
                    level.addParticle(ParticleTypes.FLAME,
                            explosionPos.x, explosionPos.y, explosionPos.z,
                            explosionVel.x, explosionVel.y, explosionVel.z);
                } else {
                    level.addParticle(ParticleTypes.LAVA,
                            explosionPos.x, explosionPos.y, explosionPos.z,
                            explosionVel.x * 0.3, explosionVel.y * 0.3, explosionVel.z * 0.3);
                }
            }
        }

        // Add smoke cloud for impact
        for (int i = 0; i < size * 15; i++) {
            Vec3 smokeVel = new Vec3(
                    (random.nextDouble() - 0.5) * 0.2,
                    random.nextDouble() * 0.3,
                    (random.nextDouble() - 0.5) * 0.2
            );

            Vec3 smokePos = impactPos.add(
                    (random.nextDouble() - 0.5) * size * 2,
                    random.nextDouble() * size,
                    (random.nextDouble() - 0.5) * size * 2
            );

            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    smokePos.x, smokePos.y, smokePos.z,
                    smokeVel.x, smokeVel.y, smokeVel.z);
        }

        // Debug message for impact
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cMeteor impact at " + (int)impactPos.x + ", " + (int)impactPos.y + ", " + (int)impactPos.z), false);
        }
    }

    // Meteorite instance data class
    private static class MeteoriteInstance {
        final Vec3 startPos;
        final Vec3 endPos;
        final Vec3 velocity;
        final float size;
        final int lifetimeTicks;
        final boolean hasTrail;
        final int coreColor;
        final int trailColor;
        final float intensity;
        final long spawnTime;

        MeteoriteInstance(Vec3 startPos, Vec3 endPos, Vec3 velocity, float size, int lifetimeTicks,
                          boolean hasTrail, int coreColor, int trailColor, float intensity, long spawnTime) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.velocity = velocity;
            this.size = size;
            this.lifetimeTicks = lifetimeTicks;
            this.hasTrail = hasTrail;
            this.coreColor = coreColor;
            this.trailColor = trailColor;
            this.intensity = intensity;
            this.spawnTime = spawnTime;
        }
    }
}