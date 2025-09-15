package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.entity.custom.PlayerlikeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class AdaptationWheelRenderer {

    private static final ConcurrentHashMap<Integer, WheelData> WHEEL_DATA = new ConcurrentHashMap<>();

    public static void onWheelRotation(int entityId, float rotation, float resistanceLevel) {
        WheelData data = WHEEL_DATA.computeIfAbsent(entityId, id -> new WheelData());
        data.targetRotation = rotation;
        data.resistanceLevel = resistanceLevel;
        data.lastUpdateTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partialTick = event.getPartialTick();

        // Clean up old data
        long currentTime = System.currentTimeMillis();
        WHEEL_DATA.entrySet().removeIf(entry -> currentTime - entry.getValue().lastUpdateTime > 30000); // 30 seconds

        // Find and render wheels for all PlayerlikeEntity instances
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof PlayerlikeEntity playerlikeEntity) {
                int entityId = entity.getId();

                // Create wheel data if it doesn't exist (for continuous rendering)
                WheelData data = WHEEL_DATA.computeIfAbsent(entityId, id -> {
                    WheelData newData = new WheelData();
                    newData.targetRotation = playerlikeEntity.getWheelRotation();
                    newData.currentRotation = newData.targetRotation;
                    newData.lastUpdateTime = currentTime;
                    return newData;
                });

                // Update rotation from entity if available
                data.targetRotation = playerlikeEntity.getWheelRotation();
                data.lastUpdateTime = currentTime;

                renderAdaptationWheel(playerlikeEntity, data, partialTick);
            }
        }
    }

    private static void renderAdaptationWheel(PlayerlikeEntity entity, WheelData data, float partialTick) {
        // Smooth rotation interpolation
        float deltaTime = partialTick * 0.05f;
        float rotationDiff = data.targetRotation - data.currentRotation;

        // Handle rotation wrapping (shortest path)
        if (rotationDiff > 180.0f) {
            rotationDiff -= 360.0f;
        } else if (rotationDiff < -180.0f) {
            rotationDiff += 360.0f;
        }

        data.currentRotation += rotationDiff * deltaTime * 5.0f;
        if (data.currentRotation >= 360.0f) data.currentRotation -= 360.0f;
        if (data.currentRotation < 0.0f) data.currentRotation += 360.0f;

        // Get entity position and create wheel center above entity
        Vec3 entityPos = entity.position();
        Vec3 wheelCenter = new Vec3(
                entityPos.x,
                entityPos.y + entity.getBbHeight() + 1.2,
                entityPos.z
        );

        // Determine wheel properties based on boss phase and resistance
        int bossPhase = entity.getBossPhase();
        float wheelRadius = getWheelRadius(bossPhase);
        int wheelColor = getWheelColor(data.resistanceLevel, bossPhase);
        float glowIntensity = getGlowIntensity(bossPhase);

        // Create the wheel structure using world coordinates
        renderWheelStructure(wheelCenter, wheelRadius, wheelColor, data.currentRotation, bossPhase, glowIntensity);

        // Add phase-specific effects
        renderPhaseEffects(wheelCenter, bossPhase, data.currentRotation);
    }

    private static void renderWheelStructure(Vec3 center, float radius, int color, float rotation, int phase, float glowIntensity) {
        float rotationRad = (float) Math.toRadians(rotation);

        // Determine complexity based on phase
        int rimSegments = Math.min(64, 32 + phase * 8); // More segments in higher phases
        int spokeCount = Math.min(12, 8 + phase); // More spokes in higher phases

        // Create outer rim using connected line segments (vertical wheel)
        List<Vec3> rimPoints = new ArrayList<>();
        for (int i = 0; i <= rimSegments; i++) {
            double angle = (i * 2.0 * Math.PI) / rimSegments;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Apply rotation around Y axis
            double rotatedX = x * Math.cos(rotationRad) - z * Math.sin(rotationRad);
            double rotatedZ = x * Math.sin(rotationRad) + z * Math.cos(rotationRad);

            Vec3 point = center.add(rotatedX, 0, rotatedZ);
            rimPoints.add(point);
        }

        // Draw rim segments with thickness based on phase
        float rimThickness = 0.06f + phase * 0.02f;
        for (int i = 0; i < rimPoints.size() - 1; i++) {
            VectorRenderer.drawLineWorld(rimPoints.get(i), rimPoints.get(i + 1),
                    color, rimThickness, glowIntensity > 0, 3, VectorRenderer.Transform.IDENTITY);
        }

        // Draw spokes from center to rim
        float spokeThickness = 0.04f + phase * 0.015f;
        for (int i = 0; i < spokeCount; i++) {
            double angle = (i * 2.0 * Math.PI) / spokeCount;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Apply rotation
            double rotatedX = x * Math.cos(rotationRad) - z * Math.sin(rotationRad);
            double rotatedZ = x * Math.sin(rotationRad) + z * Math.cos(rotationRad);

            Vec3 spokeEnd = center.add(rotatedX, 0, rotatedZ);

            VectorRenderer.drawLineWorld(center, spokeEnd,
                    color, spokeThickness, glowIntensity > 0, 3, VectorRenderer.Transform.IDENTITY);
        }

        // Draw center hub with phase-dependent size
        float hubSize = 0.08f + phase * 0.04f;
        VectorRenderer.drawSphereWorld(center, hubSize, color, 8, 12,
                glowIntensity > 0, 3, VectorRenderer.Transform.IDENTITY);

        // Draw adaptation nodes at rim intersections
        float nodeSize = 0.06f + phase * 0.02f;
        for (int i = 0; i < spokeCount; i++) {
            double angle = (i * 2.0 * Math.PI) / spokeCount;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Apply rotation
            double rotatedX = x * Math.cos(rotationRad) - z * Math.sin(rotationRad);
            double rotatedZ = x * Math.sin(rotationRad) + z * Math.cos(rotationRad);

            Vec3 nodePos = center.add(rotatedX, 0, rotatedZ);

            VectorRenderer.drawSphereWorld(nodePos, nodeSize, color, 6, 8,
                    glowIntensity > 0, 3, VectorRenderer.Transform.IDENTITY);
        }

        // Add inner ring for higher phases
        if (phase >= 2) {
            float innerRadius = radius * 0.6f;
            List<Vec3> innerRimPoints = new ArrayList<>();

            for (int i = 0; i <= rimSegments / 2; i++) {
                double angle = (i * 2.0 * Math.PI) / (rimSegments / 2);
                double x = Math.cos(angle) * innerRadius;
                double z = Math.sin(angle) * innerRadius;

                double rotatedX = x * Math.cos(rotationRad * 0.5f) - z * Math.sin(rotationRad * 0.5f);
                double rotatedZ = x * Math.sin(rotationRad * 0.5f) + z * Math.cos(rotationRad * 0.5f);

                Vec3 point = center.add(rotatedX, 0, rotatedZ);
                innerRimPoints.add(point);
            }

            for (int i = 0; i < innerRimPoints.size() - 1; i++) {
                VectorRenderer.drawLineWorld(innerRimPoints.get(i), innerRimPoints.get(i + 1),
                        color, rimThickness * 0.7f, glowIntensity > 0, 3, VectorRenderer.Transform.IDENTITY);
            }
        }
    }

    private static void renderPhaseEffects(Vec3 center, int phase, float rotation) {
        if (phase <= 0) return;

        float time = System.currentTimeMillis() * 0.001f;

        switch (phase) {
            case 1:
                // Subtle energy particles around the wheel
                renderEnergyParticles(center, rotation, 0x66FF6600, 8);
                break;
            case 2:
                // Regeneration aura + energy particles
                renderRegenerationAura(center, time);
                renderEnergyParticles(center, rotation, 0x66FFAA00, 12);
                break;
            case 3:
                // Full power effects - intense aura and particles
                renderPowerAura(center, time);
                renderEnergyParticles(center, rotation, 0x66FF0000, 16);
                renderLightningEffects(center, rotation, time);
                break;
        }
    }

    private static void renderEnergyParticles(Vec3 center, float rotation, int color, int count) {
        float rotationRad = (float) Math.toRadians(rotation);

        for (int i = 0; i < count; i++) {
            double angle = (i * 2.0 * Math.PI) / count;
            double radius = 1.3 + Math.sin(System.currentTimeMillis() * 0.003 + i) * 0.3;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = Math.sin(System.currentTimeMillis() * 0.004 + i) * 0.2;

            double rotatedX = x * Math.cos(rotationRad) - z * Math.sin(rotationRad);
            double rotatedZ = x * Math.sin(rotationRad) + z * Math.cos(rotationRad);

            Vec3 particlePos = center.add(rotatedX, y, rotatedZ);

            VectorRenderer.drawSphereWorld(particlePos, 0.04f, color, 4, 6,
                    true, 3, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void renderRegenerationAura(Vec3 center, float time) {
        // Green healing aura that pulses
        float pulseScale = 1.0f + (float) Math.sin(time * 3.0f) * 0.3f;
        int healColor = 0x8800FF00; // Translucent green

        // Multiple concentric healing rings
        for (int ring = 0; ring < 3; ring++) {
            float ringRadius = (1.5f + ring * 0.5f) * pulseScale;
            int segments = 24;
            List<Vec3> ringPoints = new ArrayList<>();

            for (int i = 0; i <= segments; i++) {
                double angle = (i * 2.0 * Math.PI) / segments;
                double x = Math.cos(angle) * ringRadius;
                double z = Math.sin(angle) * ringRadius;
                double y = Math.sin(angle * 2 + time * 2) * 0.1;

                Vec3 point = center.add(x, y, z);
                ringPoints.add(point);
            }

            for (int i = 0; i < ringPoints.size() - 1; i++) {
                VectorRenderer.drawLineWorld(ringPoints.get(i), ringPoints.get(i + 1),
                        healColor, 0.03f, true, 3, VectorRenderer.Transform.IDENTITY);
            }
        }
    }

    private static void renderPowerAura(Vec3 center, float time) {
        // Intense red/orange power aura
        float pulseScale = 1.0f + (float) Math.sin(time * 5.0f) * 0.5f;
        int powerColor = 0xAAFF3300; // Intense red-orange

        // Turbulent energy field
        for (int layer = 0; layer < 4; layer++) {
            float layerRadius = (2.0f + layer * 0.3f) * pulseScale;
            int segments = 32;

            for (int i = 0; i < segments; i++) {
                double angle1 = (i * 2.0 * Math.PI) / segments;
                double angle2 = ((i + 1) * 2.0 * Math.PI) / segments;

                double noise1 = Math.sin(time * 4 + angle1 * 3 + layer) * 0.2;
                double noise2 = Math.sin(time * 4 + angle2 * 3 + layer) * 0.2;

                double x1 = Math.cos(angle1) * (layerRadius + noise1);
                double z1 = Math.sin(angle1) * (layerRadius + noise1);
                double y1 = Math.sin(angle1 * 3 + time * 3) * 0.3;

                double x2 = Math.cos(angle2) * (layerRadius + noise2);
                double z2 = Math.sin(angle2) * (layerRadius + noise2);
                double y2 = Math.sin(angle2 * 3 + time * 3) * 0.3;

                Vec3 point1 = center.add(x1, y1, z1);
                Vec3 point2 = center.add(x2, y2, z2);

                VectorRenderer.drawLineWorld(point1, point2, powerColor, 0.04f,
                        true, 3, VectorRenderer.Transform.IDENTITY);
            }
        }
    }

    private static void renderLightningEffects(Vec3 center, float rotation, float time) {
        // Electric arcs radiating from the wheel
        int lightningColor = 0xFFFFFFFF; // Bright white
        float rotationRad = (float) Math.toRadians(rotation);

        for (int i = 0; i < 6; i++) {
            if ((time * 10 + i) % 3 < 0.1) { // Intermittent lightning
                double angle = (i * 2.0 * Math.PI) / 6;
                double x = Math.cos(angle) * 2.5;
                double z = Math.sin(angle) * 2.5;

                double rotatedX = x * Math.cos(rotationRad) - z * Math.sin(rotationRad);
                double rotatedZ = x * Math.sin(rotationRad) + z * Math.cos(rotationRad);

                Vec3 endPoint = center.add(rotatedX, 0, rotatedZ);

                // Jagged lightning path
                List<Vec3> lightningPath = generateLightningPath(center, endPoint, 5);

                for (int j = 0; j < lightningPath.size() - 1; j++) {
                    VectorRenderer.drawLineWorld(lightningPath.get(j), lightningPath.get(j + 1),
                            lightningColor, 0.02f, true, 3, VectorRenderer.Transform.IDENTITY);
                }
            }
        }
    }

    private static List<Vec3> generateLightningPath(Vec3 start, Vec3 end, int segments) {
        List<Vec3> path = new ArrayList<>();
        path.add(start);

        for (int i = 1; i < segments; i++) {
            float t = (float) i / segments;
            Vec3 basePoint = start.lerp(end, t);

            // Add random jagged offset
            double offsetX = (Math.random() - 0.5) * 0.4;
            double offsetY = (Math.random() - 0.5) * 0.4;
            double offsetZ = (Math.random() - 0.5) * 0.4;

            path.add(basePoint.add(offsetX, offsetY, offsetZ));
        }

        path.add(end);
        return path;
    }

    private static float getWheelRadius(int phase) {
        return 1.0f + phase * 0.2f; // Wheel grows with each phase
    }

    private static int getWheelColor(float resistanceLevel, int phase) {
        // Base color changes with phase, intensity with resistance
        int baseColor;
        switch (phase) {
            case 0:
                baseColor = 0xFF666666; // Gray - dormant
                break;
            case 1:
                baseColor = 0xFF0066FF; // Blue - adapting
                break;
            case 2:
                baseColor = 0xFF00FF66; // Green - regenerating
                break;
            case 3:
            default:
                baseColor = 0xFFFF3300; // Red - enraged
                break;
        }

        // Increase intensity based on resistance
        if (resistanceLevel >= 0.8f) {
            // Brighten the color for high resistance
            int r = Math.min(255, ((baseColor >> 16) & 0xFF) + (int)(resistanceLevel * 100));
            int g = Math.min(255, ((baseColor >> 8) & 0xFF) + (int)(resistanceLevel * 100));
            int b = Math.min(255, (baseColor & 0xFF) + (int)(resistanceLevel * 100));
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        return baseColor;
    }

    private static float getGlowIntensity(int phase) {
        return phase >= 2 ? 1.0f : 0.0f; // Only glow in phases 2 and 3
    }

    private static class WheelData {
        float currentRotation = 0.0f;
        float targetRotation = 0.0f;
        float resistanceLevel = 0.0f;
        long lastUpdateTime = System.currentTimeMillis();
    }
}