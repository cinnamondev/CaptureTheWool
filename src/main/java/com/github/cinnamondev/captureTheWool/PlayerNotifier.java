package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.woolCube.events.CubeAttackEvent;
import com.github.cinnamondev.captureTheWool.woolCube.events.CubeClaimedEvent;
import com.github.cinnamondev.captureTheWool.woolCube.events.CubeDamageEvent;
import com.github.cinnamondev.captureTheWool.events.PlayerFinalDeathEvent;
import com.github.cinnamondev.captureTheWool.items.RespawnCompass;
import com.github.cinnamondev.captureTheWool.items.Spyglass;
import com.google.common.collect.Iterables;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Team;

import java.util.*;


public class PlayerNotifier implements Listener {
    private final CaptureTheWool p;

    public PlayerNotifier(CaptureTheWool p) {
        this.p = p;
    }
    private static final Sound SOUND_CLAIMED_LOSS_CLOSE = Sound.sound(Key.key("entity.allay.death"), Sound.Source.MASTER, 3f,0.7f);
    private static final Sound SOUND_CLAIMED_LOSS_DISTANT = Sound.sound(Key.key("entity.allay.ambient_without_item"), Sound.Source.MASTER, 1f,0.7f);
    private static final Sound SOUND_CLAIMED_CUBE_CLOSE = Sound.sound(Key.key("block.end_portal.spawn"), Sound.Source.MASTER,3f,1f);
    private static final Sound SOUND_CLEAR_BLOCK = Sound.sound(Key.key("block.end_portal_frame.fill"), Sound.Source.BLOCK, 1f, 1f);
    private static final Sound SOUND_CLAIMED_CUBE_DISTANCE = Sound.sound(Key.key("weather.end_flash"), Sound.Source.MASTER,1f,2f);

