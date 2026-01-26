package com.github.cinnamondev.captureTheWool.commands;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.concurrent.CompletableFuture;

public final class TeamArgument implements CustomArgumentType.Converted<TeamMeta, String> {
    private final CaptureTheWool p;
    public TeamArgument(CaptureTheWool p) {
        this.p = p;
    }

    private static final DynamicCommandExceptionType ERROR_INVALID_TEAM_NAME = new DynamicCommandExceptionType(t -> {
        return MessageComponentSerializer.message().serialize(Component.text(t + " is not a valid team."));
    });

    @Override
    public TeamMeta convert(String nativeType) throws CommandSyntaxException {
        TeamMeta meta = p.teams.get(nativeType);
        if (meta == null) { throw ERROR_INVALID_TEAM_NAME.create(nativeType); }
        return meta;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        for (Material key: p.teams.keySet()) {
            String keyString = key.toString();
            if (keyString.startsWith(builder.getRemaining())) {
                builder.suggest(keyString);
            }
        }
        return builder.buildFuture();
    }
    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }
}
