package com.theglitch.common;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Adds keys that exist in the jar's bundled config.yml but are missing from the
 * live copy, keeping every value (and comment) the live file already has.
 *
 * Live configs are seeded once, so keys added to the repo later never reached
 * the server — and reads like {@code getInt(path, def)} ignore the bundled
 * defaults, silently falling back to hard-coded values (e.g. extraction points
 * picked off-map in Eleria/Horizons, 2026-09-30). Call right after
 * saveDefaultConfig().
 */
public final class ConfigDefaults {

    private ConfigDefaults() {
    }

    public static int merge(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        Configuration defaults = config.getDefaults();
        if (defaults == null) return 0;
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (!config.isSet(key)) {
                config.set(key, defaults.get(key));
                added++;
            }
        }
        if (added > 0) {
            plugin.saveConfig();
            plugin.getLogger().info("config.yml: added " + added + " missing key(s) from the bundled defaults.");
        }
        return added;
    }
}
