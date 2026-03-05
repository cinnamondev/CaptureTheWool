package com.github.cinnamondev.captureTheWool.events;

import com.github.cinnamondev.captureTheWool.woolCube.WoolCube;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SpyGlassUseEvent extends Event implements Cancellable {
    private final Player player;
    private final WoolCube cube;
    public Player player() { return player; }
    public WoolCube cube() { return cube; }
    private boolean cancelled = false;

    public SpyGlassUseEvent(Player player, WoolCube cube) {
        this.player = player;
        this.cube = cube;
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    private static final HandlerList HANDLER_LIST = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLER_LIST; }
    @Override public HandlerList getHandlers() { return HANDLER_LIST; }
}
