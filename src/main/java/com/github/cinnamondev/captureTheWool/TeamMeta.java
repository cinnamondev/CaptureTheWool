package com.github.cinnamondev.captureTheWool;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

public record TeamMeta(Team scoreboardTeam, Component name, NamedTextColor colour, Material woolColour) implements ForwardingAudience {
    @Override
    public @NotNull Iterable<? extends Audience> audiences() {
        return scoreboardTeam.audiences();
    }
}
