package com.github.cinnamondev.captureTheWool.commands;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCubeSnapshot;
import com.github.cinnamondev.captureTheWool.items.RespawnCompass;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CtwCommand {
    CaptureTheWool p;
    public CtwCommand(CaptureTheWool p) {
        this.p = p;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        var cubeSubCommand = Commands.literal("cube")
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.string())
                        .requires(src -> src.getSender() instanceof Player)
                        .then(Commands.argument("team", new TeamArgument(p))
                                .executes(ctx -> createCubeExecutor(ctx, true)))
                        .then(Commands.literal("unclaimed")
                                .executes(ctx -> createCubeExecutor(ctx, false)))
                ))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            p.cubes.forEach(woolCube -> {
                                ctx.getSource().getSender().sendMessage(woolCube.cubeBrief());
                            });
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("at")
                        .then(Commands.argument("location", ArgumentTypes.blockPosition()).then(Commands.argument("world", ArgumentTypes.world())
                                .then(Commands.literal("reveal").executes(this::revealCubeExecutor))
                                .then(Commands.literal("save").executes(this::saveCubeExecutor))
                                .executes(ctx -> {
                                    Location loc = resolveLocation(ctx);
                                    p.findWoolCubeAt(loc, true).ifPresent(cube -> {
                                        ctx.getSource().getSender().sendMessage(cube.lore());
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })
                        ))
                )
                .then(Commands.literal("all")
                        .then(Commands.literal("reveal").executes(ctx -> {
                            CaptureTheWool.cubes.forEach(WoolCube::revealLocation);
                            return Command.SINGLE_SUCCESS;
                        }))
                );

        var compassCommand = Commands.literal("compass")
                .then(Commands.literal("give").then(Commands.argument("players", ArgumentTypes.players())
                        .executes(ctx -> {
                            final PlayerSelectorArgumentResolver targetResolver = ctx.getArgument("players", PlayerSelectorArgumentResolver.class);
                            final List<Player> targets = targetResolver.resolve(ctx.getSource());
                            final CommandSender sender = ctx.getSource().getSender();

                            for (final Player target : targets) {
                                target.give(RespawnCompass.createItem());
                            }
                            return Command.SINGLE_SUCCESS;
                        })));

        return Commands.literal("ctw")
                .requires(src -> src.getSender().hasPermission("ctw.admin"))
                .then(Commands.literal("reloadSave").executes(ctx -> {
                    p.loadSave();
                    p.loadCubes();
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("saveGame").executes(ctx -> {
                    if (CaptureTheWool.cubes.isEmpty()) { return Command.SINGLE_SUCCESS; }
                    var list = CaptureTheWool.cubes.stream().map(WoolCube::createSnapshot).toList();
                    p.getSave().set("cubes", list);
                    p.save();
                    return Command.SINGLE_SUCCESS;
                }))
                .then(compassCommand)
                .then(cubeSubCommand)
                .build();
    }

    private static final SimpleCommandExceptionType ERROR_CANT_RESOLVE_LOCATION = new SimpleCommandExceptionType(
            MessageComponentSerializer.message().serialize(Component.text("cant resolve location!"))
    );

    private Location resolveLocation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final BlockPositionResolver blockPositionResolver = ctx.getArgument("location", BlockPositionResolver.class);
        final BlockPosition blockPosition = blockPositionResolver.resolve(ctx.getSource());
        return blockPosition.toLocation(ctx.getArgument("world", World.class));
    }

    int revealCubeExecutor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Location loc = resolveLocation(ctx);

        p.findWoolCubeAt(loc, false).ifPresentOrElse(
                WoolCube::revealLocation,
                () -> ctx.getSource().getSender().sendMessage(Component.text("No cube found!"))
        );
        return Command.SINGLE_SUCCESS;
    }
    int createCubeExecutor(CommandContext<CommandSourceStack> ctx, boolean hasTeam) {
        if (!(ctx.getSource().getSender() instanceof Player player)) { return 0; }

        Location loc = player.getLocation().subtract(0,3,0);
        TeamMeta meta = null;
        if (hasTeam) {
            meta = ctx.getArgument("team", TeamMeta.class);
        }

        WoolCube cube = new WoolCube(p, UUID.randomUUID(), loc, Component.text(ctx.getArgument("name", String.class)), meta);
        p.addCube(cube);
        cube.spawnCube();
        player.teleport(loc.add(0, 4, 0));
        player.sendMessage(Component.text("Done!"));
        return Command.SINGLE_SUCCESS;
    }
    int saveCubeExecutor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Location loc = resolveLocation(ctx);

        p.findWoolCubeAt(loc, false).ifPresentOrElse(c -> {
            List<WoolCubeSnapshot> list = (List<WoolCubeSnapshot>) p.getSave().getList("cubes");
            if (list == null) {
               p.getSave().set("cubes", List.of(c.createSnapshot()));
            } else {
                list = list.stream()
                        .filter(s -> !s.cubeUUID().equals(c.uuid()))
                        .collect(Collectors.toCollection(ArrayList::new));
                list.add(c.createSnapshot());
                p.getSave().set("cubes", list);
            }
            p.save();
        }, () -> {
            ctx.getSource().getSender().sendMessage("Cant find!");
        });


        return Command.SINGLE_SUCCESS;
    }
}
