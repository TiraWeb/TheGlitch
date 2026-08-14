package com.theglitch.glitchitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-world loot containers (design GAME_DESIGN.md §3, ITEM_SYSTEM.md §9):
 * Debris Pile (free), Loot Cache (Cache Key), Vault (Vault Key),
 * Rift Vault (Rift Key).
 *
 * Containers are marked per-block via persistent data (admin command
 * /glitchcontainers set <type>). Opening rolls the type's rarity table
 * directly into the player's inventory (overflow drops at the block),
 * respects a per-block regen cooldown, consumes the required key, and applies
 * the Residual Glitch loot-luck consumer (per-roll rarity surge + surge drop).
 */
public final class ContainerManager {

    private static final NamespacedKey ORAXEN_KEY = new NamespacedKey("oraxen", "custom_item_id");
    private static final NamespacedKey TYPE_KEY = new NamespacedKey(GlitchItems.getInstance(), "glitch_container");
    private static final NamespacedKey LAST_KEY = new NamespacedKey(GlitchItems.getInstance(), "glitch_container_last");

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
            String keyName,
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
    private volatile Map<String, ContainerType> types = new HashMap<>();

    public ContainerManager(GlitchItems plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        Map<String, ContainerType> loaded = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("containers.types");
        if (section == null) {
            // Stale live configs (seeded before the containers feature) fail
            // silently as zero types — make it visible in the log instead.
            plugin.getLogger().warning("No 'containers.types' section in config.yml — "
                    + "container types are empty. Restore the default config or add the section.");
        } else {
            for (String name : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(name);
                if (t == null) continue;
                Material material = Material.matchMaterial(t.getString("material", "CHEST"));
                if (material == null) {
                    material = Material.CHEST;
                }
                Map<Rarity, Integer> rarityWeights = new HashMap<>();
                int nothingWeight = 0;
                ConfigurationSection drops = t.getConfigurationSection("drops");
                if (drops != null) {
                    for (String rarityId : drops.getKeys(false)) {
                        if (rarityId.equals("nothing")) {
                            // "nothing" is a real outcome (e.g. debris 15%) — it is
                            // not a Rarity, so it must be tracked separately or it
                            // would be silently dropped and the weights skew.
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
                loaded.put(name, new ContainerType(
                        name,
                        t.getString("display", name),
                        material,
                        t.getString("key-id", ""),
                        t.getString("key-material", ""),
                        t.getString("key-name", ""),
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
        plugin.getLogger().info("Containers loaded: " + loaded.size() + " types");
    }

    public List<ContainerType> getTypes() {
        return new ArrayList<>(types.values());
    }

    public ContainerType typeOf(Block block) {
        if (block == null) return null;
        PersistentDataContainer data = data(block);
        if (data == null) return null;
        String name = data.get(TYPE_KEY, PersistentDataType.STRING);
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
        // The PDC lives on the BlockState instance — mutate and update the SAME
        // snapshot, or the change is silently discarded.
        BlockState state = block.getState();
        if (!(state instanceof PersistentDataHolder holder)) {
            block.setType(previous);
            return false;
        }
        holder.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type.name());
        return state.update(true, false);
    }

    public void clear(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof PersistentDataHolder holder)) return;
        PersistentDataContainer data = holder.getPersistentDataContainer();
        data.remove(TYPE_KEY);
        data.remove(LAST_KEY);
        state.update(true, false);
    }

    /**
     * Open a container for a player. Returns true when loot was rolled.
     */
    public boolean open(Player player, Block block) {
        ContainerType type = typeOf(block);
        if (type == null) {
            player.sendMessage(msg("not-container"));
            return false;
        }
        if (!enabledWorlds().contains(block.getWorld().getName())) {
            player.sendMessage(msg("disabled-world"));
            return false;
        }

        PersistentDataContainer data = data(block);
        if (data == null) {
            player.sendMessage(msg("not-container"));
            return false;
        }
        long last = data.getOrDefault(LAST_KEY, PersistentDataType.LONG, 0L);
        long now = System.currentTimeMillis();
        long remaining = last + type.regenSeconds() * 1000L - now;
        if (remaining > 0) {
            player.sendMessage(msg("not-ready", "<container>", type.display(),
                    "<time>", String.valueOf((remaining + 999L) / 1000L)));
            return false;
        }

        if (type.requiresKey()) {
            if (!hasKey(player, type)) {
                player.sendMessage(msg("need-key", "<container>", type.display(), "<key>", keyDisplayName(type)));
                return false;
            }
            consumeKey(player, type);
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int luck = plugin.getGlitchManager().lootLuckBonus(player);

        List<ItemStack> loot = new ArrayList<>();
        boolean surged = false;

        int rolls = type.maxRolls();
        // Specter Scavenge (GlitchClasses trait, scoreboard tag) — extra rolls
        if (player.getScoreboardTags().contains("specter_scavenge")) {
            rolls += plugin.getConfig().getInt("containers.scavenge-bonus-rolls", 1);
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
        giveLoot(player, block, loot);

        if (type.shardsMin() > 0 && type.shardsMax() >= type.shardsMin()) {
            int shards = rand.nextInt(type.shardsMin(), type.shardsMax() + 1);
            if (shards > 0 && depositShards(player, shards)) {
                player.sendMessage(msg("shards", "<amount>", String.valueOf(shards)));
            }
        }

        // Write the regen cooldown through the same snapshot that carries the
        // PDC change — calling update() on a separate getState() would drop it.
        BlockState state = block.getState();
        if (state instanceof PersistentDataHolder holder) {
            holder.getPersistentDataContainer().set(LAST_KEY, PersistentDataType.LONG, now);
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

    // --- rolling -------------------------------------------------------------

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
        // The roll landed on the "nothing" outcome — no drop this roll.
        return null;
    }

    private Rarity upgrade(Rarity rarity) {
        if (rarity == Rarity.LEGENDARY) return rarity;
        return Rarity.values()[rarity.getTier() + 1];
    }

    // --- key handling ----------------------------------------------------------

    private boolean hasKey(Player player, ContainerType type) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && isKey(stack, type)) {
                return true;
            }
        }
        return false;
    }

    private void consumeKey(Player player, ContainerType type) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && isKey(stack, type)) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return;
            }
        }
    }

