package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Class selection GUI with upgrade tree.
 * Main menu: 6-row chest with themed border, 4 class cards, reset button.
 * Upgrade menu: class-specific page with abilities, stats, and upgrade button.
 */
public class ClassGUI implements Listener {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final NamespacedKey CLASS_KEY = new NamespacedKey(GlitchClasses.getInstance(), "class_id");
    private static final NamespacedKey ACTION_KEY = new NamespacedKey(GlitchClasses.getInstance(), "action");
    private static final NamespacedKey PAGE_KEY = new NamespacedKey(GlitchClasses.getInstance(), "page");

    private static final Map<UUID, String> openClassSessions = new HashMap<>();
    private static final Set<UUID> switchingGui = new HashSet<>();

    private final GlitchClasses plugin;
    private final ClassManager classManager;

    // Class order in GUI
    private static final String[] CLASS_ORDER = {"vanguard", "warden", "specter", "operator"};

    // Class colors
    private static final Map<String, NamedTextColor> CLASS_COLORS = Map.of(
            "vanguard", NamedTextColor.RED,
            "warden", NamedTextColor.GREEN,
            "specter", NamedTextColor.DARK_PURPLE,
            "operator", NamedTextColor.AQUA
    );

    // Class display items
    private static final Map<String, Material> CLASS_ICONS = Map.of(
            "vanguard", Material.SHIELD,
            "warden", Material.GOLDEN_APPLE,
            "specter", Material.ENDER_EYE,
            "operator", Material.REDSTONE
    );

    // Class role descriptions
    private static final Map<String, String> CLASS_ROLES = Map.of(
            "vanguard", "Tank / Frontline",
            "warden", "Support / Healer",
            "specter", "Stealth / Looter",
            "operator", "Tech / Control"
    );

    // Ability icons per class
    private static final Map<String, Material[]> CLASS_ABILITY_ICONS = Map.of(
            "vanguard", new Material[]{Material.BARRIER, Material.RED_BED, Material.IRON_CHESTPLATE, Material.TOTEM_OF_UNDYING},
            "warden", new Material[]{Material.GLISTERING_MELON_SLICE, Material.BEACON, Material.BREAD, Material.SPECTRAL_ARROW},
            "specter", new Material[]{Material.PHANTOM_MEMBRANE, Material.ENDER_PEARL, Material.FEATHER, Material.CHEST},
            "operator", new Material[]{Material.DISPENSER, Material.ENDER_PEARL, Material.ANVIL, Material.CLOCK}
    );

    // Ability type labels
    private static final String[] ABILITY_TYPE_LABELS = {"PRIME", "TACTICAL", "TRAIT I", "TRAIT II"};

    public ClassGUI(GlitchClasses plugin, ClassManager classManager) {
        this.plugin = plugin;
        this.classManager = classManager;
    }

