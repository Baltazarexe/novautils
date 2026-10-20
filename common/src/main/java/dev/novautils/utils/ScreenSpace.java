package dev.novautils.utils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Boxes and lines drawn on the HUD layer instead of in the world, used while a shaderpack is on
 * (shaders replace the world pass and swallow our see-through geometry, but never touch the HUD).
 * World positions are projected with the camera's position, rotation and FOV; a box becomes the
 * screen rectangle around its corners, a line becomes a line.
 */
public final class ScreenSpace {
    private ScreenSpace() {}

    private record Box(AABB box, int color) {}
    private record Line(Vec3 a, Vec3 b, int color) {}

    private static final List<Box> boxes = new ArrayList<>();
    private static final List<Line> lines = new ArrayList<>();
    private static boolean capturing;

    /** While true, RenderUtils' box/line helpers queue here instead of emitting vertices. */
    public static boolean capturing() {
        return capturing;
    }

    static void setCapturing(boolean on) {
        capturing = on;
    }

    public static void queueBox(AABB box, int color) {
        if (boxes.size() < 4000) boxes.add(new Box(box, color));
    }

    public static void queueLine(Vec3 a, Vec3 b, int color) {
        if (lines.size() < 8000) lines.add(new Line(a, b, color));
    }

    // ---- projection

    private static Vec3 camPos, right, up, forward;
    private static double tanHalf, aspect, sw, sh;

    private static boolean setup() {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = PlatformCompat.camera(mc);
        if (cam == null) return false;
        camPos = cam.position();
        double yaw = Math.toRadians(cam.yRot()), pitch = Math.toRadians(cam.xRot());
        forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        right = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        up = right.cross(forward).normalize();
        sw = mc.getWindow().getGuiScaledWidth();
        sh = mc.getWindow().getGuiScaledHeight();
        aspect = sw / sh;
        tanHalf = Math.tan(Math.toRadians(cam.getFov()) / 2);
        return true;
    }

    /** Camera-space (x right, y up, z forward). */
    private static Vec3 toCamera(Vec3 p) {
        Vec3 rel = p.subtract(camPos);
        return new Vec3(rel.dot(right), rel.dot(up), rel.dot(forward));
    }

    private static double[] toScreen(Vec3 c) {
        double x = c.x / (c.z * tanHalf * aspect), y = c.y / (c.z * tanHalf);
        return new double[]{(x + 1) / 2 * sw, (1 - y) / 2 * sh};
    }

    private static final double NEAR = 0.05;

    /** Sets up the projection for this frame (HUD draws). False if there is no camera yet. */
    public static boolean begin() {
        return setup();
    }

    /** The camera's right vector (world space) after begin(): for placing things beside an entity. */
    public static Vec3 rightVector() {
        return right;
    }

    /** Screen position (gui pixels) of a world point after begin(), or null when it is behind you. */
    public static double[] project(Vec3 world) {
        Vec3 c = toCamera(world);
        if (c.z < NEAR) return null;
        return toScreen(c);
    }

    /** ModuleManager.renderHud, first thing: draw what the world pass queued this frame. */
    public static void draw(GuiGraphicsExtractor g) {
        if (boxes.isEmpty() && lines.isEmpty()) return;
        if (setup()) {
            for (Box b : boxes) drawBox(g, b);
            for (Line l : lines) drawLine(g, l);
        }
        boxes.clear();
        lines.clear();
    }

    private static void drawBox(GuiGraphicsExtractor g, Box b) {
        AABB a = b.box();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        int visible = 0;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? a.minX : a.maxX, (i & 2) == 0 ? a.minY : a.maxY, (i & 4) == 0 ? a.minZ : a.maxZ);
            Vec3 c = toCamera(corner);
            if (c.z < NEAR) continue;
            visible++;
            double[] s = toScreen(c);
            minX = Math.min(minX, s[0]); maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]); maxY = Math.max(maxY, s[1]);
        }
        if (visible == 0 || maxX < 0 || maxY < 0 || minX > sw || minY > sh) return;
        int x1 = (int) Math.floor(minX), y1 = (int) Math.floor(minY);
        int x2 = Math.max(x1 + 2, (int) Math.ceil(maxX)), y2 = Math.max(y1 + 2, (int) Math.ceil(maxY));
        int color = b.color();
        int alpha = ARGB.alpha(color);
        g.fill(x1, y1, x2, y2, ARGB.color(Math.max(12, (int) (alpha * 0.35)), color & 0xFFFFFF));
        int edge = ARGB.color(Math.min(255, alpha * 2 + 60), color & 0xFFFFFF);
        g.fill(x1, y1, x2, y1 + 1, edge);
        g.fill(x1, y2 - 1, x2, y2, edge);
        g.fill(x1, y1, x1 + 1, y2, edge);
        g.fill(x2 - 1, y1, x2, y2, edge);
    }

    private static void drawLine(GuiGraphicsExtractor g, Line l) {
        Vec3 a = toCamera(l.a()), b = toCamera(l.b());
        if (a.z < NEAR && b.z < NEAR) return;
        // clip the part behind the camera
        if (a.z < NEAR) a = a.add(b.subtract(a).scale((NEAR - a.z) / (b.z - a.z)));
        if (b.z < NEAR) b = b.add(a.subtract(b).scale((NEAR - b.z) / (a.z - b.z)));
        double[] p = toScreen(a), q = toScreen(b);
        double dx = q[0] - p[0], dy = q[1] - p[1];
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len > 4000) return;
        int steps = Math.max(1, (int) (len / 1.5));
        int color = l.color() | 0xFF000000;
        for (int i = 0; i <= steps; i++) {
            int x = (int) (p[0] + dx * i / steps), y = (int) (p[1] + dy * i / steps);
            if (x < -1 || y < -1 || x > sw + 1 || y > sh + 1) continue;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
