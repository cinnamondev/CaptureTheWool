package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.events.CubeAttackEvent;
import com.github.cinnamondev.captureTheWool.events.CubeClaimedEvent;
import com.github.cinnamondev.captureTheWool.events.StateChangeEvent;
import com.github.cinnamondev.captureTheWool.util.BlockUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.text.DecimalFormat;
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
    public final Location respawnLocation;
    private final Component displayName;
    public Component displayName() {
        NamedTextColor color = switch (cubeState) {
            case State.Claimed(TeamMeta claimer, boolean _cd) -> claimer.colour();
            case State.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) when claimer != null -> claimer.colour();
            case null, default -> p.unclaimedColour;
        };
        return displayName.color(color);
    }
    public State cubeState;
    protected boolean updateCubeState(State newState) {
        StateChangeEvent e;
        if (newState instanceof State.UnderAttack attackState && !(cubeState instanceof State.UnderAttack)) {
            // initial attack!
            e = new CubeAttackEvent(this, cubeState, attackState, attackState.attackers.getFirst());
        } else if (newState instanceof State.Claimed claimedState && !(cubeState instanceof State.Claimed)) {
            e = new CubeClaimedEvent(this, cubeState, claimedState);
        } else {
            e = new StateChangeEvent(this, cubeState, newState);
        }

        boolean updated = e.callEvent();
        if (updated) {
            p.getLogger().info("updated cube state from " + (cubeState != null ? cubeState.getClass().getName() : "none") + " to " + newState.getClass().getName() );
            this.cubeState = newState; // if not cancelled update
        }
        return updated;
    }
    protected final Set<Location> woolLocations;
    public WoolCube(CaptureTheWool p, Location root, Component displayName, @Nullable TeamMeta currentOwner) {
        this.p = p;
        this.displayName = displayName;

        this.root = root;
        this.respawnLocation = root.add(0, 4, 0);
        woolLocations = allWallBlocks(root);

        World world = root.getWorld();
        for (Location woolLocation : woolLocations) {
            Chunk chunk = woolLocation.getChunk();
            world.setChunkForceLoaded(chunk.getX(), chunk.getZ(), true);
        }
        
        if (currentOwner == null) {
            updateCubeState(new State.Unclaimed());
        } else {
            updateCubeState(new State.Claimed(currentOwner, false));
        }
    }

    @EventHandler
    public void playerBreakBlock(BlockBreakEvent e) {
        if (!woolLocations.contains(e.getBlock().getLocation())) {
            return;
        }

        Player player = e.getPlayer();
        TeamMeta team = p.getPlayerTeam(player);
        if (team == null) {
            p.getLogger().info("Player " + player.getName() + " has no wool team, ignoring");
            return;
        }
        Material m = team.woolColour();

        e.setCancelled(true);
        Material oldColour = e.getBlock().getType();

        State previousState = cubeState;
        State newState;
        boolean updated = switch (cubeState) {
            case State.Claimed(TeamMeta currentClaimer, boolean _cooldown) -> {
                if (!currentClaimer.equals(team)) {
                    yield updateCubeState(new State.UnderAttack(currentClaimer, new ArrayList<>(Collections.singleton(team))));
                    // we are now under attack
                } else { yield false; } // currnet claimer same
            }
            case State.UnderAttack(TeamMeta currentClaimer, ArrayList<TeamMeta> attackers) -> {
                // add to attackers if not already, check dominant material otherwise
                if (getMaterialCountFor(m) == 24 && e.getBlock().getType() != m) { // is a non team block and one more to go. by omission this is the last block.
                    // this attacker has claimed the cube now
                    boolean isClaimAccepted = updateCubeState(new State.Claimed(team, true));
                    if (isClaimAccepted) {
                        p.getServer().getScheduler().runTaskLater(p, () -> {
                            updateCubeState(new State.Claimed(team, false));
                        }, 200);
                    }
                    yield isClaimAccepted;
                } else if (!attackers.contains(team) && !Objects.equals(currentClaimer, team)) {
                    attackers.add(team);
                }
                yield true;
            }
            case State.Unclaimed _unclaimed -> {
                if (updateCubeState(new State.Claimed(team, false))) {
                    woolLocations.forEach(l -> l.getBlock().setType(m));
                    yield true;
                } else { yield false; }
            }
        };

        if (updated) {
            e.getBlock().setType(m);
        } else {
            e.getBlock().setType(oldColour);
        }
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
        Material m = switch (cubeState) {
            case State.Claimed(TeamMeta claimer, boolean _cd) -> claimer.woolColour();
            case State.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) -> claimer.woolColour();
            case State.Unclaimed unclaimed -> p.unclaimedMaterial;
        };

        for (Location woolLocation : woolLocations) {
            if (!woolLocation.isChunkLoaded() && !woolLocation.getChunk().load(false)) {
                throw new RuntimeException("failed ot load chunk while trying to spawn wool cube at " + woolLocation);
            }
            woolLocation.getBlock().setType(m);
        }
    }

    // for commands.
    public Component cubeBrief() {
        Style style = switch (cubeState) {
            case State.Claimed(TeamMeta claimer, boolean cooldown) -> Style.style(claimer.colour());
            case State.UnderAttack attackState -> Style.style(attackState.claimer().colour(), TextDecoration.BOLD);
            case null,default -> Style.style(p.unclaimedColour);
        };
        return Component.text("[" + root.getBlockX() + " " + root.getBlockY() + " " + root.getBlockZ() + "]")
                .style(style)
                .hoverEvent(HoverEvent.showText(lore()))
                .clickEvent(ClickEvent.suggestCommand("/ctw cube at " + root.getBlockX() + " " + root.getBlockY() + " " + root.getBlockZ() + " " + root.getWorld() + " "));
    }

    public Component lore() {
        return switch (cubeState) {
            case State.Claimed(TeamMeta claimer, boolean cooldown) ->
                    Component.text("Claimed by " + claimer.scoreboardTeam().getName() + ".")
                        .appendNewline()
                        .append(Component.text(cooldown ? "Players cannot respawn here." : "Players can respawn here."));
            case State.UnderAttack attackState -> underAttackInfo(this, attackState);
            case null,default -> Component.text("Unclaimed!");
        };
    }
    private static final DecimalFormat df = new DecimalFormat("#.##%");
    public static Component underAttackInfo(WoolCube cube, State.UnderAttack state) {
        var countMap = cube.materialCountMap();
        TeamMeta currentClaimer = state.claimer();
        var ratioCurrent = df.format(countMap.get(currentClaimer.woolColour()) / 25f);
        Component percentageList = Component.join(
                JoinConfiguration.newlines(),
                state.attackers().stream()
                        .map(t -> t.name()
                                .append(Component.text(" (" + df.format(countMap.get(t.woolColour()) / 25f) + ")"))
                                .color(t.colour())
                        )
                        .toList()
        );

        return Component.join(JoinConfiguration.newlines(),
                Component.text("Under attack!"),
                Component.text("Claiming team: ")
                        .append(currentClaimer.name())
                        .append(Component.text(" (" + ratioCurrent + ")")),
                Component.text("Attacking teams: "),
                percentageList
        );
    }
    static Set<Location> allWallBlocks(Location root) {
        Set<Location> locations = new HashSet<>();
        for (int x = -1; x <= 1; x+=1) {
            for (int y = -1; y <= 1; y+=1) {
                for (int z = -1; z <= 1; z+=1) {
                    if (x==0 && y!=1 && z==0) { continue;}
                    locations.add(new Location(root.getWorld(), root.blockX() + x, root.blockY() + y, root.blockZ() + z).toBlockLocation());
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
    public Material getDominantMaterial() {
        return materialCountMap().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
    }
    public int getMaterialCountFor(Material m) {
        return woolLocations.stream()
                .map(Location::getBlock)
                .map(Block::getType)
                .filter(m2 -> m2 == m)
                .toList()
                .size();
    }
    // Reveal location by spawning an end gateway for N ticks
    public void revealLocation(CaptureTheWool p) {
        Location skyBlock = BlockUtil.getFirstSkyExposedBlock(root);
        skyBlock.getBlock().setType(Material.END_GATEWAY);

        p.getServer().getScheduler().runTaskLater(p, task -> {
            skyBlock.getBlock().setType(Material.AIR);
        }, 100);
    }

    public static Optional<WoolCube> getNearestCube(Location current, List<WoolCube> cubes) {
        if (cubes.isEmpty()) { return Optional.empty(); }
        return cubes.stream().min(Comparator.comparing(c -> c.respawnLocation.distanceSquared(current)));
    }
}
