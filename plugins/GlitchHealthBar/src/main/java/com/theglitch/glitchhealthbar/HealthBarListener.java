package com.theglitch.glitchhealthbar;

import io.papermc.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

public final class HealthBarListener implements Listener {

    private final GlitchHealthBar plugin;
    private final HealthBarManager manager;

    public HealthBarListener(GlitchHealthBar plugin, HealthBarManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (plugin.shouldTrack(mob)) {
            manager.attach(mob);
        }
    }

    // Paper-level event — fires for EVERY entity added to a world, including
    // plugin spawn paths that skip EntitySpawnEvent (e.g. MythicMobs 5.x).
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (plugin.shouldTrack(mob)) {
            manager.attach(mob);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        manager.refresh(entity);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        manager.remove(event.getEntity());
    }
}
