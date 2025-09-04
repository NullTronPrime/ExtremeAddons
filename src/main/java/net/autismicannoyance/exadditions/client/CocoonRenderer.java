package net.autismicannoyance.exadditions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class CocoonRenderer {
    private static final Map<Integer, CocoonEffectData> EFFECTS = new ConcurrentHashMap<>();

    public static void addEffect(int entityId, int lifetime) {
        EFFECTS.put(entityId, new CocoonEffectData(entityId, lifetime));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();

        // **Use org.joml.Matrix4f** exactly (matches VertexConsumer.vertex signature in your mappings)
        Matrix4f matrix = event.getPoseStack().last().pose();

        // Render setup
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        Iterator<Map.Entry<Integer, CocoonEffectData>> iter = EFFECTS.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, CocoonEffectData> entry = iter.next();
            CocoonEffectData effect = entry.getValue();

            if (effect.age >= effect.lifetime) {
                iter.remove();
                continue;
            }

            Entity target = mc.level.getEntity(effect.entityId);
            if (!(target instanceof LivingEntity)) {
                iter.remove();
                continue;
            }

            // advance effect
            effect.age++;
            effect.angle += 4.0f;

            float partial = event.getPartialTick();
            double ex = Mth.lerp(partial, target.xOld, target.getX());
            double ey = Mth.lerp(partial, target.yOld, target.getY()) + target.getBbHeight() / 2.0;
            double ez = Mth.lerp(partial, target.zOld, target.getZ());

            // camera-relative coordinates (this is the common pattern)
            double cx = ex - camPos.x;
            double cy = ey - camPos.y;
            double cz = ez - camPos.z;

            for (int i = 0; i < 5; i++) {
                float baseAngle = effect.angle * (i % 2 == 0 ? 1f : -1f) + (i * 72f);
                drawSpinningTriangle(buffer, matrix, cx, cy, cz, baseAngle, effect.radius);
            }
        }

        // flush vertices (use end() in your mappings)
        tess.end();

        // restore GL state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawSpinningTriangle(BufferBuilder buffer, Matrix4f matrix, double cx, double cy, double cz, float angleDeg, float radius) {
        float r = 1f, g = 1f, b = 1f, a = 1f; // white

        for (int v = 0; v < 3; v++) {
            double theta = Math.toRadians(angleDeg + v * 120.0);
            double x = cx + Math.cos(theta) * radius;
            double y = cy + (v == 0 ? 0.5 : -0.5);
            double z = cz + Math.sin(theta) * radius;

            // **Pass org.joml.Matrix4f**, then world coords relative to camera
            buffer.vertex(matrix, (float) x, (float) y, (float) z)
                    .color(r, g, b, a)
                    .endVertex();
        }
    }

    private static class CocoonEffectData {
        final int entityId;
        float angle = 0f;
        final int lifetime;
        int age = 0;
        final float radius;

        CocoonEffectData(int entityId, int lifetime) {
            this.entityId = entityId;
            this.lifetime = lifetime;
            this.radius = 1.2f;
        }
    }
}
