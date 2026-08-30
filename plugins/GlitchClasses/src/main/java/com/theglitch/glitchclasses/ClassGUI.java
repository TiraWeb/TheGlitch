package com.theglitch.glitchclasses;

import com.theglitch.glitchclasses.ui.DialogUI;
import com.theglitch.glitchclasses.ui.FloatingBanner;
import com.theglitch.glitchclasses.ui.UiKit;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
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
    private volatile boolean holoEnabled = true;
    private net.milkbowl.vault.economy.Economy cachedEconomy;
    private long economyCacheTime;

    public ClassGUI(GlitchClasses plugin, ClassManager classManager) {
        this.plugin = plugin;
        this.classManager = classManager;
        reloadConfig();
    }

    public void reloadConfig() {
        cachedUltimateLevel = plugin.getConfig().getInt("ultimate-level", 10);
        holoEnabled = plugin.getConfig().getBoolean("modern-ui.hologram-banner", true);
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

    // ==================== MAIN MENU (54 slots) ====================

    public void openMainMenu(Player player) {
        ClassData data = classManager.getClassData(player.getUniqueId());

        String titleMini = UiKit.titleCustom(UiKit.classGradientFrom(data.className()),
                UiKit.classGradientTo(data.className()), "CHOOSE YOUR CLASS");
        if (holoEnabled) {
            FloatingBanner.show(plugin, player, titleMini, 90L);
        }
        Inventory inv = Bukkit.createInventory(null, 54, UiKit.mm().deserialize(titleMini));

        for (int col = 0; col < 9; col++) {
            inv.setItem(col, UiKit.rampPane(col));
        }
        for (int slot = 45; slot < 54; slot++) {
            inv.setItem(slot, UiKit.blankPane(Material.BLACK_STAINED_GLASS_PANE));
        }
        inv.setItem(18, UiKit.blankPane(UiKit.RAMP[4]));
        inv.setItem(26, UiKit.blankPane(UiKit.RAMP[4]));
        inv.setItem(27, UiKit.blankPane(UiKit.RAMP[4]));
        inv.setItem(35, UiKit.blankPane(UiKit.RAMP[4]));

        int[] cardSlots = {19, 21, 23, 25};
        for (int i = 0; i < CLASS_ORDER.length; i++) {
            inv.setItem(cardSlots[i], classCard(CLASS_ORDER[i], data));
        }

        inv.setItem(4, infoItem(data));
        inv.setItem(9, UiKit.runeCorner());
        inv.setItem(17, UiKit.runeCorner());
        inv.setItem(40, hintItem());

        if (!data.className().equals("none")) {
            inv.setItem(47, resetItem());
            inv.setItem(49, closeItem());
        } else {
            inv.setItem(47, closeItem());
            inv.setItem(49, closeItem());
        }

        openSessions.put(player.getUniqueId(), "main54");
        player.openInventory(inv);
    }

    private ItemStack hintItem() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize("<gray><italic>Choose wisely — you can reset for shards later.</italic></gray>"));
        meta.lore(List.of(
                MM.deserialize("<gray>Click a class above to view its abilities.</gray>"),
                MM.deserialize("<dark_gray>First pick grants the starter kit.</dark_gray>")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack closeItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MM.deserialize("<red><bold>Close</bold></red>"));
        meta.lore(List.of(MM.deserialize("<gray>Close this menu.</gray>")));
        item.setItemMeta(meta);
        return item;
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
        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        List<Component> lore = new ArrayList<>();
        String role = cls != null ? cls.getString("role", "") : "";
        String description = cls != null ? cls.getString("description", "") : "";
        // glyph + role line — use shard glyph for flavor
        if (!role.isEmpty()) {
            lore.add(MM.deserialize("\uE049 <gray><italic>" + role + "</italic></gray>"));
        }
        if (!description.isEmpty()) {
            lore.add(MM.deserialize("<gray>" + description + "</gray>"));
        }
        // ability preview line
        ConfigurationSection ab = plugin.getConfig().getConfigurationSection("abilities." + className);
        if (ab != null) {
            String prime = ab.getConfigurationSection("prime") != null ? ab.getConfigurationSection("prime").getString("name", "Prime") : "Prime";
            lore.add(Component.empty());
            lore.add(MM.deserialize("<dark_gray>Prime:</dark_gray> <white>" + prime + "</white>"));
        }
        lore.add(Component.empty());
        if (selected) {
            lore.add(MM.deserialize("<green><bold>✦ YOUR CLASS</bold></green>"));
            lore.add(MM.deserialize("<gray>Level:</gray> <gold>" + data.level() + "</gold><gray>/</gray><gold>" + classManager.getMaxLevel() + "</gold>"));
            lore.add(MM.deserialize("<yellow>Click to manage →</yellow>"));
        } else {
            lore.add(MM.deserialize("<yellow><bold>Click to view</bold></yellow>"));
            lore.add(MM.deserialize("<gray>View abilities & upgrade path.</gray>"));
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

        String titleMini = UiKit.titleCustom(UiKit.classGradientFrom(className),
                UiKit.classGradientTo(className), className.toUpperCase(java.util.Locale.ROOT));
        if (holoEnabled) {
            FloatingBanner.show(plugin, player, titleMini, 90L);
        }
        Inventory inv = Bukkit.createInventory(null, 45, UiKit.mm().deserialize(titleMini));

        paintBands45(inv);
        inv.setItem(4, UiKit.pipsItem(data.level(), classManager.getMaxLevel()));

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
                ? Material.PURPLE_STAINED_GLASS_PANE
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

        if (session.equals("main54")) {
            if (slot == 49 || (slot == 47 && data.className().equals("none"))) {
                player.closeInventory();
                return;
            }
            if (slot == 47) {
                handleClassReset(player);
                return;
            }
            int[] cardSlots = {19, 21, 23, 25};
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

    public String[] classOrder() {
        return CLASS_ORDER.clone();
    }

    public int ultimateLevelPublic() {
        return cachedUltimateLevel;
    }

    // ==================== ACTIONS ====================

    private void handleClassSelect(Player player, String className) {
        applyClassSelectCore(player, className);

        switchingGui.add(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> openClassMenu(player, className), 5L);
    }

    private void applyClassSelectCore(Player player, String className) {
        boolean firstSelect = !classManager.hasClass(player.getUniqueId());
        classManager.setClass(player.getUniqueId(), className);
        if (firstSelect) {
            plugin.getStarterKit().giveIfFirstSelect(player);
        }
        player.sendMessage(plugin.getComponent("class-selected", "<class>",
                className.substring(0, 1).toUpperCase() + className.substring(1)));
        classManager.applyMaxHealth(player, classManager.getClassData(player.getUniqueId()).level());
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    private void handleClassReset(Player player) {
        if (!applyResetCore(player)) return;

        openSessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    private boolean applyResetCore(Player player) {
        int cost = classManager.getResetCost();
        var economy = getEconomy();
        if (economy == null) {
            plugin.getLogger().warning("Vault economy unavailable — blocking class reset for " + player.getName());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return false;
        }
        try {
            if (!economy.has(player, cost)) {
                player.sendMessage(Component.text("Not enough shards! Need " + cost + " shards.", NamedTextColor.RED));
                return false;
            }
            var resp = economy.withdrawPlayer(player, cost);
            if (!resp.transactionSuccess()) {
                player.sendMessage(Component.text("Economy error: " + resp.errorMessage, NamedTextColor.RED));
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Vault economy error for reset check: " + e.getMessage());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return false;
        }
        classManager.resetClass(player.getUniqueId());
        classManager.applyMaxHealth(player, 0);
        player.sendMessage(plugin.getComponent("class-reset"));
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
        return true;
    }

    private void handleUpgrade(Player player, ClassData data) {
        ClassData newData = applyUpgradeCore(player, data);
        if (newData == null) return;

        // Close + reopen like class select — switchingGui keeps the session
        // alive across the close event, otherwise the reopened GUI has no
        // session and every click is dead.
        switchingGui.add(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> openClassMenu(player, newData.className()), 10L);
    }

    private ClassData applyUpgradeCore(Player player, ClassData data) {
        int cost = classManager.getUpgradeCost(data.level());
        var economy = getEconomy();
        if (economy == null) {
            plugin.getLogger().warning("Vault economy unavailable — blocking upgrade for " + player.getName());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return null;
        }
        try {
            if (!economy.has(player, cost)) {
                player.sendMessage(Component.text("Not enough shards! Need " + cost + " shards.", NamedTextColor.RED));
                return null;
            }
            var resp = economy.withdrawPlayer(player, cost);
            if (!resp.transactionSuccess()) {
                player.sendMessage(Component.text("Economy error: " + resp.errorMessage, NamedTextColor.RED));
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Vault economy error for upgrade check: " + e.getMessage());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return null;
        }
        boolean leveledUp = classManager.addXp(player.getUniqueId(), classManager.getXpForLevel(data.level() + 1));
        if (!leveledUp) {
            economy.depositPlayer(player, cost);
            player.sendMessage(Component.text("Upgrade failed — shards refunded.", NamedTextColor.RED));
            return null;
        }

        ClassData newData = classManager.getClassData(player.getUniqueId());
        player.sendMessage(plugin.getComponent("level-up",
                "<level>", String.valueOf(newData.level()),
                "<class>", newData.className().substring(0, 1).toUpperCase() + newData.className().substring(1)));
        classManager.applyMaxHealth(player, newData.level());
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        return newData;
    }

    // ==================== DIALOG ENTRY POINTS ====================

    public boolean selectFromDialog(Player player, String className) {
        if (!isConfiguredClass(className)) return false;
        applyClassSelectCore(player, className);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> DialogUI.openClass(plugin, this, player, className,
                        () -> openClassMenu(player, className)),
                5L);
        return true;
    }

    public void upgradeFromDialog(Player player) {
        ClassData data = classManager.getClassData(player.getUniqueId());
        String current = data.className();
        if (current.equals("none")) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> DialogUI.openRoot(plugin, this, player, () -> openMainMenu(player)), 5L);
            return;
        }
        if (data.level() >= classManager.getMaxLevel()) {
            player.sendMessage(Component.text("Already at max level.", NamedTextColor.YELLOW));
            return;
        }
        applyUpgradeCore(player, data);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> DialogUI.openClass(plugin, this, player, current,
                        () -> openClassMenu(player, current)),
                5L);
    }

    public boolean resetFromDialog(Player player) {
        boolean done = false;
        if (classManager.hasClass(player.getUniqueId())) {
            done = applyResetCore(player);
        }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> DialogUI.openRoot(plugin, this, player, () -> openMainMenu(player)), 5L);
        return done;
    }

    private boolean isConfiguredClass(String className) {
        for (String c : CLASS_ORDER) {
            if (c.equalsIgnoreCase(className)) return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (switchingGui.remove(player.getUniqueId())) return;
        openSessions.remove(player.getUniqueId());
    }

    // ==================== HELPERS ====================

    /**
     * Modern framing for the shared 45-slot class menu: gradient header row,
     * BLUE side rails on the middle rows, BLACK footer row. Occupied
     * (non-border) cells are skipped so controls always win.
     */
    private void paintBands45(Inventory inv) {
        for (int col = 0; col < 9; col++) {
            setPaneIfFree(inv, col, UiKit.rampPane(col));
        }
        int[] rails = {9, 17, 18, 26};
        for (int slot : rails) {
            setPaneIfFree(inv, slot, UiKit.blankPane(UiKit.RAMP[4]));
        }
        for (int slot = 36; slot < 45; slot++) {
            setPaneIfFree(inv, slot, UiKit.blankPane(Material.BLACK_STAINED_GLASS_PANE));
        }
    }

    private void setPaneIfFree(Inventory inv, int slot, ItemStack pane) {
        ItemStack current = inv.getItem(slot);
        if (current == null || current.getType() == Material.AIR
                || current.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            inv.setItem(slot, pane);
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
}
