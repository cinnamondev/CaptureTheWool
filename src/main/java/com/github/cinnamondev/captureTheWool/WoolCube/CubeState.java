package com.github.cinnamondev.captureTheWool.WoolCube;

import com.github.cinnamondev.captureTheWool.CaptureTheWool;
import com.github.cinnamondev.captureTheWool.TeamMeta;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public sealed interface CubeState extends ConfigurationSerializable {
    static void registerConfiguration() {
        ConfigurationSerialization.registerClass(CubeState.class);
        ConfigurationSerialization.registerClass(CubeState.Claimed.class);
        ConfigurationSerialization.registerClass(CubeState.Unclaimed.class);
        ConfigurationSerialization.registerClass(CubeState.UnderAttack.class);

    }

    record Claimed(TeamMeta claimer, boolean respawnCooldownActive) implements CubeState {
        @Override
        public @NotNull Map<String, Object> serialize() {
            return Map.of(
                    "cooldownActive", false,
                    "claimerName", claimer.scoreboardTeam().getName()
            );
        }

        public static Claimed deserialize(Map<String,Object> args) {
            TeamMeta claimingTeam; {
                String claimerString = (String) args.get("claimerName");
                if (claimerString.equals("null")) { claimingTeam = null; }
                claimingTeam = CaptureTheWool.teamByName.get(claimerString); // MIGHT still be null now. precautionary you need to still check even when you know.
            }
            if (claimingTeam == null) { throw new IllegalArgumentException("cannot have null claimer."); }
            Boolean cooldown = (Boolean) args.get("cooldownActive");
            if (cooldown == null) { throw new IllegalArgumentException("cannot have null cooldown"); }

            return new Claimed(claimingTeam, cooldown);
        }
    }
    // one or more teams may be attacking!
    record UnderAttack(@Nullable TeamMeta claimer, ArrayList<TeamMeta> attackers) implements CubeState {
        @Override
        public @NotNull Map<String, Object> serialize() {
            return Map.of(
                    "claimerName", claimer.scoreboardTeam() != null ? claimer.scoreboardTeam().getName() : "null",
                    "attackers", attackers.stream().map(TeamMeta::scoreboardTeam).map(Team::getName).toList()
            );
        }

        public static UnderAttack deserialize(Map<String, Object> args) {
            TeamMeta claimingTeam; {
                String claimerString = (String) args.get("claimerName");
                if (claimerString.equals("null")) { claimerString = null; }
                claimingTeam = CaptureTheWool.teamByName.get(claimerString); // MIGHT still be null now. precautionary you need to still check even when you know.
            }

            List<String> stringList = (List<String>) args.get("attackers");
            if (stringList == null) { throw new IllegalArgumentException("fuck"); }
            var attackingTeams = stringList.stream()
                    .map(s -> CaptureTheWool.teamByName.get(s))
                    .collect(Collectors.toCollection(ArrayList::new));

            return new UnderAttack(claimingTeam, attackingTeams);
        }
    }
    record Unclaimed() implements CubeState {
        @Override
        public @NotNull Map<String, Object> serialize() {
            return Map.of();
        }

        public static Unclaimed deserialize(Map<String, Object> args) {
            return new Unclaimed();
        }
    }
}
