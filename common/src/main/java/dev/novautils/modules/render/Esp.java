package dev.novautils.modules.render;

import dev.novautils.render.RenderContext;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.settings.Setting;
import dev.novautils.utils.EspRender;
import dev.novautils.utils.PlatformCompat;
import dev.novautils.utils.RenderUtils;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity ESP. CHAMS outlines the real model through walls using the game's own glow outline
 * (coloured by a client-side scoreboard team, so nothing is sent to the server). BOX draws an
 * outlined box; BOTH does both.
 */
public class Esp extends Module {
    public enum Mode { CHAMS, BOX, BOTH }

    private enum Kind { PLAYER, HOSTILE, PASSIVE }

    private final Setting<Mode> mode = register(Setting.builder(Mode.class, "mode")
        .description("CHAMS = glowing model through walls, BOX = outlined box, BOTH.")
        .defaultValue(Mode.CHAMS).build());

    private final Setting<Boolean> players = register(Setting.builder(Boolean.class, "players")
        .description("Highlight other players.").defaultValue(true).build());

    private final Setting<Boolean> hostiles = register(Setting.builder(Boolean.class, "hostile-mobs")
        .description("Highlight hostile mobs.").defaultValue(true).build());

    private final Setting<Boolean> passives = register(Setting.builder(Boolean.class, "passive-mobs")
        .description("Highlight passive and neutral mobs.").defaultValue(false).build());

    private final Setting<Double> opacity = register(Setting.builder(Double.class, "box-opacity")
        .description("Opacity of the BOX fill/edges.").defaultValue(0.35).range(0.05, 1.0).build());

    private final Map<Kind, PlayerTeam> teams = new HashMap<>();
    private final Map<Integer, Entity> glowing = new HashMap<>();
    private Scoreboard boundTo;

    public Esp() {
        super("Esp", "Highlights entities through walls (chams / box).", Category.RENDER);
    }

    private Kind kindOf(Entity e) {
        if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive()) return null;
        if (e instanceof Player) return players.get() ? Kind.PLAYER : null;
        if (e instanceof Monster) return hostiles.get() ? Kind.HOSTILE : null;
        return passives.get() ? Kind.PASSIVE : null;
    }

    private PlayerTeam team(Kind kind) {
        Scoreboard board = mc.level.getScoreboard();
        if (board != boundTo) {
            teams.clear();
            glowing.clear();
            boundTo = board;
        }
        return teams.computeIfAbsent(kind, k -> {
            String name = "novautils_" + k.name().toLowerCase();
            PlayerTeam team = board.getPlayerTeam(name);
            if (team == null) team = board.addPlayerTeam(name);
            switch (k) {
                case PLAYER -> PlatformCompat.setPlayerColor(team);
                case HOSTILE -> PlatformCompat.setHostileColor(team);
                case PASSIVE -> PlatformCompat.setPassiveColor(team);
            }
            return team;
        });
    }

    /** Vanilla reads the synced shared flag 6 (glowing) on the client, so set that one. */
    private static void glow(Entity e, boolean on) {
        ((dev.novautils.mixin.EntityAccessor) e).novautils$setSharedFlag(6, on);
    }

    private void clearChams() {
        if (mc.level != null) {
            Scoreboard board = mc.level.getScoreboard();
            for (Entity e : glowing.values()) {
                glow(e, false);
                if (board == boundTo) board.removePlayerFromTeam(e.getScoreboardName());
            }
        }
        glowing.clear();
    }

    @Override
    public void onDisable() {
        clearChams();
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;

        boolean chams = mode.get() != Mode.BOX;
        if (!chams) {
            if (!glowing.isEmpty()) clearChams();
            return;
        }

        Scoreboard board = mc.level.getScoreboard();
        Map<Integer, Entity> seen = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            Kind kind = kindOf(e);
            if (kind == null) continue;

            PlayerTeam team = team(kind);
            seen.put(e.getId(), e);
            glow(e, true);
            if (board.getPlayersTeam(e.getScoreboardName()) != team) board.addPlayerToTeam(e.getScoreboardName(), team);
            glowing.put(e.getId(), e);
        }

        // Entities that stopped qualifying (out of range, dead, category turned off).
        glowing.values().removeIf(e -> {
            if (seen.containsKey(e.getId())) return false;
            glow(e, false);
            board.removePlayerFromTeam(e.getScoreboardName());
            return true;
        });
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null || mc.player == null || mode.get() == Mode.CHAMS) return;

        Vec3 cam = context.cameraPos();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float alpha = (float) (double) opacity.get();

        for (Entity e : mc.level.entitiesForRendering()) {
            Kind kind = kindOf(e);
            if (kind == null) continue;

            int color = switch (kind) {
                case PLAYER -> ARGB.colorFromFloat(alpha, 1f, 0.2f, 0.2f);
                case HOSTILE -> ARGB.colorFromFloat(alpha, 1f, 0.55f, 0f);
                case PASSIVE -> ARGB.colorFromFloat(alpha, 0.2f, 1f, 0.3f);
            };

            // Smooth between ticks instead of snapping to the tick position.
            AABB box = e.getBoundingBox().move(e.getPosition(pt).subtract(e.position()));

            context.poseStack().pushPose();
            context.poseStack().translate(-cam.x, -cam.y, -cam.z);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), EspRender.filledNoDepth(),
                (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
            context.poseStack().popPose();
        }
    }
}
