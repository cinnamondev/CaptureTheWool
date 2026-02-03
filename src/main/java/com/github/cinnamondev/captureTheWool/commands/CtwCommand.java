package com.github.cinnamondev.captureTheWool.commands;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

public class CtwCommand {
    CaptureTheWool p;
    public CtwCommand(CaptureTheWool p) {
        this.p = p;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        var cubeSubCommand = Commands.literal("cube")
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
                        .requires(src -> src.getSender() instanceof Player)
                        .then(Commands.argument("team", new TeamArgument(p))
                                .executes(ctx -> createCubeExecutor(ctx, true)))
                        .then(Commands.literal("noTeam")
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
                                .then(Commands.literal("reveal").executes(ctx -> revealCubeExecutor(ctx, false)))
                                .executes(ctx -> {
                                    Location loc = resolveLocation(ctx, false );
                                    p.findWoolCubeAt(loc, true).ifPresent(cube -> {
                                        ctx.getSource().getSender().sendMessage(cube.lore());
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })
                        ))
                        .then(Commands.literal("ahead").requires(src -> src.getSender() instanceof HumanEntity)
                                .then(Commands.literal("reveal").executes(ctx -> revealCubeExecutor(ctx, true)))
                                .executes(ctx -> {
                                    Location loc = resolveLocation(ctx, true);
                                    p.findWoolCubeAt(loc, false).ifPresent(cube -> {
                                        ctx.getSource().getSender().sendMessage(cube.lore());
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );
        return Commands.literal("ctw")
                .then(Commands.literal("team")
                        .then(Commands.argument("team", new TeamArgument(p))))
                .then(cubeSubCommand)
                .build();
    }

    private static final SimpleCommandExceptionType ERROR_CANT_RESOLVE_LOCATION = new SimpleCommandExceptionType(
            MessageComponentSerializer.message().serialize(Component.text("cant resolve location!"))
    );
    private Location resolveLocation(CommandContext<CommandSourceStack> ctx, boolean lookahead) throws CommandSyntaxException {
        Location loc = null;
        if (!lookahead) {
            final BlockPositionResolver blockPositionResolver = ctx.getArgument("location", BlockPositionResolver.class);
            final BlockPosition blockPosition = blockPositionResolver.resolve(ctx.getSource());
            loc = blockPosition.toLocation(ctx.getArgument("world", World.class));
        } else if (ctx.getSource().getExecutor() instanceof Player player) {
            Block block = player.getTargetBlockExact(20);
            if (block != null && block.getType() != Material.AIR) {
                loc = block.getLocation().toBlockLocation();
            }
        }
        if (loc == null) {throw ERROR_CANT_RESOLVE_LOCATION.create();}
        return loc;
    }

    int revealCubeExecutor(CommandContext<CommandSourceStack> ctx, boolean lookahead) throws CommandSyntaxException {
        Location loc = resolveLocation(ctx, lookahead);

        p.findWoolCubeAt(loc, !lookahead).ifPresentOrElse(
                cube -> {
                    cube.revealLocation(p);
                }, () -> {
                    ctx.getSource().getSender().sendMessage(Component.text("No cube found!"));
                });
        return Command.SINGLE_SUCCESS;
    }
    int createCubeExecutor(CommandContext<CommandSourceStack> ctx, boolean hasTeam) {
        if (!(ctx.getSource().getSender() instanceof Player player)) { return 0; }

        Location loc = player.getLocation().subtract(0,3,0);
        TeamMeta meta = null;
        if (hasTeam) {
            meta = ctx.getArgument("team", TeamMeta.class);
        }

        WoolCube cube = new WoolCube(p, loc, Component.text(ctx.getArgument("name", String.class)), meta);
        p.addCube(cube);
        cube.spawnCube();
        player.teleport(loc.add(0, 4, 0));
        player.sendMessage(Component.text("Done!"));
        return Command.SINGLE_SUCCESS;
    }
}
