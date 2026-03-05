package com.github.cinnamondev.captureTheWool.customRecipe;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;


public interface CustomRecipe<T extends Recipe> extends RecipeProvider<T> {
    NamespacedKey recipeKey();
    ItemStack item();
    Component explainRecipe();
    default boolean isItemCustomRecipe(ItemStack item) {
        return Boolean.TRUE.equals(
                item.getItemMeta().getPersistentDataContainer().get(recipeKey(), PersistentDataType.BOOLEAN)
        );
    }
}
