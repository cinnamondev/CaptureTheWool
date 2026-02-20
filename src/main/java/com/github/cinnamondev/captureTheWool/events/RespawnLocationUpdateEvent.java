package com.github.cinnamondev.captureTheWool.events;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RespawnLocationUpdateEvent extends Event implements Cancellable {
    @Nullable private final Location oldLocation;
    @NotNull private final Location newLocation;
    @NotNull private final Reason reason;

    public RespawnLocationUpdateEvent(@Nullable Location oldLocation, @NotNull Location newLocation, @NotNull Reason reason) {
        this.oldLocation = oldLocation;
        this.newLocation = newLocation;
        this.reason = reason;
    }

    private static final HandlerList HANDLER_LIST = new HandlerList();
    public static HandlerList getHandlerList() { return HANDLER_LIST;}
    @Override public HandlerList getHandlers() { return HANDLER_LIST; }

    private boolean cancelled = false;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public enum Reason {
        USER_INPUT,
        UNTENABLE_LOCATION,
        OTHER
    }
}
