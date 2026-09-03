package com.theglitch.glitchitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-world loot containers (design GAME_DESIGN.md §3, ITEM_SYSTEM.md §9):
 * Debris Pile (free), Loot Cache (Cache Key), Vault (Vault Key),
 * Rift Vault (Rift Key).
 */
public final class ContainerManager {

    private static final NamespacedKey ORAXEN_KEY = new NamespacedKey("oraxen", "custom_item_id");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Must match AbilityListener.SCAVENGE_TAG — scoreboard tag that grants bonus rolls. */
    public static final String SCAVENGE_TAG = "specter_scavenge";

    // Cached GlitchRaid bridge reflection — avoids per-open getMethod scans on the loot path.
    // Keyed by runtime class so plugin reloads (new classloaders) re-resolve instead of reusing stale Methods.
    private static final Map<Class<?>, java.lang.reflect.Method> RAID_GET_MANAGER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Method> RAID_IS_IN_RAID_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Method> RAID_ADD_LOOT_FROM_ITEMS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<Class<?>> RAID_ADD_LOOT_FROM_ITEMS_MISSING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, java.lang.reflect.Method> RAID_ADD_LOOT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static java.lang.reflect.Method cachedRaidMethod(Object target, String name, int slot)
            throws NoSuchMethodException {
        Class<?> clazz = target.getClass();
        switch (slot) {
            case 0: {
                java.lang.reflect.Method cached = RAID_GET_MANAGER_CACHE.get(clazz);
                if (cached == null) {
                    cached = clazz.getMethod(name);
                    RAID_GET_MANAGER_CACHE.put(clazz, cached);
                }
                return cached;
            }
            case 1: {
                java.lang.reflect.Method cached = RAID_IS_IN_RAID_CACHE.get(clazz);
                if (cached == null) {
                    cached = clazz.getMethod(name, java.util.UUID.class);
                    RAID_IS_IN_RAID_CACHE.put(clazz, cached);
                }
                return cached;
            }
            case 2: {
                if (RAID_ADD_LOOT_FROM_ITEMS_MISSING.contains(clazz)) {
                    throw new NoSuchMethodException(name);
                }
                java.lang.reflect.Method cached = RAID_ADD_LOOT_FROM_ITEMS_CACHE.get(clazz);
                if (cached == null) {
                    try {
                        cached = clazz.getMethod(name, Player.class, java.util.Collection.class);
                    } catch (NoSuchMethodException e) {
                        RAID_ADD_LOOT_FROM_ITEMS_MISSING.add(clazz);
                        throw e;
                    }
                    RAID_ADD_LOOT_FROM_ITEMS_CACHE.put(clazz, cached);
                }
                return cached;
            }
            default: {
                java.lang.reflect.Method cached = RAID_ADD_LOOT_CACHE.get(clazz);
                if (cached == null) {
                    cached = clazz.getMethod(name, java.util.UUID.class, int.class);
                    RAID_ADD_LOOT_CACHE.put(clazz, cached);
                }
                return cached;
            }
        }
    }

    private static final Map<String, Material> MATERIAL_MATERIALS = Map.of(
            "rune_fragment", Material.PAPER,
            "aether_shard", Material.GLOWSTONE_DUST,
            "rift_crystal", Material.AMETHYST_SHARD,
            "void_essence", Material.BLACK_DYE,
            "legendary_relic", Material.NETHER_STAR);

    public record ContainerType(
            String name,
            String display,
            Material material,
            String keyId,
            String keyMaterial,
            Material keyMaterialResolved,
            String keyName,
            String keyNameLower,
            long regenSeconds,
            int maxRolls,
            Map<Rarity, Integer> rarityWeights,
            int nothingWeight,
            Map<String, Integer> materialWeights,
            int shardsMin,
            int shardsMax) {

        boolean requiresKey() {
            return keyId != null && !keyId.isEmpty();
        }
    }

    private final GlitchItems plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey lastKey;
    private volatile Map<String, ContainerType> types = new HashMap<>();

    // Cached config
    private volatile Set<String> enabledWorlds = Set.of("glitch_red", "glitch_pve");
    private volatile int scavengeBonusRolls = 1;
    private volatile Map<String, String> messagesRaw = new HashMap<>();

