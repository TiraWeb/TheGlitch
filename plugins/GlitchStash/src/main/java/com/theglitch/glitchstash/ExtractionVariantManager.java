package com.theglitch.glitchstash;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast/Silent extraction variants (design ROADMAP 5.11.5):
 * key-requiring extraction zones with shorter VelKoth capture timers.
 */
public final class ExtractionVariantManager {

    private static final NamespacedKey ORAXEN_KEY = new NamespacedKey("oraxen", "custom_item_id");

    private final GlitchStash plugin;
    private final NamespacedKey variantKey;
    private final NamespacedKey variantExpiryKey;
    private volatile List<Variant> variants = new ArrayList<>();
    // World-indexed for fast variantAt without scanning all zones
    private volatile Map<String, List<Variant>> byWorld = new HashMap<>();

    // Cached hot-path values — refreshed on reload
    private volatile int cachedArmDuration = 180;
    private volatile boolean cachedEnabled = true;
    private volatile boolean cachedEnforceKey = true;

    public record Variant(String name, String world, double x1, double z1, double x2, double z2,
                           String keyId, String keyMaterial, String keyName, int payoutBonus) {
        boolean requiresKey() {
            return keyId != null && !keyId.isEmpty();
        }
    }

    public ExtractionVariantManager(GlitchStash plugin) {
        this.plugin = plugin;
        this.variantKey = new NamespacedKey(plugin, "extract_variant");
        this.variantExpiryKey = new NamespacedKey(plugin, "extract_variant_expiry");
        reload();
    }

    public void reload() {
        // Refresh cached config values from plugin (already cached there) — no getConfig() polling later
        cachedEnabled = plugin.isVariantEnabled();
        cachedEnforceKey = plugin.isVariantEnforceKey();
        int arm = plugin.getVariantArmDuration();
        if (arm < 1) {
            plugin.getLogger().warning("Invalid arm-duration " + arm + " — clamped to 1.");
            arm = 1;
        }
        cachedArmDuration = arm;

        List<Variant> loaded = new ArrayList<>();
        ConfigurationSection zones = plugin.getConfig().getConfigurationSection("extraction-variants.zones");
        if (zones != null) {
            for (String name : zones.getKeys(false)) {
                ConfigurationSection z = zones.getConfigurationSection(name);
                if (z == null) continue;
                String world = z.getString("world", "");
                if (world == null || world.isBlank()) {
                    plugin.getLogger().warning("Extraction variant '" + name + "' missing world — skipped.");
                    continue;
                }
                double x1 = z.getDouble("x1", 0);
                double z1 = z.getDouble("z1", 0);
                double x2 = z.getDouble("x2", 0);
                double z2 = z.getDouble("z2", 0);
                String keyId = z.getString("key-id", "");
                String keyMat = z.getString("key-material", "");
                String keyName = z.getString("key-name", "");
                int bonus = z.getInt("payout-bonus", 0);
                if (bonus < 0 || bonus > 1000) {
                    plugin.getLogger().warning("Variant '" + name + "' payout-bonus " + bonus + " out of range — clamped.");
                    bonus = Math.max(0, Math.min(bonus, 1000));
                }
                if (!keyMat.isEmpty()) {
                    try {
                        Material.valueOf(keyMat.toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Variant '" + name + "' invalid key-material " + keyMat + " — zone still loaded but key will never match.");
                    }
                }
                Variant v = new Variant(name, world, x1, z1, x2, z2, keyId, keyMat, keyName, bonus);
                loaded.add(v);
            }
        }
        variants = loaded;
        byWorld = indexByWorld(loaded);
        plugin.getLogger().info("Extraction variants loaded: " + loaded.size()
                + " (enabled=" + cachedEnabled + ", arm=" + cachedArmDuration + "s)");
    }

    public List<Variant> getVariants() {
        return variants;
    }

    /**
     * Replaces the in-memory zones used by the arm/consume lookup (variantAt)
     * with the given runtime zones — used by the dynamic extraction manager so
     * zones follow the randomly-picked arenas each cycle. Config zones remain
     * the template/fallback: passing null/empty (or a reload) restores them.
     */
    public void setRuntimeZones(List<Variant> zones) {
        if (zones == null || zones.isEmpty()) {
            byWorld = indexByWorld(variants);
            return;
        }
        byWorld = indexByWorld(zones);
    }

    private Map<String, List<Variant>> indexByWorld(List<Variant> list) {
        Map<String, List<Variant>> map = new HashMap<>();
        for (Variant v : list) {
            map.computeIfAbsent(v.world(), k -> new ArrayList<>()).add(v);
        }
        return map;
    }

    public boolean isEnabledCached() {
        return cachedEnabled;
    }

    public boolean isEnforceKeyCached() {
        return cachedEnforceKey;
    }

    public int getArmDurationCached() {
        return cachedArmDuration;
    }

    public Variant variantAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        // Fast path: lookup by world
        List<Variant> list = byWorld.get(location.getWorld().getName());
        if (list == null) return null;
        for (Variant variant : list) {
            if (inBounds(location, variant)) {
                return variant;
            }
        }
        return null;
    }