    private static final Sound SOUND_CALL_TO_ARMS = Sound.sound(Key.key("entity.wither.spawn"), Sound.Source.MASTER, 2f,0.95f);
    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnderAttack(CubeAttackEvent e) {
        TeamMeta claimer = e.newState().claimer();
        if (claimer == null) { return; }
        if (e.isAdditionalTeamAttacking()) { return; }

        claimer.sendMessage(
                Component.text("Your cube ")
                        .append(e.cube().displayName(), Component.text(" is under attack by "), e.attackingTeam().name())
        );

        int x = e.cube().root.blockX();
        int y = e.cube().root.blockY();
        int z = e.cube().root.blockZ();
        claimer.playSound(SOUND_CALL_TO_ARMS, x,y,z);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimed(CubeClaimedEvent e) {
        int x = e.cube().root.blockX();
        int y = e.cube().root.blockY();
        int z = e.cube().root.blockZ();


        e.oldClaimers().filter(t -> !t.equals(e.newClaimers())).ifPresent(team -> {
            team.sendMessage(
                Component.text("You have lost ")
                        .append(
                                e.cube().displayName(),
                                Component.text(" to "),
                                e.newClaimers().name(),
                                Component.text("!")
                        ).style(Style.style(NamedTextColor.DARK_RED, TextDecoration.BOLD))
            );
            team.playSound(SOUND_CLAIMED_LOSS_DISTANT, Sound.Emitter.self());
            team.playSound(SOUND_CLAIMED_LOSS_CLOSE, x,y,z);
        });

        e.newClaimers().playSound(SOUND_CLAIMED_CUBE_CLOSE, x,y,z);
        e.newClaimers().playSound(SOUND_CLAIMED_CUBE_DISTANCE, Sound.Emitter.self());

        e.newClaimers().sendMessage(Component.text("You have claimed ").append(e.cube().displayName()));
    }

    @EventHandler
    public void onTotalLoss(CubeClaimedEvent e) {
        RevealManager.Reach reach = RevealManager.Reach.valueOf(
                p.getConfig().getString("notify-on-total-loss", "ALL").toUpperCase()
        );
        if (reach == null || e.oldClaimers().isEmpty()) { return; }

        TeamMeta oldClaimers = e.oldClaimers().get();

        Iterable<Audience> attackPlayers = (Iterable<Audience>) e.newClaimers().audiences();
        Iterable<Audience> defendPlayers = (Iterable<Audience>) e.oldClaimers().get().audiences();

        if (oldClaimers.countAliveTeammates(p) != 0) { return; }

        Iterable<Audience> audiences = switch (reach) {
            case ALL -> (Iterable<Audience>) p.getServer().audiences();
            case PARTIES -> Iterables.concat(attackPlayers,defendPlayers);
            case DEFENDERS -> defendPlayers;
            case ATTACKERS -> attackPlayers;
        };

        audiences.forEach(a -> a.sendMessage(Component.empty()
                .append(oldClaimers.name())
                .append(Component.text(" has been eliminated by "))
                .append(e.newClaimers().name())
                .append(Component.text(" ! They will no longer be able to respawn!"))));

        if (reach == RevealManager.Reach.ALL || reach == RevealManager.Reach.PARTIES || reach == RevealManager.Reach.DEFENDERS) {
            defendPlayers.forEach(a -> a.sendMessage(Component.empty()
                    .append(e.newClaimers().name())
                    .append(Component.text(" has taken your final cube! Act fast before total loss!"))));
        }
    }

    @EventHandler
    public void teamSweepEvent(PlayerFinalDeathEvent e) {
        RevealManager.Reach reach = RevealManager.Reach.valueOf(
                p.getConfig().getString("notify-on-total-loss", "ALL").toUpperCase()
        );
        if (reach == null) {
            return;
        }

        Player player = e.event().getPlayer();
        TeamMeta killedTeam = p.getPlayerTeam(player);
        if (!p.getRespawnCandidatesFor(killedTeam).isEmpty()) {
            return;
        }

        if (killedTeam == null) {
            return;
        }
        //boolean isAllDead = killedTeam.getOnlinePlayers(p)
        //        .filter(p -> !p.equals(player))
        //        .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
        //        .toList().isEmpty();

        //if (isAllDead) {
        Player killer = e.event().getPlayer().getKiller();
        TeamMeta killerTeam = killer != null ? p.getPlayerTeam(killer) : null;
        Component message = null;
        if (killer == null) {
            message = killedTeam.name().append(Component.text(" has been ELIMINATED!"));
        } else if (killerTeam != null) {
            message = killedTeam.name().append(Component.text(" has been ELIMINATED by "))
                    .append(killer.name())
                    .append(Component.text(" ("))
                    .append(killerTeam.name())
                    .append(Component.text(")!"));
        }
        Sound lightning = Sound.sound(Key.key("minecraft:entity.lightning_bolt.thunder"), Sound.Source.MASTER, 1f, 1f);
        Iterable<Audience> defenders = (Iterable<Audience>) killedTeam.audiences();
        Iterable<Audience> attackers = killerTeam != null ? (Iterable<Audience>) killerTeam.audiences() : Collections.emptyList();
        Iterable<Audience> audiences = switch (reach) {
            case ALL -> (Iterable<Audience>) p.getServer().audiences();
            case PARTIES -> Iterables.concat(defenders, attackers);
            case DEFENDERS -> defenders;
            case ATTACKERS -> attackers;
        };
        for (Audience audience : audiences) {
            if (message != null) {
                audience.sendMessage(message);
            }
            audience.playSound(lightning);
        }

        //}
    }

    @EventHandler
    public void onDamage(CubeAttackEvent e) {
        TeamMeta claimer = e.newState().claimer();
        if (claimer != null) {
            claimer.showBossBar(e.cube().bossBar());
        }
    }

    @EventHandler
    public void onLoss(CubeClaimedEvent e) {
        e.oldClaimers().ifPresent(t -> t.hideBossBar(e.cube().bossBar()));
    }
}
