package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.woolCube.CubeState;
import com.github.cinnamondev.captureTheWool.woolCube.WoolCube;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public record TeamMeta(Team scoreboardTeam, Component name, NamedTextColor colour, Material woolColour) implements ForwardingAudience {
    @Override
    public @NotNull Iterable<? extends Audience> audiences() {
        return scoreboardTeam.audiences();
    }

    public Stream<WoolCube> getCubesOwnedBy(CaptureTheWool p) {
        return CaptureTheWool.cubes.stream()
                .filter(cube ->
                        (cube.cubeState instanceof CubeState.Claimed(TeamMeta claimer, boolean cd) && claimer.equals(this))
                        || (cube.cubeState instanceof CubeState.UnderAttack(TeamMeta claimer1, ArrayList<TeamMeta> att1) && claimer1.equals(this))
                );
    }

    public Stream<Player> getOnlinePlayers(Plugin p) {
        return scoreboardTeam.getEntries().stream()
                .flatMap(str -> Optional.ofNullable(p.getServer().getPlayer(str)).stream());
    }

    public int countAliveTeammates(Plugin p) {
        return getOnlinePlayers(p).filter(player -> player.getGameMode() != GameMode.SPECTATOR).toList().size();
    }
}
