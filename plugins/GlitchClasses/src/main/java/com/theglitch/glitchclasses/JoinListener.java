package com.theglitch.glitchclasses;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Re-gives class ability items when a player joins.
 */
public class JoinListener implements Listener {

    private final GlitchClasses plugin;
    private final ClassManager classManager;
    private final AbilityItemManager abilityItemManager;

    public JoinListener(GlitchClasses plugin, ClassManager classManager, AbilityItemManager abilityItemManager) {
        this.plugin = plugin;
        this.classManager = classManager;
        this.abilityItemManager = abilityItemManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ClassData data = classManager.getClassData(player.getUniqueId());
        if (!data.className().equals("none")) {
            // Delay to let inventory fully load
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                abilityItemManager.giveClassItems(player, data.className());
            }, 5L);
        }
    }
}
