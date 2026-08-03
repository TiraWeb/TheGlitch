package com.theglitch.glitchshops;

import io.th0rgal.oraxen.api.OraxenItems;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShopGUI implements Listener {

    private static final int SIZE = 54;
    private static final List<String> TAB_ORDER = List.of("materials", "keys", "alchemy", "rifts", "gear");

    private static final NamespacedKey ACTION_KEY = new NamespacedKey("glitchshops", "action");
    private static final NamespacedKey CATEGORY_KEY = new NamespacedKey("glitchshops", "category");
    private static final NamespacedKey ITEM_KEY = new NamespacedKey("glitchshops", "item");
    private static final NamespacedKey GEAR_SLOT_KEY = new NamespacedKey("glitchshops", "gearslot");

    private static final Map<UUID, Session> sessions = new HashMap<>();

    private record Session(String category, boolean sellMode) {
    }

    private final GlitchShops plugin;
    private final ShopManager shopManager;

    public ShopGUI(GlitchShops plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public void open(Player player, String category) {
        open(player, category, false);
    }

    public void open(Player player, String category, boolean sellMode) {
        if (!TAB_ORDER.contains(category)) {
            category = plugin.getConfig().getString("default-tab", "materials");
        }
        String title = "<dark_purple><bold>✧ GRAND BAZAAR ✧</bold></dark_purple>";
        Inventory inv = Bukkit.createInventory(null, SIZE, MiniMessage.miniMessage().deserialize(title));

        fillBorder(inv);

        inv.setItem(0, logo());
        inv.setItem(1, balanceItem(player));
        inv.setItem(3, tabButton("tab_buy", "BUY", Material.EMERALD, !sellMode));
        inv.setItem(4, tabButton("tab_sell", "SELL", Material.GOLD_INGOT, sellMode));
        inv.setItem(7, closeButton());

        for (int i = 0; i < TAB_ORDER.size(); i++) {
            String tab = TAB_ORDER.get(i);
            inv.setItem(9 + i, categoryTab(tab, tab.equals(category)));
        }

        if (sellMode) {
            inv.setItem(22, MiniMessageItem.builder(Material.GOLD_BLOCK,
                    "<gold><bold>SELLING</bold></gold>",
                    "<gray>Click items in your inventory below.</gray>",
                    "<yellow>Left-click = 1 · Shift-click = stack</yellow>")
                    .tag(ACTION_KEY, "none")
                    .build());
        } else {
            fillStock(inv, player, category);
        }

        sessions.put(player.getUniqueId(), new Session(category, sellMode));
        player.openInventory(inv);
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
                lore.add(MiniMessage.miniMessage().deserialize(
                        entry.superRare() ? "<gold>SUPER RARE — max rolls</gold>" : ""));
                lore.add(Component.empty());
                lore.add(MiniMessage.miniMessage().deserialize("<aqua>Buy: " + entry.price() + " Shards</aqua>"));
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
                inv.setItem(22, MiniMessageItem.builder(Material.BARRIER,
                        "<red>Out of stock</red>",
                        "<gray>The vendor will restock soon.</gray>")
                        .tag(ACTION_KEY, "none")
                        .build());
            }
            return;
        }

        ShopManager.Shop shop = shopManager.getShop(category);
        if (shop == null) return;
        for (Map.Entry<String, ShopManager.StockEntry> entry : shop.stock().entrySet()) {
            if (slot >= 27) break;
            ItemStack item = OraxenItems.getItemById(entry.getKey()).build().clone();
            ItemMeta meta = item.getItemMeta();
            List<Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : meta.lore();
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<aqua>Buy: " + entry.getValue().buy() + " Shards</aqua>"));
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

    private ItemStack logo() {
        return MiniMessageItem.builder(Material.NETHER_STAR,
                "<dark_purple><bold>GRAND BAZAAR</bold></dark_purple>",
                "<gray>Trade custom items for Shards.</gray>")
                .tag(ACTION_KEY, "none")
                .build();
    }

    private ItemStack balanceItem(Player player) {
        Economy economy = plugin.getEconomy();
        int balance = economy == null ? 0 : (int) economy.getBalance(player);
        return MiniMessageItem.builder(Material.ECHO_SHARD,
                "<aqua><bold>" + balance + " Shards</bold></aqua>",
                "<gray>Your current balance.</gray>")
                .tag(ACTION_KEY, "none")
                .build();
    }

    private ItemStack tabButton(String action, String label, Material material, boolean active) {
        MiniMessageItem.Builder builder = MiniMessageItem.builder(material,
                (active ? "<green><bold>" : "<gray>") + label + (active ? "</bold>" : ""),
                "<gray>Click to " + (action.equals("buy") ? "buy" : "sell") + " items.</gray>")
                .tag(ACTION_KEY, action);
        if (active) {
            builder.glow();
        }
        return builder.build();
    }

    private ItemStack categoryTab(String category, boolean active) {
        String label;
        Material material;
        switch (category) {
            case "materials":
                label = "Materials";
                material = Material.REDSTONE;
                break;
            case "keys":
                label = "Keys";
                material = Material.TRIPWIRE_HOOK;
                break;
            case "alchemy":
                label = "Alchemy";
                material = Material.HONEY_BOTTLE;
                break;
            case "rifts":
                label = "Rifts";
                material = Material.AMETHYST_SHARD;
                break;
            default:
                label = "Gear";
                material = Material.DIAMOND_SWORD;
                break;
        }
        MiniMessageItem.Builder builder = MiniMessageItem.builder(material,
                (active ? "<dark_purple><bold>" : "<gray>") + label + (active ? "</bold>" : ""),
                "<gray>Browse " + label.toLowerCase() + ".</gray>")
                .tag(ACTION_KEY, "tab")
                .tag(CATEGORY_KEY, category);
        if (active) {
            builder.glow();
        }
        return builder.build();
    }

    private ItemStack closeButton() {
        return MiniMessageItem.builder(Material.BARRIER,
                "<red><bold>CLOSE</bold></red>",
                "<gray>Close the bazaar.</gray>")
                .tag(ACTION_KEY, "close")
                .build();
    }

    private void fillBorder(Inventory inv) {
        ItemStack border = MiniMessageItem.builder(Material.BLACK_STAINED_GLASS_PANE,
                " ", " ").tag(ACTION_KEY, "none").build();
        for (int i = 0; i < SIZE; i++) {
            if (i < 18 || i >= 27) {
                inv.setItem(i, border);
            }
        }
        for (int i : new int[]{2, 5, 6, 8}) {
            inv.setItem(i, border);
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
        Integer price = shopManager.sellPrice(item);
        if (price == null) {
            message(player, "no-value");
            sound(player, false);
            return;
        }
        int amount = click.isShiftClick() ? item.getAmount() : 1;
        int total = price * amount;

        int remaining = item.getAmount() - amount;
        if (remaining <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            ItemStack copy = item.clone();
            copy.setAmount(remaining);
            player.getInventory().setItem(slot, copy);
        }

        Economy economy = plugin.getEconomy();
        if (economy != null) {
            economy.depositPlayer(player, total);
        }

        message(player, "sold", "{amount}", String.valueOf(amount),
                "{item}", plainName(item), "{price}", String.valueOf(total));
        sound(player, true);
        refreshBalance(player);
    }

    private void buyItem(Player player, String itemId, int price, int amount, int gearSlot) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            message(player, "denied");
            return;
        }
        int total = price * amount;
        if (!economy.has(player, total)) {
            message(player, "not-enough-shards", "{price}", String.valueOf(total));
            sound(player, false);
            return;
        }
        economy.withdrawPlayer(player, total);

        ItemStack bought;
        if (gearSlot >= 0) {
            ShopManager.GearStockEntry entry = shopManager.getGearStock().get(gearSlot);
            bought = entry == null ? null : entry.item().clone();
        } else {
            bought = OraxenItems.getItemById(itemId).build().clone();
            bought.setAmount(amount);
        }
        if (bought == null) {
            economy.depositPlayer(player, total);
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), bought);
            message(player, "full-inventory");
        } else {
            player.getInventory().addItem(bought);
            message(player, "bought", "{amount}", String.valueOf(amount),
                    "{item}", plainName(bought), "{price}", String.valueOf(total));
        }
        sound(player, true);
        refreshBalance(player);
    }

    private void refreshBalance(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        top.setItem(1, balanceItem(player));
    }

    private int buyStackSize() {
        return plugin.getConfig().getInt("buy-stack-size", 64);
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
        String template = plugin.getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            template = template.replace(replacements[i], replacements[i + 1]);
        }
        player.sendMessage(MiniMessage.miniMessage().deserialize(template));
    }

    private void sound(Player player, boolean success) {
        player.playSound(player.getLocation(),
                success ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_VILLAGER_NO,
                1.0f, success ? 1.4f : 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sessions.remove(player.getUniqueId());
        }
    }

    private static final class MiniMessageItem {
        private MiniMessageItem() {
        }

        static Builder builder(Material material, String name, String... lore) {
            return new Builder(material, name, lore);
        }

        private static final class Builder {
            private final ItemStack item;
            private final ItemMeta meta;
            private final Map<NamespacedKey, String> tags = new LinkedHashMap<>();
            private boolean glow;

            Builder(Material material, String name, String... lore) {
                item = new ItemStack(material);
                meta = item.getItemMeta();
                if (name != null && !name.equals(" ")) {
                    meta.customName(MiniMessage.miniMessage().deserialize(name));
                }
                if (lore != null && lore.length > 0 && !lore[0].equals(" ")) {
                    List<Component> lines = new java.util.ArrayList<>();
                    for (String line : lore) {
                        lines.add(MiniMessage.miniMessage().deserialize(line));
                    }
                    meta.lore(lines);
                }
            }

            Builder tag(NamespacedKey key, String value) {
                tags.put(key, value);
                return this;
            }

            Builder glow() {
                this.glow = true;
                return this;
            }

            ItemStack build() {
                for (Map.Entry<NamespacedKey, String> entry : tags.entrySet()) {
                    meta.getPersistentDataContainer().set(entry.getKey(), PersistentDataType.STRING, entry.getValue());
                }
                if (glow) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
                return item;
            }
        }
    }
}
