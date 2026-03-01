package com.github.cinnamondev.captureTheWool.WoolCube;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

@SerializableAs("CubeSnapshot")
public record WoolCubeSnapshot(UUID cubeUUID, Location root, CubeState state, Component displayName) implements ConfigurationSerializable {
    public static void registerConfiguration() {
        ConfigurationSerialization.registerClass(WoolCubeSnapshot.class);
    }
    private static JSONComponentSerializer serializer = JSONComponentSerializer.json();
    public WoolCube toWoolCube(CaptureTheWool p) {
        return new WoolCube(p, cubeUUID, root, displayName, state);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        return Map.of(
                "uuid", cubeUUID.toString(),
                "location", root.serialize(),
                "displayName", serializer.serialize(displayName),
                "state", state
        );
    }

    public static WoolCubeSnapshot deserialize(Map<String,Object> args ) {
        UUID uuid = UUID.fromString((String) args.get("uuid"));
        Location loc = Location.deserialize((Map<String, Object>) args.get("location"));
        Component displayName = serializer.deserialize((String) args.get("displayName"));
        CubeState state = (CubeState) args.get("state");
        return new WoolCubeSnapshot(uuid,loc,state,displayName);
    }
}