    /**
     * Open the main class selection GUI.
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE,
                MiniMessage.miniMessage().deserialize("<dark_purple><bold>CHOOSE YOUR CLASS</bold></dark_purple>"));

        // Fill border with black stained glass
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.customName(Component.empty());
        border.setItemMeta(borderMeta);
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        // Player's current class
        ClassData data = classManager.getClassData(player.getUniqueId());
        String currentClass = data.className();

        // Place class cards in row 2 (slots 10, 11, 12, 13)
        // and row 4 (slots 37, 38, 39, 40) for the upgrade tree
        int[] classSlots = {10, 11, 12, 13};
        int[] upgradeSlots = {37, 38, 39, 40};

        for (int i = 0; i < CLASS_ORDER.length; i++) {
            String className = CLASS_ORDER[i];
            boolean isSelected = className.equals(currentClass);
            inv.setItem(classSlots[i], createClassCard(className, isSelected, data));
            inv.setItem(upgradeSlots[i], createClassUpgradeCard(className, isSelected, data));
        }

        // Info display — top center
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.customName(Component.text("CLASS SYSTEM", NamedTextColor.GOLD, TextDecoration.BOLD));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.empty());
        infoLore.add(Component.text("Choose a class to specialize in.", NamedTextColor.GRAY));
        infoLore.add(Component.text("Each class has unique abilities", NamedTextColor.GRAY));
        infoLore.add(Component.text("that upgrade as you level up.", NamedTextColor.GRAY));
        if (!currentClass.equals("none")) {
            infoLore.add(Component.empty());
            infoLore.add(Component.text("Current: ", NamedTextColor.GRAY)
                    .append(Component.text(currentClass.toUpperCase(),
                            CLASS_COLORS.getOrDefault(currentClass, NamedTextColor.WHITE),
                            TextDecoration.BOLD)));
            infoLore.add(Component.text("Level: ", NamedTextColor.GRAY)
                    .append(Component.text(data.level() + "/" + classManager.getMaxLevel(),
                            NamedTextColor.GOLD)));
        } else {
            infoLore.add(Component.empty());
            infoLore.add(Component.text("You have no class selected.", NamedTextColor.RED));
        }
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Reset button — bottom center
        if (!currentClass.equals("none")) {
            ItemStack reset = new ItemStack(Material.BARRIER);
            ItemMeta resetMeta = reset.getItemMeta();
            resetMeta.customName(Component.text("RESET CLASS", NamedTextColor.RED, TextDecoration.BOLD));
            List<Component> resetLore = new ArrayList<>();
            resetLore.add(Component.empty());
            resetLore.add(Component.text("Cost: " + classManager.getResetCost() + " shards", NamedTextColor.GRAY));
            resetLore.add(Component.text("This will reset your class to none.", NamedTextColor.RED));
            resetLore.add(Component.empty());
            resetLore.add(Component.text("Click to confirm.", NamedTextColor.YELLOW));
            resetMeta.lore(resetLore);
            reset.setItemMeta(resetMeta);
            inv.setItem(49, reset);
        }

        openClassSessions.put(player.getUniqueId(), "main");
        player.openInventory(inv);
    }

    /**
     * Open the upgrade page for a specific class.
     */
    public void openUpgradeMenu(Player player, String className) {
        ClassData data = classManager.getClassData(player.getUniqueId());
        boolean isSelected = className.equals(data.className());

        Inventory inv = Bukkit.createInventory(null, SIZE,
                MiniMessage.miniMessage().deserialize(
                        "<" + getColorName(className) + "><bold>" + className.toUpperCase() + "</bold></" + getColorName(className) + ">"));

        // Border
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.customName(Component.empty());
        border.setItemMeta(borderMeta);
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        // Class icon — large display
        Material icon = CLASS_ICONS.get(className);
        ItemStack classIcon = new ItemStack(icon);
        ItemMeta iconMeta = classIcon.getItemMeta();
        NamedTextColor color = CLASS_COLORS.get(className);
        iconMeta.customName(Component.text(className.toUpperCase(), color, TextDecoration.BOLD));
        List<Component> iconLore = new ArrayList<>();
        iconLore.add(Component.empty());
        iconLore.add(Component.text("Role: " + CLASS_ROLES.get(className), NamedTextColor.GRAY));
        if (isSelected) {
            iconLore.add(Component.text("Level: " + data.level() + "/" + classManager.getMaxLevel(), NamedTextColor.GOLD));
            int xpNeeded = classManager.getXpForLevel(data.level() + 1);
            if (data.level() < classManager.getMaxLevel()) {
                iconLore.add(Component.text("XP: " + data.xp() + "/" + xpNeeded, NamedTextColor.GRAY));
            }
        } else {
            iconLore.add(Component.text("Click to select this class", NamedTextColor.YELLOW));
        }
        iconMeta.lore(iconLore);
        classIcon.setItemMeta(iconMeta);
        inv.setItem(4, classIcon);

        // Abilities — row 2 (slots 10-14: prime, tactical, trait1, trait2, ultimate)
        String[] abilityKeys = {"prime", "tactical", "trait1", "trait2", "ultimate"};
        int[] abilitySlots = {10, 11, 12, 13, 14};
        int[] abilityUnlocks = {1, 1, 1, 3, 10};
        ConfigurationSection abilities = plugin.getConfig().getConfigurationSection("abilities." + className);

        for (int i = 0; i < abilityKeys.length; i++) {
            String abilityKey = abilityKeys[i];
            ConfigurationSection ability = abilities.getConfigurationSection(abilityKey);
            Material abilityIcon = CLASS_ABILITY_ICONS.get(className)[Math.min(i, 3)];

            boolean isUltimate = abilityKey.equals("ultimate");
            boolean unlocked = isSelected && data.level() >= abilityUnlocks[i];

            ItemStack item = new ItemStack(isUltimate ? Material.NETHER_STAR : abilityIcon);
            ItemMeta itemMeta = item.getItemMeta();

            String abilityName = ability.getString("name", abilityKey);
            String abilityDesc = ability.getString("description", "");
            int cooldown = ability.getInt("cooldown", 0);

            if (isUltimate) {
                itemMeta.customName(Component.text("ULTIMATE: " + abilityName.toUpperCase(),
                        NamedTextColor.GOLD, TextDecoration.BOLD));
            } else {
                itemMeta.customName(Component.text(ABILITY_TYPE_LABELS[i] + ": " + abilityName,
                        unlocked ? color : NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
            }

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (unlocked) {
                lore.add(Component.text(abilityDesc, NamedTextColor.GRAY));
                if (cooldown > 0) {
                    lore.add(Component.empty());
                    lore.add(Component.text("Cooldown: " + cooldown + "s", NamedTextColor.YELLOW));
                }
            } else if (isSelected) {
                lore.add(Component.text("LOCKED — Reach level " + abilityUnlocks[i] + " to unlock",
                        NamedTextColor.RED));
            } else {
                lore.add(Component.text("Select this class to unlock", NamedTextColor.GRAY));
            }
            itemMeta.lore(lore);

            if (unlocked) {
                itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }

            item.setItemMeta(itemMeta);
            inv.setItem(abilitySlots[i], item);
        }

        // Upgrade section — row 4
        if (isSelected && data.level() < classManager.getMaxLevel()) {
            int upgradeCost = classManager.getUpgradeCost(data.level());
            ItemStack upgrade = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta upgradeMeta = upgrade.getItemMeta();
            upgradeMeta.customName(Component.text("UPGRADE TO LEVEL " + (data.level() + 1),
                    NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> upgradeLore = new ArrayList<>();
            upgradeLore.add(Component.empty());
            upgradeLore.add(Component.text("Cost: " + upgradeCost + " shards", NamedTextColor.GRAY));
            int currentLevel = data.level();
            List<String> upgrades = plugin.getConfig().getStringList("upgrades." + className);
            if (currentLevel < upgrades.size()) {
                upgradeLore.add(Component.empty());
                upgradeLore.add(Component.text(upgrades.get(currentLevel), NamedTextColor.GREEN));
            }
            upgradeLore.add(Component.empty());
            upgradeLore.add(Component.text("Click to upgrade", NamedTextColor.YELLOW));
            upgradeMeta.lore(upgradeLore);
            upgrade.setItemMeta(upgradeMeta);
            inv.setItem(40, upgrade);
        } else if (isSelected) {
            // Max level display
            ItemStack maxItem = new ItemStack(Material.NETHER_STAR);
            ItemMeta maxMeta = maxItem.getItemMeta();
            maxMeta.customName(Component.text("MAX LEVEL", NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> maxLore = new ArrayList<>();
            maxLore.add(Component.empty());
            maxLore.add(Component.text("You have mastered the " + className + ".", NamedTextColor.GRAY));
            maxLore.add(Component.text("Your abilities are at full power.", NamedTextColor.GRAY));
            maxMeta.lore(maxLore);
            maxItem.setItemMeta(maxMeta);
            inv.setItem(40, maxItem);
        }

        // Back button — bottom left
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.customName(Component.text("Back to class selection", NamedTextColor.GRAY));
        back.setItemMeta(backMeta);
        inv.setItem(45, back);

        // Role info — row 3 (slots 28, 29, 30, 31)
        String[] roleItems = {"SHIELD", "GOLDEN_APPLE", "ENDER_EYE", "REDSTONE"};
        for (int i = 0; i < CLASS_ORDER.length; i++) {
            String rc = CLASS_ORDER[i];
            Material roleIcon = Material.valueOf(roleItems[i]);
            ItemStack roleItem = new ItemStack(rc.equals(className) ? roleIcon : Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta roleMeta = roleItem.getItemMeta();
            roleMeta.customName(Component.text(rc.substring(0, 1).toUpperCase() + rc.substring(1),
                    rc.equals(className) ? CLASS_COLORS.get(rc) : NamedTextColor.DARK_GRAY));
            roleItem.setItemMeta(roleMeta);
            inv.setItem(28 + i, roleItem);
        }

        openClassSessions.put(player.getUniqueId(), "upgrade:" + className);
        player.openInventory(inv);
    }

    private ItemStack createClassCard(String className, boolean isSelected, ClassData data) {
        Material icon = CLASS_ICONS.get(className);
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor color = CLASS_COLORS.get(className);
        if (isSelected) {
            meta.customName(Component.text(className.toUpperCase(), color, TextDecoration.BOLD));
        } else {
            meta.customName(Component.text(className.substring(0, 1).toUpperCase() + className.substring(1), color));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Role: " + CLASS_ROLES.get(className), NamedTextColor.GRAY));

        if (isSelected) {
            lore.add(Component.text("Level: " + data.level() + "/" + classManager.getMaxLevel(), NamedTextColor.GOLD));
            lore.add(Component.empty());
            lore.add(Component.text("YOUR CLASS", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("Click to view abilities", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClassUpgradeCard(String className, boolean isSelected, ClassData data) {
        Material icon = CLASS_ICONS.get(className);
        ItemStack item = new ItemStack(isSelected ? icon : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor color = CLASS_COLORS.get(className);
        String prefix = isSelected ? "" : "??? - ";

        meta.customName(Component.text(prefix + className.toUpperCase() + " UPGRADES",
                isSelected ? color : NamedTextColor.DARK_GRAY));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        List<String> upgrades = plugin.getConfig().getStringList("upgrades." + className);
        for (int i = 0; i < Math.min(upgrades.size(), 10); i++) {
            boolean unlocked = isSelected && data.level() > i;
            boolean isNext = isSelected && data.level() == i;
            String status = unlocked ? "<green>" : (isNext ? "<gold>" : "<dark_gray>");
            String icon2 = unlocked ? "\u2714" : (isNext ? "\u25B6" : "\u2718");
            lore.add(MiniMessage.miniMessage().deserialize(status + icon2 + " " + upgrades.get(i) + "</" + (unlocked ? "green" : (isNext ? "gold" : "dark_gray")) + ">"));
        }

        if (isSelected) {
            lore.add(Component.empty());
            lore.add(Component.text("Click to open upgrade page", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        String session = openClassSessions.get(uuid);
        if (session == null) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        ClassData data = classManager.getClassData(uuid);

        if (session.equals("main")) {
            handleMainMenuClick(player, slot, data);
        } else if (session.startsWith("upgrade:")) {
            String className = session.substring(7);
            handleUpgradeMenuClick(player, slot, data, className);
        }
    }

    private void handleMainMenuClick(Player player, int slot, ClassData data) {
        // Class cards — row 2 (slots 10, 11, 12, 13)
        int[] classSlots = {10, 11, 12, 13};
        for (int i = 0; i < classSlots.length; i++) {
            if (slot == classSlots[i]) {
                switchingGui.add(player.getUniqueId());
                openUpgradeMenu(player, CLASS_ORDER[i]);
                return;
            }
        }

        // Upgrade cards — row 4 (slots 37, 38, 39, 40)
        int[] upgradeSlots = {37, 38, 39, 40};
        for (int i = 0; i < upgradeSlots.length; i++) {
            if (slot == upgradeSlots[i]) {
                switchingGui.add(player.getUniqueId());
                openUpgradeMenu(player, CLASS_ORDER[i]);
                return;
            }
        }

        // Reset button — slot 49
        if (slot == 49 && !data.className().equals("none")) {
            handleClassReset(player);
        }
    }

    private void handleUpgradeMenuClick(Player player, int slot, ClassData data, String className) {
        // Back button — slot 45
        if (slot == 45) {
            switchingGui.add(player.getUniqueId());
            openMainMenu(player);
            return;
        }

        // Select class — slot 4 (class icon)
        if (slot == 4 && !className.equals(data.className())) {
            handleClassSelect(player, className);
            return;
        }

        // Upgrade button — slot 40
        if (slot == 40 && className.equals(data.className()) && data.level() < classManager.getMaxLevel()) {
            handleUpgrade(player, data);
            return;
        }
    }

    private void handleClassSelect(Player player, String className) {
        boolean firstSelect = !classManager.hasClass(player.getUniqueId());
        classManager.setClass(player.getUniqueId(), className);
        switchingGui.add(player.getUniqueId());
        player.closeInventory();
        if (firstSelect) {
            plugin.getStarterKit().giveIfFirstSelect(player);
        }
        player.sendMessage(plugin.getComponent("class-selected", "<class>", className.substring(0, 1).toUpperCase() + className.substring(1)));

        // Apply health boost
        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20 + (classManager.getClassData(player.getUniqueId()).level() * 2));

        // Play select sound
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        // Re-open the upgrade menu for the newly selected class
        Bukkit.getScheduler().runTaskLater(plugin, () -> openUpgradeMenu(player, className), 5L);
    }

    private void handleClassReset(Player player) {
        int cost = classManager.getResetCost();

        // Charge the reset cost via Vault (shards)
        try {
            var eco = org.bukkit.Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (eco != null) {
                net.milkbowl.vault.economy.Economy economy = eco.getProvider();
                if (!economy.has(player, cost)) {
                    player.sendMessage(Component.text("Not enough shards! Need " + cost + " shards.",
                            NamedTextColor.RED));
                    return;
                }
                economy.withdrawPlayer(player, cost);
            }
        } catch (Exception e) {
            // Vault not available — log warning but allow reset anyway
            plugin.getLogger().warning("Vault economy not available for reset check: " + e.getMessage());
        }

        classManager.resetClass(player.getUniqueId());
        switchingGui.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(plugin.getComponent("class-reset"));

        // Reset health
        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20);

        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
    }

    private void handleUpgrade(Player player, ClassData data) {
        int cost = classManager.getUpgradeCost(data.level());

        // Check if player has enough shards via Vault economy
        try {
            var eco = org.bukkit.Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (eco != null) {
                net.milkbowl.vault.economy.Economy economy = eco.getProvider();
                if (!economy.has(player, cost)) {
                    player.sendMessage(Component.text("Not enough shards! Need " + cost + " shards.",
                            NamedTextColor.RED));
                    return;
                }
                economy.withdrawPlayer(player, cost);
            }
        } catch (Exception e) {
            // Vault not available — log warning but allow upgrade anyway
            plugin.getLogger().warning("Vault economy not available for upgrade check: " + e.getMessage());
        }

        // Add exactly enough XP to reach the next level — one purchase = one
        // level (the XP curve requires 50 more XP than the upgrade cost, so
        // granting raw shard amounts would silently make upgrades 2-3x costlier).
        boolean leveledUp = classManager.addXp(player.getUniqueId(),
                classManager.getXpForLevel(data.level() + 1));

        if (leveledUp) {
            ClassData newData = classManager.getClassData(player.getUniqueId());
            player.sendMessage(plugin.getComponent("level-up",
                    "<level>", String.valueOf(newData.level()),
                    "<class>", newData.className().substring(0, 1).toUpperCase() + newData.className().substring(1)));

            // Apply health boost
            player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20 + (newData.level() * 2));

            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // Re-open upgrade menu
            Bukkit.getScheduler().runTaskLater(plugin, () -> openUpgradeMenu(player, data.className()), 10L);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (switchingGui.remove(uuid)) return;
        openClassSessions.remove(uuid);
    }

    private String getColorName(String className) {
        NamedTextColor color = CLASS_COLORS.get(className);
        if (color == null) return "white";
        if (color.equals(NamedTextColor.RED)) return "red";
        if (color.equals(NamedTextColor.GREEN)) return "green";
        if (color.equals(NamedTextColor.DARK_PURPLE)) return "dark_purple";
        if (color.equals(NamedTextColor.AQUA)) return "aqua";
        return "white";
    }
}
