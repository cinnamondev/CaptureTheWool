package com.github.cinnamondev.captureTheWool.commands;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class CtwCommand {
    CaptureTheWool p;
    public CtwCommand(CaptureTheWool p) {
        this.p = p;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        var cubeSubCommand = Commands.literal("cube")
                .then(Commands.literal("create")
                        .requires(src -> src.getSender() instanceof Player)
                        .then(Commands.argument("team", new TeamArgument(p))
                                .executes(ctx -> createCubeExecutor(ctx, true)))
                        .then(Commands.literal("noTeam")
                                .executes(ctx -> createCubeExecutor(ctx, false)))
                )
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            p.cubes.forEach(woolCube -> {
                                ctx.getSource().getSender().sendMessage(woolCube.cubeBrief());
                            });
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("at")
                        .then(Commands.argument("location", ArgumentTypes.blockPosition())

                        ));
        return Commands.literal("ctw")
                .then(Commands.literal("team")
                        .then(Commands.argument("team", new TeamArgument(p))))
                .then(cubeSubCommand)
                .build();
    }


    int createCubeExecutor(CommandContext<CommandSourceStack> ctx, boolean hasTeam) {
        if (!(ctx.getSource() instanceof Player player)) { return 0; }

        Location loc = player.getLocation();
        TeamMeta meta = null;
        if (hasTeam) {
            meta = ctx.getArgument("team", TeamMeta.class);
        }

        WoolCube cube = new WoolCube(p, loc, meta);
        p.addCube(cube);

        player.teleport(loc.add(0, 4, 0));
        player.sendMessage(Component.text("Done!"));
        return Command.SINGLE_SUCCESS;
    }
}
