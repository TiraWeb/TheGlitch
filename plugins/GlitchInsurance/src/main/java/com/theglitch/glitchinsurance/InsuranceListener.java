package com.theglitch.glitchinsurance;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InsuranceListener implements Listener {

    private final GlitchInsurance plugin;
    private final InsuranceManager manager;

    // Track players who had items protected on death to show hint on respawn
    private final Map<UUID, Integer> pendingClaimNotice = new ConcurrentHashMap<>();

    public InsuranceListener(GlitchInsurance plugin, InsuranceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String world = player.getWorld().getName();
        if (!manager.isEnabledWorld(world)) return;

        UUID uuid = player.getUniqueId();
        List<InsuranceManager.InsuredItem> insured = manager.getInsured(uuid);
        if (insured.isEmpty()) return;

        // Kept-inventory deaths (e.g. dungeons calling setKeepInventory(true) and
        // clearing drops): drops are empty, so consume matching policies against the
        // retained inventory instead — gear kept, policy spent, nothing to claim.
        if (event.getKeepInventory() || event.getDrops().isEmpty()) {
            int consumed = manager.consumeMatchingRetained(uuid, player.getInventory().getContents());
            if (consumed > 0) {
                player.sendMessage(Component.text(
                        "Your insured gear was kept on death — the policy was spent (nothing to claim)."));
            }
            return;
        }

        // Move insured items from drops to itemsToKeep (Paper API)
        int kept = manager.consumeMatching(event.getDrops(), event.getItemsToKeep(), uuid);
        if (kept > 0) {
            // Send claim message — insured gear was protected
            player.sendMessage(plugin.getComponent("claim"));
            pendingClaimNotice.put(uuid, kept);

            // Schedule expiry notice removal? The claim window is for manual claim fallback;
            // since we auto-kept, we just hint on respawn.
            // Optionally schedule removal of pending notice after claim window
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> pendingClaimNotice.remove(uuid),
                    (long) manager.getClaimWindowSeconds() * 20L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Integer kept = pendingClaimNotice.get(uuid);
        if (kept != null && kept > 0) {
            // Delayed to ensure player is fully respawned
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) {
                    player.sendMessage(plugin.getComponent("claim"));
                    // Also hint to use list
                    player.sendMessage(Component.text("Your insured items were kept on death. (" + kept + " items protected)"));
                }
                pendingClaimNotice.remove(uuid);
            }, 20L);
        } else {
            // If player still has pending insured items (not yet died), give hint
            List<InsuranceManager.InsuredItem> insured = manager.getInsured(uuid);
            if (!insured.isEmpty()) {
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                    if (player.isOnline() && !manager.getInsured(uuid).isEmpty()) {
                        // Show remaining insurance count as hint
                        player.sendMessage(plugin.getComponent("list-header",
                                "<count>", String.valueOf(insured.size()),
                                "<max>", String.valueOf(manager.getMaxInsuredItems())));
                    }
                }, 20L);
            }
        }
    }
}
