package com.fivemcodehub.chronos.command;

import com.fivemcodehub.chronos.store.StreakStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public final class ChronosAdminCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final StreakStore store;

    public ChronosAdminCommand(JavaPlugin plugin, StreakStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize(
                    "<gray>/chronos <reload|reset <player>|inspect <player>></gray>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(MM.deserialize("<green>Configuration reloaded.</green>"));
            }
            case "reset", "inspect" -> {
                if (args.length < 2) {
                    sender.sendMessage(MM.deserialize("<red>Specify a player.</red>"));
                    return true;
                }

                // Resolve offline so streaks can be inspected for absent players.
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

                if (args[0].equalsIgnoreCase("reset")) {
                    store.reset(target.getUniqueId());
                    sender.sendMessage(MM.deserialize("<green>Streak reset.</green>"));
                    return true;
                }

                var streak = store.get(target.getUniqueId());
                sender.sendMessage(MM.deserialize("<gray>Streak for <name>:</gray>",
                        Placeholder.unparsed("name", args[1])));
                sender.sendMessage(MM.deserialize(
                        "  <gray>current</gray> <white><c></white>  "
                                + "<gray>best</gray> <white><b></white>  "
                                + "<gray>total</gray> <white><t></white>",
                        Placeholder.unparsed("c", String.valueOf(streak.current())),
                        Placeholder.unparsed("b", String.valueOf(streak.best())),
                        Placeholder.unparsed("t", String.valueOf(streak.totalClaims()))));
                sender.sendMessage(MM.deserialize("  <gray>last claim</gray> <white><d></white>",
                        Placeholder.unparsed("d", streak.lastClaim() == null
                                ? "never" : streak.lastClaim().toString())));
            }
            default -> sender.sendMessage(MM.deserialize("<red>Unknown subcommand.</red>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("reload", "reset", "inspect");
        if (args.length == 2) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
