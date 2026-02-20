package com.github.cinnamondev.captureTheWool.WoolCube.events;

import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;

public class CubeClaimedEvent extends StateChangeEvent implements Cancellable {
    public CubeClaimedEvent(WoolCube cube, @Nullable CubeState previous, CubeState.Claimed newState) {
        super(cube, previous, newState);
    }

    @Override
    public CubeState.Claimed newState() {
        return (CubeState.Claimed) super.newState();
    }

    public TeamMeta newClaimers() {
        return newState().claimer();
    }

    public Optional<TeamMeta> oldClaimers() {
        return switch (previousState()) {
            case CubeState.Claimed(TeamMeta claimer, boolean _cd) -> Optional.of(claimer);
            case CubeState.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> _att) -> Optional.ofNullable(claimer);
            case null, default -> Optional.empty();
        };
    }
    private static final HandlerList HANDLER_LIST = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLER_LIST; }
    @Override public HandlerList getHandlers() { return HANDLER_LIST; }
}