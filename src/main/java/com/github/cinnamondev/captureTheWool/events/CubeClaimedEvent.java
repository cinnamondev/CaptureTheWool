package com.github.cinnamondev.captureTheWool.events;

import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;

public class CubeClaimedEvent extends StateChangeEvent implements Cancellable {
    public CubeClaimedEvent(WoolCube cube, @Nullable WoolCube.State previous, WoolCube.State.Claimed newState) {
        super(cube, previous, newState);
    }

    @Override
    public WoolCube.State.Claimed newState() {
        return (WoolCube.State.Claimed) super.newState();
    }

    public TeamMeta newClaimers() {
        return newState().claimer();
    }

    public Optional<TeamMeta> oldClaimers() {
        return switch (previousState()) {
            case WoolCube.State.Claimed(TeamMeta claimer, boolean _cd) -> Optional.of(claimer);
            case WoolCube.State.UnderAttack(TeamMeta claimer, ArrayList<TeamMeta> _att) -> Optional.ofNullable(claimer);
            case null, default -> Optional.empty();
        };
    }
    private static final HandlerList HANDLER_LIST = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLER_LIST; }
    @Override public HandlerList getHandlers() { return HANDLER_LIST; }
}