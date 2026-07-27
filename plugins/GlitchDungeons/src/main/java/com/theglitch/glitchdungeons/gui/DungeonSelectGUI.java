package com.theglitch.glitchdungeons.gui;

import com.theglitch.glitchdungeons.GlitchDungeons;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class DungeonSelectGUI implements Listener {
    private final GlitchDungeons plugin;
    private static final String TITLE = "Select Dungeon";

    public DungeonSelectGUI(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, TITLE);

        // Tier 1
        gui.setItem(10, createItem(Material.STONE, "§aTier 1 - Corrupted Ruins",
            "§7Waves: 3 | Time: 10min",
            "§7Rewards: §e50+ Shards",
            "",
            "§aClick to join"));

        // Tier 2
        gui.setItem(12, createItem(Material.IRON_BLOCK, "§bTier 2 - Fractured Labs",
            "§7Waves: 4 | Time: 15min",
            "§7Rewards: §e100+ Shards",
            "",
            "§bClick to join"));

        // Tier 3
        gui.setItem(14, createItem(Material.DIAMOND_BLOCK, "§dTier 3 - Glitch Core",
            "§7Waves: 5 | Time: 20min",
            "§7Rewards: §e200+ Shards",
            "",
            "§dClick to join"));

        // Tier 4
        gui.setItem(16, createItem(Material.NETHERITE_BLOCK, "§4Tier 4 - The Abyss",
            "§7Waves: 5 | Time: 20min",
            "§7Rewards: §e400+ Shards",
            "",
            "§4Click to join"));

        // Info item
        gui.setItem(4, createItem(Material.BOOK, "§6Dungeon Info",
            "§7Form a party with §e/p invite <player>",
            "§7Each tier has unique waves and rewards",
            "§7Complete all waves and extract for loot!"));

        // Filler glass
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        int tier = switch (slot) {
            case 10 -> 1;
            case 12 -> 2;
            case 14 -> 3;
            case 16 -> 4;
            default -> -1;
        };

        if (tier == -1) return;
        player.closeInventory();

        // Run join command
        plugin.getServer().dispatchCommand(player, "dungeon join " + tier);
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }
}
