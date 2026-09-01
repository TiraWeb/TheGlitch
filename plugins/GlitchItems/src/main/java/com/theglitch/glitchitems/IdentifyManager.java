package com.theglitch.glitchitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class IdentifyManager {

    private static final NamespacedKey ORAXEN_ID_KEY = new NamespacedKey("oraxen", "custom_item_id");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchItems plugin;
    private final GearManager gearManager;
    private volatile double revealWeaponChance = 0.6;
    private volatile int cachedRarityUpgradePerStack = 2;
    // Attunement Pack tokens: pending free identifies per player (any rarity)
    private final Map<UUID, Integer> freeIdentifies = new ConcurrentHashMap<>();

    public IdentifyManager(GlitchItems plugin, GearManager gearManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
        reload();
    }

    /** Grants pending free-identify tokens (Rift Attunement Pack consume). */
    public void addFreeIdentify(UUID playerId, int amount) {
        if (amount <= 0) return;
        freeIdentifies.merge(playerId, amount, Integer::sum);
    }

    public int freeIdentifyCount(UUID playerId) {
        return freeIdentifies.getOrDefault(playerId, 0);
    }

    public void reload() {
        revealWeaponChance = plugin.getConfig().getDouble("reveal-weapon-chance", 0.6);
        cachedRarityUpgradePerStack = plugin.getGlitchManager() != null
                ? plugin.getGlitchManager().getRarityUpgradePercentPerStack()
                : plugin.getConfig().getInt("residual-glitch.rarity-upgrade-percent-per-stack", 2);
    }

    public Rarity riftRarity(ItemStack item) {
        String id = oraxenId(item);
        if (id == null) return null;
        if (!id.startsWith("unstable_rift_")) {
            String lower = id.toLowerCase();
            if (!lower.contains("unstable rift")) return null;
            return matchRarityName(id);
        }
        String suffix = id.substring("unstable_rift_".length());
        Rarity rarity = Rarity.fromId(suffix);
        if (rarity == null) {
            rarity = matchRarityName(id);
        }
        return rarity;
    }

    public String oraxenId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        try {
            String id = pdc.get(ORAXEN_ID_KEY, PersistentDataType.STRING);
            if (id != null && !id.isEmpty()) return id;
        } catch (Exception ignored) {}

        for (NamespacedKey key : pdc.getKeys()) {
            try {
                if (!pdc.has(key, PersistentDataType.STRING)) continue;
                String value = pdc.get(key, PersistentDataType.STRING);
                if (value != null && value.startsWith("unstable_rift_")) {
                    return value;
                }
            } catch (Exception ignored) {}
        }

        if (item.getItemMeta().hasCustomName()) {
            Component nameComponent = item.getItemMeta().customName();
            if (nameComponent != null) {
                String name = PlainTextComponentSerializer.plainText().serialize(nameComponent);
                if (name.contains("Unstable Rift")) {
                    return name;
                }
            }
        }
        return null;
    }

    private Rarity matchRarityName(String text) {
        String lower = text.toLowerCase();
        Rarity best = null;
        for (Rarity rarity : Rarity.values()) {
            if (lower.contains(rarity.getId())
                    && (best == null || rarity.getId().length() > best.getId().length())) {
                best = rarity;
            }
        }
        return best;
    }

    public boolean identify(Player player, boolean force) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Rarity rarity = riftRarity(held);
        if (rarity == null || held.getType().isAir()) {
            player.sendMessage(MM.deserialize(
                    "<red>Hold an Unstable Rift to identify it.</red>"));
            return false;
        }

        int fee = gearManager.identifyFee(rarity);
        Economy economy = plugin.getEconomy();
        boolean useFree = !force && freeIdentifyCount(player.getUniqueId()) > 0;
        if (!force && !useFree) {
            if (economy == null) {
                player.sendMessage(MM.deserialize(
                        "<red>Economy not available.</red>"));
                return false;
            }
            if (economy.getBalance(player) < fee) {
                player.sendMessage(MM.deserialize(
                        "<red>You need " + fee + " Shards to identify this rift (you have "
                                + (int) economy.getBalance(player) + ").</red>"));
                return false;
            }
        }

        if (!force && !useFree && !economy.withdrawPlayer(player, fee).transactionSuccess()) {
            player.sendMessage(MM.deserialize(
                    "<red>Could not take the identify fee.</red>"));
            return false;
        }

        if (useFree) {
            freeIdentifies.merge(player.getUniqueId(), -1, Integer::sum);
            freeIdentifies.remove(player.getUniqueId(), 0);
            player.sendMessage(MM.deserialize(
                    "<blue>The attunement takes hold — <white>no fee charged</white>.</blue>"));
        }

        int amount = held.getAmount();
        if (amount > 1) {
            held.setAmount(amount - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        boolean weapon = ThreadLocalRandom.current().nextDouble() < revealWeaponChance;
        GearType type = weapon ? GearType.randomWeapon() : GearType.randomArmor();

        // Residual Glitch loot luck (design ITEM_SYSTEM.md §6):
        // - chance the revealed rarity surges one tier
        // - chance of +1 star on each stat roll
        int stacks = plugin.getGlitchManager().getStacks(player);
        Rarity revealed = rarity;
        int upgradePercent = stacks * cachedRarityUpgradePerStack;
        if (upgradePercent > 0 && revealed != Rarity.LEGENDARY
                && ThreadLocalRandom.current().nextInt(100) < upgradePercent) {
            revealed = Rarity.values()[revealed.getTier() + 1];
            player.sendMessage(MM.deserialize(
                    "<gold>The rift surges — a higher rarity shines through!</gold>"));
        }
        int luck = plugin.getGlitchManager().lootLuckBonus(player);
        ItemStack gear = gearManager.generateGear(type, revealed, null, luck);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), gear);
            player.sendMessage(MM.deserialize(
                    "<gold>The rift stabilizes... your inventory is full, the gear drops at your feet.</gold>"));
        } else {
            player.getInventory().addItem(gear);
            player.sendMessage(Component.text("The rift stabilizes into ", NamedTextColor.GOLD)
                    .append(gear.getItemMeta().displayName()));
        }
        return true;
    }
}
