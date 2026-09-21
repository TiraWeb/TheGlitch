package com.theglitch.glitchraid.gui;

import com.theglitch.glitchraid.GlitchRaid;
import com.theglitch.glitchraid.RaidManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

/**
 * Lets the player pick which Red Zone world to enter — replaces the hub NPC's
 * old direct "mv tp glitch_red" action now that there's more than one.
 */
public class RedZoneSelectGUI implements Listener {

    private static final String TITLE = "<red><bold>SELECT RED ZONE</bold></red>";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    // Centered slots for up to 5 worlds on a 27-slot chest; extra worlds beyond
    // this are simply not shown rather than crashing — operators should keep
    // auto-start-worlds to a handful of entries.
    private static final int[] OPTION_SLOTS = {11, 12, 13, 14, 15};
    private static final Material[] OPTION_MATERIALS = {
            Material.REDSTONE_BLOCK, Material.NETHERITE_BLOCK, Material.CRIMSON_NYLIUM,
            Material.MAGMA_BLOCK, Material.RED_CONCRETE
    };

    private final GlitchRaid plugin;
    private final RaidManager manager;

    public RedZoneSelectGUI(GlitchRaid plugin, RaidManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, MM.deserialize(TITLE));
        List<String> worlds = manager.getAutoStartWorlds();

        for (int i = 0; i < worlds.size() && i < OPTION_SLOTS.length; i++) {
            String world = worlds.get(i);
            String display = manager.getWorldDisplayName(world);
            Material mat = OPTION_MATERIALS[i % OPTION_MATERIALS.length];
            gui.setItem(OPTION_SLOTS[i], createItem(mat, "<red><bold>" + display + "</bold></red>",
                    "<gray>Full-loot PvPvE extraction.</gray>",
                    "",
                    "<yellow>Click to enter</yellow>"));
        }

        gui.setItem(22, createItem(Material.BARRIER, "<red><bold>Close</bold></red>",
                "<gray>Close this menu.</gray>"));

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
        String title = event.getView().getTitle();
        if (!title.contains("SELECT RED ZONE")) return;
        event.setCancelled(true);

        if (event.getRawSlot() == 22) {
            player.closeInventory();
            return;
        }

        List<String> worlds = manager.getAutoStartWorlds();
        int slot = event.getRawSlot();
        int index = -1;
        for (int i = 0; i < OPTION_SLOTS.length; i++) {
            if (OPTION_SLOTS[i] == slot) { index = i; break; }
        }
        if (index < 0 || index >= worlds.size()) return;

        String world = worlds.get(index);
        player.closeInventory();
        dispatchJoin(player, world);
    }

    /** Console-dispatched teleport — same mechanism the hub NPC used before this GUI existed. */
    public void dispatchJoin(Player player, String world) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv tp " + player.getName() + " " + world);
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
