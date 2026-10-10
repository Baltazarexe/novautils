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

    /**
     * Nicer box: a soft translucent fill plus crisp brighter edges. {@code color} is ARGB; its alpha
     * is used for the fill and boosted for the edges.
     */
    public static void drawBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        if (ScreenSpace.capturing()) {
            ScreenSpace.queueBox(box, color);
            return;
        }
        int alpha = net.minecraft.util.ARGB.alpha(color);
        int fill = net.minecraft.util.ARGB.color(Math.max(10, (int) (alpha * 0.45)), color & 0xFFFFFF);
        int edge = net.minecraft.util.ARGB.color(Math.min(255, (int) (alpha * 2.4) + 60), color & 0xFFFFFF);

        drawFilledBox(pose, buffer, box, fill);

        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        double w = 0.012;
        Vec3 a = new Vec3(x0, y0, z0), b = new Vec3(x1, y0, z0), c = new Vec3(x1, y0, z1), d = new Vec3(x0, y0, z1);
        Vec3 e = new Vec3(x0, y1, z0), f = new Vec3(x1, y1, z0), g = new Vec3(x1, y1, z1), h = new Vec3(x0, y1, z1);
        drawRibbon(pose, buffer, a, b, w, edge); drawRibbon(pose, buffer, b, c, w, edge);
        drawRibbon(pose, buffer, c, d, w, edge); drawRibbon(pose, buffer, d, a, w, edge);
        drawRibbon(pose, buffer, e, f, w, edge); drawRibbon(pose, buffer, f, g, w, edge);
        drawRibbon(pose, buffer, g, h, w, edge); drawRibbon(pose, buffer, h, e, w, edge);
        drawRibbon(pose, buffer, a, e, w, edge); drawRibbon(pose, buffer, b, f, w, edge);
        drawRibbon(pose, buffer, c, g, w, edge); drawRibbon(pose, buffer, d, h, w, edge);
    }

    /**
     * Draws see-through world geometry (boxes, lines) in world coordinates. Normally it's submitted
     * to the world pass with the no-depth ESP type; while a shaderpack is on, the same drawBox /
     * drawRibbon calls are captured and drawn on the HUD instead (ScreenSpace), because shaders
     * drop custom world geometry.
     */
    public static void world(dev.novautils.render.RenderContext context,
                             java.util.function.BiConsumer<PoseStack.Pose, VertexConsumer> draw) {
        if (ShaderCompat.shadersActive()) {
            ScreenSpace.setCapturing(true);
            try {
                draw.accept(null, null);
            } finally {
                ScreenSpace.setCapturing(false);
            }
            return;
        }
        Vec3 cam = context.cameraPos();
        var pose = context.poseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        context.submitNodeCollector().submitCustomGeometry(pose, EspRender.filledNoDepth(), draw::accept);
        pose.popPose();
    }

    /**
     * A floating text label at a world position that always faces the camera and keeps about the
     * same size on screen however far away it is. Drawn through walls when {@code seeThrough}.
     */
    public static void label(dev.novautils.render.RenderContext context, Vec3 pos,
                             net.minecraft.network.chat.Component text, float scale, boolean seeThrough) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        Vec3 cam = context.cameraPos();
        double dist = Math.max(4.0, cam.distanceTo(pos));
        float s = (float) (0.004 * dist * scale);

        var pose = context.poseStack();
        pose.pushPose();
        pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        pose.rotateAround(PlatformCompat.camera(mc).rotation(), 0f, 0f, 0f);
        pose.scale(s, -s, s);

        var seq = text.getVisualOrderText();
        float x = -mc.font.width(seq) / 2f;
        var mode = seeThrough ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH
            : net.minecraft.client.gui.Font.DisplayMode.NORMAL;
        context.submitNodeCollector().submitText(pose, x, 0, seq, false, mode,
            net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, 0x90000000, 0);
        pose.popPose();
    }

    /** Thin two-plane ribbon between two world-space points (a stand-in for a line). */
    public static void drawRibbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, double width, int color) {
        if (ScreenSpace.capturing()) {
            ScreenSpace.queueLine(a, b, color);
            return;
        }
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
