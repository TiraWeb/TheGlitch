package com.theglitch.glitchhealthbar;

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
