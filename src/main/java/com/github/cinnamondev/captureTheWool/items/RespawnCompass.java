package com.github.cinnamondev.captureTheWool.items;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import com.github.cinnamondev.captureTheWool.woolCube.CubeState;
import com.github.cinnamondev.captureTheWool.woolCube.WoolCube;
import com.github.cinnamondev.captureTheWool.woolCube.events.CubeAttackEvent;
import com.github.cinnamondev.captureTheWool.woolCube.events.StateChangeEvent;
import com.github.cinnamondev.captureTheWool.customRecipe.ConfigRecipe;
import com.github.cinnamondev.captureTheWool.customRecipe.RecipeProvider;
import com.github.cinnamondev.captureTheWool.customRecipe.ShapedConfigRecipe;
import com.github.cinnamondev.captureTheWool.customRecipe.ShapelessConfigRecipe;
import com.github.cinnamondev.captureTheWool.dialogs.RespawnCompassDialog;
import com.github.cinnamondev.captureTheWool.events.PlayerFinalDeathEvent;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import it.unimi.dsi.fastutil.Pair;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RespawnCompass implements Listener, RecipeProvider<Recipe> {
    private final CaptureTheWool p;
    public static final NamespacedKey recipeKey = NamespacedKey.fromString("capturethewool:compass/recipe");
    public RespawnCompass(CaptureTheWool p) {
        this.p = p;
        // CREATE RECIPE
        ConfigurationSection section = p.getConfig().getConfigurationSection("compass");
        if (section == null) {
            throw new IllegalArgumentException("Cannot have empty compass config!");
        }

        if (section.getBoolean("shapeless", false)) {
            this.recipe = new ShapelessConfigRecipe(p, recipeKey, section, this::createItem);
        } else {
            this.recipe =new ShapedConfigRecipe(p, recipeKey, section, this::createItem);

        }
        p.getServer().getScheduler().runTaskTimer(p, this::ticker, 20,20);
    }    // idea
    // player can only respawn on a location when
    // the area is fully claimed by their team
    //   they have set that location to respawn
    //     you can onyl respawn on a location if it is 'out of grace' (see, WoolCube implementation of State)

    public void ticker() {
        p.getServer().getOnlinePlayers()
                .forEach(player -> {
                    if (!(isItem(player.getInventory().getItem(EquipmentSlot.HAND))
                            || isItem(player.getInventory().getItem(EquipmentSlot.OFF_HAND)))) {
                        // not in either hand
                        return;
                    }
                    WoolCube c = CaptureTheWool.setRespawnLocations.get(player.getUniqueId());
                    Component msg = Component.text("No respawn point set!");
                    if (c != null) {
                        msg = Component.text("Current respawn point: ").append(c.displayName());
                    }
                    player.sendActionBar(msg);
                });
    }


    static final @NotNull NamespacedKey itemKey = Objects.requireNonNull(NamespacedKey.fromString("capturethewool:compass/item"));

    private static boolean isItem(ItemStack itemStack) {
        return itemStack.getType() == Material.COMPASS
                && itemStack.getPersistentDataContainer().get(itemKey, PersistentDataType.BOOLEAN) != null;
    }
    private static @Nullable List<ItemStack> extractCompassFromPlayer(PlayerInventory player) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : player.getContents()) {
            if (item != null && isItem(item)) {
                list.add(item);
            }
        }
        return list; // if no returned item, null.
    }

    public static List<Integer> extractCompassSlot(PlayerInventory inventory) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < inventory.getContents().length; i++) {
            ItemStack item = inventory.getContents()[i];
            if (item == null) { continue; }
            if (isItem(item)) { list.add(i); }
        }
        return list;
    }

    private ItemStack createItem(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();

        itemMeta.setMaxStackSize(1);
        itemMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        itemMeta.setUnbreakable(true);
        itemMeta.displayName(Component.text("Respawn Compass"));
        itemMeta.getPersistentDataContainer().set(itemKey, PersistentDataType.BOOLEAN, true);
        itemMeta.setItemModel(NamespacedKey.fromString("minecraft:compass"));

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public ItemStack createItem() {
        return recipe.item();
    }

    public ItemStack createFor(Player player) {
        return updateItem(createItem(), player);
    }

    public ItemStack updateItem(ItemStack item, Player player) {
        WoolCube cube = CaptureTheWool.setRespawnLocations.get(player.getUniqueId());
        if (cube == null) { return item; }
        if (item != null) {
            CompassMeta meta = (CompassMeta) item.getItemMeta();
            meta.setLodestone(cube.respawnLocation);
            meta.setLodestoneTracked(false);
            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean updateCompassFor(Player player) {
        var list = extractCompassFromPlayer(player.getInventory());
        if (list.isEmpty()) { return false; }
        list.forEach(i -> updateItem(i, player));
        return true;
    }

    @EventHandler
    public void fixOnPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) { return; }
        if (!isItem(e.getItem().getItemStack())) { return; }

        updateCompassFor(player);
    }

    @EventHandler
    public void fixOnCraft(CraftItemEvent e) {
        if (e.getCurrentItem() == null) { return; }
        if (isItem(e.getCurrentItem())) {
            e.setCurrentItem(createFor((Player) e.getWhoClicked()));
        }
    }

    @EventHandler
    public void cycleCompassMenu(PlayerInteractEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        if (e.getAction() != Action.RIGHT_CLICK_AIR || !isItem(item)) { return; }
        boolean success = showPlayerDialog(e.getPlayer());
        if (success) { e.setCancelled(true); }
    }

    public boolean showPlayerDialog(Player player) {
        TeamMeta t = p.getPlayerTeam(player);
        if (t == null) { return false; }
        player.showDialog(RespawnCompassDialog.dialog(player.getLocation(), p.getRespawnCandidatesFor(t)));
        return true;
    }


    @EventHandler
    public void onDialogInteraction(PlayerCustomClickEvent e) {

        if (!(e.getCommonConnection() instanceof PlayerGameConnection connection)
                || !e.getIdentifier().equals(RespawnCompassDialog.setLocationKey)
                || e.getDialogResponseView() == null) { return; }

        Player player = connection.getPlayer();

        RespawnCompassDialog.snbtToCube(e.getDialogResponseView().payload().string(),p).ifPresentOrElse(c -> {
            TeamMeta playerTeam = p.getPlayerTeam(player);
            if (playerTeam == null) { return; }
            p.getComponentLogger().info(Component.text("Player set respawn location to").append(c.cubeBrief()));
            if (!p.getRespawnCandidatesFor(playerTeam).contains(c)) {
                p.getLogger().warning("Player sent invalid cube for their team as a respawn target. Ignoring!");
            } else {
                CaptureTheWool.setRespawnLocations.put(player.getUniqueId(), c);
                c.revealLocation(Collections.singletonList(player));
                updateCompassFor(player);
            }
        }, () -> p.getLogger().warning("player has sent in a malformed snbt string? see \n" +  e.getDialogResponseView().payload().string()));
    }

    @EventHandler
    public void notifyOnRespawnLoss(CubeAttackEvent e) {
        Audience audience = e.newState().claimer().filterAudience(a -> a instanceof Player player // filter audience to Players who have their respawn location set to this cube.
                && e.cube().equals(CaptureTheWool.setRespawnLocations.get(player.getUniqueId())));

        audience.sendActionBar(Component.text("Your respawn point is under attack!").color(NamedTextColor.RED));
    }

    private final HashMap<UUID, Pair<List<Integer>, Location>> respawnMap = new HashMap<>();
    private final ArrayList<UUID> playersInRespawnCoolDown = new ArrayList<>();
    @EventHandler
    public void setRespawnPoint(PlayerDeathEvent e) {

        e.getDrops().removeIf(RespawnCompass::isItem);
        TeamMeta playerTeam = p.getPlayerTeam(e.getPlayer());
        if (playerTeam == null) { return; }

        List<WoolCube> respawnCandidates = p.getRespawnCandidatesFor(playerTeam).stream()
                .sorted(Comparator.comparing(c ->
                        c.root.getWorld().equals(e.getPlayer().getLocation().getWorld())
                                ? c.root.distanceSquared(e.getPlayer().getLocation())
                                : Double.MAX_VALUE // put them all at the end if not of this world.
                ))
                .toList();

        if (respawnCandidates.isEmpty()
                && playerTeam.countAliveTeammates(p) == 1
                && p.getConfig().getBoolean("last-chance.enabled", false)) {
            // if theres no real respawn candidates (team is pretty much teetering on the edge here.)
            // then we will check if theres any other candidates. this is a final spur, to prevent teams being
            // eliminated before their cubes are decimated.

            // FIRST: we will try to see if theres any CLAIMED cubes. these will be cubes that are
            // claimed by the team but have a pending cooldown. theyll go to the closest claimed.
            // assuming there wasnt a set respawn location at one of these points at one point.
            // this is intended to allow players some grace at the very edge of their teams round.
            respawnCandidates = playerTeam.getCubesOwnedBy(p)
                    .filter(c -> c.cubeState instanceof CubeState.Claimed)
                    .sorted(Comparator.comparing(c -> {
                        WoolCube cube = (WoolCube) c;
                        return cube.root.getWorld().equals(e.getPlayer().getLocation().getWorld())
                                ? cube.root.distanceSquared(e.getPlayer().getLocation())
                                : Double.MAX_VALUE; // put them all at the end if not of this world.
                    }).reversed())
                    .toList();

            if (respawnCandidates.isEmpty()) {
                // finally, we will just indiscriminately go to Any cube belonging to this team.
                respawnCandidates = playerTeam.getCubesOwnedBy(p)
                        .sorted(Comparator.comparing(c -> {
                            WoolCube cube = (WoolCube) c;
                            return cube.root.getWorld().equals(e.getPlayer().getLocation().getWorld())
                                    ? cube.root.distanceSquared(e.getPlayer().getLocation())
                                    : Double.MAX_VALUE; // put them all at the end if not of this world.
                        }).reversed())
                        .toList();
            }
            // if after all that its not empty, send player fun message.
            if (!respawnCandidates.isEmpty()) {
                playersInRespawnCoolDown.add(e.getPlayer().getUniqueId());
                e.getPlayer().setGameMode(GameMode.SPECTATOR);

                e.getPlayer().sendActionBar(Component.text("Is it really that time again?"));
                p.getServer().getScheduler().runTaskLater(p, () -> {
                    e.getPlayer().setGameMode(GameMode.SURVIVAL);
                    playersInRespawnCoolDown.remove(e.getPlayer().getUniqueId());
                    e.getPlayer().sendMessage(Component.text("But it refused.").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
                }, 10 * 20);
            }
        }

        List<Integer> slots = RespawnCompass.extractCompassSlot(e.getPlayer().getInventory());
        if (respawnCandidates.isEmpty()) {
            // Player is DEAD.
            new PlayerFinalDeathEvent(e).callEvent();
            respawnMap.put(e.getPlayer().getUniqueId(), Pair.of(slots,e.getPlayer().getLocation()));
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
            return;
        }

        WoolCube playerAssignedLocation = CaptureTheWool.setRespawnLocations.get(e.getPlayer().getUniqueId());
        Location respawnLocation;
        if (playerAssignedLocation == null
                || !respawnCandidates.contains(playerAssignedLocation)) {
            // player hasnt assigned a location or it isnt a feasible respawn location anymore.
            // Thus, we will punish them by spawning them as far away as possible.
            respawnLocation = respawnCandidates.getLast().respawnLocation;
            e.getPlayer().setRespawnLocation(respawnCandidates.getLast().respawnLocation, true);
        } else {
            respawnLocation = playerAssignedLocation.respawnLocation;
        }
        respawnMap.put(e.getPlayer().getUniqueId(), Pair.of(slots, respawnLocation));
        p.getServer().getScheduler().runTaskLater(p, () -> e.getPlayer().spigot().respawn(), 1);
    }

    @EventHandler
    public void preventMoveDuringCoolDown(PlayerMoveEvent e) {
        if (playersInRespawnCoolDown.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); }
    }

    @EventHandler
    public void preventSpectatingProperlyDuringCooldown(PlayerStartSpectatingEntityEvent e) {
        if (playersInRespawnCoolDown.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); }
    }
    @EventHandler
    public void setRespawnLocation(PlayerRespawnEvent e) {
        Pair<List<Integer>, Location> p = respawnMap.get(e.getPlayer().getUniqueId());
        Location loc = p.right(); List<Integer> slots = p.left();
        if (loc == null) { return; }
        ItemStack item = createFor(e.getPlayer());
        slots.forEach(slot -> e.getPlayer().getInventory().setItem(slot, item));
        e.setRespawnLocation(loc);

        respawnMap.remove(e.getPlayer().getUniqueId());
    }

    private static final PotionEffect absorption = new PotionEffect(PotionEffectType.ABSORPTION, 3600,2);
    private static final PotionEffect regen = new PotionEffect(PotionEffectType.REGENERATION, 1800,2);

    @EventHandler
    public void returnFromTheDead(StateChangeEvent e) {
        if (e.newState() instanceof CubeState.Claimed(TeamMeta claimer, boolean newCooldown)
                && !newCooldown) {
            List<Player> revived = claimer.getOnlinePlayers(p)
                    .filter(player -> player.getGameMode() == GameMode.SPECTATOR)
                    .toList();

            if (revived.isEmpty()) {
                return;
            }

            revived.forEach(player -> {
                player.teleport(e.cube().respawnLocation);
                player.setGameMode(GameMode.SURVIVAL);
                player.addPotionEffect(absorption);
                player.addPotionEffect(regen);
                player.sendMessage(Component.text("You feel a burst of energy..!"));
            });

            e.cube().root.getWorld().strikeLightning(e.cube().root);
            e.cube().revealLocation(claimer.getOnlinePlayers(p).toList());
        }
    }

    @EventHandler
    public void returnFromTheDeadJoin(PlayerJoinEvent e) {
        TeamMeta playerTeam = p.getPlayerTeam(e.getPlayer());
        if (playerTeam == null) { return; }
        if (playerTeam.getOnlinePlayers(p)
                .filter(p -> p.getGameMode() == GameMode.SPECTATOR)
                .noneMatch(p -> p.equals(e.getPlayer()))) {
            return;
        }

        List<WoolCube> respawnCubes = p.getRespawnCandidatesFor(playerTeam);
        if (respawnCubes.isEmpty()) { return; }

        WoolCube setRespawnPoint = CaptureTheWool.setRespawnLocations.get(e.getPlayer().getUniqueId());
        if (setRespawnPoint == null || !respawnCubes.contains(setRespawnPoint)) {
            setRespawnPoint = respawnCubes.getLast();
        }

        e.getPlayer().teleport(setRespawnPoint.respawnLocation);
        e.getPlayer().setGameMode(GameMode.SURVIVAL);
        e.getPlayer().addPotionEffect(absorption);
        e.getPlayer().addPotionEffect(regen);
        e.getPlayer().sendMessage(Component.text("You feel a burst of energy..!"));

        setRespawnPoint.revealLocation(playerTeam.getOnlinePlayers(p).toList());
    }

    private final ConfigRecipe<?> recipe;
    @Override
    public Recipe recipe() {
        return recipe.recipe();
    }
}
