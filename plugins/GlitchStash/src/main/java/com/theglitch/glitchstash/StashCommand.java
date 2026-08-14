package com.theglitch.glitchstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /stash command — opens the extraction stash GUI.
 */
public record StashCommand(GlitchStash plugin, StashManager stashManager) implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /stashtp — teleport to stash chest in hub
        if (command.getName().equalsIgnoreCase("stashtp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                return true;
            }
            player.sendMessage(plugin.getComponent("teleporting"));
            // Direct teleport first — dispatchCommand returns true even when
            // the command fails internally, so commands are fallbacks only.
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                org.bukkit.World hub = Bukkit.getWorld("hub");
                if (hub != null) {
                    player.teleport(hub.getSpawnLocation());
                    return;
                }
                if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "mv tp " + player.getName() + " hub");
                    return;
                }
                plugin.getLogger().warning("Could not teleport " + player.getName()
                        + " — hub world not found.");
                player.sendMessage(plugin.getComponent("teleport-failed"));
            });
            return true;
        }

        // /stash [give <item> <amount>]
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return handleGive(player, args[1], args[2]);
        }

        if (!stashManager.hasStash(player.getUniqueId())) {
            player.sendMessage(plugin.getComponent("stash-empty"));
            return true;
        }

        // Open the stash GUI
        StashGUI.open(player, stashManager, plugin);
        return true;
    }

    private boolean handleGive(Player player, String itemName, String amountStr) {
        // /stash give <item> <amount> — add items to stash (admin/debug)
        if (!player.hasPermission("glitchstash.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        try {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(itemName.toUpperCase());
            if (material == null) {
                player.sendMessage(Component.text("Unknown item: " + itemName, NamedTextColor.RED));
                return true;
            }

            int amount = Integer.parseInt(amountStr);
            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, amount);

            // Save to stash
            var contents = new org.bukkit.inventory.ItemStack[]{item};
            var armor = new org.bukkit.inventory.ItemStack[4];
            stashManager.saveStash(player.getUniqueId(), player.getName(), contents, armor, null);

            player.sendMessage(Component.text("Added " + amount + " " + material.name() + " to stash.",
                    NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED));
        }
        return true;
    }
}
