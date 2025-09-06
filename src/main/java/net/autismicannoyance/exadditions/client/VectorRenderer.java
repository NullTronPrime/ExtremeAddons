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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * VectorRenderer v2 — Command-based renderer with wireframe groups and filled polygon support.
 * Extended with: sphere, box (AABB), plane-rectangle, cylinder commands + world/attached API convenience wrappers.
 *
 * Designed for Forge 1.20.1 (parchment mappings).
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class VectorRenderer {
    private static final List<RenderCommand> COMMANDS = new CopyOnWriteArrayList<>();

    // Global toggles
    public static boolean GLOBAL_ENABLE_SCREEN_SPACE_THICKNESS = true;
    public static boolean GLOBAL_ENABLE_ENTITY_INTERPOLATION = true;

    /* ---------------- Public API ---------------- */

    // Lines
    public static void drawLineWorld(Vec3 a, Vec3 b, int colorArgb, float thickness, boolean thicknessIsPixels, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new LineCommand(a, b, colorArgb, thickness, thicknessIsPixels, lifetimeTicks, null, transform));
    }

    public static void drawLineAttached(int entityId, Vec3 offsetA, Vec3 offsetB, int colorArgb, float thickness, boolean thicknessIsPixels, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new LineCommand(null, null, colorArgb, thickness, thicknessIsPixels, lifetimeTicks, new Attachment(entityId, offsetA, offsetB, interpolate), transform));
    }

    // Polylines (list of world points)
    public static void drawPolylineWorld(List<Vec3> points, int colorArgb, float thickness, boolean thicknessIsPixels, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PolylineCommand(points, colorArgb, thickness, thicknessIsPixels, lifetimeTicks, transform));
    }

    // Plane/triangle (existing triangle API)
    public static void drawPlaneWorld(Vec3 a, Vec3 b, Vec3 c, int[] perVertexArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PlaneCommand(a, b, c, perVertexArgb, doubleSided, lifetimeTicks, null, null, transform));
    }

    public static void drawPlaneAttached(int entityId, Vec3 aOffset, Vec3 bOffset, Vec3 cOffset, int[] perVertexArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PlaneCommand(null, null, null, perVertexArgb, doubleSided, lifetimeTicks, null, new PlaneAttachment(entityId, aOffset, bOffset, cOffset, interpolate), transform));
    }

    // Filled polygon (triangle fan)
    public static void drawFilledPolygonWorld(List<Vec3> points, int colorArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PolygonCommand(points, colorArgb, doubleSided, lifetimeTicks, null, null, transform));
    }

    public static void drawFilledPolygonAttached(int entityId, List<Vec3> localOffsets, int colorArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        PolygonAttachment pa = new PolygonAttachment(entityId, localOffsets, interpolate);
        COMMANDS.add(new PolygonCommand(null, colorArgb, doubleSided, lifetimeTicks, null, pa, transform));
    }

    // Textured quad
    public static void drawTexturedQuadWorld(Vec3 center, float width, float height, ResourceLocation texture, int colorArgb, boolean faceCamera, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new TexturedQuadCommand(center, width, height, texture, colorArgb, faceCamera, lifetimeTicks, null, transform));
    }

    public static void drawTexturedQuadAttached(int entityId, Vec3 offset, float width, float height, ResourceLocation texture, int colorArgb, boolean faceCamera, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new TexturedQuadCommand(null, width, height, texture, colorArgb, faceCamera, lifetimeTicks, new Attachment(entityId, offset, null, interpolate), transform));
    }

    // Wireframe: group many lines defined in local/object space
    public static Wireframe createWireframe() { return new Wireframe(); }

    public static void drawWireframeWorld(Wireframe wf, Vec3 worldOrigin, int colorArgb, float thickness, boolean thicknessIsPixels, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new WireframeCommand(wf, worldOrigin, null, colorArgb, thickness, thicknessIsPixels, doubleSided, lifetimeTicks, transform));
    }

    public static void drawWireframeAttached(Wireframe wf, int entityId, Vec3 offset, boolean interpolate, int colorArgb, float thickness, boolean thicknessIsPixels, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new WireframeCommand(wf, null, new Attachment(entityId, offset, null, interpolate), colorArgb, thickness, thicknessIsPixels, doubleSided, lifetimeTicks, transform));
    }

    // -------- NEW: Plane rectangle (center, normal) ----------
    public static void drawPlaneRectWorld(Vec3 center, Vec3 normal, float width, float height, int colorArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PlaneRectCommand(center, normal, width, height, colorArgb, doubleSided, lifetimeTicks, null, transform));
    }

    public static void drawPlaneRectAttached(int entityId, Vec3 centerOffset, Vec3 normal, float width, float height, int colorArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new PlaneRectCommand(null, normal, width, height, colorArgb, doubleSided, lifetimeTicks, new Attachment(entityId, centerOffset, null, interpolate), transform));
    }

    // -------- NEW: Sphere ----------
    public static void drawSphereWorld(Vec3 center, float radius, int colorArgb, int latSegments, int lonSegments, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new SphereCommand(center, radius, colorArgb, latSegments, lonSegments, doubleSided, lifetimeTicks, null, transform));
    }

    public static void drawSphereAttached(int entityId, Vec3 offset, float radius, int colorArgb, int latSegments, int lonSegments, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new SphereCommand(null, radius, colorArgb, latSegments, lonSegments, doubleSided, lifetimeTicks, new Attachment(entityId, offset, null, interpolate), transform));
    }

    // -------- NEW: Box (AABB) ----------
    public static void drawBoxWorld(Vec3 min, Vec3 max, int colorArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new BoxCommand(min, max, colorArgb, doubleSided, lifetimeTicks, null, transform));
    }

    public static void drawBoxAttached(int entityId, Vec3 minOffset, Vec3 maxOffset, int colorArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new BoxCommand(null, null, colorArgb, doubleSided, lifetimeTicks, new Attachment(entityId, minOffset, maxOffset, interpolate), transform));
    }

    // -------- NEW: Cylinder (center->axis) ----------
    public static void drawCylinderWorld(Vec3 baseCenter, Vec3 axisDirection, float radius, float height, int radialSegments, int heightSegments, int colorArgb, boolean doubleSided, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new CylinderCommand(baseCenter, axisDirection, radius, height, radialSegments, heightSegments, colorArgb, doubleSided, lifetimeTicks, null, transform));
    }

    public static void drawCylinderAttached(int entityId, Vec3 baseOffset, Vec3 axisDirection, float radius, float height, int radialSegments, int heightSegments, int colorArgb, boolean doubleSided, boolean interpolate, int lifetimeTicks, Transform transform) {
        COMMANDS.add(new CylinderCommand(null, axisDirection, radius, height, radialSegments, heightSegments, colorArgb, doubleSided, lifetimeTicks, new Attachment(entityId, baseOffset, null, interpolate), transform));
    }

    // Management
    public static void clearAll() { COMMANDS.clear(); }
    public static void removeExpired() { COMMANDS.removeIf(RenderCommand::isExpired); }

    /* ---------------- Rendering pipeline ---------------- */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Default to AFTER_PARTICLES (reliable for world-space geometry)
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        float partialTick = event.getPartialTick();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.getBuilder();
        Matrix4f matrix = event.getPoseStack().last().pose();

        // 1) Color-only primitives
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (RenderCommand cmd : COMMANDS) {
            if (cmd.isExpired()) continue;
            if (cmd instanceof TexturedQuadCommand) continue; // textured later
            cmd.render(buffer, matrix, camPos, partialTick, mc);
            cmd.tick();
        }
        tess.end();

        // 2) Textured primitives: group by texture
        Map<ResourceLocation, List<TexturedQuadCommand>> grouped = COMMANDS.stream()
                .filter(c -> !c.isExpired() && c instanceof TexturedQuadCommand)
                .map(c -> (TexturedQuadCommand)c)
                .collect(Collectors.groupingBy(tq -> tq.texture));

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (Map.Entry<ResourceLocation, List<TexturedQuadCommand>> e : grouped.entrySet()) {
            ResourceLocation tex = e.getKey();
            List<TexturedQuadCommand> list = e.getValue();
            RenderSystem.setShaderTexture(0, tex);
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (TexturedQuadCommand tq : list) {
                if (tq.isExpired()) continue;
                tq.render(buffer, matrix, camPos, partialTick, mc);
                tq.tick();
            }
            tess.end();
        }

        // cleanup expired
        COMMANDS.removeIf(RenderCommand::isExpired);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /* ---------------- Command classes ---------------- */
    private static abstract class RenderCommand {
        protected final int lifetime; // -1 = persistent
        protected int age = 0;
        protected final Transform transform;

        RenderCommand(int lifetime, Transform transform) { this.lifetime = lifetime; this.transform = transform == null ? Transform.IDENTITY : transform; }
        public boolean isExpired() { return lifetime >= 0 && age >= lifetime; }
        public void tick() { if (lifetime >= 0) age++; }

        abstract void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc);
    }

    private static class Attachment {
        final int entityId;
        final Vec3 offsetA; // primary offset
        final Vec3 offsetB; // secondary offset (if needed)
        final boolean interpolate;
        Attachment(int entityId, Vec3 offsetA, Vec3 offsetB, boolean interpolate) { this.entityId = entityId; this.offsetA = offsetA == null ? Vec3.ZERO : offsetA; this.offsetB = offsetB == null ? Vec3.ZERO : offsetB; this.interpolate = interpolate; }
    }

    private static class PlaneAttachment {
        final int entityId;
        final Vec3 a, b, c;
        final boolean interpolate;
        PlaneAttachment(int entityId, Vec3 a, Vec3 b, Vec3 c, boolean interpolate) { this.entityId = entityId; this.a = a == null ? Vec3.ZERO : a; this.b = b == null ? Vec3.ZERO : b; this.c = c == null ? Vec3.ZERO : c; this.interpolate = interpolate; }
    }

    // Polygon attachment for filled polygons attached to entities
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

    /* -------- Plane (triangle) -------- */
    private static class PlaneCommand extends RenderCommand {
        private final Vec3 aOrig, bOrig, cOrig; // may be null when attached
        private final int[] colors;
        private final boolean doubleSided;
        private final Attachment attachment; // optional
        private final PlaneAttachment planeAttachment; // optional

        // single constructor taking both attachment variants (avoid null-ambiguity)
        PlaneCommand(Vec3 a, Vec3 b, Vec3 c, int[] colors, boolean doubleSided, int lifetime, Attachment attachment, PlaneAttachment planeAttachment, Transform transform) {
            super(lifetime, transform);
            this.aOrig = a; this.bOrig = b; this.cOrig = c; this.colors = colors; this.doubleSided = doubleSided; this.attachment = attachment; this.planeAttachment = planeAttachment;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 a = aOrig, b = bOrig, c = cOrig;

            if (planeAttachment != null) {
                Entity e = mc.level.getEntity(planeAttachment.entityId);
                if (e == null) return;
                Vec3 base;
                if (planeAttachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    base = new Vec3(ix, iy, iz);
                } else {
                    base = e.position();
                }
                a = base.add(planeAttachment.a);
                b = base.add(planeAttachment.b);
                c = base.add(planeAttachment.c);
            } else if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return;
                Vec3 base;
                if (attachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    base = new Vec3(ix, iy, iz);
                } else {
                    base = e.position();
                }
                a = base.add(attachment.offsetA);
                b = base.add(attachment.offsetB);
                c = base.add(attachment.offsetB); // fallback
            }

            if (a == null || b == null || c == null) return;

            a = applyTransform(a, transform);
            b = applyTransform(b, transform);
            c = applyTransform(c, transform);

            // camera relative
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

    /* --------- Polygon (filled triangle-fan) --------- */
    private static class PolygonCommand extends RenderCommand {
        // If worldPoints != null -> treat those as absolute world coordinates.
        // Otherwise polygonAttachment is used (points local to entity).
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
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            List<Vec3> pts = new ArrayList<>();
            Vec3 base = null;

            if (polygonAttachment != null) {
                Entity e = mc.level.getEntity(polygonAttachment.entityId);
                if (e == null) return;
                if (polygonAttachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    base = new Vec3(ix, iy, iz);
                } else {
                    base = e.position();
                }
                // local offsets are relative to entity base
                for (Vec3 local : polygonAttachment.localPoints) {
                    pts.add(base.add(local));
                }
            } else if (worldPoints != null) {
                for (Vec3 w : worldPoints) pts.add(w);
            } else {
                return;
            }

            if (pts.size() < 3) return;

            // Compute centroid (center) for fan
            double cx = 0, cy = 0, cz = 0;
            for (Vec3 p : pts) { cx += p.x; cy += p.y; cz += p.z; }
            cx /= pts.size(); cy /= pts.size(); cz /= pts.size();
            Vec3 center = new Vec3(cx, cy, cz);

            // Apply transform to each point and center
            List<Vec3> transformed = new ArrayList<>(pts.size());
            for (Vec3 p : pts) transformed.add(applyTransform(p, transform));
            Vec3 tCenter = applyTransform(center, transform);

            // Camera-relative
            List<Vec3> rel = new ArrayList<>(transformed.size());
            for (Vec3 p : transformed) rel.add(p.subtract(camPos));
            Vec3 relCenter = tCenter.subtract(camPos);

            float[] rgba = unpackColor(color);

            // Emit triangle fan (center, i, i+1)
            int n = rel.size();
            for (int i = 0; i < n; i++) {
                Vec3 a = relCenter;
                Vec3 b = rel.get(i);
                Vec3 c = rel.get((i + 1) % n);

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

    /* -------- Line & Polyline -------- */
    private static class LineCommand extends RenderCommand {
        private final Vec3 aOrig, bOrig;
        private final int color;
        private final float thicknessValue;
        private final boolean thicknessIsPixels;
        private final Attachment attachment;

        LineCommand(Vec3 a, Vec3 b, int color, float thicknessValue, boolean thicknessIsPixels, int lifetime, Attachment attachment, Transform transform) {
            super(lifetime, transform);
            this.aOrig = a; this.bOrig = b; this.color = color; this.thicknessValue = thicknessValue; this.thicknessIsPixels = thicknessIsPixels; this.attachment = attachment;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 a = aOrig, b = bOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return;
                Vec3 base;
                if (attachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    base = new Vec3(ix, iy, iz);
                } else {
                    base = e.position();
                }
                a = base.add(attachment.offsetA);
                b = base.add(attachment.offsetB);
            }

            if (a == null || b == null) return;

            a = applyTransform(a, transform);
            b = applyTransform(b, transform);

            Vec3 ra = a.subtract(camPos);
            Vec3 rb = b.subtract(camPos);
            Vec3 dir = rb.subtract(ra);
            double len = dir.length();
            if (len <= 1e-6) return;

            float effThickness;
            if (thicknessIsPixels && GLOBAL_ENABLE_SCREEN_SPACE_THICKNESS) effThickness = pixelThicknessToWorld(thicknessValue, (float) ra.length(), mc);
            else effThickness = thicknessValue;

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
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            if (points.size() < 2) return;
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 pa = applyTransform(points.get(i), transform);
                Vec3 pb = applyTransform(points.get(i + 1), transform);
                new LineCommand(pa, pb, color, thicknessValue, thicknessIsPixels, lifetime, null, Transform.IDENTITY).render(buffer, poseMatrix, camPos, partialTick, mc);
            }
        }
    }

    /* -------- Wireframe (grouped lines) -------- */
    public static class Wireframe {
        // store lines in local/object-space relative to origin (Vec3)
        private final List<Segment> segments = new ArrayList<>();

        public void addLine(Vec3 aLocal, Vec3 bLocal) { segments.add(new Segment(aLocal, bLocal)); }

        public void clear() { segments.clear(); }

        private static class Segment { final Vec3 a, b; Segment(Vec3 a, Vec3 b) { this.a = a; this.b = b; } }
    }

    private static class WireframeCommand extends RenderCommand {
        private final Wireframe wf;
        private final Vec3 worldOrigin; // mutually exclusive with attachment
        private final Attachment attachment;
        private final int color;
        private final float thickness;
        private final boolean thicknessIsPixels;
        private final boolean doubleSided;

        WireframeCommand(Wireframe wf, Vec3 worldOrigin, Attachment attachment, int color, float thickness, boolean thicknessIsPixels, boolean doubleSided, int lifetime, Transform transform) {
            super(lifetime, transform);
            this.wf = wf; this.worldOrigin = worldOrigin; this.attachment = attachment; this.color = color; this.thickness = thickness; this.thicknessIsPixels = thicknessIsPixels; this.doubleSided = doubleSided;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 base = worldOrigin;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return;
                if (attachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    base = new Vec3(ix, iy, iz).add(attachment.offsetA);
                } else {
                    base = e.position().add(attachment.offsetA);
                }
            }

            if (base == null) return;

            // For each segment in local space, transform by group's transform (scale/rotate/pivot/translation)
            float[] rgba = unpackColor(this.color);

            for (Wireframe.Segment s : wf.segments) {
                // transform local endpoints
                Vec3 aWorld = base.add(applyLocalTransform(s.a, transform));
                Vec3 bWorld = base.add(applyLocalTransform(s.b, transform));

                // convert to camera-relative
                Vec3 ra = aWorld.subtract(camPos);
                Vec3 rb = bWorld.subtract(camPos);

                // compute quad for thick line
                Vec3 dir = rb.subtract(ra);
                if (dir.length() <= 1e-6) continue;

                float effThickness;
                if (this.thicknessIsPixels && GLOBAL_ENABLE_SCREEN_SPACE_THICKNESS) effThickness = pixelThicknessToWorld(this.thickness, (float) ra.length(), mc);
                else effThickness = this.thickness;

                Vec3 viewDir = camPos.subtract(aWorld);
                Vec3 perp = dir.cross(viewDir);
                if (perp.length() <= 1e-6) {
                    perp = dir.cross(new Vec3(0, 1, 0));
                    if (perp.length() <= 1e-6) continue;
                }
                perp = perp.normalize().scale(effThickness * 0.5);

                Vec3 p1 = ra.add(perp);
                Vec3 p2 = ra.subtract(perp);
                Vec3 p3 = rb.subtract(perp);
                Vec3 p4 = rb.add(perp);

                putVertex(buffer, poseMatrix, p1, rgba);
                putVertex(buffer, poseMatrix, p2, rgba);
                putVertex(buffer, poseMatrix, p3, rgba);

                putVertex(buffer, poseMatrix, p1, rgba);
                putVertex(buffer, poseMatrix, p3, rgba);
                putVertex(buffer, poseMatrix, p4, rgba);
            }
        }

        // transform a local-space point by the group's transform (scale, rotate around pivot in local-space, then translate)
        private Vec3 applyLocalTransform(Vec3 local, Transform t) {
            if (t == null || t == Transform.IDENTITY) return local;
            // pivot is specified in local coords
            double x = local.x - t.pivot.x; double y = local.y - t.pivot.y; double z = local.z - t.pivot.z;
            // scale
            x *= t.scale; y *= t.scale; z *= t.scale;
            // rotate
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
            // translate back from pivot and apply translation
            double finalX = x + t.pivot.x + t.translation.x;
            double finalY = y + t.pivot.y + t.translation.y;
            double finalZ = z + t.pivot.z + t.translation.z;
            return new Vec3(finalX, finalY, finalZ);
        }
    }

    /* -------- Textured quad / sprite -------- */
    private static class TexturedQuadCommand extends RenderCommand {
        final ResourceLocation texture;
        final Vec3 center; // world center if not attached
        final float width, height;
        final int color; // tint ARGB
        final boolean faceCamera;
        final Attachment attachment; // optional

        TexturedQuadCommand(Vec3 center, float width, float height, ResourceLocation texture, int color, boolean faceCamera, int lifetime, Attachment attachment, Transform transform) {
            super(lifetime, transform);
            this.center = center; this.width = width; this.height = height; this.texture = texture; this.color = color; this.faceCamera = faceCamera; this.attachment = attachment;
        }

        @Override
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 cen = center;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return;
                if (attachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    cen = new Vec3(ix, iy, iz).add(attachment.offsetA);
                } else {
                    cen = e.position().add(attachment.offsetA);
                }
            }
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

            // UVs: (0,1),(1,1),(1,0),(0,0)
            putTexturedVertex(buffer, poseMatrix, p1, 0f, 1f, rgba);
            putTexturedVertex(buffer, poseMatrix, p2, 1f, 1f, rgba);
            putTexturedVertex(buffer, poseMatrix, p3, 1f, 0f, rgba);

            putTexturedVertex(buffer, poseMatrix, p1, 0f, 1f, rgba);
            putTexturedVertex(buffer, poseMatrix, p3, 1f, 0f, rgba);
            putTexturedVertex(buffer, poseMatrix, p4, 0f, 0f, rgba);
        }
    }

    /* ---------------- NEW: Plane rectangle (center, normal) command ---------------- */
    private static class PlaneRectCommand extends RenderCommand {
        final Vec3 centerOrig; // world if not attached
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
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 center = centerOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return;
                Vec3 base;
                if (attachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    base = new Vec3(ix, iy, iz);
                } else {
                    base = e.position();
                }
                center = base.add(attachment.offsetA);
            }
            if (center == null) return;

            // create orthonormal basis from normal
            Vec3 n = this.normal.normalize();
            Vec3 upCandidate = new Vec3(0, 1, 0);
            if (Math.abs(n.dot(upCandidate)) > 0.999) upCandidate = new Vec3(1, 0, 0);
            Vec3 right = upCandidate.cross(n).normalize().scale(width * 0.5);
            Vec3 up = n.cross(right).normalize().scale(height * 0.5);

            // rectangle corners in world
            Vec3 p1 = center.add(right).add(up); // top-right
            Vec3 p2 = center.subtract(right).add(up); // top-left
            Vec3 p3 = center.subtract(right).subtract(up); // bottom-left
            Vec3 p4 = center.add(right).subtract(up); // bottom-right

            // apply group transform
            p1 = applyTransform(p1, transform).subtract(camPos);
            p2 = applyTransform(p2, transform).subtract(camPos);
            p3 = applyTransform(p3, transform).subtract(camPos);
            p4 = applyTransform(p4, transform).subtract(camPos);

            float[] rgba = unpackColor(color);

            // two triangles
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

    /* ---------------- NEW: Sphere ---------------- */
    private static class SphereCommand extends RenderCommand {
        final Vec3 centerOrig; // may be null when attached
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
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 center = centerOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return;
                Vec3 base;
                if (attachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    base = new Vec3(ix, iy, iz);
                } else {
                    base = e.position();
                }
                center = base.add(attachment.offsetA);
            }
            if (center == null) return;

            float[] rgba = unpackColor(color);

            // generate lat/lon rings, emit quads as two triangles
            for (int lat = 0; lat < latSegments; lat++) {
                double theta1 = Math.PI * (double) lat / latSegments;
                double theta2 = Math.PI * (double) (lat + 1) / latSegments;
                double y1 = Math.cos(theta1), r1 = Math.sin(theta1);
                double y2 = Math.cos(theta2), r2 = Math.sin(theta2);

                for (int lon = 0; lon < lonSegments; lon++) {
                    double phi1 = 2.0 * Math.PI * (double) lon / lonSegments;
                    double phi2 = 2.0 * Math.PI * (double) (lon + 1) / lonSegments;

                    Vec3 v11 = new Vec3(center.x + radius * r1 * Math.cos(phi1), center.y + radius * y1, center.z + radius * r1 * Math.sin(phi1));
                    Vec3 v12 = new Vec3(center.x + radius * r1 * Math.cos(phi2), center.y + radius * y1, center.z + radius * r1 * Math.sin(phi2));
                    Vec3 v21 = new Vec3(center.x + radius * r2 * Math.cos(phi1), center.y + radius * y2, center.z + radius * r2 * Math.sin(phi1));
                    Vec3 v22 = new Vec3(center.x + radius * r2 * Math.cos(phi2), center.y + radius * y2, center.z + radius * r2 * Math.sin(phi2));

                    // apply transform then camera-rel
                    Vec3 p11 = applyTransform(v11, transform).subtract(camPos);
                    Vec3 p12 = applyTransform(v12, transform).subtract(camPos);
                    Vec3 p21 = applyTransform(v21, transform).subtract(camPos);
                    Vec3 p22 = applyTransform(v22, transform).subtract(camPos);

                    // tri1 p11, p21, p22
                    putVertexWithRGBA(buffer, poseMatrix, p11, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p21, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p22, rgba);

                    // tri2 p11, p22, p12
                    putVertexWithRGBA(buffer, poseMatrix, p11, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p22, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p12, rgba);

                    if (doubleSided) {
                        // reverse-wound triangles for back-face
                        putVertexWithRGBA(buffer, poseMatrix, p11, rgba);
                        putVertexWithRGBA(buffer, poseMatrix, p22, rgba);
                        putVertexWithRGBA(buffer, poseMatrix, p21, rgba);

                        putVertexWithRGBA(buffer, poseMatrix, p11, rgba);
                        putVertexWithRGBA(buffer, poseMatrix, p12, rgba);
                        putVertexWithRGBA(buffer, poseMatrix, p22, rgba);
                    }
                }
            }
        }
    }

    /* ---------------- NEW: Box (AABB) ---------------- */
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
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 min = minOrig;
            Vec3 max = maxOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return;
                Vec3 base;
                if (attachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    base = new Vec3(ix, iy, iz);
                } else {
                    base = e.position();
                }
                min = base.add(attachment.offsetA);
                max = base.add(attachment.offsetB);
            }
            if (min == null || max == null) return;

            // corners
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

            // faces: each face as two triangles (indices into vs)
            int[][] faces = {
                    {0,1,2,3}, // -Z
                    {5,4,7,6}, // +Z
                    {1,5,6,2}, // +X
                    {4,0,3,7}, // -X
                    {3,2,6,7}, // +Y
                    {4,5,1,0}  // -Y
            };

            for (int[] f : faces) {
                Vec3 p0 = vs[f[0]], p1 = vs[f[1]], p2 = vs[f[2]], p3 = vs[f[3]];
                // tri1 p0,p1,p2
                putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                // tri2 p0,p2,p3
                putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p3, rgba);

                if (doubleSided) {
                    // reverse winding
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

    /* ---------------- NEW: Cylinder ---------------- */
    private static class CylinderCommand extends RenderCommand {
        final Vec3 baseOrig; // may be null when attached
        final Vec3 axisDirection; // normalized axis unit
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
        void render(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 camPos, float partialTick, Minecraft mc) {
            Vec3 base = baseOrig;
            if (attachment != null) {
                Entity e = mc.level.getEntity(attachment.entityId);
                if (e == null) return;
                Vec3 b;
                if (attachment.interpolate && GLOBAL_ENABLE_ENTITY_INTERPOLATION) {
                    double ix = Mth.lerp(partialTick, e.xOld, e.getX());
                    double iy = Mth.lerp(partialTick, e.yOld, e.getY());
                    double iz = Mth.lerp(partialTick, e.zOld, e.getZ());
                    b = new Vec3(ix, iy, iz);
                } else {
                    b = e.position();
                }
                base = b.add(attachment.offsetA);
            }
            if (base == null) return;

            // create orthonormal basis (u,v) perpendicular to axisDirection
            Vec3 axis = this.axisDirection.normalize();
            Vec3 upCandidate = new Vec3(0, 1, 0);
            if (Math.abs(axis.dot(upCandidate)) > 0.999) upCandidate = new Vec3(1, 0, 0);
            Vec3 u = upCandidate.cross(axis).normalize(); // radial x-axis
            Vec3 v = axis.cross(u).normalize(); // radial y-axis

            float[] rgba = unpackColor(color);

            // for each height ring
            for (int h = 0; h < heightSegments; h++) {
                double t0 = (double) h / heightSegments;
                double t1 = (double) (h + 1) / heightSegments;
                Vec3 ring0center = base.add(axis.scale(t0 * height));
                Vec3 ring1center = base.add(axis.scale(t1 * height));

                for (int s = 0; s < radialSegments; s++) {
                    double phi0 = 2.0 * Math.PI * s / radialSegments;
                    double phi1 = 2.0 * Math.PI * (s + 1) / radialSegments;

                    Vec3 r0a = ring0center.add(u.scale(radius * Math.cos(phi0))).add(v.scale(radius * Math.sin(phi0)));
                    Vec3 r0b = ring0center.add(u.scale(radius * Math.cos(phi1))).add(v.scale(radius * Math.sin(phi1)));
                    Vec3 r1a = ring1center.add(u.scale(radius * Math.cos(phi0))).add(v.scale(radius * Math.sin(phi0)));
                    Vec3 r1b = ring1center.add(u.scale(radius * Math.cos(phi1))).add(v.scale(radius * Math.sin(phi1)));

                    Vec3 p0 = applyTransform(r0a, transform).subtract(camPos);
                    Vec3 p1 = applyTransform(r0b, transform).subtract(camPos);
                    Vec3 p2 = applyTransform(r1b, transform).subtract(camPos);
                    Vec3 p3 = applyTransform(r1a, transform).subtract(camPos);

                    // two tris per quad
                    putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p3, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p2, rgba);

                    putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p1, rgba);

                    if (doubleSided) {
                        putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                        putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                        putVertexWithRGBA(buffer, poseMatrix, p3, rgba);

                        putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                        putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                        putVertexWithRGBA(buffer, poseMatrix, p2, rgba);
                    }
                }
            }

            // optional caps (flat discs)
            // bottom cap
            Vec3 center0 = applyTransform(base, transform).subtract(camPos);
            Vec3 centerTop = applyTransform(base.add(axis.scale(height)), transform).subtract(camPos);
            // bottom
            for (int s = 0; s < radialSegments; s++) {
                double phi0 = 2.0 * Math.PI * s / radialSegments;
                double phi1 = 2.0 * Math.PI * (s + 1) / radialSegments;
                Vec3 p0 = applyTransform(base.add(u.scale(radius * Math.cos(phi0))).add(v.scale(radius * Math.sin(phi0))), transform).subtract(camPos);
                Vec3 p1 = applyTransform(base.add(u.scale(radius * Math.cos(phi1))).add(v.scale(radius * Math.sin(phi1))), transform).subtract(camPos);
                // triangle center0,p1,p0 (winding so normal points outward)
                putVertexWithRGBA(buffer, poseMatrix, center0, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                if (doubleSided) {
                    putVertexWithRGBA(buffer, poseMatrix, center0, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                }
            }
            // top
            for (int s = 0; s < radialSegments; s++) {
                double phi0 = 2.0 * Math.PI * s / radialSegments;
                double phi1 = 2.0 * Math.PI * (s + 1) / radialSegments;
                Vec3 p0 = applyTransform(base.add(axis.scale(height)).add(u.scale(radius * Math.cos(phi0))).add(v.scale(radius * Math.sin(phi0))), transform).subtract(camPos);
                Vec3 p1 = applyTransform(base.add(axis.scale(height)).add(u.scale(radius * Math.cos(phi1))).add(v.scale(radius * Math.sin(phi1))), transform).subtract(camPos);
                putVertexWithRGBA(buffer, poseMatrix, centerTop, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                if (doubleSided) {
                    putVertexWithRGBA(buffer, poseMatrix, centerTop, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p1, rgba);
                    putVertexWithRGBA(buffer, poseMatrix, p0, rgba);
                }
            }
        }
    }

    /* ---------------- Utilities ---------------- */
    private static float pixelThicknessToWorld(float pixelThickness, float distance, Minecraft mc) {
        double fovDegrees = mc.options.fov().get();
        int screenH = mc.getWindow().getGuiScaledHeight();
        double worldPerPixel = 2.0 * distance * Math.tan(Math.toRadians(fovDegrees * 0.5)) / Math.max(1, screenH);
        return (float) (pixelThickness * worldPerPixel);
    }

    private static void putVertex(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float[] rgba) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(rgba[0], rgba[1], rgba[2], rgba[3])
                .endVertex();
    }

    private static void putVertexWithRGBA(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float[] rgba) { putVertex(buffer, matrix, p, rgba); }

    private static void putTexturedVertex(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float u, float v, float[] rgba) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .uv(u, v)
                .color(rgba[0], rgba[1], rgba[2], rgba[3])
                .endVertex();
    }

    private static float[] unpackColor(int color) {
        // returns [r,g,b,a] in 0..1 floats (matches DefaultVertexFormat.POSITION_COLOR)
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    public static class Transform {
        public final Vec3 translation;
        public final float yawDeg, pitchDeg, rollDeg;
        public final float scale;
        public final Vec3 pivot;
        public final Quaternionf rotationQuaternion;

        public static final Transform IDENTITY = new Transform(Vec3.ZERO, 0f,0f,0f,1f, Vec3.ZERO, null);

        public Transform(Vec3 translation, float yawDeg, float pitchDeg, float rollDeg, float scale, Vec3 pivot, Quaternionf quat) {
            this.translation = translation == null ? Vec3.ZERO : translation;
            this.yawDeg = yawDeg; this.pitchDeg = pitchDeg; this.rollDeg = rollDeg; this.scale = scale; this.pivot = pivot == null ? Vec3.ZERO : pivot; this.rotationQuaternion = quat;
        }

        public static Transform fromEuler(Vec3 translation, float yawDeg, float pitchDeg, float rollDeg, float scale, Vec3 pivot) {
            return new Transform(translation, yawDeg, pitchDeg, rollDeg, scale, pivot, null);
        }

        public static Transform fromQuaternion(Vec3 translation, Quaternionf quat, float scale, Vec3 pivot) {
            return new Transform(translation, 0f, 0f, 0f, scale, pivot, quat);
        }
    }

    private static Vec3 applyTransform(Vec3 p, Transform t) {
        if (t == null || t == Transform.IDENTITY) return p;
        double x = p.x - t.pivot.x; double y = p.y - t.pivot.y; double z = p.z - t.pivot.z;
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
