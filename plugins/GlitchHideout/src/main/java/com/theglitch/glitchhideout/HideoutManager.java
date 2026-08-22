package com.theglitch.glitchhideout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Hideout progression (design GAME_DESIGN.md §4): station levels, shard
 * payments, prerequisites, extended stash / armory storage, med station,
 * and the workbench crafting recipes (ITEM_SYSTEM.md §7).
 * Player data persists per-player under plugins/GlitchHideout/players/.
 * <p>
 * Persistence is async + atomic: savePlayer builds a YamlConfiguration snapshot
 * on the calling thread then schedules an async task that writes to a temp file
 * and atomically moves it. saveAll() performs synchronous atomic writes for
 * all dirty/remaining entries (union of levels/stash/armory) to guarantee flush
 * on disable.
 */
public final class HideoutManager {

    private static final NamespacedKey ORAXEN_KEY = new NamespacedKey("oraxen", "custom_item_id");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public record Station(String id, String display, String icon, String description,
                          int[] costs, Map<Integer, String> requires) {
        int maxLevel() {
            return costs.length;
        }
    }

    public record Recipe(String id, String display, String icon, String output,
                          Map<String, Integer> materials) {
    }

    public enum UpgradeResult {
        OK, MAXED, PREREQ, SHARDS
    }

    private final GlitchHideout plugin;
    private volatile Map<String, Station> stations = new LinkedHashMap<>();
    private volatile Map<String, Recipe> recipes = new LinkedHashMap<>();

    private final Map<UUID, Map<String, Integer>> levels = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> stash = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> armory = new ConcurrentHashMap<>();
    private final Map<UUID, Long> medCooldown = new ConcurrentHashMap<>();
    private final Path dataDir;
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    // Cached economy — invalidated on reload
    private volatile Economy cachedEconomy;

