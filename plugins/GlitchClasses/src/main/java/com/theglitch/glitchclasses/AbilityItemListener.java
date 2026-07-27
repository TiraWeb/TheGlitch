package com.theglitch.glitchclasses;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Prevents ability items from being moved, dropped, or dragged.
 * Re-gives ability items when entering game worlds.
 */
public class AbilityItemListener implements Listener {

    private static final Set<String> GAME_WORLDS = Set.of("glitch_pve", "glitch_red");

    private final GlitchClasses plugin;
    private final ClassManager classManager;
    private final AbilityItemManager abilityItemManager;

    public AbilityItemListener(GlitchClasses plugin, ClassManager classManager, AbilityItemManager abilityItemManager) {
        this.plugin = plugin;
        this.classManager = classManager;
        this.abilityItemManager = abilityItemManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && abilityItemManager.isAbilityItem(event.getCurrentItem())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendActionBar("§cClass abilities cannot be moved.");
            }
        }
        // Also prevent placing ability items from cursor
        if (event.getCursor() != null && abilityItemManager.isAbilityItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (abilityItemManager.isAbilityItem(item)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendActionBar("§cClass abilities cannot be moved.");
                }
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (abilityItemManager.isAbilityItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar("§cClass abilities cannot be dropped.");
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        if (GAME_WORLDS.contains(worldName)) {
            ClassData data = classManager.getClassData(player.getUniqueId());
            if (!data.className().equals("none")) {
                // Force re-place ability items in correct slots when entering game worlds
                abilityItemManager.forceGiveClassItems(player, data.className());
            }
        }
    }
}
