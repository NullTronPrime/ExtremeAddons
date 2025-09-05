package net.autismicannoyance.exadditions.client;

import net.autismicannoyance.exadditions.ExAdditions;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * ChaosCrystalRenderer (command-based)
 * - Uses VectorRenderer.Wireframe + VectorRenderer.drawWireframeAttached
 * - Builds a wireframe dodecahedron (edges only) and submits as a single WireframeCommand.
 *
 * Usage:
 *   ChaosCrystalRenderer.addEffect(entityId, 200);
 */
@Mod.EventBusSubscriber(modid = ExAdditions.MOD_ID, value = Dist.CLIENT)
public class ChaosCrystalRenderer {

    /**
     * Add a dodecahedron wireframe effect attached to an entity.
     * @param entityId target entity id
     * @param lifetimeTicks lifetime in ticks (-1 for persistent)
     */
    public static void addEffect(int entityId, int lifetimeTicks) {
        addEffect(entityId, lifetimeTicks, 0.6, false);
    }

    /**
     * Add effect with explicit size and optional doubleSided flag (doubleSided unused for pure wireframe but kept for API parity).
     */
    public static void addEffect(int entityId, int lifetimeTicks, double size, boolean doubleSided) {
        // build a wireframe dodecahedron in local/object space centered at origin
        VectorRenderer.Wireframe wf = buildDodecahedronWireframe(size);

        // anchored at entity with no extra offset; interpolate entity position for smooth motion
        Vec3 attachmentOffset = Vec3.ZERO;

        // color white (opaque), thickness in world units; set thicknessIsPixels true to use screen-space thickness
        int color = 0xFFFFFFFF;
        float thickness = 0.03f;
        boolean thicknessIsPixels = false;

        // group transform: identity -> stationary. If you want a rotated wireframe group, build a Transform and pass it here.
        VectorRenderer.Transform transform = VectorRenderer.Transform.IDENTITY;

        // submit the wireframe as a single command attached to the entity
        VectorRenderer.drawWireframeAttached(
                wf,
                entityId,
                attachmentOffset,
                true,               // interpolate entity position
                color,
                thickness,
                thicknessIsPixels,
                doubleSided,
                lifetimeTicks,
                transform
        );
    }

    /**
     * Build a Wireframe representing a dodecahedron by extracting unique edges from the 12 pentagonal faces.
     * The returned Wireframe's segments are in local/object-space with the center at the origin.
     */
    private static VectorRenderer.Wireframe buildDodecahedronWireframe(double size) {
        final double phi = (1.0 + Math.sqrt(5.0)) / 2.0;

        // 20 vertices (object space)
        double[][] base = new double[][] {
                { 1,  1,  1}, { 1,  1, -1}, { 1, -1,  1}, { 1, -1, -1},
                {-1,  1,  1}, {-1,  1, -1}, {-1, -1,  1}, {-1, -1, -1},
                { 0,  1.0/phi,  phi}, { 0,  1.0/phi, -phi}, { 0, -1.0/phi,  phi}, { 0, -1.0/phi, -phi},
                { 1.0/phi,  phi,  0}, { 1.0/phi, -phi,  0}, {-1.0/phi,  phi,  0}, {-1.0/phi, -phi,  0},
                { phi,  0,  1.0/phi}, { phi,  0, -1.0/phi}, {-phi,  0,  1.0/phi}, {-phi,  0, -1.0/phi}
        };

        // pentagonal faces (same indexing as used previously)
        int[][] faces = {
                {0, 16, 2, 10, 8}, {0, 8, 4, 14, 12}, {16, 17, 1, 12, 0},
                {1, 9, 11, 3, 17}, {1, 17, 16, 0, 12}, {2, 13, 15, 6, 10},
                {13, 3, 11, 7, 15}, {2, 16, 17, 3, 13}, {4, 8, 10, 6, 18},
                {14, 5, 19, 18, 4}, {5, 9, 1, 12, 14}, {19, 7, 11, 9, 5}
        };

        // scale vertices by size
        Vec3[] verts = new Vec3[base.length];
        for (int i = 0; i < base.length; i++) {
            double x = base[i][0] * size;
            double y = base[i][1] * size;
            double z = base[i][2] * size;
            verts[i] = new Vec3(x, y, z);
        }

        // extract unique edges from faces (unordered pair set)
        Set<Long> edges = new HashSet<>();
        VectorRenderer.Wireframe wf = VectorRenderer.createWireframe();

        for (int[] face : faces) {
            int len = face.length;
            for (int i = 0; i < len; i++) {
                int a = face[i];
                int b = face[(i + 1) % len];
                int min = Math.min(a, b);
                int max = Math.max(a, b);
                long key = (((long) min) << 32) | (max & 0xffffffffL);
                if (edges.add(key)) {
                    // add this edge to the wireframe (local/object space)
                    wf.addLine(verts[a], verts[b]);
                }
            }
        }

        return wf;
    }
}
