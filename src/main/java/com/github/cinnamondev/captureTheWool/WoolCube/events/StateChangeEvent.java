package com.github.cinnamondev.captureTheWool.WoolCube.events;

import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
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
    private @Nullable CubeState previous;
    public CubeState previousState() { return this.previous; }
    private CubeState current;
    public CubeState newState() { return this.current; }
    public StateChangeEvent(WoolCube cube, @Nullable CubeState previousState, CubeState currentState) {
        this.cube = cube;
        this.previous = previousState;
        this.current = currentState;
    }

    public List<TeamMeta> relevantTeams() {
        ArrayList<TeamMeta> teams = new ArrayList<>();

        switch (previous) {
            case CubeState.Claimed(TeamMeta claimer, boolean respawn)-> {
                teams.add(claimer);
            }
            case CubeState.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) -> {
                teams.add(claimer);
                teams.addAll(attackers.stream().filter(teams::contains).toList());
            }
            case CubeState.Unclaimed unclaimed-> {}
            case null, default -> {}
        }
        switch (current) {
            case CubeState.Claimed(TeamMeta claimer, boolean respawn)-> {
                if (!teams.contains(claimer)) { teams.add(claimer); }
            }
            case CubeState.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> attackers) -> {
                if (!teams.contains(claimer)) { teams.add(claimer); }
                teams.addAll(attackers.stream().filter(teams::contains).toList());
            }
            case CubeState.Unclaimed unclaimed -> {}
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
    public static Reason deduceReason(CubeState previous, CubeState current) {
        if (!(previous instanceof CubeState.UnderAttack)
                && current instanceof CubeState.UnderAttack) {
            return Reason.AttackUnderway;
        }

        if (previous instanceof CubeState.Claimed(TeamMeta claim, boolean oCooldown)
                && current instanceof CubeState.Claimed(TeamMeta claim2, boolean nCooldown)
                && !oCooldown && nCooldown) {
            return Reason.RespawnUnlocked;
        }

        if (previous instanceof CubeState.Unclaimed && current instanceof CubeState.Claimed) {
            return Reason.InitialClaim;
        }

        if (previous instanceof CubeState.UnderAttack && current instanceof CubeState.Claimed) {
            return Reason.CompletedAttack;
        }


        if (previous instanceof CubeState.UnderAttack
                && (current instanceof CubeState.UnderAttack || current instanceof CubeState.Unclaimed)) {
            return Reason.AttackTimeout; // only edge case reason
        }

        return Reason.Undetermined;
    }
}
