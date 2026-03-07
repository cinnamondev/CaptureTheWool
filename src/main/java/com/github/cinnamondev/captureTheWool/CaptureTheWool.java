package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.woolCube.CubeState;
import com.github.cinnamondev.captureTheWool.woolCube.WoolCube;
import com.github.cinnamondev.captureTheWool.woolCube.WoolCubeSnapshot;
import com.github.cinnamondev.captureTheWool.commands.CtwCommand;
import com.github.cinnamondev.captureTheWool.items.RespawnCompass;
import com.github.cinnamondev.captureTheWool.items.Spyglass;
import com.github.cinnamondev.captureTheWool.util.ColourConverter;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CaptureTheWool extends JavaPlugin {
    public static Material UNCLAIMED_MATERIAL;
    public static NamedTextColor UNCLAIMED_COLOUR;
    public RespawnCompass compass;
    public Spyglass spyglass;

    private final File file = new File(this.getDataFolder(), "save.yml");
    //public Map<UUID, Material> assignedTeams = new HashMap<>();
    public @Nullable TeamMeta getPlayerTeam(OfflinePlayer player) {
        Team team = getServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        if (team == null) { return null; }
        return teamByName.get(team.getName());
    }


    static public HashMap<UUID, WoolCube> setRespawnLocations = new HashMap<>();

    static public Map<Material, TeamMeta> teams = new HashMap<>();
    static public Map<String, TeamMeta> teamByName = new HashMap<>();
    static public HashSet<WoolCube> cubes = new HashSet<>();
    public WoolCube cubeByUUID(UUID cubeUUID) {
        for (var cube : cubes) {
            if (cube.uuid().equals(cubeUUID)) { return cube; }
        }
        return null;
    }
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
                cube.cubeState instanceof CubeState.Claimed(TeamMeta cubeTeam, boolean cooldownActive)
                && !cooldownActive && team.equals(cubeTeam)
        ).toList();
    }
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
                    getLogger().info("LDSHF" + cfg.getString("colour", "WHITE").toUpperCase().trim());
                    NamedTextColor colour = ColourConverter
                            .tryNamedColourFromString(cfg.getString("colour", "WHITE").toUpperCase().trim())
                            .orElseThrow();

                    Component teamName = mm.deserialize(cfg.getString("display_name", key)).color(colour);
                    String internalTeamName = cfg.getString("team_name", key);

                    Team team = scoreboard.getTeam(internalTeamName);
                    if (team != null && remakeTeams) {
                        getLogger().info("Team " + e.getKey() + " existed, recreating.");
                        team.unregister();
                        team = null;
                    }
                    if (team == null) {
                        getLogger().info("Team " + e.getKey() + " didn't exist, creating.");
                        team = scoreboard.registerNewTeam(internalTeamName);
                        team.displayName(teamName);
                        team.color(colour);
                        team.setAllowFriendlyFire(false);
                    }
                    return Map.entry(woolBlock, new TeamMeta(team, teamName, colour, woolBlock));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void teamsFromConfig(boolean remakeTeams) {
        teams = discoverTeams(remakeTeams);
        teamByName = teams.values().stream()
                .map(t -> Map.entry(t.scoreboardTeam().getName(), t))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private @Nullable BossBar activeBar;

    @Override
    public void onEnable() {
        CubeState.registerConfiguration();
        WoolCubeSnapshot.registerConfiguration();
        saveDefaultConfig();
        reloadConfig();
        teamsFromConfig(false);

        UNCLAIMED_MATERIAL = Material.matchMaterial(
                getConfig().getString("unclaimed-material", "LIGHT_GRAY")
        );
        if (UNCLAIMED_MATERIAL == null) { UNCLAIMED_MATERIAL = Material.LIGHT_GRAY_WOOL; }
        UNCLAIMED_COLOUR = ColourConverter.namedColourFromString(
                getConfig().getString("unclaimed-colour", "GRAY"),
                NamedTextColor.GRAY
        );

        loadSave();

        this.compass = new RespawnCompass(this);
        this.spyglass = new Spyglass(this);
        getServer().getPluginManager().registerEvents(compass, this);
        getServer().getPluginManager().registerEvents(spyglass, this);
        getServer().getPluginManager().registerEvents(new PlayerNotifier(this), this);
        getServer().getPluginManager().registerEvents(new RevealManager(this), this);

        CtwCommand cmd = new CtwCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e  -> {
            Commands commands = e.registrar();
            commands.register(
                    cmd.command(),
                    "capture the wool",
                    Collections.singleton("capturethewool")
            );
        });

        Bukkit.addRecipe(compass.recipe(), true);
        //getServer().addRecipe(compass.recipe(), true);
        getServer().addRecipe(spyglass.recipe(), true);
        loadCubes();
        // Plugin startup logic

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Saves
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private YamlConfiguration save;
    public void loadSave() {
        this.save = YamlConfiguration.loadConfiguration(file);
    }
    public Configuration getSave() { return save; }
    public void save() {
        try {
            save.save(file);
        } catch (IOException e) {
            getLogger().severe(e.getMessage());
        }
    }
    public void loadCubes() {
        List<WoolCubeSnapshot> snapshots = (List<WoolCubeSnapshot>) getSave().getList("cubes", Collections.emptyList());
        snapshots.forEach(s -> { if (s != null) { addCube(s.toWoolCube(this)); }});
    }
    public void loadRespawnLocations() {
        ConfigurationSection section = getSave().getConfigurationSection("respawns");
        if (section == null) {
            section = getSave().createSection("respawns");
        }
        setRespawnLocations = new HashMap<>(
                section.getKeys(false).stream().flatMap(str -> {
                    UUID playerUUID = UUID.fromString(str);
                    var cubeUuidStr = getSave().getString("respawns." + str);
                    if (cubeUuidStr == null) {
                        return Stream.empty();
                    }
                    UUID cubeUUID = UUID.fromString(cubeUuidStr);
                    WoolCube cube = cubeByUUID(cubeUUID);
                    if (cube == null) {
                        return Stream.empty();
                    }
                    return Stream.of(Map.entry(playerUUID, cube));
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }

    public void writeRespawnLocations() {
        ConfigurationSection section = getSave().getConfigurationSection("respawns");
        if (section == null) {
            section = getSave().createSection("respawns");
        }
        for (var entry : setRespawnLocations.entrySet()) {
            section.set(entry.getKey().toString(), entry.getValue().uuid().toString());
        }
    }
    @Override
    public void onDisable() {
        getLogger().info("Saving the Wool Game.");
        save();
        // Plugin shutdown logic
    }
}
