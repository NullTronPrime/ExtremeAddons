            package net.autismicannoyance.exadditions.client;

            import com.mojang.blaze3d.systems.RenderSystem;
            import net.autismicannoyance.exadditions.ExAdditions;
            import net.minecraft.client.Camera;
            import net.minecraft.client.Minecraft;
            import net.minecraft.client.renderer.GameRenderer;
            import net.minecraft.core.BlockPos;
            import net.minecraft.util.Mth;
            import net.minecraft.world.entity.Entity;
            import net.minecraft.world.entity.LivingEntity;
            import net.minecraft.world.level.block.state.BlockState;
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
             * Reworked EyeWatcherRenderer — uses robust polygon offset via edge-offset/intersection,
             * centroid-based fan fill, per-layer tiny depth bias scaled by eye size, and high segment count.
             *
             * Keeps: blinking, repositioning, 3x multiplier, darting iris, and uses VectorRenderer planes.
             */
            @Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
            public final class EyeWatcherRenderer {
                private static final Map<Integer, EyeEffectData> EFFECTS = new ConcurrentHashMap<>();
                private static final Random RAND = new Random();

                // general config
                private static final int MIN_EYES = 2;
                private static final int MAX_EYES = 24;
                private static final double MIN_DIST = 3.0;
                private static final double MAX_DIST = 6.0;

                // visuals
                private static final int COLOR_OUTLINE = 0xFFFF0000; // red
                private static final int COLOR_SCLERA  = 0xFF111111; // dark
                private static final int COLOR_PUPIL   = 0xFFFFFFFF; // white
                private static final int COLOR_IRIS    = 0xFF000000; // black

                // geometry / tuning
                private static final int SEGMENTS = 160;         // more segments => smoother outline
                private static final double EXPONENT = 0.028;    // smaller => sharper tips (superellipse exponent)
                private static final double OUTLINE_THICKNESS_FRACT = 0.075; // outline width = fraction * eye width

                public static void addEffect(int entityId, int eyeCount, int lifetimeTicks) {
                    int multiplied = Math.max(MIN_EYES, Math.min(MAX_EYES, eyeCount * 3));
                    EFFECTS.put(entityId, new EyeEffectData(entityId, multiplied, lifetimeTicks));
                }

                public static void removeEffect(int entityId) {
                    EFFECTS.remove(entityId);
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

                        // lifetime
                        if (data.lifetime >= 0 && data.age++ >= data.lifetime) { it.remove(); continue; }

                        Entity maybe = mc.level.getEntity(data.entityId);
                        if (!(maybe instanceof LivingEntity target)) { it.remove(); continue; }

                        // interpolated target position
                        double tx = Mth.lerp(partial, target.xOld, target.getX());
                        double ty = Mth.lerp(partial, target.yOld, target.getY()) + target.getBbHeight() * 0.5;
                        double tz = Mth.lerp(partial, target.zOld, target.getZ());
                        Vec3 targetPos = new Vec3(tx, ty, tz);

                        data.ensureEyesInitialized(target, mc);

                        for (EyeInstance inst : data.eyes) {
                            Vec3 baseWorld = targetPos.add(inst.offset);

                            // if inside block, try repositioning
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

                            // orientation quaternion: rotate local +Z to look vector
                            Vec3 look = targetPos.subtract(baseWorld);
                            Vector3f lookJ = new Vector3f((float) look.x, (float) look.y, (float) look.z);
                            if (lookJ.length() <= 1e-6f) continue;
                            lookJ.normalize();
                            Quaternionf quat = new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), lookJ);

                            // animate iris/pupil jitter
                            animateIris(inst);

                            // draw using robust offset polygon & centroid-based fan
                            drawEye(baseWorld, inst, quat, blinkFraction);
                        }
                    }

                    RenderSystem.enableCull();
                    RenderSystem.disableBlend();
                }

                /* ----------- drawing helpers ----------- */

                private static void drawEye(Vec3 baseWorld, EyeInstance inst, Quaternionf quat, float blinkFraction) {
                    float width = inst.width;
                    float height = inst.height * (1f - blinkFraction * 0.92f);

                    // local contour in plane z=0 (superellipse-like)
                    List<Vec3> local = createAlmondLocal(width, height, SEGMENTS, EXPONENT);

                    // compute centroid (local) and rotate it for correct fan root in world
                    Vec3 centroidLocal = centroidLocal(local);
                    Vec3 worldCentroid = baseWorld.add(rotateLocalByQuat(centroidLocal, quat));

                    // compute outward offset polygon by offsetting edges and intersecting adjacent offset lines
                    double outlineAmount = width * OUTLINE_THICKNESS_FRACT;
                    List<Vec3> outerLocal = offsetPolygonByEdgeIntersection(local, outlineAmount);

                    // depth bias scale based on width (so larger eyes get slightly larger bias)
                    double baseBias = 0.0009 * (width / 0.8);

                    Vec3 forward = rotateLocalByQuat(new Vec3(0, 0, 1), quat);
                    Vec3 biasOutline = forward.scale(-baseBias * 1.8);
                    Vec3 biasSclera = forward.scale(0.0);
                    Vec3 biasPupil = forward.scale(baseBias * 1.05);
                    Vec3 biasIris = forward.scale(baseBias * 1.9);

                    // 1) outline ring (draw behind)
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

                        // two triangles form the quad between rings
                        VectorRenderer.drawPlaneWorld(wOutA, wOutB, wInB, outlineCol, true, 1, VectorRenderer.Transform.IDENTITY);
                        VectorRenderer.drawPlaneWorld(wOutA, wInB, wInA, outlineCol, true, 1, VectorRenderer.Transform.IDENTITY);
                    }

                    // 2) sclera (fill) - fan from centroid
                    int[] scleraCol = new int[]{COLOR_SCLERA, COLOR_SCLERA, COLOR_SCLERA};
                    for (int i = 0; i < n; i++) {
                        Vec3 a = local.get(i);
                        Vec3 b = local.get((i + 1) % n);
                        Vec3 wa = baseWorld.add(rotateLocalByQuat(a, quat)).add(biasSclera);
                        Vec3 wb = baseWorld.add(rotateLocalByQuat(b, quat)).add(biasSclera);
                        VectorRenderer.drawPlaneWorld(worldCentroid, wa, wb, scleraCol, true, 1, VectorRenderer.Transform.IDENTITY);
                    }

                    // 3) white pupil (small disk) - forward bias to avoid z-fight
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

                    // 4) small iris (black) that darts around inside pupil - most forward
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

                /* ---------- polygon & geometry helpers ---------- */

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

                // offset polygon by offsetting each edge by its normal then intersecting adjacent offset lines (robust)
                private static List<Vec3> offsetPolygonByEdgeIntersection(List<Vec3> poly, double amount) {
                    int n = poly.size();
                    List<Vec3> out = new ArrayList<>(n);
                    if (n < 3) return new ArrayList<>(poly);

                    // compute offset lines (p_i->q_i) for each original edge i = v_i->v_{i+1}
                    Vec3[] p = new Vec3[n];
                    Vec3[] q = new Vec3[n];
                    for (int i = 0; i < n; i++) {
                        Vec3 a = poly.get(i);
                        Vec3 b = poly.get((i + 1) % n);
                        double ex = b.x - a.x;
                        double ey = b.y - a.y;
                        // outward normal for CCW polygon: (-ey, ex)
                        double nx = -ey;
                        double ny = ex;
                        double len = Math.sqrt(nx*nx + ny*ny);
                        if (len <= 1e-9) { nx = 0; ny = 0; }
                        else { nx /= len; ny /= len; }
                        // offset by amount along (nx, ny)
                        p[i] = new Vec3(a.x + nx * amount, a.y + ny * amount, 0.0);
                        q[i] = new Vec3(b.x + nx * amount, b.y + ny * amount, 0.0);
                    }

                    // compute intersection of adjacent offset lines L_{i-1} and L_i (treat as infinite lines)
                    for (int i = 0; i < n; i++) {
                        int prev = (i - 1 + n) % n;
                        Vec3 A = p[prev];
                        Vec3 B = q[prev];
                        Vec3 C = p[i];
                        Vec3 D = q[i];

                        Vec3 inter = lineIntersect2D(A, B, C, D);
                        if (inter == null) {
                            // fallback: average offset points to avoid holes
                            Vec3 fallback = new Vec3((q[prev].x + p[i].x) * 0.5, (q[prev].y + p[i].y) * 0.5, 0.0);
                            out.add(fallback);
                        } else {
                            // clamp runaway intersections (prevent very long spikes) by limiting distance from original vertex
                            Vec3 orig = poly.get(i);
                            double dx = inter.x - orig.x;
                            double dy = inter.y - orig.y;
                            double dist = Math.sqrt(dx*dx + dy*dy);
                            double maxAllowed = Math.max( amount * 12.0,  (Math.abs(orig.x)+Math.abs(orig.y))*0.001 + amount * 8.0 );
                            if (dist > maxAllowed) {
                                // fallback to simple offset of the vertex by averaged normals
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

                // intersect two infinite 2D lines (A->B) and (C->D). returns null when parallel (no intersection)
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

                /* ---------- animation helpers ---------- */

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

                /* ---------- placement helpers ---------- */

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
                    BlockPos bp = new BlockPos((int) Math.floor(world.x), (int) Math.floor(world.y), (int) Math.floor(world.z));
                    BlockState s = mc.level.getBlockState(bp);
                    return !s.isAir() && !s.getCollisionShape(mc.level, bp).isEmpty();
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

                                inst.pupilOffset = new Vec3(0, 0, 0);
                                inst.pupilTargetOffset = new Vec3(0, 0, 0);
                                inst.pupilJitterTimer = 8 + RAND.nextInt(30);
                                inst.pupilCenterMaxOffset = Math.max(0.01, inst.width * 0.03);
                                inst.pupilMaxOffset = Math.max(0.03, Math.min(inst.width * 0.18, inst.height * 0.18));
                                inst.irisOffset = new Vec3(0, 0, 0);
                                inst.irisTarget = new Vec3(0, 0, 0);
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

                    // blink
                    boolean blinking = false;
                    int blinkTimer = 0;
                    int initialBlinkDuration = 8;
                    int cooldown = 30;

                    // pupil/iris anim
                    Vec3 pupilOffset = Vec3.ZERO;
                    Vec3 pupilTargetOffset = Vec3.ZERO;
                    int pupilJitterTimer = 0;
                    double pupilCenterMaxOffset = 0.02;
                    double pupilMaxOffset = 0.06;
            
                    Vec3 irisOffset = Vec3.ZERO;
                    Vec3 irisTarget = Vec3.ZERO;
                    int irisTimer = 0;
                }
            }
