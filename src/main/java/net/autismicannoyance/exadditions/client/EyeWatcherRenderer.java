package net.autismicannoyance.exadditions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
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
 * EyeWatcherRenderer — outer oval wireframe, filled circular pupil, centered white dot.
 * Eyes are attached to an entity and always face it using a quaternion rotation.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class EyeWatcherRenderer {
    private static final Map<Integer, EyeEffectData> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // config
    private static final int MIN_EYES = 2;
    private static final int MAX_EYES = 8;
    private static final double MIN_DIST = 2.0;
    private static final double MAX_DIST = 5.0;
    private static final int OUTER_SEGMENTS = 36;     // outer ellipse resolution
    private static final int PUPIL_SEGMENTS = 20;     // pupil triangle-fan resolution
    private static final int DOT_SEGMENTS = 10;       // white dot resolution
    private static final float OUTLINE_THICKNESS_PIXELS = 2f;

    // colors ARGB
    private static final int COLOR_OUTER = 0xFFFF4444; // red
    private static final int COLOR_PUPIL = 0xFF000000; // black
    private static final int COLOR_DOT   = 0xFFFFFFFF; // white

    public static void addEffect(int entityId, int eyeCount, int lifetimeTicks) {
        eyeCount = Math.max(MIN_EYES, Math.min(MAX_EYES, eyeCount));
        EFFECTS.put(entityId, new EyeEffectData(entityId, eyeCount, lifetimeTicks));
    }

    public static void removeEffect(int entityId) { EFFECTS.remove(entityId); }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();

        // GL / shader setup
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Iterator<Map.Entry<Integer, EyeEffectData>> it = EFFECTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, EyeEffectData> entry = it.next();
            EyeEffectData data = entry.getValue();

            if (data.lifetime >= 0 && data.age++ >= data.lifetime) {
                it.remove();
                continue;
            }

            Entity target = mc.level.getEntity(data.entityId);
            if (!(target instanceof LivingEntity)) {
                it.remove();
                continue;
            }

            float partial = event.getPartialTick();
            double tx = Mth.lerp(partial, target.xOld, target.getX());
            double ty = Mth.lerp(partial, target.yOld, target.getY()) + target.getBbHeight() * 0.5;
            double tz = Mth.lerp(partial, target.zOld, target.getZ());
            Vec3 targetPos = new Vec3(tx, ty, tz);

            data.ensureEyesInitialized((LivingEntity) target, mc);

            for (EyeInstance inst : data.eyes) {
                Vec3 desiredWorld = targetPos.add(inst.offset);

                // avoid embedded in blocks: try lifting or respawn offset
                if (isInsideBlock(mc, desiredWorld)) {
                    Vec3 lifted = tryLiftAboveBlock(mc, desiredWorld, 3.0);
                    if (lifted == null) {
                        inst.offset = pickOffsetAroundTarget((LivingEntity) target, mc);
                        desiredWorld = targetPos.add(inst.offset);
                        if (isInsideBlock(mc, desiredWorld)) continue; // skip this eye this tick
                    } else {
                        desiredWorld = lifted;
                        inst.offset = desiredWorld.subtract(targetPos);
                    }
                }

                // blinking: longer blinks (~12..20 ticks)
                if (inst.cooldown-- <= 0) {
                    if (!inst.blinking && RAND.nextFloat() < 0.06f) {
                        inst.blinking = true;
                        inst.initialBlinkDuration = 12 + RAND.nextInt(9);
                        inst.blinkTimer = inst.initialBlinkDuration;
                    }
                    inst.cooldown = 20 + RAND.nextInt(40);
                }
                if (inst.blinking) {
                    inst.blinkTimer--;
                    if (inst.blinkTimer <= 0) inst.blinking = false;
                }
                float blinkFraction = 0f;
                if (inst.blinking) {
                    blinkFraction = 1f - ((float) inst.blinkTimer / Math.max(1, inst.initialBlinkDuration));
                    blinkFraction = Math.max(0f, Math.min(1f, blinkFraction));
                }

                // compute direction from eye -> target (note: targetPos - eye)
                Vec3 eyeWorld = desiredWorld;
                Vec3 look = targetPos.subtract(eyeWorld);
                Vector3f lookJ = new Vector3f((float) look.x, (float) look.y, (float) look.z);
                if (lookJ.length() <= 1e-6f) continue;
                lookJ.normalize();

                // create quaternion rotating local forward (0,0,1) to lookJ
                Vector3f localForward = new Vector3f(0f, 0f, 1f);
                Quaternionf quat = new Quaternionf().rotationTo(localForward, lookJ);

                // transform that orients the local XY-plane to face the target
                Vector3f transVec = new Vector3f(0f, 0f, 0f);
                VectorRenderer.Transform rotTransform = VectorRenderer.Transform.fromQuaternion(new Vec3(0,0,0), quat, 1f, Vec3.ZERO);

                // draw outer oval wireframe (local plane XY)
                VectorRenderer.Wireframe outer = VectorRenderer.createWireframe();
                buildEllipseWireframe(outer, inst.radiusX, inst.radiusY, OUTER_SEGMENTS);
                VectorRenderer.drawWireframeAttached(
                        outer,
                        data.entityId,
                        inst.offset,
                        true,
                        COLOR_OUTER,
                        OUTLINE_THICKNESS_PIXELS,
                        true,
                        false,
                        1,
                        rotTransform
                );

                // pupil = circle (centered), scaled by blink fraction
                float basePupil = Math.min(inst.radiusX, inst.radiusY) * 0.45f;
                float pupilScale = 1f - blinkFraction; // 1 = open, 0 = closed
                pupilScale = Math.max(0f, Math.min(1f, pupilScale));
                float pupilR = Math.max(0.005f, basePupil * pupilScale);

                if (pupilR <= 0.01f && inst.blinking) {
                    // nearly closed -> simple horizontal lid line
                    VectorRenderer.Wireframe lid = VectorRenderer.createWireframe();
                    float lx = inst.radiusX * 0.6f;
                    lid.addLine(new Vec3(-lx, 0, 0), new Vec3(lx, 0, 0));
                    VectorRenderer.drawWireframeAttached(
                            lid,
                            data.entityId,
                            inst.offset,
                            true,
                            COLOR_PUPIL,
                            Math.max(1f, OUTLINE_THICKNESS_PIXELS),
                            true,
                            false,
                            1,
                            rotTransform
                    );
                } else {
                    // filled pupil triangle fan (draw double-sided so visible from any side)
                    List<Vec3[]> pupilTris = buildCircleTriangleFan(pupilR, PUPIL_SEGMENTS);
                    for (Vec3[] tri : pupilTris) {
                        VectorRenderer.drawPlaneAttached(
                                data.entityId,
                                inst.offset.add(tri[0]),
                                inst.offset.add(tri[1]),
                                inst.offset.add(tri[2]),
                                new int[]{COLOR_PUPIL, COLOR_PUPIL, COLOR_PUPIL},
                                true,   // doubleSided
                                true,
                                1,
                                rotTransform
                        );
                    }

                    // centered white dot (small filled circle)
                    float dotR = Math.max(0.01f, pupilR * 0.18f);
                    List<Vec3[]> dotTris = buildCircleTriangleFan(dotR, DOT_SEGMENTS);
                    for (Vec3[] tri : dotTris) {
                        VectorRenderer.drawPlaneAttached(
                                data.entityId,
                                inst.offset.add(tri[0]),
                                inst.offset.add(tri[1]),
                                inst.offset.add(tri[2]),
                                new int[]{COLOR_DOT, COLOR_DOT, COLOR_DOT},
                                true,
                                true,
                                1,
                                rotTransform
                        );
                    }
                }
            }
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /* ---------- geometry helpers ---------- */

    private static void buildEllipseWireframe(VectorRenderer.Wireframe wf, double rx, double ry, int segments) {
        if (segments < 4) segments = 4;
        Vec3 first = null, prev = null;
        for (int i = 0; i <= segments; i++) {
            double t = (i / (double) segments) * Math.PI * 2.0;
            double x = Math.cos(t) * rx;
            double y = Math.sin(t) * ry;
            Vec3 p = new Vec3(x, y, 0);
            if (prev != null) wf.addLine(prev, p);
            if (first == null) first = p;
            prev = p;
        }
        if (prev != null && first != null) wf.addLine(prev, first);
    }

    private static List<Vec3[]> buildCircleTriangleFan(double r, int segments) {
        List<Vec3[]> out = new ArrayList<>(segments);
        if (segments < 3) segments = 3;
        Vec3 center = new Vec3(0, 0, 0);
        Vec3 prev = null;
        for (int i = 0; i <= segments; i++) {
            double t = (i / (double) segments) * Math.PI * 2.0;
            double x = Math.cos(t) * r;
            double y = Math.sin(t) * r;
            Vec3 p = new Vec3(x, y, 0);
            if (i > 0 && prev != null) out.add(new Vec3[]{center, prev, p});
            prev = p;
        }
        return out;
    }

    /* ---------- placement helpers ---------- */

    private static Vec3 pickOffsetAroundTarget(LivingEntity target, Minecraft mc) {
        for (int tries = 0; tries < 30; tries++) {
            double dist = MIN_DIST + RAND.nextDouble() * (MAX_DIST - MIN_DIST);
            double angle = RAND.nextDouble() * Math.PI * 2.0;
            double ovalFactor = 0.6 + RAND.nextDouble() * 0.9;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist * ovalFactor;
            double dy = (RAND.nextDouble() - 0.2) * Math.max(0.6, target.getBbHeight());
            Vec3 candidate = new Vec3(dx, dy, dz);
            Vec3 world = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ()).add(candidate);
            if (!isInsideBlock(mc, world)) {
                // ensure oval shape by using different rx/ry
                return candidate;
            }
            Vec3 lifted = tryLiftAboveBlock(mc, world, 3.0);
            if (lifted != null) return lifted.subtract(new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ()));
        }
        return new Vec3(0, Math.max(0.8, target.getBbHeight() * 0.6), 0);
    }

    private static boolean isInsideBlock(Minecraft mc, Vec3 worldPos) {
        BlockPos bp = new BlockPos((int) Math.floor(worldPos.x), (int) Math.floor(worldPos.y), (int) Math.floor(worldPos.z));
        BlockState st = mc.level.getBlockState(bp);
        return !st.isAir();
    }

    private static Vec3 tryLiftAboveBlock(Minecraft mc, Vec3 start, double maxUp) {
        double step = 0.25;
        for (double s = step; s <= maxUp + 1e-6; s += step) {
            Vec3 trial = start.add(0, s, 0);
            if (!isInsideBlock(mc, trial)) return trial;
        }
        return null;
    }

    /* ---------- data containers ---------- */

    private static final class EyeEffectData {
        final int entityId;
        final int lifetime;
        int age = 0;
        final List<EyeInstance> eyes = new ArrayList<>();

        EyeEffectData(int entityId, int eyeCount, int lifetime) {
            this.entityId = entityId;
            this.lifetime = lifetime;
            for (int i = 0; i < eyeCount; i++) eyes.add(new EyeInstance());
        }

        void ensureEyesInitialized(LivingEntity target, Minecraft mc) {
            for (EyeInstance inst : eyes) {
                if (!inst.initialized) {
                    // pick offset first
                    inst.offset = pickOffsetAroundTarget(target, mc);
                    // ensure oval: radiusX != radiusY deliberately
                    float rx = 0.28f + RAND.nextFloat() * 0.5f; // horizontal size
                    float ry = rx * (0.55f + RAND.nextFloat() * 0.9f); // vertical -> generally different
                    inst.radiusX = rx;
                    inst.radiusY = ry;
                    inst.initialized = true;
                }
            }
        }
    }

    private static final class EyeInstance {
        Vec3 offset = Vec3.ZERO;
        boolean initialized = false;
        float radiusX = 0.45f;
        float radiusY = 0.28f;
        // blink state
        boolean blinking = false;
        int blinkTimer = 0;
        int initialBlinkDuration = 12;
        int cooldown = 20;
    }
}
