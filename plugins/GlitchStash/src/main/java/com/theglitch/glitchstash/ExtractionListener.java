package com.theglitch.glitchstash;

import dev.velmax.velkoth.api.event.KothWinEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for VelKoth extraction wins and saves player inventory to stash.
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

        // 3. Notify player
        player.sendMessage(plugin.getComponent("extracted"));

        // 4. Teleport to hub (delay 1 tick so client processes the inventory clear)
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            teleportToHub(player);
        }, 5L);
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
