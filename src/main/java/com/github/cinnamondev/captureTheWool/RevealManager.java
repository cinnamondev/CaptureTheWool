package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.woolCube.CubeState;
import com.github.cinnamondev.captureTheWool.woolCube.events.CubeAttackEvent;
import com.github.cinnamondev.captureTheWool.woolCube.events.CubeClaimedEvent;
import com.github.cinnamondev.captureTheWool.woolCube.events.StateChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Stream;

public class RevealManager implements Listener {
    private final Plugin p;
    enum Reach {
        ALL,
        PARTIES,
        DEFENDERS,
        ATTACKERS
    }
    private final HashMap<StateChangeEvent.Reason, Reach> config = new HashMap<>();

    public RevealManager(Plugin p) {
        this.p = p;
    }
    @EventHandler
    public void onCubeClaim(CubeClaimedEvent e) {
        //Collection<Player> players = Collections.emptyList();

        Collection<Player> players = switch (e.previousState()) {
            case CubeState.Unclaimed unclaimed -> {
                Reach reach = config.get(StateChangeEvent.Reason.InitialClaim);
                if (reach == null) { yield Collections.emptyList(); }
                yield switch (reach) {
                    case ALL -> (Collection<Player>) p.getServer().getOnlinePlayers();
                    case ATTACKERS, PARTIES -> e.newClaimers().getOnlinePlayers(p).toList();
                    case null, default -> Collections.emptyList();
                };
            }
            case CubeState.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) -> {
                Reach reach = config.get(StateChangeEvent.Reason.CompletedAttack);
                if (reach == null) { yield Collections.emptyList(); }
                var attackPlayers = attackers.stream().flatMap(t -> t.getOnlinePlayers(p));
                var defendPlayers = claimer.getOnlinePlayers(p);
                yield switch (reach) {
                    case ALL -> (Collection<Player>) p.getServer().getOnlinePlayers();
                    case PARTIES -> Stream.concat(attackPlayers, defendPlayers).toList();
                    case ATTACKERS -> attackPlayers.toList();
                    case DEFENDERS -> defendPlayers.toList();
                };
            }
            case null, default -> Collections.emptyList();
        };
        e.cube().revealLocation(players);
    }

    @EventHandler
    public void onCubeAttack(CubeAttackEvent e) {
        Reach reach = config.get(StateChangeEvent.Reason.AttackUnderway);
        if (reach == null) { return; }

        var attackPlayers = e.attackingTeam().getOnlinePlayers(p);
        var defendPlayers = e.defendingTeam().getOnlinePlayers(p);
        Collection<Player> players = switch (reach) {
            case ALL -> (Collection<Player>) p.getServer().getOnlinePlayers();
            case PARTIES -> Stream.concat(attackPlayers, defendPlayers).toList();
            case ATTACKERS -> attackPlayers.toList();
            case DEFENDERS -> defendPlayers.toList();
        };
        e.cube().revealLocation(players);
        //e.attackingTeam()
    }

    @EventHandler
    public void respawnUnlockedEvent(StateChangeEvent e) {
        Reach reach = config.get(StateChangeEvent.Reason.AttackUnderway);
        if (reach == null) { return; }
        if (e.newState() instanceof CubeState.Claimed(TeamMeta claimer, boolean newCooldown)
                && e.previousState() instanceof CubeState.Claimed(TeamMeta claimer1, boolean oldCooldown)
                && !newCooldown && oldCooldown) {
            var defendPlayers = claimer.getOnlinePlayers(p);

            Collection<Player> players =switch (reach) {
                case ALL -> (Collection<Player>) p.getServer().getOnlinePlayers();
                case PARTIES, DEFENDERS -> defendPlayers.toList();
                default -> Collections.emptyList();
            };

            e.cube().revealLocation(players);
        }
    }
}
