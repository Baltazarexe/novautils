package dev.novautils.modules.world;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.InvUtils;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Place blocks in mid-air: with a block in hand and nothing in reach under the crosshair,
 * right-click puts it at a fixed distance along your view, with an outline showing where. Uses the
 * vanilla rule that clicking a replaceable block (air) places into that same spot. Holding
 * right-click keeps placing at the normal block-placing pace.
 */
public class AirPlace extends Module {
    private final Setting<Double> range = register(Setting.builder(Double.class, "range")
        .description("How far in front of you the block goes (survival reach is 4.5).")
        .defaultValue(4.0).range(1.0, 5.0).build());

    private final Setting<Boolean> preview = register(Setting.builder(Boolean.class, "preview")
        .description("Outline where the block will be placed.").defaultValue(true).build());

    private final Setting<Integer> color = register(Setting.builder(Integer.class, "preview-color")
        .description("Outline colour.").defaultValue(0x608B9CFF).asColor()
        .visible(() -> preview.get()).build());

    public AirPlace() {
        super("AirPlace", "Place blocks in the air where you look.", Category.WORLD);
    }

    private InteractionHand blockHand() {
        if (mc.player.getMainHandItem().getItem() instanceof BlockItem) return InteractionHand.MAIN_HAND;
        if (mc.player.getOffhandItem().getItem() instanceof BlockItem) return InteractionHand.OFF_HAND;
        return null;
    }

    /** Where the block would go right now, or null (not holding a block, aiming at something, occupied). */
    public BlockPos target() {
        if (mc.player == null || mc.level == null) return null;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) return null;
        if (blockHand() == null) return null;
        Vec3 at = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(range.get()));
        BlockPos pos = BlockPos.containing(at);
        if (!mc.level.getBlockState(pos).canBeReplaced()) return null;
        if (!mc.level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos), e -> e.isAlive() && e.blocksBuilding).isEmpty()) return null;
        return pos;
    }

    /**
     * MinecraftUseMixin, whenever vanilla handles a right-click (a tap, or each repeat while held).
     * Returns true when we placed a block in the air, so vanilla doesn't also "use" the item.
     */
    public static boolean tryPlace() {
        if (!dev.novautils.NovaUtils.modulesReady) return false;
        AirPlace ap = dev.novautils.modules.ModuleManager.get().get(AirPlace.class);
        if (!ap.isEnabled() || mc.player == null || mc.gameMode == null) return false;
        BlockPos pos = ap.target();
        InteractionHand hand = ap.blockHand();
        if (pos == null || hand == null) return false;
        mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        InvUtils.swing();
        return true;
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (!preview.get()) return;
        BlockPos pos = target();
        if (pos == null) return;
        AABB box = new AABB(pos);
        int c = color.get();
        RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, c));
    }
}