    private boolean inBounds(Location location, Variant variant) {
        double x = location.getX();
        double z = location.getZ();
        return x >= Math.min(variant.x1(), variant.x2()) && x <= Math.max(variant.x1(), variant.x2())
                && z >= Math.min(variant.z1(), variant.z2()) && z <= Math.max(variant.z1(), variant.z2());
    }

    /**
     * Mirror of GlitchItems' OraxenUtil.isIdShaped — avoids a cross-plugin dependency
     * while eliminating the costly regex {@code value.matches("[a-z_]+")} on the hot path.
     */
    private static boolean isIdShaped(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '_' && (c < 'a' || c > 'z')) return false;
        }
        return true;
    }

    private String oraxenId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(ORAXEN_KEY, PersistentDataType.STRING);
        if (id != null && !id.isEmpty()) return id;
        for (NamespacedKey key : pdc.getKeys()) {
            String value = pdc.get(key, PersistentDataType.STRING);
            if (isIdShaped(value)) {
                return value;
            }
        }
        return null;
    }

    public boolean hasKey(Player player, Variant variant) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && isKey(stack, variant)) {
                return true;
            }
        }
        return isKey(player.getInventory().getItemInOffHand(), variant);
    }

    public boolean isKey(ItemStack stack, Variant variant) {
        if (stack == null || stack.getType().isAir()) return false;

        if (!variant.keyId().isEmpty()) {
            String id = oraxenId(stack);
            if (variant.keyId().equalsIgnoreCase(id)) {
                return true;
            }
        }

        if (!variant.keyMaterial().isEmpty()) {
            Material material;
            try {
                material = Material.valueOf(variant.keyMaterial().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (stack.getType() != material) return false;
            if (!variant.keyName().isEmpty()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta == null || !meta.hasCustomName()) return false;
                String name = PlainTextComponentSerializer.plainText().serialize(meta.customName());
                return name != null && name.toLowerCase(java.util.Locale.ROOT)
                        .contains(variant.keyName().toLowerCase(java.util.Locale.ROOT));
            }
            return true;
        }
        return false;
    }

    public void arm(Player player, Variant variant) {
        // Use cached duration — no getConfig() per arm
        int armSeconds = cachedArmDuration;
        player.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, variant.name());
        player.getPersistentDataContainer().set(variantExpiryKey, PersistentDataType.LONG,
                System.currentTimeMillis() + armSeconds * 1000L);
    }

    public boolean isArmed(Player player, Variant variant) {
        String armed = player.getPersistentDataContainer().get(variantKey, PersistentDataType.STRING);
        if (armed == null || !armed.equals(variant.name())) return false;
        long expiry = player.getPersistentDataContainer()
                .getOrDefault(variantExpiryKey, PersistentDataType.LONG, 0L);
        if (expiry < System.currentTimeMillis()) {
            player.getPersistentDataContainer().remove(variantKey);
            player.getPersistentDataContainer().remove(variantExpiryKey);
            return false;
        }
        return true;
    }

    public void clearArmed(Player player) {
        player.getPersistentDataContainer().remove(variantKey);
        player.getPersistentDataContainer().remove(variantExpiryKey);
    }

    public boolean consumeOne(Player player, Variant variant) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && isKey(stack, variant)) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && isKey(offhand, variant)) {
            if (offhand.getAmount() > 1) {
                offhand.setAmount(offhand.getAmount() - 1);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            return true;
        }
        return false;
    }
}
