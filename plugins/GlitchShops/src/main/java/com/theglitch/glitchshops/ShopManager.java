package com.theglitch.glitchshops;

import com.theglitch.glitchitems.GearRolls;
import com.theglitch.glitchitems.GlitchItems;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ShopManager {

    private static final NamespacedKey ORAXEN_ID_KEY = new NamespacedKey("oraxen", "custom_item_id");
    private static final NamespacedKey GEAR_KEY = new NamespacedKey("glitchitems", "gear");

    public record StockEntry(int buy, int sell) {
    }

    public record Shop(String id, String title, String tabIcon, LinkedHashMap<String, StockEntry> stock) {
    }

    public record GearStockEntry(String id, ItemStack item, int price, boolean superRare) {
    }

    private final GlitchShops plugin;
    private final Map<String, Shop> shops = new LinkedHashMap<>();
    private final Map<String, Integer> sellPrices = new HashMap<>();
    private final List<GearStockEntry> gearStock = new ArrayList<>();
    private long gearIdCounter = 0;
    private int restockTaskId = -1;

    // ---- Cached config (refreshed on reload, read without getConfig() on hot path) ----
    private volatile int restockMinutes = 10;
    private volatile List<String> weaponSlots = List.of();
    private volatile List<String> armorSlots = List.of();
    private volatile int maxSlots = 5;
    private volatile double superRareChance = 0.0001;
    private volatile double buyMultiplier = 1.75;
    private volatile Map<String, Integer> rarityWeights = new LinkedHashMap<>();
    private volatile List<String> rarityNames = List.of();
    private volatile int rarityTotalWeight = 0;
    private volatile List<String> tabOrder = List.of("materials", "keys", "alchemy", "rifts", "gear");
    private volatile String defaultTab = "materials";
    private volatile int buyStackSize = 64;
    private volatile Map<String, String> messageTemplates = new HashMap<>();
    private volatile Economy cachedEconomy;

    public ShopManager(GlitchShops plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (restockTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(restockTaskId);
            restockTaskId = -1;
        }
        // Invalidate cached economy so reload picks up new provider if changed
        cachedEconomy = null;

        File file = new File(plugin.getDataFolder(), "shops.yml");
        if (!file.exists()) {
            plugin.saveResource("shops.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        shops.clear();
        sellPrices.clear();

        ConfigurationSection shopsSection = config.getConfigurationSection("shops");
        if (shopsSection != null) {
            for (String id : shopsSection.getKeys(false)) {
                ConfigurationSection shopSection = shopsSection.getConfigurationSection(id);
                if (shopSection == null) continue;
                String title = shopSection.getString("title", id);
                String tabIcon = shopSection.getString("tab-icon", "");
                LinkedHashMap<String, StockEntry> stock = new LinkedHashMap<>();
                ConfigurationSection stockSection = shopSection.getConfigurationSection("stock");
                if (stockSection != null) {
                    for (String itemId : stockSection.getKeys(false)) {
                        int buy = stockSection.getInt(itemId + ".buy", 0);
                        int sell = stockSection.getInt(itemId + ".sell", 0);
                        stock.put(itemId, new StockEntry(buy, sell));
                        sellPrices.put(itemId, sell);
                    }
                }
                shops.put(id, new Shop(id, title, tabIcon, stock));
            }
        }

        cacheConfig();

        restockGear();
        startRestockTimer();
    }

    private void cacheConfig() {
        try {
            // Restock period
            int minutes = plugin.getConfig().getInt("gear.restock-minutes", 10);
            if (minutes < 1) {
                plugin.getLogger().warning("Invalid gear.restock-minutes " + minutes + " — clamped to 1.");
                minutes = 1;
            } else if (minutes > 1440) {
                plugin.getLogger().warning("Very large gear.restock-minutes " + minutes + " — check config.");
            }
            restockMinutes = minutes;

            // Weapon / armor slots (cached lists — no getStringList on hot path)
            weaponSlots = List.copyOf(plugin.getConfig().getStringList("gear.weapon-slots"));
            armorSlots = List.copyOf(plugin.getConfig().getStringList("gear.armor-slots"));

            int slots = plugin.getConfig().getInt("gear.max-slots", 5);
            if (slots < 1 || slots > 54) {
                plugin.getLogger().warning("Invalid gear.max-slots " + slots + " — clamped to 5.");
                slots = Math.max(1, Math.min(slots, 54));
            }
            maxSlots = slots;

            double sr = plugin.getConfig().getDouble("gear.super-rare-chance", 0.0001);
            if (sr < 0 || sr > 1) {
                plugin.getLogger().warning("Invalid gear.super-rare-chance " + sr + " — clamped to 0.0001.");
                sr = 0.0001;
            }
            superRareChance = sr;

            double mult = plugin.getConfig().getDouble("gear.buy-multiplier", 1.75);
            if (mult <= 0 || mult > 100) {
                plugin.getLogger().warning("Invalid gear.buy-multiplier " + mult + " — clamped to 1.75.");
                mult = 1.75;
            }
            buyMultiplier = mult;

            // Rarity weights — cache once, not per roll
            LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
            ConfigurationSection ws = plugin.getConfig().getConfigurationSection("gear.rarity-weights");
            int total = 0;
            if (ws != null) {
                for (String name : ws.getKeys(false)) {
                    if (com.theglitch.glitchitems.Rarity.fromId(name) == null) {
                        plugin.getLogger().warning("Unknown rarity in gear.rarity-weights: " + name + " — ignored.");
                        continue;
                    }
                    int w = ws.getInt(name, 0);
                    if (w < 0) {
                        plugin.getLogger().warning("Negative rarity weight for " + name + " — treated as 0.");
                        w = 0;
                    }
                    weights.put(name, w);
                    total += w;
                }
            }
            if (weights.isEmpty() || total <= 0) {
                plugin.getLogger().warning("Empty or zero rarity-weights — using default weights.");
                weights = new LinkedHashMap<>(Map.of("common", 5, "uncommon", 10, "rare", 12, "epic", 6, "legendary", 1));
                total = 34;
            }
            rarityWeights = weights;
            rarityNames = List.copyOf(weights.keySet());
            rarityTotalWeight = total;

            // Tab order & default tab
            List<String> cfgTabs = plugin.getConfig().getStringList("tab-order");
            if (cfgTabs != null && !cfgTabs.isEmpty()) {
                tabOrder = List.copyOf(cfgTabs);
            } else {
                tabOrder = List.of("materials", "keys", "alchemy", "rifts", "gear");
            }
            String def = plugin.getConfig().getString("default-tab", "materials");
            if (def == null || def.isBlank() || !tabOrder.contains(def)) {
                plugin.getLogger().warning("Invalid default-tab '" + def + "' — falling back to " + tabOrder.get(0));
                def = tabOrder.get(0);
            }
            defaultTab = def;

            // Buy stack size
            int bss = plugin.getConfig().getInt("buy-stack-size", 64);
            if (bss < 1 || bss > 64) {
                plugin.getLogger().warning("Invalid buy-stack-size " + bss + " — clamped to 64.");
                bss = Math.max(1, Math.min(bss, 64));
            }
            buyStackSize = bss;

            // Message templates (single hash lookup per transaction vs getConfig path traversal)
            Map<String, String> msgs = new HashMap<>();
            ConfigurationSection mSec = plugin.getConfig().getConfigurationSection("messages");
            if (mSec != null) {
                for (String k : mSec.getKeys(false)) {
                    String v = mSec.getString(k);
                    if (v != null) msgs.put(k, v);
                }
            }
            if (msgs.isEmpty()) {
                plugin.getLogger().warning("No messages configured — using defaults.");
                // Keep at least the keys we use so message() never returns null
                msgs.putIfAbsent("not-enough-shards", "<red>Not enough Shards — you need {price}.</red>");
                msgs.putIfAbsent("sold", "<green>Sold {amount}x {item} for {price} Shards.</green>");
                msgs.putIfAbsent("bought", "<green>Bought {amount}x {item} for {price} Shards.</green>");
                msgs.putIfAbsent("no-value", "<red>This item has no value here.</red>");
                msgs.putIfAbsent("full-inventory", "<yellow>Inventory full — the item dropped at your feet.</yellow>");
                msgs.putIfAbsent("denied", "<red>That item can't be traded.</red>");
            }
            messageTemplates = msgs;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to cache ShopManager config: " + e.getMessage());
        }
    }

    private void startRestockTimer() {
        long ticks = Math.max(restockMinutes, 1) * 60L * 20L;
        restockTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::restockGear, ticks, ticks).getTaskId();
    }

    public void restockGear() {
        gearStock.clear();
        // Use cached fields — no getConfig() polling
        List<String> weapons = weaponSlots;
        List<String> armor = armorSlots;
        int max = maxSlots;
        double srChance = superRareChance;
        double mult = buyMultiplier;

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int slots = 0;
        for (String typeId : weapons) {
            if (slots >= max) break;
            gearStock.add(rollGearEntry(typeId, true, srChance, mult, rand));
            slots++;
        }
        for (String typeId : armor) {
            if (slots >= max) break;
            gearStock.add(rollGearEntry(typeId, false, srChance, mult, rand));
            slots++;
        }
        plugin.getLogger().info("Gear vendor restocked: " + gearStock.size() + " slots.");
    }

    private GearStockEntry rollGearEntry(String typeId, boolean weapon, double superRareChance,
                                         double buyMultiplier, ThreadLocalRandom rand) {
        String id = typeId + "-" + (++gearIdCounter);
        com.theglitch.glitchitems.GearManager manager = gearManager();
        if (manager == null) {
            return new GearStockEntry(id, null, 0, false);
        }
        com.theglitch.glitchitems.GearType type = com.theglitch.glitchitems.GearType.fromId(typeId);
        if (type == null) {
            plugin.getLogger().warning("Unknown gear type in shop rotation: " + typeId);
            return new GearStockEntry(id, null, 0, false);
        }
        boolean superRare = rand.nextDouble() < superRareChance;
        ItemStack item;
        if (superRare) {
            item = manager.generateGodroll(type);
        } else {
            com.theglitch.glitchitems.Rarity rarity = weightedRarity(rand);
            item = manager.generateGear(type, rarity);
        }
        int sellValue = manager.sellValue(gearRarity(item));
        int price = (int) Math.round(sellValue * buyMultiplier);
        return new GearStockEntry(id, item, price, superRare);
    }

    private com.theglitch.glitchitems.Rarity weightedRarity(ThreadLocalRandom rand) {
        if (rarityNames.isEmpty() || rarityTotalWeight <= 0) {
            return com.theglitch.glitchitems.Rarity.COMMON;
        }
        int roll = rand.nextInt(rarityTotalWeight);
        for (String name : rarityNames) {
            int w = rarityWeights.getOrDefault(name, 0);
            roll -= w;
            if (roll < 0) {
                return com.theglitch.glitchitems.Rarity.fromId(name);
            }
        }
        return com.theglitch.glitchitems.Rarity.COMMON;
    }

    private com.theglitch.glitchitems.Rarity gearRarity(ItemStack item) {
        GearRolls rolls = gearRolls(item);
        return rolls == null ? null : rolls.rarity;
    }

    private com.theglitch.glitchitems.GearManager gearManager() {
        GlitchItems glitchItems = GlitchItems.getInstance();
        return glitchItems == null ? null : glitchItems.getGearManager();
    }

    public List<GearStockEntry> getGearStock() {        return gearStock;
    }

    public GearStockEntry gearStockById(String id) {
        if (id == null || id.isBlank()) return null;
        for (GearStockEntry entry : gearStock) {
            if (entry.id() != null && entry.id().equals(id)) return entry;
        }
        return null;
    }

    public Shop getShop(String id) {
        return shops.get(id);
    }

    public Integer buyPrice(String shopId, String itemId) {
        Shop shop = shops.get(shopId);
        if (shop == null) return null;
        StockEntry entry = shop.stock().get(itemId);
        return entry == null ? null : entry.buy();
    }

    public Integer sellPrice(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        String id = oraxenId(item);
        if (id != null && sellPrices.containsKey(id)) {
            return sellPrices.get(id);
        }
        GearRolls rolls = gearRolls(item);
        if (rolls != null && rolls.rarity != null && gearManager() != null) {
            return gearManager().sellValue(rolls.rarity);
        }
        return null;
    }

    private static boolean isIdShaped(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '_' && (c < 'a' || c > 'z')) return false;
        }
        return true;
    }

    public String oraxenId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(ORAXEN_ID_KEY, PersistentDataType.STRING);
        if (id != null && !id.isEmpty()) return id;
        for (NamespacedKey key : pdc.getKeys()) {
            String value = pdc.get(key, PersistentDataType.STRING);
            if (isIdShaped(value)) {
                return value;
            }
        }
        return null;
    }

    public GearRolls gearRolls(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String data = item.getItemMeta().getPersistentDataContainer().get(GEAR_KEY, PersistentDataType.STRING);
        return data == null ? null : GearRolls.deserialize(data);
    }

    // ---- Cached getters ----
    public List<String> getTabOrder() {
        return tabOrder;
    }

    public String getDefaultTab() {
        return defaultTab;
    }

    public int getBuyStackSize() {
        return buyStackSize;
    }

    public String getMessageTemplate(String key) {
        String t = messageTemplates.get(key);
        return t != null ? t : key;
    }
}
