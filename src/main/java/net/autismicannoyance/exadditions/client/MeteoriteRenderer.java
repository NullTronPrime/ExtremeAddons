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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced meteorite renderer using VectorRenderer for realistic 3D meteorites
 * Creates rocky cores with flame auras and temperature-based color trails
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
        if (mc.player != null && mc.level != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7Meteorite " + meteoriteId + " entering atmosphere"), false);
        }
    }

    /**
     * Remove a specific meteorite (called when receiving removal packets)
     */
    public static void removeMeteorite(int meteoriteId) {
        activeMeteorites.remove(meteoriteId);
    }

    /**
     * Clear all meteorites (useful for cleanup)
     */
    public static void clearAllMeteorites() {
        activeMeteorites.clear();
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

            long elapsed = currentTime - meteorite.spawnTime();
            float progress = Math.min(1.0f, elapsed / (meteorite.lifetimeTicks() * 50.0f));

            if (progress >= 1.0f) {
                // Meteorite has reached the ground - create impact effects
                createImpactEffects(level, meteorite);
                iterator.remove();
                continue;
            }

            // Calculate current position using smooth interpolation based on progress
            Vec3 currentPos = meteorite.startPos().lerp(meteorite.endPos(), progress);

            // Add some natural variation to the path (slight wobble due to atmospheric turbulence)
            if (progress > 0.1f && progress < 0.9f) {
                double wobbleX = Math.sin(elapsed * 0.01) * meteorite.size() * 0.1;
                double wobbleZ = Math.cos(elapsed * 0.015) * meteorite.size() * 0.1;
                currentPos = currentPos.add(wobbleX, 0, wobbleZ);
            }

            // Calculate temperature and effects
            double currentSpeed = meteorite.velocity().length();
            float temperature = calculateTemperature(meteorite.size(), currentSpeed, progress);

            // Render the meteorite using VectorRenderer
            renderMeteoriteWithVectorRenderer(meteorite, currentPos, temperature);
        }
    }

    private static void renderMeteoriteWithVectorRenderer(MeteoriteInstance meteorite, Vec3 pos, float temperature) {

        // 1. Render rocky core sphere
        renderRockyCore(pos, meteorite.size());

        // 2. Render flame aura around the core
        renderFlameAura(pos, meteorite.size(), temperature);

        // 3. Render temperature-based trail
        if (meteorite.hasTrail()) {
            renderRealisticTrail(meteorite, pos, temperature);
        }

        // 4. Add atmospheric compression effects for large meteorites
        if (meteorite.size() > 2.0f) {
            renderShockWave(pos, meteorite.size(), temperature);
        }
    }

    private static void renderRockyCore(Vec3 pos, float size) {
        // Dark rocky core - use multiple spheres for texture
        int baseColor = 0xFF2A2A2A; // Dark gray rock
        int darkColor = 0xFF1A1A1A; // Very dark rock

        // Main rocky core
        VectorRenderer.drawSphereWorld(pos, size * 0.4f, baseColor, 12, 16, false, 2, null);

        // Add surface texture with smaller darker spheres
        for (int i = 0; i < 6; i++) {
            Vec3 offset = new Vec3(
                    (random.nextGaussian()) * size * 0.15,
                    (random.nextGaussian()) * size * 0.15,
                    (random.nextGaussian()) * size * 0.15
            );
            Vec3 texturePos = pos.add(offset);
            VectorRenderer.drawSphereWorld(texturePos, size * 0.1f, darkColor, 6, 8, false, 2, null);
        }

        // Add some bright hot spots where the rock is heating up
        int hotSpotColor = 0xFF8B4513; // Dark orange for heated rock
        for (int i = 0; i < 3; i++) {
            Vec3 offset = new Vec3(
                    (random.nextGaussian()) * size * 0.2,
                    (random.nextGaussian()) * size * 0.2,
                    (random.nextGaussian()) * size * 0.2
            );
            Vec3 hotPos = pos.add(offset);
            VectorRenderer.drawSphereWorld(hotPos, size * 0.08f, hotSpotColor, 6, 8, false, 2, null);
        }
    }

    private static void renderFlameAura(Vec3 pos, float size, float temperature) {
        int innerFlameColor = getFlameColor(temperature, 1.0f);
        int middleFlameColor = getFlameColor(temperature, 0.7f);
        int outerFlameColor = getFlameColor(temperature, 0.4f);

        // Multi-layered flame aura with transparency
        VectorRenderer.drawSphereWorld(pos, size * 0.8f, outerFlameColor, 10, 14, false, 3, null);
        VectorRenderer.drawSphereWorld(pos, size * 0.6f, middleFlameColor, 8, 12, false, 3, null);
        VectorRenderer.drawSphereWorld(pos, size * 0.45f, innerFlameColor, 6, 10, false, 3, null);

        // Add flame wisps extending from the core
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2 * i) / 8;
            Vec3 flameDir = new Vec3(Math.cos(angle), random.nextGaussian() * 0.3, Math.sin(angle));
            Vec3 flameStart = pos.add(flameDir.scale(size * 0.4));
            Vec3 flameEnd = pos.add(flameDir.scale(size * (0.9 + random.nextFloat() * 0.3)));

            int wispColor = getFlameColor(temperature, 0.6f);
            VectorRenderer.drawLineWorld(flameStart, flameEnd, wispColor, size * 0.1f, false, 3, null);
        }
    }

    private static void renderRealisticTrail(MeteoriteInstance meteorite, Vec3 currentPos, float temperature) {
        Vec3 direction = meteorite.velocity().normalize();
        int trailSegments = Math.max(20, (int)(meteorite.size() * 12));

        for (int i = 0; i < trailSegments; i++) {
            float segmentProgress = (float) i / trailSegments;
            float trailDistance = meteorite.size() * (8 + segmentProgress * 4); // Trail gets longer towards back

            Vec3 segmentPos = currentPos.subtract(direction.scale(trailDistance * segmentProgress));

            // Add natural variation to trail position
            Vec3 variation = new Vec3(
                    random.nextGaussian() * meteorite.size() * 0.15,
                    random.nextGaussian() * meteorite.size() * 0.1,
                    random.nextGaussian() * meteorite.size() * 0.15
            );
            segmentPos = segmentPos.add(variation);

            // Calculate temperature at this trail segment (cooler towards back)
            float segmentTemp = temperature * Math.max(0.1f, 1.0f - segmentProgress * 0.8f);
            float segmentSize = meteorite.size() * (1.0f - segmentProgress * 0.7f); // Smaller towards back
            float segmentAlpha = 1.0f - segmentProgress * 0.6f; // More transparent towards back

            int trailColor = getFlameColor(segmentTemp, segmentAlpha);

            // Render trail segment as elongated sphere
            VectorRenderer.drawSphereWorld(segmentPos, segmentSize * 0.3f, trailColor, 6, 8, false, 2, null);

            // Add connecting lines between segments for continuity
            if (i > 0) {
                Vec3 prevSegmentPos = currentPos.subtract(direction.scale(trailDistance * ((float)(i-1) / trailSegments)));
                VectorRenderer.drawLineWorld(segmentPos, prevSegmentPos, trailColor, segmentSize * 0.2f, false, 2, null);
            }
        }

        // Add flame streamers for more dramatic effect
        for (int i = 0; i < 6; i++) {
            Vec3 streamerStart = currentPos.subtract(direction.scale(meteorite.size() * 0.5));
            Vec3 streamerEnd = currentPos.subtract(direction.scale(meteorite.size() * (6 + random.nextFloat() * 4)));

            // Add curve to streamers
            Vec3 perpendicular = direction.cross(new Vec3(0, 1, 0)).normalize();
            if (perpendicular.length() < 0.1) perpendicular = direction.cross(new Vec3(1, 0, 0)).normalize();

            Vec3 curve = perpendicular.scale((random.nextFloat() - 0.5) * meteorite.size() * 2);
            streamerEnd = streamerEnd.add(curve);

            int streamerColor = getFlameColor(temperature * 0.8f, 0.7f);
            VectorRenderer.drawLineWorld(streamerStart, streamerEnd, streamerColor, meteorite.size() * 0.15f, false, 2, null);
        }
    }

    private static void renderShockWave(Vec3 pos, float size, float temperature) {
        // Atmospheric compression wave for large meteorites
        int waveColor = getFlameColor(temperature * 0.5f, 0.3f);
        float waveRadius = size * 1.5f;

        // Render shock wave as expanding rings
        for (int ring = 0; ring < 3; ring++) {
            float ringRadius = waveRadius + ring * size * 0.3f;
            float ringAlpha = Math.max(0.1f, 0.4f - ring * 0.1f);
            int ringColor = adjustColorAlpha(waveColor, ringAlpha);

            // Create ring using multiple line segments
            int segments = 24;
            for (int i = 0; i < segments; i++) {
                double angle1 = (Math.PI * 2 * i) / segments;
                double angle2 = (Math.PI * 2 * (i + 1)) / segments;

                Vec3 point1 = pos.add(new Vec3(Math.cos(angle1) * ringRadius, 0, Math.sin(angle1) * ringRadius));
                Vec3 point2 = pos.add(new Vec3(Math.cos(angle2) * ringRadius, 0, Math.sin(angle2) * ringRadius));

                VectorRenderer.drawLineWorld(point1, point2, ringColor, size * 0.05f, false, 1, null);
            }
        }
    }

    private static float calculateTemperature(float size, double speed, float atmosphericProgress) {
        // Temperature calculation: larger + faster + more atmospheric entry = hotter
        float sizeHeatFactor = Math.min(1.0f, size / 6.0f);
        float speedHeatFactor = Math.min(1.0f, (float)(speed / 3.0));
        float atmosphericHeat = Math.min(1.0f, atmosphericProgress * 2.0f);

        return Math.min(1.0f, (sizeHeatFactor * 0.3f) + (speedHeatFactor * 0.4f) + (atmosphericHeat * 0.4f));
    }

    private static int getFlameColor(float temperature, float alpha) {
        int a = Math.max(20, Math.min(255, (int)(alpha * 255)));
        int r, g, b;

        if (temperature > 0.9f) {
            // Blue-white hot (extremely hot meteorite)
            r = 200 + (int)(55 * temperature);
            g = 220 + (int)(35 * temperature);
            b = 255;
        } else if (temperature > 0.7f) {
            // Blue hot
            r = (int)(100 + 100 * (temperature - 0.7f) / 0.2f);
            g = (int)(150 + 70 * (temperature - 0.7f) / 0.2f);
            b = 255;
        } else if (temperature > 0.5f) {
            // White-yellow hot
            r = 255;
            g = 255;
            b = (int)(100 + 155 * (temperature - 0.5f) / 0.2f);
        } else if (temperature > 0.3f) {
            // Yellow-orange hot
            r = 255;
            g = (int)(150 + 105 * (temperature - 0.3f) / 0.2f);
            b = (int)(50 * (temperature - 0.3f) / 0.2f);
        } else {
            // Red-orange hot (cooler)
            r = (int)(180 + 75 * temperature / 0.3f);
            g = (int)(50 + 100 * temperature / 0.3f);
            b = 0;
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustColorAlpha(int color, float alpha) {
        int a = Math.max(20, Math.min(255, (int)(alpha * 255)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static void createImpactEffects(ClientLevel level, MeteoriteInstance meteorite) {
        Vec3 impactPos = meteorite.endPos();

        // Create multi-stage impact visualization with proper scaling
        createImpactFlash(impactPos, meteorite.size());
        createImpactCrater(impactPos, meteorite.size());
        createDebrisExplosion(impactPos, meteorite.size());
        createShockwaveRings(impactPos, meteorite.size());
        createFireAndSmoke(impactPos, meteorite.size());

        // Debug message
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cMeteor impact at " + (int)impactPos.x + ", " + (int)impactPos.y + ", " + (int)impactPos.z), false);
        }
    }

    private static void createImpactFlash(Vec3 pos, float size) {
        // Bright initial flash - multiple layers for intensity
        int flashColorCore = getFlameColor(1.0f, 1.0f);    // Bright white-blue core
        int flashColorMid = getFlameColor(0.9f, 0.8f);     // Blue-white middle
        int flashColorOuter = getFlameColor(0.7f, 0.6f);   // Yellow-orange outer

        // Core flash
        VectorRenderer.drawSphereWorld(pos, size * 3.0f, flashColorCore, 16, 20, false, 15, null);
        VectorRenderer.drawSphereWorld(pos, size * 2.2f, flashColorMid, 12, 16, false, 20, null);
        VectorRenderer.drawSphereWorld(pos, size * 1.5f, flashColorOuter, 10, 12, false, 25, null);
    }

    private static void createImpactCrater(Vec3 pos, float size) {
        // Create crater visualization with raised rim and depressed center
        float craterRadius = size * 4.0f;
        int craterColor = 0x70654321; // Dark brown/earth color with transparency
        int rimColor = 0x80A0522D;    // Darker earth color for rim

        // Crater depression (flat circle)
        VectorRenderer.drawPlaneRectWorld(
                pos.add(0, -0.8, 0),
                new Vec3(0, 1, 0),
                craterRadius * 2,
                craterRadius * 2,
                craterColor,
                false,
                100,
                null
        );

        // Crater rim - create ring of raised earth
        int rimSegments = 24;
        for (int i = 0; i < rimSegments; i++) {
            double angle1 = (Math.PI * 2 * i) / rimSegments;
            double angle2 = (Math.PI * 2 * (i + 1)) / rimSegments;

            // Inner rim
            Vec3 innerPoint1 = pos.add(new Vec3(Math.cos(angle1) * craterRadius * 0.8, 0, Math.sin(angle1) * craterRadius * 0.8));
            Vec3 innerPoint2 = pos.add(new Vec3(Math.cos(angle2) * craterRadius * 0.8, 0, Math.sin(angle2) * craterRadius * 0.8));

            // Outer rim
            Vec3 outerPoint1 = pos.add(new Vec3(Math.cos(angle1) * craterRadius, 0.5, Math.sin(angle1) * craterRadius));
            Vec3 outerPoint2 = pos.add(new Vec3(Math.cos(angle2) * craterRadius, 0.5, Math.sin(angle2) * craterRadius));

            // Create rim segments
            VectorRenderer.drawPlaneWorld(innerPoint1, outerPoint1, outerPoint2,
                    new int[]{rimColor, rimColor, rimColor}, false, 80, null);
            VectorRenderer.drawPlaneWorld(innerPoint1, outerPoint2, innerPoint2,
                    new int[]{rimColor, rimColor, rimColor}, false, 80, null);
        }
    }

    private static void createDebrisExplosion(Vec3 pos, float size) {
        // Create flying debris field using spheres and lines
        int debrisCount = Math.max(20, (int)(size * 25));

        for (int i = 0; i < debrisCount; i++) {
            // Random debris trajectory
            double angle = random.nextDouble() * Math.PI * 2;
            double elevation = random.nextDouble() * Math.PI * 0.4; // Up to 72 degrees
            double distance = size * (3 + random.nextDouble() * 8);

            Vec3 debrisDirection = new Vec3(
                    Math.cos(angle) * Math.cos(elevation),
                    Math.sin(elevation),
                    Math.sin(angle) * Math.cos(elevation)
            );

            Vec3 debrisStart = pos.add(debrisDirection.scale(size * 0.5));
            Vec3 debrisEnd = pos.add(debrisDirection.scale(distance));

            // Different debris types
            float debrisType = random.nextFloat();
            if (debrisType < 0.4f) {
                // Rock chunks
                int rockColor = 0xFF654321; // Brown rock
                float debrisSize = size * (0.1f + random.nextFloat() * 0.3f);
                VectorRenderer.drawSphereWorld(debrisEnd, debrisSize, rockColor, 6, 8, false, 60, null);

                // Debris trail
                int trailColor = 0x60654321; // Translucent brown
                VectorRenderer.drawLineWorld(debrisStart, debrisEnd, trailColor, debrisSize * 0.5f, false, 40, null);

            } else if (debrisType < 0.7f) {
                // Hot fragments
                int hotColor = getFlameColor(0.6f, 0.8f);
                float fragmentSize = size * (0.05f + random.nextFloat() * 0.2f);
                VectorRenderer.drawSphereWorld(debrisEnd, fragmentSize, hotColor, 4, 6, false, 45, null);

                // Hot trail
                int hotTrailColor = getFlameColor(0.4f, 0.6f);
                VectorRenderer.drawLineWorld(debrisStart, debrisEnd, hotTrailColor, fragmentSize * 0.8f, false, 30, null);

            } else {
                // Molten metal pieces
                int moltenColor = 0xFFFF4500; // Bright orange-red
                float moltenSize = size * (0.03f + random.nextFloat() * 0.15f);
                VectorRenderer.drawSphereWorld(debrisEnd, moltenSize, moltenColor, 4, 6, false, 50, null);
            }
        }
    }

    private static void createShockwaveRings(Vec3 pos, float size) {
        // Multiple expanding shockwave rings
        int ringCount = Math.max(3, (int)(size * 2));

        for (int ring = 0; ring < ringCount; ring++) {
            float ringRadius = size * (4 + ring * 2);
            float ringHeight = 0.2f + ring * 0.1f;
            float ringAlpha = Math.max(0.2f, 0.8f - ring * 0.2f);

            int waveColor = adjustColorAlpha(0xFFFFFFFF, ringAlpha); // White shockwave

            // Create ring using line segments
            int segments = 32;
            for (int i = 0; i < segments; i++) {
                double angle1 = (Math.PI * 2 * i) / segments;
                double angle2 = (Math.PI * 2 * (i + 1)) / segments;

                Vec3 point1 = pos.add(new Vec3(Math.cos(angle1) * ringRadius, ringHeight, Math.sin(angle1) * ringRadius));
                Vec3 point2 = pos.add(new Vec3(Math.cos(angle2) * ringRadius, ringHeight, Math.sin(angle2) * ringRadius));

                VectorRenderer.drawLineWorld(point1, point2, waveColor, size * 0.08f, false, 25 + ring * 10, null);
            }

            // Vertical shockwave components
            for (int i = 0; i < segments / 4; i++) {
                double angle = (Math.PI * 2 * i) / (segments / 4);
                Vec3 basePoint = pos.add(new Vec3(Math.cos(angle) * ringRadius, 0, Math.sin(angle) * ringRadius));
                Vec3 topPoint = basePoint.add(0, ringHeight * 3, 0);

                int verticalColor = adjustColorAlpha(0xFFCCCCCC, ringAlpha * 0.7f);
                VectorRenderer.drawLineWorld(basePoint, topPoint, verticalColor, size * 0.06f, false, 20 + ring * 8, null);
            }
        }
    }

    private static void createFireAndSmoke(Vec3 pos, float size) {
        // Create persistent fire effects at impact site
        int fireElements = Math.max(15, (int)(size * 20));

        for (int i = 0; i < fireElements; i++) {
            // Random positions within impact area
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * size * 3;
            double height = random.nextDouble() * size * 2;

            Vec3 firePos = pos.add(
                    Math.cos(angle) * distance,
                    height,
                    Math.sin(angle) * distance
            );

            // Different fire types based on position
            if (distance < size * 1.5) {
                // Hot core fires
                int hotFireColor = getFlameColor(0.9f, 0.9f);
                VectorRenderer.drawSphereWorld(firePos, size * 0.2f, hotFireColor, 6, 8, false, 120, null);

                // Fire wisps rising up
                Vec3 wispEnd = firePos.add(0, size * (1 + random.nextDouble()), 0);
                int wispColor = getFlameColor(0.7f, 0.6f);
                VectorRenderer.drawLineWorld(firePos, wispEnd, wispColor, size * 0.1f, false, 100, null);

            } else {
                // Outer fires and smoke
                int smokeFireColor = getFlameColor(0.4f, 0.7f);
                VectorRenderer.drawSphereWorld(firePos, size * 0.15f, smokeFireColor, 4, 6, false, 80, null);
            }
        }

        // Large smoke columns
        int smokeColumns = Math.max(3, (int)(size));
        for (int i = 0; i < smokeColumns; i++) {
            double angle = (Math.PI * 2 * i) / smokeColumns;
            double radius = size * 1.5;

            Vec3 smokeBase = pos.add(new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
            Vec3 smokeTop = smokeBase.add(0, size * 5, 0);

            // Create smoke column with multiple segments
            int smokeSegments = 8;
            for (int seg = 0; seg < smokeSegments; seg++) {
                float segmentProgress = (float) seg / smokeSegments;
                Vec3 segmentPos = smokeBase.lerp(smokeTop, segmentProgress);

                // Add wind dispersion to smoke
                Vec3 windOffset = new Vec3(
                        Math.sin(segmentProgress * Math.PI) * size * 0.5,
                        0,
                        Math.cos(segmentProgress * Math.PI) * size * 0.3
                );
                segmentPos = segmentPos.add(windOffset);

                float smokeAlpha = Math.max(0.3f, 1.0f - segmentProgress * 0.8f);
                int smokeColor = adjustColorAlpha(0xFF666666, smokeAlpha); // Gray smoke
                float smokeSize = size * (0.3f + segmentProgress * 0.4f); // Expanding smoke

                VectorRenderer.drawSphereWorld(segmentPos, smokeSize, smokeColor, 6, 8, false, 150, null);
            }
        }

        // Heat distortion effects around the crater
        createHeatDistortion(pos, size);
    }

    private static void createHeatDistortion(Vec3 pos, float size) {
        // Create visual heat distortion using translucent elements
        int distortionElements = (int)(size * 10);

        for (int i = 0; i < distortionElements; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * size * 2.5;
            double height = random.nextDouble() * size * 1.5;

            Vec3 distortionPos = pos.add(
                    Math.cos(angle) * distance,
                    height,
                    Math.sin(angle) * distance
            );

            // Very translucent heat shimmer effects
            int shimmerColor = adjustColorAlpha(0xFFFFDD88, 0.15f); // Very faint yellow
            VectorRenderer.drawSphereWorld(distortionPos, size * 0.3f, shimmerColor, 6, 8, false, 60, null);
        }
    }

    // Meteorite instance record class for better data handling
    public static record MeteoriteInstance(
            Vec3 startPos,
            Vec3 endPos,
            Vec3 velocity,
            float size,
            int lifetimeTicks,
            boolean hasTrail,
            int coreColor,
            int trailColor,
            float intensity,
            long spawnTime
    ) {}
}