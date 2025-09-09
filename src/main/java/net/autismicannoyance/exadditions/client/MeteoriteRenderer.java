package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced meteorite renderer with visible rocky cores and dynamic effects
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class MeteoriteRenderer {

    private static final Map<Integer, MeteoriteInstance> activeMeteorites = new ConcurrentHashMap<>();
    private static final Map<Integer, ImpactEffect> activeImpacts = new ConcurrentHashMap<>();
    private static final RandomSource random = RandomSource.create();
    private static int nextImpactId = 0;

    public static void spawnMeteorite(Vec3 startPos, Vec3 endPos, Vec3 velocity, float size,
                                      int lifetimeTicks, int meteoriteId, boolean hasTrail,
                                      int coreColor, int trailColor, float intensity) {

        // Generate random rotation speeds for tumbling
        Vec3 rotationSpeed = new Vec3(
                2.0f + random.nextFloat() * 6.0f,
                2.0f + random.nextFloat() * 6.0f,
                2.0f + random.nextFloat() * 6.0f
        );

        Vec3 initialRotation = new Vec3(
                random.nextFloat() * 360f,
                random.nextFloat() * 360f,
                random.nextFloat() * 360f
        );

        MeteoriteInstance meteorite = new MeteoriteInstance(
                startPos, endPos, velocity, size, lifetimeTicks,
                hasTrail, coreColor, trailColor, intensity,
                System.currentTimeMillis(), rotationSpeed, initialRotation
        );

        activeMeteorites.put(meteoriteId, meteorite);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7Meteorite " + meteoriteId + " entering atmosphere"), false);
        }
    }

    public static void removeMeteorite(int meteoriteId) {
        activeMeteorites.remove(meteoriteId);
    }

    public static void clearAllMeteorites() {
        activeMeteorites.clear();
        activeImpacts.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        long currentTime = System.currentTimeMillis();

        // Render active meteorites
        Iterator<Map.Entry<Integer, MeteoriteInstance>> meteoriteIter = activeMeteorites.entrySet().iterator();
        while (meteoriteIter.hasNext()) {
            Map.Entry<Integer, MeteoriteInstance> entry = meteoriteIter.next();
            MeteoriteInstance meteorite = entry.getValue();

            long elapsed = currentTime - meteorite.spawnTime();
            float progress = Math.min(1.0f, elapsed / (meteorite.lifetimeTicks() * 50.0f));

            if (progress >= 1.0f) {
                // Create impact and remove meteorite
                createImpactEffect(meteorite);
                meteoriteIter.remove();
                continue;
            }

            renderMeteorite(meteorite, progress, currentTime);
        }

        // Render and update impact effects
        Iterator<Map.Entry<Integer, ImpactEffect>> impactIter = activeImpacts.entrySet().iterator();
        while (impactIter.hasNext()) {
            Map.Entry<Integer, ImpactEffect> entry = impactIter.next();
            ImpactEffect impact = entry.getValue();

            if (currentTime - impact.startTime > 15000) { // 15 seconds
                impactIter.remove();
                continue;
            }

            renderImpactEffect(impact, currentTime);
        }
    }

    private static void renderMeteorite(MeteoriteInstance meteorite, float progress, long currentTime) {
        // Calculate current position with wobble
        Vec3 currentPos = meteorite.startPos().lerp(meteorite.endPos(), progress);

        if (progress > 0.1f && progress < 0.9f) {
            double wobbleX = Math.sin(currentTime * 0.008 + meteorite.hashCode() * 0.1) * meteorite.size() * 0.15;
            double wobbleY = Math.cos(currentTime * 0.012 + meteorite.hashCode() * 0.2) * meteorite.size() * 0.08;
            double wobbleZ = Math.sin(currentTime * 0.010 + meteorite.hashCode() * 0.15) * meteorite.size() * 0.12;
            currentPos = currentPos.add(wobbleX, wobbleY, wobbleZ);
        }

        // Calculate rotation
        float elapsedSeconds = (currentTime - meteorite.spawnTime()) / 1000.0f;
        Vec3 currentRotation = meteorite.initialRotation().add(meteorite.rotationSpeed().scale(elapsedSeconds));

        // Create rotation transform
        Quaternionf rotationQuat = new Quaternionf()
                .rotateXYZ((float) Math.toRadians(currentRotation.x),
                        (float) Math.toRadians(currentRotation.y),
                        (float) Math.toRadians(currentRotation.z));

        VectorRenderer.Transform transform = VectorRenderer.Transform.fromQuaternion(
                Vec3.ZERO, rotationQuat, 1.0f, Vec3.ZERO);

        // Calculate temperature
        double currentSpeed = meteorite.velocity().length();
        float temperature = calculateTemperature(meteorite.size(), currentSpeed, progress);

        // 1. ROCK CORE FIRST - Use lifetime 2 (renders first, opaque)
        renderRockCore(currentPos, meteorite.size(), transform);

        // 2. Heat glow - Use lifetime 30 (semi-transparent)
        if (temperature > 0.3f) {
            renderHeatGlow(currentPos, meteorite.size(), temperature, transform);
        }

        // 3. Flame effects - Use lifetime 100+ (transparent, renders last)
        renderFlameEffects(currentPos, meteorite.size(), temperature, transform);

        // 4. Trail
        if (meteorite.hasTrail()) {
            renderTrail(meteorite, currentPos, temperature);
        }

        // 5. Sparks
        renderSparks(currentPos, meteorite.size(), temperature);
    }

    private static void renderRockCore(Vec3 pos, float size, VectorRenderer.Transform transform) {
        // Main solid rock core - LIFETIME 2 for highest priority (opaque)
        int darkRock = 0xFF2A2A2A;
        VectorRenderer.drawSphereWorld(pos, size * 0.4f, darkRock, 10, 12, false, 2, transform);

        // Add rock texture chunks - also LIFETIME 2
        int chunks = Math.max(6, (int)(size * 4));
        for (int i = 0; i < chunks; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double elevation = random.nextDouble() * Math.PI;
            double distance = size * (0.15 + random.nextDouble() * 0.25);

            Vec3 offset = new Vec3(
                    Math.sin(elevation) * Math.cos(angle) * distance,
                    Math.cos(elevation) * distance,
                    Math.sin(elevation) * Math.sin(angle) * distance
            );

            Vec3 chunkPos = pos.add(offset);
            float chunkSize = size * (0.06f + random.nextFloat() * 0.08f);

            int chunkColor = random.nextBoolean() ? 0xFF1A1A1A : 0xFF3A3A3A;
            VectorRenderer.drawSphereWorld(chunkPos, chunkSize, chunkColor, 6, 8, false, 2, transform);
        }

        // Hot spots on the rock - LIFETIME 2
        int hotSpots = Math.max(2, (int)(size * 2));
        for (int i = 0; i < hotSpots; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double elevation = random.nextDouble() * Math.PI;
            double distance = size * (0.1 + random.nextDouble() * 0.2);

            Vec3 offset = new Vec3(
                    Math.sin(elevation) * Math.cos(angle) * distance,
                    Math.cos(elevation) * distance,
                    Math.sin(elevation) * Math.sin(angle) * distance
            );

            Vec3 hotPos = pos.add(offset);
            float hotSize = size * (0.03f + random.nextFloat() * 0.05f);
            int hotColor = 0xFF8B2500; // Dark orange-red

            VectorRenderer.drawSphereWorld(hotPos, hotSize, hotColor, 4, 6, false, 2, transform);
        }
    }

    private static void renderHeatGlow(Vec3 pos, float size, float temperature, VectorRenderer.Transform transform) {
        // Inner heat glow - LIFETIME 30 (semi-transparent)
        int glowColor = getHeatGlowColor(temperature, 0.5f);
        float glowSize = size * (0.45f + temperature * 0.1f);
        VectorRenderer.drawSphereWorld(pos, glowSize, glowColor, 8, 10, false, 30, transform);
    }

    private static void renderFlameEffects(Vec3 pos, float size, float temperature, VectorRenderer.Transform transform) {
        // Outer flame layer - LIFETIME 100+ (transparent)
        int flameColor = getFlameColor(temperature, 0.3f);
        float flameSize = size * (0.6f + temperature * 0.3f);
        VectorRenderer.drawSphereWorld(pos, flameSize, flameColor, 6, 8, false, 100, transform);

        // Flame wisps - LIFETIME 120
        int wisps = Math.max(6, (int)(size * 3));
        for (int i = 0; i < wisps; i++) {
            double angle = (Math.PI * 2 * i) / wisps + random.nextDouble() * 0.5;
            Vec3 direction = new Vec3(Math.cos(angle), random.nextDouble() * 0.4, Math.sin(angle));

            Vec3 wispStart = pos.add(direction.scale(size * 0.4));
            Vec3 wispEnd = pos.add(direction.scale(size * (0.8 + random.nextFloat() * 0.4)));

            int wispColor = getFlameColor(temperature * 0.8f, 0.6f);
            float wispThickness = size * 0.08f;

            VectorRenderer.drawLineWorld(wispStart, wispEnd, wispColor, wispThickness, false, 120, transform);
        }
    }

    private static void renderTrail(MeteoriteInstance meteorite, Vec3 currentPos, float temperature) {
        Vec3 direction = meteorite.velocity().normalize();
        int segments = Math.max(15, (int)(meteorite.size() * 8));

        for (int i = 0; i < segments; i++) {
            float segmentProgress = (float) i / segments;
            float distance = meteorite.size() * (4 + segmentProgress * 6);

            Vec3 segmentPos = currentPos.subtract(direction.scale(distance * segmentProgress));

            // Add some randomness
            Vec3 randomOffset = new Vec3(
                    (random.nextGaussian()) * meteorite.size() * 0.1,
                    (random.nextGaussian()) * meteorite.size() * 0.1,
                    (random.nextGaussian()) * meteorite.size() * 0.1
            );
            segmentPos = segmentPos.add(randomOffset);

            float segmentTemp = temperature * (1.0f - segmentProgress * 0.8f);
            float segmentSize = meteorite.size() * (0.3f - segmentProgress * 0.2f);
            float alpha = 1.0f - segmentProgress * 0.7f;

            int trailColor = getFlameColor(segmentTemp, alpha);

            // Use LIFETIME 150+ for trail (renders after core)
            VectorRenderer.drawSphereWorld(segmentPos, segmentSize, trailColor, 4, 6, false, 150 + i, null);
        }
    }

    private static void renderSparks(Vec3 pos, float size, float temperature) {
        int sparkCount = (int)(size * 5 + temperature * 8);

        for (int i = 0; i < sparkCount; i++) {
            Vec3 sparkOffset = new Vec3(
                    (random.nextGaussian()) * size * 0.8,
                    (random.nextGaussian()) * size * 0.8,
                    (random.nextGaussian()) * size * 0.8
            );

            Vec3 sparkPos = pos.add(sparkOffset);
            float sparkSize = size * (0.02f + random.nextFloat() * 0.04f);

            int sparkColor = random.nextBoolean() ?
                    getFlameColor(temperature, 0.9f) :
                    0xFFFFFFFF; // White sparks

            // LIFETIME 80 for sparks
            VectorRenderer.drawSphereWorld(sparkPos, sparkSize, sparkColor, 4, 4, false, 80, null);
        }
    }

    private static void createImpactEffect(MeteoriteInstance meteorite) {
        int impactId = nextImpactId++;
        ImpactEffect impact = new ImpactEffect(
                meteorite.endPos(),
                meteorite.size(),
                System.currentTimeMillis()
        );
        activeImpacts.put(impactId, impact);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cMeteor impact at " + (int)impact.position.x + ", " + (int)impact.position.y + ", " + (int)impact.position.z), false);
        }
    }

    private static void renderImpactEffect(ImpactEffect impact, long currentTime) {
        double timeSinceImpact = (currentTime - impact.startTime) / 1000.0; // seconds
        Vec3 pos = impact.position;
        float size = impact.size;

        // Flash effect (first 2 seconds)
        if (timeSinceImpact < 2.0) {
            float flashIntensity = 1.0f - (float)(timeSinceImpact / 2.0);
            int flashColor = getFlameColor(1.0f, flashIntensity);
            float flashSize = size * (2.0f + flashIntensity * 3.0f);

            VectorRenderer.drawSphereWorld(pos, flashSize, flashColor, 12, 16, false, 20, null);
        }

        // Fire effects (10 seconds, diminishing)
        if (timeSinceImpact < 10.0) {
            float fireIntensity = Math.max(0.1f, 1.0f - (float)(timeSinceImpact / 10.0));
            int fireElements = (int)(size * 15 * fireIntensity);

            for (int i = 0; i < fireElements; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = random.nextDouble() * size * 3 * (1.0 + timeSinceImpact * 0.2);
                double height = random.nextDouble() * size * 2 * fireIntensity;

                // Flickering motion
                double flickerX = Math.sin(currentTime * 0.01 + i) * size * 0.2;
                double flickerZ = Math.cos(currentTime * 0.015 + i) * size * 0.2;

                Vec3 firePos = pos.add(
                        Math.cos(angle) * distance + flickerX,
                        height,
                        Math.sin(angle) * distance + flickerZ
                );

                float temperature = fireIntensity * (0.3f + random.nextFloat() * 0.7f);
                int fireColor = getFlameColor(temperature, fireIntensity * 0.8f);
                float fireSize = size * 0.15f * fireIntensity;

                VectorRenderer.drawSphereWorld(firePos, fireSize, fireColor, 4, 6, false, 60, null);

                // Flame columns
                if (distance < size * 1.5) {
                    double flameHeight = size * (1.0 + random.nextDouble()) * fireIntensity;
                    Vec3 flameTop = firePos.add(0, flameHeight, 0);

                    int columnColor = getFlameColor(temperature * 0.7f, fireIntensity * 0.6f);
                    VectorRenderer.drawLineWorld(firePos, flameTop, columnColor, size * 0.08f, false, 50, null);
                }
            }
        }

        // MOVING SMOKE (15 seconds)
        if (timeSinceImpact < 15.0) {
            float smokeIntensity = Math.max(0.2f, 1.0f - (float)(timeSinceImpact / 15.0));
            int smokeColumns = Math.max(3, (int)(size));

            for (int col = 0; col < smokeColumns; col++) {
                double baseAngle = (Math.PI * 2 * col) / smokeColumns;
                double radius = size * (1.0 + random.nextDouble());

                Vec3 smokeBase = pos.add(new Vec3(
                        Math.cos(baseAngle) * radius,
                        0,
                        Math.sin(baseAngle) * radius
                ));

                // Create rising smoke segments
                int segments = 10;
                for (int seg = 0; seg < segments; seg++) {
                    float segProgress = (float) seg / segments;
                    double columnHeight = size * 5 * segProgress;

                    // WIND DRIFT - smoke moves over time
                    double windTime = currentTime * 0.002 + col;
                    double windStrength = segProgress * timeSinceImpact * 0.3;
                    double windX = Math.sin(windTime) * windStrength * size;
                    double windZ = Math.cos(windTime * 0.8) * windStrength * size * 0.6;

                    // Spiral motion
                    double spiralAngle = baseAngle + segProgress * Math.PI + timeSinceImpact * 0.1;
                    double spiralX = Math.sin(spiralAngle) * radius * 0.2 * segProgress;
                    double spiralZ = Math.cos(spiralAngle) * radius * 0.2 * segProgress;

                    Vec3 smokePos = smokeBase.add(
                            windX + spiralX,
                            columnHeight,
                            windZ + spiralZ
                    );

                    float smokeAlpha = smokeIntensity * (0.6f - segProgress * 0.4f);
                    int smokeColor = adjustColorAlpha(0xFF666666, smokeAlpha);
                    float smokeSize = size * (0.2f + segProgress * 0.5f + (float)timeSinceImpact * 0.05f);

                    // Short lifetime so smoke doesn't hang around forever
                    int lifetime = Math.max(30, 100 - (int)(timeSinceImpact * 5) - seg);
                    VectorRenderer.drawSphereWorld(smokePos, smokeSize, smokeColor, 4, 6, false, lifetime, null);
                }
            }
        }

        // Heat distortion (8 seconds)
        if (timeSinceImpact < 8.0) {
            float heatIntensity = Math.max(0.1f, 1.0f - (float)(timeSinceImpact / 8.0));
            int distortionCount = (int)(size * 8 * heatIntensity);

            for (int i = 0; i < distortionCount; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = random.nextDouble() * size * 2;

                // Heat rises over time
                double risingCycle = (currentTime * 0.003 + i) % 5000 / 5000.0; // 5 second cycle
                double baseHeight = random.nextDouble() * size;
                double risingHeight = baseHeight + risingCycle * size * 2;

                Vec3 heatPos = pos.add(
                        Math.cos(angle) * distance,
                        risingHeight,
                        Math.sin(angle) * distance
                );

                float heightFade = Math.max(0.1f, 1.0f - (float)(risingHeight / (size * 3)));
                float heatAlpha = 0.15f * heatIntensity * heightFade;

                int heatColor = adjustColorAlpha(0xFFFFDD88, heatAlpha);
                float heatSize = size * 0.25f;

                VectorRenderer.drawSphereWorld(heatPos, heatSize, heatColor, 4, 6, false, 40, null);
            }
        }
    }

    // Utility methods
    private static float calculateTemperature(float size, double speed, float atmosphericProgress) {
        float sizeHeat = Math.min(1.0f, size / 6.0f);
        float speedHeat = Math.min(1.0f, (float)(speed / 3.0));
        float atmosphericHeat = Math.min(1.0f, atmosphericProgress * 2.0f);

        return Math.min(1.0f, (sizeHeat * 0.3f) + (speedHeat * 0.4f) + (atmosphericHeat * 0.4f));
    }

    private static int getFlameColor(float temperature, float alpha) {
        int a = Math.max(20, Math.min(255, (int)(alpha * 255)));
        int r, g, b;

        if (temperature > 0.9f) {
            r = 200 + (int)(55 * temperature); g = 220 + (int)(35 * temperature); b = 255;
        } else if (temperature > 0.7f) {
            r = (int)(100 + 100 * (temperature - 0.7f) / 0.2f); g = (int)(150 + 70 * (temperature - 0.7f) / 0.2f); b = 255;
        } else if (temperature > 0.5f) {
            r = 255; g = 255; b = (int)(100 + 155 * (temperature - 0.5f) / 0.2f);
        } else if (temperature > 0.3f) {
            r = 255; g = (int)(150 + 105 * (temperature - 0.3f) / 0.2f); b = (int)(50 * (temperature - 0.3f) / 0.2f);
        } else {
            r = (int)(180 + 75 * temperature / 0.3f); g = (int)(50 + 100 * temperature / 0.3f); b = 0;
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int getHeatGlowColor(float temperature, float alpha) {
        int a = Math.max(30, Math.min(255, (int)(alpha * 255)));

        if (temperature > 0.7f) {
            return (a << 24) | (255 << 16) | (200 << 8) | 100;
        } else if (temperature > 0.5f) {
            return (a << 24) | (255 << 16) | (150 << 8) | 50;
        } else {
            return (a << 24) | (200 << 16) | (100 << 8) | 20;
        }
    }

    private static int adjustColorAlpha(int color, float alpha) {
        int a = Math.max(20, Math.min(255, (int)(alpha * 255)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // Data classes
    public static record MeteoriteInstance(
            Vec3 startPos, Vec3 endPos, Vec3 velocity, float size, int lifetimeTicks,
            boolean hasTrail, int coreColor, int trailColor, float intensity,
            long spawnTime, Vec3 rotationSpeed, Vec3 initialRotation
    ) {}

    public static record ImpactEffect(
            Vec3 position, float size, long startTime
    ) {}
}