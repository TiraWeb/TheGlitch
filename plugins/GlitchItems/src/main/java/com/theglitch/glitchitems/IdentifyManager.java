package com.theglitch.glitchitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

public final class IdentifyManager {

    private static final NamespacedKey ORAXEN_ID_KEY = new NamespacedKey("oraxen", "custom_item_id");

    private final GlitchItems plugin;
    private final GearManager gearManager;

    public IdentifyManager(GlitchItems plugin, GearManager gearManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
    }

    public Rarity riftRarity(ItemStack item) {
        String id = oraxenId(item);
        if (id == null) return null;
        if (!id.startsWith("unstable_rift_")) return null;
        String suffix = id.substring("unstable_rift_".length());
        Rarity rarity = Rarity.fromId(suffix);
        if (rarity == null) {
            rarity = matchRarityName(id);
        }
        return rarity;
    }

    public String oraxenId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(ORAXEN_ID_KEY, PersistentDataType.STRING);
        if (id != null && !id.isEmpty()) return id;
        if (item.getType().name().contains("AMETHYST") && item.getItemMeta().hasDisplayName()) {
            String name = item.getItemMeta().displayName().toString();
            if (name.contains("Unstable Rift")) {
                return name;
            }
        }
        return null;
    }

    private Rarity matchRarityName(String text) {
        for (Rarity rarity : Rarity.values()) {
            if (text.toLowerCase().contains(rarity.getId())) {
                return rarity;
            }
        }
        return null;
    }

    public boolean identify(Player player, boolean force) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Rarity rarity = riftRarity(held);
        if (rarity == null || held.getType().isAir()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Hold an Unstable Rift to identify it.</red>"));
            return false;
        }

        int fee = gearManager.identifyFee(rarity);
        Economy economy = plugin.getEconomy();
        if (!force) {
            if (economy == null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>Economy not available.</red>"));
                return false;
            }
            if (economy.getBalance(player) < fee) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>You need " + fee + " Shards to identify this rift (you have "
                                + (int) economy.getBalance(player) + ").</red>"));
                return false;
            }
        }

        if (!force && !economy.withdrawPlayer(player, fee).transactionSuccess()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Could not take the identify fee.</red>"));
            return false;
        }

        int amount = held.getAmount();
        if (amount > 1) {
            held.setAmount(amount - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        boolean weapon = ThreadLocalRandom.current().nextDouble()
                < plugin.getConfig().getDouble("reveal-weapon-chance", 0.6);
        GearType type = weapon ? GearType.randomWeapon() : GearType.randomArmor();
        ItemStack gear = gearManager.generateGear(type, rarity);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), gear);
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gold>The rift stabilizes... your inventory is full, the gear drops at your feet.</gold>"));
        } else {
            player.getInventory().addItem(gear);
            player.sendMessage(Component.text("The rift stabilizes into ", NamedTextColor.GOLD)
                    .append(gear.getItemMeta().displayName()));
        }
        return true;
    }
}
