package com.theglitch.glitchhideout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import com.theglitch.common.ChatConfirm;
import com.theglitch.common.MenuTitles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HideoutGUI implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Marks storage slots past the player's unlocked count (the background paints every slot). */
    private static final ItemStack CACHED_LOCKED;
    static {
        ItemStack b = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            m.customName(MM.deserialize("<!italic><dark_gray>Locked slot"));
            m.lore(List.of(MM.deserialize("<!italic><gray>Upgrade the station to unlock.")));
            b.setItemMeta(m);
        }
        CACHED_LOCKED = b;
    }

    private static final int SIZE = 54;
    // Main menu: 3-row HIDEOUT background, one card per station in row 1.
    private static final int MAIN_SIZE = 27;
    private static final int[] STATION_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int CLOSE_SLOT = 22;
    // Workbench: 6-row CRAFTING background, recipes in inner columns 1-7 of rows 1-4.
    private static final int[] RECIPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int BACK_SLOT = 45;
    private static final int WORKBENCH_UPGRADE_SLOT = 49;

    private record Session(String type, int from, int to, Inventory inventory) {}

    private final GlitchHideout plugin;
    private final HideoutManager manager;
    private final Map<UUID, Session> sessions = new HashMap<>();
    // Resolved Material.matchMaterial results — config icon strings are a tiny fixed set
    private final Map<String, Material> iconCache = new java.util.concurrent.ConcurrentHashMap<>();

    public HideoutGUI(GlitchHideout plugin, HideoutManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, MAIN_SIZE,
                MenuTitles.title(player, MenuTitles.HIDEOUT, "<dark_purple>The Hideout</dark_purple>"));

        List<HideoutManager.Station> stations = manager.getStations();
        for (int i = 0; i < stations.size() && i < STATION_SLOTS.length; i++) {
            inv.setItem(STATION_SLOTS[i], stationCard(player, stations.get(i)));
        }
        inv.setItem(CLOSE_SLOT, useButton(Material.BARRIER, "<red>Close", null));

        sessions.put(player.getUniqueId(), new Session("main", 0, 0, inv));
        player.openInventory(inv);
    }

    /** What left-clicking a station card does, or null when the station has nothing to open. */
    private static String useHint(String stationId) {
        return switch (stationId) {
            case "workbench" -> "open the workbench";
            case "med" -> "heal (30s cooldown)";
            case "stash" -> "open the stash";
            case "armory" -> "open the armory";
            case "trainer" -> "open the class menu";
            default -> null;
        };
    }

    private ItemStack stationCard(Player player, HideoutManager.Station station) {
        int level = manager.getLevel(player.getUniqueId(), station.id());
        Material material = resolveIcon(station.icon());
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();

        meta.customName(MM.deserialize("<!italic>" + station.display()));

        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<!italic><gray>" + station.description()));
        lore.add(Component.empty());
        lore.add(MM.deserialize("<!italic><gray>Level <gold>" + level + "/" + station.maxLevel()));
        if (level < station.maxLevel()) {
            lore.add(MM.deserialize("<!italic><gray>Next: <yellow>" + station.costs()[level] + " shards"));
            String req = station.requires().get(level + 1);
            if (req != null && !req.isEmpty()) {
                lore.add(MM.deserialize("<!italic><red>Requires " + req.replace(":", " Lv ")));
            }
        } else {
            lore.add(MM.deserialize("<!italic><green>Fully upgraded"));
        }
        lore.add(Component.empty());
        String use = useHint(station.id());
        if (use != null) lore.add(MM.deserialize("<!italic><green>Left-click <gray>to " + use));
        if (level < station.maxLevel()) lore.add(MM.deserialize("<!italic><yellow>Right-click <gray>to upgrade"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack useButton(Material material, String display, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize("<!italic>" + display));
        if (description != null) meta.lore(List.of(MM.deserialize("<!italic><gray>" + description)));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private void openWorkbench(Player player) {
        if (manager.getLevel(player.getUniqueId(), "workbench") < 1) {
            player.sendMessage(plugin.getComponent("craft-locked"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, SIZE,
                MenuTitles.title(player, MenuTitles.WORKBENCH, "<gold>Workbench</gold>"));

        inv.setItem(BACK_SLOT, useButton(Material.ARROW, "<gray>Back", "Back to the hideout"));
        inv.setItem(WORKBENCH_UPGRADE_SLOT, useButton(Material.ANVIL, "<yellow>Upgrade Held Armor", "Upgrade the armor piece you are holding"));

        List<HideoutManager.Recipe> recipes = manager.getRecipes();
        for (int i = 0; i < recipes.size() && i < RECIPE_SLOTS.length; i++) {
            inv.setItem(RECIPE_SLOTS[i], recipeItem(recipes.get(i)));
        }

        sessions.put(player.getUniqueId(), new Session("workbench", 0, 0, inv));
        player.openInventory(inv);
    }

    private ItemStack recipeItem(HideoutManager.Recipe recipe) {
        Material material = resolveIcon(recipe.icon());
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize("<!italic>" + recipe.display()));

        List<Component> lore = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : recipe.materials().entrySet()) {
            lore.add(MM.deserialize("<!italic><gray>• " + entry.getKey() + " <white>x" + entry.getValue()));
        }
        lore.add(Component.empty());
        lore.add(MM.deserialize("<!italic><green>Click <gray>to craft"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private void openStash(Player player) {
        int slots = manager.stashSlots(player.getUniqueId());
        if (slots < 1) {
            player.sendMessage(plugin.getComponent("stash-locked"));
            return;
        }
        openStorage(player, "stash", slots,
                MenuTitles.title(player, MenuTitles.STASH, "<dark_purple>Extended Stash</dark_purple>"),
                manager.getStash(player.getUniqueId()));
        player.sendMessage(plugin.getComponent("stash-opened", "<slots>", String.valueOf(slots)));
    }

    private void openArmory(Player player) {
        int slots = manager.armorySlots(player.getUniqueId());
        if (slots < 1) {
            player.sendMessage(plugin.getComponent("armory-locked"));
            return;
        }
        openStorage(player, "armory", slots,
                MenuTitles.title(player, MenuTitles.ARMORY, "<blue>Armory</blue>"),
                manager.getArmory(player.getUniqueId()));
        player.sendMessage(plugin.getComponent("armory-opened", "<slots>", String.valueOf(slots)));
    }

    private void openStorage(Player player, String type, int slots, Component title, List<ItemStack> items) {
        int from = slots >= 54 ? 0 : 9;
        int to = Math.min(from + slots, SIZE) - 1;

        Inventory inv = Bukkit.createInventory(null, SIZE, title);
        for (int i = to + 1; i < SIZE; i++) {
            inv.setItem(i, CACHED_LOCKED.clone());
        }
        if (type.equals("armory")) {
            inv.setItem(4, useButton(Material.HOPPER, "<green>Auto-Sort", "Sort all stored gear"));
        }

        int slot = from;
        for (ItemStack item : items) {
            if (item == null) continue;
            if (slot > to) break;
            inv.setItem(slot, item.clone());
            slot++;
        }

        sessions.put(player.getUniqueId(), new Session(type, from, to, inv));
        player.openInventory(inv);
    }

    private void saveStorage(Player player, Session session) {
        UUID uuid = player.getUniqueId();
        List<ItemStack> remaining = new ArrayList<>();
        for (int i = session.from(); i <= session.to(); i++) {
            ItemStack item = player.getOpenInventory().getTopInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                remaining.add(item.clone());
            }
        }
        List<ItemStack> target = session.type().equals("armory")
                ? manager.getArmory(uuid) : manager.getStash(uuid);
        target.clear();
        target.addAll(remaining);
        manager.saveStorage(uuid);
    }

    private Material resolveIcon(String raw) {
        if (raw == null) return null;
        String key = raw.toUpperCase(java.util.Locale.ROOT);
        if (iconCache.containsKey(key)) return iconCache.get(key);
        Material resolved = Material.matchMaterial(raw);
        // ConcurrentHashMap disallows null values — only cache successful resolutions
        if (resolved != null) iconCache.put(key, resolved);
        return resolved;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            // Clicking your own inventory while the stash/armory is open stores the item
            // (there was previously no way to put anything in).
            if (session.type().equals("stash") || session.type().equals("armory")) {
                depositFromInventory(player, session, event.getSlot());
            }
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        switch (session.type()) {
            case "main" -> handleMainClick(player, slot, event.isRightClick());
            case "workbench" -> handleWorkbenchClick(player, slot);
            case "stash" -> handleStorageClick(player, slot, false);
            case "armory" -> handleStorageClick(player, slot, true);
        }
    }

    private void handleMainClick(Player player, int slot, boolean rightClick) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        List<HideoutManager.Station> stations = manager.getStations();
        for (int i = 0; i < STATION_SLOTS.length; i++) {
            if (slot != STATION_SLOTS[i] || i >= stations.size()) continue;
            HideoutManager.Station station = stations.get(i);
            if (rightClick) {
                player.closeInventory();
                confirmUpgrade(player, station.id(), null);
            } else {
                useStation(player, station);
            }
            return;
        }
    }

    private void useStation(Player player, HideoutManager.Station station) {
        switch (station.id()) {
            case "workbench" -> openWorkbench(player);
            case "med" -> manager.medHeal(player);
            case "stash" -> openStash(player);
            case "armory" -> openArmory(player);
            case "trainer" -> {
                player.performCommand("class");
                player.sendMessage(plugin.getComponent("class-menu"));
            }
            default -> player.sendMessage(MM.deserialize("<gray>Right-click " + station.display() + " <gray>to upgrade it."));
        }
    }

    /**
     * Chat [YES]/[NO] before spending shards on an upgrade — used by the chest
     * menu (right-click) and the floating hub panel. Nothing is charged until YES.
     */
    public void confirmUpgrade(Player player, String id, Runnable onUpgraded) {
        HideoutManager.Station station = manager.getStation(id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        if (station == null) {
            player.sendMessage(Component.text("Unknown station.", NamedTextColor.RED));
            return;
        }
        int current = manager.getLevel(player.getUniqueId(), station.id());
        if (current >= station.maxLevel()) {
            player.sendMessage(plugin.getComponent("station-maxed", "<station>", station.id()));
            return;
        }
        ChatConfirm.ask(player, MM.deserialize("<gray>Upgrade " + station.display() + " <gray>to <gold>Lv " + (current + 1)
                        + "</gold> for <aqua>" + station.costs()[current] + " shards</aqua>?"),
                () -> {
                    if (upgradeFromUi(player, station.id()) == HideoutManager.UpgradeResult.OK && onUpgraded != null) {
                        onUpgraded.run();
                    }
                });
    }

    public HideoutManager.UpgradeResult upgradeFromUi(Player player, String id) {
        HideoutManager.Station station = manager.getStation(id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
        if (station == null) {
            player.sendMessage(Component.text("Unknown station.", NamedTextColor.RED));
            return null;
        }
        UUID uuid = player.getUniqueId();
        int current = manager.getLevel(uuid, station.id());
        if (current >= station.maxLevel()) {
            player.sendMessage(plugin.getComponent("station-maxed", "<station>", station.id()));
            return HideoutManager.UpgradeResult.MAXED;
        }
        int next = current + 1;

        HideoutManager.UpgradeResult result = manager.upgrade(player, station);
        switch (result) {
            case OK -> {
                player.sendMessage(plugin.getComponent("hideout-upgraded",
                        "<station>", station.display(),
                        "<level>", String.valueOf(next)));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            case MAXED -> player.sendMessage(plugin.getComponent("station-maxed", "<station>", station.id()));
            case PREREQ -> {
                String req = station.requires().get(next);
                if (req == null || req.isEmpty()) {
                    player.sendMessage(plugin.getComponent("prereq-missing",
                            "<station>", "unknown",
                            "<level>", "?"));
                    return result;
                }
                String[] parts = req.split(":", 2);
                if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    plugin.getLogger().warning("Displaying malformed prerequisite '" + req + "' for station " + station.id() + " at level " + next);
                    player.sendMessage(plugin.getComponent("prereq-missing",
                            "<station>", parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : "unknown",
                            "<level>", parts.length == 2 && !parts[1].isBlank() ? parts[1].trim() : "?"));
                    return result;
                }
                player.sendMessage(plugin.getComponent("prereq-missing",
                        "<station>", parts[0].trim(),
                        "<level>", parts[1].trim()));
            }
            case SHARDS -> player.sendMessage(plugin.getComponent("not-enough-shards",
                    "<cost>", String.valueOf(station.costs()[current])));
        }
        return result;
    }

    public void craftFromUi(Player player, String recipeId) {
        if (manager.getLevel(player.getUniqueId(), "workbench") < 1) {
            player.sendMessage(plugin.getComponent("craft-locked"));
            return;
        }
        HideoutManager.Recipe recipe = manager.getRecipe(recipeId == null ? "" : recipeId.toLowerCase(java.util.Locale.ROOT));
        if (recipe == null) {
            player.sendMessage(Component.text("Unknown recipe.", NamedTextColor.RED));
            return;
        }
        plugin.getHideoutManager().craft(player, recipe);
    }

    private void handleWorkbenchClick(Player player, int slot) {
        if (slot == BACK_SLOT) {
            openMain(player);
            return;
        }
        if (slot == WORKBENCH_UPGRADE_SLOT) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "armor upgrade " + player.getName());
            return;
        }
        List<HideoutManager.Recipe> recipes = manager.getRecipes();
        int index = -1;
        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            if (RECIPE_SLOTS[i] == slot) index = i;
        }
        if (index < 0 || index >= recipes.size()) return;
        plugin.getHideoutManager().craft(player, recipes.get(index));
    }

    private void depositFromInventory(Player player, Session session, int invSlot) {
        ItemStack item = player.getInventory().getItem(invSlot);
        if (item == null || item.getType().isAir()) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        for (int i = session.from(); i <= session.to(); i++) {
            ItemStack current = top.getItem(i);
            if (current == null || current.getType().isAir()) {
                top.setItem(i, item.clone());
                player.getInventory().setItem(invSlot, null);
                saveStorage(player, session);
                return;
            }
        }
        player.sendMessage(Component.text("Storage is full — upgrade the station for more slots.", NamedTextColor.RED));
    }

    private void handleStorageClick(Player player, int slot, boolean isArmory) {
        if (isArmory && slot == 4) {
            sessions.remove(player.getUniqueId());
            manager.sortArmory(player.getUniqueId());
            player.sendMessage(plugin.getComponent("armory-sorted"));
            openArmory(player);
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (slot < session.from() || slot > session.to()) return;

        ItemStack clicked = player.getOpenInventory().getTopInventory().getItem(slot);
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemStack toGive = clicked.clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
        if (leftover.isEmpty()) {
            player.getOpenInventory().getTopInventory().setItem(slot, null);
        } else {
            int leftoverAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            int given = clicked.getAmount() - leftoverAmount;
            if (given > 0) {
                ItemStack remaining = clicked.clone();
                remaining.setAmount(leftoverAmount);
                player.getOpenInventory().getTopInventory().setItem(slot, remaining);
                player.sendMessage(Component.text("Inventory full! Only took " + given + ".",
                        NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("Inventory full!", NamedTextColor.RED));
            }
        }
        saveStorage(player, session);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (sessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.inventory() == event.getInventory()) {
            sessions.remove(player.getUniqueId());
        }
    }
}
