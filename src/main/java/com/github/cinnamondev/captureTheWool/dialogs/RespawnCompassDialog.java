package com.github.cinnamondev.captureTheWool.dialogs;

import com.github.cinnamondev.captureTheWool.WoolCube.WoolCube;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RespawnCompassDialog {
    public static @NotNull Key setLocationKey = Key.key("capturethewool:compass/set");

    public static String locationToSnbt(Location location) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String string = "{x:" + x + ",y:" + y + ",z:" + z + ",world:\"" + location.getWorld().getName() + "\"}";
        return string;
    }
    public static Optional<Location> SnbtToLocation(Plugin plugin, String string) {
        Pattern p = Pattern.compile("\\{world:\"(.+)\",x:(\\d+),y:(\\d+),z:(\\d+)}");
        Matcher m = p.matcher(string);

        if (m.find()) {
            try {
                World world = plugin.getServer().getWorld(m.group(1));
                int x = Integer.parseInt(m.group(2));
                int y = Integer.parseInt(m.group(3));
                int z = Integer.parseInt(m.group(4));
                if (world == null) {
                    plugin.getLogger().info(string);
                    plugin.getLogger().info("snbt -> location called malformed world.");
                    return Optional.empty();
                }

                Location loc = new Location(world,x,y,z).toBlockLocation();
                return Optional.of(loc);
            } catch (NumberFormatException e) {
                plugin.getLogger().info(string);
                plugin.getLogger().info("malformed snbt in snbt -> location");
                return Optional.empty();
            }
        } else {
            plugin.getLogger().info(string);
            plugin.getLogger().info("snbt -> location called malformed snbt string(?)");
            return Optional.empty();
        }
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
                            DecimalFormat df = new DecimalFormat(); df.setMaximumFractionDigits(1);
                            text = c.displayName().append(Component.text(
                                    " (" + df.format(c.root.distance(playerLocation)) + ")"
                            ));
                        } else {
                            text = c.displayName().append(Component.text(" (" + c.root.getWorld().key().value() + ")"));
                        }
                    }
                    return ActionButton.create(text, null, 200,
                            DialogAction.customClick(
                                    setLocationKey,
                                    // IMPORTANT: we need to validate this is a valid location when we get it back.
                                    BinaryTagHolder.binaryTagHolder(locationToSnbt(c.root))
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
                        2
                ))
        );
    }
}
