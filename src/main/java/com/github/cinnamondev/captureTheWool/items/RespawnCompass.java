package com.github.cinnamondev.captureTheWool.items;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import com.github.cinnamondev.captureTheWool.dialogs.RespawnCompassDialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
        itemMeta.itemName(Component.text("Respawn Compass"));
        itemMeta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        return itemStack;
    }

    @EventHandler
    public void cycleCompassMenu(PlayerInteractEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        if (!isItem(item)) { return; }
        TeamMeta t = p.getPlayerTeam(e.getPlayer());
        if (t == null) { return; }
        e.setCancelled(true);
        e.getPlayer().showDialog(RespawnCompassDialog.dialog(t.getCubesOwnedBy(p).toList()));
    }

    @EventHandler
    public void onDialogInteraction(PlayerCustomClickEvent e) {
        if (!e.getIdentifier().equals(RespawnCompassDialog.setLocationKey)) { return; }


    }

    @EventHandler
    public void preventCompassDrop(PlayerDropItemEvent e) {
        if (isItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
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
        List<WoolCube> respawnCandidates = p.getRespawnCandidatesFor(playerTeam);

        if (playerTeam == null || respawnCandidates.isEmpty()) {
            // Player is DEAD. (or has no team, therefore not playing The Game.)
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
            return;
        }

        WoolCube playerAssignedLocation = p.setRespawnLocations.get(e.getPlayer().getUniqueId());
        if (playerAssignedLocation == null
                || (playerAssignedLocation.cubeState instanceof CubeState.Claimed(TeamMeta cubeTeam, boolean canRespawn)
                  && (!canRespawn || !playerTeam.equals(cubeTeam)))) {
            // player hasnt assigned a location or it isnt a feasible respawn location anymore.
            e.setRespawnLocation(respawnCandidates.getFirst().respawnLocation);
        } else {
            e.setRespawnLocation(playerAssignedLocation.respawnLocation);
        }
    }

}
