package com.github.cinnamondev.captureTheWool.events;

import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.WoolCube;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CubeAttackEvent extends Event implements Cancellable {
    private WoolCube cube;
    private Player attacker;
    private TeamMeta attackingTeam;
    public CubeAttackEvent(WoolCube cube, Player attacker, TeamMeta attackingTeam) {
        this.cube = cube;
        this.attacker = attacker;
        this.attackingTeam = attackingTeam;
    }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    private static final HandlerList HANDLER_LIST = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLER_LIST;}
    @Override public HandlerList getHandlers() { return HANDLER_LIST; }
}
