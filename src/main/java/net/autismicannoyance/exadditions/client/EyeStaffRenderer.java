package net.autismicannoyance.exadditions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.autismicannoyance.exadditions.ExAdditions;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heavily optimized EyeStaffRenderer with batched rendering and reduced geometry complexity
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class EyeStaffRenderer {
    private static final Map<Integer, EyeEffectData> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // Reduced complexity settings for performance
    private static final int MIN_EYES = 2;
    private static final int MAX_EYES = 24;
    private static final double MIN_DIST = 3.0;
    private static final double MAX_DIST = 6.0;

    private static final int COLOR_OUTLINE = 0xFFFF0000;
    private static final int COLOR_SCLERA  = 0xFF111111;
    private static final int COLOR_PUPIL   = 0xFFFFFFFF;
    private static final int COLOR_IRIS    = 0xFF000000;
    private static final int COLOR_LASER   = 0xFFFF5555;

    // Dramatically reduced geometry complexity
    private static final int SEGMENTS = 32; // Reduced from 160
    private static final double OUTLINE_THICKNESS_FRACT = 0.075;

    private static final int CLIENT_TTL = 40;

    // Pre-calculated geometry cache
    private static List<Vec3> CACHED_CIRCLE_32;
    private static List<Vec3> CACHED_CIRCLE_16;
    private static List<Vec3> CACHED_CIRCLE_8;

    // Batch rendering data
    private static final List<EyeRenderData> RENDER_BATCH = new ArrayList<>();

    static {
        // Pre-calculate geometry to avoid repeated calculations
        CACHED_CIRCLE_32 = createCircleLocal(1.0, 32);
        CACHED_CIRCLE_16 = createCircleLocal(1.0, 16);
        CACHED_CIRCLE_8 = createCircleLocal(1.0, 8);
    }

    public static void handlePacket(net.autismicannoyance.exadditions.network.EyeRenderPacket msg) {
        int ownerId = msg.entityId;
        List<net.autismicannoyance.exadditions.network.EyeRenderPacket.EyeEntry> entries =
                msg.eyes == null ? Collections.emptyList() : msg.eyes;

        EyeEffectData data = new EyeEffectData(ownerId,
                Math.max(MIN_EYES, Math.min(MAX_EYES, entries.size())), CLIENT_TTL);
        data.eyes.clear();

        for (net.autismicannoyance.exadditions.network.EyeRenderPacket.EyeEntry e : entries) {
            EyeInstance inst = new EyeInstance();
            inst.offset = e.offset == null ? Vec3.ZERO : e.offset;
            inst.initialized = true;
            inst.width = 0.6f + RAND.nextFloat() * 0.9f;
            inst.height = inst.width * 0.45f;

            inst.pupilOffset = Vec3.ZERO;
            inst.pupilTargetOffset = Vec3.ZERO;
            inst.pupilJitterTimer = 8 + RAND.nextInt(30);
            inst.pupilCenterMaxOffset = Math.max(0.01, inst.width * 0.03);
            inst.pupilMaxOffset = Math.max(0.03, Math.min(inst.width * 0.18, inst.height * 0.18));
            inst.irisOffset = Vec3.ZERO;
            inst.irisTarget = Vec3.ZERO;
            inst.irisTimer = 4 + RAND.nextInt(22);

            inst.firing = e.firing;
            inst.laserEnd = e.laserEnd;
            inst.lookDirection = e.lookDirection;

            // Handle server blinking if available
            try {
                java.lang.reflect.Field blinkingField = e.getClass().getField("isBlinking");
                java.lang.reflect.Field phaseField = e.getClass().getField("blinkPhase");
                inst.serverBlinking = blinkingField.getBoolean(e);
                inst.serverBlinkPhase = phaseField.getFloat(e);
            } catch (Exception ex) {
                inst.serverBlinking = false;
                inst.serverBlinkPhase = 0.0f;
            }

            data.eyes.add(inst);
        }

        EFFECTS.put(ownerId, data);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Early exit if no effects
        if (EFFECTS.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float partial = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f poseMatrix = event.getPoseStack().last().pose();

        // Use batch rendering
        RENDER_BATCH.clear();

        Iterator<Map.Entry<Integer, EyeEffectData>> it = EFFECTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, EyeEffectData> ent = it.next();
            EyeEffectData data = ent.getValue();

            if (data.lifetime >= 0 && data.age++ >= data.lifetime) {
                it.remove();
                continue;
            }

            Entity maybe = mc.level.getEntity(data.entityId);
            if (!(maybe instanceof LivingEntity target)) {
                it.remove();
                continue;
            }

            double tx = Mth.lerp(partial, target.xOld, target.getX());
            double ty = Mth.lerp(partial, target.yOld, target.getY()) + target.getBbHeight() * 0.5;
            double tz = Mth.lerp(partial, target.zOld, target.getZ());
            Vec3 targetPos = new Vec3(tx, ty, tz);

            data.ensureEyesInitialized(target, mc);

            for (EyeInstance inst : data.eyes) {
                Vec3 baseWorld = targetPos.add(inst.offset);

                // Distance culling - don't render eyes that are too far
                double distanceToCamera = baseWorld.distanceToSqr(cameraPos);
                if (distanceToCamera > 64 * 64) continue; // Skip distant eyes

                if (isInsideBlock(mc, baseWorld)) {
                    inst.repositionTimer--;
                    if (inst.repositionTimer <= 0) {
                        inst.offset = pickOffsetAroundTarget(target, mc);
                        inst.repositionTimer = 40;
                    }
                    continue;
                }

                // Calculate blink fraction
                float blinkFraction;
                if (inst.serverBlinking && inst.serverBlinkPhase > 0.0f) {
                    blinkFraction = getSmoothedBlinkFraction(inst.serverBlinkPhase);
                } else {
                    updateAnimatedBlinking(inst);
                    blinkFraction = getBlinkFraction(inst);
                }

                // Skip fully blinked eyes to save rendering
                if (blinkFraction > 0.95f) continue;

                // Compute orientation quaternion
                Quaternionf quat = calculateEyeOrientation(inst, targetPos, baseWorld);

                // Enhanced iris/pupil animation
                animateIrisWithTracking(inst, quat, cameraPos);

                // Check if eye is facing camera for outline visibility
                boolean facingCamera = isEyeFacingCamera(baseWorld, quat, cameraPos);

                // Add to batch instead of immediate rendering
                RENDER_BATCH.add(new EyeRenderData(baseWorld, inst, quat, blinkFraction, facingCamera));
            }
        }

        // Batch render all eyes
        if (!RENDER_BATCH.isEmpty()) {
            renderEyesBatched(poseMatrix, cameraPos);
        }

        // Render laser beams separately (these are less common)
        renderLaserBeams();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderEyesBatched(Matrix4f poseMatrix, Vec3 cameraPos) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // Start batch
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (EyeRenderData renderData : RENDER_BATCH) {
            renderEyeOptimized(buffer, poseMatrix, renderData, cameraPos);
        }

        // End batch
        tesselator.end();
    }

    private static void renderEyeOptimized(BufferBuilder buffer, Matrix4f poseMatrix, EyeRenderData renderData, Vec3 cameraPos) {
        Vec3 baseWorld = renderData.baseWorld;
        EyeInstance inst = renderData.inst;
        Quaternionf quat = renderData.quat;
        float blinkFraction = renderData.blinkFraction;
        boolean facingCamera = renderData.facingCamera;

        float width = inst.width;
        float height = inst.height * (1f - blinkFraction * 0.95f);

        // Use cached geometry and scale it
        List<Vec3> local = scaleCircle(CACHED_CIRCLE_32, width, height);
        Vec3 centroidLocal = Vec3.ZERO; // For circles, centroid is origin

        Vec3 worldCentroid = baseWorld.add(rotateLocalByQuat(centroidLocal, quat));

        // Simplified depth biasing
        Vec3 forward = rotateLocalByQuat(new Vec3(0, 0, 1), quat);
        double baseBias = 0.0009 * (width / 0.8);
        Vec3 biasOutline = forward.scale(-baseBias * 1.8);
        Vec3 biasSclera = Vec3.ZERO;
        Vec3 biasPupil = forward.scale(baseBias * 1.05);
        Vec3 biasIris = forward.scale(baseBias * 1.9);

        // Only render outline if facing camera and not too distant
        if (facingCamera && baseWorld.distanceToSqr(cameraPos) < 32 * 32) {
            renderOutline(buffer, poseMatrix, local, baseWorld, quat, width, biasOutline, biasSclera, cameraPos);
        }

        // Render sclera (main eye body) with reduced complexity
        renderSclera(buffer, poseMatrix, local, worldCentroid, baseWorld, quat, biasSclera, cameraPos);

        // Render pupil with reduced complexity
        renderPupil(buffer, poseMatrix, inst, baseWorld, quat, blinkFraction, biasPupil, cameraPos);

        // Render iris with reduced complexity
        renderIris(buffer, poseMatrix, inst, baseWorld, quat, blinkFraction, biasIris, cameraPos);
    }

    private static void renderOutline(BufferBuilder buffer, Matrix4f poseMatrix, List<Vec3> local,
                                      Vec3 baseWorld, Quaternionf quat, float width, Vec3 biasOutline, Vec3 biasSclera, Vec3 cameraPos) {
        double outlineAmount = width * OUTLINE_THICKNESS_FRACT;
        List<Vec3> outerLocal = offsetPolygonSimplified(local, outlineAmount);

        int[] outlineCol = new int[]{COLOR_OUTLINE, COLOR_OUTLINE, COLOR_OUTLINE};

        // Reduce outline complexity by using fewer segments
        int step = Math.max(1, local.size() / 16); // Use only every nth point
        for (int i = 0; i < local.size(); i += step) {
            int nextI = Math.min(i + step, local.size() - 1);
            if (nextI == i) break;

            Vec3 inA = local.get(i);
            Vec3 inB = local.get(nextI);
            Vec3 outA = outerLocal.get(i);
            Vec3 outB = outerLocal.get(nextI);

            Vec3 wOutA = baseWorld.add(rotateLocalByQuat(outA, quat)).add(biasOutline).subtract(cameraPos);
            Vec3 wOutB = baseWorld.add(rotateLocalByQuat(outB, quat)).add(biasOutline).subtract(cameraPos);
            Vec3 wInA = baseWorld.add(rotateLocalByQuat(inA, quat)).add(biasSclera).subtract(cameraPos);
            Vec3 wInB = baseWorld.add(rotateLocalByQuat(inB, quat)).add(biasSclera).subtract(cameraPos);

            addTriangle(buffer, poseMatrix, wOutA, wOutB, wInB, outlineCol);
            addTriangle(buffer, poseMatrix, wOutA, wInB, wInA, outlineCol);
        }
    }

    private static void renderSclera(BufferBuilder buffer, Matrix4f poseMatrix, List<Vec3> local,
                                     Vec3 worldCentroid, Vec3 baseWorld, Quaternionf quat, Vec3 biasSclera, Vec3 cameraPos) {
        int[] scleraCol = new int[]{COLOR_SCLERA, COLOR_SCLERA, COLOR_SCLERA};
        Vec3 relCentroid = worldCentroid.subtract(cameraPos);

        // Reduce sclera complexity
        int step = Math.max(1, local.size() / 16);
        for (int i = 0; i < local.size(); i += step) {
            int nextI = (i + step) % local.size();
            Vec3 a = local.get(i);
            Vec3 b = local.get(nextI);
            Vec3 wa = baseWorld.add(rotateLocalByQuat(a, quat)).add(biasSclera).subtract(cameraPos);
            Vec3 wb = baseWorld.add(rotateLocalByQuat(b, quat)).add(biasSclera).subtract(cameraPos);

            addTriangle(buffer, poseMatrix, relCentroid, wa, wb, scleraCol);
        }
    }

    private static void renderPupil(BufferBuilder buffer, Matrix4f poseMatrix, EyeInstance inst,
                                    Vec3 baseWorld, Quaternionf quat, float blinkFraction, Vec3 biasPupil, Vec3 cameraPos) {
        float pupilRadius = Math.min(inst.width, inst.height) * 0.32f * (1f - blinkFraction);

        // Use much simpler geometry for pupils
        List<Vec3> pupilLocal = scaleCircle(CACHED_CIRCLE_8, pupilRadius * 2, pupilRadius * 2);

        Vec3 pupilCenterLocal = new Vec3(
                inst.pupilOffset.x * (1f - blinkFraction),
                inst.pupilOffset.y * (1f - blinkFraction),
                0.0
        );

        Vec3 worldPupilCenter = baseWorld.add(rotateLocalByQuat(pupilCenterLocal, quat)).add(biasPupil).subtract(cameraPos);
        int[] pupilCol = new int[]{COLOR_PUPIL, COLOR_PUPIL, COLOR_PUPIL};

        for (int i = 0; i < pupilLocal.size(); i++) {
            Vec3 a = pupilLocal.get(i).add(pupilCenterLocal);
            Vec3 b = pupilLocal.get((i + 1) % pupilLocal.size()).add(pupilCenterLocal);
            Vec3 wa = baseWorld.add(rotateLocalByQuat(a, quat)).add(biasPupil).subtract(cameraPos);
            Vec3 wb = baseWorld.add(rotateLocalByQuat(b, quat)).add(biasPupil).subtract(cameraPos);

            addTriangle(buffer, poseMatrix, worldPupilCenter, wa, wb, pupilCol);
        }
    }

    private static void renderIris(BufferBuilder buffer, Matrix4f poseMatrix, EyeInstance inst,
                                   Vec3 baseWorld, Quaternionf quat, float blinkFraction, Vec3 biasIris, Vec3 cameraPos) {
        float pupilRadius = Math.min(inst.width, inst.height) * 0.32f * (1f - blinkFraction);
        float irisRadius = Math.max(0.02f, pupilRadius * 0.20f);

        // Use simplest geometry for iris
        List<Vec3> irisLocal = scaleCircle(CACHED_CIRCLE_8, irisRadius * 2, irisRadius * 2);

        Vec3 irisCenterLocal = new Vec3(
                (inst.pupilOffset.x + inst.irisOffset.x) * (1f - blinkFraction * 0.5f),
                (inst.pupilOffset.y + inst.irisOffset.y) * (1f - blinkFraction * 0.5f),
                0.0
        );

        Vec3 worldIrisCenter = baseWorld.add(rotateLocalByQuat(irisCenterLocal, quat)).add(biasIris).subtract(cameraPos);
        int[] irisCol = new int[]{COLOR_IRIS, COLOR_IRIS, COLOR_IRIS};

        for (int i = 0; i < irisLocal.size(); i++) {
            Vec3 a = irisLocal.get(i).add(irisCenterLocal);
            Vec3 b = irisLocal.get((i + 1) % irisLocal.size()).add(irisCenterLocal);
            Vec3 wa = baseWorld.add(rotateLocalByQuat(a, quat)).add(biasIris).subtract(cameraPos);
            Vec3 wb = baseWorld.add(rotateLocalByQuat(b, quat)).add(biasIris).subtract(cameraPos);

            addTriangle(buffer, poseMatrix, worldIrisCenter, wa, wb, irisCol);
        }
    }

    private static void renderLaserBeams() {
        // Render laser beams separately for firing eyes
        for (EyeRenderData renderData : RENDER_BATCH) {
            if (renderData.inst.firing && renderData.inst.laserEnd != null) {
                drawSimplifiedBeam(renderData.baseWorld, renderData.inst.laserEnd, 0.06f, COLOR_LASER);
            }
        }
    }

    private static void addTriangle(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 a, Vec3 b, Vec3 c, int[] colors) {
        float[] ca = unpackColor(colors[0]);
        float[] cb = colors.length > 1 ? unpackColor(colors[1]) : ca;
        float[] cc = colors.length > 2 ? unpackColor(colors[2]) : ca;

        buffer.vertex(poseMatrix, (float) a.x, (float) a.y, (float) a.z)
                .color(ca[0], ca[1], ca[2], ca[3])
                .endVertex();
        buffer.vertex(poseMatrix, (float) b.x, (float) b.y, (float) b.z)
                .color(cb[0], cb[1], cb[2], cb[3])
                .endVertex();
        buffer.vertex(poseMatrix, (float) c.x, (float) c.y, (float) c.z)
                .color(cc[0], cc[1], cc[2], cc[3])
                .endVertex();
    }

    // Simplified geometry helpers
    private static List<Vec3> scaleCircle(List<Vec3> baseCircle, double width, double height) {
        List<Vec3> result = new ArrayList<>(baseCircle.size());
        double sx = width * 0.5;
        double sy = height * 0.5;
        for (Vec3 p : baseCircle) {
            result.add(new Vec3(p.x * sx, p.y * sy, 0.0));
        }
        return result;
    }

    private static List<Vec3> offsetPolygonSimplified(List<Vec3> poly, double amount) {
        // Much simplified offset calculation
        List<Vec3> result = new ArrayList<>(poly.size());
        for (Vec3 p : poly) {
            double len = Math.sqrt(p.x * p.x + p.y * p.y);
            if (len > 1e-9) {
                double scale = (len + amount) / len;
                result.add(new Vec3(p.x * scale, p.y * scale, 0.0));
            } else {
                result.add(new Vec3(amount, 0, 0));
            }
        }
        return result;
    }

    private static Quaternionf calculateEyeOrientation(EyeInstance inst, Vec3 targetPos, Vec3 baseWorld) {
        if (inst.lookDirection != null && inst.lookDirection.length() > 1e-6) {
            Vector3f lookJ = new Vector3f((float) inst.lookDirection.x, (float) inst.lookDirection.y, (float) inst.lookDirection.z);
            lookJ.normalize();
            return new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), lookJ);
        } else {
            Vec3 look = targetPos.subtract(baseWorld);
            Vector3f lookJ = new Vector3f((float) look.x, (float) look.y, (float) look.z);
            if (lookJ.length() > 1e-6f) {
                lookJ.normalize();
                return new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), lookJ);
            } else {
                return new Quaternionf();
            }
        }
    }

    private static void drawSimplifiedBeam(Vec3 start, Vec3 end, float halfWidth, int colorArgb) {
        // Keep the old method for backwards compatibility but make it simpler
        VectorRenderer.drawLineWorld(start, end, colorArgb, halfWidth * 2, false, 1, VectorRenderer.Transform.IDENTITY);

        // Simple end point
        List<Vec3> endCircle = createCircleAt(end, halfWidth, 6);
        VectorRenderer.drawFilledPolygonWorld(endCircle, 0xFFFFCCCC, false, 1, VectorRenderer.Transform.IDENTITY);
    }

    // ... (keeping essential helper methods but simplified)

    // Rest of the helper methods remain the same but simplified where possible
    private static void updateAnimatedBlinking(EyeInstance inst) {
        if (inst.cooldown-- <= 0) {
            if (!inst.blinking && RAND.nextFloat() < 0.03f) {
                inst.blinking = true;
                inst.initialBlinkDuration = 6 + RAND.nextInt(12);
                inst.blinkTimer = inst.initialBlinkDuration;
            }
            inst.cooldown = 40 + RAND.nextInt(100);
        }

        if (inst.blinking) {
            inst.blinkTimer--;
            if (inst.blinkTimer <= 0) {
                inst.blinking = false;
            }
        }
    }

    private static float getBlinkFraction(EyeInstance inst) {
        if (!inst.blinking) return 0f;
        float progress = 1f - ((float) inst.blinkTimer / Math.max(1, inst.initialBlinkDuration));
        return getSmoothedBlinkFraction(progress);
    }

    private static float getSmoothedBlinkFraction(float progress) {
        if (progress <= 0.3f) {
            return (float) Math.sin((progress / 0.3f) * Math.PI * 0.5);
        } else {
            float openProgress = (progress - 0.3f) / 0.7f;
            return (float) (1.0 - Math.sin(openProgress * Math.PI * 0.5));
        }
    }

    private static boolean isEyeFacingCamera(Vec3 eyePos, Quaternionf eyeRotation, Vec3 cameraPos) {
        Vector3f forward = new Vector3f(0, 0, 1);
        eyeRotation.transform(forward);
        Vec3 toCamera = cameraPos.subtract(eyePos).normalize();
        Vector3f toCameraJ = new Vector3f((float) toCamera.x, (float) toCamera.y, (float) toCamera.z);
        float dot = forward.dot(toCameraJ);
        return dot > 0.3f;
    }

    private static void animateIrisWithTracking(EyeInstance inst, Quaternionf eyeRotation, Vec3 cameraPos) {
        if (inst.irisTimer-- <= 0) {
            double ang = RAND.nextDouble() * Math.PI * 2.0;
            double r = RAND.nextDouble() * inst.pupilMaxOffset * 0.8;
            inst.irisTarget = new Vec3(Math.cos(ang) * r, Math.sin(ang) * r, 0.0);
            inst.irisTimer = 8 + RAND.nextInt(28);
        }

        Vec3 d = inst.irisTarget.subtract(inst.irisOffset);
        inst.irisOffset = inst.irisOffset.add(d.scale(0.25));

        if (inst.pupilJitterTimer-- <= 0) {
            double a = RAND.nextDouble() * Math.PI * 2.0;
            double r = RAND.nextDouble() * inst.pupilCenterMaxOffset;
            inst.pupilTargetOffset = new Vec3(Math.cos(a) * r, Math.sin(a) * r, 0.0);
            inst.pupilJitterTimer = 12 + RAND.nextInt(24);
        }

        Vec3 pd = inst.pupilTargetOffset.subtract(inst.pupilOffset);
        inst.pupilOffset = inst.pupilOffset.add(pd.scale(0.15));
    }

    // Essential helper methods
    private static List<Vec3> createCircleLocal(double radius, int segments) {
        List<Vec3> out = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double a = (i / (double) segments) * Math.PI * 2.0;
            out.add(new Vec3(Math.cos(a) * radius, Math.sin(a) * radius, 0.0));
        }
        return out;
    }

    private static Vec3 rotateLocalByQuat(Vec3 local, Quaternionf q) {
        Vector3f vin = new Vector3f((float) local.x, (float) local.y, (float) local.z);
        q.transform(vin);
        return new Vec3(vin.x(), vin.y(), vin.z());
    }

    private static Vec3 pickOffsetAroundTarget(LivingEntity target, Minecraft mc) {
        for (int tries = 0; tries < 20; tries++) { // Reduced tries
            double dist = MIN_DIST + RAND.nextDouble() * (MAX_DIST - MIN_DIST);
            double angleH = RAND.nextDouble() * Math.PI * 2.0;
            double angleV = (RAND.nextDouble() - 0.5) * Math.PI * 0.6;
            double dx = Math.cos(angleH) * Math.cos(angleV) * dist;
            double dy = Math.sin(angleV) * dist;
            double dz = Math.sin(angleH) * Math.cos(angleV) * dist;

            Vec3 world = new Vec3(dx, dy, dz);
            Vec3 abs = new Vec3(target.getX() + dx, target.getY() + target.getBbHeight() * 0.5 + dy, target.getZ() + dz);
            if (!isInsideBlock(mc, abs)) return world;
        }
        return new Vec3(0, target.getBbHeight() + 1.5, 0);
    }

    private static boolean isInsideBlock(Minecraft mc, Vec3 world) {
        net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos((int) Math.floor(world.x), (int) Math.floor(world.y), (int) Math.floor(world.z));
        net.minecraft.world.level.block.state.BlockState s = mc.level.getBlockState(bp);
        return !s.isAir() && !s.getCollisionShape(mc.level, bp).isEmpty();
    }

    private static List<Vec3> createCircleAt(Vec3 center, double radius, int segments) {
        List<Vec3> pts = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double a = (i / (double) segments) * Math.PI * 2.0;
            pts.add(new Vec3(center.x + Math.cos(a) * radius, center.y + Math.sin(a) * radius, center.z));
        }
        return pts;
    }

    private static float[] unpackColor(int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    // Data classes
    private static final class EyeEffectData {
        final int entityId;
        final int lifetime;
        int age = 0;
        final List<EyeInstance> eyes = new ArrayList<>();

        EyeEffectData(int entityId, int count, int lifetime) {
            this.entityId = entityId;
            this.lifetime = lifetime;
            for (int i = 0; i < count; i++) eyes.add(new EyeInstance());
        }

        void ensureEyesInitialized(LivingEntity target, Minecraft mc) {
            for (EyeInstance inst : eyes) {
                if (!inst.initialized) {
                    inst.initialized = true;
                }
            }
        }
    }

    private static final class EyeInstance {
        Vec3 offset = Vec3.ZERO;
        boolean initialized = false;
        float width = 0.6f;
        float height = 0.3f;
        int repositionTimer = 0;

        boolean blinking = false;
        int blinkTimer = 0;
        int initialBlinkDuration = 8;
        int cooldown = 30;

        Vec3 pupilOffset = Vec3.ZERO;
        Vec3 pupilTargetOffset = Vec3.ZERO;
        int pupilJitterTimer = 0;
        double pupilCenterMaxOffset = 0.02;
        double pupilMaxOffset = 0.06;

        Vec3 irisOffset = Vec3.ZERO;
        Vec3 irisTarget = Vec3.ZERO;
        int irisTimer = 0;

        boolean firing = false;
        Vec3 laserEnd = null;
        Vec3 lookDirection = null;

        boolean serverBlinking = false;
        float serverBlinkPhase = 0.0f;
    }

    // Batch rendering data structure
    private static final class EyeRenderData {
        final Vec3 baseWorld;
        final EyeInstance inst;
        final Quaternionf quat;
        final float blinkFraction;
        final boolean facingCamera;

        EyeRenderData(Vec3 baseWorld, EyeInstance inst, Quaternionf quat, float blinkFraction, boolean facingCamera) {
            this.baseWorld = baseWorld;
            this.inst = inst;
            this.quat = quat;
            this.blinkFraction = blinkFraction;
            this.facingCamera = facingCamera;
        }
    }
}