package com.theglitch.glitchclasses;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Starter kit granted on the player's very first class selection.
 * Items are config-driven: vanilla materials or Oraxen ids (via /o give).
 * Design: docs/GAME_DESIGN.md §7 step 3.
 */
public final class StarterKit {

    private static final NamespacedKey KIT_KEY = new NamespacedKey(
            GlitchClasses.getInstance(), "starter_kit_received");

    private final GlitchClasses plugin;

    public StarterKit(GlitchClasses plugin) {
        this.plugin = plugin;
    }

    public void giveIfFirstSelect(Player player) {
        if (!plugin.getConfig().getBoolean("starter-kit.enabled", true)) return;
        if (plugin.getConfig().getBoolean("starter-kit.once-per-player", true)
                && player.getPersistentDataContainer().has(KIT_KEY, PersistentDataType.BOOLEAN)) {
            return;
        }

        give(player);
        player.getPersistentDataContainer().set(KIT_KEY, PersistentDataType.BOOLEAN, true);
        player.sendMessage(plugin.getComponent("starter-kit-given"));
    }

    private void give(Player player) {
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("starter-kit.items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            ConfigurationSection entry = items.getConfigurationSection(key);
            if (entry == null) continue;

            int amount = Math.max(1, entry.getInt("amount", 1));

            String oraxen = entry.getString("oraxen");
            if (oraxen != null && !oraxen.isEmpty()) {
                giveOraxen(player, oraxen, amount);
                continue;
            }

            String materialName = entry.getString("material");
            if (materialName == null) continue;
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Starter kit: unknown material '" + materialName + "' for entry '" + key + "'");
                continue;
            }

            ItemStack stack = new ItemStack(material, amount);
            player.getInventory().addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void giveOraxen(Player player, String id, int amount) {
        boolean dispatched = plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "o give " + player.getName() + " " + id + " " + amount);
        if (!dispatched) {
            plugin.getLogger().warning("Starter kit: Oraxen command unavailable, could not give '" + id + "'");
        }
    }
}
