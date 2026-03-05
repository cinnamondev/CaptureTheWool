package com.github.cinnamondev.captureTheWool.customRecipe;

import org.bukkit.inventory.Recipe;

public interface RecipeProvider<T extends Recipe> {
    T recipe();
}
