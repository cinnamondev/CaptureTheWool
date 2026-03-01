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
        CompletedAttack, // UnderAttack -> Claimed
        //AttackTimeout,
        RespawnUnlocked,
        AttackUnderway,
        InitialClaim,
        Undetermined
    }

    private WoolCube cube;

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
        return switch (current) {
            case CubeState.Claimed(TeamMeta t1, boolean cd1)
                    when previous instanceof CubeState.Claimed(TeamMeta t2, boolean cd2)
                    && t1.equals(t2) && !cd1 -> Reason.RespawnUnlocked;
            case CubeState.Claimed c when previous instanceof CubeState.Unclaimed -> Reason.InitialClaim;
            case CubeState.Claimed c when previous instanceof CubeState.UnderAttack -> Reason.CompletedAttack;
            case CubeState.UnderAttack ua when !(previous instanceof CubeState.UnderAttack) -> Reason.AttackUnderway;
            case null, default -> Reason.Undetermined;
        };

        //if (previous instanceof CubeState.UnderAttack
        //        && (current instanceof CubeState.UnderAttack || current instanceof CubeState.Unclaimed)) {
        //    return Reason.AttackTimeout; // only edge case reason
        //}
    }
}
