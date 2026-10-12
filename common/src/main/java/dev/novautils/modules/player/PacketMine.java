package dev.novautils.modules.player;

import dev.novautils.mixin.MultiPlayerGameModeAccessor;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Click a block once and it gets mined in the background - you can look away, walk, or click the
 * next blocks to queue them. It tells the server "started digging", counts the same progress the
 * server counts (with the best hotbar tool), and sends "finished" once the block is done,
 * switching to that tool just for the finishing packet.
 */
public class PacketMine extends Module {
    private final Setting<Integer> queueSize = register(Setting.builder(Integer.class, "queue")
        .description("How many blocks can wait in line.").defaultValue(4).range(1, 16).build());

    private final Setting<Boolean> bestTool = register(Setting.builder(Boolean.class, "best-tool")
        .description("Use the fastest hotbar tool when finishing a block.").defaultValue(true).build());

    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("Give up on blocks farther than this.").defaultValue(5.5).range(3.0, 6.0).build());

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "color")
        .description("Highlight colour.").defaultValue(0x608B9CFF).asColor().build());

    private record Job(BlockPos pos, Direction dir) {}

    private final Deque<Job> queue = new ArrayDeque<>();
    private Job current;
    private float progress;
    private int waitTicks;

    public PacketMine() {
        super("PacketMine", "Click a block to mine it in the background; queue several.", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        if (current != null) send(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, current);
        current = null;
        queue.clear();
    }

    /** GameModeMixin, on every left-click / hold on a block. True = we took it. */
    public boolean onClick(BlockPos pos, Direction dir) {
        if (mc.player == null || mc.level == null || mc.player.getAbilities().instabuild) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0) return false;
        // instant breaks (grass, crops...) are faster the normal way
        if (state.getDestroyProgress(mc.player, mc.level, pos) >= 1f) return false;
        if (current != null && current.pos().equals(pos)) return true;
        for (Job j : queue) if (j.pos().equals(pos)) return true;
        if (queue.size() < queueSize.get()) queue.add(new Job(pos.immutable(), dir));
        return true;
    }

    private void send(ServerboundPlayerActionPacket.Action action, Job job) {
        if (mc.getConnection() == null || mc.level == null) return;
        ((MultiPlayerGameModeAccessor) mc.gameMode).novautils$startPrediction(mc.level,
            seq -> new ServerboundPlayerActionPacket(action, job.pos(), job.dir(), seq));
    }

    private int bestSlot(BlockState state, BlockPos pos) {
        var inv = mc.player.getInventory();
        int original = inv.getSelectedSlot(), best = original;
        float bestSpeed = -1;
        for (int i = 0; i < 9; i++) {
            inv.setSelectedSlot(i);
            float s = state.getDestroyProgress(mc.player, mc.level, pos);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = i;
            }
        }
        inv.setSelectedSlot(original);
        return best;
    }

    /** Progress per tick for the tool we'll finish with. */
    private float speed(BlockState state, BlockPos pos) {
        if (!bestTool.get()) return state.getDestroyProgress(mc.player, mc.level, pos);
        var inv = mc.player.getInventory();
        int original = inv.getSelectedSlot();
        inv.setSelectedSlot(bestSlot(state, pos));
        float s = state.getDestroyProgress(mc.player, mc.level, pos);
        inv.setSelectedSlot(original);
        return s;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (current == null) {
            current = queue.poll();
            if (current == null) return;
            progress = 0;
            send(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, current);
        }
        BlockState state = mc.level.getBlockState(current.pos());
        if (state.isAir() || mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(current.pos())) > range.get()) {
            if (!state.isAir()) send(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, current);
            current = null;
            return;
        }
        progress += speed(state, current.pos());
        if (progress < 1f) return;

        int original = mc.player.getInventory().getSelectedSlot();
        int slot = bestTool.get() ? bestSlot(state, current.pos()) : original;
        if (slot != original) InvUtils.selectHotbar(slot);
        send(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, current);
        InvUtils.swing();
        if (slot != original) InvUtils.selectHotbar(original);
        current = null;
        waitTicks = 1;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null) return;
        int c = color.get();
        if (current != null) {
            float f = Math.max(0.05f, Math.min(1f, progress));
            AABB box = new AABB(current.pos()).deflate(0.5 * (1 - f));
            AABB outline = new AABB(current.pos());
            RenderUtils.world(context, (pose, buffer) -> {
                RenderUtils.drawBox(pose, buffer, box, c);
                RenderUtils.drawBox(pose, buffer, outline, (c & 0xFFFFFF) | 0x20000000);
            });
            RenderUtils.label(context, Vec3.atCenterOf(current.pos()).add(0, 0.7, 0),
                Component.literal(Math.round(f * 100) + "%").withColor(c & 0xFFFFFF), 0.8f, true);
        }
        for (Job j : queue) {
            AABB box = new AABB(j.pos());
            RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, (c & 0xFFFFFF) | 0x28000000));
        }
    }
}
