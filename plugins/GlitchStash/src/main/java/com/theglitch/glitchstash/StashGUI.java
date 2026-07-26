package com.theglitch.glitchstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Chest GUI for stash retrieval. 6 rows (54 slots).
 * Top row: border. Items fill from slot 10.
 */
public class StashGUI implements Listener {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final Map<UUID, openSession> openSessions = new HashMap<>();

    private record openSession(UUID playerUuid, List<ItemStack> stashItems) {}

    /**
     * Open the stash GUI for a player.
     */
    public static void open(Player player, StashManager stashManager, GlitchStash plugin) {
        UUID uuid = player.getUniqueId();
        Optional<StashManager.StashData> dataOpt = stashManager.peekStash(uuid);
        if (dataOpt.isEmpty()) {
            player.sendMessage(plugin.getComponent("stash-empty"));
            return;
        }

        StashManager.StashData data = dataOpt.get();
        Inventory inv = Bukkit.createInventory(null, SIZE,
                MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("display-name", "<dark_purple>YOUR STASH</dark_purple>")));

        // Collect all non-null items from stash
        List<ItemStack> stashItems = new ArrayList<>();
        for (ItemStack item : data.contents()) {
            if (item != null) stashItems.add(item.clone());
        }
        for (ItemStack item : data.armor()) {
            if (item != null) stashItems.add(item.clone());
        }
        if (data.offhand() != null) stashItems.add(data.offhand().clone());

        // Add decorative border (top row = stained glass pane)
        ItemStack border = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.customName(Component.empty());
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        // Fill items starting at slot 10 (row 2, col 2)
        int slot = 10;
        for (ItemStack item : stashItems) {
            if (slot >= SIZE) break;
            // Skip last slot of each row (right border)
            if ((slot + 1) % 9 == 0) slot++;
            if (slot >= SIZE) break;
            inv.setItem(slot, item);
            slot++;
        }

        // Register session
        openSessions.put(uuid, new openSession(uuid, stashItems));

        player.openInventory(inv);
        player.sendMessage(plugin.getComponent("stash-opened"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openSessions.containsKey(player.getUniqueId())) return;

        event.setCancelled(true); // Prevent moving items out of stash directly

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        // Skip border (top row)
        if (slot < 9) return;

        ItemStack clicked = event.getClickedInventory().getItem(slot);
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Give item to player
        ItemStack toGive = clicked.clone();
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);

        if (leftover.isEmpty()) {
            // All items fit — remove from GUI
            event.getClickedInventory().setItem(slot, null);
            player.sendMessage(Component.text("+ " + clicked.getAmount() + " " +
                    clicked.getType().name().toLowerCase().replace("_", " "),
                    NamedTextColor.GREEN));
        } else {
            // Some items didn't fit
            int given = clicked.getAmount() - leftover.get(0).getAmount();
            if (given > 0) {
                event.getClickedInventory().setItem(slot,
                        given > 0 ? new ItemStack(clicked.getType(), given) : null);
                player.sendMessage(Component.text("Inventory full! Only took " + given + " items.",
                        NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("Inventory full! Clear some space first.",
                        NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openSession session = openSessions.remove(player.getUniqueId());
        if (session == null) return;

        // Check if any items remain in the GUI — save them back to stash
        List<ItemStack> remaining = new ArrayList<>();
        for (int i = 9; i < SIZE; i++) {
            ItemStack item = event.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                remaining.add(item);
            }
        }

        // If all items were taken, clear the stash
        if (remaining.isEmpty()) {
            GlitchStash.getInstance().getStashManager().clearStash(player.getUniqueId());
            player.sendMessage(GlitchStash.getInstance().getComponent("all-retrieved"));
        }
        // Remaining items stay in the in-memory stash (but we don't re-persist them
        // since the session tracks original items — next /stash will show original)
    }
}
