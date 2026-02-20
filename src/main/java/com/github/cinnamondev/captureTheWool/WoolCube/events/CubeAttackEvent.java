package com.github.cinnamondev.captureTheWool.WoolCube.events;

import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

public class CubeAttackEvent extends StateChangeEvent implements Cancellable {
    private TeamMeta attackingTeam;
    public TeamMeta attackingTeam() { return attackingTeam; }
    public CubeAttackEvent(WoolCube cube, @Nullable CubeState previous, CubeState.UnderAttack newState, TeamMeta attackingTeam) {
        super(cube, previous, newState);
        this.attackingTeam = attackingTeam;
        attackingTeam.scoreboardTeam();
    }

    @Override
    public CubeState.UnderAttack newState() {
        return (CubeState.UnderAttack) super.newState();
    }

    private static final HandlerList HANDLER_LIST = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLER_LIST; }
    @Override public HandlerList getHandlers() { return HANDLER_LIST; }
}
