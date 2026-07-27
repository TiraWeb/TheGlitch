package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates and gives class ability items.
 * Each class gets 2 active abilities (prime + tactical) as clickable items.
 * Passive traits are event-based and need no items.
 */
public final class AbilityItemManager {

    private static final Map<String, NamedTextColor> CLASS_COLORS = Map.of(
            "vanguard", NamedTextColor.RED,
            "warden", NamedTextColor.GREEN,
            "specter", NamedTextColor.DARK_PURPLE,
            "operator", NamedTextColor.AQUA
    );

    private final GlitchClasses plugin;
    private final NamespacedKey classItemKey;

    // Hotbar slots for ability items
    private static final int PRIME_SLOT = 0;
    private static final int TACTICAL_SLOT = 1;

    public AbilityItemManager(GlitchClasses plugin) {
        this.plugin = plugin;
        this.classItemKey = new NamespacedKey(plugin, "class_ability");
    }

    /**
     * Give a player their class ability items. Clears old ability items first.
     */
    public void giveClassItems(Player player, String className) {
        if (className.equals("none")) return;

        // Remove old ability items from hotbar
        clearClassItems(player);

        ConfigurationSection abilities = plugin.getConfig().getConfigurationSection("abilities." + className);
        if (abilities == null) {
            plugin.getLogger().warning("[AbilityItemManager] No abilities config for class: " + className);
            return;
        }

        NamedTextColor color = CLASS_COLORS.getOrDefault(className, NamedTextColor.WHITE);

        // Give prime ability
        ConfigurationSection prime = abilities.getConfigurationSection("prime");
        if (prime != null) {
            ItemStack item = createAbilityItem(prime, "prime", className, color, true);
            player.getInventory().setItem(PRIME_SLOT, item);
            plugin.getLogger().info("[AbilityItemManager] Gave prime item to " + player.getName() + " slot " + PRIME_SLOT + ": " + item.getType());
        } else {
            plugin.getLogger().warning("[AbilityItemManager] No prime ability config for: " + className);
        }

        // Give tactical ability
        ConfigurationSection tactical = abilities.getConfigurationSection("tactical");
        if (tactical != null) {
            ItemStack item = createAbilityItem(tactical, "tactical", className, color, false);
            player.getInventory().setItem(TACTICAL_SLOT, item);
            plugin.getLogger().info("[AbilityItemManager] Gave tactical item to " + player.getName() + " slot " + TACTICAL_SLOT + ": " + item.getType());
        } else {
            plugin.getLogger().warning("[AbilityItemManager] No tactical ability config for: " + className);
        }

        player.updateInventory();
    }

    /**
     * Clear all ability items from a player's hotbar (slots 0 and 1).
     */
    public void clearClassItems(Player player) {
        ItemStack slot0 = player.getInventory().getItem(PRIME_SLOT);
        ItemStack slot1 = player.getInventory().getItem(TACTICAL_SLOT);

        if (slot0 != null && slot0.hasItemMeta() && slot0.getItemMeta().getPersistentDataContainer().has(classItemKey, PersistentDataType.STRING)) {
            player.getInventory().setItem(PRIME_SLOT, null);
        }
        if (slot1 != null && slot1.hasItemMeta() && slot1.getItemMeta().getPersistentDataContainer().has(classItemKey, PersistentDataType.STRING)) {
            player.getInventory().setItem(TACTICAL_SLOT, null);
        }
    }

    /**
     * Check if an item is a class ability item.
     */
    public boolean isAbilityItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(classItemKey, PersistentDataType.STRING);
    }

    /**
     * Get the ability type from an item.
     */
    public String getAbilityType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(classItemKey, PersistentDataType.STRING);
    }

    private ItemStack createAbilityItem(ConfigurationSection ability, String type, String className, NamedTextColor color, boolean isPrime) {
        String iconName = ability.getString("icon", "STONE");
        Material material;
        try {
            material = Material.valueOf(iconName);
        } catch (IllegalArgumentException e) {
            material = isPrime ? Material.BLAZE_ROD : Material.BLAZE_POWDER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = ability.getString("name", type);
        int cooldown = ability.getInt("cooldown", 0);
        String description = ability.getString("description", "");

        // Name: colored by class with "PRIME" or "TACTICAL" prefix
        String label = isPrime ? "PRIME" : "TACTICAL";
        meta.customName(Component.text(label + ": " + name.toUpperCase(), color, TextDecoration.BOLD));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(description, NamedTextColor.GRAY));
        if (cooldown > 0) {
            lore.add(Component.empty());
            lore.add(Component.text("Cooldown: " + cooldown + "s", NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to activate", NamedTextColor.GREEN));
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Tag the item so AbilityListener can identify it
        meta.getPersistentDataContainer().set(classItemKey, PersistentDataType.STRING, type);

        item.setItemMeta(meta);
        return item;
    }
}
