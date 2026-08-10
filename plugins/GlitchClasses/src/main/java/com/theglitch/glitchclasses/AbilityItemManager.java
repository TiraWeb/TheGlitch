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
    private static final int ULTIMATE_SLOT = 2;

    public AbilityItemManager(GlitchClasses plugin) {
        this.plugin = plugin;
        this.classItemKey = new NamespacedKey(plugin, "class_ability");
    }

    /**
     * Ensure player has their class items. No-op if they already have them.
     * Used for /class kit and world change.
     */
    public void giveClassItems(Player player, String className) {
        if (className.equals("none")) return;
        if (hasClassItems(player)) return;
        giveItemsNow(player, className);
    }

    /**
     * Force-give class items, replacing any existing ones.
     * Used when selecting a class.
     */
    public void forceGiveClassItems(Player player, String className) {
        if (className.equals("none")) return;
        clearClassItems(player);
        giveItemsNow(player, className);
    }

    private void giveItemsNow(Player player, String className) {

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
            placeItem(player, PRIME_SLOT, item);
        } else {
            plugin.getLogger().warning("[AbilityItemManager] No prime ability config for: " + className);
        }

        // Give tactical ability
        ConfigurationSection tactical = abilities.getConfigurationSection("tactical");
        if (tactical != null) {
            ItemStack item = createAbilityItem(tactical, "tactical", className, color, false);
            placeItem(player, TACTICAL_SLOT, item);
        } else {
            plugin.getLogger().warning("[AbilityItemManager] No tactical ability config for: " + className);
        }

        // Give ultimate ability (locked until level 10 — enforced on activation)
        ConfigurationSection ultimate = abilities.getConfigurationSection("ultimate");
        if (ultimate != null) {
            ItemStack item = createAbilityItem(ultimate, "ultimate", className, color, false);
            placeItem(player, ULTIMATE_SLOT, item);
        } else {
            plugin.getLogger().warning("[AbilityItemManager] No ultimate ability config for: " + className);
        }

        player.updateInventory();
    }

    /**
     * Place an ability item in a slot without destroying whatever the player
     * had there — displaced non-ability items move to a free slot or drop at
     * the player's feet.
     */
    private void placeItem(Player player, int slot, ItemStack item) {
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing != null && !existing.getType().isAir() && !isAbilityItem(existing)) {
            player.getInventory().addItem(existing).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.getInventory().setItem(slot, item);
    }

    /**
     * Clear all ability items from a player's inventory (all slots).
     */
    public void clearClassItems(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && isAbilityItem(item)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /**
     * Check if the player already has any ability items in their inventory.
     */
    public boolean hasClassItems(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && isAbilityItem(item)) {
                return true;
            }
        }
        return false;
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

        boolean isUltimate = type.equals("ultimate");

        // Name: colored by class with "PRIME" / "TACTICAL" / "ULTIMATE" prefix
        String label = isUltimate ? "ULTIMATE" : (isPrime ? "PRIME" : "TACTICAL");
        NamedTextColor nameColor = isUltimate ? NamedTextColor.GOLD : color;
        meta.customName(Component.text(label + ": " + name.toUpperCase(), nameColor, TextDecoration.BOLD));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(description, NamedTextColor.GRAY));
        if (isUltimate) {
            lore.add(Component.empty());
            lore.add(Component.text("Requires level 10", NamedTextColor.GOLD));
        }
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
