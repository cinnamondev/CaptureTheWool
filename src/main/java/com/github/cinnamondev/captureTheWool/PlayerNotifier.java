package com.github.cinnamondev.captureTheWool;

import com.github.cinnamondev.captureTheWool.events.CubeAttackEvent;
import com.github.cinnamondev.captureTheWool.events.CubeClaimedEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;


public class PlayerNotifier implements Listener {
    private static final Sound SOUND_CLAIMED_LOSS_CLOSE = Sound.sound(Key.key("entity.allay.death"), Sound.Source.MASTER, 3f,0.7f);
    private static final Sound SOUND_CLAIMED_LOSS_DISTANT = Sound.sound(Key.key("entity.allay.ambient_without_item"), Sound.Source.MASTER, 1f,0.7f);
    private static final Sound SOUND_CLAIMED_CUBE_CLOSE = Sound.sound(Key.key("block.end_portal.spawn"), Sound.Source.MASTER,3f,1f);
    private static final Sound SOUND_CLEAR_BLOCK = Sound.sound(Key.key("block.end_portal_frame.fill"), Sound.Source.BLOCK, 1f, 1f);
    private static final Sound SOUND_CLAIMED_CUBE_DISTANCE = Sound.sound(Key.key("weather.end_flash"), Sound.Source.MASTER,1f,2f);

    private static final Sound SOUND_CALL_TO_ARMS = Sound.sound(Key.key("entity.wither.spawn"), Sound.Source.MASTER, 2f,0.95f);
    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnderAttack(CubeAttackEvent e) {
        e.newState().claimer().sendMessage(
                Component.text("Your cube ")
                        .append(e.cube().displayName(), Component.text(" is under attack by "), e.attackingTeam().name())
        );

        int x = e.cube().root.blockX();
        int y = e.cube().root.blockY();
        int z = e.cube().root.blockZ();
        e.newState().claimer().playSound(SOUND_CALL_TO_ARMS, x,y,z);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimed(CubeClaimedEvent e) {
        int x = e.cube().root.blockX();
        int y = e.cube().root.blockY();
        int z = e.cube().root.blockZ();

        e.oldClaimers().ifPresent(team -> {
            team.sendMessage(
                Component.text("You have lost ")
                        .append(
                                e.cube().displayName(),
                                Component.text(" to"),
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
}
