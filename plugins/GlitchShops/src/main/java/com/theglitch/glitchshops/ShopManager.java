package com.theglitch.glitchshops;

import com.theglitch.glitchitems.GearRolls;
import com.theglitch.glitchitems.GlitchItems;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
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

    public record GearStockEntry(ItemStack item, int price, boolean superRare) {
    }

    private final GlitchShops plugin;
    private final Map<String, Shop> shops = new LinkedHashMap<>();
    private final Map<String, Integer> sellPrices = new HashMap<>();
    private final List<GearStockEntry> gearStock = new ArrayList<>();
    private int restockTaskId = -1;

    public ShopManager(GlitchShops plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (restockTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(restockTaskId);
            restockTaskId = -1;
        }
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

        restockGear();
        startRestockTimer();
    }

    private void startRestockTimer() {
        int minutes = plugin.getConfig().getInt("gear.restock-minutes", 10);
        long ticks = Math.max(minutes, 1) * 60L * 20L;
        restockTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::restockGear, ticks, ticks).getTaskId();
    }

    public void restockGear() {
        gearStock.clear();
        List<String> weapons = plugin.getConfig().getStringList("gear.weapon-slots");
        List<String> armor = plugin.getConfig().getStringList("gear.armor-slots");
        int maxSlots = plugin.getConfig().getInt("gear.max-slots", 5);
        double superRareChance = plugin.getConfig().getDouble("gear.super-rare-chance", 0.0001);
        double buyMultiplier = plugin.getConfig().getDouble("gear.buy-multiplier", 1.75);

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int slots = 0;
        for (String typeId : weapons) {
            if (slots >= maxSlots) break;
            gearStock.add(rollGearEntry(typeId, true, superRareChance, buyMultiplier, rand));
            slots++;
        }
        for (String typeId : armor) {
            if (slots >= maxSlots) break;
            gearStock.add(rollGearEntry(typeId, false, superRareChance, buyMultiplier, rand));
            slots++;
        }
        plugin.getLogger().info("Gear vendor restocked: " + gearStock.size() + " slots.");
    }

    private GearStockEntry rollGearEntry(String typeId, boolean weapon, double superRareChance,
                                         double buyMultiplier, ThreadLocalRandom rand) {
        com.theglitch.glitchitems.GearType type = com.theglitch.glitchitems.GearType.fromId(typeId);
        if (type == null) {
            return new GearStockEntry(null, 0, false);
        }
        boolean superRare = rand.nextDouble() < superRareChance;
        ItemStack item;
        if (superRare) {
            item = gearManager().generateGodroll(type);
        } else {
            com.theglitch.glitchitems.Rarity rarity = weightedRarity(rand);
            item = gearManager().generateGear(type, rarity);
        }
        int sellValue = gearManager().sellValue(gearRarity(item));
        int price = (int) Math.round(sellValue * buyMultiplier);
        return new GearStockEntry(item, price, superRare);
    }

    private com.theglitch.glitchitems.Rarity weightedRarity(ThreadLocalRandom rand) {
        ConfigurationSection weights = plugin.getConfig().getConfigurationSection("gear.rarity-weights");
        if (weights == null) {
            return com.theglitch.glitchitems.Rarity.COMMON;
        }
        List<String> names = weights.getKeys(false)
                .stream()
                .filter(name -> com.theglitch.glitchitems.Rarity.fromId(name) != null)
                .toList();
        int total = 0;
        for (String name : names) {
            total += plugin.getConfig().getInt("gear.rarity-weights." + name, 0);
        }
        if (total <= 0) return com.theglitch.glitchitems.Rarity.COMMON;
        int roll = rand.nextInt(total);
        for (String name : names) {
            roll -= plugin.getConfig().getInt("gear.rarity-weights." + name, 0);
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

    public Map<String, Shop> getShops() {
        return shops;
    }

    public List<GearStockEntry> getGearStock() {
        return gearStock;
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
        if (item == null || item.getType().isAir() || isAbilityItem(item)) {
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

    public String oraxenId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(ORAXEN_ID_KEY, PersistentDataType.STRING);
        if (id != null && !id.isEmpty()) return id;
        for (NamespacedKey key : pdc.getKeys()) {
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value != null && value.matches("[a-z_]+")) {
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

    public boolean isAbilityItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        for (NamespacedKey key : item.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (key.getKey().equals("class_ability")) {
                return true;
            }
        }
        return false;
    }
}
