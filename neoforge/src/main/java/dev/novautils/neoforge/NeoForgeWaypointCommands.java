package dev.novautils.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.novautils.modules.ModuleManager;
import dev.novautils.modules.render.Waypoints;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** {@code /waypoint add|remove|list}, registered against vanilla's CommandSourceStack. */
public final class NeoForgeWaypointCommands {
    private NeoForgeWaypointCommands() {}

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("waypoint")
                .then(Commands.literal("add")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            var player = ctx.getSource().getPlayer();
                            if (player == null) return 0;

                            ModuleManager.get().get(Waypoints.class).add(name, player.getX(), player.getY(), player.getZ());
                            feedback(ctx.getSource(), "Waypoint '" + name + "' added.");
                            return 1;
                        })))
                .then(Commands.literal("remove")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            boolean removed = ModuleManager.get().get(Waypoints.class).remove(name);
                            feedback(ctx.getSource(), removed ? "Waypoint '" + name + "' removed." : "No waypoint named '" + name + "'.");
                            return 1;
                        })))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        var lines = ModuleManager.get().get(Waypoints.class).list();
                        if (lines.isEmpty()) {
                            feedback(ctx.getSource(), "No waypoints set.");
                        } else {
                            for (String line : lines) feedback(ctx.getSource(), line);
                        }
                        return 1;
                    }))
        );
    }

    private static void feedback(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
