package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Class system GUI.
 *
 * Main menu (27 slots): four class cards in the middle row, current-class
 * info at the bottom center, reset button at the bottom right.
 *
 * Class menu (45 slots): ability info row (prime / tactical / traits /
 * ultimate), the 10-level upgrade path, and select / upgrade / back controls.
 */
public class ClassGUI implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final String[] CLASS_ORDER = {"vanguard", "warden", "specter", "operator"};
    private static final String[] ABILITY_KEYS = {"prime", "tactical", "trait1", "trait2", "ultimate"};
    private static final String[] ABILITY_LABELS = {"PRIME", "TACTICAL", "TRAIT I", "TRAIT II", "ULTIMATE"};
    private static final int[] ABILITY_UNLOCKS = {1, 1, 1, 3, -1}; // -1 = ultimate (config level)
    private static final Material[] ABILITY_FALLBACK_ICONS = {
            Material.AMETHYST_SHARD, Material.ENDER_PEARL, Material.BOOK, Material.BOOK, Material.NETHER_STAR};
    private static final Map<String, Integer> KEY_TO_INDEX = Map.of(
            "prime", 0, "tactical", 1, "trait1", 2, "trait2", 3, "ultimate", 4);
    private static final ItemStack CACHED_BORDER;

    static {
        ItemStack b = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = b.getItemMeta();
        m.customName(Component.empty());
        b.setItemMeta(m);
        CACHED_BORDER = b;
    }

    private static final Map<String, NamedTextColor> COLOR_FALLBACKS = Map.of(
            "RED", NamedTextColor.RED,
            "GREEN", NamedTextColor.GREEN,
            "DARK_PURPLE", NamedTextColor.DARK_PURPLE,
            "AQUA", NamedTextColor.AQUA);

    private static final Map<UUID, String> openSessions = new HashMap<>();
    private static final Set<UUID> switchingGui = new HashSet<>();

    private final GlitchClasses plugin;
    private final ClassManager classManager;
    private volatile int cachedUltimateLevel = 10;
    private net.milkbowl.vault.economy.Economy cachedEconomy;
    private long economyCacheTime;

    public ClassGUI(GlitchClasses plugin, ClassManager classManager) {
        this.plugin = plugin;
        this.classManager = classManager;
        reloadConfig();
    }

    public void reloadConfig() {
        cachedUltimateLevel = plugin.getConfig().getInt("ultimate-level", 10);
        cachedEconomy = null;
    }

    private net.milkbowl.vault.economy.Economy getEconomy() {
        long now = System.currentTimeMillis();
        if (cachedEconomy != null && now - economyCacheTime < 30_000L) return cachedEconomy;
        var reg = org.bukkit.Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        cachedEconomy = reg != null ? reg.getProvider() : null;
        economyCacheTime = now;
        return cachedEconomy;
    }

    // ==================== MAIN MENU (27 slots) ====================

    public void openMainMenu(Player player) {
        String title = plugin.getConfig().getString("gui.title",
                "<dark_purple><bold>CHOOSE YOUR CLASS</bold></dark_purple>");
        Inventory inv = Bukkit.createInventory(null, 27, MM.deserialize(title));
        fillBorder(inv, 27);

        ClassData data = classManager.getClassData(player.getUniqueId());

        int[] cardSlots = {11, 12, 13, 14};
        for (int i = 0; i < CLASS_ORDER.length; i++) {
            inv.setItem(cardSlots[i], classCard(CLASS_ORDER[i], data));
        }

        inv.setItem(22, infoItem(data));

        if (!data.className().equals("none")) {
            inv.setItem(26, resetItem());
        }

        openSessions.put(player.getUniqueId(), "main");
        player.openInventory(inv);
    }

    private ItemStack classCard(String className, ClassData data) {
        ConfigurationSection cls = plugin.getConfig().getConfigurationSection("classes." + className);
        Material icon = material(cls != null ? cls.getString("icon", "") : "", Material.SHIELD);
        NamedTextColor color = classColor(className);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        boolean selected = className.equals(data.className());

        String display = cls != null ? cls.getString("display-name", "") : "";
        if (!display.isEmpty()) {
            meta.customName(MM.deserialize(display));
        } else {
            meta.customName(Component.text(className.toUpperCase(), color, TextDecoration.BOLD));
        }

        List<Component> lore = new ArrayList<>();
        String role = cls != null ? cls.getString("role", "") : "";
        String description = cls != null ? cls.getString("description", "") : "";
        if (!role.isEmpty()) {
            lore.add(Component.text(role, NamedTextColor.GRAY, TextDecoration.ITALIC));
        }
        if (!description.isEmpty()) {
            lore.add(MM.deserialize(description));
        }
        lore.add(Component.empty());
        if (selected) {
            lore.add(Component.text("YOUR CLASS", NamedTextColor.GREEN, TextDecoration.BOLD));
            lore.add(Component.text("Level: " + data.level() + "/" + classManager.getMaxLevel(),
                    NamedTextColor.GOLD));
        } else {
            lore.add(Component.text("Click to view", NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem(ClassData data) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("CLASS SYSTEM", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (!data.className().equals("none")) {
            lore.add(Component.text("Current: ", NamedTextColor.GRAY)
                    .append(Component.text(data.className().toUpperCase(),
                            classColor(data.className()), TextDecoration.BOLD)));
            lore.add(Component.text("Level: ", NamedTextColor.GRAY)
                    .append(Component.text(data.level() + "/" + classManager.getMaxLevel(),
                            NamedTextColor.GOLD)));
            lore.add(Component.text("XP: ", NamedTextColor.GRAY)
                    .append(Component.text(data.xp() + "/" + classManager.getXpForLevel(data.level() + 1),
                            NamedTextColor.YELLOW)));
        } else {
            lore.add(Component.text("No class selected.", NamedTextColor.RED));
            lore.add(Component.text("Pick a class above to begin.", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack resetItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("RESET CLASS", NamedTextColor.RED, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Cost: " + classManager.getResetCost() + " shards", NamedTextColor.GRAY),
                Component.text("Resets your class and level to none.", NamedTextColor.RED),
                Component.empty(),
                Component.text("Click to reset.", NamedTextColor.YELLOW)));
        item.setItemMeta(meta);
        return item;
    }

    // ==================== CLASS MENU (45 slots) ====================

    public void openClassMenu(Player player, String className) {
        ClassData data = classManager.getClassData(player.getUniqueId());
        boolean selected = className.equals(data.className());

        String title = "<" + colorName(className) + "><bold>" + className.toUpperCase()
                + "</bold></" + colorName(className) + ">";
        Inventory inv = Bukkit.createInventory(null, 45, MM.deserialize(title));
        fillBorder(inv, 45);

        // Ability info — row 2, slots 10-14
        ConfigurationSection abilities = plugin.getConfig().getConfigurationSection("abilities." + className);
        if (abilities != null) {
            int[] abilitySlots = {10, 11, 12, 13, 14};
            for (int i = 0; i < ABILITY_KEYS.length; i++) {
                ConfigurationSection ability = abilities.getConfigurationSection(ABILITY_KEYS[i]);
                if (ability == null) continue;
                inv.setItem(abilitySlots[i], abilityItem(className, ABILITY_KEYS[i], ability, selected, data));
            }
        }

        // Upgrade path — row 3, slots 19-28
        List<String> upgrades = plugin.getConfig().getStringList("upgrades." + className);
        for (int level = 1; level <= 10; level++) {
            int slot = 18 + level;
            String upgradeText = level - 1 < upgrades.size() ? upgrades.get(level - 1) : "";
            inv.setItem(slot, upgradeItem(level, upgradeText, selected, data));
        }

        // Controls — row 4: back (30), select (31), upgrade (32)
        inv.setItem(30, backItem());
        inv.setItem(31, selectItem(className, selected, data));
        if (selected) {
            inv.setItem(32, buyUpgradeItem(className, data));
        }

        openSessions.put(player.getUniqueId(), "class:" + className);
        player.openInventory(inv);
    }

    private ItemStack abilityItem(String className, String key, ConfigurationSection ability,
                                   boolean selected, ClassData data) {
        int idx = KEY_TO_INDEX.getOrDefault(key, 0);
        boolean ultimate = key.equals("ultimate");
        int unlockLevel = ultimate ? cachedUltimateLevel : ABILITY_UNLOCKS[Math.min(idx, ABILITY_UNLOCKS.length - 1)];
        boolean unlocked = selected && data.level() >= unlockLevel;

        Material icon = ultimate
                ? Material.NETHER_STAR
                : material(ability.getString("icon", ""), ABILITY_FALLBACK_ICONS[Math.min(idx, ABILITY_FALLBACK_ICONS.length - 1)]);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        String name = ability.getString("name", key);
        String description = ability.getString("description", "");
        int cooldown = ability.getInt("cooldown", 0);
        NamedTextColor color = classColor(className);

        meta.customName(Component.text(ABILITY_LABELS[idx] + ": " + name,
                unlocked ? (ultimate ? NamedTextColor.GOLD : color) : NamedTextColor.DARK_GRAY,
                TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (unlocked) {
            if (!description.isEmpty()) {
                lore.add(Component.text(description, NamedTextColor.GRAY));
            }
            if (cooldown > 0) {
                lore.add(Component.empty());
                lore.add(Component.text("Cooldown: " + cooldown + "s", NamedTextColor.YELLOW));
            }
        } else if (selected) {
            lore.add(Component.text("LOCKED — Reach level " + unlockLevel + " to unlock", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Select this class to unlock", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack upgradeItem(int level, String upgradeText, boolean selected, ClassData data) {
        boolean unlocked = selected && data.level() >= level;
        boolean isNext = selected && data.level() == level - 1;

        ItemStack item = new ItemStack(unlocked
                ? Material.LIME_STAINED_GLASS_PANE
                : (isNext ? Material.EXPERIENCE_BOTTLE : Material.GRAY_STAINED_GLASS_PANE));
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("LEVEL " + level,
                unlocked ? NamedTextColor.GREEN : (isNext ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY),
                TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        if (!upgradeText.isEmpty()) {
            lore.add(Component.text(upgradeText, unlocked ? NamedTextColor.GREEN
                    : (isNext ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY)));
        }
        lore.add(Component.empty());
        if (unlocked) {
            lore.add(Component.text("UNLOCKED", NamedTextColor.GREEN));
        } else if (isNext) {
            lore.add(Component.text("NEXT UPGRADE", NamedTextColor.GOLD));
            lore.add(Component.text("Buy it with the XP bottle to the right.", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("LOCKED", NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack backItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("Back", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack selectItem(String className, boolean selected, ClassData data) {
        ConfigurationSection cls = plugin.getConfig().getConfigurationSection("classes." + className);
        Material icon = material(cls != null ? cls.getString("icon", "") : "", Material.SHIELD);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = classColor(className);

        if (selected) {
            meta.customName(Component.text("YOUR CLASS — " + className.toUpperCase(), color, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("Level: " + data.level() + "/" + classManager.getMaxLevel(),
                            NamedTextColor.GOLD)));
        } else {
            meta.customName(Component.text("SELECT " + className.toUpperCase(), color, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("First selection grants the starter kit.", NamedTextColor.GRAY),
                    Component.text("Click to select.", NamedTextColor.YELLOW)));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buyUpgradeItem(String className, ClassData data) {
        if (data.level() >= classManager.getMaxLevel()) {
            ItemStack item = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = item.getItemMeta();
            meta.customName(Component.text("MAX LEVEL", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("You have mastered " + className + ".", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            return item;
        }

        int cost = classManager.getUpgradeCost(data.level());
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("UPGRADE TO LEVEL " + (data.level() + 1),
                NamedTextColor.GOLD, TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Cost: " + cost + " shards", NamedTextColor.GRAY));
        List<String> upgrades = plugin.getConfig().getStringList("upgrades." + className);
        if (data.level() < upgrades.size()) {
            lore.add(Component.empty());
            lore.add(Component.text(upgrades.get(data.level()), NamedTextColor.GREEN));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to upgrade.", NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== CLICK HANDLING ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String session = openSessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        ClassData data = classManager.getClassData(player.getUniqueId());

        if (session.equals("main")) {
            if (slot == 26 && !data.className().equals("none")) {
                handleClassReset(player);
                return;
            }
            int[] cardSlots = {11, 12, 13, 14};
            for (int i = 0; i < cardSlots.length; i++) {
                if (slot == cardSlots[i]) {
                    switchingGui.add(player.getUniqueId());
                    openClassMenu(player, CLASS_ORDER[i]);
                    return;
                }
            }
            return;
        }

        if (session.startsWith("class:")) {
            String className = session.substring("class:".length());
            if (slot == 30) {
                switchingGui.add(player.getUniqueId());
                openMainMenu(player);
                return;
            }
            if (slot == 31 && !className.equals(data.className())) {
                handleClassSelect(player, className);
                return;
            }
            if (slot == 32 && className.equals(data.className())
                    && data.level() < classManager.getMaxLevel()) {
                handleUpgrade(player, data);
            }
        }
    }

    // ==================== ACTIONS ====================

    private void handleClassSelect(Player player, String className) {
        boolean firstSelect = !classManager.hasClass(player.getUniqueId());
        classManager.setClass(player.getUniqueId(), className);
        if (firstSelect) {
            plugin.getStarterKit().giveIfFirstSelect(player);
        }
        player.sendMessage(plugin.getComponent("class-selected", "<class>",
                className.substring(0, 1).toUpperCase() + className.substring(1)));
        classManager.applyMaxHealth(player, classManager.getClassData(player.getUniqueId()).level());
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        switchingGui.add(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> openClassMenu(player, className), 5L);
    }

    private void handleClassReset(Player player) {
        int cost = classManager.getResetCost();
        var economy = getEconomy();
        if (economy == null) {
            plugin.getLogger().warning("Vault economy unavailable — blocking class reset for " + player.getName());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return;
        }
        try {
            if (!economy.has(player, cost)) {
                player.sendMessage(Component.text("Not enough shards! Need " + cost + " shards.", NamedTextColor.RED));
                return;
            }
            var resp = economy.withdrawPlayer(player, cost);
            if (!resp.transactionSuccess()) {
                player.sendMessage(Component.text("Economy error: " + resp.errorMessage, NamedTextColor.RED));
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Vault economy error for reset check: " + e.getMessage());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return;
        }
        classManager.resetClass(player.getUniqueId());
        classManager.applyMaxHealth(player, 0);
        player.sendMessage(plugin.getComponent("class-reset"));
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);

        openSessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    private void handleUpgrade(Player player, ClassData data) {
        int cost = classManager.getUpgradeCost(data.level());
        var economy = getEconomy();
        if (economy == null) {
            plugin.getLogger().warning("Vault economy unavailable — blocking upgrade for " + player.getName());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return;
        }
        try {
            if (!economy.has(player, cost)) {
                player.sendMessage(Component.text("Not enough shards! Need " + cost + " shards.", NamedTextColor.RED));
                return;
            }
            var resp = economy.withdrawPlayer(player, cost);
            if (!resp.transactionSuccess()) {
                player.sendMessage(Component.text("Economy error: " + resp.errorMessage, NamedTextColor.RED));
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Vault economy error for upgrade check: " + e.getMessage());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return;
        }
        boolean leveledUp = classManager.addXp(player.getUniqueId(), classManager.getXpForLevel(data.level() + 1));
        if (!leveledUp) return;

        ClassData newData = classManager.getClassData(player.getUniqueId());
        player.sendMessage(plugin.getComponent("level-up",
                "<level>", String.valueOf(newData.level()),
                "<class>", newData.className().substring(0, 1).toUpperCase() + newData.className().substring(1)));
        classManager.applyMaxHealth(player, newData.level());
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Close + reopen like class select — switchingGui keeps the session
        // alive across the close event, otherwise the reopened GUI has no
        // session and every click is dead.
        switchingGui.add(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> openClassMenu(player, newData.className()), 10L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (switchingGui.remove(player.getUniqueId())) return;
        openSessions.remove(player.getUniqueId());
    }

    // ==================== HELPERS ====================

    private void fillBorder(Inventory inv, int size) {
        for (int i = 0; i < size; i++) {
            inv.setItem(i, CACHED_BORDER.clone());
        }
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Material.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private NamedTextColor classColor(String className) {
        ConfigurationSection cls = plugin.getConfig().getConfigurationSection("classes." + className);
        String colorName = cls != null ? cls.getString("color", "") : "";
        return COLOR_FALLBACKS.getOrDefault(colorName, NamedTextColor.WHITE);
    }

    private String colorName(String className) {
        ConfigurationSection cls = plugin.getConfig().getConfigurationSection("classes." + className);
        String colorName = cls != null ? cls.getString("color", "") : "";
        if (COLOR_FALLBACKS.containsKey(colorName)) return colorName.toLowerCase(java.util.Locale.ROOT);
        return "white";
    }

    private int ArraysIndexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return 0;
    }
}
