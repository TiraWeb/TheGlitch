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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HideoutGUI implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final ItemStack CACHED_BORDER;
    static {
        ItemStack b = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            m.customName(Component.empty());
            m.lore(List.of(MM.deserialize("<dark_gray>—</dark_gray>")));
            b.setItemMeta(m);
        }
        CACHED_BORDER = b;
    }

    private static final int SIZE = 54;
    private static final int[] STATION_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int USE_WORKBENCH = 40;
    private static final int USE_MED = 41;
    private static final int USE_STASH = 42;
    private static final int USE_ARMORY = 43;
    private static final int USE_CLASS = 44;
    private static final int CLOSE_SLOT = 49;
    private static final int BACK_SLOT = 45;
    private static final int WORKBENCH_UPGRADE_SLOT = 40;

    private record Session(String type, int from, int to, Inventory inventory) {}

    private final GlitchHideout plugin;
    private final HideoutManager manager;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public HideoutGUI(GlitchHideout plugin, HideoutManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void openMain(Player player) {
        // \uE049 glyph in default font + Inter UI font for readable title
        Inventory inv = Bukkit.createInventory(null, SIZE,
                MM.deserialize("<font:minecraft:default>\uE049</font> <gradient:#C084FC:#F0ABFC><bold>THE HIDEOUT</bold></gradient> <font:minecraft:default>\uE049</font>"));

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border());
        }

        List<HideoutManager.Station> stations = manager.getStations();
        for (int i = 0; i < stations.size() && i < STATION_SLOTS.length; i++) {
            inv.setItem(STATION_SLOTS[i], stationCard(player, stations.get(i)));
        }

        inv.setItem(USE_WORKBENCH, useButton(Material.CRAFTING_TABLE, "<gold>Workbench", "Open the crafting table"));
        inv.setItem(USE_MED, useButton(Material.BREWING_STAND, "<green>Med Station", "Free full heal (30s cooldown)"));
        inv.setItem(USE_STASH, useButton(Material.CHEST, "<dark_purple>Extended Stash", "Extra storage (" + manager.stashSlots(player.getUniqueId()) + " slots)"));
        inv.setItem(USE_ARMORY, useButton(Material.ITEM_FRAME, "<blue>Armory", "Gear storage (" + manager.armorySlots(player.getUniqueId()) + " slots)"));
        inv.setItem(USE_CLASS, useButton(Material.DIAMOND_SWORD, "<red>Class Menu", "Upgrades, abilities and reset"));

        inv.setItem(CLOSE_SLOT, useButton(Material.BARRIER, "<red>Close", "Close the hideout"));

        sessions.put(player.getUniqueId(), new Session("main", 0, 0, inv));
        player.openInventory(inv);
    }

    private ItemStack stationCard(Player player, HideoutManager.Station station) {
        int level = manager.getLevel(player.getUniqueId(), station.id());
        Material material = Material.matchMaterial(station.icon());
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();

        meta.customName(MM.deserialize(station.display()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(MM.deserialize(station.description()));
        lore.add(Component.empty());
        lore.add(Component.text("Level: ", NamedTextColor.GRAY)
                .append(Component.text(level + "/" + station.maxLevel(), NamedTextColor.GOLD)));

        if (level < station.maxLevel()) {
            int next = level + 1;
            String req = station.requires().get(next);
            if (req != null && !req.isEmpty()) {
                lore.add(Component.text("Requires: " + req, NamedTextColor.RED));
            }
            lore.add(Component.text("Cost: " + station.costs()[level] + " shards", NamedTextColor.YELLOW));
            lore.add(Component.empty());
            lore.add(Component.text("Click to upgrade", NamedTextColor.GREEN));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("Fully upgraded", NamedTextColor.GREEN, TextDecoration.BOLD));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack useButton(Material material, String display, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize(display));
        meta.lore(List.of(
                Component.empty(),
                Component.text(description, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to use", NamedTextColor.GREEN)));
        item.setItemMeta(meta);
        return item;
    }

    private void openWorkbench(Player player) {
        if (manager.getLevel(player.getUniqueId(), "workbench") < 1) {
            player.sendMessage(plugin.getComponent("craft-locked"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, SIZE,
                MM.deserialize("<gold><bold>WORKBENCH</bold></gold>"));

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border());
        }
        inv.setItem(BACK_SLOT, useButton(Material.ARROW, "<gray>Back", "Back to hideout"));
        inv.setItem(WORKBENCH_UPGRADE_SLOT, useButton(Material.ANVIL, "<yellow>Upgrade Held Armor", "Upgrade the armor piece you are holding"));

        List<HideoutManager.Recipe> recipes = manager.getRecipes();
        for (int i = 0; i < recipes.size() && i < 25; i++) {
            inv.setItem(10 + i, recipeItem(recipes.get(i)));
        }

        sessions.put(player.getUniqueId(), new Session("workbench", 0, 0, inv));
        player.openInventory(inv);
    }

    private ItemStack recipeItem(HideoutManager.Recipe recipe) {
        Material material = Material.matchMaterial(recipe.icon());
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize(recipe.display()));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (Map.Entry<String, Integer> entry : recipe.materials().entrySet()) {
            lore.add(Component.text(" - " + entry.getKey() + " x" + entry.getValue(), NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to craft", NamedTextColor.GREEN));
        meta.lore(lore);
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
                MM.deserialize("<dark_purple><bold>EXTENDED STASH</bold></dark_purple>"),
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
                MM.deserialize("<blue><bold>ARMORY</bold></blue>"),
                manager.getArmory(player.getUniqueId()));
        player.sendMessage(plugin.getComponent("armory-opened", "<slots>", String.valueOf(slots)));
    }

    private void openStorage(Player player, String type, int slots, Component title, List<ItemStack> items) {
        int from = slots >= 54 ? 0 : 9;
        int to = Math.min(from + slots, SIZE) - 1;

        Inventory inv = Bukkit.createInventory(null, SIZE, title);
        if (from > 0) {
            for (int i = 0; i < from; i++) {
                inv.setItem(i, border());
            }
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

    private ItemStack border() {
        return CACHED_BORDER.clone();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        switch (session.type()) {
            case "main" -> handleMainClick(player, slot);
            case "workbench" -> handleWorkbenchClick(player, slot);
            case "stash" -> handleStorageClick(player, slot, false);
            case "armory" -> handleStorageClick(player, slot, true);
        }
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        List<HideoutManager.Station> stations = manager.getStations();
        for (int i = 0; i < STATION_SLOTS.length; i++) {
            if (slot == STATION_SLOTS[i] && i < stations.size()) {
                upgradeStation(player, stations.get(i));
                return;
            }
        }
        switch (slot) {
            case USE_WORKBENCH -> openWorkbench(player);
            case USE_MED -> manager.medHeal(player);
            case USE_STASH -> openStash(player);
            case USE_ARMORY -> openArmory(player);
            case USE_CLASS -> {
                player.performCommand("class");
                player.sendMessage(plugin.getComponent("class-menu"));
            }
            default -> {
            }
        }
    }

    private void upgradeStation(Player player, HideoutManager.Station station) {
        HideoutManager.UpgradeResult result = upgradeFromUi(player, station.id());
        if (result == HideoutManager.UpgradeResult.OK) {
            openMain(player);
        }
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
        int index = slot - 10;
        if (index < 0 || index >= recipes.size()) return;
        plugin.getHideoutManager().craft(player, recipes.get(index));
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
