package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.events.StateChangeEvent;
import com.github.cinnamondev.captureTheWool.util.BlockUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;
import javax.xml.stream.events.StartElement;
import java.net.http.WebSocket;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WoolCube implements Listener {
    public sealed interface State {
        record Claimed(TeamMeta claimer, boolean respawnCooldownActive) implements State {}
        record UnderAttack(@Nullable TeamMeta claimer, ArrayList<TeamMeta> attackers) implements State {} // one or more teams may be attacking!
        record Unclaimed() implements State { }
    }

    private final CaptureTheWool p;
    final Location root;
    final Location respawnLocation;
    protected State cubeState;
    protected boolean updateCubeState(State newState) {
        var e = new StateChangeEvent(this, cubeState, newState);
        boolean updated = e.callEvent();
        if (updated) {
            p.getLogger().info("updated cube state from " + cubeState.toString() + " to " + newState.toString());
            this.cubeState = newState; // if not cancelled update
        }
        return updated;
    }
    protected final Set<Location> woolLocations;
    public WoolCube(CaptureTheWool p, Location root, @Nullable TeamMeta currentOwner) {
        this.p = p;

        if (currentOwner == null) {
            updateCubeState(new State.Unclaimed());
        } else {
            updateCubeState(new State.Claimed(currentOwner, false));
        }

        this.root = root;
        this.respawnLocation = root.add(0, 4, 0);
        woolLocations = allWallBlocks(root);

        World world = root.getWorld();
        for (Location woolLocation : woolLocations) {
            Chunk chunk = woolLocation.getChunk();
            world.setChunkForceLoaded(chunk.getX(), chunk.getZ(), true);
        }
    }

    @EventHandler
    public void playerBreakBlock(BlockBreakEvent e) {
        if (woolLocations.contains(e.getBlock().getLocation())) {
            return;
        }

        e.setCancelled(true);
        Player player = e.getPlayer();
        Material m = p.assignedTeams.get(player.getUniqueId());
        if (m == null) {
            p.getLogger().info("Player " + player.getUniqueId().toString() + " has no wool team, ignoring");
            return;
        }
        TeamMeta team = Objects.requireNonNull(p.teams.get(m));

        State previousState = cubeState;
        State newState;
        switch (cubeState) {
            case State.Claimed(TeamMeta currentClaimer, boolean _cooldown) -> {
                updateCubeState(new State.UnderAttack(currentClaimer, new ArrayList<>(Collections.singleton(team))));
                // we are now under attack
            }
            case State.UnderAttack(TeamMeta currentClaimer, ArrayList<TeamMeta> attackers) -> {
                // add to attackers if not already, check dominant material otherwise
                if (attackers.contains(team) && getMaterialCountFor(team) == 25) {
                    // this attacker has claimed the cube now
                    updateCubeState(new State.Claimed(currentClaimer, true));
                    p.getServer().getScheduler().runTaskLater(p, () -> {
                        updateCubeState(new State.Claimed(currentClaimer, false));
                    }, 200);
                } else {
                    attackers.add(team);
                }
            }
            case State.Unclaimed _unclaimed -> {
                updateCubeState(new State.Claimed(team, false));
            }
        };
    }


    @EventHandler
    public void keepChunkLoaded(ChunkUnloadEvent e) {
        if (woolLocations.stream().map(l -> l.getChunk().getChunkKey())
                .anyMatch(c -> c == e.getChunk().getChunkKey())) {
            e.getChunk().load();
        }
    }


    // Spawn cube with current claim (or no claim if unclaimed)
    public void spawnCube() {
        // create 3x3 region around point and fill with material.
        World world = root.getWorld();
        Material m = (cubeState instanceof State.Claimed(TeamMeta claimer, boolean _cd))
                ? claimer.woolColour()
                : p.teams.get(null).woolColour();
        for (Location woolLocation : woolLocations) {
            if (!woolLocation.isChunkLoaded() && !woolLocation.getChunk().load(false)) {
                throw new RuntimeException("failed ot load chunk while trying to spawn wool cube at " + woolLocation);
            }
            woolLocation.getBlock().setType(m);
        }
    }

    // for commands.
    public Component cubeBrief() {
        Component loreText;
        Style style;
        switch (cubeState) {
            case State.Claimed(TeamMeta claimer, boolean cooldown) -> {
                style = Style.style(claimer.colour());
                loreText = Component.text("Claimed by " + claimer.scoreboardTeam().getName() + ".")
                        .appendNewline()
                        .append(Component.text(cooldown ? "Players can respawn here." : "Players cannot respawn here."));
            }
            case State.Unclaimed unclaimed -> {
                style = Style.style(p.unclaimedColour);
                loreText = Component.text("Unclaimed!");
            }
            case State.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) -> {
                style = Style.style(claimer.colour(), TextDecoration.BOLD);
                loreText = Component.text("Under attack by: ")
                        .append(Component.join(JoinConfiguration.newlines(),
                                attackers.stream().map(TeamMeta::name).toList()
                        ));
            }
        }
        return Component.text("[" + root.getX() + " " + root.getY() + " " + root.getZ() + "]")
                .style(style)
                .hoverEvent(HoverEvent.showText(loreText))
                .clickEvent(ClickEvent.suggestCommand("/ctw cube at " + root.getX() + " " + root.getY() + " " + root.getZ() + " "));
    }
    static Set<Location> allWallBlocks(Location root) {
        Set<Location> locations = new HashSet<>();
        for (double x = -1; x <= 1; x++) {
            for (double y = -1; y <= 1; y++) {
                for (double z = -1; z <= 1; z++) {
                    if (x==0 && y!=1 && z==0) { continue;}
                    locations.add(root.add(x, y, z));
                }
            }
        }
        return Collections.unmodifiableSet(locations);
    }

    public Stream<Material> getAllMaterialsOnWalls() {
        return woolLocations.stream().map(Location::getBlock).map(Block::getType);
    }
    public Map<Material, Long> materialCountMap() {
        return woolLocations.stream()
                .map(Location::getBlock)
                .map(Block::getType)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
    public boolean isWallsAllMadeOf(Material material) {
        return getAllMaterialsOnWalls().allMatch(m -> m == material);
    }
    public Material getDominantMaterial() {
        return materialCountMap().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
    }
    public int getMaterialCountFor(TeamMeta team) {
        return woolLocations.stream()
                .map(Location::getBlock)
                .map(Block::getType)
                .filter(b -> b.equals(team.woolColour()))
                .toList()
                .size();
    }
    // Reveal location by spawning an end gateway for N ticks
    public void revealLocation(CaptureTheWool p) {
        Location skyBlock = BlockUtil.getFirstSkyExposedBlock(root);
        skyBlock.getBlock().setType(Material.END_GATEWAY);

        p.getServer().getScheduler().runTaskLater(p, task -> {
            skyBlock.getBlock().setType(Material.AIR);
        }, 20);
    }
}
