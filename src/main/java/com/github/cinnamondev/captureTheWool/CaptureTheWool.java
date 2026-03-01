package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCubeSnapshot;
import com.github.cinnamondev.captureTheWool.commands.CtwCommand;
import com.github.cinnamondev.captureTheWool.items.RespawnCompass;
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

    private final File file = new File(this.getDataFolder(), "save.yml");
    //public Map<UUID, Material> assignedTeams = new HashMap<>();
    public @Nullable TeamMeta getPlayerTeam(OfflinePlayer player) {
        Team team = getServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        if (team == null) { return null; }
        return teamByName.get(team.getName());
    }

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

    static public HashMap<UUID, WoolCube> setRespawnLocations = new HashMap<>();
    // todo: is this actually neccesary. might be a lot of fuddy duddy messing with this.
    //public void loadRespawnLocations() {
    //    ConfigurationSection section = getSave().getConfigurationSection("respawns");
    //    if (section == null) {
    //        section = getSave().createSection("respawns");
    //    }
    //    setRespawnLocations = new HashMap<>(
    //            section.getKeys(false).stream().flatMap(str -> {
    //                UUID playerUUID = UUID.fromString(str);
    //                var cubeUuidStr = getSave().getString("respawns." + str);
    //                if (cubeUuidStr == null) {
    //                    return Stream.empty();
    //                }
    //                UUID cubeUUID = UUID.fromString(cubeUuidStr);
    //                WoolCube cube = cubeByUUID(cubeUUID);
    //                if (cube == null) {
    //                    return Stream.empty();
    //                }
    //                return Stream.of(Map.entry(playerUUID, cube));
    //            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
    //    );
    //}
//
    //public void writeRespawnLocations() {
    //    ConfigurationSection section = getSave().getConfigurationSection("respawns");
    //    if (section == null) {
    //        section = getSave().createSection("respawns");
    //    }
    //    for (var entry : setRespawnLocations.entrySet()) {
    //        section.set(entry.getKey().toString(), entry.getValue().uuid().toString());
    //    }
    //}
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
                cube.cubeState instanceof CubeState.Claimed(TeamMeta cubeTeam, boolean canRespawn)
                && !canRespawn && team.equals(cubeTeam)
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
                    NamedTextColor colour = ColourConverter
                            .tryNamedColourFromString(cfg.getString("colour", "WHITE"))
                            .orElseThrow();

                    Component teamName = mm.deserialize(cfg.getString("display_name", key)).color(colour);

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
        CubeState.registerConfiguration();
        WoolCubeSnapshot.registerConfiguration();
        saveDefaultConfig();
        reloadConfig();

        teams = discoverTeams(false); // if teams are already made it wont remove them. because it might break scorekeeping
        teamByName = teams.values().stream()
                .map(t -> Map.entry(t.scoreboardTeam().getName(), t))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        UNCLAIMED_MATERIAL = Material.matchMaterial(
                getConfig().getString("unclaimed-material", "LIGHT_GRAY")
        );
        if (UNCLAIMED_MATERIAL == null) { UNCLAIMED_MATERIAL = Material.LIGHT_GRAY_WOOL; }
        UNCLAIMED_COLOUR = ColourConverter.namedColourFromString(
                getConfig().getString("unclaimed-colour", "GRAY"),
                NamedTextColor.GRAY
        );

        loadSave();

        //CubeState state = getSave().getObject("state", CubeState.class);
        //getLogger().info(state.toString());
        //CubeState state2 = new CubeState.Claimed(
        //        teamByName.values().stream().findFirst().orElseThrow(),
        //        false
        //);
        //getSave().set("state", state2);
        //save();

        //return;

        this.compass = new RespawnCompass(this);
        getServer().getPluginManager().registerEvents(compass, this);
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

    public void loadCubes() {
        List<WoolCubeSnapshot> snapshots = (List<WoolCubeSnapshot>) getSave().getList("cubes", Collections.emptyList());
        snapshots.forEach(s -> { if (s != null) { addCube(s.toWoolCube(this)); }});
    }

    @Override
    public void onDisable() {
        getLogger().info("Saving the Wool Game.");
        save();
        // Plugin shutdown logic
    }
}
