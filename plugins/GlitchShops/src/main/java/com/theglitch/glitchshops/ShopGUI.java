package com.theglitch.glitchshops;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ShopGUI implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    // \uE049 = glitch-diamond glyph (Oraxen glyphs/theglitch.yml)
    private static final Component BAZAAR_TITLE = MM.deserialize(
            "\uE049 <gradient:#C084FC:#F0ABFC><bold>GRAND BAZAAR</bold></gradient> \uE049");

    // Cached border — single allocation cloned per slot instead of new ItemStack per open * per slot
    private static final ItemStack CACHED_BORDER_PANE;
    static {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        CACHED_BORDER_PANE = pane;
    }

    private static final int SIZE = 54;

    private static final NamespacedKey ACTION_KEY = new NamespacedKey("glitchshops", "action");
    private static final NamespacedKey CATEGORY_KEY = new NamespacedKey("glitchshops", "category");
    private static final NamespacedKey ITEM_KEY = new NamespacedKey("glitchshops", "item");
    private static final NamespacedKey GEAR_SLOT_KEY = new NamespacedKey("glitchshops", "gearslot");

    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Set<UUID> switchingGui = new HashSet<>();

    private record Session(String category, boolean sellMode) {
    }

    private final GlitchShops plugin;
    private final ShopManager shopManager;

    // Cached hot-path config — refreshed on reload, no getConfig() per click/open
    private volatile List<String> cachedTabOrder;
    private volatile String cachedDefaultTab;
    private volatile int cachedBuyStackSize;
    private volatile Economy cachedEconomy;

    public ShopGUI(GlitchShops plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        refreshCache();
    }

    /** Refresh cached config after reload — called by GlitchShops.reloadPlugin(). */
    public void refreshCache() {
        this.cachedTabOrder = shopManager.getTabOrder();
        this.cachedDefaultTab = shopManager.getDefaultTab();
        this.cachedBuyStackSize = shopManager.getBuyStackSize();
        this.cachedEconomy = plugin.getEconomy(); // invalidated already in plugin
        if (this.cachedTabOrder == null || this.cachedTabOrder.isEmpty()) {
            plugin.getLogger().warning("ShopGUI: cached tab order empty — using fallback.");
            this.cachedTabOrder = List.of("materials", "keys", "alchemy", "rifts", "gear");
        }
        if (this.cachedDefaultTab == null || this.cachedDefaultTab.isBlank()) {
            this.cachedDefaultTab = this.cachedTabOrder.get(0);
        }
    }

    public void open(Player player, String category) {
        open(player, category, false);
    }

    public void open(Player player, String category, boolean sellMode) {
        // No getConfig() — use cached default tab and cached tab order
        if (cachedTabOrder == null) refreshCache();
        if (!cachedTabOrder.contains(category)) {
            category = cachedDefaultTab;
            if (category == null || !cachedTabOrder.contains(category)) {
                category = cachedTabOrder.get(0);
            }
        }
        Inventory inv = Bukkit.createInventory(null, SIZE, BAZAAR_TITLE);

        fillBorder(inv);

        inv.setItem(0, balanceItem(player));
        inv.setItem(3, tabButton("tab_buy", "gui_buy", Material.EMERALD, "BUY",
                "<green><bold>BUY</bold></green>",
                "<gray>Left-click = 1 · Shift-click = stack.</gray>", !sellMode));
        inv.setItem(4, tabButton("tab_sell", "gui_sell", Material.GOLD_INGOT, "SELL",
                "<gold><bold>SELL</bold></gold>",
                "<gray>Click items in your inventory below.</gray>", sellMode));
        inv.setItem(8, closeButton());

        for (int i = 0; i < cachedTabOrder.size(); i++) {
            String tab = cachedTabOrder.get(i);
            inv.setItem(9 + i, categoryTab(tab, tab.equals(category)));
        }

        if (sellMode) {
            inv.setItem(22, guiIcon("gui_coin", Material.GOLD_BLOCK,
                    "<gold><bold>SELLING</bold></gold>",
                    "<gray>Click items in your inventory below.</gray>",
                    "<yellow>Left-click = 1 · Shift-click = stack</yellow>"));
        } else {
            fillStock(inv, player, category);
        }

        sessions.put(player.getUniqueId(), new Session(category, sellMode));
        switchingGui.add(player.getUniqueId());
        player.openInventory(inv);
        switchingGui.remove(player.getUniqueId());
    }

    private void fillStock(Inventory inv, Player player, String category) {
        int slot = 18;
        if (category.equals("gear")) {
            for (int i = 0; i < shopManager.getGearStock().size() && slot < 27; i++) {
                ShopManager.GearStockEntry entry = shopManager.getGearStock().get(i);
                if (entry.item() == null) continue;
                ItemStack display = entry.item().clone();
                ItemMeta meta = display.getItemMeta();
                List<Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : meta.lore();
                lore.add(MM.deserialize(
                        entry.superRare() ? "<gold>SUPER RARE — max rolls</gold>" : ""));
                lore.add(Component.empty());
                lore.add(MM.deserialize("<aqua>Buy: " + entry.price() + " Shards</aqua>"));
                meta.lore(lore);
                display.setItemMeta(meta);
                int gearIndex = i;
                display.editMeta(ItemMeta.class, m -> {
                    m.getPersistentDataContainer().set(GEAR_SLOT_KEY, PersistentDataType.INTEGER, gearIndex);
                    m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "buygear");
                });
                inv.setItem(slot, display);
                slot++;
            }
            if (slot == 18) {
                inv.setItem(22, guiIcon("gui_close", Material.BARRIER,
                        "<red>Out of stock</red>",
                        "<gray>The vendor will restock soon.</gray>"));
            }
            return;
        }

        ShopManager.Shop shop = shopManager.getShop(category);
        if (shop == null) return;
        for (Map.Entry<String, ShopManager.StockEntry> entry : shop.stock().entrySet()) {
            if (slot >= 27) break;
            ItemStack item;
            try {
                ItemBuilder builder = OraxenItems.getItemById(entry.getKey());
                if (builder == null) {
                    plugin.getLogger().warning("Unknown Oraxen item in shop " + category + ": " + entry.getKey());
                    continue;
                }
                item = builder.build().clone();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to build Oraxen item " + entry.getKey() + ": " + e.getMessage());
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            List<Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : meta.lore();
            lore.add(Component.empty());
            lore.add(MM.deserialize("<aqua>Buy: " + entry.getValue().buy() + " Shards</aqua>"));
            meta.lore(lore);
            item.setItemMeta(meta);
            item.editMeta(ItemMeta.class, m -> {
                m.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, entry.getKey());
                m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "buy");
            });
            inv.setItem(slot, item);
            slot++;
        }
    }

    private ItemStack guiIcon(String oraxenId, Material fallback, String name, String... lore) {
        ItemStack item;
        try {
            ItemBuilder builder = OraxenItems.getItemById(oraxenId);
            item = builder == null ? new ItemStack(fallback) : builder.build();
        } catch (Exception e) {
            item = new ItemStack(fallback);
        }
        ItemMeta meta = item.getItemMeta();
        if (name != null && !name.equals(" ")) {
            meta.customName(MM.deserialize(name));
        }
        if (lore != null && lore.length > 0 && !lore[0].equals(" ")) {
            List<Component> lines = new java.util.ArrayList<>();
            for (String line : lore) {
                if (line != null && !line.equals(" ")) {
                    lines.add(MM.deserialize(line));
                }
            }
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack balanceItem(Player player) {
        // Use cached economy — no provider lookup per open
        Economy economy = cachedEconomy != null ? cachedEconomy : plugin.getEconomy();
        if (cachedEconomy == null) cachedEconomy = economy;
        int balance = economy == null ? 0 : (int) economy.getBalance(player);
        return guiIcon("gui_coin", Material.ECHO_SHARD,
                "<aqua><bold>" + balance + " Shards</bold></aqua>",
                "<gray>Your current balance.</gray>");
    }

    private ItemStack tabButton(String action, String iconId, Material fallback,
                                 String label, String activeName, String lore, boolean active) {
        ItemStack item = guiIcon(iconId, fallback,
                active ? activeName : "<gray>" + label + "</gray>", lore);
        item.editMeta(ItemMeta.class, m -> {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
            if (active) {
                m.addEnchant(Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    private ItemStack categoryTab(String category, boolean active) {
        String iconId = "gui_tab_" + category;
        String label;
        Material fallback;
        switch (category) {
            case "materials":
                label = "Materials";
                fallback = Material.REDSTONE;
                break;
            case "keys":
                label = "Keys";
                fallback = Material.TRIPWIRE_HOOK;
                break;
            case "alchemy":
                label = "Alchemy";
                fallback = Material.HONEY_BOTTLE;
                break;
            case "rifts":
                label = "Rifts";
                fallback = Material.AMETHYST_SHARD;
                break;
            default:
                label = "Gear";
                fallback = Material.DIAMOND_SWORD;
                break;
        }
        String name = (active ? "<dark_purple><bold>" : "<gray>") + label + (active ? "</bold>" : "");
        ItemStack item = guiIcon(iconId, fallback, name, "<gray>Browse " + label.toLowerCase() + ".</gray>");
        item.editMeta(ItemMeta.class, m -> {
            m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "tab");
            m.getPersistentDataContainer().set(CATEGORY_KEY, PersistentDataType.STRING, category);
            if (active) {
                m.addEnchant(Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    private ItemStack closeButton() {
        ItemStack item = guiIcon("gui_close", Material.BARRIER,
                "<red><bold>CLOSE</bold></red>",
                "<gray>Close the bazaar.</gray>");
        item.editMeta(ItemMeta.class, m ->
                m.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "close"));
        return item;
    }

    private void fillBorder(Inventory inv) {
        // Reuse static pane — clone per slot to prevent inventory mutation side effects
        for (int i = 0; i < SIZE; i++) {
            if (i < 18 || i >= 27) {
                inv.setItem(i, CACHED_BORDER_PANE.clone());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            handleTopClick(player, event.getRawSlot(), event.getClick(), session);
        } else if (session.sellMode()) {
            handleSellClick(player, event.getSlot(), event.getCurrentItem(), event.getClick());
        }
    }

    private void handleTopClick(Player player, int rawSlot, ClickType click, Session session) {
        if (rawSlot < 0 || rawSlot >= SIZE) return;
        ItemStack clicked = player.getOpenInventory().getTopInventory().getItem(rawSlot);
        if (clicked == null || !clicked.hasItemMeta()) return;

        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "tab_buy":
                open(player, session.category(), false);
                break;
            case "tab_sell":
                open(player, session.category(), true);
                break;
            case "close":
                player.closeInventory();
                break;
            case "tab":
                String category = clicked.getItemMeta().getPersistentDataContainer()
                        .get(CATEGORY_KEY, PersistentDataType.STRING);
                if (category != null) {
                    open(player, category, session.sellMode());
                }
                break;
            case "buy": {
                if (session.sellMode()) return;
                String itemId = clicked.getItemMeta().getPersistentDataContainer()
                        .get(ITEM_KEY, PersistentDataType.STRING);
                Integer price = shopManager.buyPrice(session.category(), itemId);
                if (itemId != null && price != null) {
                    buyItem(player, itemId, price, click.isShiftClick() ? buyStackSize() : 1, -1);
                }
                break;
            }
            case "buygear": {
                if (session.sellMode()) return;
                Integer gearSlot = clicked.getItemMeta().getPersistentDataContainer()
                        .get(GEAR_SLOT_KEY, PersistentDataType.INTEGER);
                if (gearSlot != null && gearSlot >= 0 && gearSlot < shopManager.getGearStock().size()) {
                    ShopManager.GearStockEntry entry = shopManager.getGearStock().get(gearSlot);
                    buyItem(player, null, entry.price(), 1, gearSlot);
                }
                break;
            }
            default:
                break;
        }
    }

    private void handleSellClick(Player player, int slot, ItemStack item, ClickType click) {
        if (item == null || item.getType().isAir()) return;
        Economy economy = cachedEconomy != null ? cachedEconomy : plugin.getEconomy();
        if (cachedEconomy == null) cachedEconomy = economy;
        if (economy == null) {
            message(player, "denied");
            sound(player, false);
            return;
        }
        Integer price = shopManager.sellPrice(item);
        if (price == null) {
            message(player, "no-value");
            sound(player, false);
            return;
        }
        int amount = click.isShiftClick() ? item.getAmount() : 1;
        if (amount <= 0) return;
        int total = price * amount;
        ItemStack snapshot = item.clone();
        snapshot.setAmount(amount);

        net.milkbowl.vault.economy.EconomyResponse depResp;
        try {
            depResp = economy.depositPlayer(player, total);
        } catch (Exception e) {
            plugin.getLogger().warning("Shop sell deposit failed for " + player.getName() + ": " + e.getMessage());
            message(player, "denied");
            sound(player, false);
            return;
        }
        if (depResp == null || !depResp.transactionSuccess()) {
            String err = depResp != null ? depResp.errorMessage : "null response";
            plugin.getLogger().warning("Shop sell deposit failed for " + player.getName() + ": " + err + " amount=" + total);
            message(player, "denied");
            sound(player, false);
            return;
        }

        int remaining = item.getAmount() - amount;
        if (remaining <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            ItemStack copy = item.clone();
            copy.setAmount(remaining);
            player.getInventory().setItem(slot, copy);
        }

        message(player, "sold", "{amount}", String.valueOf(amount),
                "{item}", plainName(snapshot), "{price}", String.valueOf(total));
        sound(player, true);
        refreshBalance(player);
    }

    private void buyItem(Player player, String itemId, int price, int amount, int gearSlot) {
        Economy economy = cachedEconomy != null ? cachedEconomy : plugin.getEconomy();
        if (cachedEconomy == null) cachedEconomy = economy;
        if (economy == null) {
            message(player, "denied");
            sound(player, false);
            return;
        }
        int total = price * amount;
        try {
            if (!economy.has(player, total)) {
                message(player, "not-enough-shards", "{price}", String.valueOf(total));
                sound(player, false);
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Shop buy has() check failed for " + player.getName() + ": " + e.getMessage());
            message(player, "denied");
            sound(player, false);
            return;
        }
        net.milkbowl.vault.economy.EconomyResponse withdrawResp;
        try {
            withdrawResp = economy.withdrawPlayer(player, total);
        } catch (Exception e) {
            plugin.getLogger().warning("Shop buy withdraw failed for " + player.getName() + ": " + e.getMessage());
            message(player, "denied");
            sound(player, false);
            return;
        }
        if (withdrawResp == null || !withdrawResp.transactionSuccess()) {
            String err = withdrawResp != null ? withdrawResp.errorMessage : "null response";
            plugin.getLogger().warning("Shop buy withdraw failed for " + player.getName() + ": " + err + " cost=" + total);
            message(player, "not-enough-shards", "{price}", String.valueOf(total));
            sound(player, false);
            return;
        }

        ItemStack bought;
        if (gearSlot >= 0) {
            if (gearSlot < 0 || gearSlot >= shopManager.getGearStock().size()) {
                plugin.getLogger().warning("Shop buy failed: gear slot " + gearSlot + " out of range for " + player.getName() + " — refunding " + total);
                refundDeposit(economy, player, total);
                message(player, "denied");
                sound(player, false);
                return;
            }
            ShopManager.GearStockEntry entry = shopManager.getGearStock().get(gearSlot);
            if (entry == null || entry.item() == null) {
                plugin.getLogger().warning("Shop buy failed: gear slot " + gearSlot + " empty for " + player.getName() + " — refunding " + total);
                refundDeposit(economy, player, total);
                message(player, "denied");
                sound(player, false);
                return;
            }
            bought = entry.item().clone();
        } else {
            if (itemId == null) {
                plugin.getLogger().warning("Shop buy failed: null itemId for " + player.getName() + " — refunding " + total);
                refundDeposit(economy, player, total);
                message(player, "denied");
                sound(player, false);
                return;
            }
            try {
                ItemBuilder builder = OraxenItems.getItemById(itemId);
                if (builder == null) {
                    plugin.getLogger().warning("Shop buy failed: unknown Oraxen item " + itemId + " for " + player.getName() + " — refunding " + total);
                    refundDeposit(economy, player, total);
                    message(player, "denied");
                    sound(player, false);
                    return;
                }
                bought = builder.build().clone();
                bought.setAmount(amount);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to build Oraxen item " + itemId + ": " + e.getMessage());
                refundDeposit(economy, player, total);
                message(player, "denied");
                sound(player, false);
                return;
            }
        }
        if (bought == null) {
            plugin.getLogger().warning("Shop buy failed: bought is null for " + player.getName() + " — refunding " + total);
            refundDeposit(economy, player, total);
            message(player, "denied");
            sound(player, false);
            return;
        }

        try {
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItem(player.getLocation(), bought);
                message(player, "full-inventory");
            } else {
                java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(bought);
                if (!leftover.isEmpty()) {
                    for (ItemStack left : leftover.values()) {
                        player.getWorld().dropItem(player.getLocation(), left);
                    }
                    message(player, "full-inventory");
                } else {
                    message(player, "bought", "{amount}", String.valueOf(amount),
                            "{item}", plainName(bought), "{price}", String.valueOf(total));
                }
            }
            sound(player, true);
            refreshBalance(player);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to give bought item to " + player.getName() + " — refunding " + total, e);
            refundDeposit(economy, player, total);
            try {
                player.getWorld().dropItem(player.getLocation(), bought);
                message(player, "full-inventory");
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to drop fallback item for " + player.getName() + ": " + ex.getMessage());
            }
            refreshBalance(player);
        }
    }

    private void refundDeposit(Economy economy, Player player, int amount) {
        try {
            net.milkbowl.vault.economy.EconomyResponse resp = economy.depositPlayer(player, amount);
            if (resp == null || !resp.transactionSuccess()) {
                String err = resp != null ? resp.errorMessage : "null response";
                plugin.getLogger().warning("CRITICAL: refund deposit failed for " + player.getName() + ": " + err + " amount=" + amount + " — shards may be lost, needs manual correction.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("CRITICAL: refund deposit exception for " + player.getName() + ": " + e.getMessage() + " amount=" + amount);
        }
    }

    private void refreshBalance(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        top.setItem(0, balanceItem(player));
    }

    private int buyStackSize() {
        return cachedBuyStackSize > 0 ? cachedBuyStackSize : shopManager.getBuyStackSize();
    }

    private String plainName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasCustomName()) {
            Component name = item.getItemMeta().customName();
            if (name != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(name);
                if (!plain.isEmpty()) {
                    return plain;
                }
            }
        }
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private void message(Player player, String key, String... replacements) {
        String template = shopManager.getMessageTemplate(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            template = template.replace(replacements[i], replacements[i + 1]);
        }
        player.sendMessage(MM.deserialize(template));
    }

    private void sound(Player player, boolean success) {
        player.playSound(player.getLocation(),
                success ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_VILLAGER_NO,
                1.0f, success ? 1.4f : 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (switchingGui.remove(player.getUniqueId())) return;
        sessions.remove(player.getUniqueId());
    }
}
