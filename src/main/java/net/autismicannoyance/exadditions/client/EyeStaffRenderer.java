package net.autismicannoyance.exadditions.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merged EyeWatcher + blocky 3D laser renderer.
 * Uses server-provided per-eye offsets so lasers line up exactly.
 * Eyes now properly face their targets based on server-sent look direction.
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class EyeStaffRenderer {
    private static final Map<Integer, EyeEffectData> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    private static final int MIN_EYES = 2;
    private static final int MAX_EYES = 24;
    private static final double MIN_DIST = 3.0;
    private static final double MAX_DIST = 6.0;

    private static final int COLOR_OUTLINE = 0xFFFF0000; // red
    private static final int COLOR_SCLERA  = 0xFF111111; // dark
    private static final int COLOR_PUPIL   = 0xFFFFFFFF; // white
    private static final int COLOR_IRIS    = 0xFF000000; // black
    private static final int COLOR_LASER   = 0xFFFF5555; // laser color

    private static final int SEGMENTS = 160;
    private static final double EXPONENT = 0.028;
    private static final double OUTLINE_THICKNESS_FRACT = 0.075;

    // keep short TTL so effects expire if server stops sending
    private static final int CLIENT_TTL = 40;

    /** Called by EyeRenderPacket.handle on client thread */
    public static void handlePacket(net.autismicannoyance.exadditions.network.EyeRenderPacket msg) {
        int ownerId = msg.entityId;
        List<net.autismicannoyance.exadditions.network.EyeRenderPacket.EyeEntry> entries = msg.eyes == null ? Collections.emptyList() : msg.eyes;

        // create EyeEffectData sized to packet count but keep same class for drawing logic
        EyeEffectData data = new EyeEffectData(ownerId, Math.max(MIN_EYES, Math.min(MAX_EYES, entries.size())), CLIENT_TTL);
        data.eyes.clear();
        for (net.autismicannoyance.exadditions.network.EyeRenderPacket.EyeEntry e : entries) {
            EyeInstance inst = new EyeInstance();
            inst.offset = e.offset == null ? Vec3.ZERO : e.offset; // server-provided offset for exactness
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

            // server-provided firing state / laser end
            inst.firing = e.firing;
            inst.laserEnd = e.laserEnd;

            // server-provided look direction
            inst.lookDirection = e.lookDirection;

            data.eyes.add(inst);
        }

        EFFECTS.put(ownerId, data);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float partial = event.getPartialTick();

        Iterator<Map.Entry<Integer, EyeEffectData>> it = EFFECTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, EyeEffectData> ent = it.next();
            EyeEffectData data = ent.getValue();

            if (data.lifetime >= 0 && data.age++ >= data.lifetime) { it.remove(); continue; }

            Entity maybe = mc.level.getEntity(data.entityId);
            if (!(maybe instanceof LivingEntity target)) { it.remove(); continue; }

            double tx = Mth.lerp(partial, target.xOld, target.getX());
            double ty = Mth.lerp(partial, target.yOld, target.getY()) + target.getBbHeight() * 0.5;
            double tz = Mth.lerp(partial, target.zOld, target.getZ());
            Vec3 targetPos = new Vec3(tx, ty, tz);

            data.ensureEyesInitialized(target, mc);

            for (EyeInstance inst : data.eyes) {
                // base world position for this eye
                Vec3 baseWorld = targetPos.add(inst.offset);

                // inside block repositioning preserved (same as original)
                if (isInsideBlock(mc, baseWorld)) {
                    inst.repositionTimer--;
                    if (inst.repositionTimer <= 0) {
                        inst.offset = pickOffsetAroundTarget(target, mc);
                        inst.repositionTimer = 40;
                    }
                    continue;
                }

                // blinking
                if (inst.cooldown-- <= 0) {
                    if (!inst.blinking && RAND.nextFloat() < 0.04f) {
                        inst.blinking = true;
                        inst.initialBlinkDuration = 8 + RAND.nextInt(8);
                        inst.blinkTimer = inst.initialBlinkDuration;
                    }
                    inst.cooldown = 30 + RAND.nextInt(60);
                }
                if (inst.blinking) {
                    inst.blinkTimer--;
                    if (inst.blinkTimer <= 0) inst.blinking = false;
                }
                float blinkFraction = 0f;
                if (inst.blinking) {
                    float t = 1f - ((float) inst.blinkTimer / Math.max(1, inst.initialBlinkDuration));
                    blinkFraction = (float) Math.sin(t * Math.PI);
                }

                // NEW: Compute orientation quaternion based on server-provided look direction
                Quaternionf quat;
                if (inst.lookDirection != null && inst.lookDirection.length() > 1e-6) {
                    // Use server-provided look direction
                    Vector3f lookJ = new Vector3f((float) inst.lookDirection.x, (float) inst.lookDirection.y, (float) inst.lookDirection.z);
                    lookJ.normalize();
                    quat = new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), lookJ);
                } else {
                    // Fallback - look at the player (owner)
                    Vec3 look = targetPos.subtract(baseWorld);
                    Vector3f lookJ = new Vector3f((float) look.x, (float) look.y, (float) look.z);
                    if (lookJ.length() > 1e-6f) {
                        lookJ.normalize();
                        quat = new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), lookJ);
                    } else {
                        quat = new Quaternionf(); // identity
                    }
                }

                // animate iris/pupil jitter
                animateIris(inst);

                // draw eye exactly as your original code (unchanged)
                drawEye(baseWorld, inst, quat, blinkFraction);

                // draw blocky 3D laser if server flagged firing and sent laserEnd
                if (inst.firing && inst.laserEnd != null) {
                    drawBlockyBeam(baseWorld, inst.laserEnd, 0.06f, COLOR_LASER);
                    // small impact fill
                    VectorRenderer.drawFilledPolygonWorld(createCircleAt(inst.laserEnd, 0.06, 10), 0xFFFFCCCC, false, 6, VectorRenderer.Transform.IDENTITY);
                }
            }
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /* ---------------- drawing helpers (exactly as your original EyeWatcherRenderer) ---------------- */

    private static void drawEye(Vec3 baseWorld, EyeInstance inst, Quaternionf quat, float blinkFraction) {
        float width = inst.width;
        float height = inst.height * (1f - blinkFraction * 0.92f);

        List<Vec3> local = createAlmondLocal(width, height, SEGMENTS, EXPONENT);
        Vec3 centroidLocal = centroidLocal(local);
        Vec3 worldCentroid = baseWorld.add(rotateLocalByQuat(centroidLocal, quat));

        double outlineAmount = width * OUTLINE_THICKNESS_FRACT;
        List<Vec3> outerLocal = offsetPolygonByEdgeIntersection(local, outlineAmount);

        double baseBias = 0.0009 * (width / 0.8);

        Vec3 forward = rotateLocalByQuat(new Vec3(0, 0, 1), quat);
        Vec3 biasOutline = forward.scale(-baseBias * 1.8);
        Vec3 biasSclera = forward.scale(0.0);
        Vec3 biasPupil = forward.scale(baseBias * 1.05);
        Vec3 biasIris = forward.scale(baseBias * 1.9);

        // outline
        int[] outlineCol = new int[]{COLOR_OUTLINE, COLOR_OUTLINE, COLOR_OUTLINE};
        int n = local.size();
        for (int i = 0; i < n; i++) {
            Vec3 inA = local.get(i);
            Vec3 inB = local.get((i + 1) % n);
            Vec3 outA = outerLocal.get(i);
            Vec3 outB = outerLocal.get((i + 1) % n);

            Vec3 wOutA = baseWorld.add(rotateLocalByQuat(outA, quat)).add(biasOutline);
            Vec3 wOutB = baseWorld.add(rotateLocalByQuat(outB, quat)).add(biasOutline);
            Vec3 wInA = baseWorld.add(rotateLocalByQuat(inA, quat)).add(biasSclera);
            Vec3 wInB = baseWorld.add(rotateLocalByQuat(inB, quat)).add(biasSclera);

            VectorRenderer.drawPlaneWorld(wOutA, wOutB, wInB, outlineCol, true, 1, VectorRenderer.Transform.IDENTITY);
            VectorRenderer.drawPlaneWorld(wOutA, wInB, wInA, outlineCol, true, 1, VectorRenderer.Transform.IDENTITY);
        }

        // sclera
        int[] scleraCol = new int[]{COLOR_SCLERA, COLOR_SCLERA, COLOR_SCLERA};
        for (int i = 0; i < n; i++) {
            Vec3 a = local.get(i);
            Vec3 b = local.get((i + 1) % n);
            Vec3 wa = baseWorld.add(rotateLocalByQuat(a, quat)).add(biasSclera);
            Vec3 wb = baseWorld.add(rotateLocalByQuat(b, quat)).add(biasSclera);
            VectorRenderer.drawPlaneWorld(worldCentroid, wa, wb, scleraCol, true, 1, VectorRenderer.Transform.IDENTITY);
        }

        // pupil
        float pupilRadius = Math.min(width, height) * 0.32f * (1f - blinkFraction);
        List<Vec3> pupilLocal = createCircleLocal(pupilRadius, Math.max(20, SEGMENTS / 5));
        Vec3 pupilCenterLocal = new Vec3(inst.pupilOffset.x, inst.pupilOffset.y, 0.0);
        Vec3 worldPupilCenter = baseWorld.add(rotateLocalByQuat(pupilCenterLocal, quat)).add(biasPupil);
        int[] pupilCol = new int[]{COLOR_PUPIL, COLOR_PUPIL, COLOR_PUPIL};
        for (int i = 0; i < pupilLocal.size(); i++) {
            Vec3 a = pupilLocal.get(i).add(pupilCenterLocal);
            Vec3 b = pupilLocal.get((i + 1) % pupilLocal.size()).add(pupilCenterLocal);
            Vec3 wa = baseWorld.add(rotateLocalByQuat(a, quat)).add(biasPupil);
            Vec3 wb = baseWorld.add(rotateLocalByQuat(b, quat)).add(biasPupil);
            VectorRenderer.drawPlaneWorld(worldPupilCenter, wa, wb, pupilCol, true, 1, VectorRenderer.Transform.IDENTITY);
        }

        // iris
        float irisRadius = Math.max(0.02f, pupilRadius * 0.20f);
        List<Vec3> irisLocal = createCircleLocal(irisRadius, 12);
        Vec3 irisCenterLocal = new Vec3(inst.pupilOffset.x + inst.irisOffset.x, inst.pupilOffset.y + inst.irisOffset.y, 0.0);
        Vec3 worldIrisCenter = baseWorld.add(rotateLocalByQuat(irisCenterLocal, quat)).add(biasIris);
        int[] irisCol = new int[]{COLOR_IRIS, COLOR_IRIS, COLOR_IRIS};
        for (int i = 0; i < irisLocal.size(); i++) {
            Vec3 a = irisLocal.get(i).add(irisCenterLocal);
            Vec3 b = irisLocal.get((i + 1) % irisLocal.size()).add(irisCenterLocal);
            Vec3 wa = baseWorld.add(rotateLocalByQuat(a, quat)).add(biasIris);
            Vec3 wb = baseWorld.add(rotateLocalByQuat(b, quat)).add(biasIris);
            VectorRenderer.drawPlaneWorld(worldIrisCenter, wa, wb, irisCol, true, 1, VectorRenderer.Transform.IDENTITY);
        }
    }

    /* ---------------- geometry helpers (unchanged) ---------------- */

    private static List<Vec3> createAlmondLocal(double width, double height, int segments, double exponent) {
        List<Vec3> pts = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double t = (i / (double) segments) * Math.PI * 2.0;
            double sx = Math.cos(t);
            double sy = Math.sin(t);
            double x = sx * (width * 0.5);
            double vy = Math.pow(Math.abs(sy), exponent);
            double y = Math.signum(sy) * vy * (height * 0.5);
            pts.add(new Vec3(x, y, 0.0));
        }
        return pts;
    }

    private static Vec3 centroidLocal(List<Vec3> pts) {
        double sx = 0, sy = 0;
        for (Vec3 p : pts) { sx += p.x; sy += p.y; }
        int n = pts.size();
        if (n == 0) return new Vec3(0,0,0);
        return new Vec3(sx / n, sy / n, 0.0);
    }

    private static List<Vec3> offsetPolygonByEdgeIntersection(List<Vec3> poly, double amount) {
        int n = poly.size();
        List<Vec3> out = new ArrayList<>(n);
        if (n < 3) return new ArrayList<>(poly);

        Vec3[] p = new Vec3[n];
        Vec3[] q = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 a = poly.get(i);
            Vec3 b = poly.get((i + 1) % n);
            double ex = b.x - a.x;
            double ey = b.y - a.y;
            double nx = -ey;
            double ny = ex;
            double len = Math.sqrt(nx*nx + ny*ny);
            if (len <= 1e-9) { nx = 0; ny = 0; }
            else { nx /= len; ny /= len; }
            p[i] = new Vec3(a.x + nx * amount, a.y + ny * amount, 0.0);
            q[i] = new Vec3(b.x + nx * amount, b.y + ny * amount, 0.0);
        }

        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            Vec3 A = p[prev], B = q[prev], C = p[i], D = q[i];
            Vec3 inter = lineIntersect2D(A, B, C, D);
            if (inter == null) {
                Vec3 fallback = new Vec3((q[prev].x + p[i].x) * 0.5, (q[prev].y + p[i].y) * 0.5, 0.0);
                out.add(fallback);
            } else {
                Vec3 orig = poly.get(i);
                double dx = inter.x - orig.x;
                double dy = inter.y - orig.y;
                double dist = Math.sqrt(dx*dx + dy*dy);
                double maxAllowed = Math.max( amount * 12.0,  (Math.abs(orig.x)+Math.abs(orig.y))*0.001 + amount * 8.0 );
                if (dist > maxAllowed) {
                    Vec3 avg = new Vec3((p[prev].x - poly.get(prev).x + p[i].x - poly.get(i).x) * 0.5,
                            (p[prev].y - poly.get(prev).y + p[i].y - poly.get(i).y) * 0.5, 0.0);
                    out.add(new Vec3(poly.get(i).x + avg.x, poly.get(i).y + avg.y, 0.0));
                } else {
                    out.add(inter);
                }
            }
        }
        return out;
    }

    private static Vec3 lineIntersect2D(Vec3 A, Vec3 B, Vec3 C, Vec3 D) {
        double r_x = B.x - A.x;
        double r_y = B.y - A.y;
        double s_x = D.x - C.x;
        double s_y = D.y - C.y;
        double denom = r_x * s_y - r_y * s_x;
        if (Math.abs(denom) < 1e-9) return null; // parallel
        double t = ((C.x - A.x) * s_y - (C.y - A.y) * s_x) / denom;
        return new Vec3(A.x + r_x * t, A.y + r_y * t, 0.0);
    }

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

    /* ---------- animation helpers (unchanged) ---------- */

    private static void animateIris(EyeInstance inst) {
        if (inst.irisTimer-- <= 0) {
            double ang = RAND.nextDouble() * Math.PI * 2.0;
            double r = RAND.nextDouble() * inst.pupilMaxOffset;
            inst.irisTarget = new Vec3(Math.cos(ang) * r, Math.sin(ang) * r, 0.0);
            inst.irisTimer = 6 + RAND.nextInt(36);
        }
        Vec3 d = inst.irisTarget.subtract(inst.irisOffset);
        inst.irisOffset = inst.irisOffset.add(d.scale(0.18));

        if (inst.pupilJitterTimer-- <= 0) {
            double a = RAND.nextDouble() * Math.PI * 2.0;
            double r = RAND.nextDouble() * inst.pupilCenterMaxOffset;
            inst.pupilTargetOffset = new Vec3(Math.cos(a) * r, Math.sin(a) * r, 0.0);
            inst.pupilJitterTimer = 18 + RAND.nextInt(36);
        }
        Vec3 pd = inst.pupilTargetOffset.subtract(inst.pupilOffset);
        inst.pupilOffset = inst.pupilOffset.add(pd.scale(0.12));
    }

    /* ---------- placement helpers (unchanged) ---------- */

    private static Vec3 pickOffsetAroundTarget(LivingEntity target, Minecraft mc) {
        for (int tries = 0; tries < 50; tries++) {
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

    /* ---------- helper to create small circle around point (world coords) ---------- */
    private static List<Vec3> createCircleAt(Vec3 center, double radius, int segments) {
        List<Vec3> pts = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double a = (i / (double) segments) * Math.PI * 2.0;
            pts.add(new Vec3(center.x + Math.cos(a) * radius, center.y + Math.sin(a) * radius, center.z));
        }
        return pts;
    }

    /* ---------- BLOCKY BEAM DRAWER ---------- */
    // draw a rectangular prism between start and end. halfWidth controls thickness.
    private static void drawBlockyBeam(Vec3 start, Vec3 end, float halfWidth, int colorArgb) {
        // compute basis
        Vec3 dir = end.subtract(start);
        if (dir.length() <= 1e-6) return;
        Vec3 forward = dir.normalize();
        Vec3 up = new Vec3(0, 1, 0);
        // if forward is nearly parallel to up, pick another up
        if (Math.abs(forward.dot(up)) > 0.999) up = new Vec3(1, 0, 0);
        Vec3 right = forward.cross(up);
        if (right.length() <= 1e-6) return;
        right = right.normalize().scale(halfWidth);
        Vec3 realUp = right.cross(forward).normalize().scale(halfWidth);

        // 8 corners: s +/- right +/- up, e +/- right +/- up
        Vec3 s1 = start.add(right).add(realUp);
        Vec3 s2 = start.add(right).subtract(realUp);
        Vec3 s3 = start.subtract(right).subtract(realUp);
        Vec3 s4 = start.subtract(right).add(realUp);

        Vec3 e1 = end.add(right).add(realUp);
        Vec3 e2 = end.add(right).subtract(realUp);
        Vec3 e3 = end.subtract(right).subtract(realUp);
        Vec3 e4 = end.subtract(right).add(realUp);

        int[] col = new int[]{colorArgb, colorArgb, colorArgb};

        // draw 4 side faces (each as two triangles)
        VectorRenderer.drawPlaneWorld(s1, e1, e2, col, true, 1, VectorRenderer.Transform.IDENTITY);
        VectorRenderer.drawPlaneWorld(s1, e2, s2, col, true, 1, VectorRenderer.Transform.IDENTITY);

        VectorRenderer.drawPlaneWorld(s2, e2, e3, col, true, 1, VectorRenderer.Transform.IDENTITY);
        VectorRenderer.drawPlaneWorld(s2, e3, s3, col, true, 1, VectorRenderer.Transform.IDENTITY);

        VectorRenderer.drawPlaneWorld(s3, e3, e4, col, true, 1, VectorRenderer.Transform.IDENTITY);
        VectorRenderer.drawPlaneWorld(s3, e4, s4, col, true, 1, VectorRenderer.Transform.IDENTITY);

        VectorRenderer.drawPlaneWorld(s4, e4, e1, col, true, 1, VectorRenderer.Transform.IDENTITY);
        VectorRenderer.drawPlaneWorld(s4, e1, s1, col, true, 1, VectorRenderer.Transform.IDENTITY);

        // cap ends (optional: small translucent caps)
        VectorRenderer.drawPlaneWorld(s1, s2, s3, col, true, 1, VectorRenderer.Transform.IDENTITY);
        VectorRenderer.drawPlaneWorld(s1, s3, s4, col, true, 1, VectorRenderer.Transform.IDENTITY);
        VectorRenderer.drawPlaneWorld(e1, e3, e2, col, true, 1, VectorRenderer.Transform.IDENTITY);
        VectorRenderer.drawPlaneWorld(e1, e4, e3, col, true, 1, VectorRenderer.Transform.IDENTITY);
    }

    /* ---------- data ---------- */
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
                    inst.offset = pickOffsetAroundTarget(target, mc);
                    float scale = 0.6f + RAND.nextFloat() * 0.9f;
                    inst.width = scale * 0.88f;
                    inst.height = scale * 0.45f;

                    inst.pupilOffset = Vec3.ZERO;
                    inst.pupilTargetOffset = Vec3.ZERO;
                    inst.pupilJitterTimer = 8 + RAND.nextInt(30);
                    inst.pupilCenterMaxOffset = Math.max(0.01, inst.width * 0.03);
                    inst.pupilMaxOffset = Math.max(0.03, Math.min(inst.width * 0.18, inst.height * 0.18));
                    inst.irisOffset = Vec3.ZERO;
                    inst.irisTarget = Vec3.ZERO;
                    inst.irisTimer = 4 + RAND.nextInt(22);
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

        // server-driven laser state
        boolean firing = false;
        Vec3 laserEnd = null;
        Vec3 lookDirection = null; // where the eye should be looking
    }
}