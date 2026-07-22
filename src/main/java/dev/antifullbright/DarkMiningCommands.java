package dev.antifullbright;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Path;

final class DarkMiningCommands {
    private DarkMiningCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, DarkMiningManager manager) {
        dispatcher.register(Commands.literal("darkmining")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    manager.statusLines(context.getSource().getServer(), player)
                                            .forEach(line -> context.getSource().sendSuccess(() -> line, false));
                                    return 1;
                                })))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    manager.resetWarnings(context.getSource().getServer(), player);
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            Messages.reset(player.getGameProfile().getName())), true);
                                    return 1;
                                })))
                .then(Commands.literal("setwarning")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 1_000))
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            int count = IntegerArgumentType.getInteger(context, "count");
                                            manager.setWarnings(context.getSource().getServer(), player, count);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    Messages.setWarning(player.getGameProfile().getName(), count)), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            Path path = context.getSource().getServer().getServerDirectory()
                                    .resolve("config").resolve(AntiFullbright.MOD_ID + "-server.toml");
                            try {
                                int changed = AntiFullbrightConfig.reloadFromDisk(path);
                                context.getSource().sendSuccess(() -> Component.literal(Messages.reloaded(changed)), true);
                                return Math.max(1, changed);
                            } catch (RuntimeException exception) {
                                AntiFullbright.LOGGER.error("Could not reload {}", path, exception);
                                context.getSource().sendFailure(Component.literal(Messages.reloadFailed(exception.getMessage())));
                                return 0;
                            }
                        }))
                .then(Commands.literal("debug")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    manager.debugLines(context.getSource().getServer(), player)
                                            .forEach(line -> context.getSource().sendSuccess(() -> line, false));
                                    return 1;
                                }))));
    }
}
