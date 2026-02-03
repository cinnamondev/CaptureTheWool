package com.github.cinnamondev.captureTheWool.items;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube;
import com.github.cinnamondev.captureTheWool.events.StateChangeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RespawnCompass implements Listener {
    static final @NotNull NamespacedKey key = Objects.requireNonNull(NamespacedKey.fromString("capturethewool:respawncompass"));
    private CaptureTheWool p;
    public RespawnCompass(CaptureTheWool p) {
        this.p = p;
    }    // idea
    // player can only respawn on a location when
    // the area is fully claimed by their team
    //   they have set that location to respawn
    //     you can onyl respawn on a location if it is 'out of grace' (see, WoolCube implementation of State)

    private ItemStack createItem() {
        ItemStack itemStack = ItemStack.of(Material.COMPASS);
        CompassMeta itemMeta = (CompassMeta) itemStack.getItemMeta();
        itemMeta.setMaxStackSize(1);
        itemMeta.itemName(Component.text("Respawn Compass"));
        itemMeta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        return itemStack;
    }

    private @Nullable ItemStack extractCompassFromPlayer(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) { continue; }
            if ((item.getType() == Material.COMPASS) // is compass and has PDC tag
                    && (item.getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN) != null)) {
                return item;
            }
        }
        return null; // if no returned item, null.
    }


    @EventHandler
    public void onStateUpdate(StateChangeEvent e) {
        e.relevantTeams().stream().map(TeamMeta::woolColour)
                .forEach(this::updateRespawnLocationsFor);
    }

    private void updateRespawnLocationsFor(Material material) {
        List<WoolCube> ownedCubes = p.cubes.stream()
                .filter(cube -> cube.cubeState instanceof WoolCube.State.Claimed(
                        TeamMeta claimer, boolean respawnCooldownActive
                ) && !respawnCooldownActive && claimer.woolColour() == material)
                .toList();

        for (Player player : p.getServer().getOnlinePlayers()) {
            Material team = p.getPlayerTeam(player).woolColour();
            if (team != material) { continue; }
            if (team == null) { continue; }

            ItemStack compass = extractCompassFromPlayer(player);
            if (compass == null) {
                WoolCube.getNearestCube(player.getLocation(), ownedCubes).ifPresent(cube -> {
                    p.setRespawnLocations.put(player.getUniqueId(), cube); // as it will double check which ones are candidates there.
                });
                continue;
            }

            CompassMeta meta = (CompassMeta) compass.getItemMeta();
            if (!meta.hasLodestone()
                    || ownedCubes.stream().filter(w -> Objects.equals(meta.getLodestone(), w.respawnLocation)).findFirst().isEmpty()) {
                WoolCube.getNearestCube(player.getLocation(), ownedCubes).ifPresent(cube -> {
                    meta.setLodestone(cube.respawnLocation); // this should always ensure there's a viable respawn location for the player. see `setRespawnPoint`,
                    p.setRespawnLocations.put(player.getUniqueId(), cube); // as it will double check which ones are candidates there.
                });
            }
        }
    }

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
                || (playerAssignedLocation.cubeState instanceof WoolCube.State.Claimed(TeamMeta cubeTeam, boolean canRespawn)
                  && (!canRespawn || !playerTeam.equals(cubeTeam)))) {
            // player hasnt assigned a location or it isnt a feasible respawn location anymore.
            e.setRespawnLocation(respawnCandidates.getFirst().respawnLocation);
        } else {
            e.setRespawnLocation(playerAssignedLocation.respawnLocation);
        }
    }

}
