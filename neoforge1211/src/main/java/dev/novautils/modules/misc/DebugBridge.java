package dev.novautils.modules.misc;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import dev.novautils.utils.PlatformCompat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/**
 * Read-only debug bridge: a tiny HTTP server on 127.0.0.1 that answers with what the game is doing
 * (where you are, which modules are on and how they are set, server messages, why you got kicked),
 * so problems on a real server can be looked at from outside the game. It only reads: there is no
 * way to send anything to the game or the server through it, and it only listens on this computer.
 *
 * <pre>
 *   curl http://127.0.0.1:25599/state
 *   curl http://127.0.0.1:25599/modules
 *   curl "http://127.0.0.1:25599/module?name=NewChunks"
 *   curl http://127.0.0.1:25599/log
 * </pre>
 */
public class DebugBridge extends Module {
    private final Setting<Integer> port = register(Setting.builder(Integer.class, "port")
        .description("Local port (only this computer can connect). Turn the module off and on after changing it.")
        .defaultValue(25599).range(1024, 65535).build());

    private final Setting<Integer> logSize = register(Setting.builder(Integer.class, "log-lines")
        .description("How many recent server messages and disconnect reasons to keep.").defaultValue(200).range(20, 2000).build());

    private final Deque<String> log = new ArrayDeque<>();
    private volatile String state = "{}";
    private volatile ServerSocket server;
    private Object lastScreen;
    private int ticks;

    public DebugBridge() {
        super("DebugBridge", "Read-only local bridge for debugging: game state, module settings and server messages over http://127.0.0.1.", Category.MISC);
    }

    @Override
    public void onEnable() {
        int p = port.get();
        try {
            ServerSocket s = new ServerSocket(p, 8, InetAddress.getLoopbackAddress());
            server = s;
            Thread t = new Thread(() -> serve(s), "NovaUtils-DebugBridge");
            t.setDaemon(true);
            t.start();
            note("bridge", "listening on 127.0.0.1:" + p);
        } catch (Exception e) {
            note("bridge", "could not open port " + p + ": " + e);
        }
    }

    @Override
    public void onDisable() {
        ServerSocket s = server;
        server = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ---- what the game is doing

    @Override
    public void onTick() {
        Object screen = PlatformCompat.currentScreen(mc);
        if (screen != lastScreen) {
            lastScreen = screen;
            if (screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen d) note("disconnected", d.getTitle().getString());
        }
        if (++ticks % 10 == 0) state = buildState();
    }

    @Override
    public boolean onReceivePacket(Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket p) note(p.overlay() ? "actionbar" : "system", p.content().getString());
        return false;
    }

