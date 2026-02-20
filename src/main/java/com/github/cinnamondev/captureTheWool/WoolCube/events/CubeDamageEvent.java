package com.github.cinnamondev.captureTheWool.WoolCube.events;

import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube.CubeState;
import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

public class CubeDamageEvent extends StateChangeEvent implements Cancellable {
    private final TeamMeta attackingTeam;
    public TeamMeta attackingTeam() {
        return attackingTeam;
    }

    public CubeDamageEvent(WoolCube cube, @Nullable CubeState.UnderAttack previous, CubeState.UnderAttack newState, TeamMeta attackingTeam) {
        super(cube,previous,newState);
        this.attackingTeam = attackingTeam;
        attackingTeam.scoreboardTeam();

        // INTENDED MECHANIC: fog of war?
        // so, we want the defending players to know how much theyre losing. i suppose this should be neutralized if a 'scrambler' is in effect, though
        // perhaps it should still launch a gateway. I dont know!
        //
        // but maybe it should also notify the attackers __WHO ARE NEARBY__ how much theyve gained? so they know when to move off defense?
        // or maybe this is leaning too much off the communication aspect of the game?
    }


    private static final HandlerList HANDLER_LIST = new HandlerList();

    @Override
    public CubeState.UnderAttack previousState() {
        return (CubeState.UnderAttack) super.previousState();
    }

    @Override
    public CubeState.UnderAttack newState() {
        return (CubeState.UnderAttack) super.newState();
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}
