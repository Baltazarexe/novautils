package dev.novautils.modules.render;

import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.render.RenderContext;
import dev.novautils.settings.Setting;
import dev.novautils.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights storage through walls. Each kind (chests, ender chests, barrels, shulkers, furnaces,
 * hoppers, dispensers, anything else) can be switched on/off and recoloured on its own. Container
 * vehicles (chest/hopper minecarts, chest boats) are entities, so they are tracked live every frame
 * rather than from the periodic chunk scan.
 */
public class StorageEsp extends Module {
    /** {@code matches} are substrings of the block id; an empty array means "any other container". */
    private record Kind(String[] matches, String[] excludes, Setting<Boolean> shown, Setting<Integer> color) {}

    private record Found(BlockPos pos, Kind kind) {}

    private final Setting<Integer> chunkRadius = register(Setting.builder(Integer.class, "chunk-radius")
        .description("How many chunks around you to scan for containers.")
        .defaultValue(6).range(1, 16).build());

    private final List<Kind> kinds = new ArrayList<>();
    private final Kind other;

    private final Setting<Boolean> vehicles = register(Setting.builder(Boolean.class, "minecarts-and-boats")
        .description("Highlight chest/hopper/furnace minecarts and chest boats.")
        .defaultValue(true).build());

    private final Setting<Integer> vehicleColor = register(Setting.builder(Integer.class, "minecarts-and-boats-color")
        .description("Colour for container minecarts and boats.")
        .defaultValue(0x80FF8C42).asColor()
        .visible(() -> vehicles.get()).build());

    private final List<Found> found = new ArrayList<>();
    private int rescanTimer = 0;

    public StorageEsp() {
        super("StorageEsp", "Highlights chests, shulkers, barrels and more through walls - pick which.", Category.RENDER);

        kind("chests", 0x663399FF, true, new String[]{"chest"}, new String[]{"ender_chest"});
        kind("ender-chests", 0x66A040FF, true, new String[]{"ender_chest"}, new String[0]);
        kind("shulkers", 0x66FF5CC8, true, new String[]{"shulker_box"}, new String[0]);
        kind("barrels", 0x66C8904A, true, new String[]{"barrel"}, new String[0]);
        kind("furnaces", 0x66A0A0A0, false, new String[]{"furnace", "smoker"}, new String[0]);
        kind("hoppers", 0x66707070, false, new String[]{"hopper"}, new String[0]);
        kind("dispensers", 0x66B0B0B0, false, new String[]{"dispenser", "dropper"}, new String[0]);
        other = kind("other-containers", 0x66FFFFFF, false, new String[0], new String[0]);
    }

    private Kind kind(String name, int color, boolean on, String[] matches, String[] excludes) {
        Setting<Boolean> shown = register(Setting.builder(Boolean.class, name)
            .description("Show " + name.replace('-', ' ') + ".").defaultValue(on).build());
        Setting<Integer> c = register(Setting.builder(Integer.class, name + "-color")
            .description("Colour for " + name.replace('-', ' ') + ".")
            .defaultValue(color).asColor().visible(shown::get).build());
        Kind kind = new Kind(matches, excludes, shown, c);
        kinds.add(kind);
        return kind;
    }

    @Override
    public void onEnable() {
        rescanTimer = 0;
    }

    @Override
    public void onDisable() {
        found.clear();
    }

    /** Classifies a block entity, or null when it isn't storage we care about. */
    private Kind kindOf(BlockPos pos, BlockEntity be) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock());
        String path = key == null ? "" : key.getPath();

        for (Kind k : kinds) {
            if (k == other) continue;
            boolean hit = false;
            for (String m : k.matches()) if (path.contains(m)) hit = true;
            for (String ex : k.excludes()) if (path.contains(ex)) hit = false;
            if (hit) return k;
        }
        return be instanceof BaseContainerBlockEntity ? other : null;
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (rescanTimer-- > 0) return;
        rescanTimer = 20;

        found.clear();

        int radius = chunkRadius.get();
        int centerX = mc.player.chunkPosition().x;
        int centerZ = mc.player.chunkPosition().z;

        for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
            for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;

                ChunkAccess chunk = mc.level.getChunk(cx, cz);
                for (var pos : chunk.getBlockEntitiesPos()) {
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (be == null) continue;
                    Kind kind = kindOf(pos, be);
                    if (kind != null) found.add(new Found(pos, kind));
                }
            }
        }
    }

    private void box(RenderContext context, Vec3 cam, AABB box, int color) {
        RenderUtils.world(context, (pose, buffer) -> RenderUtils.drawBox(pose, buffer, box, color));
    }

    @Override
    public void onRenderWorld(RenderContext context) {
        if (mc.level == null) return;
        Vec3 cam = context.cameraPos();

        for (Found f : found) {
            if (!f.kind().shown().get()) continue;
            box(context, cam, new AABB(f.pos()), f.kind().color().get());
        }

        if (vehicles.get()) {
            int color = vehicleColor.get();
            float partial = mc.getFrameTime();
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof ContainerEntity)) continue;
                AABB bounds = entity.getBoundingBox()
                    .move(entity.getPosition(partial).subtract(entity.position()))
                    .inflate(0.05);
                box(context, cam, bounds, color);
            }
        }
    }
}
