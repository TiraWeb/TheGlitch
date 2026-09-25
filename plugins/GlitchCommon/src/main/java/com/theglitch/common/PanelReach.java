package com.theglitch.common;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/**
 * Lets players click the floating hub panels from ~5 blocks instead of the
 * vanilla survival 3: a transient +2 entity-interaction-range modifier while
 * they are in one of the given worlds (the hub — PvP and mob spawning are
 * denied there, so the longer reach can't be abused in combat).
 */
public final class PanelReach implements Listener {

    private static final NamespacedKey KEY = new NamespacedKey("glitch", "panel_reach");
    private static final double BONUS = 2.0;
    private static volatile boolean registered;

    private final Set<String> worlds;

    private PanelReach(Set<String> worlds) {
        this.worlds = worlds;
    }

    /** Register once per server; later calls (from other plugins' shaded copies too) are no-ops. */
    public static synchronized void register(Plugin plugin, Set<String> worlds) {
        if (registered || Bukkit.getPluginManager().getPermission("glitch.panelreach.registered") != null) return;
        registered = true;
        Bukkit.getPluginManager().addPermission(new org.bukkit.permissions.Permission("glitch.panelreach.registered"));
        PanelReach reach = new PanelReach(worlds);
        Bukkit.getPluginManager().registerEvents(reach, plugin);
        Bukkit.getOnlinePlayers().forEach(reach::apply);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        AttributeInstance attr = event.getPlayer().getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr != null) attr.removeModifier(KEY);
    }

    private void apply(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr == null) return;
        attr.removeModifier(KEY);
        if (worlds.contains(player.getWorld().getName())) {
            attr.addTransientModifier(new AttributeModifier(KEY, BONUS, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
