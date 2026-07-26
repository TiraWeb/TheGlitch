package com.theglitch.glitchstash;

import dev.velmax.velkoth.api.event.KothWinEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for VelKoth extraction wins and saves player inventory to stash.
 */
public record ExtractionListener(GlitchStash plugin, StashManager stashManager) implements Listener {

    @EventHandler
    public void onExtractionWin(KothWinEvent event) {
        Player player = event.getPlayer();

        // Save full inventory to stash
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();

        stashManager.saveStash(player.getUniqueId(), player.getName(), contents, armor, offhand);

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        // Notify player
        player.sendMessage(plugin.getComponent("extracted"));

        // Teleport to hub
        String teleportCmd = plugin.getConfig().getString("extract-teleport", "spawn");
        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        teleportCmd.replace("%player%", player.getName())));
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
                            net.kyori.adventure.text.Color.NamedTextColor.GRAY));
                }
            }, 40L); // 2 second delay
        }
    }
}
