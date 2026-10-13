package dev.novautils.modules.render;

import dev.novautils.mixin.MultiPlayerGameModeAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Shows how far along every block being broken is - yours and other players' (the server tells
 * every client nearby) - as a box that fills up and turns from yellow to red, with a percentage.
 * Handy to see someone digging into your base or through your wall.
 */
public class BreakIndicator extends Module {
    private final Setting<Boolean> own = register(Setting.builder(Boolean.class, "own")
        .description("Also show the block you are mining.").defaultValue(true).build());

    private final Setting<Boolean> others = register(Setting.builder(Boolean.class, "others")
        .description("Show blocks other players are mining.").defaultValue(true).build());

    private final Setting<Boolean> percent = register(Setting.builder(Boolean.class, "percent")
        .description("Write the progress over the block.").defaultValue(true).build());

    private record Breaking(BlockPos pos, int stage, long seen) {}

    private final Map<Integer, Breaking> breaking = new HashMap<>();

    public BreakIndicator() {
        super("BreakIndicator", "Shows the progress of blocks being broken, yours and others'.", Category.RENDER);
    }

    @Override
    public void onDisable() {
        breaking.clear();
    }

    @Override
    public boolean onReceivePacket(Packet<?> packet) {
        if (packet instanceof ClientboundBlockDestructionPacket p) {
            if (p.getProgress() < 0 || p.getProgress() > 9) breaking.remove(p.getId());
            else breaking.put(p.getId(), new Breaking(p.getPos(), p.getProgress(), System.currentTimeMillis()));
        }
        return false;
    }

    private static int color(float progress) {
        int r = 0xFF, gr = (int) (0xD8 * (1f - progress));
        return 0x60000000 | r << 16 | gr << 8 | 0x30;
    }

    private void draw(RenderContext context, BlockPos pos, float progress) {
        if (mc.level.getBlockState(pos).isAir()) return;
        progress = Math.max(0.05f, Math.min(1f, progress));
        double inset = 0.5 * (1 - progress);
        AABB box = new AABB(pos).deflate(inset);
        AABB outline = new AABB(pos);
        int c = color(progress);
        RenderUtils.world(context, (pose, buffer) -> {
            RenderUtils.drawBox(pose, buffer, box, c);
            RenderUtils.drawBox(pose, buffer, outline, (c & 0xFFFFFF) | 0x20000000);
        });
        if (percent.get()) {
            RenderUtils.label(context, Vec3.atCenterOf(pos).add(0, 0.7, 0),
                Component.literal(Math.round(progress * 100) + "%").withStyle(s_ -> s_.withColor(c & 0xFFFFFF)), 0.8f, true);
        }
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        BlockPos mine = null;
        if (own.get() && mc.gameMode != null && mc.gameMode.isDestroying()) {
            var gm = (MultiPlayerGameModeAccessor) mc.gameMode;
            mine = gm.novautils$getDestroyBlockPos();
            draw(context, mine, gm.novautils$getDestroyProgress());
        }
        if (!others.get()) return;
        for (Iterator<Map.Entry<Integer, Breaking>> it = breaking.entrySet().iterator(); it.hasNext(); ) {
            Breaking b = it.next().getValue();
            // the server stops sending updates when someone gives up; forget it after a while
            if (now - b.seen() > 10_000) {
                it.remove();
                continue;
            }
            if (b.pos().equals(mine)) continue;
            draw(context, b.pos(), (b.stage() + 1) / 10f);
        }
    }
}
