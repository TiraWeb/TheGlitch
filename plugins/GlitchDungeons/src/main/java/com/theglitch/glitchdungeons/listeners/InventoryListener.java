package com.theglitch.glitchdungeons.listeners;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class InventoryListener implements Listener {
    private final GlitchDungeons plugin;

    public InventoryListener(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonRun run = plugin.getDungeonManager().getPlayerRun(player.getUniqueId());
        if (run == null) return;

        // Keep everything during dungeon run (gamerule handles this, but belt-and-suspenders)
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }
}
