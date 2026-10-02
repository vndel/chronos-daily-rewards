package com.fivemcodehub.chronos.store;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persists per-player streak state to YAML.
 *
 * <p>Stores the last claim as an ISO date string rather than an epoch
 * millisecond value. A timestamp would have to be re-interpreted against the
 * configured zone on every read, and would silently shift meaning if the
 * server's zone changed; a {@code LocalDate} is already the thing the streak
 * logic actually compares.
 */
public final class StreakStore {

    public record Streak(LocalDate lastClaim, int current, int best, int totalClaims) {

        public Streak withClaim(LocalDate date, boolean consecutive) {
            int next = consecutive ? current + 1 : 1;
            return new Streak(date, next, Math.max(best, next), totalClaims + 1);
        }

        public static Streak fresh() {
            return new Streak(null, 0, 0, 0);
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Streak> streaks = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final File file;

    public StreakStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "streaks.yml");
    }

    public void load() {
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("players");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed UUID in streaks.yml: " + key);
                continue;
            }

            String rawDate = section.getString(key + ".last-claim");
            LocalDate last = null;
            if (rawDate != null && !rawDate.isBlank()) {
                try {
                    last = LocalDate.parse(rawDate);
                } catch (java.time.format.DateTimeParseException ex) {
                    plugin.getLogger().warning("Bad date for " + key + ": " + rawDate);
                }
            }

            streaks.put(uuid, new Streak(
                    last,
                    section.getInt(key + ".current", 0),
                    section.getInt(key + ".best", 0),
                    section.getInt(key + ".total", 0)));

            String name = section.getString(key + ".name");
            if (name != null) names.put(uuid, name);
        }

        plugin.getLogger().info("Loaded " + streaks.size() + " streak record(s)");
    }

    public Streak get(UUID uuid) {
        return streaks.getOrDefault(uuid, Streak.fresh());
    }

    public void put(UUID uuid, String name, Streak streak) {
        streaks.put(uuid, streak);
        names.put(uuid, name);
        dirty.set(true);
    }

    public void reset(UUID uuid) {
        streaks.remove(uuid);
        dirty.set(true);
    }

    public void saveIfDirty() {
        if (dirty.compareAndSet(true, false)) saveNow();
    }

    public synchronized void saveNow() {
        YamlConfiguration yaml = new YamlConfiguration();
        streaks.forEach((uuid, streak) -> {
            String base = "players." + uuid;
            yaml.set(base + ".last-claim",
                    streak.lastClaim() == null ? null : streak.lastClaim().toString());
            yaml.set(base + ".current", streak.current());
            yaml.set(base + ".best", streak.best());
            yaml.set(base + ".total", streak.totalClaims());
            yaml.set(base + ".name", names.get(uuid));
        });

        try {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save streaks.yml: " + ex.getMessage());
            // Re-mark dirty so the next autosave retries.
            dirty.set(true);
        }
    }

    public int trackedPlayers() {
        return streaks.size();
    }
}
