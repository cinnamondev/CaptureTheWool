package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.commands.CtwCommand;
import com.github.cinnamondev.captureTheWool.util.ColourConverter;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public final class CaptureTheWool extends JavaPlugin {
    public Material unclaimedMaterial;
    public NamedTextColor unclaimedColour;

    //public Map<UUID, Material> assignedTeams = new HashMap<>();
    public @Nullable TeamMeta getPlayerTeam(OfflinePlayer player) {
        Team team = getServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        return teamByName.get(team.getName());
    }



    public HashMap<UUID, WoolCube> setRespawnLocations = new HashMap<>();
    public Map<Material, TeamMeta> teams = new HashMap<>();
    public Map<String, TeamMeta> teamByName = new HashMap<>();
    public HashSet<WoolCube> cubes = new HashSet<>();
    public void addCube(WoolCube cube) {
        this.getServer().getPluginManager().registerEvents(cube, this);
        cubes.add(cube);
    }
    public Optional<WoolCube> findWoolCubeAt(Location root, boolean strict) {
        BlockPosition blockPosition = root.toBlock();
        for (WoolCube cube: cubes) {
            if (cube.root.toBlock().equals(blockPosition)) {
                return Optional.of(cube);
            } else if (!strict) {
                for (Location loc: cube.woolLocations) {
                    if (loc.toBlock().equals(blockPosition)) {
                        return Optional.of(cube);
                    }
                }
            }
        }
        return Optional.empty();
    }
    public List<WoolCube> getRespawnCandidatesFor(TeamMeta team) {
        return cubes.stream().filter(cube ->
                cube.cubeState instanceof WoolCube.State.Claimed(TeamMeta cubeTeam, boolean canRespawn)
                && canRespawn && team.equals(cubeTeam)
        ).toList();
    }
    public NamedTextColor color = NamedTextColor.YELLOW;

    public Map<Material, TeamMeta> discoverTeams(boolean remakeTeams) {
        Scoreboard scoreboard = getServer().getScoreboardManager().getMainScoreboard();
        MiniMessage mm = MiniMessage.miniMessage();

        return Objects.requireNonNull(getConfig().getConfigurationSection("teams"))
                .getKeys(false).stream()
                .map(str -> Map.entry(str, Objects.requireNonNull(getConfig().getConfigurationSection("teams." + str))))
                .map(e -> {
                    var key = e.getKey();
                    var cfg = e.getValue();
                    Material woolBlock = Material.matchMaterial(e.getKey());
                    if (woolBlock == null) { throw new IllegalArgumentException("Invalid woolBlock for team " + key); }
                    NamedTextColor colour = ColourConverter
                            .tryNamedColourFromString(cfg.getString("colour", "WHITE"))
                            .orElseThrow();

                    Component teamName = mm.deserialize(cfg.getString("teamName", key)).color(colour);

                    Team team = scoreboard.getTeam(key);
                    if (team != null && remakeTeams) {
                        team.unregister();
                        team = null;
                    }
                    if (team == null) {
                        team = scoreboard.registerNewTeam(key);
                        team.displayName(teamName);
                        team.color(colour);
                        team.setAllowFriendlyFire(false);
                    }
                    return Map.entry(woolBlock, new TeamMeta(team, teamName, colour, woolBlock));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // TODO: FUTURE. OBJECTIVE KEEPING??
    public void createObjectives(boolean recreate) {
        Scoreboard sb = getServer().getScoreboardManager().getMainScoreboard();
        Objective woolBreaks = sb.registerNewObjective("woolBreaks", Criteria.TRIGGER, Component.text("Wool breaks"));
        Objective woolRepairs = sb.registerNewObjective("woolRepairs", Criteria.TRIGGER, Component.text("Wool fixes"));
    }
    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        this.teams = discoverTeams(false); // if teams are already made it wont remove them. because it might break scorekeeping
        this.teamByName = this.teams.values().stream()
                .map(t -> Map.entry(t.scoreboardTeam().getName(), t))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        this.unclaimedMaterial = Material.matchMaterial(
                getConfig().getString("unclaimed-material", "LIGHT_GRAY")
        );
        if (unclaimedMaterial == null) { this.unclaimedMaterial = Material.LIGHT_GRAY_WOOL; }
        this.unclaimedColour = ColourConverter.namedColourFromString(
                getConfig().getString("unclaimed-colour", "GRAY"),
                NamedTextColor.GRAY
        );

        getServer().getPluginManager().registerEvents(new PlayerNotifier(), this);

        CtwCommand cmd = new CtwCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e  -> {
            Commands commands = e.registrar();
            commands.register(
                    cmd.command(),
                    "capture the wool",
                    Collections.singleton("capturethewool")
            );
        });
        // Plugin startup logic

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
