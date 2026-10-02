package com.fivemcodehub.chronos;

import com.fivemcodehub.chronos.command.ChronosAdminCommand;
import com.fivemcodehub.chronos.command.DailyCommand;
import com.fivemcodehub.chronos.reward.RewardLadder;
import com.fivemcodehub.chronos.store.StreakStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.ZoneId;

/**
 * Streak-based daily rewards.
 *
 * <p>The hard part of a daily reward is not the reward, it is deciding when
 * "tomorrow" starts. Using {@code System.currentTimeMillis() / 86_400_000}
 * rolls the day over at UTC midnight, which for a regional community lands in
 * the middle of prime time. This resolves day boundaries in a configured
 * {@link ZoneId} so the reset happens at local midnight and survives daylight
 * saving transitions.
 */
public final class ChronosPlugin extends JavaPlugin {

    private StreakStore store;
    private RewardLadder ladder;
    private ZoneId zone;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String configured = getConfig().getString("timezone", "UTC");
        try {
            this.zone = ZoneId.of(configured == null ? "UTC" : configured);
        } catch (java.time.DateTimeException ex) {
            // Covers ZoneRulesException (unknown region) and malformed ids alike.
            getLogger().warning("Unknown timezone '" + configured + "', falling back to UTC.");
            this.zone = ZoneId.of("UTC");
        }

        this.store = new StreakStore(this);
        this.store.load();

        this.ladder = new RewardLadder(this);

        var daily = getCommand("daily");
        if (daily != null) daily.setExecutor(new DailyCommand(this, store, ladder, zone));

        var admin = getCommand("chronos");
        if (admin != null) {
            ChronosAdminCommand executor = new ChronosAdminCommand(this, store);
            admin.setExecutor(executor);
            admin.setTabCompleter(executor);
        }

        long saveInterval = getConfig().getLong("persistence.autosave-interval-ticks", 6000L);
        getServer().getScheduler().runTaskTimerAsynchronously(
                this, store::saveIfDirty, saveInterval, saveInterval);

        getLogger().info("ChronosDailyRewards enabled (zone " + zone + ", "
                + store.trackedPlayers() + " tracked)");
    }

    @Override
    public void onDisable() {
        if (store != null) store.saveNow();
    }

    public ZoneId zone() {
        return zone;
    }
}