    public ContainerManager(GlitchItems plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "glitch_container");
        this.lastKey = new NamespacedKey(plugin, "glitch_container_last");
        reload();
    }

    public void reload() {
        Map<String, ContainerType> loaded = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("containers.types");
        if (section == null) {
            plugin.getLogger().warning("No 'containers.types' section in config.yml — "
                    + "container types are empty. Restore the default config or add the section.");
        } else {
            for (String name : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(name);
                if (t == null) continue;
                Material material = Material.matchMaterial(t.getString("material", "CHEST"));
                if (material == null) material = Material.CHEST;

                Map<Rarity, Integer> rarityWeights = new LinkedHashMap<>();
                int nothingWeight = 0;
                ConfigurationSection drops = t.getConfigurationSection("drops");
                if (drops != null) {
                    for (String rarityId : drops.getKeys(false)) {
                        if (rarityId.equals("nothing")) {
                            nothingWeight = Math.max(0, drops.getInt("nothing"));
                            continue;
                        }
                        Rarity rarity = Rarity.fromId(rarityId);
                        if (rarity != null) {
                            rarityWeights.put(rarity, Math.max(0, drops.getInt(rarityId)));
                        }
                    }
                }
                Map<String, Integer> materialWeights = new HashMap<>();
                ConfigurationSection materials = t.getConfigurationSection("materials");
                if (materials != null) {
                    for (String id : materials.getKeys(false)) {
                        materialWeights.put(id, Math.max(0, materials.getInt(id)));
                    }
                }
                String keyId = t.getString("key-id", "");
                String keyMatStr = t.getString("key-material", "");
                Material keyMatResolved = null;
                if (!keyMatStr.isEmpty()) {
                    try {
                        keyMatResolved = Material.valueOf(keyMatStr.toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown key-material '" + keyMatStr + "' for container " + name);
                    }
                }
                String keyName = t.getString("key-name", "");
                String keyNameLower = keyName.toLowerCase(java.util.Locale.ROOT);

                loaded.put(name, new ContainerType(
                        name,
                        t.getString("display", name),
                        material,
                        keyId,
                        keyMatStr,
                        keyMatResolved,
                        keyName,
                        keyNameLower,
                        t.getLong("regen-seconds", 600),
                        Math.max(1, t.getInt("max-rolls", 3)),
                        rarityWeights,
                        nothingWeight,
                        materialWeights,
                        t.getInt("shards-min", 0),
                        t.getInt("shards-max", 0)));
            }
        }
        types = loaded;
        enabledWorlds = Set.copyOf(plugin.getConfig().getStringList("containers.enabled-worlds"));
        if (enabledWorlds.isEmpty()) enabledWorlds = Set.of("glitch_red", "glitch_pve");
        scavengeBonusRolls = plugin.getConfig().getInt("containers.scavenge-bonus-rolls", 1);
        Map<String, String> msgs = new HashMap<>();
        ConfigurationSection msgSec = plugin.getConfig().getConfigurationSection("containers.messages");
        if (msgSec != null) {
            for (String k : msgSec.getKeys(false)) msgs.put(k, msgSec.getString(k, "<gray>" + k + "</gray>"));
        }
        messagesRaw = msgs;
        plugin.getLogger().info("Containers loaded: " + loaded.size() + " types");
    }

    public List<ContainerType> getTypes() {
        return new ArrayList<>(types.values());
    }

    public ContainerType typeOf(Block block) {
        if (block == null) return null;
        PersistentDataContainer data = data(block);
        if (data == null) return null;
        String name = data.get(typeKey, PersistentDataType.STRING);
        return name == null ? null : types.get(name);
    }

    public ContainerType getType(String name) {
        return name == null ? null : types.get(name);
    }

    public boolean isContainer(Block block) {
        return typeOf(block) != null;
    }

    public boolean mark(Block block, ContainerType type) {
        Material previous = block.getType();
        block.setType(type.material());
        BlockState state = block.getState();
        if (!(state instanceof PersistentDataHolder holder)) {
            block.setType(previous);
            return false;
        }
        holder.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        return state.update(true, false);
    }

    public void clear(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof PersistentDataHolder holder)) return;
        PersistentDataContainer data = holder.getPersistentDataContainer();
        data.remove(typeKey);
        data.remove(lastKey);
        state.update(true, false);
    }

    // ---- Scatter bridge (for GlitchStash AutoExtractScheduler reflection) ----
    // GlitchStash probes GlitchItems#getContainerManager() for scatter entry points.
    // These aliases delegate to ScatterManager so extracted cycles can trigger
    // scatter even if the event hook is not yet wired.
    public void scatter() { try { GlitchItems.getInstance().getScatterManager().scatterNow(); } catch (Exception ignored) {} }
    public void resetContainers() { scatter(); }
    public void onCycleEnd() { scatter(); }
    public void handleCycleEnd() { scatter(); }
    public void doScatter() { scatter(); }

    public boolean open(Player player, Block block) {
        ContainerType type = typeOf(block);
        if (type == null) {
            player.sendMessage(msg("not-container"));
            return false;
        }
        if (!enabledWorlds.contains(block.getWorld().getName())) {
            player.sendMessage(msg("disabled-world"));
            return false;
        }

        PersistentDataContainer data = data(block);
        if (data == null) {
            player.sendMessage(msg("not-container"));
            return false;
        }
        long last = data.getOrDefault(lastKey, PersistentDataType.LONG, 0L);
        long now = System.currentTimeMillis();
        long remaining = last + type.regenSeconds() * 1000L - now;
        if (remaining > 0) {
            player.sendMessage(msg("not-ready", "<container>", type.display(),
                    "<time>", String.valueOf((remaining + 999L) / 1000L)));
            return false;
        }

        if (type.requiresKey() && !hasKey(player, type)) {
            player.sendMessage(msg("need-key", "<container>", type.display(), "<key>", keyDisplayName(type)));
            return false;
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int luck = plugin.getGlitchManager().lootLuckBonus(player);

        List<ItemStack> loot = new ArrayList<>();
        boolean surged = false;

        int rolls = type.maxRolls();
        if (player.getScoreboardTags().contains(SCAVENGE_TAG)) {
            rolls += scavengeBonusRolls;
        }
        for (int i = 0; i < rolls; i++) {
            Rarity rarity = rollRarity(type, rand);
            if (rarity == null) continue;
            if (luck > 0 && rand.nextInt(100) < luck) {
                rarity = upgrade(rarity);
            }
            loot.add(buildRift(rarity));
        }
        if (luck > 0 && rand.nextInt(100) < luck) {
            Rarity surge = rollRarity(type, rand);
            if (surge != null) {
                loot.add(buildRift(upgrade(surge)));
                surged = true;
            }
        }

        for (Map.Entry<String, Integer> entry : type.materialWeights().entrySet()) {
            if (entry.getValue() > 0 && rand.nextInt(100) < entry.getValue()) {
                loot.add(buildMaterial(entry.getKey()));
            }
        }

        boolean emptied = loot.isEmpty();
        if (type.requiresKey() && !emptied) {
            consumeKey(player, type);
        }
        // Hook: count loot toward active GlitchRaid (if installed) — fixes raid loot not ticking for containers
        if (!loot.isEmpty()) {
            try {
                org.bukkit.plugin.Plugin raidPlugin = Bukkit.getPluginManager().getPlugin("GlitchRaid");
                if (raidPlugin != null && raidPlugin.isEnabled()) {
                    Object raidMgr = cachedRaidMethod(raidPlugin, "getRaidManager", 0).invoke(raidPlugin);
                    if (raidMgr != null) {
                        java.util.UUID pid = player.getUniqueId();
                        Boolean inRaid = (Boolean) cachedRaidMethod(raidMgr, "isInRaid", 1).invoke(raidMgr, pid);
                        if (Boolean.TRUE.equals(inRaid)) {
                            try {
                                cachedRaidMethod(raidMgr, "addLootFromItems", 2).invoke(raidMgr, player, loot);
                            } catch (NoSuchMethodException nsme) {
                                int est = 0;
                                for (ItemStack s : loot) {
                                    if (s != null && !s.getType().isAir()) est += s.getAmount() * 10;
                                }
                                cachedRaidMethod(raidMgr, "addLoot", 3).invoke(raidMgr, pid, est);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        giveLoot(player, block, loot);

        if (type.shardsMin() > 0 && type.shardsMax() >= type.shardsMin()) {
            int shards = rand.nextInt(type.shardsMin(), type.shardsMax() + 1);
            if (shards > 0 && depositShards(player, shards)) {
                player.sendMessage(msg("shards", "<amount>", String.valueOf(shards)));
            }
        }

        BlockState state = block.getState();
        if (state instanceof PersistentDataHolder holder) {
            holder.getPersistentDataContainer().set(lastKey, PersistentDataType.LONG, now);
            state.update(true, false);
        }

        if (emptied && !surged) {
            player.sendMessage(msg("emptied", "<container>", type.display()));
            return false;
        }
        player.sendMessage(msg("looted", "<container>", type.display()));
        if (surged) {
            player.sendMessage(msg("surge"));
        }
        return true;
    }

    private Rarity rollRarity(ContainerType type, ThreadLocalRandom rand) {
        int total = type.nothingWeight();
        for (int weight : type.rarityWeights().values()) {
            total += weight;
        }
        if (total <= 0) return null;
        int pick = rand.nextInt(total);
        for (Map.Entry<Rarity, Integer> entry : type.rarityWeights().entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Rarity upgrade(Rarity rarity) {
        if (rarity == Rarity.LEGENDARY) return rarity;
        return Rarity.values()[rarity.getTier() + 1];
    }

    private boolean hasKey(Player player, ContainerType type) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && isKey(stack, type)) return true;
        }
        return isKey(inv.getItemInOffHand(), type);
    }

    private void consumeKey(Player player, ContainerType type) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && isKey(stack, type)) {
                if (stack.getAmount() > 1) stack.setAmount(stack.getAmount() - 1);
                else inv.setItem(i, null);
                return;
            }
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && isKey(offhand, type)) {
            if (offhand.getAmount() > 1) offhand.setAmount(offhand.getAmount() - 1);
            else inv.setItemInOffHand(null);
        }
    }

    private boolean isKey(ItemStack stack, ContainerType type) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!type.keyId().isEmpty()) {
            String id = OraxenUtil.idOf(stack);
            if (type.keyId().equalsIgnoreCase(id)) return true;
        }
        if (!type.keyMaterial().isEmpty()) {
            if (type.keyMaterialResolved() == null) return false;
            if (stack.getType() != type.keyMaterialResolved()) return false;
            if (!type.keyName().isEmpty()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta == null || !meta.hasCustomName()) return false;
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(meta.customName());
                return name != null && name.toLowerCase(java.util.Locale.ROOT).contains(type.keyNameLower());
            }
            return true;
        }
        return false;
    }

    private String keyDisplayName(ContainerType type) {
        String name = type.keyName() != null && !type.keyName().isEmpty() ? type.keyName() : type.keyId();
        return type.requiresKey() && !type.keyId().isEmpty() && !name.equals(type.keyId())
                ? name + " (" + type.keyId() + ")" : name;
    }

    private ItemStack buildRift(Rarity rarity) {
        String riftId = "unstable_rift_" + rarity.getId();
        ItemStack item = OraxenUtil.build(riftId);
        if (item != null) return item;
        ItemStack fallback = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = fallback.getItemMeta();
        meta.customName(MM.deserialize("<white>Unstable Rift (" + rarity.getDisplayName() + ")</white>"));
        meta.lore(List.of(
                MM.deserialize("<gray>An unstable piece of the Glitch.</gray>"),
                MM.deserialize("<gray>Identify it at the hub.</gray>")));
        meta.getPersistentDataContainer().set(ORAXEN_KEY, PersistentDataType.STRING, riftId);
        fallback.setItemMeta(meta);
        return fallback;
    }

    private ItemStack buildMaterial(String id) {
        ItemStack item = OraxenUtil.build(id);
        if (item != null) return item;
        ItemStack fallback = new ItemStack(MATERIAL_MATERIALS.getOrDefault(id, Material.PAPER), 1);
        ItemMeta meta = fallback.getItemMeta();
        String label = id.replace('_', ' ');
        label = label.substring(0, 1).toUpperCase() + label.substring(1);
        meta.customName(MM.deserialize("<white>" + label + "</white>"));
        meta.getPersistentDataContainer().set(ORAXEN_KEY, PersistentDataType.STRING, id);
        fallback.setItemMeta(meta);
        return fallback;
    }

    private void giveLoot(Player player, Block block, List<ItemStack> loot) {
        if (loot.isEmpty()) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(loot.toArray(new ItemStack[0]));
        if (!leftovers.isEmpty()) {
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(loc, left));
        }
    }

    private PersistentDataContainer data(Block block) {
        BlockState state = block.getState();
        return state instanceof PersistentDataHolder holder ? holder.getPersistentDataContainer() : null;
    }

    private boolean depositShards(Player player, int amount) {
        var economy = plugin.getEconomy();
        if (economy == null) return false;
        economy.depositPlayer(player, amount);
        return true;
    }

    private Component msg(String key) {
        return deserializeMsg(messagesRaw.getOrDefault(key, "<gray>" + key + "</gray>"), key);
    }

    private Component msg(String key, String ph1, String v1) {
        return deserializeMsg(messagesRaw.getOrDefault(key, "<gray>" + key + "</gray>").replace(ph1, v1), key);
    }

    private Component msg(String key, String ph1, String v1, String ph2, String v2) {
        return deserializeMsg(
                messagesRaw.getOrDefault(key, "<gray>" + key + "</gray>").replace(ph1, v1).replace(ph2, v2), key);
    }

    private static Component deserializeMsg(String raw, String key) {
        try { return MM.deserialize(raw); } catch (Exception e) { return Component.text(key); }
    }
}
