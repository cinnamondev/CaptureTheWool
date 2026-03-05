package com.github.cinnamondev.captureTheWool.dialogs;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.woolCube.WoolCube;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RespawnCompassDialog {
    public static @NotNull Key setLocationKey = Key.key("capturethewool:compass/set");

    public static String cubeToSnbt(WoolCube c) {
        return "{uuid:\"" + c.uuid().toString() +"\"}";
    }

    public static Optional<WoolCube> snbtToCube(String string, CaptureTheWool p) {
        Pattern pattern = Pattern.compile("\\{uuid:\"([0-9a-fA-F-]+)\"}");
        Matcher m = pattern.matcher(string);

        if (m.find()) {
            try {
                UUID uuid = UUID.fromString(m.group(1));
                return CaptureTheWool.cubes.stream()
                        .filter(c -> c.uuid().equals(uuid))
                        .findAny();
            } catch (IllegalArgumentException e) {
                p.getLogger().warning("player sent malformed cube UUID.");
            }
        }
        return Optional.empty();
    }
    public static Dialog dialog(Location playerLocation, List<WoolCube> candidateLocations) {
        var list = candidateLocations.stream()
                .sorted(Comparator.comparing(c ->
                        c.root.getWorld().equals(playerLocation.getWorld())
                                ? c.root.distanceSquared(playerLocation)
                                : Double.MAX_VALUE // put them all at the end if not of this world.
                ))
                .map(c -> {
                    Component text; {
                        if (c.root.getWorld().equals(playerLocation.getWorld())) {
                            DecimalFormat df = new DecimalFormat(); df.setMaximumFractionDigits(0);
                            text = c.displayName().append(Component.text(
                                    " (" + df.format(c.root.distance(playerLocation)) + " m away)"
                            ));
                        } else {
                            text = c.displayName().append(Component.text(" (" + c.root.getWorld().key().value() + ")"));
                        }
                    }
                    return ActionButton.create(text, null, 120,
                            DialogAction.customClick(
                                    setLocationKey,
                                    // IMPORTANT: we need to validate this is a valid location when we get it back.
                                    BinaryTagHolder.binaryTagHolder(cubeToSnbt(c))
                            )
                    );
                }).toList();
        return Dialog.create(b -> b.empty()
                .base(DialogBase
                        .builder(Component.text("Update respawn location..."))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(
                        list,
                        ActionButton.builder(Component.text("Cancel")).build(),
                        1
                ))
        );
    }
}
