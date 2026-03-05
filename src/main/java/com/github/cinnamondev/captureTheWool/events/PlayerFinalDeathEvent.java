package com.github.cinnamondev.captureTheWool.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerFinalDeathEvent extends Event {
    private final PlayerDeathEvent e;
    public PlayerDeathEvent event() { return e;}
    public PlayerFinalDeathEvent(PlayerDeathEvent e) {
        this.e = e;
    }
    private static final HandlerList HANDLER_LIST = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLER_LIST; }
    @Override public HandlerList getHandlers() { return HANDLER_LIST; }

}
