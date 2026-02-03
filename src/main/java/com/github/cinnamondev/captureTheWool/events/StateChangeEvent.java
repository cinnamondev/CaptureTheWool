package com.github.cinnamondev.captureTheWool.events;

import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StateChangeEvent extends Event implements Cancellable {
    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public enum Reason {
        CompletedAttack,
        AttackTimeout,
        RespawnUnlocked,
        AttackUnderway,
        InitialClaim,
        Undetermined
    }

    private WoolCube cube;

    /**
     * Cube that is being interacted with. IMPORTANT: cube state will be the previous state.
     * @return
     */
    public WoolCube cube() { return this.cube; }
    private @Nullable WoolCube.State previous;
    public WoolCube.State previousState() { return this.previous; }
    private WoolCube.State current;
    public WoolCube.State newState() { return this.current; }
    public StateChangeEvent(WoolCube cube, @Nullable WoolCube.State previousState, WoolCube.State currentState) {
        this.cube = cube;
        this.previous = previousState;
        this.current = currentState;
    }

    public List<TeamMeta> relevantTeams() {
        ArrayList<TeamMeta> teams = new ArrayList<>();

        switch (previous) {
            case WoolCube.State.Claimed(TeamMeta claimer, boolean respawn)-> {
                teams.add(claimer);
            }
            case WoolCube.State.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) -> {
                teams.add(claimer);
                teams.addAll(attackers.stream().filter(teams::contains).toList());
            }
            case WoolCube.State.Unclaimed unclaimed-> {}
            case null, default -> {}
        }
        switch (current) {
            case WoolCube.State.Claimed(TeamMeta claimer, boolean respawn)-> {
                if (!teams.contains(claimer)) { teams.add(claimer); }
            }
            case WoolCube.State.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) -> {
                if (!teams.contains(claimer)) { teams.add(claimer); }
                teams.addAll(attackers.stream().filter(teams::contains).toList());
            }
            case WoolCube.State.Unclaimed unclaimed -> {}
            case null, default -> {}
        }
        return teams;
    }

    private static final HandlerList HANDLER_LIST = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLER_LIST;}
    @Override public HandlerList getHandlers() { return HANDLER_LIST; }

    public Reason reason() {
        return deduceReason(previous, current);
    }
    public static Reason deduceReason(WoolCube.State previous, WoolCube.State current) {
        if (!(previous instanceof WoolCube.State.UnderAttack)
                && current instanceof WoolCube.State.UnderAttack) {
            return Reason.AttackUnderway;
        }

        if (previous instanceof WoolCube.State.Claimed(TeamMeta claim, boolean oCooldown)
                && current instanceof WoolCube.State.Claimed(TeamMeta claim2, boolean nCooldown)
                && !oCooldown && nCooldown) {
            return Reason.RespawnUnlocked;
        }

        if (previous instanceof WoolCube.State.Unclaimed && current instanceof WoolCube.State.Claimed) {
            return Reason.InitialClaim;
        }

        if (previous instanceof WoolCube.State.UnderAttack && current instanceof WoolCube.State.Claimed) {
            return Reason.CompletedAttack;
        }


        if (previous instanceof WoolCube.State.UnderAttack
                && (current instanceof WoolCube.State.UnderAttack || current instanceof WoolCube.State.Unclaimed)) {
            return Reason.AttackTimeout; // only edge case reason
        }

        return Reason.Undetermined;
    }
}
