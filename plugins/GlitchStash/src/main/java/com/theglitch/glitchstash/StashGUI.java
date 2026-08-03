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
import org.bukkit.event.inventory.InventoryDragEvent;
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

    private record openSession(UUID playerUuid, List<ItemStack> allItems, int displayedCount) {}

    /**
     * Open the stash GUI for a player.
     * Border = top row only; items fill slots 9-53 (45 slots, covers a full
     * extraction: 36 inventory + 4 armor + 1 offhand = 41 items).
     * Items beyond the display (rare merge overflow) are preserved on close,
     * never silently deleted.
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

        // Decorative border (top row only)
        ItemStack border = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.customName(Component.empty());
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        // Fill items starting at slot 9 (row 2, full width)
        int slot = 9;
        int displayed = 0;
        for (ItemStack item : stashItems) {
            if (slot >= SIZE) break;
            inv.setItem(slot, item);
            slot++;
            displayed++;
        }

        // Register session
        openSessions.put(uuid, new openSession(uuid, stashItems, displayed));

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
            // Some items didn't fit — calculate how many were actually given
            int given = clicked.getAmount();
            if (!leftover.isEmpty()) {
                // Sum up all leftover amounts
                int leftoverAmount = 0;
                for (ItemStack left : leftover.values()) {
                    leftoverAmount += left.getAmount();
                }
                given = clicked.getAmount() - leftoverAmount;
            }
            if (given > 0) {
                ItemStack partial = clicked.clone();
                partial.setAmount(given);
                event.getClickedInventory().setItem(slot, partial);
                player.sendMessage(Component.text("Inventory full! Only took " + given + " items.",
                        NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("Inventory full! Clear some space first.",
                        NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openSessions.containsKey(player.getUniqueId())) return;
        // Cancel all drag events in stash GUI to prevent bypass
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openSession session = openSessions.remove(player.getUniqueId());
        if (session == null) return;

        // Rebuild stash from remaining items in GUI
        List<ItemStack> remaining = new ArrayList<>();
        for (int i = 9; i < SIZE; i++) {
            ItemStack item = event.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                remaining.add(item.clone());
            }
        }

        // Items beyond the displayed count were never shown in the GUI, so they
        // could not have been taken — preserve them instead of silently deleting.
        if (session.displayedCount() < session.allItems().size()) {
            remaining.addAll(
                    session.allItems().subList(session.displayedCount(), session.allItems().size()));
        }

        if (remaining.isEmpty()) {
            // All items taken — clear stash
            GlitchStash.getInstance().getStashManager().clearStash(player.getUniqueId());
            player.sendMessage(GlitchStash.getInstance().getComponent("all-retrieved"));
        } else {
            // Partial retrieval — rebuild stash from remaining items and persist
            ItemStack[] newContents = new ItemStack[remaining.size()];
            remaining.toArray(newContents);
            GlitchStash.getInstance().getStashManager().replaceStash(
                    player.getUniqueId(), newContents);
        }
    }
}
