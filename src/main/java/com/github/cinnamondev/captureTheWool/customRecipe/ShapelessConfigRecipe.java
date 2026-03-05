package com.github.cinnamondev.captureTheWool.customRecipe;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ShapelessConfigRecipe extends ConfigRecipe<ShapelessRecipe> {
    public ShapelessConfigRecipe(Plugin p, NamespacedKey recipeKey, ConfigurationSection recipeConfig) {
        super(p, recipeKey, recipeConfig);
    }
    public ShapelessConfigRecipe(Plugin p, NamespacedKey recipeKey, ConfigurationSection recipeConfig, Function<ItemStack, ItemStack> itemModifier) {
        super(p, recipeKey, recipeConfig, itemModifier);
    }

    @Override
    protected ShapelessRecipe createRecipe() {
        List<Material> ingredientList = getMaterialList();
        ShapelessRecipe recipe = new ShapelessRecipe(this.recipeKey, item());

        ingredientList.stream()
                .filter(Objects::nonNull)
                .forEach(recipe::addIngredient);
        return recipe;
    }

    @Override
    public Component explainRecipe() {
        return Component.text("Recipe for ")
                .append(item().displayName())
                .append(net.kyori.adventure.text.Component.text(" is:").appendNewline())
                .append(net.kyori.adventure.text.Component.text(
                        getMaterialList().stream().map(Object::toString).collect(Collectors.joining(" and "))
                ));
    }

}
