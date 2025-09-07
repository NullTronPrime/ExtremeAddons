package net.autismicannoyance.exadditions.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Enhanced VectorRenderer with proper transparent rendering support.
 * Handles depth sorting and proper blend states for transparent objects.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class VectorRenderer {
    private static final ConcurrentLinkedQueue<RenderCommand> COMMANDS = new ConcurrentLinkedQueue<>();

    // Transparency thresholds for sorting
    private static final float OPAQUE_THRESHOLD = 0.99f;
    private static final float TRANSLUCENT_THRESHOLD = 0.01f;

    // Global configuration
    public static boolean GLOBAL_ENABLE_SCREEN_SPACE_THICKNESS = true;
    public static boolean GLOBAL_ENABLE_ENTITY_INTERPOLATION = true;

    /* ---------------- Public API ---------------- */

    // Line rendering
    public static void drawLineWorld(Vec3 a, Vec3 b, int colorArgb, float thickness, boolean thicknessIsPixels, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new LineCommand(a, b, colorArgb, thickness, thicknessIsPixels, lifetimeTicks, null, transform));
    }

    public static void drawLineAttached(int entityId, Vec3 offsetA, Vec3 offsetB, int colorArgb, float thickness, boolean thicknessIsPixels, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new LineCommand(null, null, colorArgb, thickness, thicknessIsPixels, lifetimeTicks, new Attachment(entityId, offsetA, offsetB, interpolate), transform));
    }

    // Polyline rendering
    public static void drawPolylineWorld(List<Vec3> points, int colorArgb, float thickness, boolean thicknessIsPixels, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PolylineCommand(points, colorArgb, thickness, thicknessIsPixels, lifetimeTicks, transform));
    }

    // Plane/triangle rendering
    public static void drawPlaneWorld(Vec3 a, Vec3 b, Vec3 c, int[] perVertexArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PlaneCommand(a, b, c, perVertexArgb, doubleSided, lifetimeTicks, null, null, transform));
    }

    public static void drawPlaneAttached(int entityId, Vec3 aOffset, Vec3 bOffset, Vec3 cOffset, int[] perVertexArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PlaneCommand(null, null, null, perVertexArgb, doubleSided, lifetimeTicks, null, new PlaneAttachment(entityId, aOffset, bOffset, cOffset, interpolate), transform));
    }

    // Filled polygon rendering
    public static void drawFilledPolygonWorld(List<Vec3> points, int colorArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PolygonCommand(points, colorArgb, doubleSided, lifetimeTicks, null, null, transform));
    }

    public static void drawFilledPolygonAttached(int entityId, List<Vec3> localOffsets, int colorArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        PolygonAttachment pa = new PolygonAttachment(entityId, localOffsets, interpolate);
        COMMANDS.add(new PolygonCommand(null, colorArgb, doubleSided, lifetimeTicks, null, pa, transform));
    }

    // Textured quad rendering
    public static void drawTexturedQuadWorld(Vec3 center, float width, float height, ResourceLocation texture, int colorArgb, boolean faceCamera, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new TexturedQuadCommand(center, width, height, texture, colorArgb, faceCamera, lifetimeTicks, null, transform));
    }

    public static void drawTexturedQuadAttached(int entityId, Vec3 offset, float width, float height, ResourceLocation texture, int colorArgb, boolean faceCamera, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new TexturedQuadCommand(null, width, height, texture, colorArgb, faceCamera, lifetimeTicks, new Attachment(entityId, offset, null, interpolate), transform));
    }

    // Wireframe rendering
    public static Wireframe createWireframe() { return new Wireframe(); }

    public static void drawWireframeWorld(Wireframe wf, Vec3 worldOrigin, int colorArgb, float thickness, boolean thicknessIsPixels, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new WireframeCommand(wf, worldOrigin, null, colorArgb, thickness, thicknessIsPixels, doubleSided, lifetimeTicks, transform));
    }

    public static void drawWireframeAttached(Wireframe wf, int entityId, Vec3 offset, boolean interpolate, int colorArgb, float thickness, boolean thicknessIsPixels, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new WireframeCommand(wf, null, new Attachment(entityId, offset, null, interpolate), colorArgb, thickness, thicknessIsPixels, doubleSided, lifetimeTicks, transform));
    }

    // Plane rectangle rendering
    public static void drawPlaneRectWorld(Vec3 center, Vec3 normal, float width, float height, int colorArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PlaneRectCommand(center, normal, width, height, colorArgb, doubleSided, lifetimeTicks, null, transform));
    }

    public static void drawPlaneRectAttached(int entityId, Vec3 centerOffset, Vec3 normal, float width, float height, int colorArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PlaneRectCommand(null, normal, width, height, colorArgb, doubleSided, lifetimeTicks, new Attachment(entityId, centerOffset, null, interpolate), transform));
    }

    // Sphere rendering
    public static void drawSphereWorld(Vec3 center, float radius, int colorArgb, int latSegments, int lonSegments, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new SphereCommand(center, radius, colorArgb, latSegments, lonSegments, doubleSided, lifetimeTicks, null, transform));
    }

    public static void drawSphereAttached(int entityId, Vec3 offset, float radius, int colorArgb, int latSegments, int lonSegments, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new SphereCommand(null, radius, colorArgb, latSegments, lonSegments, doubleSided, lifetimeTicks, new Attachment(entityId, offset, null, interpolate), transform));
    }

    // Box rendering
    public static void drawBoxWorld(Vec3 min, Vec3 max, int colorArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new BoxCommand(min, max, colorArgb, doubleSided, lifetimeTicks, null, transform));
    }

    public static void drawBoxAttached(int entityId, Vec3 minOffset, Vec3 maxOffset, int colorArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new BoxCommand(null, null, colorArgb, doubleSided, lifetimeTicks, new Attachment(entityId, minOffset, maxOffset, interpolate), transform));
    }

    // Cylinder rendering
    public static void drawCylinderWorld(Vec3 baseCenter, Vec3 axisDirection, float radius, float height, int radialSegments, int heightSegments, int colorArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new CylinderCommand(baseCenter, axisDirection, radius, height, radialSegments, heightSegments, colorArgb, doubleSided, lifetimeTicks, null, transform));
    }

    public static void drawCylinderAttached(int entityId, Vec3 baseOffset, Vec3 axisDirection, float radius, float height, int radialSegments, int heightSegments, int colorArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new CylinderCommand(null, axisDirection, radius, height, radialSegments, heightSegments, colorArgb, doubleSided, lifetimeTicks, new Attachment(entityId, baseOffset, null, interpolate), transform));
    }

    // Management functions
    public static void clearAll() { COMMANDS.clear(); }
    public static void removeExpired() { COMMANDS.removeIf(RenderCommand::isExpired); }

    /* ---------------- Enhanced rendering pipeline with transparency support ---------------- */

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        float partialTick = event.getPartialTick();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        Matrix4f matrix = event.getPoseStack().last().pose();

        // Snapshot commands for stable iteration
        List<RenderCommand> snapshot = new ArrayList<>(COMMANDS);

        // Separate commands by transparency and sort them
        List<RenderCommandDistance> opaqueCommands = new ArrayList<>();
        List<RenderCommandDistance> translucentCommands = new ArrayList<>();
        List<TexturedQuadCommand> texturedCommands = new ArrayList<>();

        for (RenderCommand cmd : snapshot) {
            if (cmd.isExpired()) continue;

            if (cmd instanceof TexturedQuadCommand) {
                texturedCommands.add((TexturedQuadCommand) cmd);
                continue;
            }

            float alpha = cmd.getAlpha();
            double distance = cmd.getDistanceToCamera(camPos, partialTick, mc);
            RenderCommandDistance cmdDist = new RenderCommandDistance(cmd, distance);

            if (alpha >= OPAQUE_THRESHOLD) {
                opaqueCommands.add(cmdDist);
            } else if (alpha > TRANSLUCENT_THRESHOLD) {
                translucentCommands.add(cmdDist);
            }
        }

        // Sort opaque front-to-back (for early Z rejection)
        opaqueCommands.sort(Comparator.comparingDouble(a -> a.distance));

        // Sort translucent back-to-front (for proper blending)
        translucentCommands.sort(Comparator.comparingDouble(a -> -a.distance));

        // 1) Render opaque objects first
        if (!opaqueCommands.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true); // Write to depth buffer for opaque objects
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (RenderCommandDistance cmdDist : opaqueCommands) {
                cmdDist.command.render(buffer, matrix, camPos, partialTick, mc);
                cmdDist.command.tick();
            }
            tess.end();
        }

        // 2) Render translucent objects
        if (!translucentCommands.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false); // Don't write to depth buffer for transparent objects
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (RenderCommandDistance cmdDist : translucentCommands) {
                cmdDist.command.render(buffer, matrix, camPos, partialTick, mc);
                cmdDist.command.tick();
            }
            tess.end();

            // Restore depth mask
            RenderSystem.depthMask(true);
        }

        // 3) Textured primitives (handle transparency separately)
        if (!texturedCommands.isEmpty()) {
            // Separate textured commands by transparency
            Map<ResourceLocation, List<TexturedQuadCommand>> opaqueTextured = new HashMap<>();
            Map<ResourceLocation, List<TexturedQuadCommand>> translucentTextured = new HashMap<>();

            for (TexturedQuadCommand tq : texturedCommands) {
                float alpha = ((tq.color >> 24) & 0xFF) / 255f;
                Map<ResourceLocation, List<TexturedQuadCommand>> targetMap =
                        alpha >= OPAQUE_THRESHOLD ? opaqueTextured : translucentTextured;
                targetMap.computeIfAbsent(tq.texture, k -> new ArrayList<>()).add(tq);
            }

            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

            // Render opaque textured first
            for (Map.Entry<ResourceLocation, List<TexturedQuadCommand>> entry : opaqueTextured.entrySet()) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.setShaderTexture(0, entry.getKey());

                buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
                for (TexturedQuadCommand tq : entry.getValue()) {
                    tq.render(buffer, matrix, camPos, partialTick, mc);
                    tq.tick();
                }
                tess.end();
            }

            // Render translucent textured last
            for (Map.Entry<ResourceLocation, List<TexturedQuadCommand>> entry : translucentTextured.entrySet()) {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(770, 771); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.setShaderTexture(0, entry.getKey());

                // Sort textured commands by distance
                List<TexturedQuadCommand> sorted = entry.getValue();
                sorted.sort((a, b) -> {
                    double distA = a.getDistanceToCamera(camPos, partialTick, mc);
                    double distB = b.getDistanceToCamera(camPos, partialTick, mc);
                    return Double.compare(distB, distA); // Back to front
                });

                buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
                for (TexturedQuadCommand tq : sorted) {
                    tq.render(buffer, matrix, camPos, partialTick, mc);
                    tq.tick();
                }
                tess.end();

                RenderSystem.depthMask(true);
            }
        }

        // Cleanup expired commands
        COMMANDS.removeIf(RenderCommand::isExpired);

        // Restore render state
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /* ---------------- Helper classes ---------------- */

    private static class RenderCommandDistance {
        final RenderCommand command;
        final double distance;

        RenderCommandDistance(RenderCommand command, double distance) {
            this.command = command;
            this.distance = distance;
        }
    }

    /* ---------------- Base command class ---------------- */

    private static abstract class RenderCommand {
        protected final int lifetime;
        protected int age = 0;
        protected final Transform transform;

        RenderCommand(int lifetime, Transform transform) {
            this.lifetime = lifetime;
            this.transform = transform == null ? Transform.IDENTITY : transform;
        }

        public boolean isExpired() { return lifetime >= 0 && age >= lifetime; }
        public void tick() { if (lifetime >= 0) age++; }

        // Get the alpha value of this command for transparency sorting
        public abstract float getAlpha();

        // Get the distance from camera for sorting
        public abstract double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc);

        abstract void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc);
    }

    /* ---------------- Attachment classes ---------------- */

    private static class Attachment {
        final int entityId;
        final Vec3 offsetA, offsetB;
        final boolean interpolate;
        Attachment(int entityId, Vec3 offsetA, Vec3 offsetB, boolean interpolate) {
            this.entityId = entityId;
            this.offsetA = offsetA == null ? Vec3.ZERO : offsetA;
            this.offsetB = offsetB == null ? Vec3.ZERO : offsetB;
            this.interpolate = interpolate;
        }
    }

    private static class PlaneAttachment {
        final int entityId;
        final Vec3 a, b, c;
        final boolean interpolate;
        PlaneAttachment(int entityId, Vec3 a, Vec3 b, Vec3 c, boolean interpolate) {
            this.entityId = entityId;
            this.a = a == null ? Vec3.ZERO : a;
            this.b = b == null ? Vec3.ZERO : b;
            this.c = c == null ? Vec3.ZERO : c;
            this.interpolate = interpolate;
        }
    }

    private static class PolygonAttachment {
        final int entityId;
        final List<Vec3> localPoints;
        final boolean interpolate;
        PolygonAttachment(int entityId, List<Vec3> localPoints, boolean interpolate) {
            this.entityId = entityId;
            this.localPoints = localPoints == null ? Collections.emptyList() : new ArrayList<>(localPoints);
            this.interpolate = interpolate;
        }
    }

    /* ---------------- Command implementations ---------------- */

    private static class LineCommand extends RenderCommand {
        private final Vec3 aOrig, bOrig;
        private final int color;
        private final float thicknessValue;
        private final boolean thicknessIsPixels;
        private final Attachment attachment;

        LineCommand(Vec3 a, Vec3 b, int color, float thicknessValue, boolean thicknessIsPixels, int lifetime, Attachment attachment, Transform transform) {
            super(lifetime, transform);
            this.aOrig = a; this.bOrig = b; this.color = color; this.thicknessValue = thicknessValue;
            this.thicknessIsPixels = thicknessIsPixels; this.attachment = attachment;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 a = getWorldPositionA(partialTick, mc);
            Vec3 b = getWorldPositionB(partialTick, mc);
            if (a == null || b == null) return Double.MAX_VALUE;

            // Return distance to midpoint of line
            Vec3 midpoint = a.add(b).scale(0.5);
            return midpoint.distanceTo(camPos);
        }

        private Vec3 getWorldPositionA(float partialTick, Minecraft mc) {
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                return base.add(attachment.offsetA);
            }
            return aOrig;
        }

        private Vec3 getWorldPositionB(float partialTick, Minecraft mc) {
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                return base.add(attachment.offsetB);
            }
            return bOrig;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 a = getWorldPositionA(partialTick, mc);
            Vec3 b = getWorldPositionB(partialTick, mc);
            if (a == null || b == null) return;

            a = applyTransform(a, transform);
            b = applyTransform(b, transform);

            renderLine(buffer, poseMatrix, a, b, camPos, color, thicknessValue, thicknessIsPixels, mc);
        }
    }

    private static class PolylineCommand extends RenderCommand {
        private final List<Vec3> points;
        private final int color;
        private final float thicknessValue;
        private final boolean thicknessIsPixels;

        PolylineCommand(List<Vec3> points, int color, float thicknessValue, boolean thicknessIsPixels, int lifetime, Transform transform) {
            super(lifetime, transform);
            this.points = new ArrayList<>(points);
            this.color = color; this.thicknessValue = thicknessValue; this.thicknessIsPixels = thicknessIsPixels;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            if (points.isEmpty()) return Double.MAX_VALUE;

            // Calculate centroid distance
            double avgX = 0, avgY = 0, avgZ = 0;
            for (Vec3 p : points) {
                Vec3 transformed = applyTransform(p, transform);
                avgX += transformed.x; avgY += transformed.y; avgZ += transformed.z;
            }
            avgX /= points.size(); avgY /= points.size(); avgZ /= points.size();
            return camPos.distanceTo(new Vec3(avgX, avgY, avgZ));
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            if (points.size() < 2) return;
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 pa = applyTransform(points.get(i), transform);
                Vec3 pb = applyTransform(points.get(i + 1), transform);
                renderLine(buffer, poseMatrix, pa, pb, camPos, color, thicknessValue, thicknessIsPixels, mc);
            }
        }
    }

    private static class PlaneCommand extends RenderCommand {
        private final Vec3 aOrig, bOrig, cOrig;
        private final int[] colors;
        private final boolean doubleSided;
        private final Attachment attachment;
        private final PlaneAttachment planeAttachment;

        PlaneCommand(Vec3 a, Vec3 b, Vec3 c, int[] colors, boolean doubleSided, int lifetime, Attachment attachment, PlaneAttachment planeAttachment, Transform transform) {
            super(lifetime, transform);
            this.aOrig = a; this.bOrig = b; this.cOrig = c; this.colors = colors; this.doubleSided = doubleSided;
            this.attachment = attachment; this.planeAttachment = planeAttachment;
        }

        @Override
        public float getAlpha() {
            if (colors != null && colors.length > 0) {
                // Return average alpha of all vertices
                float totalAlpha = 0;
                for (int color : colors) {
                    totalAlpha += ((color >> 24) & 0xFF) / 255f;
                }
                return totalAlpha / colors.length;
            }
            return 1.0f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 a = getWorldPositionA(partialTick, mc);
            Vec3 b = getWorldPositionB(partialTick, mc);
            Vec3 c = getWorldPositionC(partialTick, mc);
            if (a == null || b == null || c == null) return Double.MAX_VALUE;

            // Distance to triangle centroid
            Vec3 centroid = a.add(b).add(c).scale(1.0/3.0);
            return centroid.distanceTo(camPos);
        }

        private Vec3 getWorldPositionA(float partialTick, Minecraft mc) {
            if (planeAttachment != null) {
                Entity e = mc.level.getEntity(planeAttachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, planeAttachment.interpolate);
                return base.add(planeAttachment.a);
            }
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                return base.add(attachment.offsetA);
            }
            return aOrig;
        }

        private Vec3 getWorldPositionB(float partialTick, Minecraft mc) {
            if (planeAttachment != null) {
                Entity e = mc.level.getEntity(planeAttachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, planeAttachment.interpolate);
                return base.add(planeAttachment.b);
            }
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                return base.add(attachment.offsetB);
            }
            return bOrig;
        }

        private Vec3 getWorldPositionC(float partialTick, Minecraft mc) {
            if (planeAttachment != null) {
                Entity e = mc.level.getEntity(planeAttachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, planeAttachment.interpolate);
                return base.add(planeAttachment.c);
            }
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                return base.add(attachment.offsetB); // fallback
            }
            return cOrig;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 a = getWorldPositionA(partialTick, mc);
            Vec3 b = getWorldPositionB(partialTick, mc);
            Vec3 c = getWorldPositionC(partialTick, mc);
            if (a == null || b == null || c == null) return;

            a = applyTransform(a, transform);
            b = applyTransform(b, transform);
            c = applyTransform(c, transform);

            Vec3 ta = a.subtract(camPos);
            Vec3 tb = b.subtract(camPos);
            Vec3 tc = c.subtract(camPos);

            float[] ca = unpackColor(colors != null && colors.length > 0 ? colors[0] : 0xFFFFFFFF);
            float[] cb = unpackColor(colors != null && colors.length > 1 ? colors[1] : 0xFFFFFFFF);
            float[] cc = unpackColor(colors != null && colors.length > 2 ? colors[2] : 0xFFFFFFFF);

            putVertexWithRGBA(buffer, poseMatrix, ta, ca);
            putVertexWithRGBA(buffer, poseMatrix, tb, cb);
            putVertexWithRGBA(buffer, poseMatrix, tc, cc);

            if (doubleSided) {
                putVertexWithRGBA(buffer, poseMatrix, ta, ca);
                putVertexWithRGBA(buffer, poseMatrix, tc, cc);
                putVertexWithRGBA(buffer, poseMatrix, tb, cb);
            }
        }
    }

    private static class PolygonCommand extends RenderCommand {
        private final List<Vec3> worldPoints;
        private final int color;
        private final boolean doubleSided;
        private final PolygonAttachment polygonAttachment;

        PolygonCommand(List<Vec3> worldPoints, int color, boolean doubleSided, int lifetime, Attachment attachment, PolygonAttachment polygonAttachment, Transform transform) {
            super(lifetime, transform);
            this.worldPoints = worldPoints == null ? null : new ArrayList<>(worldPoints);
            this.color = color;
            this.doubleSided = doubleSided;
            this.polygonAttachment = polygonAttachment;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            List<Vec3> pts = getWorldPoints(partialTick, mc);
            if (pts.isEmpty()) return Double.MAX_VALUE;

            double cx=0, cy=0, cz=0;
            for (Vec3 p : pts) { cx += p.x; cy += p.y; cz += p.z; }
            cx /= pts.size(); cy /= pts.size(); cz /= pts.size();
            Vec3 center = new Vec3(cx, cy, cz);
            return center.distanceTo(camPos);
        }

        private List<Vec3> getWorldPoints(float partialTick, Minecraft mc) {
            List<Vec3> pts = new ArrayList<>();

            if (polygonAttachment != null) {
                Entity e = mc.level.getEntity(polygonAttachment.entityId);
                if (e == null) return pts;
                Vec3 base = getEntityPosition(e, partialTick, polygonAttachment.interpolate);
                for (Vec3 local : polygonAttachment.localPoints) pts.add(base.add(local));
            } else if (worldPoints != null) {
                for (Vec3 w : worldPoints) pts.add(w);
            }
            return pts;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            List<Vec3> pts = getWorldPoints(partialTick, mc);
            if (pts.size() < 3) return;

            double cx=0, cy=0, cz=0;
            for (Vec3 p : pts) { cx += p.x; cy += p.y; cz += p.z; }
            cx /= pts.size(); cy /= pts.size(); cz /= pts.size();
            Vec3 center = new Vec3(cx, cy, cz);

            List<Vec3> transformed = new ArrayList<>(pts.size());
            for (Vec3 p : pts) transformed.add(applyTransform(p, transform));
            Vec3 tCenter = applyTransform(center, transform);

            List<Vec3> rel = new ArrayList<>(transformed.size());
            for (Vec3 p : transformed) rel.add(p.subtract(camPos));
            Vec3 relCenter = tCenter.subtract(camPos);

            float[] rgba = unpackColor(color);

            int n = rel.size();
            for (int i=0;i<n;i++){
                Vec3 a = relCenter;
                Vec3 b = rel.get(i);
                Vec3 c = rel.get((i+1)%n);
                putVertexWithRGBA(buffer, poseMatrix, a, rgba);
                putVertexWithRGBA(buffer, poseMatrix, b, rgba);
                putVertexWithRGBA(buffer, poseMatrix, c, rgba);
                if (doubleSided) {
                    putVertexWithRGBA(buffer, poseMatrix, a, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, c, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, b, rgba);
                }
            }
        }
    }

    private static class TexturedQuadCommand extends RenderCommand {
        final ResourceLocation texture;
        final Vec3 center;
        final float width, height;
        final int color;
        final boolean faceCamera;
        final Attachment attachment;

        TexturedQuadCommand(Vec3 center, float width, float height, ResourceLocation texture, int color, boolean faceCamera, int lifetime, Attachment attachment, Transform transform) {
            super(lifetime, transform);
            this.center = center; this.width = width; this.height = height; this.texture = texture;
            this.color = color; this.faceCamera = faceCamera; this.attachment = attachment;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 worldCenter = getWorldCenter(partialTick, mc);
            if (worldCenter == null) return Double.MAX_VALUE;
            return worldCenter.distanceTo(camPos);
        }

        private Vec3 getWorldCenter(float partialTick, Minecraft mc) {
            Vec3 cen = center;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                cen = base.add(attachment.offsetA);
            }
            return cen;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 cen = getWorldCenter(partialTick, mc);
            if (cen == null) return;

            cen = applyTransform(cen, transform);
            Vec3 rc = cen.subtract(camPos);

            Vec3 right, up;
            if (faceCamera) {
                Vec3 view = camPos.subtract(cen).normalize();
                right = view.cross(new Vec3(0, 1, 0));
                if (right.length() <= 1e-6) right = view.cross(new Vec3(1, 0, 0));
                right = right.normalize().scale(width * 0.5);
                up = right.cross(view).normalize().scale(height * 0.5);
            } else {
                Quaternionf q = transform.rotationQuaternion;
                if (q != null) {
                    Vec3 forward = rotateVecByQuat(new Vec3(0, 0, 1), q);
                    Vec3 upVec = rotateVecByQuat(new Vec3(0, 1, 0), q);
                    right = forward.cross(upVec).normalize().scale(width * 0.5);
                    up = upVec.normalize().scale(height * 0.5);
                } else {
                    right = new Vec3(1, 0, 0).scale(width * 0.5);
                    up = new Vec3(0, 1, 0).scale(height * 0.5);
                }
            }

            float[] rgba = unpackColor(color);

            Vec3 p1 = rc.subtract(right).subtract(up);
            Vec3 p2 = rc.add(right).subtract(up);
            Vec3 p3 = rc.add(right).add(up);
            Vec3 p4 = rc.subtract(right).add(up);

            putTexturedVertex(buffer, poseMatrix, p1, 0f, 1f, rgba);
            putTexturedVertex(buffer, poseMatrix, p2, 1f, 1f, rgba);
            putTexturedVertex(buffer, poseMatrix, p3, 1f, 0f, rgba);
            putTexturedVertex(buffer, poseMatrix, p1, 0f, 1f, rgba);
            putTexturedVertex(buffer, poseMatrix, p3, 1f, 0f, rgba);
            putTexturedVertex(buffer, poseMatrix, p4, 0f, 0f, rgba);
        }
    }

    private static class WireframeCommand extends RenderCommand {
        private final Wireframe wf;
        private final Vec3 worldOrigin;
        private final Attachment attachment;
        private final int color;
        private final float thickness;
        private final boolean thicknessIsPixels;
        private final boolean doubleSided;

        WireframeCommand(Wireframe wf, Vec3 worldOrigin, Attachment attachment, int color, float thickness, boolean thicknessIsPixels, boolean doubleSided, int lifetime, Transform transform) {
            super(lifetime, transform);
            this.wf = wf; this.worldOrigin = worldOrigin; this.attachment = attachment;
            this.color = color; this.thickness = thickness; this.thicknessIsPixels = thicknessIsPixels;
            this.doubleSided = doubleSided;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 base = getWorldOrigin(partialTick, mc);
            if (base == null) return Double.MAX_VALUE;
            return base.distanceTo(camPos);
        }

        private Vec3 getWorldOrigin(float partialTick, Minecraft mc) {
            Vec3 base = worldOrigin;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 entityPos = getEntityPosition(e, partialTick, attachment.interpolate);
                base = entityPos.add(attachment.offsetA);
            }
            return base;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 base = getWorldOrigin(partialTick, mc);
            if (base == null) return;

            for (Wireframe.Segment s : wf.segments) {
                Vec3 aWorld = base.add(applyLocalTransform(s.a, transform));
                Vec3 bWorld = base.add(applyLocalTransform(s.b, transform));
                renderLine(buffer, poseMatrix, aWorld, bWorld, camPos, color, thickness, thicknessIsPixels, mc);
            }
        }

        private Vec3 applyLocalTransform(Vec3 local, Transform t) {
            if (t == null || t == Transform.IDENTITY) return local;
            double x = local.x - t.pivot.x; double y = local.y - t.pivot.y; double z = local.z - t.pivot.z;
            x *= t.scale; y *= t.scale; z *= t.scale;
            if (t.rotationQuaternion != null) {
                Vec3 v = rotateVecByQuat(new Vec3(x, y, z), t.rotationQuaternion);
                x = v.x; y = v.y; z = v.z;
            } else {
                double yaw = Math.toRadians(t.yawDeg), pitch = Math.toRadians(t.pitchDeg), roll = Math.toRadians(t.rollDeg);
                double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
                double rx = cosY * x + sinY * z;
                double rz = -sinY * x + cosY * z;
                double ry = y;
                double cosX = Math.cos(pitch), sinX = Math.sin(pitch);
                double ry2 = cosX * ry - sinX * rz;
                double rz2 = sinX * ry + cosX * rz;
                double cosZ = Math.cos(roll), sinZ = Math.sin(roll);
                double rx2 = cosZ * rx - sinZ * ry2;
                double ry3 = sinZ * rx + cosZ * ry2;
                double rz3 = rz2;
                x = rx2; y = ry3; z = rz3;
            }
            double finalX = x + t.pivot.x + t.translation.x;
            double finalY = y + t.pivot.y + t.translation.y;
            double finalZ = z + t.pivot.z + t.translation.z;
            return new Vec3(finalX, finalY, finalZ);
        }
    }

    private static class PlaneRectCommand extends RenderCommand {
        final Vec3 centerOrig;
        final Vec3 normal;
        final float width, height;
        final int color;
        final boolean doubleSided;
        final Attachment attachment;

        PlaneRectCommand(Vec3 center, Vec3 normal, float width, float height, int color, boolean doubleSided, int lifetime, Attachment attachment, Transform transform) {
            super(lifetime, transform);
            this.centerOrig = center;
            this.normal = normal == null ? new Vec3(0, 1, 0) : normal;
            this.width = width; this.height = height; this.color = color; this.doubleSided = doubleSided; this.attachment = attachment;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 center = getWorldCenter(partialTick, mc);
            if (center == null) return Double.MAX_VALUE;
            return center.distanceTo(camPos);
        }

        private Vec3 getWorldCenter(float partialTick, Minecraft mc) {
            Vec3 center = centerOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                center = base.add(attachment.offsetA);
            }
            return center;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 center = getWorldCenter(partialTick, mc);
            if (center == null) return;

            Vec3 n = this.normal.normalize();
            Vec3 upCandidate = new Vec3(0, 1, 0);
            if (Math.abs(n.dot(upCandidate)) > 0.999) upCandidate = new Vec3(1, 0, 0);
            Vec3 right = upCandidate.cross(n).normalize().scale(width * 0.5);
            Vec3 up = n.cross(right).normalize().scale(height * 0.5);

            Vec3 p1 = center.add(right).add(up);
            Vec3 p2 = center.subtract(right).add(up);
            Vec3 p3 = center.subtract(right).subtract(up);
            Vec3 p4 = center.add(right).subtract(up);

            p1 = applyTransform(p1, transform).subtract(camPos);
            p2 = applyTransform(p2, transform).subtract(camPos);
            p3 = applyTransform(p3, transform).subtract(camPos);
            p4 = applyTransform(p4, transform).subtract(camPos);

            float[] rgba = unpackColor(color);

            putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
            putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
            putVertexWithRGBA(buffer, poseMatrix, p3, rgba);
            putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
            putVertexWithRGBA(buffer, poseMatrix, p3, rgba);
            putVertexWithRGBA(buffer, poseMatrix, p4, rgba);

            if (doubleSided) {
                putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p3, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p4, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p3, rgba);
            }
        }
    }

    private static class SphereCommand extends RenderCommand {
        final Vec3 centerOrig;
        final float radius;
        final int color;
        final int latSegments, lonSegments;
        final boolean doubleSided;
        final Attachment attachment;

        SphereCommand(Vec3 center, float radius, int color, int latSegments, int lonSegments, boolean doubleSided, int lifetime, Attachment attachment, Transform transform) {
            super(lifetime, transform);
            this.centerOrig = center;
            this.radius = Math.max(0.0001f, radius);
            this.color = color;
            this.latSegments = Math.max(2, latSegments);
            this.lonSegments = Math.max(3, lonSegments);
            this.doubleSided = doubleSided;
            this.attachment = attachment;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 center = getCenterPosition(partialTick, mc);
            if (center == null) return Double.MAX_VALUE;
            return center.distanceTo(camPos);
        }

        private Vec3 getCenterPosition(float partialTick, Minecraft mc) {
            Vec3 center = centerOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                center = base.add(attachment.offsetA);
            }
            return center;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 center = getCenterPosition(partialTick, mc);
            if (center == null) return;

            // LOD calculation
            int lat = latSegments, lon = lonSegments;
            double dist = center.distanceTo(camPos);
            if (dist <= 0) dist = 0.0001;
            {
                double fov = mc.options.fov().get();
                int screenH = mc.getWindow().getGuiScaledHeight();
                double projectedRadiusPx = radius * screenH / (2.0 * dist * Math.tan(Math.toRadians(fov * 0.5)));
                if (projectedRadiusPx < 4) { lat = Math.max(6, lat/4); lon = Math.max(8, lon/4); }
                else if (projectedRadiusPx < 12) { lat = Math.max(8, lat/2); lon = Math.max(12, lon/2); }
            }

            float[] mesh = MeshCache.getUnitSphere(lat, lon);
            if (mesh == null || mesh.length == 0) return;

            float[] rgba = unpackColor(color);
            float[] out = TL_VEC.get();

            for (int i = 0; i < mesh.length; i += 3) {
                double vx = mesh[i] * radius + center.x;
                double vy = mesh[i+1] * radius + center.y;
                double vz = mesh[i+2] * radius + center.z;

                applyTransformToFloats(vx, vy, vz, transform, out);
                float camX = out[0] - (float) camPos.x;
                float camY = out[1] - (float) camPos.y;
                float camZ = out[2] - (float) camPos.z;

                putVertexFromFloats(buffer, poseMatrix, camX, camY, camZ, rgba);
            }
        }
    }

    private static class BoxCommand extends RenderCommand {
        final Vec3 minOrig, maxOrig;
        final int color;
        final boolean doubleSided;
        final Attachment attachment;

        BoxCommand(Vec3 min, Vec3 max, int color, boolean doubleSided, int lifetime, Attachment attachment, Transform transform) {
            super(lifetime, transform);
            this.minOrig = min;
            this.maxOrig = max;
            this.color = color;
            this.doubleSided = doubleSided;
            this.attachment = attachment;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 min = getMinPosition(partialTick, mc);
            Vec3 max = getMaxPosition(partialTick, mc);
            if (min == null || max == null) return Double.MAX_VALUE;

            Vec3 center = min.add(max).scale(0.5);
            return center.distanceTo(camPos);
        }

        private Vec3 getMinPosition(float partialTick, Minecraft mc) {
            Vec3 min = minOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                min = base.add(attachment.offsetA);
            }
            return min;
        }

        private Vec3 getMaxPosition(float partialTick, Minecraft mc) {
            Vec3 max = maxOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 base = getEntityPosition(e, partialTick, attachment.interpolate);
                max = base.add(attachment.offsetB);
            }
            return max;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 min = getMinPosition(partialTick, mc);
            Vec3 max = getMaxPosition(partialTick, mc);
            if (min == null || max == null) return;

            Vec3 v000 = new Vec3(min.x, min.y, min.z);
            Vec3 v100 = new Vec3(max.x, min.y, min.z);
            Vec3 v110 = new Vec3(max.x, max.y, min.z);
            Vec3 v010 = new Vec3(min.x, max.y, min.z);
            Vec3 v001 = new Vec3(min.x, min.y, max.z);
            Vec3 v101 = new Vec3(max.x, min.y, max.z);
            Vec3 v111 = new Vec3(max.x, max.y, max.z);
            Vec3 v011 = new Vec3(min.x, max.y, max.z);

            Vec3[] vs = new Vec3[]{v000, v100, v110, v010, v001, v101, v111, v011};
            for (int i = 0; i < vs.length; i++) vs[i] = applyTransform(vs[i], transform).subtract(camPos);

            float[] rgba = unpackColor(color);
            int[][] faces = {
                    {0,1,2,3}, {5,4,7,6}, {1,5,6,2}, {4,0,3,7}, {3,2,6,7}, {4,5,1,0}
            };

            for (int[] f : faces) {
                Vec3 p0 = vs[f[0]], p1 = vs[f[1]], p2 = vs[f[2]], p3 = vs[f[3]];
                putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p3, rgba);

                if (doubleSided) {
                    putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p3, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                }
            }
        }
    }

    private static class CylinderCommand extends RenderCommand {
        final Vec3 baseOrig;
        final Vec3 axisDirection;
        final float radius;
        final float height;
        final int radialSegments, heightSegments;
        final int color;
        final boolean doubleSided;
        final Attachment attachment;

        CylinderCommand(Vec3 base, Vec3 axisDirection, float radius, float height, int radialSegments, int heightSegments, int color, boolean doubleSided, int lifetime, Attachment attachment, Transform transform) {
            super(lifetime, transform);
            this.baseOrig = base;
            this.axisDirection = axisDirection == null ? new Vec3(0, 1, 0) : axisDirection.normalize();
            this.radius = Math.max(0.0001f, radius);
            this.height = height;
            this.radialSegments = Math.max(3, radialSegments);
            this.heightSegments = Math.max(1, heightSegments);
            this.color = color;
            this.doubleSided = doubleSided;
            this.attachment = attachment;
        }

        @Override
        public float getAlpha() {
            return ((color >> 24) & 0xFF) / 255f;
        }

        @Override
        public double getDistanceToCamera(Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 base = getBasePosition(partialTick, mc);
            if (base == null) return Double.MAX_VALUE;
            return base.distanceTo(camPos);
        }

        private Vec3 getBasePosition(float partialTick, Minecraft mc) {
            Vec3 base = baseOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return null;
                Vec3 entityBase = getEntityPosition(e, partialTick, attachment.interpolate);
                base = entityBase.add(attachment.offsetA);
            }
            return base;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 base = getBasePosition(partialTick, mc);
            if (base == null) return;

            // LOD based on projected radius
            int radial = radialSegments;
            double dist = base.distanceTo(camPos);
            if (dist <= 0) dist = 0.0001;
            {
                double fov = mc.options.fov().get();
                int screenH = mc.getWindow().getGuiScaledHeight();
                double projectedRadiusPx = radius * screenH / (2.0 * dist * Math.tan(Math.toRadians(fov * 0.5)));
                if (projectedRadiusPx < 4) radial = Math.max(6, radial / 4);
                else if (projectedRadiusPx < 12) radial = Math.max(8, radial / 2);
            }

            float[] mesh = MeshCache.getUnitCylinder(radial, heightSegments);
            if (mesh == null || mesh.length == 0) return;

            float[] rgba = unpackColor(color);
            float[] out = TL_VEC.get();

            for (int i = 0; i < mesh.length; i += 4) {
                double ux = mesh[i];
                double uy = mesh[i+1];
                double uz = mesh[i+2];
                double vx = ux * radius;
                double vy = uy * height;
                double vz = uz * radius;
                double worldX = base.x + vx;
                double worldY = base.y + vy;
                double worldZ = base.z + vz;

                applyTransformToFloats(worldX, worldY, worldZ, transform, out);
                float camX = out[0] - (float) camPos.x;
                float camY = out[1] - (float) camPos.y;
                float camZ = out[2] - (float) camPos.z;

                putVertexFromFloats(buffer, poseMatrix, camX, camY, camZ, rgba);
            }
        }
    }

    /* ---------------- Wireframe support ---------------- */

    public static class Wireframe {
        private final List<Segment> segments = new ArrayList<>();

        public void addLine(Vec3 aLocal, Vec3 bLocal) {
            segments.add(new Segment(aLocal, bLocal));
        }

        public void clear() {
            segments.clear();
        }

        private static class Segment {
            final Vec3 a, b;
            Segment(Vec3 a, Vec3 b) {
                this.a = a;
                this.b = b;
            }
        }
    }

    /* ---------------- Transform system ---------------- */

    public static class Transform {
        public final Vec3 translation;
        public final float yawDeg, pitchDeg, rollDeg;
        public final float scale;
        public final Vec3 pivot;
        public final Quaternionf rotationQuaternion;

        public static final Transform IDENTITY = new Transform(Vec3.ZERO, 0f, 0f, 0f, 1f, Vec3.ZERO, null);

        public Transform(Vec3 translation, float yawDeg, float pitchDeg, float rollDeg, float scale, Vec3 pivot, Quaternionf quat) {
            this.translation = translation == null ? Vec3.ZERO : translation;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.scale = scale;
            this.pivot = pivot == null ? Vec3.ZERO : pivot;
            this.rotationQuaternion = quat;
        }

        public static Transform fromEuler(Vec3 translation, float yawDeg, float pitchDeg, float rollDeg, float scale, Vec3 pivot) {
            return new Transform(translation, yawDeg, pitchDeg, rollDeg, scale, pivot, null);
        }

        public static Transform fromQuaternion(Vec3 translation, Quaternionf quat, float scale, Vec3 pivot) {
            return new Transform(translation, 0f, 0f, 0f, scale, pivot, quat);
        }
    }

    /* ---------------- Mesh caching system ---------------- */

    private static final class MeshCache {
        private static final ConcurrentHashMap<String, float[]> CACHE = new ConcurrentHashMap<>();

        static float[] getUnitSphere(int latSegments, int lonSegments) {
            String key = "sphere:" + latSegments + ":" + lonSegments;
            return CACHE.computeIfAbsent(key, k -> buildUnitSphere(latSegments, lonSegments));
        }

        private static float[] buildUnitSphere(int latSegments, int lonSegments) {
            ArrayList<Float> verts = new ArrayList<>();
            for (int lat = 0; lat < latSegments; lat++) {
                double theta1 = Math.PI * (double) lat / latSegments;
                double theta2 = Math.PI * (double) (lat + 1) / latSegments;
                double y1 = Math.cos(theta1), r1 = Math.sin(theta1);
                double y2 = Math.cos(theta2), r2 = Math.sin(theta2);

                for (int lon = 0; lon < lonSegments; lon++) {
                    double phi1 = 2.0 * Math.PI * (double) lon / lonSegments;
                    double phi2 = 2.0 * Math.PI * (double) (lon + 1) / lonSegments;

                    float v11x = (float) (r1 * Math.cos(phi1));
                    float v11y = (float) (y1);
                    float v11z = (float) (r1 * Math.sin(phi1));

                    float v12x = (float) (r1 * Math.cos(phi2));
                    float v12y = (float) (y1);
                    float v12z = (float) (r1 * Math.sin(phi2));

                    float v21x = (float) (r2 * Math.cos(phi1));
                    float v21y = (float) (y2);
                    float v21z = (float) (r2 * Math.sin(phi1));

                    float v22x = (float) (r2 * Math.cos(phi2));
                    float v22y = (float) (y2);
                    float v22z = (float) (r2 * Math.sin(phi2));

                    // 2 triangles: (v11,v21,v22) and (v11,v22,v12)
                    verts.add(v11x); verts.add(v11y); verts.add(v11z);
                    verts.add(v21x); verts.add(v21y); verts.add(v21z);
                    verts.add(v22x); verts.add(v22y); verts.add(v22z);

                    verts.add(v11x); verts.add(v11y); verts.add(v11z);
                    verts.add(v22x); verts.add(v22y); verts.add(v22z);
                    verts.add(v12x); verts.add(v12y); verts.add(v12z);
                }
            }
            float[] mesh = new float[verts.size()];
            for (int i = 0; i < verts.size(); i++) mesh[i] = verts.get(i);
            return mesh;
        }

        static float[] getUnitCylinder(int radial, int heightSegs) {
            String key = "cyl:" + radial + ":" + heightSegs;
            return CACHE.computeIfAbsent(key, k -> buildUnitCylinder(radial, heightSegs));
        }

        private static float[] buildUnitCylinder(int radial, int heightSegs) {
            ArrayList<Float> verts = new ArrayList<>();
            for (int h = 0; h < heightSegs; h++) {
                double t0 = (double) h / heightSegs;
                double t1 = (double) (h + 1) / heightSegs;
                for (int s = 0; s < radial; s++) {
                    double phi0 = 2.0 * Math.PI * s / radial;
                    double phi1 = 2.0 * Math.PI * (s + 1) / radial;

                    float x00 = (float) Math.cos(phi0), z00 = (float) Math.sin(phi0);
                    float x01 = (float) Math.cos(phi1), z01 = (float) Math.sin(phi1);

                    // two triangles
                    verts.add(x00); verts.add((float) t0); verts.add(z00); verts.add(0f);
                    verts.add(x01); verts.add((float) t0); verts.add(z01); verts.add(0f);
                    verts.add(x01); verts.add((float) t1); verts.add(z01); verts.add(0f);

                    verts.add(x00); verts.add((float) t0); verts.add(z00); verts.add(0f);
                    verts.add(x01); verts.add((float) t1); verts.add(z01); verts.add(0f);
                    verts.add(x00); verts.add((float) t1); verts.add(z00); verts.add(0f);
                }
            }

            // bottom cap at y=0
            for (int s = 0; s < radial; s++) {
                double phi0 = 2.0 * Math.PI * s / radial;
                double phi1 = 2.0 * Math.PI * (s + 1) / radial;
                verts.add(0f); verts.add(0f); verts.add(0f); verts.add(0f); // center
                verts.add((float)Math.cos(phi1)); verts.add(0f); verts.add((float)Math.sin(phi1)); verts.add(0f);
                verts.add((float)Math.cos(phi0)); verts.add(0f); verts.add((float)Math.sin(phi0)); verts.add(0f);
            }
            // top cap at y=1
            for (int s = 0; s < radial; s++) {
                double phi0 = 2.0 * Math.PI * s / radial;
                double phi1 = 2.0 * Math.PI * (s + 1) / radial;
                verts.add(0f); verts.add(1f); verts.add(0f); verts.add(0f); // center
                verts.add((float)Math.cos(phi0)); verts.add(1f); verts.add((float)Math.sin(phi0)); verts.add(0f);
                verts.add((float)Math.cos(phi1)); verts.add(1f); verts.add((float)Math.sin(phi1)); verts.add(0f);
            }

            float[] mesh = new float[verts.size()];
            for (int i = 0; i < verts.size(); i++) mesh[i] = verts.get(i);
            return mesh;
        }

        static void clear() { CACHE.clear(); }
    }

    /* ---------------- Utility methods ---------------- */

    // ThreadLocal scratch array to avoid allocations
    private static final ThreadLocal<float[]> TL_VEC = ThreadLocal.withInitial(() -> new float[3]);

    private static float[] unpackColor(int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    private static void putVertexFromFloats(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float[] rgba) {
        buffer.vertex(matrix, x, y, z)
                .color(rgba[0], rgba[1], rgba[2], rgba[3])
                .endVertex();
    }

    private static void putVertex(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float[] rgba) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(rgba[0], rgba[1], rgba[2], rgba[3])
                .endVertex();
    }

    private static void putVertexWithRGBA(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float[] rgba) {
        putVertex(buffer, matrix, p, rgba);
    }

    private static void putTexturedVertex(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float u, float v, float[] rgba) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .uv(u, v)
                .color(rgba[0], rgba[1], rgba[2], rgba[3])
                .endVertex();
    }

    private static void renderLine(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 a, Vec3 b, Vec3 camPos,
                                   int color, float thickness, boolean thicknessIsPixels, Minecraft mc) {
        Vec3 ra = a.subtract(camPos);
        Vec3 rb = b.subtract(camPos);
        Vec3 dir = rb.subtract(ra);
        double len = dir.length();
        if (len <= 1e-6) return;

        float effThickness;
        if (thicknessIsPixels && GLOBAL_ENABLE_SCREEN_SPACE_THICKNESS) {
            effThickness = pixelThicknessToWorld(thickness, (float) ra.length(), mc);
        } else {
            effThickness = thickness;
        }

        Vec3 viewDir = camPos.subtract(a);
        Vec3 perp = dir.cross(viewDir);
        if (perp.length() <= 1e-6) {
            perp = dir.cross(new Vec3(0, 1, 0));
            if (perp.length() <= 1e-6) return;
        }
        perp = perp.normalize().scale(effThickness * 0.5);

        Vec3 p1 = ra.add(perp);
        Vec3 p2 = ra.subtract(perp);
        Vec3 p3 = rb.subtract(perp);
        Vec3 p4 = rb.add(perp);

        float[] rgba = unpackColor(color);
        putVertex(buffer, poseMatrix, p1, rgba);
        putVertex(buffer, poseMatrix, p2, rgba);
        putVertex(buffer, poseMatrix, p3, rgba);
        putVertex(buffer, poseMatrix, p1, rgba);
        putVertex(buffer, poseMatrix, p3, rgba);
        putVertex(buffer, poseMatrix, p4, rgba);
    }

    private static float pixelThicknessToWorld(float pixelThickness, float distance, Minecraft mc) {
        double fovDegrees = mc.options.fov().get();
        int screenH = mc.getWindow().getGuiScaledHeight();
        double worldPerPixel = 2.0 * distance * Math.tan(Math.toRadians(fovDegrees * 0.5)) / Math.max(1, screenH);
        return (float) (pixelThickness * worldPerPixel);
    }

    private static Vec3 getEntityPosition(Entity e, float partialTick, boolean interpolate) {
        if (interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
            double ix = Mth.lerp(partialTick, e.xOld, e.getX());
            double iy = Mth.lerp(partialTick, e.yOld, e.getY());
            double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
            return new Vec3(ix, iy, iz);
        }
        return e.position();
    }

    private static void applyTransformToFloats(double x0, double y0, double z0, Transform t, float[] out) {
        if (t == null || t == Transform.IDENTITY) {
            out[0] = (float) x0;
            out[1] = (float) y0;
            out[2] = (float) z0;
            return;
        }
        double x = x0 - t.pivot.x, y = y0 - t.pivot.y, z = z0 - t.pivot.z;
        x *= t.scale; y *= t.scale; z *= t.scale;

        if (t.rotationQuaternion != null) {
            org.joml.Vector3f v = new org.joml.Vector3f((float) x, (float) y, (float) z);
            org.joml.Vector3f outv = new org.joml.Vector3f();
            t.rotationQuaternion.transform(v, outv);
            x = outv.x(); y = outv.y(); z = outv.z();
        } else {
            double yaw = Math.toRadians(t.yawDeg), pitch = Math.toRadians(t.pitchDeg), roll = Math.toRadians(t.rollDeg);
            double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
            double rx = cosY * x + sinY * z;
            double rz = -sinY * x + cosY * z;
            double ry = y;
            double cosX = Math.cos(pitch), sinX = Math.sin(pitch);
            double ry2 = cosX * ry - sinX * rz;
            double rz2 = sinX * ry + cosX * rz;
            double cosZ = Math.cos(roll), sinZ = Math.sin(roll);
            double rx2 = cosZ * rx - sinZ * ry2;
            double ry3 = sinZ * rx + cosZ * ry2;
            double rz3 = rz2;
            x = rx2; y = ry3; z = rz3;
        }

        double finalX = x + t.pivot.x + t.translation.x;
        double finalY = y + t.pivot.y + t.translation.y;
        double finalZ = z + t.pivot.z + t.translation.z;
        out[0] = (float) finalX;
        out[1] = (float) finalY;
        out[2] = (float) finalZ;
    }

    private static Vec3 applyTransform(Vec3 p, Transform t) {
        if (t == null || t == Transform.IDENTITY) return p;

        double x = p.x - t.pivot.x;
        double y = p.y - t.pivot.y;
        double z = p.z - t.pivot.z;

        x *= t.scale; y *= t.scale; z *= t.scale;

        if (t.rotationQuaternion != null) {
            Vec3 v = rotateVecByQuat(new Vec3(x, y, z), t.rotationQuaternion);
            x = v.x; y = v.y; z = v.z;
        } else {
            double yaw = Math.toRadians(t.yawDeg), pitch = Math.toRadians(t.pitchDeg), roll = Math.toRadians(t.rollDeg);
            double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
            double rx = cosY * x + sinY * z;
            double rz = -sinY * x + cosY * z;
            double ry = y;
            double cosX = Math.cos(pitch), sinX = Math.sin(pitch);
            double ry2 = cosX * ry - sinX * rz;
            double rz2 = sinX * ry + cosX * rz;
            double cosZ = Math.cos(roll), sinZ = Math.sin(roll);
            double rx2 = cosZ * rx - sinZ * ry2;
            double ry3 = sinZ * rx + cosZ * ry2;
            double rz3 = rz2;
            x = rx2; y = ry3; z = rz3;
        }

        double finalX = x + t.pivot.x + t.translation.x;
        double finalY = y + t.pivot.y + t.translation.y;
        double finalZ = z + t.pivot.z + t.translation.z;
        return new Vec3(finalX, finalY, finalZ);
    }

    private static Vec3 rotateVecByQuat(Vec3 v, Quaternionf q) {
        org.joml.Vector3f vec = new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z);
        org.joml.Vector3f out = new org.joml.Vector3f();
        q.transform(vec, out);
        return new Vec3(out.x(), out.y(), out.z());
    }
}