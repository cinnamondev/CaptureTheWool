package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Stream;

public record TeamMeta(Team scoreboardTeam, Component name, NamedTextColor colour, Material woolColour) implements ForwardingAudience {
    @Override
    public @NotNull Iterable<? extends Audience> audiences() {
        return scoreboardTeam.audiences();
    }

    public Stream<WoolCube> getCubesOwnedBy(CaptureTheWool p) {
        return p.cubes.stream()
                .filter(cube -> cube.cubeState instanceof CubeState.Claimed(
                        TeamMeta claimer, boolean respawnCooldownActive
                ) && !respawnCooldownActive && claimer.woolColour() == woolColour);
    }
}
