package com.theglitch.glitchitems;

import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Right-click handler for furniture-backed loot containers (Debris/Cache/Rift
 * Vault crate models — see ContainerManager class javadoc). Mirrors
 * {@link ContainerListener}, which still handles the legacy block-based Vault.
 * <p>
 * Denies Nexo's own storage GUI ({@code canOpenStorage}) so our loot-roll
 * logic runs instead — these furniture items have no {@code storage} section
 * configured, but denying explicitly avoids relying on that.
 * </p>
 */
public record ContainerFurnitureListener(ContainerManager manager) implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureInteract(NexoFurnitureInteractEvent event) {
        String furnitureId = event.getMechanic().getItemID();
        ContainerManager.ContainerType type = manager.typeForFurniture(furnitureId);
        if (type == null) return;

        event.setCanOpenStorage(Event.Result.DENY);
        event.setCancelled(true);

        Player player = event.getPlayer();
        manager.open(player, event.getBaseEntity().getLocation());
    }
}
