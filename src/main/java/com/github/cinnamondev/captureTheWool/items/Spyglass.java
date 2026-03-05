package com.github.cinnamondev.captureTheWool.items;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.woolCube.WoolCube;
import com.github.cinnamondev.captureTheWool.customRecipe.ConfigRecipe;
import com.github.cinnamondev.captureTheWool.customRecipe.RecipeProvider;
import com.github.cinnamondev.captureTheWool.customRecipe.ShapedConfigRecipe;
import com.github.cinnamondev.captureTheWool.customRecipe.ShapelessConfigRecipe;
import com.github.cinnamondev.captureTheWool.dialogs.SpyTeamDialog;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public class Spyglass implements Listener, RecipeProvider<Recipe> {
    private static final NamespacedKey itemKey = NamespacedKey.fromString("ctw:spyglass/item");
    public static final NamespacedKey recipeKey = NamespacedKey.fromString("ctw:spyglass/recipe");
    private final ConfigRecipe<?> recipe;

    private final CaptureTheWool p;
    public Spyglass(CaptureTheWool p) {
        this.p = p;

        // CREATE RECIPE
        ConfigurationSection section = p.getConfig().getConfigurationSection("spyglass");
        if (section == null) {
            throw new IllegalArgumentException("Cannot have empty spyglass config!");
        }

        if (section.getBoolean("shapeless", false)) {
            this.recipe = new ShapelessConfigRecipe(p, recipeKey, section, Spyglass::makeSpyglass);
        } else {
            this.recipe = new ShapedConfigRecipe(p, recipeKey, section, Spyglass::makeSpyglass);
        }
    }

    public static Optional<Integer> extractSpyglassSlot(PlayerInventory inventory) {
        ItemStack offHand = inventory.getItem(40);
        if (offHand != null && Spyglass.isSpyglass(offHand)) { return Optional.of(40); }
        for (int i = 0; i < inventory.getContents().length; i++) {
            ItemStack item = inventory.getContents()[i];
            if (item == null) { continue; }
            if (Spyglass.isSpyglass(item)) { return Optional.of(i); }
        }
        return Optional.empty();
    }

    public static boolean isSpyglass(ItemStack item) {
        return item.getPersistentDataContainer().has(itemKey, PersistentDataType.BOOLEAN);
    }

    public static ItemStack makeSpyglass(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.itemName(Component.text("Wool Spyglass")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD)
        );
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void openDialogOnInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR) { return; }
        if (!Spyglass.isSpyglass(e.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        boolean success = openDialogFor(e.getPlayer());
        if (success) { e.setCancelled(true); }
    }

    public boolean openDialogFor(Player player) {
        TeamMeta playerTeam = p.getPlayerTeam(player);
        if (playerTeam == null) { return false; }

        Collection<TeamMeta> teams = CaptureTheWool.teams.values().stream()
                .filter(t -> !t.equals(playerTeam))
                .filter(t -> t.getCubesOwnedBy(p).findAny().isPresent())
                .toList();

        Dialog dialog = SpyTeamDialog.dialog(playerTeam, teams);
        player.showDialog(dialog);
        return true;
    }
    private static final Sound revealScare = Sound.sound(Key.key("ctw:reveal_doom"), Sound.Source.MASTER, 1f,1f);
    @EventHandler
    public void dialogSubmitEvent(PlayerCustomClickEvent e) {
        if (!e.getIdentifier().equals(SpyTeamDialog.actionKey)) { return; }
        if (!(e.getCommonConnection() instanceof PlayerGameConnection connection)) { return; }
        if (e.getDialogResponseView() == null) { return; }
        Player player = connection.getPlayer();
        TeamMeta playerTeam = p.getPlayerTeam(player);
        if (playerTeam == null) { return; }

        int spyglassSlot; {
            var oSlot = extractSpyglassSlot(player.getInventory());
            if (oSlot.isEmpty()) { return; }
            spyglassSlot = oSlot.get();
        }

        TeamMeta team = SpyTeamDialog.snbtToTeam(e.getDialogResponseView().payload().toString(),p).orElse(null);
        if (team == null) {
            p.getLogger().warning("Player sent malformed dialog response, possible RU ROH!");
            return;
        }

        if (team.equals(playerTeam)) {
            p.getLogger().warning("player was able to target their own team? ruroh??");
            return;
        }

        Location loc = player.getLocation();
        var oCube = team.getCubesOwnedBy(p).min(Comparator.comparing(c -> c.root.distanceSquared(loc)));

        if (oCube.isEmpty()) {
            p.getLogger().warning("Player was able to target team with no cubes?");
            return;
        }

        // now we know theres no issues, lets move on.
        WoolCube cube = oCube.get();

        p.getConfig().getBoolean("verbose-to-defenders", false);
        String revealMode = p.getConfig().getString("spyglass.reveal.to", "PARTIES");

        switch (revealMode) {
            case "PARTIES":
                team.playSound(revealScare);
                team.sendActionBar(Component.text("Watch your backs..."));
                if (p.getConfig().getBoolean("spyglass.verbose-to-defenders", false)) {
                    team.getCubesOwnedBy(p).forEach(c -> spyglassReveal(c, p, team));
                } else {
                    spyglassReveal(cube, p, playerTeam);
                }

                if (p.getConfig().getBoolean("spyglass.glow.on", false)) {
                    p.getServer().getScheduler().runTaskLater(p, () -> team.getOnlinePlayers(p)
                            .forEach(teamPlayer -> team.getOnlinePlayers(p)
                                    .filter(otherTeamPlayer -> !teamPlayer.equals(otherTeamPlayer))
                                    .forEach(enemy -> enemy
                                            .sendPotionEffectChange(enemy, new PotionEffect(PotionEffectType.GLOWING, 1, 1))
                                    )), p.getConfig().getInt("spyglass.glow.after", 300));
                    p.getServer().getScheduler().runTaskLater(p, () -> {
                        team.getOnlinePlayers(p).forEach(teamPlayer -> {
                            team.getOnlinePlayers(p)
                                    .filter(otherTeamPlayer -> !teamPlayer.equals(otherTeamPlayer))
                                    .forEach(enemy -> enemy
                                            .sendPotionEffectChangeRemove(enemy, PotionEffectType.GLOWING)
                                    );
                        });
                    }, p.getConfig().getInt("spyglass.glow.for", 200));
                }

            case "DEFENDERS":
                //  just the team invovled will be told where to go.
                cube.revealLocation(playerTeam.getOnlinePlayers(p).toList());
                playerTeam.sendActionBar(
                        Component.text("Your team is temporarily blessed with clairvoyance...")
                        .color(NamedTextColor.LIGHT_PURPLE)
                );
                spyglassReveal(cube, p, playerTeam);

                if (p.getConfig().getBoolean("spyglass.glow.on", false)) {
                    p.getServer().getScheduler().runTaskLater(p, () -> {
                        playerTeam.getOnlinePlayers(p).forEach(teamPlayer -> {
                            team.getOnlinePlayers(p).forEach(enemy -> enemy
                                    .sendPotionEffectChange(enemy, new PotionEffect(PotionEffectType.GLOWING, 1,1))
                            );
                        });
                    }, p.getConfig().getInt("spyglass.glow.after", 300));
                    p.getServer().getScheduler().runTaskLater(p, () -> {
                        playerTeam.getOnlinePlayers(p).forEach(teamPlayer -> {
                            team.getOnlinePlayers(p).forEach(enemy -> enemy
                                    .sendPotionEffectChangeRemove(enemy, PotionEffectType.GLOWING)
                            );
                        });
                    }, p.getConfig().getInt("spyglass.glow.for", 200));
                }

        }
        player.getInventory().setItem(spyglassSlot, null);
    }

    private static void spyglassReveal(WoolCube c, CaptureTheWool p, TeamMeta team) {
        var playerList = team.getOnlinePlayers(p).toList();
        c.revealLocation(playerList);
        for (int i = 0; i < p.getConfig().getInt("spyglass.reveal.additional-times", 1); i++) {
            p.getServer().getScheduler().runTaskLater(p, () -> {
                c.revealLocation(playerList);
            }, p.getConfig().getInt("spyglass.reveal.period", 600*(i+1)));
        }
    }
    @Override
    public Recipe recipe() {
        return recipe.recipe();
    }

}
