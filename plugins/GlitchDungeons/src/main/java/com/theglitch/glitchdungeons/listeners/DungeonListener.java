package com.theglitch.glitchdungeons.listeners;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DungeonListener implements Listener {
    private final GlitchDungeons plugin;

    public DungeonListener(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonRun run = plugin.getDungeonManager().getPlayerRun(player.getUniqueId());
        if (run == null) return;
        if (run.getState() != DungeonRun.State.ACTIVE) return;

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        plugin.getDungeonManager().playerDied(player.getUniqueId());

        String msg = "&c" + player.getName() + " has fallen!";
        for (java.util.UUID member : run.getParty().getMembers()) {
            Player p = org.bukkit.Bukkit.getPlayer(member);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg.replaceAll("&([0-9a-fk-or])", "\u00A7$1"));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DungeonRun run = plugin.getDungeonManager().getPlayerRun(player.getUniqueId());
        if (run != null) {
            DungeonRun.State state = run.getState();
            if (state == DungeonRun.State.ACTIVE) {
                plugin.getDungeonManager().playerDied(player.getUniqueId());
            } else if (state == DungeonRun.State.EXTRACTING) {
                // Check if any party members are still online and in extraction zone
                boolean anyoneOnline = false;
                for (java.util.UUID member : run.getParty().getMembers()) {
                    if (!member.equals(player.getUniqueId())) {
                        Player p = org.bukkit.Bukkit.getPlayer(member);
                        if (p != null && p.isOnline()) {
                            anyoneOnline = true;
                            break;
                        }
                    }
                }
                if (!anyoneOnline) {
                    plugin.getDungeonManager().failDungeon(run, DungeonRun.FailReason.WIPE);
                }
            } else if (state == DungeonRun.State.PREP || state == DungeonRun.State.ASSIGNING) {
                // Remove from alive list so wipe detection works
                run.playerDied(player.getUniqueId());
                // If all remaining players are gone, fail the dungeon
                if (run.isWiped()) {
                    plugin.getDungeonManager().failDungeon(run, DungeonRun.FailReason.WIPE);
                }
            }
        }
        plugin.getPartyManager().cleanupOffline(player);
    }
}
