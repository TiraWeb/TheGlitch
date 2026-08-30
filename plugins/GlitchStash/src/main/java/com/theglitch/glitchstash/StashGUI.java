package com.theglitch.glitchstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Chest GUI for stash retrieval. 6 rows (54 slots), paginated.
 * Header row: border + info/close. Content: slots 9-44 + 46-52.
 * Nav arrows at slots 45/53 page through the FULL stash list — the
 * StashManager state is the single source of truth: clicks write
 * through immediately (takeFromUi) and the close handler does NOT
 * write back a snapshot (that duplicated retrieved items and could
 * overwrite an extraction landing while the GUI was open).
 */
public class StashGUI implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;
    private static final int PAGE_SIZE = 43; // slots 9-44 + 46-52 (nav arrows at 45/53)
    private static final int[] CONTENT_SLOTS = new int[PAGE_SIZE];
    static {
        int idx = 0;
        for (int slot = 9; slot < SIZE; slot++) {
            if (slot == SLOT_PREV || slot == SLOT_NEXT) continue;
            CONTENT_SLOTS[idx++] = slot;
        }
    }

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

    private record openSession(int page) {}

    public static void open(Player player, StashManager stashManager, GlitchStash plugin) {
        UUID uuid = player.getUniqueId();
        Optional<StashManager.StashData> dataOpt = stashManager.peekStash(uuid);
        if (dataOpt.isEmpty()) {
            player.sendMessage(plugin.getComponent("stash-empty"));
            return;
        }

        // Use cached display-name — no getConfig() polling per open
        String titleRaw = plugin.getCachedDisplayName();
        Inventory inv = Bukkit.createInventory(null, SIZE, MM.deserialize(titleRaw));

        // Header row — subtle border but with info/close controls
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, CACHED_BORDER.clone());
        }
        inv.setItem(8, stashCloseItem());

        openSessions.put(uuid, new openSession(0));
        renderPage(player, inv, 0);

        player.openInventory(inv);
        player.sendMessage(plugin.getComponent("stash-opened"));
    }

    /**
     * Re-render the open GUI from current manager state (write-through sync).
     * Closes the GUI if the stash emptied while it was open.
     */
    public static void refresh(Player player) {
        openSession session = openSessions.get(player.getUniqueId());
        if (session == null) return;
        GlitchStash plugin = GlitchStash.getInstance();
        if (plugin == null || plugin.getStashManager() == null) return;
        if (plugin.getStashManager().listStash(player.getUniqueId()).isEmpty()) {
            openSessions.remove(player.getUniqueId());
            FoliaScheduler.runDelayedEntity(player, plugin, player::closeInventory, 1L);
            return;
        }
        renderPage(player, player.getOpenInventory().getTopInventory(), session.page());
    }

    private static void renderPage(Player player, Inventory inv, int page) {
        GlitchStash plugin = GlitchStash.getInstance();
        StashManager manager = plugin == null ? null : plugin.getStashManager();
        List<ItemStack> flat = manager == null ? new ArrayList<>() : manager.listStash(player.getUniqueId());

        int pages = Math.max(1, (flat.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(0, page), pages - 1);
        if (current != page) {
            openSessions.put(player.getUniqueId(), new openSession(current));
        }

        for (int slot = 9; slot < SIZE; slot++) {
            inv.setItem(slot, null);
        }
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = current * PAGE_SIZE + i;
            if (index >= flat.size()) break;
            inv.setItem(CONTENT_SLOTS[i], flat.get(index));
        }

        inv.setItem(4, stashInfoItem(flat.size(), current + 1, pages));
        inv.setItem(SLOT_PREV, current > 0
                ? stashNavItem("<yellow>\u00ab Previous page") : CACHED_BORDER.clone());
        inv.setItem(SLOT_NEXT, current < pages - 1
                ? stashNavItem("<yellow>Next page \u00bb") : CACHED_BORDER.clone());
    }

    private static int contentIndexOf(int slot) {
        for (int i = 0; i < PAGE_SIZE; i++) {
            if (CONTENT_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private static ItemStack stashInfoItem(int count, int page, int pages) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize("<aqua><bold>" + count + " items stashed</bold></aqua>"));
        meta.lore(List.of(
                MM.deserialize("<gray>Page " + page + "/" + pages + "</gray>"),
                MM.deserialize("<gray>Click items to retrieve.</gray>"),
                MM.deserialize("<dark_gray>Changes save instantly.</dark_gray>")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack stashNavItem(String label) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize(label));
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
        openSession session = openSessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        if (slot < 9) {
            if (slot == 8) player.closeInventory();
            return;
        }

        Inventory top = event.getView().getTopInventory();

        if (slot == SLOT_PREV) {
            if (session.page() > 0) renderPage(player, top, session.page() - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            renderPage(player, top, session.page() + 1); // clamped inside renderPage
            return;
        }

        int contentIndex = contentIndexOf(slot);
        if (contentIndex < 0) return;

        GlitchStash instance = GlitchStash.getInstance();
        if (instance == null || instance.getStashManager() == null) return;

        // Write-through: mutate the manager immediately; takeFromUi gives the
        // item, updates stored state, and refreshes this open view.
        int flatIndex = session.page() * PAGE_SIZE + contentIndex;
        instance.getStashManager().takeFromUi(player, flatIndex);
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
        openSessions.remove(player.getUniqueId());
        // NO snapshot write-back: every click already wrote through to the
        // StashManager, so replacing the stash here would resurrect items
        // from a stale view and clobber any extraction that merged meanwhile.
    }
}
