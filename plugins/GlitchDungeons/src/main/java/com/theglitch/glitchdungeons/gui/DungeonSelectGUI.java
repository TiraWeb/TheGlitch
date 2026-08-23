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
    private static final String TITLE = "<font:minecraft:default>\uE049</font><font:theglitch:ui> <gradient:#C084FC:#F0ABFC><bold>SELECT DUNGEON</bold></gradient> </font><font:minecraft:default>\uE049</font>";
    private static final net.kyori.adventure.text.minimessage.MiniMessage MM = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

    public DungeonSelectGUI(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, MM.deserialize(TITLE));

        // Tier 1 — centered row
        gui.setItem(19, createItem(Material.STONE, "<green><bold>Tier 1 — Corrupted Ruins</bold></green>",
            "<gray>Waves: 3  •  Time: 10 min</gray>",
            "<gray>Rewards: <gold>50+ Shards</gold></gray>",
            "",
            "<yellow>Click to join</yellow>"));

        // Tier 2
        gui.setItem(21, createItem(Material.IRON_BLOCK, "<aqua><bold>Tier 2 — Fractured Labs</bold></aqua>",
            "<gray>Waves: 4  •  Time: 15 min</gray>",
            "<gray>Rewards: <gold>100+ Shards</gold></gray>",
            "",
            "<yellow>Click to join</yellow>"));

        // Tier 3
        gui.setItem(23, createItem(Material.DIAMOND_BLOCK, "<light_purple><bold>Tier 3 — Glitch Core</bold></light_purple>",
            "<gray>Waves: 5  •  Time: 20 min</gray>",
            "<gray>Rewards: <gold>200+ Shards</gold></gray>",
            "",
            "<yellow>Click to join</yellow>"));

        // Tier 4
        gui.setItem(25, createItem(Material.NETHERITE_BLOCK, "<red><bold>Tier 4 — The Abyss</bold></red>",
            "<gray>Waves: 5  •  Time: 20 min</gray>",
            "<gray>Rewards: <gold>400+ Shards</gold></gray>",
            "",
            "<yellow>Click to join</yellow>"));

        // Info item top center
        gui.setItem(4, createItem(Material.BOOK, "<gold><bold>Dungeon Info</bold></gold>",
            "<gray>Form a party with <yellow>/p invite &lt;player&gt;</yellow></gray>",
            "<gray>Each tier has unique waves and rewards.</gray>",
            "<gray>Complete all waves and extract for loot!</gray>"));
        gui.setItem(40, createItem(Material.BARRIER, "<red><bold>Close</bold></red>",
            "<gray>Close this menu.</gray>"));

        // Filler glass — themed gray, not black
        for (int i = 0; i < 45; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.contains("SELECT DUNGEON") && !title.contains("Select Dungeon")) return;
        event.setCancelled(true);

        if (event.getRawSlot() == 40) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        int tier = switch (slot) {
            case 19 -> 1;
            case 21 -> 2;
            case 23 -> 3;
            case 25 -> 4;
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
            meta.customName(MM.deserialize(name));
            List<net.kyori.adventure.text.Component> loreList = new ArrayList<>();
            for (String line : lore) {
                if (line == null || line.equals(" ")) {
                    loreList.add(net.kyori.adventure.text.Component.empty());
                } else {
                    loreList.add(MM.deserialize(line));
                }
            }
            meta.lore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }
}
