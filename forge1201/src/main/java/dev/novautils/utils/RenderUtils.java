package dev.novautils.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Small helpers for drawing simple world-space geometry (ESP boxes, waypoint markers, ...).
 * 1.20.1 flavour: everything a frame's modules draw goes into one see-through quad batch, opened by
 * the first {@link #world} call and drawn by {@link #flush} once all modules are done.
 */
public final class RenderUtils {
    private RenderUtils() {}

    private static BufferBuilder batch;
    private static PoseStack.Pose batchPose;

    public static void drawFilledBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        quad(pose, buffer, color, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        quad(pose, buffer, color, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ);
        quad(pose, buffer, color, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ);
        quad(pose, buffer, color, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        quad(pose, buffer, color, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
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
        int alpha = ARGB.alpha(color);
        int fill = ARGB.color(Math.max(10, (int) (alpha * 0.45)), color & 0xFFFFFF);
        int edge = ARGB.color(Math.min(255, (int) (alpha * 2.4) + 60), color & 0xFFFFFF);

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
     * Draws see-through world geometry (boxes, lines) in world coordinates. While a shaderpack is on
     * (Oculus), the same drawBox / drawRibbon calls are captured and drawn on the HUD instead
     * (ScreenSpace), because shaders drop custom world geometry.
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
        if (batch == null) {
            Vec3 cam = context.cameraPos();
            PoseStack stack = new PoseStack();
            stack.last().pose().set(context.poseStack().last().pose());
            stack.translate(-cam.x, -cam.y, -cam.z);
            batchPose = stack.last();
            batch = Tesselator.getInstance().getBuilder();
            batch.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }
        draw.accept(batchPose, batch);
    }

    /** Draws this frame's batch through walls. Called once after all modules rendered. */
    public static void flush() {
        if (batch == null) return;
        BufferBuilder.RenderedBuffer built = batch.end();
        batch = null;
        batchPose = null;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(built);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * A floating text label at a world position that always faces the camera and keeps about the
     * same size on screen however far away it is. Drawn through walls when {@code seeThrough}.
     */
    public static void label(dev.novautils.render.RenderContext context, Vec3 pos,
                             net.minecraft.network.chat.Component text, float scale, boolean seeThrough) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (ShaderCompat.shadersActive()) {
            ScreenSpace.queueLabel(pos, text);
            return;
        }
        Vec3 cam = context.cameraPos();
        double dist = Math.max(4.0, cam.distanceTo(pos));
        float s = (float) (0.004 * dist * scale);

        var pose = context.poseStack();
        pose.pushPose();
        pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        pose.mulPose(PlatformCompat.camera(mc).rotation());
        pose.scale(-s, -s, s);

        var seq = text.getVisualOrderText();
        float x = -mc.font.width(seq) / 2f;
        var buffers = mc.renderBuffers().bufferSource();
        var mode = seeThrough ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH
            : net.minecraft.client.gui.Font.DisplayMode.NORMAL;
        mc.font.drawInBatch(seq, x, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers, mode,
            0x90000000, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        buffers.endBatch();
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
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, int color) {
        v(pose, buffer, (float) p.x, (float) p.y, (float) p.z, color);
    }

    private static void v(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int c) {
        buffer.vertex(pose.pose(), x, y, z).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).endVertex();
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float x3, float y3, float z3, float x4, float y4, float z4) {
        v(pose, buffer, x1, y1, z1, color);
        v(pose, buffer, x2, y2, z2, color);
        v(pose, buffer, x3, y3, z3, color);
        v(pose, buffer, x4, y4, z4, color);
    }
}
