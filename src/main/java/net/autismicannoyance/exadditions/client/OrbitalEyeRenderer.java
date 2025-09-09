package net.autismicannoyance.exadditions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.autismicannoyance.exadditions.ExAdditions;
import net.autismicannoyance.exadditions.network.OrbitalEyeRenderPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
 * Spherical orbital eye renderer with customizable colors and pulse lasers
 * Supports attachment to both entities and items
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class OrbitalEyeRenderer {
    private static final Map<Integer, OrbitalEyeData> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // Rendering constants for spherical eyes
    private static final int SPHERE_LAT_SEGMENTS = 16;
    private static final int SPHERE_LON_SEGMENTS = 20;
    private static final int CLIENT_TTL = 40;

    // Performance settings
    private static final double LOD_DISTANCE_HIGH = 12.0;
    private static final double LOD_DISTANCE_MED = 24.0;
    private static final double LOD_DISTANCE_LOW = 48.0;

    // Animation settings
    private static final float PULSE_SPEED = 0.1f;
    private static final float LASER_PULSE_DURATION = 0.3f; // 30% of firing cycle

    // Pre-calculated sphere geometry at different LOD levels
    private static List<Vec3> HIGH_DETAIL_SPHERE;
    private static List<Vec3> MED_DETAIL_SPHERE;
    private static List<Vec3> LOW_DETAIL_SPHERE;

    static {
        HIGH_DETAIL_SPHERE = generateSphereVertices(16, 20);
        MED_DETAIL_SPHERE = generateSphereVertices(12, 16);
        LOW_DETAIL_SPHERE = generateSphereVertices(8, 12);
    }

    public static void handlePacket(OrbitalEyeRenderPacket msg) {
        int targetId = msg.targetId;
        boolean isEntity = msg.isEntity;
        List<OrbitalEyeRenderPacket.OrbitalEyeEntry> entries =
                msg.eyes == null ? Collections.emptyList() : msg.eyes;

        OrbitalEyeData data = new OrbitalEyeData(targetId, isEntity, CLIENT_TTL);
        data.eyes.clear();

        for (OrbitalEyeRenderPacket.OrbitalEyeEntry e : entries) {
            OrbitalEyeInstance inst = new OrbitalEyeInstance();
            inst.offset = e.offset;
            inst.radius = e.radius;
            inst.scleraColor = e.scleraColor;
            inst.pupilColor = e.pupilColor;
            inst.irisColor = e.irisColor;
            inst.firing = e.firing;
            inst.isBlinking = e.isBlinking;
            inst.blinkPhase = e.blinkPhase;
            inst.laserEnd = e.laserEnd;
            inst.lookDirection = e.lookDirection;
            inst.isPulsing = e.isPulsing;
            inst.pulseIntensity = e.pulseIntensity;
            inst.laserColor = e.laserColor;

            // Initialize animation values
            inst.pulseTimer = RAND.nextFloat();
            inst.rotationOffset = RAND.nextFloat() * (float)(Math.PI * 2);

            data.eyes.add(inst);
        }

        EFFECTS.put(targetId, data);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || EFFECTS.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float partialTick = event.getPartialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f poseMatrix = event.getPoseStack().last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // Separate opaque and transparent eyes
        List<RenderableEye> opaqueEyes = new ArrayList<>();
        List<RenderableEye> transparentEyes = new ArrayList<>();

        Iterator<Map.Entry<Integer, OrbitalEyeData>> it = EFFECTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, OrbitalEyeData> entry = it.next();
            OrbitalEyeData data = entry.getValue();

            if (data.lifetime >= 0 && data.age++ >= data.lifetime) {
                it.remove();
                continue;
            }

            Entity target = getTargetEntity(mc, data);
            if (target == null) {
                it.remove();
                continue;
            }

            Vec3 targetPos = getTargetPosition(target, partialTick);
            double distanceToTarget = targetPos.distanceTo(cameraPos);

            for (OrbitalEyeInstance eye : data.eyes) {
                updateEyeAnimation(eye, partialTick);

                Vec3 eyeWorldPos = targetPos.add(eye.offset);
                double distanceToCamera = eyeWorldPos.distanceTo(cameraPos);

                // Skip very distant eyes
                if (distanceToCamera > LOD_DISTANCE_LOW) continue;

                int lodLevel = determineLODLevel(distanceToCamera);
                Quaternionf orientation = calculateEyeOrientation(eye, eyeWorldPos, cameraPos);

                RenderableEye renderableEye = new RenderableEye(eye, eyeWorldPos, orientation,
                        lodLevel, distanceToCamera);

                // Separate by transparency
                float alpha = getEyeAlpha(eye);
                if (alpha >= 0.95f) {
                    opaqueEyes.add(renderableEye);
                } else {
                    transparentEyes.add(renderableEye);
                }
            }
        }

        // Render opaque eyes first (front-to-back)
        opaqueEyes.sort(Comparator.comparingDouble(e -> e.distanceToCamera));
        if (!opaqueEyes.isEmpty()) {
            RenderSystem.depthMask(true);
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (RenderableEye eye : opaqueEyes) {
                renderSphericalEye(buffer, poseMatrix, eye, cameraPos);
            }
            tesselator.end();
        }

        // Render transparent eyes (back-to-front)
        transparentEyes.sort(Comparator.comparingDouble(e -> -e.distanceToCamera));
        if (!transparentEyes.isEmpty()) {
            RenderSystem.depthMask(false);
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (RenderableEye eye : transparentEyes) {
                renderSphericalEye(buffer, poseMatrix, eye, cameraPos);
            }
            tesselator.end();
            RenderSystem.depthMask(true);
        }

        // Render laser pulses separately
        renderLaserPulses(opaqueEyes, transparentEyes, poseMatrix, cameraPos);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderSphericalEye(BufferBuilder buffer, Matrix4f poseMatrix, RenderableEye renderableEye, Vec3 cameraPos) {
        OrbitalEyeInstance eye = renderableEye.eye;
        Vec3 eyePos = renderableEye.worldPos;
        Quaternionf orientation = renderableEye.orientation;
        int lodLevel = renderableEye.lodLevel;

        float radius = eye.radius;
        float blinkScale = 1.0f - eye.blinkPhase * 0.8f; // Eyes shrink when blinking
        float pulseScale = eye.isPulsing ? 1.0f + eye.pulseIntensity * 0.2f : 1.0f;
        float finalRadius = radius * blinkScale * pulseScale;

        List<Vec3> sphereVertices = getSphereForLOD(lodLevel);

        // Render sclera (eye white)
        renderSpherePart(buffer, poseMatrix, sphereVertices, eyePos, finalRadius,
                orientation, eye.scleraColor, cameraPos, 0.0f);

        // Render pupil (smaller sphere, offset towards look direction)
        if (lodLevel > 0) { // Skip pupil at lowest LOD
            float pupilRadius = finalRadius * 0.4f;
            Vec3 pupilOffset = calculatePupilOffset(eye, orientation, finalRadius * 0.3f);
            Vec3 pupilPos = eyePos.add(pupilOffset);

            renderSpherePart(buffer, poseMatrix, sphereVertices, pupilPos, pupilRadius,
                    orientation, eye.pupilColor, cameraPos, 0.001f);

            // Render iris (even smaller, same position as pupil but slightly forward)
            if (lodLevel > 1) { // Skip iris at medium and low LOD
                float irisRadius = pupilRadius * 0.3f;
                Vec3 irisPos = pupilPos.add(getForwardVector(orientation).scale(0.002));

                renderSpherePart(buffer, poseMatrix, getSphereForLOD(2), irisPos, irisRadius,
                        orientation, eye.irisColor, cameraPos, 0.002f);
            }
        }
    }

    private static void renderSpherePart(BufferBuilder buffer, Matrix4f poseMatrix, List<Vec3> sphereVertices,
                                         Vec3 center, float radius, Quaternionf orientation, int color,
                                         Vec3 cameraPos, float depthBias) {
        float[] rgba = unpackColor(color);
        Vec3 biasedCenter = center.add(getForwardVector(orientation).scale(depthBias));
        Vec3 relativeCenter = biasedCenter.subtract(cameraPos);

        for (int i = 0; i < sphereVertices.size(); i += 3) {
            Vec3 v1 = transformSphereVertex(sphereVertices.get(i), biasedCenter, radius, orientation, cameraPos);
            Vec3 v2 = transformSphereVertex(sphereVertices.get(i + 1), biasedCenter, radius, orientation, cameraPos);
            Vec3 v3 = transformSphereVertex(sphereVertices.get(i + 2), biasedCenter, radius, orientation, cameraPos);

            addTriangle(buffer, poseMatrix, v1, v2, v3, rgba);
        }
    }

    private static void renderLaserPulses(List<RenderableEye> opaqueEyes, List<RenderableEye> transparentEyes,
                                          Matrix4f poseMatrix, Vec3 cameraPos) {
        List<RenderableEye> allEyes = new ArrayList<>();
        allEyes.addAll(opaqueEyes);
        allEyes.addAll(transparentEyes);

        for (RenderableEye renderableEye : allEyes) {
            OrbitalEyeInstance eye = renderableEye.eye;
            if (eye.firing && eye.laserEnd != null && eye.isPulsing && eye.pulseIntensity > 0.1f) {
                float beamThickness = 0.02f + eye.pulseIntensity * 0.08f;
                int beamColor = modulateColorAlpha(eye.laserColor, eye.pulseIntensity);

                VectorRenderer.drawLineWorld(
                        renderableEye.worldPos,
                        eye.laserEnd,
                        beamColor,
                        beamThickness,
                        false,
                        1,
                        VectorRenderer.Transform.IDENTITY
                );

                // Add pulse effect at laser end
                if (eye.pulseIntensity > 0.5f) {
                    float pulseRadius = 0.1f + eye.pulseIntensity * 0.2f;
                    int pulseColor = modulateColorAlpha(eye.laserColor, eye.pulseIntensity * 0.7f);

                    VectorRenderer.drawSphereWorld(
                            eye.laserEnd,
                            pulseRadius,
                            pulseColor,
                            8, 10,
                            false,
                            1,
                            VectorRenderer.Transform.IDENTITY
                    );
                }
            }
        }
    }

    // Helper methods

    private static Entity getTargetEntity(Minecraft mc, OrbitalEyeData data) {
        Entity target = mc.level.getEntity(data.targetId);
        if (data.isEntity) {
            return (target instanceof LivingEntity || target instanceof ItemEntity) ? target : null;
        } else {
            // For item-based effects, look for ItemEntity
            return target instanceof ItemEntity ? target : null;
        }
    }

    private static Vec3 getTargetPosition(Entity target, float partialTick) {
        double x = Mth.lerp(partialTick, target.xOld, target.getX());
        double y = Mth.lerp(partialTick, target.yOld, target.getY());
        double z = Mth.lerp(partialTick, target.zOld, target.getZ());

        if (target instanceof LivingEntity living) {
            y += living.getBbHeight() * 0.5;
        } else if (target instanceof ItemEntity) {
            y += 0.3; // Slightly above item
        }

        return new Vec3(x, y, z);
    }

    private static void updateEyeAnimation(OrbitalEyeInstance eye, float partialTick) {
        // Update pulse animation
        eye.pulseTimer += PULSE_SPEED * partialTick;
        if (eye.pulseTimer > 1.0f) eye.pulseTimer -= 1.0f;

        if (eye.firing) {
            // Pulse intensity follows a sharp spike pattern for short laser pulses
            float cyclePos = eye.pulseTimer;
            if (cyclePos < LASER_PULSE_DURATION) {
                float pulsePos = cyclePos / LASER_PULSE_DURATION;
                // Sharp attack, quick decay
                eye.pulseIntensity = (float)(Math.sin(pulsePos * Math.PI) * Math.exp(-pulsePos * 3));
            } else {
                eye.pulseIntensity = 0.0f;
            }
            eye.isPulsing = eye.pulseIntensity > 0.1f;
        } else {
            eye.pulseIntensity = 0.0f;
            eye.isPulsing = false;
        }
    }

    private static int determineLODLevel(double distance) {
        if (distance < LOD_DISTANCE_HIGH) return 2; // High detail
        else if (distance < LOD_DISTANCE_MED) return 1; // Medium detail
        else return 0; // Low detail
    }

    private static Quaternionf calculateEyeOrientation(OrbitalEyeInstance eye, Vec3 eyePos, Vec3 cameraPos) {
        Vec3 lookDir;
        if (eye.lookDirection != null && eye.lookDirection.length() > 1e-6) {
            lookDir = eye.lookDirection.normalize();
        } else {
            lookDir = cameraPos.subtract(eyePos).normalize();
        }

        Vector3f forward = new Vector3f(0, 0, 1);
        Vector3f target = new Vector3f((float)lookDir.x, (float)lookDir.y, (float)lookDir.z);
        return new Quaternionf().rotationTo(forward, target);
    }

    private static float getEyeAlpha(OrbitalEyeInstance eye) {
        int scleraAlpha = (eye.scleraColor >> 24) & 0xFF;
        return scleraAlpha / 255.0f;
    }

    private static List<Vec3> getSphereForLOD(int lodLevel) {
        switch (lodLevel) {
            case 2: return HIGH_DETAIL_SPHERE;
            case 1: return MED_DETAIL_SPHERE;
            default: return LOW_DETAIL_SPHERE;
        }
    }

    private static Vec3 calculatePupilOffset(OrbitalEyeInstance eye, Quaternionf orientation, float maxOffset) {
        if (eye.lookDirection == null) return Vec3.ZERO;

        // Project look direction onto eye surface
        Vec3 lookDir = eye.lookDirection.normalize();
        Vec3 forward = getForwardVector(orientation);

        // Calculate offset in eye's local space
        double dotProduct = lookDir.dot(forward);
        if (Math.abs(dotProduct) > 0.9) return Vec3.ZERO; // Looking directly forward/backward

        Vec3 offset = lookDir.subtract(forward.scale(dotProduct)).normalize().scale(maxOffset);
        return offset;
    }

    private static Vec3 getForwardVector(Quaternionf orientation) {
        Vector3f forward = new Vector3f(0, 0, 1);
        orientation.transform(forward);
        return new Vec3(forward.x(), forward.y(), forward.z());
    }

    private static Vec3 transformSphereVertex(Vec3 vertex, Vec3 center, float radius,
                                              Quaternionf orientation, Vec3 cameraPos) {
        // Scale vertex
        Vec3 scaled = vertex.scale(radius);

        // Apply orientation
        Vector3f vec = new Vector3f((float)scaled.x, (float)scaled.y, (float)scaled.z);
        orientation.transform(vec);

        // Translate to world position and make relative to camera
        Vec3 worldPos = center.add(vec.x(), vec.y(), vec.z());
        return worldPos.subtract(cameraPos);
    }

    private static int modulateColorAlpha(int color, float intensity) {
        int alpha = (int)(((color >> 24) & 0xFF) * intensity);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static List<Vec3> generateSphereVertices(int latSegments, int lonSegments) {
        List<Vec3> vertices = new ArrayList<>();

        for (int lat = 0; lat < latSegments; lat++) {
            double theta1 = Math.PI * lat / latSegments;
            double theta2 = Math.PI * (lat + 1) / latSegments;

            for (int lon = 0; lon < lonSegments; lon++) {
                double phi1 = 2.0 * Math.PI * lon / lonSegments;
                double phi2 = 2.0 * Math.PI * (lon + 1) / lonSegments;

                // Generate two triangles for each quad
                Vec3 v1 = sphericalToCartesian(theta1, phi1);
                Vec3 v2 = sphericalToCartesian(theta1, phi2);
                Vec3 v3 = sphericalToCartesian(theta2, phi1);
                Vec3 v4 = sphericalToCartesian(theta2, phi2);

                // First triangle
                vertices.add(v1);
                vertices.add(v3);
                vertices.add(v2);

                // Second triangle
                vertices.add(v2);
                vertices.add(v3);
                vertices.add(v4);
            }
        }

        return vertices;
    }

    private static Vec3 sphericalToCartesian(double theta, double phi) {
        double x = Math.sin(theta) * Math.cos(phi);
        double y = Math.cos(theta);
        double z = Math.sin(theta) * Math.sin(phi);
        return new Vec3(x, y, z);
    }

    private static float[] unpackColor(int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    private static void addTriangle(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 a, Vec3 b, Vec3 c, float[] rgba) {
        buffer.vertex(poseMatrix, (float) a.x, (float) a.y, (float) a.z)
                .color(rgba[0], rgba[1], rgba[2], rgba[3])
                .endVertex();
        buffer.vertex(poseMatrix, (float) b.x, (float) b.y, (float) b.z)
                .color(rgba[0], rgba[1], rgba[2], rgba[3])
                .endVertex();
        buffer.vertex(poseMatrix, (float) c.x, (float) c.y, (float) c.z)
                .color(rgba[0], rgba[1], rgba[2], rgba[3])
                .endVertex();
    }

    // Data classes

    private static final class OrbitalEyeData {
        final int targetId;
        final boolean isEntity;
        final int lifetime;
        int age = 0;
        final List<OrbitalEyeInstance> eyes = new ArrayList<>();

        OrbitalEyeData(int targetId, boolean isEntity, int lifetime) {
            this.targetId = targetId;
            this.isEntity = isEntity;
            this.lifetime = lifetime;
        }
    }

    private static final class OrbitalEyeInstance {
        Vec3 offset = Vec3.ZERO;
        float radius = 0.5f;
        int scleraColor = 0xFF222222;
        int pupilColor = 0xFFFFFFFF;
        int irisColor = 0xFF000000;
        boolean firing = false;
        boolean isBlinking = false;
        float blinkPhase = 0.0f;
        Vec3 laserEnd = null;
        Vec3 lookDirection = null;
        boolean isPulsing = false;
        float pulseIntensity = 0.0f;
        int laserColor = 0xFFFF3333;

        // Animation state
        float pulseTimer = 0.0f;
        float rotationOffset = 0.0f;
    }

    private static final class RenderableEye {
        final OrbitalEyeInstance eye;
        final Vec3 worldPos;
        final Quaternionf orientation;
        final int lodLevel;
        final double distanceToCamera;

        RenderableEye(OrbitalEyeInstance eye, Vec3 worldPos, Quaternionf orientation,
                      int lodLevel, double distanceToCamera) {
            this.eye = eye;
            this.worldPos = worldPos;
            this.orientation = orientation;
            this.lodLevel = lodLevel;
            this.distanceToCamera = distanceToCamera;
        }
    }}