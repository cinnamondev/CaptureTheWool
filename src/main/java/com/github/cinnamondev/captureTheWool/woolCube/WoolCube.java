package com.github.cinnamondev.captureTheWool.woolCube;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.woolCube.events.CubeAttackEvent;
import com.github.cinnamondev.captureTheWool.woolCube.events.CubeClaimedEvent;
import com.github.cinnamondev.captureTheWool.woolCube.events.CubeDamageEvent;
import com.github.cinnamondev.captureTheWool.woolCube.events.StateChangeEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WoolCube implements Listener {
    private final CaptureTheWool p;
    public final Location root;
    public final Location respawnLocation;
    private final Component displayName;
    private final BossBar bossBar;
    protected final UUID uuid;
    public UUID uuid() { return uuid; }
    public BossBar bossBar() { return bossBar; }
    public Component displayName() {
        NamedTextColor color = switch (cubeState) {
            case CubeState.Claimed(TeamMeta claimer, boolean _cd) -> claimer.colour();
            case CubeState.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) when claimer != null -> claimer.colour();
            case null, default -> CaptureTheWool.UNCLAIMED_COLOUR;
        };
        return displayName.color(color);
    }
    public CubeState cubeState;
    public boolean updateCubeState(CubeState newState, @Nullable Player causingPlayer) {
        StateChangeEvent e;
        e = switch (newState) {
            case CubeState.UnderAttack attackState when !(cubeState instanceof CubeState.UnderAttack) ->
                    new CubeAttackEvent(this, false, cubeState, attackState, attackState.attackers().getLast(),causingPlayer);
            case CubeState.UnderAttack newAttack when cubeState instanceof CubeState.UnderAttack oldAttack
                        && !newAttack.attackers().equals(oldAttack.attackers()) ->
                    new CubeAttackEvent(this, true, cubeState, newAttack, newAttack.attackers().getLast(),causingPlayer);
            case CubeState.UnderAttack attackState when cubeState instanceof CubeState.UnderAttack prevAttackState ->
                    new CubeDamageEvent(this, prevAttackState, attackState, attackState.attackers().getFirst(),causingPlayer);
            case CubeState.Claimed claimedState when !(cubeState instanceof CubeState.Claimed)
                    -> new CubeClaimedEvent(this,cubeState,claimedState, causingPlayer);
            default -> new StateChangeEvent(this, cubeState, newState, causingPlayer);
        };

        boolean updated = e.callEvent();
        if (updated) {
            //p.getLogger().info("updated cube state from " + (cubeState != null ? cubeState.getClass().getName() : "none") + " to " + newState.getClass().getName() );
            this.cubeState = newState; // if not cancelled update
        }
        return updated;
    }
    public final Set<Location> woolLocations;
    public WoolCube(CaptureTheWool p, UUID uuid, Location root, Component displayName, @Nullable TeamMeta currentOwner) {
        this(p,
                uuid,
                root,
                displayName,
                currentOwner == null ? new CubeState.Unclaimed() : new CubeState.Claimed(currentOwner, false));
    }

    WoolCube(CaptureTheWool p, UUID uuid, Location root, Component displayName, CubeState initialState) {
        this.p = p;
        this.root = root.toBlockLocation();
        this.displayName = displayName;
        this.respawnLocation = root.clone().add(0,2,0);
        this.cubeState = initialState;
        this.uuid = uuid;
        woolLocations = allWallBlocks(root);
        allSurroundingBlocks = WoolCube.allSurroundingBlocks(root);
        World world = root.getWorld();
        for (Location woolLocation : woolLocations) {
            Chunk chunk = woolLocation.getChunk();
            world.setChunkForceLoaded(chunk.getX(), chunk.getZ(), true);
        }
        this.bossBar = BossBar.bossBar(this::displayName, 1, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    }
    public WoolCubeSnapshot createSnapshot() { return new WoolCubeSnapshot(uuid, root, cubeState, displayName); }
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

        CubeState previousState = cubeState;
        CubeState newState;

        int diff = 0;
        Material originalMaterial = CaptureTheWool.UNCLAIMED_MATERIAL;
        boolean updated = switch (cubeState) {
            case CubeState.Claimed(TeamMeta currentClaimer, boolean _cooldown) -> {
                originalMaterial = currentClaimer.woolColour();
                if (team.equals(currentClaimer)) { diff = 1; }
                if (!currentClaimer.equals(team)) {
                    yield updateCubeState(new CubeState.UnderAttack(currentClaimer, new ArrayList<>(Collections.singleton(team))), player);
                    // we are now under attack
                } else { yield false; } // currnet claimer same
            }
            case CubeState.UnderAttack(TeamMeta currentClaimer, ArrayList<TeamMeta> attackers)
                    when getMaterialCountFor(m) == 24 && e.getBlock().getType() != m -> { // initial attack
                // add to attackers if not already, check dominant material otherwise
                // is a non team block and one more to go. by omission this is the last block.
                // this attacker has claimed the cube now
                if (currentClaimer != null) { originalMaterial = currentClaimer.woolColour();}
                if (team.equals(currentClaimer)) { diff = 1; } else { diff = -1; }
                boolean isClaimAccepted = updateCubeState(new CubeState.Claimed(team, true),player);
                if (isClaimAccepted) {
                    p.getServer().getScheduler().runTaskLater(p, () -> {
                        if (cubeState instanceof CubeState.Claimed(TeamMeta claimer, boolean respawnCooldownActive)
                                && respawnCooldownActive && claimer == team) { // if another team attacks in a short time, this might not be true.
                            updateCubeState(new CubeState.Claimed(team, false),player);
                        }
                    }, p.getConfig().getInt("respawn-cooldown", 200));
                }
                yield isClaimAccepted;
            }
            case CubeState.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers)
                    when !attackers.contains(team) && !team.equals(claimer) -> {
                if (claimer != null) { originalMaterial = claimer.woolColour();}
                if (team.equals(claimer)) { diff = 1; } else { diff = -1; }
                var list = attackers;
                attackers.add(team);
                yield updateCubeState(new CubeState.UnderAttack(claimer, list),player);
            }
            case CubeState.UnderAttack underAttack -> {
                if (underAttack.claimer() != null) { originalMaterial = underAttack.claimer().woolColour();}
                if (team.equals(underAttack.claimer())) { diff = 1; } else { diff = -1; }
                yield updateCubeState(underAttack,player);
            } // subsequent attakc not an tack on
            case CubeState.Unclaimed _unclaimed -> {
                if (!m.equals(CaptureTheWool.UNCLAIMED_MATERIAL)) { diff = -1; }
                if (updateCubeState(new CubeState.Claimed(team, false),player)) {
                    woolLocations.forEach(l -> l.getBlock().setType(m));
                    yield true;
                } else { yield false; }
            }
        };

        if (updated) {
            this.bossBar.progress(((float) getMaterialCountFor(originalMaterial) + diff)/25f);
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
            case CubeState.Claimed(TeamMeta claimer, boolean _cd) -> claimer.woolColour();
            case CubeState.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) -> claimer.woolColour();
            case CubeState.Unclaimed unclaimed -> CaptureTheWool.UNCLAIMED_MATERIAL;
        };

        for (Location woolLocation : woolLocations) {
            if (!woolLocation.isChunkLoaded() && !woolLocation.getChunk().load(false)) {
                throw new RuntimeException("failed ot load chunk while trying to spawn wool cube at " + woolLocation);
            }
            woolLocation.getBlock().setType(m);
        }
        root.clone().subtract(0,1,0).getBlock().setType(Material.BEDROCK);
    }

    // for commands.
    public Component cubeBrief() {
        Style style = switch (cubeState) {
            case CubeState.Claimed(TeamMeta claimer, boolean cooldown) -> Style.style(claimer.colour());
            case CubeState.UnderAttack attackState -> Style.style(attackState.claimer().colour(), TextDecoration.BOLD);
            case null,default -> Style.style(CaptureTheWool.UNCLAIMED_COLOUR);
        };
        return Component.text("[" + root.getBlockX() + " " + root.getBlockY() + " " + root.getBlockZ() + "]")
                .style(style)
                .hoverEvent(HoverEvent.showText(lore()))
                .clickEvent(ClickEvent.suggestCommand("/ctw cube at " + root.getBlockX() + " " + root.getBlockY() + " " + root.getBlockZ() + " " + root.getWorld().key() + " "));
    }

    public Component lore() {
        return switch (cubeState) {
            case CubeState.Claimed(TeamMeta claimer, boolean cooldown) ->
                    Component.text("Claimed by " + claimer.scoreboardTeam().getName() + ".")
                        .appendNewline()
                        .append(Component.text(cooldown ? "Players cannot respawn here." : "Players can respawn here."));
            case CubeState.UnderAttack attackState -> underAttackInfo(this, attackState);
            case null,default -> Component.text("Unclaimed!");
        };
    }
    private static final DecimalFormat df = new DecimalFormat("#.##%");
    public static Component underAttackInfo(WoolCube cube, CubeState.UnderAttack state) {
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

    static Set<Location> allSurroundingBlocks(Location root) {
        Set<Location> locations = new HashSet<>();
        for (int x = -2; x <= 2; x+=1) {
            for (int y = -2; y <= 3; y+=1) { // we want extra Y to give room for players to respawn!
                for (int z = -2; z <= 2; z+=1) {
                    //if (x==0 && y!=1 && z==0) { continue;}
                    locations.add(new Location(root.getWorld(), root.blockX() + x, root.blockY() + y, root.blockZ() + z).toBlockLocation());
                }
            }
        }
        return Collections.unmodifiableSet(locations);
    }
    private final Set<Location> allSurroundingBlocks;

    @EventHandler
    public void preventBlockPlace(BlockPlaceEvent e) {
        //if (e.getPlayer().hasPermission("ctw.bypass-wool-protection")) { return; }
        if (allSurroundingBlocks.contains(e.getBlock().getLocation())) {
            e.getPlayer().sendMessage(Component.text("You're gonna have to try a little harder than THAT."));
            e.setBuild(false);
            e.setCancelled(true);
        }
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
    public void revealLocation() {
        revealLocation((Collection<Player>) p.getServer().getOnlinePlayers());
    }

    public void revealLocation(Collection<Player> players) {
        BlockData oldBlock = root.getBlock().getBlockData();
        players.forEach(p -> p.sendBlockChange(root, Material.END_GATEWAY.createBlockData()));

        p.getServer().getScheduler().runTaskLater(p, task -> {
            players.forEach(p -> p.sendBlockChange(root, oldBlock));
        }, p.getConfig().getInt("wool-reveal.gateway-duration", 40));
    }


    public static Optional<WoolCube> getNearestCube(Location current, List<WoolCube> cubes) {
        if (cubes.isEmpty()) { return Optional.empty(); }
        return cubes.stream().min(Comparator.comparing(c -> c.respawnLocation.distanceSquared(current)));
    }

}
