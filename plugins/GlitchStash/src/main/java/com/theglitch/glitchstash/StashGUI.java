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

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final Map<UUID, openSession> openSessions = new HashMap<>();

    // Cached border — themed to match void-purple window (subtle, readable)
    private static final ItemStack CACHED_BORDER;
    static {
        ItemStack b = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            m.customName(Component.empty());
            m.lore(List.of(MM.deserialize("<dark_gray>Stashed loot — click to retrieve</dark_gray>")));
            b.setItemMeta(m);
        }
        CACHED_BORDER = b;
    }

    private record openSession(UUID playerUuid, List<ItemStack> allItems, int displayedCount) {}

    public static void open(Player player, StashManager stashManager, GlitchStash plugin) {
        UUID uuid = player.getUniqueId();
        Optional<StashManager.StashData> dataOpt = stashManager.peekStash(uuid);
        if (dataOpt.isEmpty()) {
            player.sendMessage(plugin.getComponent("stash-empty"));
            return;
        }

        StashManager.StashData data = dataOpt.get();

        List<ItemStack> stashItems = new ArrayList<>();
        for (ItemStack item : data.contents()) {
            if (item != null) stashItems.add(item.clone());
        }
        for (ItemStack item : data.armor()) {
            if (item != null) stashItems.add(item.clone());
        }
        if (data.offhand() != null) stashItems.add(data.offhand().clone());

        // Use cached display-name — no getConfig() polling per open
        String titleRaw = plugin.getCachedDisplayName();
        Inventory inv = Bukkit.createInventory(null, SIZE, MM.deserialize(titleRaw));

        // Header row — subtle border but with info/close controls
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, CACHED_BORDER.clone());
        }
        inv.setItem(4, stashInfoItem(stashItems.size()));
        inv.setItem(8, stashCloseItem());

        int slot = 9;
        int displayed = 0;
        for (ItemStack item : stashItems) {
            if (slot >= SIZE) break;
            inv.setItem(slot, item);
            slot++;
            displayed++;
        }

        openSessions.put(uuid, new openSession(uuid, stashItems, displayed));

        player.openInventory(inv);
        player.sendMessage(plugin.getComponent("stash-opened"));
    }

    private static ItemStack stashInfoItem(int count) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize("<aqua><bold>" + count + " items stashed</bold></aqua>"));
        meta.lore(List.of(
                MM.deserialize("<gray>Click items below to retrieve.</gray>"),
                MM.deserialize("<dark_gray>Closes automatically on exit.</dark_gray>")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack stashCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize("<red><bold>Close</bold></red>"));
        meta.lore(List.of(MM.deserialize("<gray>Close this stash.</gray>")));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openSessions.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        if (slot < 9) {
            if (slot == 8) player.closeInventory();
            return;
        }

        ItemStack clicked = event.getClickedInventory().getItem(slot);
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemStack toGive = clicked.clone();
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);

        if (leftover.isEmpty()) {
            event.getClickedInventory().setItem(slot, null);
            player.sendMessage(Component.text("+ " + clicked.getAmount() + " " +
                    clicked.getType().name().toLowerCase().replace("_", " "),
                    NamedTextColor.GREEN));
        } else {
            int given = clicked.getAmount();
            if (!leftover.isEmpty()) {
                int leftoverAmount = 0;
                for (ItemStack left : leftover.values()) {
                    leftoverAmount += left.getAmount();
                }
                given = clicked.getAmount() - leftoverAmount;
            }
            if (given > 0) {
                ItemStack remaining = clicked.clone();
                remaining.setAmount(clicked.getAmount() - given);
                event.getClickedInventory().setItem(slot, remaining);
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
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openSession session = openSessions.remove(player.getUniqueId());
        if (session == null) return;

        GlitchStash instance = GlitchStash.getInstance();
        if (instance == null) return;

        List<ItemStack> remaining = new ArrayList<>();
        for (int i = 9; i < SIZE; i++) {
            ItemStack item = event.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                remaining.add(item.clone());
            }
        }

        if (session.displayedCount() < session.allItems().size()) {
            remaining.addAll(
                    session.allItems().subList(session.displayedCount(), session.allItems().size()));
        }

        if (remaining.isEmpty()) {
            instance.getStashManager().clearStash(player.getUniqueId());
            player.sendMessage(instance.getComponent("all-retrieved"));
        } else {
            ItemStack[] newContents = new ItemStack[remaining.size()];
            remaining.toArray(newContents);
            instance.getStashManager().replaceStash(
                    player.getUniqueId(), newContents);
        }
    }
}