    private void note(String kind, String text) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + " [" + kind + "] " + text.replace('\n', ' ');
        synchronized (log) {
            log.addLast(line);
            while (log.size() > logSize.get()) log.removeFirst();
        }
    }

    private String buildState() {
        Json j = new Json();
        j.str("time", new Date().toString());
        Object screen = PlatformCompat.currentScreen(mc);
        j.str("screen", screen == null ? null : screen.getClass().getName());
        j.num("fps", mc.getFps());
        var server = mc.getCurrentServer();
        j.str("server", server == null ? (mc.hasSingleplayerServer() ? "singleplayer" : null) : server.ip);
        if (mc.player != null && mc.level != null) {
            var pl = mc.player;
            j.str("dimension", mc.level.dimension().location().toString());
            j.num("x", round(pl.getX())).num("y", round(pl.getY())).num("z", round(pl.getZ()));
            j.num("yaw", round(pl.getYRot())).num("pitch", round(pl.getXRot()));
            j.num("chunkX", pl.chunkPosition().x).num("chunkZ", pl.chunkPosition().z);
            j.num("health", pl.getHealth()).num("food", pl.getFoodData().getFoodLevel());
            j.bool("onGround", pl.onGround());
            j.str("gamemode", mc.gameMode == null ? null : mc.gameMode.getPlayerMode().getName());
            j.num("renderDistance", mc.options.getEffectiveRenderDistance());
            j.num("loadedChunks", mc.level.getChunkSource().getLoadedChunksCount());
            var conn = mc.getConnection();
            if (conn != null) {
                j.num("playersOnline", conn.getOnlinePlayers().size());
                var me = conn.getPlayerInfo(pl.getUUID());
                if (me != null) j.num("ping", me.getLatency());
            }
        }
        StringBuilder on = new StringBuilder("[");
        for (Module m : ModuleManager.get().getModules()) {
            if (!m.isEnabled()) continue;
            if (on.length() > 1) on.append(',');
            on.append(Json.quote(m.name));
        }
        j.raw("enabledModules", on.append(']').toString());
        return j.end();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ---- the server (its own thread; only reads)

    private void serve(ServerSocket s) {
        while (!s.isClosed()) {
            try (Socket c = s.accept()) {
                c.setSoTimeout(3000);
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                String first = in.readLine();
                if (first == null) continue;
                String[] parts = first.split(" ");
                String target = parts.length > 1 ? parts[1] : "/";
                String body;
                int code = 200;
                try {
                    body = answer(target);
                } catch (Exception e) {
                    code = 500;
                    body = "{\"error\":" + Json.quote(e.toString()) + "}";
                }
                if (body == null) {
                    code = 404;
                    body = "{\"error\":\"unknown path\",\"paths\":[\"/state\",\"/modules\",\"/module?name=X\",\"/log\"]}";
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                OutputStream out = c.getOutputStream();
                out.write(("HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(bytes);
                out.flush();
            } catch (Exception ignored) {
                // closed, timed out, or a bad request: keep serving
            }
        }
    }

    private String answer(String target) {
        String path = target, query = "";
        int q = target.indexOf('?');
        if (q >= 0) {
            path = target.substring(0, q);
            query = target.substring(q + 1);
        }
        switch (path) {
            case "/":
                return "{\"paths\":[\"/state\",\"/modules\",\"/module?name=X\",\"/log\"]}";
            case "/state":
                return state;
            case "/modules": {
                StringBuilder sb = new StringBuilder("[");
                for (Module m : ModuleManager.get().getModules()) {
                    if (sb.length() > 1) sb.append(',');
                    sb.append(moduleJson(m, m.isEnabled()));
                }
                return sb.append(']').toString();
            }
            case "/module": {
                String name = "";
                for (String kv : query.split("&")) if (kv.startsWith("name=")) name = URLDecoder.decode(kv.substring(5), StandardCharsets.UTF_8);
                for (Module m : ModuleManager.get().getModules()) if (m.name.equalsIgnoreCase(name)) return moduleJson(m, true);
                return "{\"error\":" + Json.quote("no module " + name) + "}";
            }
            case "/log": {
                StringBuilder sb = new StringBuilder("[");
                synchronized (log) {
                    for (String l : log) {
                        if (sb.length() > 1) sb.append(',');
                        sb.append(Json.quote(l));
                    }
                }
                return sb.append(']').toString();
            }
            default:
                return null;
        }
    }

    /** A module as JSON; settings only when {@code withSettings} (the full list would be huge). */
    private static String moduleJson(Module m, boolean withSettings) {
        Json j = new Json();
        j.str("name", m.name).str("category", m.category.toString()).bool("enabled", m.isEnabled());
        if (withSettings) {
            Json s = new Json();
            for (Setting<?> st : m.getSettings()) {
                Object v = st.get();
                if (st.color && v instanceof Integer i) s.str(st.name, String.format("#%08X", i));
                else s.str(st.name, String.valueOf(v));
            }
            j.raw("settings", s.end());
        }
        return j.end();
    }

    /** Just enough JSON writing for the bridge. */
    private static final class Json {
        private final StringBuilder sb = new StringBuilder("{");

        private Json key(String k) {
            if (sb.length() > 1) sb.append(',');
            sb.append(quote(k)).append(':');
            return this;
        }

        Json str(String k, String v) {
            key(k).sb.append(v == null ? "null" : quote(v));
            return this;
        }

        Json num(String k, double v) {
            key(k).sb.append(v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v));
            return this;
        }

        Json bool(String k, boolean v) {
            key(k).sb.append(v);
            return this;
        }

        Json raw(String k, String json) {
            key(k).sb.append(json);
            return this;
        }

        String end() {
            return sb.append('}').toString();
        }

        static String quote(String s) {
            StringBuilder o = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> o.append("\\\"");
                    case '\\' -> o.append("\\\\");
                    case '\n' -> o.append("\\n");
                    case '\r' -> o.append("\\r");
                    case '\t' -> o.append("\\t");
                    default -> {
                        if (c < 0x20) o.append(String.format("\\u%04x", (int) c));
                        else o.append(c);
                    }
                }
            }
            return o.append('"').toString();
        }
    }
}
