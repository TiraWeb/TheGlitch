package com.theglitch.glitchloot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Cached MiniMessage message helper — raw strings are cached on reload,
 * placeholders replaced via simple string replace before deserialization.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchLoot plugin;
    private volatile Map<String, String> raw = new HashMap<>();

    public Messages(GlitchLoot plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        Map<String, String> msgs = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    msgs.put(key, value);
                }
            }
        }
        raw = msgs;
    }

    /** Raw string for a key, or the provided default when absent. */
    public String raw(String key, String def) {
        return raw.getOrDefault(key, def);
    }

    /** Deserialized component for a key, or the default; never throws. */
    public Component comp(String key, String def) {
        try {
            return MM.deserialize(raw(key, def));
        } catch (Exception e) {
            plugin.getLogger().warning("Bad MiniMessage for '" + key + "': " + e.getMessage());
            return Component.text(key);
        }
    }

    /** Deserialized component with placeholder pairs (ph1, v1, ph2, v2, ...). */
    public Component comp(String key, String def, String... replacements) {
        String value = raw(key, def);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        try {
            return MM.deserialize(value);
        } catch (Exception e) {
            plugin.getLogger().warning("Bad MiniMessage for '" + key + "': " + e.getMessage());
            return Component.text(key);
        }
    }
}
