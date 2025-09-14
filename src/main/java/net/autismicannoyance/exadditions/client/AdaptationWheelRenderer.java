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
        WHEEL_DATA.entrySet().removeIf(entry -> currentTime - entry.getValue().lastUpdateTime > 10000); // 10 seconds

        // Render wheels for all tracked entities
        for (var entry : WHEEL_DATA.entrySet()) {
            int entityId = entry.getKey();
            WheelData data = entry.getValue();

            Entity entity = mc.level.getEntity(entityId);
            if (entity instanceof PlayerlikeEntity playerlikeEntity) {
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

        data.currentRotation += rotationDiff * deltaTime * 10.0f;
        if (data.currentRotation >= 360.0f) data.currentRotation -= 360.0f;
        if (data.currentRotation < 0.0f) data.currentRotation += 360.0f;

        // Get entity position and create wheel center above entity
        Vec3 wheelCenter = entity.position().add(0, entity.getBbHeight() + 0.8, 0);

        // Determine wheel color based on resistance level
        int wheelColor = getWheelColor(data.resistanceLevel);

        // Create the wheel structure using world coordinates
        renderWheelStructure(wheelCenter, 1.0f, wheelColor, data.currentRotation);
    }

    private static void renderWheelStructure(Vec3 center, float radius, int color, float rotation) {
        float rotationRad = (float) Math.toRadians(rotation);

        // Create outer rim using connected line segments (vertical wheel)
        int rimSegments = 32;
        List<Vec3> rimPoints = new ArrayList<>();

        for (int i = 0; i <= rimSegments; i++) {
            double angle = (i * 2.0 * Math.PI) / rimSegments;
            // Create vertical wheel by using X-Z plane rotated by current rotation
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Apply rotation around Y axis
            double rotatedX = x * Math.cos(rotationRad) - z * Math.sin(rotationRad);
            double rotatedZ = x * Math.sin(rotationRad) + z * Math.cos(rotationRad);

            Vec3 point = center.add(rotatedX, 0, rotatedZ);
            rimPoints.add(point);
        }

        // Draw rim segments
        for (int i = 0; i < rimPoints.size() - 1; i++) {
            VectorRenderer.drawLineWorld(rimPoints.get(i), rimPoints.get(i + 1),
                    color, 0.08f, false, 3, VectorRenderer.Transform.IDENTITY);
        }

        // Draw spokes (8 spokes from center to rim)
        int spokeCount = 8;
        for (int i = 0; i < spokeCount; i++) {
            double angle = (i * 2.0 * Math.PI) / spokeCount;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Apply rotation
            double rotatedX = x * Math.cos(rotationRad) - z * Math.sin(rotationRad);
            double rotatedZ = x * Math.sin(rotationRad) + z * Math.cos(rotationRad);

            Vec3 spokeEnd = center.add(rotatedX, 0, rotatedZ);

            VectorRenderer.drawLineWorld(center, spokeEnd,
                    color, 0.06f, false, 3, VectorRenderer.Transform.IDENTITY);
        }

        // Draw center hub
        VectorRenderer.drawSphereWorld(center, 0.12f, color, 8, 12,
                false, 3, VectorRenderer.Transform.IDENTITY);

        // Draw nodes at rim intersections
        for (int i = 0; i < spokeCount; i++) {
            double angle = (i * 2.0 * Math.PI) / spokeCount;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Apply rotation
            double rotatedX = x * Math.cos(rotationRad) - z * Math.sin(rotationRad);
            double rotatedZ = x * Math.sin(rotationRad) + z * Math.cos(rotationRad);

            Vec3 nodePos = center.add(rotatedX, 0, rotatedZ);

            VectorRenderer.drawSphereWorld(nodePos, 0.1f, color, 6, 8,
                    false, 3, VectorRenderer.Transform.IDENTITY);
        }
    }

    private static int getWheelColor(float resistanceLevel) {
        if (resistanceLevel >= 1.0f) {
            // Fully immune - bright gold
            return 0xFFFFD700;
        } else if (resistanceLevel >= 0.75f) {
            // 75% resistance - orange
            return 0xFFFF8C00;
        } else if (resistanceLevel >= 0.5f) {
            // 50% resistance - red-orange
            return 0xFFFF4500;
        } else {
            // No/low resistance - red
            return 0xFFFF0000;
        }
    }

    private static class WheelData {
        float currentRotation = 0.0f;
        float targetRotation = 0.0f;
        float resistanceLevel = 0.0f;
        long lastUpdateTime = System.currentTimeMillis();
    }
}