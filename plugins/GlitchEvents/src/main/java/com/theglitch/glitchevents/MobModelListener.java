package com.theglitch.glitchevents;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Hooks pack-model displays onto configured mobs.
 *
 * <p>Attach runs a few ticks after spawn so MythicMobs has finished applying
 * the display name/options we match on. Death detaches immediately; the
 * periodic sweep in {@link MobModels} covers despawns and edge cases.</p>
 */
public final class MobModelListener implements Listener {

    private final GlitchEvents plugin;
    private final MobModels models;

    public MobModelListener(GlitchEvents plugin, MobModels models) {
        this.plugin = plugin;
        this.models = models;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!models.isEnabled()) return;
        LivingEntity mob = event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                models.tryAttach(mob);
            } catch (Exception e) {
                plugin.getLogger().warning("[MobModels] attach failed for " + mob.getType() + ": " + e.getMessage());
            }
        }, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            models.detach(event.getEntity());
        } catch (Exception e) {
            plugin.getLogger().warning("[MobModels] detach failed: " + e.getMessage());
        }
    }
}
