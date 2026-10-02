package com.fivemcodehub.chronos.command;

import com.fivemcodehub.chronos.reward.RewardLadder;
import com.fivemcodehub.chronos.store.StreakStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class DailyCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final StreakStore store;
    private final RewardLadder ladder;
    private final ZoneId zone;

    public DailyCommand(JavaPlugin plugin, StreakStore store, RewardLadder ladder, ZoneId zone) {
        this.plugin = plugin;
        this.store = store;
        this.ladder = ladder;
        this.zone = zone;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Players only.</red>"));
            return true;
        }

        LocalDate today = LocalDate.now(zone);
        StreakStore.Streak streak = store.get(player.getUniqueId());

        if (today.equals(streak.lastClaim())) {
            player.sendMessage(MM.deserialize(
                    "<yellow>Already claimed. Next reset in <t>.</yellow>",
                    Placeholder.unparsed("t", untilReset())));
            return true;
        }

        // Consecutive means exactly one calendar day has passed. Any larger gap
        // breaks the streak; this is also why dates, not timestamps, are stored.
        boolean consecutive = streak.lastClaim() != null
                && streak.lastClaim().plusDays(1).equals(today);

        int graceDays = plugin.getConfig().getInt("streak.grace-days", 0);
        if (!consecutive && streak.lastClaim() != null && graceDays > 0) {
            long missed = Duration.between(
                    streak.lastClaim().atStartOfDay(), today.atStartOfDay()).toDays();
            if (missed - 1 <= graceDays) consecutive = true;
        }

        StreakStore.Streak updated = streak.withClaim(today, consecutive);
        int tier = ladder.resolveTier(updated.current());

        if (tier < 0) {
            player.sendMessage(MM.deserialize(
                    "<red>No reward tier is configured. Tell an administrator.</red>"));
            return true;
        }

        store.put(player.getUniqueId(), player.getName(), updated);
        ladder.grant(player, tier);

        if (!consecutive && streak.lastClaim() != null) {
            player.sendMessage(MM.deserialize(
                    "<gray>Streak reset. Previous best: <b> day(s).</gray>",
                    Placeholder.unparsed("b", String.valueOf(streak.best()))));
        }

        player.sendMessage(MM.deserialize(
                "<green>Claimed <white><tier></white> — streak <gold><n></gold> day(s).</green>",
                Placeholder.unparsed("tier", ladder.tierName(tier)),
                Placeholder.unparsed("n", String.valueOf(updated.current()))));
        return true;
    }

    /** Time remaining until local midnight in the configured zone. */
    private String untilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        Duration remaining = Duration.between(now, midnight);
        return String.format("%dh %02dm", remaining.toHours(), remaining.toMinutesPart());
    }
}