    public HideoutManager(GlitchHideout plugin) {
        this.plugin = plugin;
        this.dataDir = plugin.getDataFolder().toPath().resolve("players");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create players directory", e);
        }
        reload();
    }

    public void reload() {
        // Invalidate cached economy
        cachedEconomy = null;
        stations = loadStations();
        recipes = loadRecipes();
        plugin.getLogger().info("Hideout stations loaded: " + stations.size()
                + ", recipes loaded: " + recipes.size());
    }

    public void invalidateEconomy() {
        cachedEconomy = null;
    }

    private Map<String, Station> loadStations() {
        Map<String, Station> loaded = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("stations");
        if (section == null) {
            plugin.getLogger().warning("No stations section in config — hideout will have no upgrades.");
            return loaded;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;
            List<Integer> costs = s.getIntegerList("costs");
            if (costs.isEmpty()) {
                plugin.getLogger().warning("Station '" + id + "' has no costs — skipped.");
                continue;
            }
            for (int c : costs) {
                if (c < 0) plugin.getLogger().warning("Station '" + id + "' negative cost " + c + " — will allow but check config.");
            }
            int[] costArray = costs.stream().mapToInt(Integer::intValue).toArray();
            Map<Integer, String> requires = new LinkedHashMap<>();
            ConfigurationSection req = s.getConfigurationSection("requires");
            if (req != null) {
                for (String level : req.getKeys(false)) {
                    try {
                        int lvl = Integer.parseInt(level);
                        String val = req.getString(level, "");
                        requires.put(lvl, val);
                        if (val != null && !val.isEmpty() && !val.contains(":")) {
                            plugin.getLogger().warning("Station '" + id + "' prerequisite '" + val + "' at level " + lvl + " missing ':' — format should be station:level");
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Station '" + id + "' invalid prerequisite level key '" + level + "' — ignored.");
                    }
                }
            }
            loaded.put(id, new Station(id,
                    s.getString("display", id),
                    s.getString("icon", "STONE"),
                    s.getString("description", ""),
                    costArray, requires));
        }
        return loaded;
    }

    private Map<String, Recipe> loadRecipes() {
        Map<String, Recipe> loaded = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("recipes");
        if (section == null) return loaded;
        for (String id : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) continue;
            Map<String, Integer> materials = new LinkedHashMap<>();
            ConfigurationSection mats = r.getConfigurationSection("materials");
            if (mats != null) {
                for (String mat : mats.getKeys(false)) {
                    int amt = Math.max(1, mats.getInt(mat));
                    materials.put(mat, amt);
                }
            }
            if (materials.isEmpty()) {
                plugin.getLogger().warning("Recipe '" + id + "' has no materials — will be uncraftable.");
            }
            loaded.put(id, new Recipe(id,
                    r.getString("display", id),
                    r.getString("icon", "STONE"),
                    r.getString("output", ""),
                    materials));
        }
        return loaded;
    }

    public Station getStation(String id) {
        return id == null ? null : stations.get(id);
    }

    public List<Station> getStations() {
        return new ArrayList<>(stations.values());
    }

    public List<Recipe> getRecipes() {
        return new ArrayList<>(recipes.values());
    }

    public Recipe getRecipe(String id) {
        return id == null ? null : recipes.get(id);
    }

    public int getLevel(UUID uuid, String stationId) {
        return levels.computeIfAbsent(uuid, this::loadPlayer)
                .getOrDefault(stationId, 0);
    }

    public void setLevel(UUID uuid, String stationId, int level) {
        Map<String, Integer> playerLevels = levels.computeIfAbsent(uuid, this::loadPlayer);
        playerLevels.put(stationId, Math.max(0, level));
        savePlayer(uuid);
    }

    public int intelLevel(UUID uuid) {
        return getLevel(uuid, "intel");
    }

    public int stashSlots(UUID uuid) {
        int level = getLevel(uuid, "stash");
        if (level >= 3) return 54;
        if (level >= 2) return 45;
        if (level >= 1) return 27;
        return 0;
    }

    public int armorySlots(UUID uuid) {
        int level = getLevel(uuid, "armory");
        if (level >= 2) return 45;
        if (level >= 1) return 27;
        return 0;
    }

    public UpgradeResult upgrade(Player player, Station station) {
        UUID uuid = player.getUniqueId();
        int current = getLevel(uuid, station.id());
        if (current >= station.maxLevel()) return UpgradeResult.MAXED;

        int next = current + 1;
        String req = station.requires().get(next);
        if (req != null && !req.isEmpty()) {
            String[] parts = req.split(":");
            if (parts.length == 2) {
                try {
                    if (getLevel(uuid, parts[0]) < Integer.parseInt(parts[1])) {
                        return UpgradeResult.PREREQ;
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Bad prerequisite '" + req + "' for station " + station.id());
                    return UpgradeResult.PREREQ;
                }
            }
        }

        int cost = station.costs()[current];
        Economy economy = economy();
        if (economy == null) {
            plugin.getLogger().warning("Vault economy unavailable — blocking hideout upgrade for " + player.getName());
            player.sendMessage(Component.text("Economy unavailable — try again later.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return UpgradeResult.SHARDS;
        }
        if (!economy.has(player, cost)) return UpgradeResult.SHARDS;
        net.milkbowl.vault.economy.EconomyResponse resp = economy.withdrawPlayer(player, cost);
        if (!resp.transactionSuccess()) {
            plugin.getLogger().warning("Hideout withdraw failed for " + player.getName() + ": " + resp.errorMessage);
            return UpgradeResult.SHARDS;
        }

        setLevel(uuid, station.id(), next);
        return UpgradeResult.OK;
    }

    public void medHeal(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        int cooldown = plugin.getMedCooldownSeconds() * 1000;

        Long last = medCooldown.get(uuid);
        if (last != null && now - last < cooldown) {
            player.sendMessage(plugin.getComponent("med-cooldown",
                    "<seconds>", String.valueOf((cooldown - (now - last)) / 1000)));
            return;
        }
        medCooldown.put(uuid, now);

        double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        player.setHealth(max);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.POISON);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.WITHER);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.HUNGER);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE);
        player.sendMessage(plugin.getComponent("med-heal"));
    }

    public Component craft(Player player, Recipe recipe) {
        if (getLevel(player.getUniqueId(), "workbench") < 1) {
            return plugin.getComponent("craft-locked");
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : recipe.materials().entrySet()) {
            if (countItems(player, entry.getKey()) < entry.getValue()) {
                missing.add(entry.getKey() + " x" + entry.getValue());
            }
        }
        if (!missing.isEmpty()) {
            return plugin.getComponent("craft-missing",
                    "<missing>", String.join(", ", missing));
        }
        for (Map.Entry<String, Integer> entry : recipe.materials().entrySet()) {
            consumeItems(player, entry.getKey(), entry.getValue());
        }
        String command = recipe.output().replace("<player>", player.getName());
        boolean dispatched = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
        if (!dispatched) {
            plugin.getLogger().warning("Hideout craft: could not dispatch '" + command + "'");
        }
        player.sendMessage(plugin.getComponent("crafted", "<output>", recipe.display()));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        return null;
    }

    private int countItems(Player player, String id) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && isItem(stack, id)) {
                count += stack.getAmount();
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && isItem(offhand, id)) {
            count += offhand.getAmount();
        }
        return count;
    }

    private void consumeItems(Player player, String id, int amount) {
        for (int i = 0; i < player.getInventory().getSize() && amount > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || !isItem(stack, id)) continue;
            int take = Math.min(amount, stack.getAmount());
            if (stack.getAmount() > take) {
                stack.setAmount(stack.getAmount() - take);
            } else {
                player.getInventory().setItem(i, null);
            }
            amount -= take;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (amount > 0 && offhand != null && isItem(offhand, id)) {
            int take = Math.min(amount, offhand.getAmount());
            if (offhand.getAmount() > take) {
                offhand.setAmount(offhand.getAmount() - take);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }

    /**
     * Local mirror of OraxenUtil.isIdShaped — avoids cross-plugin dependency
     * and the regex cost of {@code value.matches("[a-z_]+")}.
     */
    private static boolean isIdShaped(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '_' && (c < 'a' || c > 'z')) return false;
        }
        return true;
    }

    private String oraxenIdOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        org.bukkit.persistence.PersistentDataContainer pdc =
                stack.getItemMeta().getPersistentDataContainer();
        String pdcId = pdc.get(ORAXEN_KEY, PersistentDataType.STRING);
        if (pdcId != null && !pdcId.isEmpty()) return pdcId;
        for (NamespacedKey key : pdc.getKeys()) {
            String value = pdc.get(key, PersistentDataType.STRING);
            if (isIdShaped(value)) return value;
        }
        return null;
    }

    private boolean isItem(ItemStack stack, String id) {
        String found = oraxenIdOf(stack);
        return found != null && id.equalsIgnoreCase(found);
    }

    public List<ItemStack> getStash(UUID uuid) {
        List<ItemStack> items = stash.get(uuid);
        if (items == null) {
            loadPlayer(uuid);
            items = stash.get(uuid);
        }
        return items != null ? items : new ArrayList<>();
    }

    public List<ItemStack> getArmory(UUID uuid) {
        List<ItemStack> items = armory.get(uuid);
        if (items == null) {
            loadPlayer(uuid);
            items = armory.get(uuid);
        }
        return items != null ? items : new ArrayList<>();
    }

    public void saveStorage(UUID uuid) {
        savePlayer(uuid);
    }

    public void sortArmory(UUID uuid) {
        List<ItemStack> items = getArmory(uuid);
        items.sort((a, b) -> {
            if (a == null) return 1;
            if (b == null) return -1;
            return a.getType().name().compareTo(b.getType().name());
        });
        savePlayer(uuid);
    }

    public void resetPlayer(UUID uuid) {
        levels.remove(uuid);
        stash.remove(uuid);
        armory.remove(uuid);
        medCooldown.remove(uuid);
        dirty.remove(uuid);
        File file = dataDir.resolve(uuid + ".yml").toFile();
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete player data for " + uuid);
        }
    }

    private Map<String, Integer> loadPlayer(UUID uuid) {
        Map<String, Integer> playerLevels = new ConcurrentHashMap<>();
        List<ItemStack> loadedStash = new ArrayList<>();
        List<ItemStack> loadedArmory = new ArrayList<>();

        File file = dataDir.resolve(uuid + ".yml").toFile();
        if (file.exists()) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection stationSection = yaml.getConfigurationSection("stations");
                if (stationSection != null) {
                    for (String key : stationSection.getKeys(false)) {
                        playerLevels.put(key, Math.max(0, stationSection.getInt(key)));
                    }
                }
                List<?> stashList = yaml.getList("stash");
                if (stashList != null) {
                    for (Object o : stashList) {
                        if (o instanceof ItemStack stack && stack.getType() != org.bukkit.Material.AIR) {
                            loadedStash.add(stack);
                        }
                    }
                }
                List<?> armoryList = yaml.getList("armory");
                if (armoryList != null) {
                    for (Object o : armoryList) {
                        if (o instanceof ItemStack stack && stack.getType() != org.bukkit.Material.AIR) {
                            loadedArmory.add(stack);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load hideout data for " + uuid, e);
            }
        }
        stash.put(uuid, loadedStash);
        armory.put(uuid, loadedArmory);
        levels.put(uuid, playerLevels);
        return playerLevels;
    }

    private void savePlayer(UUID uuid) {
        if (!levels.containsKey(uuid) && !stash.containsKey(uuid) && !armory.containsKey(uuid)) {
            return;
        }
        Map<String, Integer> playerLevels = levels.get(uuid);
        if (playerLevels == null) {
            playerLevels = loadPlayer(uuid);
        }

        // Snapshot to avoid concurrent modification during async write
        Map<String, Integer> levelsSnapshot = new LinkedHashMap<>(playerLevels);
        List<ItemStack> stashSnapshot = stash.get(uuid) == null ? null : new ArrayList<>(stash.get(uuid));
        List<ItemStack> armorySnapshot = armory.get(uuid) == null ? null : new ArrayList<>(armory.get(uuid));

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", uuid.toString());
        for (Map.Entry<String, Integer> entry : levelsSnapshot.entrySet()) {
            yaml.set("stations." + entry.getKey(), entry.getValue());
        }
        if (stashSnapshot != null) {
            yaml.set("stash", stashSnapshot.stream()
                    .filter(s -> s != null && s.getType() != org.bukkit.Material.AIR).toList());
        }
        if (armorySnapshot != null) {
            yaml.set("armory", armorySnapshot.stream()
                    .filter(s -> s != null && s.getType() != org.bukkit.Material.AIR).toList());
        }

        Path file = dataDir.resolve(uuid + ".yml");
        dirty.add(uuid);
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                try {
                    atomicSave(yaml, file);
                } finally {
                    dirty.remove(uuid);
                }
            });
        } catch (Throwable t) {
            try {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        atomicSave(yaml, file);
                    } finally {
                        dirty.remove(uuid);
                    }
                });
            } catch (Throwable t2) {
                try {
                    atomicSave(yaml, file);
                } finally {
                    dirty.remove(uuid);
                }
                plugin.getLogger().log(Level.WARNING, "Async scheduler unavailable, saved synchronously for " + uuid, t2);
            }
        }
    }

    /**
     * Synchronous variant — used by saveAll/shutdown to guarantee flush.
     */
    private void savePlayerSync(UUID uuid) {
        if (!levels.containsKey(uuid) && !stash.containsKey(uuid) && !armory.containsKey(uuid)) {
            return;
        }
        Map<String, Integer> playerLevels = levels.get(uuid);
        if (playerLevels == null) {
            playerLevels = loadPlayer(uuid);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", uuid.toString());
        for (Map.Entry<String, Integer> entry : playerLevels.entrySet()) {
            yaml.set("stations." + entry.getKey(), entry.getValue());
        }
        List<ItemStack> playerStash = stash.get(uuid);
        if (playerStash != null) {
            yaml.set("stash", playerStash.stream()
                    .filter(s -> s != null && s.getType() != org.bukkit.Material.AIR).toList());
        }
        List<ItemStack> playerArmory = armory.get(uuid);
        if (playerArmory != null) {
            yaml.set("armory", playerArmory.stream()
                    .filter(s -> s != null && s.getType() != org.bukkit.Material.AIR).toList());
        }
        Path file = dataDir.resolve(uuid + ".yml");
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, uuid.toString() + "-", ".tmp");
            try {
                yaml.save(tmp.toFile());
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save hideout data for " + uuid, e);
        }
    }

    /**
     * Static utility for atomic YAML persistence.
     * Writes to a temp file in the same directory then atomically moves to target.
     * Falls back to non-atomic move if ATOMIC_MOVE is unsupported.
     */
    static void atomicSave(YamlConfiguration yaml, Path target) {
        atomicSave(yaml, target, Bukkit.getLogger());
    }

    static void atomicSave(YamlConfiguration yaml, Path target, java.util.logging.Logger logger) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, target.getFileName().toString() + "-", ".tmp");
            try {
                yaml.save(tmp.toFile());
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to atomically save " + target, e);
        }
    }

    public void saveAll() {
        Set<UUID> all = new HashSet<>();
        all.addAll(levels.keySet());
        all.addAll(stash.keySet());
        all.addAll(armory.keySet());
        // Also include dirty entries that may have been removed from maps but still pending
        all.addAll(dirty);
        for (UUID uuid : all) {
            savePlayerSync(uuid);
        }
        dirty.clear();
    }

    public void shutdown() {
        saveAll();
    }

    private Economy economy() {
        if (cachedEconomy != null) return cachedEconomy;
        // Delegate to plugin's cached economy — single provider lookup, invalidated on reload
        Economy e = plugin.getEconomy();
        cachedEconomy = e;
        return e;
    }
}
