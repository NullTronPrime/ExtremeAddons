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
 * Anatomically correct orbital eye renderer with full articulation
 * Features realistic eye structure with sclera, conjunctiva, cornea, iris, pupil, and lens
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public final class OrbitalEyeRenderer {
    private static final Map<Integer, OrbitalEyeData> EFFECTS = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();

    // Rendering constants
    private static final int CLIENT_TTL = 100;

    // Performance LOD distances
    private static final double LOD_DISTANCE_HIGH = 15.0;
    private static final double LOD_DISTANCE_MED = 30.0;
    private static final double LOD_DISTANCE_LOW = 60.0;

    // Animation timing
    private static final float PULSE_SPEED = 0.15f;
    private static final float LASER_PULSE_DURATION = 0.4f;
    private static final float BLINK_SPEED = 0.08f;
    private static final float SACCADE_SPEED = 0.12f;

    // Anatomical proportions (relative to eye radius)
    private static final float CORNEA_SIZE = 0.45f;
    private static final float PUPIL_MIN_SIZE = 0.15f;
    private static final float PUPIL_MAX_SIZE = 0.35f;
    private static final float IRIS_SIZE = 0.4f;
    private static final float CONJUNCTIVA_THICKNESS = 1.002f; // Slightly larger than sclera

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

            // Initialize anatomical animation values
            inst.pulseTimer = RAND.nextFloat();
            inst.blinkTimer = RAND.nextFloat() * 100.0f;
            inst.saccadeTimer = RAND.nextFloat();
            inst.pupilSize = PUPIL_MIN_SIZE + (PUPIL_MAX_SIZE - PUPIL_MIN_SIZE) * 0.5f;
            inst.targetLookDirection = inst.lookDirection != null ? inst.lookDirection : Vec3.ZERO;
            inst.currentLookDirection = inst.targetLookDirection;
            inst.bloodshotIntensity = 0.0f;
            inst.fatigueLevel = 0.0f;

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

        List<RenderableEye> renderableEyes = new ArrayList<>();

        // Process all eyes and build renderable list
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

            for (OrbitalEyeInstance eye : data.eyes) {
                updateAnatomicalAnimation(eye, partialTick);

                Vec3 eyeWorldPos = targetPos.add(eye.offset);
                double distanceToCamera = eyeWorldPos.distanceTo(cameraPos);

                if (distanceToCamera > LOD_DISTANCE_LOW) continue;

                int lodLevel = determineLODLevel(distanceToCamera);
                Quaternionf orientation = calculateEyeOrientation(eye, eyeWorldPos, cameraPos);

                renderableEyes.add(new RenderableEye(eye, eyeWorldPos, orientation, lodLevel, distanceToCamera));
            }
        }

        // Sort by distance (back to front for transparency)
        renderableEyes.sort(Comparator.comparingDouble(e -> -e.distanceToCamera));

        // Render eye components in anatomical order
        renderEyeComponents(renderableEyes, buffer, tesselator, poseMatrix, cameraPos);
        renderLaserEffects(renderableEyes);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderEyeComponents(List<RenderableEye> eyes, BufferBuilder buffer,
                                            Tesselator tesselator, Matrix4f poseMatrix, Vec3 cameraPos) {
        if (eyes.isEmpty()) return;

        // 1. Render sclera (eyeball base) - opaque
        RenderSystem.depthMask(true);
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (RenderableEye eye : eyes) {
            renderSclera(buffer, poseMatrix, eye, cameraPos);
        }
        tesselator.end();

        // 2. Render conjunctiva with blood vessels - semi-transparent
        RenderSystem.depthMask(false);
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (RenderableEye eye : eyes) {
            if (eye.lodLevel > 0) {
                renderConjunctiva(buffer, poseMatrix, eye, cameraPos);
            }
        }
        tesselator.end();

        // 3. Render iris (colored part) - opaque
        RenderSystem.depthMask(true);
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (RenderableEye eye : eyes) {
            if (eye.lodLevel > 0) {
                renderIris(buffer, poseMatrix, eye, cameraPos);
            }
        }
        tesselator.end();

        // 4. Render pupil (black center) - opaque
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (RenderableEye eye : eyes) {
            if (eye.lodLevel > 0) {
                renderPupil(buffer, poseMatrix, eye, cameraPos);
            }
        }
        tesselator.end();

        // 5. Render cornea (transparent protective layer)
        RenderSystem.depthMask(false);
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (RenderableEye eye : eyes) {
            if (eye.lodLevel > 1) {
                renderCornea(buffer, poseMatrix, eye, cameraPos);
            }
        }
        tesselator.end();

        // 6. Render lens highlights and reflections
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (RenderableEye eye : eyes) {
            if (eye.lodLevel > 1) {
                renderLensHighlights(buffer, poseMatrix, eye, cameraPos);
            }
        }
        tesselator.end();

        // 7. Render eyelid shadow when blinking
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (RenderableEye eye : eyes) {
            if (eye.eye.isBlinking && eye.lodLevel > 0) {
                renderEyelidShadow(buffer, poseMatrix, eye, cameraPos);
            }
        }
        tesselator.end();

        RenderSystem.depthMask(true);
    }

    private static void renderSclera(BufferBuilder buffer, Matrix4f poseMatrix, RenderableEye renderableEye, Vec3 cameraPos) {
        OrbitalEyeInstance eye = renderableEye.eye;
        Vec3 eyePos = renderableEye.worldPos;

        float radius = eye.radius * getBlinkScale(eye) * getPulseScale(eye);

        // Add bloodshot effects to sclera color
        int baseColor = eye.scleraColor;
        if (eye.bloodshotIntensity > 0.0f) {
            baseColor = blendColors(baseColor, 0xFFFF6666, eye.bloodshotIntensity * 0.3f);
        }

        renderSphere(buffer, poseMatrix, eyePos, radius, baseColor, renderableEye.orientation,
                cameraPos, 0.0f, renderableEye.lodLevel);
    }

    private static void renderConjunctiva(BufferBuilder buffer, Matrix4f poseMatrix, RenderableEye renderableEye, Vec3 cameraPos) {
        OrbitalEyeInstance eye = renderableEye.eye;
        Vec3 eyePos = renderableEye.worldPos;

        float radius = eye.radius * getBlinkScale(eye) * getPulseScale(eye) * CONJUNCTIVA_THICKNESS;

        // Semi-transparent with blood vessel coloring
        int conjunctivaColor = 0x30FFFFFF; // Transparent white base
        if (eye.bloodshotIntensity > 0.0f) {
            int redTint = (int) (255 * eye.bloodshotIntensity * 0.5f);
            conjunctivaColor = (0x30 << 24) | (redTint << 16) | (0x88 << 8) | 0x88;
        }

        renderSphere(buffer, poseMatrix, eyePos, radius, conjunctivaColor, renderableEye.orientation,
                cameraPos, 0.001f, Math.max(0, renderableEye.lodLevel - 1));
    }

    private static void renderIris(BufferBuilder buffer, Matrix4f poseMatrix, RenderableEye renderableEye, Vec3 cameraPos) {
        OrbitalEyeInstance eye = renderableEye.eye;
        Vec3 eyePos = renderableEye.worldPos;

        float eyeRadius = eye.radius * getBlinkScale(eye) * getPulseScale(eye);
        Vec3 lookOffset = calculateLookOffset(eye, renderableEye.orientation, eyeRadius * 0.15f);
        Vec3 irisPos = eyePos.add(lookOffset);

        float irisRadius = eyeRadius * IRIS_SIZE;

        // Add pulsing effect to iris color when firing
        int irisColor = eye.irisColor;
        if (eye.isPulsing) {
            irisColor = blendColors(irisColor, eye.laserColor, eye.pulseIntensity * 0.4f);
        }

        // Position iris slightly forward
        Vec3 forwardOffset = getForwardVector(renderableEye.orientation).scale(0.003);
        Vec3 finalIrisPos = irisPos.add(forwardOffset);

        renderFlatDisc(buffer, poseMatrix, finalIrisPos, irisRadius, irisColor,
                renderableEye.orientation, cameraPos, 0.003f, renderableEye.lodLevel);
    }

    private static void renderPupil(BufferBuilder buffer, Matrix4f poseMatrix, RenderableEye renderableEye, Vec3 cameraPos) {
        OrbitalEyeInstance eye = renderableEye.eye;
        Vec3 eyePos = renderableEye.worldPos;

        float eyeRadius = eye.radius * getBlinkScale(eye) * getPulseScale(eye);
        Vec3 lookOffset = calculateLookOffset(eye, renderableEye.orientation, eyeRadius * 0.15f);
        Vec3 pupilPos = eyePos.add(lookOffset);

        float pupilRadius = eyeRadius * eye.pupilSize;

        // Pupil can glow when firing lasers
        int pupilColor = eye.pupilColor;
        if (eye.firing && eye.isPulsing) {
            pupilColor = blendColors(pupilColor, eye.laserColor, eye.pulseIntensity * 0.6f);
        }

        // Position pupil more forward than iris
        Vec3 forwardOffset = getForwardVector(renderableEye.orientation).scale(0.004);
        Vec3 finalPupilPos = pupilPos.add(forwardOffset);

        renderFlatDisc(buffer, poseMatrix, finalPupilPos, pupilRadius, pupilColor,
                renderableEye.orientation, cameraPos, 0.004f, renderableEye.lodLevel);
    }

    private static void renderCornea(BufferBuilder buffer, Matrix4f poseMatrix, RenderableEye renderableEye, Vec3 cameraPos) {
        OrbitalEyeInstance eye = renderableEye.eye;
        Vec3 eyePos = renderableEye.worldPos;

        float eyeRadius = eye.radius * getBlinkScale(eye) * getPulseScale(eye);
        float corneaRadius = eyeRadius * CORNEA_SIZE;

        // Position cornea at front of eye
        Vec3 forwardOffset = getForwardVector(renderableEye.orientation).scale(eyeRadius * 0.1);
        Vec3 corneaPos = eyePos.add(forwardOffset);

        // Transparent dome effect
        int corneaColor = 0x15FFFFFF;

        renderDome(buffer, poseMatrix, corneaPos, corneaRadius, corneaColor,
                renderableEye.orientation, cameraPos, 0.005f, renderableEye.lodLevel);
    }

    private static void renderLensHighlights(BufferBuilder buffer, Matrix4f poseMatrix, RenderableEye renderableEye, Vec3 cameraPos) {
        OrbitalEyeInstance eye = renderableEye.eye;
        Vec3 eyePos = renderableEye.worldPos;

        float eyeRadius = eye.radius * getBlinkScale(eye) * getPulseScale(eye);

        // Calculate light reflection
        Vec3 lightDir = new Vec3(0.3, 0.7, 0.6).normalize();
        Vec3 viewDir = cameraPos.subtract(eyePos).normalize();
        Vec3 reflectDir = reflect(lightDir, getForwardVector(renderableEye.orientation));

        float reflectIntensity = (float) Math.max(0.0, viewDir.dot(reflectDir));
        if (reflectIntensity > 0.3f) {
            float highlightRadius = eyeRadius * 0.08f;
            Vec3 highlightOffset = reflectDir.scale(eyeRadius * 0.05);
            Vec3 highlightPos = eyePos.add(highlightOffset);

            int alpha = (int) (255 * (reflectIntensity - 0.3f) / 0.7f);
            int highlightColor = (alpha << 24) | 0xFFFFFF;

            renderSphere(buffer, poseMatrix, highlightPos, highlightRadius, highlightColor,
                    renderableEye.orientation, cameraPos, 0.006f, 2);
        }
    }

    private static void renderEyelidShadow(BufferBuilder buffer, Matrix4f poseMatrix, RenderableEye renderableEye, Vec3 cameraPos) {
        OrbitalEyeInstance eye = renderableEye.eye;
        if (eye.blinkPhase < 0.1f) return;

        Vec3 eyePos = renderableEye.worldPos;
        float eyeRadius = eye.radius * getPulseScale(eye);

        float shadowIntensity = eye.blinkPhase;
        float shadowRadius = eyeRadius * (1.0f + shadowIntensity * 0.3f);

        int shadowColor = (int) (100 * shadowIntensity) << 24;

        renderSphere(buffer, poseMatrix, eyePos, shadowRadius, shadowColor,
                renderableEye.orientation, cameraPos, 0.007f, Math.max(0, renderableEye.lodLevel - 1));
    }

    // Core rendering primitives using VectorRenderer
    private static void renderSphere(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 center, float radius,
                                     int color, Quaternionf orientation, Vec3 cameraPos, float depthBias, int lodLevel) {
        int segments = getLODSegments(lodLevel);

        // Use VectorRenderer for sphere rendering
        VectorRenderer.drawSphereWorld(center, radius, color, segments, segments * 2,
                false, 1, VectorRenderer.Transform.IDENTITY);
    }

    private static void renderFlatDisc(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 center, float radius,
                                       int color, Quaternionf orientation, Vec3 cameraPos, float depthBias, int lodLevel) {
        int segments = getLODSegments(lodLevel);

        // Create disc as flat polygon
        List<Vec3> discPoints = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Transform by orientation
            Vector3f localPoint = new Vector3f((float) x, 0, (float) z);
            orientation.transform(localPoint);

            Vec3 worldPoint = center.add(localPoint.x(), localPoint.y(), localPoint.z());
            discPoints.add(worldPoint);
        }

        VectorRenderer.drawFilledPolygonWorld(discPoints, color, false, 1, VectorRenderer.Transform.IDENTITY);
    }

    private static void renderDome(BufferBuilder buffer, Matrix4f poseMatrix, Vec3 center, float radius,
                                   int color, Quaternionf orientation, Vec3 cameraPos, float depthBias, int lodLevel) {
        // Render as partial sphere (dome)
        int segments = getLODSegments(lodLevel);
        VectorRenderer.drawSphereWorld(center, radius, color, segments / 2, segments,
                false, 1, VectorRenderer.Transform.IDENTITY);
    }

    private static void renderLaserEffects(List<RenderableEye> eyes) {
        for (RenderableEye renderableEye : eyes) {
            OrbitalEyeInstance eye = renderableEye.eye;
            if (eye.firing && eye.laserEnd != null && eye.isPulsing && eye.pulseIntensity > 0.1f) {
                float beamThickness = 0.03f + eye.pulseIntensity * 0.1f;
                int beamColor = modulateColorAlpha(eye.laserColor, eye.pulseIntensity);

                VectorRenderer.drawLineWorld(renderableEye.worldPos, eye.laserEnd, beamColor,
                        beamThickness, false, 1, VectorRenderer.Transform.IDENTITY);

                // Add pulse effect at laser end
                if (eye.pulseIntensity > 0.5f) {
                    float pulseRadius = 0.15f + eye.pulseIntensity * 0.25f;
                    int pulseColor = modulateColorAlpha(eye.laserColor, eye.pulseIntensity * 0.8f);

                    VectorRenderer.drawSphereWorld(eye.laserEnd, pulseRadius, pulseColor, 12, 16,
                            false, 1, VectorRenderer.Transform.IDENTITY);
                }
            }
        }
    }

    // Animation and state management
    private static void updateAnatomicalAnimation(OrbitalEyeInstance eye, float partialTick) {
        // Update pulse animation
        eye.pulseTimer += PULSE_SPEED * partialTick;
        if (eye.pulseTimer > 1.0f) eye.pulseTimer -= 1.0f;

        // Update laser pulse
        if (eye.firing) {
            float cyclePos = eye.pulseTimer;
            if (cyclePos < LASER_PULSE_DURATION) {
                float pulsePos = cyclePos / LASER_PULSE_DURATION;
                eye.pulseIntensity = (float) (Math.sin(pulsePos * Math.PI) * Math.exp(-pulsePos * 2));
            } else {
                eye.pulseIntensity = 0.0f;
            }
            eye.isPulsing = eye.pulseIntensity > 0.1f;

            // Pupil dilates when firing
            eye.pupilSize = PUPIL_MIN_SIZE + (PUPIL_MAX_SIZE - PUPIL_MIN_SIZE) * (0.3f + eye.pulseIntensity * 0.6f);
        } else {
            eye.pulseIntensity = 0.0f;
            eye.isPulsing = false;
            eye.pupilSize = PUPIL_MIN_SIZE + (PUPIL_MAX_SIZE - PUPIL_MIN_SIZE) * 0.4f;
        }

        // Update blink animation
        if (!eye.isBlinking) {
            eye.blinkTimer += partialTick;
            if (eye.blinkTimer > 60 + RAND.nextFloat() * 120) {
                eye.isBlinking = true;
                eye.blinkTimer = 0;
                eye.blinkPhase = 0;
            }
        } else {
            eye.blinkTimer += BLINK_SPEED * partialTick;
            if (eye.blinkTimer < 1.0f) {
                eye.blinkPhase = (float) Math.sin(eye.blinkTimer * Math.PI);
            } else {
                eye.isBlinking = false;
                eye.blinkPhase = 0;
                eye.blinkTimer = 0;
            }
        }

        // Update saccade movement
        eye.saccadeTimer += SACCADE_SPEED * partialTick;
        if (eye.saccadeTimer > 1.0f) {
            eye.saccadeTimer = 0;
            if (eye.targetLookDirection != null) {
                eye.currentLookDirection = eye.currentLookDirection.lerp(eye.targetLookDirection, 0.1f);
            }
        }

        // Update bloodshot/fatigue effects
        if (eye.firing) {
            eye.fatigueLevel = Math.min(1.0f, eye.fatigueLevel + 0.01f * partialTick);
            eye.bloodshotIntensity = Math.min(0.8f, eye.bloodshotIntensity + 0.02f * partialTick);
        } else {
            eye.fatigueLevel = Math.max(0.0f, eye.fatigueLevel - 0.005f * partialTick);
            eye.bloodshotIntensity = Math.max(0.0f, eye.bloodshotIntensity - 0.01f * partialTick);
        }
    }

    // Helper methods
    private static Entity getTargetEntity(Minecraft mc, OrbitalEyeData data) {
        Entity target = mc.level.getEntity(data.targetId);
        if (data.isEntity) {
            return (target instanceof LivingEntity || target instanceof ItemEntity) ? target : null;
        } else {
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
            y += 0.3;
        }

        return new Vec3(x, y, z);
    }

    private static int determineLODLevel(double distance) {
        if (distance < LOD_DISTANCE_HIGH) return 2;
        else if (distance < LOD_DISTANCE_MED) return 1;
        else return 0;
    }

    private static int getLODSegments(int lodLevel) {
        switch (lodLevel) {
            case 2:
                return 20; // High detail
            case 1:
                return 16; // Medium detail
            default:
                return 12; // Low detail
        }
    }

    private static Quaternionf calculateEyeOrientation(OrbitalEyeInstance eye, Vec3 eyePos, Vec3 cameraPos) {
        Vec3 lookDir;
        if (eye.currentLookDirection != null && eye.currentLookDirection.length() > 1e-6) {
            lookDir = eye.currentLookDirection.normalize();
        } else {
            lookDir = cameraPos.subtract(eyePos).normalize();
        }

        Vector3f forward = new Vector3f(0, 0, 1);
        Vector3f target = new Vector3f((float) lookDir.x, (float) lookDir.y, (float) lookDir.z);
        return new Quaternionf().rotationTo(forward, target);
    }

    private static float getBlinkScale(OrbitalEyeInstance eye) {
        return 1.0f - eye.blinkPhase * 0.7f;
    }

    private static float getPulseScale(OrbitalEyeInstance eye) {
        return eye.isPulsing ? 1.0f + eye.pulseIntensity * 0.15f : 1.0f;
    }

    private static Vec3 calculateLookOffset(OrbitalEyeInstance eye, Quaternionf orientation, float maxOffset) {
        if (eye.currentLookDirection == null || eye.currentLookDirection.length() < 1e-6) {
            return Vec3.ZERO;
        }

        Vec3 lookDir = eye.currentLookDirection.normalize();
        Vec3 forward = getForwardVector(orientation);

        double dotProduct = lookDir.dot(forward);
        if (Math.abs(dotProduct) > 0.95) return Vec3.ZERO;

        Vec3 offset = lookDir.subtract(forward.scale(dotProduct)).normalize().scale(maxOffset);
        return offset;
    }

    private static Vec3 getForwardVector(Quaternionf orientation) {
        Vector3f forward = new Vector3f(0, 0, 1);
        orientation.transform(forward);
        return new Vec3(forward.x(), forward.y(), forward.z());
    }

    private static Vec3 reflect(Vec3 incident, Vec3 normal) {
        return incident.subtract(normal.scale(2.0 * incident.dot(normal)));
    }

    private static int blendColors(int color1, int color2, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));

        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor), r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor), b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int modulateColorAlpha(int color, float intensity) {
        int alpha = (int) (((color >> 24) & 0xFF) * intensity);
        return (color & 0x00FFFFFF) | (alpha << 24);
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
        // Basic properties
        Vec3 offset = Vec3.ZERO;
        float radius = 0.5f;
        int scleraColor = 0xFF222222;
        int pupilColor = 0xFFFFFFFF;
        int irisColor = 0xFF000000;
        int laserColor = 0xFFFF3333;

        // State flags
        boolean firing = false;
        boolean isBlinking = false;
        boolean isPulsing = false;

        // Animation values
        float blinkPhase = 0.0f;
        float pulseIntensity = 0.0f;
        float pupilSize = 0.25f;

        // Direction vectors
        Vec3 laserEnd = null;
        Vec3 lookDirection = null;
        Vec3 targetLookDirection = null;
        Vec3 currentLookDirection = null;

        // Animation timers
        float pulseTimer = 0.0f;
        float blinkTimer = 0.0f;
        float saccadeTimer = 0.0f;

        // Anatomical effects
        float bloodshotIntensity = 0.0f;
        float fatigueLevel = 0.0f;
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
    }
}