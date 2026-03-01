package com.github.cinnamondev.captureTheWool.items;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCubeSnapshot;
import com.github.cinnamondev.captureTheWool.WoolCube.events.CubeAttackEvent;
import com.github.cinnamondev.captureTheWool.WoolCube.events.CubeClaimedEvent;
import com.github.cinnamondev.captureTheWool.dialogs.RespawnCompassDialog;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.sampling.BestCandidateSampling;

import java.util.*;
import java.util.stream.Stream;

public class RespawnCompass implements Listener {
    private CaptureTheWool p;
    public RespawnCompass(CaptureTheWool p) {
        this.p = p;
    }    // idea
    // player can only respawn on a location when
    // the area is fully claimed by their team
    //   they have set that location to respawn
    //     you can onyl respawn on a location if it is 'out of grace' (see, WoolCube implementation of State)

    static final @NotNull NamespacedKey key = Objects.requireNonNull(NamespacedKey.fromString("capturethewool:respawncompass"));

    private static boolean isItem(ItemStack itemStack) {
        return itemStack.getType() == Material.COMPASS
                && itemStack.getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN) != null;
    }
    private static @Nullable ItemStack extractCompassFromPlayer(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isItem(item)) {
                return item;
            }
        }
        return null; // if no returned item, null.
    }

    public static ItemStack createItem() {
        ItemStack itemStack = ItemStack.of(Material.COMPASS);
        CompassMeta itemMeta = (CompassMeta) itemStack.getItemMeta();
        itemMeta.setMaxStackSize(1);
        itemMeta.customName(Component.text("Respawn Compass"));
        itemMeta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    @EventHandler
    public void cycleCompassMenu(PlayerInteractEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        if (!isItem(item)) { return; }
        TeamMeta t = p.getPlayerTeam(e.getPlayer());
        if (t == null) { return; }
        e.setCancelled(true);
        e.getPlayer().showDialog(RespawnCompassDialog.dialog(e.getPlayer().getLocation(), t.getCubesOwnedBy(p).toList()));
    }

    @EventHandler
    public void onDialogInteraction(PlayerCustomClickEvent e) {

        if (!(e.getCommonConnection() instanceof PlayerGameConnection connection)
                || !e.getIdentifier().equals(RespawnCompassDialog.setLocationKey)
                || e.getDialogResponseView() == null) { return; }

        RespawnCompassDialog.SnbtToLocation(p, e.getDialogResponseView().payload().string()).ifPresentOrElse(l -> {
            p.getLogger().info(l.toString());
            cubeLocationUpdateHandler(connection.getPlayer(), l);
        }, () -> p.getLogger().warning("player has sent in a malformed snbt string? see \n" +  e.getDialogResponseView().payload().string()));
    }

    private void cubeLocationUpdateHandler(Player player, Location recievedLocation) {
        TeamMeta playerTeam = p.getPlayerTeam(player);
        if (playerTeam == null) { return; }

        // THIS INFORMATION IS BEING SENT BY THE CLIENT! so we have to double check that we can actually do what its
        // asking us to do.
        p.findWoolCubeAt(recievedLocation, true)
                .filter(c ->
                        c.cubeState instanceof CubeState.Claimed(TeamMeta claimer, boolean respawnCooldownActive)
                                && claimer.equals(playerTeam) && !respawnCooldownActive)
                .ifPresentOrElse(c -> {
                    // we have to validate this is a valid respawn location first.
                    ItemStack item = extractCompassFromPlayer(player);
                    if (item != null) {
                        CompassMeta meta = (CompassMeta) item.getItemMeta();
                        meta.setLodestone(c.respawnLocation);
                        meta.setLodestoneTracked(false);
                        meta.customName(Component.text("Respawn Compass"));
                        meta.setEnchantmentGlintOverride(false);
                        item.setItemMeta(meta);
                        CaptureTheWool.setRespawnLocations.put(player.getUniqueId(), c);
                    }
                }, () -> {
                    p.getLogger().warning("Warning!! Player is either sending funny data or we just had really bad timing.");
                    //player.sendMessage("");
                });
    }
    @EventHandler
    public void preventCompassDrop(PlayerDropItemEvent e) {
        if (isItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void preventCompassDropOnDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(RespawnCompass::isItem);
    }

    @EventHandler
    public void notifyOnRespawnLoss(CubeAttackEvent e) {
        Audience audience = e.newState().claimer().filterAudience(a -> a instanceof Player player // filter audience to Players who have their respawn location set to this cube.
                && CaptureTheWool.setRespawnLocations.get(player.getUniqueId()).equals(e.cube()));

        audience.sendActionBar(Component.text("Your respawn point is under attack!").color(NamedTextColor.RED));
    }
    //@EventHandler
    //public void onStateUpdate(StateChangeEvent e) {
    //    e.relevantTeams().stream().forEach(this::updateRespawnLocationsFor);
    //}

    // TODO: should we instead punish players for not appropriately setting their respawn location.
    // TODO: they will know when we set it, so maybe we should punish them by respawning them as far away from their
    // TODO: position as possible if they fail to follow the notifications.
    // TODO: ADDITONAL: Perhaps cubeclaims should be announced via bossbar. i wonder if we can do a progression for when theyre in attack?
    // https://docs.papermc.io/adventure/bossbar/

    //private void updateRespawnLocationFor(Player player, List<WoolCube> ownedCubes) {
    //    ItemStack compass = extractCompassFromPlayer(player);
    //    if (compass == null) {
    //        player.give(createItem());
    //        WoolCube.getNearestCube(player.getLocation(), ownedCubes).ifPresent(cube -> {
    //            p.setRespawnLocations.put(player.getUniqueId(), cube); // as it will double check which ones are candidates there.
    //        });
    //        return;
    //    }
//
    //    CompassMeta meta = (CompassMeta) compass.getItemMeta();
    //    if (!meta.hasLodestone()
    //            || ownedCubes.stream().anyMatch(w -> meta.getLodestone().equals(w.respawnLocation))) {
//
    //        // Player candidate cube doesnt exist!
    //        WoolCube.getNearestCube(player.getLocation(), ownedCubes).ifPresent(cube -> {
    //            meta.setLodestone(cube.respawnLocation); // this should always ensure there's a viable respawn location for the player. see `setRespawnPoint`,
    //            p.setRespawnLocations.put(player.getUniqueId(), cube); // as it will double check which ones are candidates there.
    //        });
    //    }
    //}

    //void updateRespawnLocationFor(Player player) {
    //    TeamMeta team = p.getPlayerTeam(player);
    //    if (team == null) { return; }
    //    List<WoolCube> ownedCubes = team.getCubesOwnedBy(p).toList();
    //    updateRespawnLocationFor(player, ownedCubes);
    //}
//
    //void updateRespawnLocationsFor(TeamMeta team) {
    //    List<WoolCube> ownedCubes = team.getCubesOwnedBy(p).toList();
//
    //    for (Player player : p.getServer().getOnlinePlayers()) {
    //        if (team.scoreboardTeam().hasPlayer(player)) {
    //            updateRespawnLocationFor(player, ownedCubes);
    //        }
    //    }
    //}

    @EventHandler
    public void setRespawnPoint(PlayerRespawnEvent e) {

        TeamMeta playerTeam = p.getPlayerTeam(e.getPlayer());

        List<WoolCube> respawnCandidates = CaptureTheWool.cubes.stream()
                .filter(cube ->
                        cube.cubeState instanceof CubeState.Claimed(TeamMeta team, boolean cooldownActive)
                                && team.equals(playerTeam) && !cooldownActive
                )
                .sorted(Comparator.comparing(c ->
                        c.root.getWorld().equals(e.getPlayer().getLocation().getWorld())
                                ? c.root.distanceSquared(e.getPlayer().getLocation())
                                : Double.MAX_VALUE // put them all at the end if not of this world.
                ))
                .toList();

                p.getRespawnCandidatesFor(playerTeam).stream()
                .sorted(Comparator.comparing(c ->
                        c.root.getWorld().equals(e.getPlayer().getLocation().getWorld())
                                ? c.root.distanceSquared(e.getPlayer().getLocation())
                                : Double.MAX_VALUE // put them all at the end if not of this world.
                ));

        if (playerTeam == null || respawnCandidates.isEmpty()) {
            // Player is DEAD. (or has no team, therefore not playing The Game.)
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
            return;
        }

        WoolCube playerAssignedLocation = CaptureTheWool.setRespawnLocations.get(e.getPlayer().getUniqueId());
        if (playerAssignedLocation == null
                || !respawnCandidates.contains(playerAssignedLocation)) {
            // player hasnt assigned a location or it isnt a feasible respawn location anymore.
            // Thus, we will punish them by spawning them as far away as possible.
            e.setRespawnLocation(respawnCandidates.getLast().respawnLocation);
        } else {
            e.setRespawnLocation(playerAssignedLocation.respawnLocation);
        }
    }

}