    private boolean isKey(ItemStack stack, ContainerType type) {
        if (stack == null || stack.getType().isAir()) return false;

        if (!type.keyId().isEmpty() && stack.hasItemMeta()) {
            String id = stack.getItemMeta().getPersistentDataContainer()
                    .get(ORAXEN_KEY, PersistentDataType.STRING);
            if (type.keyId().equalsIgnoreCase(id)) {
                return true;
            }
        }
        if (!type.keyMaterial().isEmpty()) {
            Material material;
            try {
                material = Material.valueOf(type.keyMaterial().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (stack.getType() != material) return false;
            if (!type.keyName().isEmpty()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta == null || !meta.hasCustomName()) return false;
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(meta.customName());
                return name != null && name.toLowerCase(java.util.Locale.ROOT)
                        .contains(type.keyName().toLowerCase(java.util.Locale.ROOT));
            }
            return true;
        }
        return false;
    }

    private String keyDisplayName(ContainerType type) {
        return type.keyName() != null && !type.keyName().isEmpty() ? type.keyName() : type.keyId();
    }

    // --- item building ---------------------------------------------------------

    private ItemStack buildRift(Rarity rarity) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.customName(MiniMessage.miniMessage().deserialize(
                "<white>Unstable Rift (" + rarity.getDisplayName() + ")</white>"));
        meta.lore(List.of(
                MiniMessage.miniMessage().deserialize("<gray>An unstable piece of the Glitch.</gray>"),
                MiniMessage.miniMessage().deserialize("<gray>Identify it at the hub.</gray>")));
        meta.getPersistentDataContainer().set(ORAXEN_KEY, PersistentDataType.STRING,
                "unstable_rift_" + rarity.getId());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildMaterial(String id) {
        ItemStack item = new ItemStack(MATERIAL_MATERIALS.getOrDefault(id, Material.PAPER), 1);
        ItemMeta meta = item.getItemMeta();
        String label = id.replace('_', ' ');
        label = label.substring(0, 1).toUpperCase() + label.substring(1);
        meta.customName(MiniMessage.miniMessage().deserialize("<white>" + label + "</white>"));
        meta.getPersistentDataContainer().set(ORAXEN_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private void giveLoot(Player player, Block block, List<ItemStack> loot) {
        if (loot.isEmpty()) return;
        // Loot goes straight into the player's inventory. The container's own
        // inventory can never be opened (the right-click that rolls the loot is
        // cancelled), so stashing loot in the block would make it unreachable.
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(
                loot.toArray(new ItemStack[0]));
        if (!leftovers.isEmpty()) {
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(loc, left));
        }
    }

    private PersistentDataContainer data(Block block) {
        BlockState state = block.getState();
        return state instanceof PersistentDataHolder holder
                ? holder.getPersistentDataContainer() : null;
    }

    private boolean depositShards(Player player, int amount) {
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> provider =
                plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (provider == null) return false;
        provider.getProvider().depositPlayer(player, amount);
        return true;
    }

    private List<String> enabledWorlds() {
        return plugin.getConfig().getStringList("containers.enabled-worlds");
    }

    private Component msg(String key) {
        return MiniMessage.miniMessage().deserialize(plugin.getConfig().getString(
                "containers.messages." + key, "<gray>" + key + "</gray>"));
    }

    private Component msg(String key, String ph1, String v1) {
        return MiniMessage.miniMessage().deserialize(plugin.getConfig().getString(
                "containers.messages." + key, "<gray>" + key + "</gray>")
                .replace(ph1, v1));
    }

    private Component msg(String key, String ph1, String v1, String ph2, String v2) {
        return MiniMessage.miniMessage().deserialize(plugin.getConfig().getString(
                "containers.messages." + key, "<gray>" + key + "</gray>")
                .replace(ph1, v1).replace(ph2, v2));
    }
}
