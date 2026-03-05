package com.github.cinnamondev.captureTheWool.customRecipe;

import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;


import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ShapedConfigRecipe extends ConfigRecipe<ShapedRecipe> {
    public ShapedConfigRecipe(Plugin p, NamespacedKey recipeKey, ConfigurationSection recipeConfig) {
        super(p, recipeKey, recipeConfig);
    }
    public ShapedConfigRecipe(Plugin p, NamespacedKey recipeKey, ConfigurationSection recipeConfig, Function<ItemStack, ItemStack> itemModifier) {
        super(p, recipeKey, recipeConfig, itemModifier);
    }

    @Override
    protected ShapedRecipe createRecipe() {
        List<Material> ingredientList = getMaterialList();

        // build map of unique characters for each ingredient
        HashMap<Material, Character> ingredientMap = new HashMap<>();
        final int FIRST_CHARACTER = 'A';
        AtomicInteger offset = new AtomicInteger();
        ingredientList.stream()
                .distinct()
                .forEach(ingredient -> ingredientMap.computeIfAbsent(ingredient, key ->
                        key != null ? (char) (FIRST_CHARACTER + offset.getAndIncrement()) : ' '
                ));

        StringBuilder shapeBuilder = ingredientList.stream().map(ingredientMap::get) // map material list to shape string
                .reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append);
        // ensure collection is padded if the array is smaller than a standard grid.
        String shape = shapeBuilder.append(" ".repeat(9 - shapeBuilder.length())).toString();

        // set shape
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, item()).shape( // assign shape
                shape.substring(0,3),
                shape.substring(3,6),
                shape.substring(6,9)
        );
        // assign ingredients
        ingredientMap.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .forEach(entry -> recipe.setIngredient(entry.getValue(), entry.getKey()));
        return recipe;
    }

    @Override
    public Component explainRecipe() {
        return Component.text("Recipe for ")
                .append(item().displayName())
                .append(Component.text(" is:").appendNewline())
                .append(Component.text(
                        Lists.partition(getMaterialList(), 3).stream()
                                .map(row -> String.format(
                                        "%-15s%-15s%-15s",
                                        row.stream()
                                                .map(Objects::toString)
                                                .map(str -> !str.equals("null") ? str : "").toArray()
                                ))
                                .collect(Collectors.joining("\n"))
                ));
    }
}
