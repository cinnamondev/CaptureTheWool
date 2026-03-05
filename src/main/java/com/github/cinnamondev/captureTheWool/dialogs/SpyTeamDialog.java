package com.github.cinnamondev.captureTheWool.dialogs;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpyTeamDialog {
    public static final NamespacedKey actionKey = NamespacedKey.fromString("ctw:spyglass/submit");

    public static String teamToSnbt(TeamMeta teamMeta) {
        return "{team:\"" + teamMeta.woolColour().toString() + "\"}";
    }

    public static Optional<TeamMeta> snbtToTeam(String string, CaptureTheWool p) {
        Pattern pattern = Pattern.compile("\\{team:\"(.+)\"}");
        Matcher m = pattern.matcher(string);

        if (m.find()) {
            try {
                Material material = Material.valueOf(m.group(1));
                p.getLogger().info(material.toString());
                return Optional.ofNullable(CaptureTheWool.teams.get(material));
            } catch (IllegalArgumentException e) {
                p.getLogger().warning("Player tried to target invalid material?");
                p.getLogger().warning(m.group(1));
            }
        } else {
            p.getLogger().warning("no match found snbt to team");
            p.getLogger().warning(string);
        }
        return Optional.empty();
    }
    public static Dialog dialog(TeamMeta userTeam, Collection<TeamMeta> candidateTeams) {
        List<ActionButton> buttons = candidateTeams.stream()
                .map(t -> ActionButton.builder(t.name())
                        .tooltip(Component.text("Spy on this team!"))
                        .width(200)
                        .action(DialogAction.customClick(actionKey, BinaryTagHolder.binaryTagHolder(teamToSnbt(t))))
                        .build()
                )
                .toList();
        return Dialog.create(b -> b.empty()
                .type(DialogType.multiAction(
                        buttons,
                        ActionButton.builder(Component.text("Cancel")).build(),
                        2
                ))
                .base(DialogBase
                        .builder(Component.text("Choose a team to spy on!"))
                        .body(Collections.singletonList(DialogBody.item(ItemStack.of(Material.SPYGLASS))
                                .description(DialogBody.plainMessage(Component.text("Reveal the location of a teams wool!")))
                                .width(120)
                                .build()))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build()
                )
        );
    }

}
