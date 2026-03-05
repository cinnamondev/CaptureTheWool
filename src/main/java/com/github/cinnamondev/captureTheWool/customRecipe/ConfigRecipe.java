package com.github.cinnamondev.captureTheWool.customRecipe;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Function;

public abstract class ConfigRecipe<T extends Recipe> implements CustomRecipe<T> {
    protected final Plugin p;
    protected final ConfigurationSection recipeConfig;
    protected final NamespacedKey recipeKey;
    private Function<ItemStack, ItemStack> itemModifier = null ;
    public ConfigRecipe(Plugin p, NamespacedKey recipeKey, ConfigurationSection recipeConfig) {
        this.p = p;
        this.recipeConfig = recipeConfig;
        this.recipeKey = recipeKey;
    }
    public ConfigRecipe(Plugin p, NamespacedKey recipeKey, ConfigurationSection recipeConfig, Function<ItemStack, ItemStack> itemModifier) {
        this(p, recipeKey, recipeConfig);
        this.itemModifier = itemModifier;
    }

    protected abstract T createRecipe();
    protected List<Material> getMaterialList() {
        return recipeConfig.getStringList("recipe")
                .stream().map(ingredient -> {
                    if (ingredient.equalsIgnoreCase("EMPTY")) { return null; }
                    Material material = Material.getMaterial(ingredient.toUpperCase());
                    if (material == null) {
                        p.getLogger()
                                .warning("Invalid material. it will be kept as an empty. " + ingredient + ".");
                    }
                    return material;
                })
                .toList();
    }

    @Override
    public ItemStack item() {
        String materialName = recipeConfig.getString("block", "").toUpperCase();
        Material material = Material.getMaterial(materialName);
        if (material == null) {
            p.getLogger().warning("couldnt get block " + materialName );
            return null;
        }
        ItemStack item = ItemStack.of(material, recipeConfig.getInt("quantity", 1));
        item.setItemMeta(itemMeta(item.getItemMeta()));
        if (itemModifier != null) { return itemModifier.apply(item); } else { return item; }
    }

    public ItemMeta itemMeta(ItemMeta inputMeta) {
        inputMeta.getPersistentDataContainer().set(recipeKey, PersistentDataType.BOOLEAN, true);
        String name = recipeConfig.getString("name", null);
        String model = recipeConfig.getString("model", null);
        if ( name != null) { inputMeta.displayName(Component.text(name)); }
        if ( model != null ) { inputMeta.setItemModel(NamespacedKey.fromString(model, p)); }
        return inputMeta;
    }

    @Override
    public NamespacedKey recipeKey() { return this.recipeKey; }

    @Override
    public T recipe() { return createRecipe(); }
}
