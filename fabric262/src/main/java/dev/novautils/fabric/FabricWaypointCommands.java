package dev.novautils.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Waypoints;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

/** {@code /waypoint add|remove|list}, registered against Fabric's client command source. */
public final class FabricWaypointCommands {
    private FabricWaypointCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
            ClientCommands.literal("waypoint")
                .then(ClientCommands.literal("add")
                    .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            var player = ctx.getSource().getPlayer();
                            if (player == null) return 0;

                            ModuleManager.get().get(Waypoints.class).add(name, player.getX(), player.getY(), player.getZ());
                            ctx.getSource().sendFeedback(Component.literal("Waypoint '" + name + "' added."));
                            return 1;
                        })))
                .then(ClientCommands.literal("remove")
                    .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            boolean removed = ModuleManager.get().get(Waypoints.class).remove(name);
                            ctx.getSource().sendFeedback(Component.literal(removed
                                ? "Waypoint '" + name + "' removed."
                                : "No waypoint named '" + name + "'."));
                            return 1;
                        })))
                .then(ClientCommands.literal("list")
                    .executes(ctx -> {
                        var lines = ModuleManager.get().get(Waypoints.class).list();
                        if (lines.isEmpty()) {
                            ctx.getSource().sendFeedback(Component.literal("No waypoints set."));
                        } else {
                            for (String line : lines) ctx.getSource().sendFeedback(Component.literal(line));
                        }
                        return 1;
                    }))
        ));
    }
}
