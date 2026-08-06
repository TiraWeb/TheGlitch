package com.theglitch.glitchdeathrules;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Red Zone mercy rule: on death the player keeps leggings + boots.
 * Everything else (helmet, chestplate, weapons, inventory) drops as normal.
 * Design: docs/ITEM_SYSTEM.md §12.
 */
public record DeathRulesListener(GlitchDeathRules plugin) implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.getConfig().getStringList("mercy-worlds").contains(player.getWorld().getName())) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        ItemStack leggings = inv.getLeggings();
        ItemStack boots = inv.getBoots();

        // keepInventory is OFF in the Red Zone, so the drop list contains every
        // item including armor. Remove the mercy slots from the drops so they
        // stay on the player through respawn.
        boolean kept = event.getDrops().removeIf(stack -> matches(stack, leggings) || matches(stack, boots));
        if (kept) {
            player.sendMessage(plugin.getComponent("mercy-kept"));
        }
    }

    private boolean matches(ItemStack drop, ItemStack slot) {
        return slot != null && !slot.getType().isAir() && drop.isSimilar(slot);
    }
}
