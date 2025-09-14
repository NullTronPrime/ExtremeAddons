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
                renderAdaptationWheel(playerlikeEntity, data, partialTick, event);
            }
        }
    }

    private static void renderAdaptationWheel(PlayerlikeEntity entity, WheelData data, float partialTick, RenderLevelStageEvent event) {
        // Smooth rotation interpolation
        float deltaTime = partialTick * 0.05f; // Adjust speed as needed
        float rotationDiff = data.targetRotation - data.currentRotation;

        // Handle rotation wrapping (shortest path)
        if (rotationDiff > 180.0f) {
            rotationDiff -= 360.0f;
        } else if (rotationDiff < -180.0f) {
            rotationDiff += 360.0f;
        }

        data.currentRotation += rotationDiff * deltaTime * 10.0f; // Smooth interpolation
        if (data.currentRotation >= 360.0f) data.currentRotation -= 360.0f;
        if (data.currentRotation < 0.0f) data.currentRotation += 360.0f;

        // Get entity position
        Vec3 entityPos = entity.position();
        Vec3 wheelCenter = entityPos.add(0, entity.getBbHeight() + 0.8, 0); // Above the entity

        // Determine wheel color based on resistance level
        int wheelColor = getWheelColor(data.resistanceLevel);

        // Render the main wheel circle
        renderWheelCircle(wheelCenter, 1.0f, wheelColor, data.currentRotation);

        // Render the spokes
        renderWheelSpokes(wheelCenter, 1.0f, wheelColor, data.currentRotation);

        // Render the center hub
        VectorRenderer.drawSphereWorld(wheelCenter, 0.1f, wheelColor, 8, 12, false, -1,
                VectorRenderer.Transform.IDENTITY);

        // Render outer nodes
        renderWheelNodes(wheelCenter, 1.0f, wheelColor, data.currentRotation);
    }

    private static void renderWheelCircle(Vec3 center, float radius, int color, float rotation) {
        // Draw main circle using line segments
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double angle1 = (i * 2.0 * Math.PI) / segments;
            double angle2 = ((i + 1) * 2.0 * Math.PI) / segments;

            Vec3 p1 = center.add(
                    Math.cos(angle1) * radius,
                    0,
                    Math.sin(angle1) * radius
            );

            Vec3 p2 = center.add(
                    Math.cos(angle2) * radius,
                    0,
                    Math.sin(angle2) * radius
            );

            VectorRenderer.drawLineWorld(p1, p2, color, 0.05f, false, -1,
                    VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void renderWheelSpokes(Vec3 center, float radius, int color, float rotation) {
        // Draw 8 spokes from center to edge
        int spokes = 8;
        for (int i = 0; i < spokes; i++) {
            double angle = (i * 2.0 * Math.PI) / spokes + Math.toRadians(rotation);

            Vec3 edgePoint = center.add(
                    Math.cos(angle) * radius,
                    0,
                    Math.sin(angle) * radius
            );

            VectorRenderer.drawLineWorld(center, edgePoint, color, 0.04f, false, -1,
                    VectorRenderer.Transform.IDENTITY);
        }
    }

    private static void renderWheelNodes(Vec3 center, float radius, int color, float rotation) {
        // Draw nodes at the edge of the wheel
        int nodes = 8;
        for (int i = 0; i < nodes; i++) {
            double angle = (i * 2.0 * Math.PI) / nodes + Math.toRadians(rotation);

            Vec3 nodePos = center.add(
                    Math.cos(angle) * radius,
                    0,
                    Math.sin(angle) * radius
            );

            VectorRenderer.drawSphereWorld(nodePos, 0.08f, color, 6, 8, false, -1,
                    VectorRenderer.Transform.IDENTITY);
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