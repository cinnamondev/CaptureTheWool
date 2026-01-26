package com.github.cinnamondev.captureTheWool.events;

import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

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

    private WoolCube.State previous;
    private WoolCube.State current;
    public StateChangeEvent(WoolCube cube, WoolCube.State previousState, WoolCube.State currentState) {
        this.previous = previousState;
        this.current = currentState;
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
