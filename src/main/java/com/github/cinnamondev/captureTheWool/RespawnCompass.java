package com.github.cinnamondev.captureTheWool;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;

public class RespawnCompass implements Listener {
    private CaptureTheWool p;
    public RespawnCompass(CaptureTheWool p) {
        this.p = p;
    }    // idea
    // player can only respawn on a location when
    // the area is fully claimed by their team
    //   they have set that location to respawn
    //     you can onyl respawn on a location if it is 'out of grace' (see, WoolCube implementation of State)

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        // we want to see if a players team is entirely gone (no cubes available. because if they are, theyre DONEZO.)
        //e.deathMessage().appendNewline().append(Component.text("... and has been ELIMINATED."));
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
