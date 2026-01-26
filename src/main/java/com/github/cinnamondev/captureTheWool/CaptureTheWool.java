package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.commands.CtwCommand;
import com.github.cinnamondev.captureTheWool.util.ColourConverter;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public final class CaptureTheWool extends JavaPlugin {
    public Material unclaimedMaterial;
    public NamedTextColor unclaimedColour;

    public Map<UUID, Material> assignedTeams = new HashMap<>();
    public @Nullable TeamMeta getPlayerTeam(Player player) {
        Material m = assignedTeams.get(player.getUniqueId());
        if (m == null) { return null; }
        return teams.get(m);
    }

    public HashMap<UUID, WoolCube> setRespawnLocations = new HashMap<>();
    public Map<Material, TeamMeta> teams = new HashMap<>();
    public HashSet<WoolCube> cubes = new HashSet<>();
    public void addCube(WoolCube cube) {
        this.getServer().getPluginManager().registerEvents(cube, this);
        cubes.add(cube);
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
                    Material woolBlock = Material.matchMaterial(cfg.getString(e.getKey()));
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        this.teams = discoverTeams(false); // if teams are already made it wont remove them. because it might break scorekeeping

        this.unclaimedMaterial = Material.matchMaterial(
                getConfig().getString("unclaimed-material", "LIGHT_GRAY")
        );
        if (unclaimedMaterial == null) { this.unclaimedMaterial = Material.LIGHT_GRAY_WOOL; }
        this.unclaimedColour = ColourConverter.namedColourFromString(
                getConfig().getString("unclaimed-colour", "GRAY"),
                NamedTextColor.GRAY
        );

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
