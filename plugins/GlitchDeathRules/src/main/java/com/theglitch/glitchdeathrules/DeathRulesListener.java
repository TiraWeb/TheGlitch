package com.theglitch.glitchdeathrules;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Iterator;

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
        // item including armor. Move the mercy slots from the drops into the
        // kept list (Paper getItemsToKeep) — merely removing them from the
        // drops only deletes them; the player does NOT get them back.
        boolean kept = false;
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            if (matches(drop, leggings) || matches(drop, boots)) {
                drops.remove();
                event.getItemsToKeep().add(drop);
                kept = true;
            }
        }
        if (kept) {
            player.sendMessage(plugin.getComponent("mercy-kept"));
        }
    }

    private boolean matches(ItemStack drop, ItemStack slot) {
        return slot != null && !slot.getType().isAir() && drop.isSimilar(slot);
    }
}
