package com.theglitch.glitchitems;

import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

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
        // Fires once per hand (main + off) for a single physical right click —
        // same reason ContainerListener guards on EquipmentSlot.HAND. Without
        // this, every click opened the container twice (2026-09-21 bug report:
        // "chests get clicked 2-3 times even if I click once").
        if (event.getHand() != EquipmentSlot.HAND) return;

        String furnitureId = event.getMechanic().getItemID();
        ContainerManager.ContainerType type = manager.typeForFurniture(furnitureId);
        if (type == null) return;

        event.setCanOpenStorage(Event.Result.DENY);
        event.setCancelled(true);

        Player player = event.getPlayer();
        // Look up by the furniture entity itself (UUID), not its Location —
        // Nexo's FurnitureMechanic#place applies its own spawn-location
        // correction (hitbox height, solid-ground snap, etc.) after we ask it
        // to place at a given Location, so the entity's *actual* Location can
        // differ from the Location we recorded the container under. Keying by
        // entity UUID (see ContainerManager#mark) sidesteps that mismatch —
        // this was causing "this is not a glitch container" on the first
        // click of most furniture containers.
        manager.open(player, event.getBaseEntity());
    }
}
