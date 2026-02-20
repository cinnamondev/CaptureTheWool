package com.github.cinnamondev.captureTheWool.WoolCube;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record WoolCubeSnapshot(Location root, CubeState state, Component displayName) implements ConfigurationSerializable {
    private static JSONComponentSerializer serializer = JSONComponentSerializer.json();
    public WoolCube toWoolCube(CaptureTheWool p) {
        return new WoolCube(p, root, displayName, state);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        return Map.of(
                "location", root.serialize(),
                "displayName", serializer.serialize(displayName),
                "state", state
        );
    }

    public static WoolCubeSnapshot deserialize(Map<String,Object> args ) {
        Location loc = (Location) args.get("location");
        Component displayName = serializer.deserialize((String) args.get("displayName"));
        CubeState state = (CubeState) args.get("state");
        return new WoolCubeSnapshot(loc, state,displayName);
    }
}
