package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.events.CubeAttackEvent;
import com.github.cinnamondev.captureTheWool.WoolCube.events.CubeClaimedEvent;
import com.github.cinnamondev.captureTheWool.WoolCube.events.StateChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RevealManager implements Listener {
    private final Plugin p;
    enum Reach {
        ALL,
        PARTIES,
        DEFENDERS,
        ATTACKERS
    }
    private HashMap<StateChangeEvent.Reason, Reach> config = new HashMap<>();

    public RevealManager(Plugin p) {
        this.p = p;
    }
    @EventHandler
    public void onCubeClaim(CubeClaimedEvent e) {
        Collection<Player> players = Collections.emptyList();
        if (e.previousState() instanceof CubeState.Unclaimed) {
            Reach reach = config.get(StateChangeEvent.Reason.InitialClaim);
            if (reach == null) { return; }
            players = switch (reach) {
                case ALL -> (Collection<Player>) p.getServer().getOnlinePlayers();
                case ATTACKERS, PARTIES ->
                        e.newClaimers().scoreboardTeam().getEntries().stream()
                        .map(str -> p.getServer().getPlayer(str))
                        .toList();
                case null, default -> Collections.emptyList();
            };

        } else if (e.previousState() instanceof CubeState.UnderAttack(TeamMeta defending, ArrayList<TeamMeta> attackers)){
            Reach reach = config.get(StateChangeEvent.Reason.CompletedAttack);
            if (reach == null) { return; }
            var attackPlayers = attackers.stream()
                    .flatMap(t -> t.scoreboardTeam().getEntries().stream()
                            .map(str -> p.getServer().getPlayer(str))
                    );
            var defendPlayers = defending.scoreboardTeam().getEntries().stream().map(str -> p.getServer().getPlayer(str));
            players = switch (reach) {
                case ALL -> (Collection<Player>) p.getServer().getOnlinePlayers();
                case PARTIES -> Stream.concat(attackPlayers, defendPlayers).toList();
                case ATTACKERS -> attackPlayers.toList();
                case DEFENDERS -> defendPlayers.toList();
            };
        }
        e.cube().revealLocation(players);
    }

    @EventHandler
    public void onCubeAttack(CubeAttackEvent e) {
        //e.attackingTeam()
    }
}
