package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class FlameJetRenderer {

    private static final Random RANDOM = new Random();

    public static void renderFlameJet(Vec3 start, Vec3 end, double width, int coreColor, int outerColor, int duration) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        Vec3 normalizedDir = direction.normalize();

        long time = System.currentTimeMillis();
        float animTime = (time % 3000) / 3000.0f;

        renderFlameJetFlow(start, normalizedDir, length, width, coreColor, outerColor, duration, animTime);
        renderFlameJetParticles(start, normalizedDir, length, width, coreColor, outerColor, duration, animTime);
    }

    public static void renderFlame(Vec3 origin, double height, double baseRadius, int coreColor, int outerColor, int duration) {
        renderFlame(origin, height, baseRadius, coreColor, outerColor, duration, null, 1.0f);
    }

    public static void renderFlame(Vec3 origin, double height, double baseRadius, int coreColor, int outerColor, int duration, Quaternionf rotation, float intensity) {
        long time = System.currentTimeMillis();
        float animTime = (time % 4000) / 4000.0f;

        VectorRenderer.Transform transform = null;
        if (rotation != null) {
            transform = VectorRenderer.Transform.fromQuaternion(Vec3.ZERO, rotation, 1.0f, origin);
        }

        renderFlameFlow(origin, height, baseRadius, coreColor, outerColor, duration, transform, animTime, intensity);
        renderFlameParticles(origin, height, baseRadius, coreColor, outerColor, duration, transform, animTime, intensity);
    }

    private static void renderFlameJetFlow(Vec3 start, Vec3 direction, double length, double width, int coreColor, int outerColor, int duration, float animTime) {
        int streamCount = 3;

        for (int stream = 0; stream < streamCount; stream++) {
            List<Vec3> flamePath = new ArrayList<>();

            double streamAngle = (2.0 * Math.PI * stream) / streamCount;
            double streamRadius = width * 0.2 * (0.3 + 0.7 * RANDOM.nextDouble());

            Vec3 perpendicular = getPerpendicularVector(direction, streamAngle);
            Vec3 streamStart = start.add(perpendicular.scale(streamRadius));

            int pathPoints = Math.max(6, (int)(length / 0.5));

            for (int i = 0; i < pathPoints; i++) {
                double t = (double) i / (pathPoints - 1);
                double pos = t * length;

                double turbulence = Math.sin(animTime * 8.0 + stream * 2.0 + t * 6.0) * width * 0.15;
                double drift = Math.sin(animTime * 3.0 + stream * 1.5 + t * 4.0) * width * 0.1;

                Vec3 flowPoint = streamStart.add(direction.scale(pos))
                        .add(turbulence * (RANDOM.nextDouble() - 0.5),
                                drift,
                                turbulence * (RANDOM.nextDouble() - 0.5));

                flamePath.add(flowPoint);
            }

            if (flamePath.size() > 1) {
                int alpha = (int)(255 * 0.8 * (0.8 + 0.2 * Math.sin(animTime * 4.0 + stream)));
                int pathColor = stream == 0 ?
                        ((alpha << 24) | (coreColor & 0xFFFFFF)) :
                        ((alpha << 24) | (outerColor & 0xFFFFFF));

                float thickness = (float)(width * 0.15 * (1.0 + 0.3 * Math.sin(animTime * 6.0 + stream * 1.8)));
                VectorRenderer.drawPolylineWorld(flamePath, pathColor, thickness, false, duration, null);
            }
        }
    }

    private static void renderFlameJetParticles(Vec3 start, Vec3 direction, double length, double width, int coreColor, int outerColor, int duration, float animTime) {
        int particleCount = Math.max(8, (int)(length * 2));

        for (int i = 0; i < particleCount; i++) {
            double particleLife = (animTime + i * 0.2) % 1.0;
            double t = particleLife;
            double pos = t * length * (1.1 + 0.2 * Math.sin(animTime * 5.0 + i * 0.7));

            if (pos > length * 1.3) continue;

            double maxRadius = width * 0.4 * (1.0 - t * 0.3);
            double angle = RANDOM.nextDouble() * 2.0 * Math.PI + animTime * 2.0;
            double radius = RANDOM.nextDouble() * maxRadius;

            Vec3 perpendicular = getPerpendicularVector(direction, angle);

            double flutter = Math.sin(animTime * 6.0 + i * 1.3) * width * 0.2 * particleLife;
            double rise = particleLife * width * 0.3;

            Vec3 particlePos = start.add(direction.scale(pos))
                    .add(perpendicular.scale(radius + flutter))
                    .add(0, rise, 0);

            float particleSize = (float)(0.06 + RANDOM.nextDouble() * 0.08);

            double heat = 1.0 - particleLife;
            int particleColor = heat > 0.7 ? coreColor : outerColor;
            int alpha = (int)(255 * heat * (0.7 + 0.3 * Math.sin(animTime * 8.0 + i)));
            particleColor = (alpha << 24) | (particleColor & 0xFFFFFF);

            VectorRenderer.drawSphereWorld(particlePos, particleSize, particleColor, 6, 4, false, duration, null);
        }
    }

    private static void renderFlameFlow(Vec3 origin, double height, double baseRadius, int coreColor, int outerColor, int duration, VectorRenderer.Transform transform, float animTime, float intensity) {
        int flowStreams = 4;

        for (int stream = 0; stream < flowStreams; stream++) {
            List<Vec3> flamePath = new ArrayList<>();

            double startAngle = (2.0 * Math.PI * stream) / flowStreams + animTime * 1.5;
            double startRadius = baseRadius * 0.3 * RANDOM.nextDouble();

            Vec3 streamStart = origin.add(
                    Math.cos(startAngle) * startRadius,
                    0,
                    Math.sin(startAngle) * startRadius
            );

            int pathPoints = Math.max(8, (int)(height / 0.3));

            for (int i = 0; i < pathPoints; i++) {
                double t = (double) i / (pathPoints - 1);
                double currentHeight = t * height;

                double waveRadius = baseRadius * (1.0 - t * 0.8) * intensity;
                double waveAngle = startAngle + t * 3.0 + Math.sin(animTime * 4.0 + stream * 1.2) * 0.5;

                double turbulence = Math.sin(animTime * 6.0 + t * 8.0 + stream * 2.1) * waveRadius * 0.3;

                Vec3 flowPoint = origin.add(
                        Math.cos(waveAngle) * (waveRadius + turbulence),
                        currentHeight,
                        Math.sin(waveAngle) * (waveRadius + turbulence)
                );

                flamePath.add(flowPoint);
            }

            if (flamePath.size() > 1) {
                int alpha = (int)(255 * intensity * 0.7);
                int pathColor = stream < 2 ?
                        ((alpha << 24) | (coreColor & 0xFFFFFF)) :
                        ((alpha << 24) | (outerColor & 0xFFFFFF));

                float thickness = (float)(baseRadius * 0.1 * intensity * (0.8 + 0.4 * Math.sin(animTime * 5.0 + stream)));
                VectorRenderer.drawPolylineWorld(flamePath, pathColor, thickness, false, duration, transform);
            }
        }
    }

    private static void renderFlameParticles(Vec3 origin, double height, double baseRadius, int coreColor, int outerColor, int duration, VectorRenderer.Transform transform, float animTime, float intensity) {
        int particleCount = (int)(12 * intensity);

        for (int i = 0; i < particleCount; i++) {
            double particleLife = (animTime + i * 0.25) % 1.0;
            double particleHeight = particleLife * height * (1.1 + 0.2 * Math.sin(animTime * 3.0 + i * 0.6));

            double maxRadius = baseRadius * (1.0 - particleLife * 0.6) * intensity;
            if (maxRadius <= 0.02) continue;

            double angle = RANDOM.nextDouble() * 2.0 * Math.PI + animTime * 1.8;
            double radius = RANDOM.nextDouble() * maxRadius;

            double flutter = Math.sin(animTime * 7.0 + i * 1.1) * maxRadius * 0.4 * particleLife;

            Vec3 particlePos = origin.add(
                    Math.cos(angle) * radius + flutter,
                    particleHeight,
                    Math.sin(angle) * radius + flutter * 0.7
            );

            float particleSize = (float)(0.05 + RANDOM.nextDouble() * 0.1) * (float)intensity;

            boolean useCore = particleLife < 0.6;
            int baseColor = useCore ? coreColor : outerColor;
            int alpha = (int)(255 * (1.0 - particleLife * 0.8) * intensity);
            int particleColor = (alpha << 24) | (baseColor & 0xFFFFFF);

            VectorRenderer.drawSphereWorld(particlePos, particleSize, particleColor, 6, 4, false, duration, transform);
        }
    }

    private static Vec3 getPerpendicularVector(Vec3 direction, double angle) {
        Vec3 up = Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = direction.cross(up).normalize();
        Vec3 forward = right.cross(direction).normalize();

        return right.scale(Math.cos(angle)).add(forward.scale(Math.sin(angle)));
    }
}