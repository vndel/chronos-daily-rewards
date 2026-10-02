package com.fivemcodehub.chronos.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Resolves which reward tier a streak earns and dispatches its commands.
 *
 * <p>Tiers are defined as "streak >= N" thresholds and the highest matching
 * tier wins, so a 30-day streak does not silently fall back to the day-1
 * reward when no exact tier exists.
 */
public final class RewardLadder {

    private final JavaPlugin plugin;

    public RewardLadder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Highest tier threshold satisfied by this streak, or -1 if none. */
    public int resolveTier(int streak) {
        var section = plugin.getConfig().getConfigurationSection("tiers");
        if (section == null) return -1;

        int best = -1;
        for (String key : section.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Non-numeric tier key in config: " + key);
                continue;
            }
            if (streak >= threshold && threshold > best) best = threshold;
        }
        return best;
    }

    public String tierName(int tier) {
        return plugin.getConfig().getString("tiers." + tier + ".name", "Day " + tier);
    }

    /**
     * Runs the configured reward commands for a tier.
     *
     * <p>Dispatch is forced onto the main thread: command execution touches
     * world and inventory state and is not safe from an async context.
     */
    public void grant(Player player, int tier) {
        List<String> commands = plugin.getConfig().getStringList("tiers." + tier + ".commands");
        if (commands.isEmpty()) {
            plugin.getLogger().warning("Tier " + tier + " has no commands configured.");
            return;
        }

        Runnable dispatch = () -> {
            for (String template : commands) {
                String resolved = template
                        .replace("%player%", player.getName())
                        .replace("%uuid%", player.getUniqueId().toString())
                        .replace("%tier%", String.valueOf(tier));
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Reward command failed ('"
                            + resolved + "'): " + ex.getMessage());
                }
            }
        };

        if (Bukkit.isPrimaryThread()) {
            dispatch.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, dispatch);
        }
    }

    public String describe(int tier) {
        return tierName(tier).toLowerCase(Locale.ROOT);
    }
}
