package com.theglitch.glitchstash;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExtractionVariantListener implements Listener {

    private static final long WARNING_INTERVAL_MS = 10_000L;

    private final GlitchStash plugin;
    private final ExtractionVariantManager manager;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public ExtractionVariantListener(GlitchStash plugin, ExtractionVariantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        // Cached check — no getConfig() per move
        if (!manager.isEnabledCached()) return;

        Player player = event.getPlayer();
        ExtractionVariantManager.Variant variant = manager.variantAt(player.getLocation());
        if (variant == null || !variant.requiresKey()) return;
        if (manager.hasKey(player, variant) || manager.isArmed(player, variant)) return;

        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last != null && now - last < WARNING_INTERVAL_MS) return;
        lastWarning.put(player.getUniqueId(), now);

        player.sendMessage(plugin.getComponent("variant-needs-key",
                "<key>", keyDisplayName(variant),
                "<variant>", variant.name()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!manager.isEnabledCached()) return;

        Player player = event.getPlayer();
        ExtractionVariantManager.Variant variant = manager.variantAt(player.getLocation());
        if (variant == null || !variant.requiresKey()) return;
        if (!manager.isKey(player.getInventory().getItemInMainHand(), variant)) return;

        event.setCancelled(true);

        if (manager.isArmed(player, variant)) {
            player.sendMessage(plugin.getComponent("variant-already-armed", "<variant>", variant.name()));
            return;
        }
        if (manager.consumeOne(player, variant)) {
            manager.arm(player, variant);
            player.sendMessage(plugin.getComponent("variant-armed", "<variant>", variant.name()));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_LEVER_CLICK, 1.0f, 1.4f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    private String keyDisplayName(ExtractionVariantManager.Variant variant) {
        return variant.keyName() != null && !variant.keyName().isEmpty()
                ? variant.keyName() : variant.keyId();
    }
}
