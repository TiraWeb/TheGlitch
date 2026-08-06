package com.theglitch.glitchstash;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast/Silent extraction variants (design ROADMAP 5.11.5):
 * key-requiring extraction zones with shorter VelKoth capture timers.
 *
 * Zones are rectangles defined in config (world + x/z bounds) so no
 * WorldGuard or VelKoth API dependency is needed. A player arms a variant by
 * right-clicking the required key while standing inside the zone; the key is
 * consumed and the arming flag lasts arm-duration-seconds.
 */
public final class ExtractionVariantManager {

    private static final NamespacedKey ORAXEN_KEY = new NamespacedKey("oraxen", "custom_item_id");

    private final GlitchStash plugin;
    private final NamespacedKey variantKey;
    private final NamespacedKey variantExpiryKey;
    private volatile List<Variant> variants = new ArrayList<>();

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
        List<Variant> loaded = new ArrayList<>();
        ConfigurationSection zones = plugin.getConfig().getConfigurationSection("extraction-variants.zones");
        if (zones != null) {
            for (String name : zones.getKeys(false)) {
                ConfigurationSection z = zones.getConfigurationSection(name);
                if (z == null) continue;
                loaded.add(new Variant(
                        name,
                        z.getString("world", ""),
                        z.getDouble("x1", 0),
                        z.getDouble("z1", 0),
                        z.getDouble("x2", 0),
                        z.getDouble("z2", 0),
                        z.getString("key-id", ""),
                        z.getString("key-material", ""),
                        z.getString("key-name", ""),
                        z.getInt("payout-bonus", 0)));
            }
        }
        variants = loaded;
        plugin.getLogger().info("Extraction variants loaded: " + loaded.size());
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public Variant variantAt(Location location) {
        if (location == null) return null;
        for (Variant variant : variants) {
            if (variant.world().equals(location.getWorld().getName()) && inBounds(location, variant)) {
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

    public boolean hasKey(Player player, Variant variant) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && isKey(stack, variant)) {
                return true;
            }
        }
        return false;
    }

    public boolean isKey(ItemStack stack, Variant variant) {
        if (stack == null || stack.getType().isAir()) return false;

        // Oraxen id match first (custom_item_id PDC), e.g. "fast_extract_key".
        if (!variant.keyId().isEmpty() && stack.hasItemMeta()) {
            String id = stack.getItemMeta().getPersistentDataContainer()
                    .get(ORAXEN_KEY, PersistentDataType.STRING);
            if (variant.keyId().equalsIgnoreCase(id)) {
                return true;
            }
        }

        // Fallback: material (+ custom name) match for servers without Oraxen.
        if (!variant.keyMaterial().isEmpty()) {
            Material material;
            try {
                material = Material.valueOf(variant.keyMaterial().toUpperCase());
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (stack.getType() != material) return false;
            if (!variant.keyName().isEmpty()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta == null || !meta.hasCustomName()) return false;
                String name = PlainTextComponentSerializer.plainText().serialize(meta.customName());
                return name != null && name.toLowerCase().contains(variant.keyName().toLowerCase());
            }
            return true;
        }
        return false;
    }

    public void arm(Player player, Variant variant) {
        int armSeconds = plugin.getConfig().getInt("extraction-variants.arm-duration-seconds", 180);
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
        return false;
    }
}
