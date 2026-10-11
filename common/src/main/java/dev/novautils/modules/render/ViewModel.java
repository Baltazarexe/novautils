package dev.novautils.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.novautils.modules.Category;
import dev.novautils.modules.Module;
import dev.novautils.modules.ModuleManager;
import dev.novautils.settings.Setting;
import net.minecraft.client.OptionInstance;
import net.minecraft.world.InteractionHand;
import org.joml.Quaternionf;

/**
 * Change how your hands and held items sit on screen: size, position and tilt (the offhand
 * mirrored to its side, with its own extra size), plus how strong and fast the enchantment glint
 * is. Purely visual, client side.
 */
public class ViewModel extends Module {
    private final Setting<Double> scale = register(Setting.builder(Double.class, "scale")
        .description("Size of the hand and item (1 = vanilla).").defaultValue(1.0).range(0.3, 2.0).build());
    private final Setting<Double> x = register(Setting.builder(Double.class, "x")
        .description("Left / right.").defaultValue(0.0).range(-1.0, 1.0).build());
    private final Setting<Double> y = register(Setting.builder(Double.class, "y")
        .description("Down / up.").defaultValue(0.0).range(-1.0, 1.0).build());
    private final Setting<Double> z = register(Setting.builder(Double.class, "z")
        .description("Closer / further.").defaultValue(0.0).range(-1.0, 1.0).build());
    private final Setting<Integer> rotX = register(Setting.builder(Integer.class, "rotate-x")
        .description("Tilt forward / back (degrees).").defaultValue(0).range(-180, 180).build());
    private final Setting<Integer> rotY = register(Setting.builder(Integer.class, "rotate-y")
        .description("Turn left / right (degrees).").defaultValue(0).range(-180, 180).build());
    private final Setting<Integer> rotZ = register(Setting.builder(Integer.class, "rotate-z")
        .description("Roll (degrees).").defaultValue(0).range(-180, 180).build());

    private final Setting<Boolean> applyOffhand = register(Setting.builder(Boolean.class, "offhand-too")
        .description("Apply to the offhand as well (mirrored to its side).").defaultValue(true).build());
    private final Setting<Double> offhandScale = register(Setting.builder(Double.class, "offhand-scale")
        .description("Extra size for the offhand only (1 = same as the main hand).").defaultValue(1.0).range(0.3, 2.0)
        .visible(() -> applyOffhand.get()).build());

    private final Setting<Boolean> glint = register(Setting.builder(Boolean.class, "custom-glint")
        .description("Override the enchantment glint strength and speed.").defaultValue(false).build());
    private final Setting<Double> glintStrength = register(Setting.builder(Double.class, "glint-strength")
        .description("How visible the enchantment shimmer is (0 = none).").defaultValue(1.0).range(0.0, 1.0)
        .visible(() -> glint.get()).build());
    private final Setting<Double> glintSpeed = register(Setting.builder(Double.class, "glint-speed")
        .description("How fast the enchantment shimmer moves.").defaultValue(0.5).range(0.0, 1.0)
        .visible(() -> glint.get()).build());

    private Double savedStrength, savedSpeed;
    private static boolean pushed;

    public ViewModel() {
        super("ViewModel", "Size, position and tilt of your hands/items, and enchant glint.", Category.RENDER);
    }

    // ---- glint: vanilla's own accessibility options, set while on and restored when off

    @Override
    public void onTick() {
        if (!glint.get()) {
            restoreGlint();
            return;
        }
        OptionInstance<Double> strength = mc.options.glintStrength(), speed = mc.options.glintSpeed();
        if (savedStrength == null) {
            savedStrength = strength.get();
            savedSpeed = speed.get();
        }
        if (!strength.get().equals(glintStrength.get())) strength.set(glintStrength.get());
        if (!speed.get().equals(glintSpeed.get())) speed.set(glintSpeed.get());
    }

    private void restoreGlint() {
        if (savedStrength == null) return;
        mc.options.glintStrength().set(savedStrength);
        mc.options.glintSpeed().set(savedSpeed);
        savedStrength = savedSpeed = null;
    }

    @Override
    public void onDisable() {
        restoreGlint();
    }

    // ---- hands: called by ViewModelMixin around vanilla's arm + item drawing

    public static void before(PoseStack pose, InteractionHand hand) {
        pushed = false;
        if (!dev.novautils.NovaUtils.modulesReady) return;
        ViewModel vm = ModuleManager.get().get(ViewModel.class);
        if (!vm.isEnabled()) return;
        boolean off = hand == InteractionHand.OFF_HAND;
        if (off && !vm.applyOffhand.get()) return;
        // main hand sits on the right for right-handed players; mirror X for the other side
        boolean leftHanded = mc.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
        boolean onLeft = off != leftHanded;
        float side = onLeft ? -1f : 1f;
        pose.pushPose();
        pushed = true;
        pose.translate(vm.x.get() * side, vm.y.get(), -vm.z.get());
        Quaternionf q = new Quaternionf().rotationXYZ((float) Math.toRadians(vm.rotX.get()),
            (float) Math.toRadians(vm.rotY.get() * side), (float) Math.toRadians(vm.rotZ.get() * side));
        pose.rotateAround(q, 0f, 0f, 0f);
        float s = (float) (double) vm.scale.get() * (off ? (float) (double) vm.offhandScale.get() : 1f);
        pose.scale(s, s, s);
    }

    public static void after(PoseStack pose) {
        if (pushed) pose.popPose();
        pushed = false;
    }
}
