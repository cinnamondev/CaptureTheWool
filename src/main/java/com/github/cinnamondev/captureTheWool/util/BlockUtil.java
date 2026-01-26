package com.github.cinnamondev.captureTheWool.util;

import org.bukkit.Location;
import org.bukkit.World;

public class BlockUtil {
    public static Location getFirstSkyExposedBlock(Location location) {
        World world = location.getWorld();
        if (world == null) { throw new RuntimeException("world is null, cant get first sky!"); }
        return world.getHighestBlockAt(location).getLocation().add(0,1,0);
    }
}
