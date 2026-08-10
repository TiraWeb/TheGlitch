package com.theglitch.glitchitems;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Right-click handler for in-world loot containers. The open itself is
 * cancelled (no vanilla chest GUI) — the loot roll replaces it.
 */
public record ContainerListener(ContainerManager manager) implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();

        ContainerManager.ContainerType type = manager.typeOf(event.getClickedBlock());
        if (type == null) return;

        event.setCancelled(true);
        manager.open(player, event.getClickedBlock());
    }
}
