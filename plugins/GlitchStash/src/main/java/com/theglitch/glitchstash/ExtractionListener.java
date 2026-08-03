package com.theglitch.glitchstash;

import com.theglitch.glitchitems.GlitchItems;
import com.theglitch.glitchshops.GlitchShops;
import com.theglitch.glitchshops.ShopManager;
import dev.velmax.velkoth.api.event.KothWinEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Listens for VelKoth extraction wins and saves player inventory to stash.
 * Applies the Residual Glitch payout multiplier (GlitchItems) as bonus shards
 * based on the saved loot's sell value (GlitchShops prices).
 * Handles teleport to hub directly (EssentialsX is incompatible with MC 26.x).
 */
public record ExtractionListener(GlitchStash plugin, StashManager stashManager) implements Listener {

    @EventHandler
    public void onExtractionWin(KothWinEvent event) {
        Player player = event.getWinner();

        // 1. Save full inventory to stash (merges with existing stash)
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        stashManager.saveStash(player.getUniqueId(), player.getName(), contents, armor, offhand);

        // 2. Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        // 3. Residual Glitch payout: bonus shards = sell value x (multiplier - 1)
        payGlitchBonus(player, contents, armor, offhand);

        // 4. Clear glitch stacks (design: clears on extraction or death)
        clearGlitchStacks(player);

        // 5. Notify player
        player.sendMessage(plugin.getComponent("extracted"));

        // 6. Teleport to hub (delay 1 tick so client processes the inventory clear)
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            teleportToHub(player);
        }, 5L);
    }

    private void payGlitchBonus(Player player, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        if (!plugin.getConfig().getBoolean("payout-enabled", true)) return;
        GlitchItems glitchItems = GlitchItems.getInstance();
        if (glitchItems == null) return;
        double multiplier = glitchItems.getGlitchManager().getPayoutMultiplier(player);
        if (multiplier <= 1.0) return;
        GlitchShops shops = GlitchShops.getInstance();
        if (shops == null) return;
        ShopManager shopManager = shops.getShopManager();

        int value = lootValue(contents) + lootValue(armor);
        if (offhand != null && !offhand.getType().isAir()) {
            Integer price = shopManager.sellPrice(offhand);
            if (price != null) {
                value += price * offhand.getAmount();
            }
        }
        int bonus = (int) Math.round(value * (multiplier - 1.0));
        if (bonus <= 0) return;

        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) return;
        provider.getProvider().depositPlayer(player, bonus);

        String raw = plugin.getMessage("glitch-payout");
        player.sendMessage(MiniMessage.miniMessage().deserialize(raw
                .replace("<multiplier>", String.format("%.1f", multiplier))
                .replace("<amount>", String.valueOf(bonus))));
    }

    private int lootValue(ItemStack[] items) {
        GlitchShops shops = GlitchShops.getInstance();
        if (shops == null) return 0;
        ShopManager shopManager = shops.getShopManager();
        int value = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            Integer price = shopManager.sellPrice(item);
            if (price != null) {
                value += price * item.getAmount();
            }
        }
        return value;
    }

    private void clearGlitchStacks(Player player) {
        GlitchItems glitchItems = GlitchItems.getInstance();
        if (glitchItems != null) {
            glitchItems.getGlitchManager().clear(player);
        }
    }

    /**
     * Teleport player to hub spawn. Tries multiple methods:
     * 1. Multiverse-Core: /mv tp <player> hub
     * 2. EssentialsX warp: /warp hub (if available)
     * 3. Direct location teleport to hub world spawn
     */
    private void teleportToHub(Player player) {
        // Try Multiverse-Core first (most reliable on MC 26.x)
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            boolean success = Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(), "mv tp " + player.getName() + " hub");
            if (success) return;
        }

        // Try EssentialsX warp (may not work on MC 26.x)
        if (Bukkit.getPluginManager().getPlugin("EssentialsX") != null
                || Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            boolean success = Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(), "warp hub " + player.getName());
            if (success) return;
        }

        // Fallback: direct location teleport to hub world spawn
        World hub = Bukkit.getWorld("hub");
        if (hub != null) {
            Location spawn = hub.getSpawnLocation();
            player.teleport(spawn);
            plugin.getLogger().info("Teleported " + player.getName() + " to hub via direct teleport.");
        } else {
            plugin.getLogger().warning("Could not teleport " + player.getName() + " — hub world not found.");
            player.sendMessage(plugin.getComponent("teleport-failed"));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (stashManager.hasStash(player.getUniqueId())) {
            // Delay message to ensure client is ready
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) {
                    player.sendMessage(plugin.getComponent("stash-saved"));
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            "Use /stash to retrieve your items",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY));
                }
            }, 40L); // 2 second delay
        }
    }
}
