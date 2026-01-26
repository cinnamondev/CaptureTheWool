package com.github.cinnamondev.captureTheWool.util;

import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// github.com/cinnamondev/LifeSeries
public class ColourConverter {
    /**
     * Tries to pull named colour from a string, defaulting to `defaultColour` otherwise. Fails silently!
     * @param colour Colour corresponding
     * @param defaultColour NamedTextColor to default to.
     * @return NamedTextColor corresponding to string
     */
    public static NamedTextColor namedColourFromString(String colour, NamedTextColor defaultColour) {
        return tryNamedColourFromString(colour).orElse(defaultColour);
    }

    /**
     * Tries to pull named colour from a string, defaulting to WHITE if could not be found. Fails silently!
     * @param colour Colour corresponding
     * @return NamedTextColor corresponding to string
     */
    public static NamedTextColor namedColourFromString(String colour) {
        return tryNamedColourFromString(colour).orElse(NamedTextColor.WHITE);
    }

    /**
     * Tries to pull a NamedTextColor out of a string. Will return `Optional.empty()` if it fails.
     * @param colour Colour string
     * @return Ok(NamedTextColour) or Empty()
     */
    public static Optional<NamedTextColor> tryNamedColourFromString(@Nullable String colour) {
        return switch (colour) {
            case "AQUA"         -> Optional.of(NamedTextColor.AQUA);
            case "BLACK"        -> Optional.of(NamedTextColor.BLACK);
            case "BLUE"         -> Optional.of(NamedTextColor.BLUE);
            case "DARK_AQUA"    -> Optional.of(NamedTextColor.DARK_AQUA);
            case "DARK_BLUE"    -> Optional.of(NamedTextColor.DARK_BLUE);
            case "DARK_GRAY"    -> Optional.of(NamedTextColor.DARK_GRAY);
            case "DARK_GREEN"   -> Optional.of(NamedTextColor.DARK_GREEN);
            case "DARK_PURPLE"  -> Optional.of(NamedTextColor.DARK_PURPLE);
            case "DARK_RED"     -> Optional.of(NamedTextColor.DARK_RED);
            case "GOLD"         -> Optional.of(NamedTextColor.GOLD);
            case "GRAY"         -> Optional.of(NamedTextColor.GRAY);
            case "GREEN"        -> Optional.of(NamedTextColor.GREEN);
            case "LIGHT_PURPLE" -> Optional.of(NamedTextColor.LIGHT_PURPLE);
            case "RED"          -> Optional.of(NamedTextColor.RED);
            case "WHITE"        -> Optional.of(NamedTextColor.WHITE);
            case "YELLOW"       -> Optional.of(NamedTextColor.YELLOW);

            default             -> Optional.empty();
        };
    }
}
