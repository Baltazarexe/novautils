package dev.novautils.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small helpers for drawing simple world-space geometry (ESP boxes, waypoint markers, ...). */
public final class RenderUtils {
    private RenderUtils() {}

    public static void drawFilledBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        // Front (-Z)
        quad(pose, buffer, color, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        // Back (+Z)
        quad(pose, buffer, color, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ);
        // Left (-X)
        quad(pose, buffer, color, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ);
        // Right (+X)
        quad(pose, buffer, color, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        // Top (+Y)
        quad(pose, buffer, color, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        // Bottom (-Y)
        quad(pose, buffer, color, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ);
    }

    /** Thin two-plane ribbon between two world-space points (a stand-in for a line). */
    public static void drawRibbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, double width, int color) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-4) return;
        Vec3 dn = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dn.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 p1 = dn.cross(ref).normalize().scale(width);
        Vec3 p2 = dn.cross(p1).normalize().scale(width);
        ribbonQuad(pose, buffer, a, b, p1, color);
        ribbonQuad(pose, buffer, a, b, p2, color);
    }

    private static void ribbonQuad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 p, int color) {
        Vec3 a1 = a.subtract(p), a2 = a.add(p), b1 = b.subtract(p), b2 = b.add(p);
        vertex(pose, buffer, a1, color); vertex(pose, buffer, a2, color); vertex(pose, buffer, b2, color); vertex(pose, buffer, b1, color);
        vertex(pose, buffer, b1, color); vertex(pose, buffer, b2, color); vertex(pose, buffer, a2, color); vertex(pose, buffer, a1, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, int color) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float x3, float y3, float z3, float x4, float y4, float z4) {
        buffer.addVertex(pose, x1, y1, z1).setColor(color);
        buffer.addVertex(pose, x2, y2, z2).setColor(color);
        buffer.addVertex(pose, x3, y3, z3).setColor(color);
        buffer.addVertex(pose, x4, y4, z4).setColor(color);
    }
}
